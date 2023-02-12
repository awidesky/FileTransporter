package com.awidesky.serverSide;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.Future;

import com.awidesky.main.Main;
import com.awidesky.util.SwingDialogs;
import com.awidesky.util.TaskLogger;


/**
 * 
 *  Represent single connection to client(receiver).
 *  
 *  
 * */
public class SendingConnection implements Runnable{

	private File[] files;
	private AsynchronousSocketChannel sendTo;
	private InetSocketAddress remoteAddr;
	
	private Future<?> future;
	private boolean isAborted = false;
	
	private int i = 0;
	private int progress = 0;
	private long sizeOfNowSendingFile = 0L;
	private long totalBytesTransfered = 0L;
	private String status = "";
	private String taskInfo;
	
	private ByteBuffer lenBuf = ByteBuffer.allocate(Main.lenBufSize);
	private ByteBuffer dataBuf = ByteBuffer.allocateDirect(Main.transferChunk);
	
	private TaskLogger logger = Main.getLogger();
	
	public SendingConnection(AsynchronousSocketChannel serverSocket, File[] f) {
		
		sendTo = serverSocket;
		files = f;
		status = "Preparing...";
		
		try {
			remoteAddr = (InetSocketAddress)sendTo.getRemoteAddress();
		} catch (IOException e) {
			
			try {
				if(sendTo != null)sendTo.close();
			} catch (IOException e1) {
				logger.log(e1);
			}
			
			SwingDialogs.error("Not connected with client!", "It seems like a client tried to connect, but not connected successfully\n%e%", e, true);
			isAborted = true;
			
			try {
				remoteAddr = new InetSocketAddress(InetAddress.getLocalHost(), -1);
			} catch (UnknownHostException e1) {
				logger.log(e1);
			}
			
			return;
		}
			
		taskInfo = "Server|Connection[" + getIP() + ":" + getPort() + "] ";
		logger.log(taskInfo + "	Connected to " + getIP());
		
	}
	
	public void setFuture(Future<?> f) {
		this.future = f;
		if(isAborted) {
			SwingDialogs.error("Connection lost!", "Not connected to client!", null, true);
			disconnect();
		}
	}
	
	public String getIP() {
		return remoteAddr.getAddress().getHostAddress();
	}

	public int getPort() {
		return remoteAddr.getPort();
	}
	
	/**
	 * Start process of sending files.<br>
	 * If the process somehow aborted in the middle of sending, it will throw a Exception.<br>
	 * When you try to re-call this method, it can't restart sending and it will throw a Exception,<br>
	 *  since it has no idea which position of the file it should send first.<br>
	 *   
	 * @return <code>true</code> if all the sending process went well. if not, <code>false</code>
	 * 
	 * */
	public void run() {

		//if(i != 0) new Exception("Connection to " + getIP() + " in port " + getPort() + "has aborted while sending " + getNowSendingFile().getAbsolutePath() + "!");
		taskInfo = Thread.currentThread().toString() + taskInfo;
		status = "Starting...";
		ByteBuffer clientResponse = ByteBuffer.allocate(1); //if client chose to receive this file, this buffer will contain 1, otherwise 0.
		
		for(; i < files.length ; i++) {
			
			logger.log(taskInfo + "Sending metadata of " +files[i].getName());
			/* Send metadata */
			ByteBuffer nameBuf = Main.charset.encode(files[i].getName());
			
			int nameBufSize = nameBuf.limit();
			lenBuf.asLongBuffer().put(nameBufSize);
			lenBuf.asLongBuffer().put(files[i].length());
			
			try (AsynchronousSocketChannel sock = sendTo) { //TODO : check if there's any error caused by this try resource
				
				while (lenBuf.hasRemaining()) {
					sendTo.write(lenBuf).get();
				}
				lenBuf.clear();
				
				while (nameBuf.hasRemaining()) {
					sendTo.write(nameBuf).get();
				}
				nameBuf.clear();
				
				Main.readFromChannel(sendTo, clientResponse, "Tried to read response from client, but client disconnected!");
				
			} catch (Exception e1) {
				String str = "Cannot send metadata : " + files[i].getAbsolutePath() + files[i].getName() + "\n";
				if(isAborted) {
					logger.log(taskInfo + str +"Thread interrupted while connecting with : " + remoteAddr.toString() + ", and download aborted!\n");
				}
				SwingDialogs.error(taskInfo + "Failed to send metadata!", str + "%e%", e1, false);
				status = "ERROR!";
				return;
			}
			
			if(clientResponse.get() == 0) { /* User don't want to download this file, skip it. */
				logger.log(taskInfo + "Skip " +files[i].getName() + " because client wanted to.");
				continue;
			}

			logger.log(taskInfo + "Sending " +files[i].getName());
			status = "Sending...";
			progress = 0;
			sizeOfNowSendingFile = files[i].length();
			totalBytesTransfered = 0L;

			try (FileChannel srcFile = FileChannel.open(files[i].toPath(), StandardOpenOption.READ)) {

				while (totalBytesTransfered < sizeOfNowSendingFile) {
					
					while (dataBuf.hasRemaining() && (srcFile.read(dataBuf) != -1)) {}
					dataBuf.flip();

					while (dataBuf.hasRemaining()) {
						long sentNow = sendTo.write(nameBuf).get();
						if (sentNow < 0) {
							/**
							 * Probably dead code, since if either socket is closed,
							 * <code>FileChannel#transferTo</code> throws ClosedChannelException
							 */
							throw new IOException("Client disconnected! (socketChannel.write returned with -1)");
						}
						totalBytesTransfered += sentNow;
					}
					
					progress = (int) Math.round(100.0 * totalBytesTransfered / sizeOfNowSendingFile);
					logger.log(taskInfo + "Sent " + totalBytesTransfered + "byte (" + progress + "%) from " + files[i].getName() + " to " + getIP());
					status = "Uploading... (" + (i + 1) + "/" + files.length + ")";
					ClientListTableModel.getinstance().updated(this);
				}

			} catch (Exception e) {
				
				String errStr =  "Cannot send file : " + files[i].getAbsolutePath() + files[i].getName() + " ("
						+ (int) Math.round(100.0 * totalBytesTransfered / sizeOfNowSendingFile) + "%)\n";
				if(isAborted) { 
					logger.log(taskInfo + "Thread interrupted while connecting with : " + getIP() + ":" + getPort() + ", and download aborted!\n" + errStr);
				}
				SwingDialogs.error(taskInfo + "Failed to send file!", errStr + "%e%", e, false);

				status = "ERROR!";
				return;
				
			}
			
			logger.log(taskInfo + "Sent " +files[i].getName() + " successfully!");
			
		} //for end
		
		try {
			sendTo.close();
		} catch (IOException e) {
			SwingDialogs.error("Failed to close connection with client!", "%e%", e, false);
		}
		status = "Completed!";

		return;
	}

	public File getNowSendingFile() {
		return files[i];
	}

	public String getNowSendingFileString() {
		return files[i].getName() + " (" + (i+1) + "/" + files.length + ")";
	}
	
	public int getProgress() {
		return progress;
	}

	public String getProgressString() {
		return progress + "% (" + Main.formatFileSize(totalBytesTransfered) + " / " +  Main.formatFileSize(sizeOfNowSendingFile) + ")";
	}
	
	public boolean isFinished() {
		return i == files.length && progress == 100;
	}

	/**
	 * note : This method is invoked in EDT
	 * */
	public void disconnect() {
		isAborted = true;
		future.cancel(true); //cancel the task
	}

	public String getStatus() {
		return status;
	}


}


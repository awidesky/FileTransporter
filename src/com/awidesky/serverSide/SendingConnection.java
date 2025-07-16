package com.awidesky.serverSide;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.Future;

import com.awidesky.Main;
import com.awidesky.util.SwingDialogs;
import com.awidesky.util.TaskLogger;


/**
 * 
 *  Represent single connection to client(receiver).
 *  
 *  
 * */
public class SendingConnection implements Runnable {

	private File[] files;
	private SocketChannel sendTo;
	private InetSocketAddress remoteAddr;
	
	private Future<?> future;
	private boolean isAborted = false;
	
	private int i = 0;
	private int progress = 0;
	private long fileSize = 0L;
	private long total = 0L;
	private String status = "";
	private String taskInfo;
	
	private ByteBuffer lenBuf = ByteBuffer.allocate(Main.lenBufSize);
	private ByteBuffer nameBuf = ByteBuffer.allocate(128);
	
	private TaskLogger logger = Main.getLogger(null);
	
	public SendingConnection(SocketChannel socket, File[] f) {
		
		sendTo = socket;
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
			
		taskInfo = "[Server connected to " + remoteAddr + "] ";
		logger.log(taskInfo + "Connected to " + remoteAddr);
		
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
		taskInfo = Thread.currentThread().getName() + " " + taskInfo;
		status = "Starting...";
		ByteBuffer clientResponse = ByteBuffer.allocate(1); //if client chose to receive this file, this buffer will contain 1, otherwise 0.
		
		while(true) {
			lenBuf.clear();
			nameBuf.clear();
			
			logger.log(taskInfo + "Sending metadata of " + files[i].getName());
			/* Send metadata */

			byte[] name = files[i].getName().getBytes(Main.charset);
			if(nameBuf.remaining() < name.length) { //resize nameBuf if needed
				int newSize = nameBuf.capacity();
				while(newSize < name.length) newSize *= 2;
				nameBuf = ByteBuffer.allocate(newSize);
			}
			nameBuf.put(name).flip();
			
			lenBuf.asLongBuffer().put(nameBuf.limit()).put(files[i].length())
				.compact();
			
			try {
				while (lenBuf.hasRemaining()) {
					sendTo.write(lenBuf);
				}
				lenBuf.clear();
				
				while (nameBuf.hasRemaining()) {
					sendTo.write(nameBuf);
				}
				nameBuf.clear();
				logger.log(taskInfo + "Metadata sent. Listen for client response...");
				
				if(!Main.readFromChannel(sendTo, clientResponse)) {
					System.out.println("read response return false, clientResponseBuf position : " + clientResponse.position());
					logger.log("Tried to read response from client, but client disconnected");
					break;
				}
				
				System.out.println("after recieving client response : " + sendTo.isOpen());
				
			} catch (Exception e1) {
				String str = "Cannot send metadata : " + files[i].getAbsolutePath() + "\n";
				if(isAborted) {
					logger.log(taskInfo + str +"Thread interrupted while connecting with : " + remoteAddr.toString() + ", and download aborted!\n");
				}
				SwingDialogs.error(taskInfo + "Failed to send metadata!", str + "%e%", e1, false);
				status = "ERROR!";
				return;
			}
			
			byte response = clientResponse.flip().get();
			logger.log("Client response : " + (int)response);
			if(response == 0) { /* User don't want to download this file, skip it. */
				logger.log(taskInfo + "Skip " +files[i].getName() + " because client wanted to.");
				continue;
			}

			logger.log("Sending " + files[i].getAbsolutePath());
			status = "Sending...";
			progress = 0;
			fileSize = files[i].length();
			total = 0L;

			try (FileChannel srcFile = FileChannel.open(files[i].toPath(), StandardOpenOption.READ)) {

				while (total < fileSize) {
					logger.log("Try transfer to " + remoteAddr); // TODO : debug level

					long read = srcFile.transferTo(total, Math.min(Main.transferChunk, fileSize), sendTo);
					total += read;
					logger.log("Transferred %s (total : %s of %s) to %s" // TODO : debug level
							.formatted(Main.formatFileSize(read), Main.formatFileSize(total), Main.formatFileSize(fileSize), remoteAddr));

					progress = (int) Math.round(100.0 * total / fileSize);
					logger.log("Sent " + total + "byte (" + progress + "%) from " + files[i].getName() + " to " + remoteAddr);
					status = "Uploading... (" + (i + 1) + "/" + files.length + ")";
					ClientListTableModel.getinstance().updated(this);
				}

			} catch (Exception e) {
				
				String errStr = "Cannot send file : " + files[i].getAbsolutePath() + " ("
						+ (int) Math.round(100.0 * total / fileSize) + "%)\n";
				if(isAborted) { 
					logger.log(taskInfo + "Thread interrupted while connecting with : " + getIP() + ":" + getPort() + ", and download aborted!\n" + errStr);
				}
				SwingDialogs.error(taskInfo + "Failed to send file!", errStr + "%e%", e, false);

				status = "ERROR!";
				return;
				
			}
			logger.log(taskInfo + "Sent " +files[i].getName() + " successfully!");

			if(i == files.length - 1)
				break;
			
			i++;
		} //while end
		
		try { //TODO : remove
			Thread.sleep(10000);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		logger.log("File transfer finished. Try connection close...");
		try {
			sendTo.close();
		} catch (IOException e) {
			SwingDialogs.error("Failed to close connection with client!", "%e%", e, false);
		}
		logger.log("Connection closed. Tasked completed.");
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
		return progress + "% (" + Main.formatFileSize(total) + " / " +  Main.formatFileSize(fileSize) + ")";
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


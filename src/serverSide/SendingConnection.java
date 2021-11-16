package serverSide;

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

import main.Main;


/**
 * 
 *  Represent single connection to client(receiver).
 *  
 *  
 * */
public class SendingConnection implements Runnable{

	private File[] files;
	private SocketChannel sendTo;
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
	
	public SendingConnection(SocketChannel ch, File[] f) {
		
		sendTo = ch;
		files = f;
		status = "Preparing...";
		
		try {
			remoteAddr = (InetSocketAddress)sendTo.getRemoteAddress();
		} catch (IOException e) {
			
			try {
				if(sendTo != null)sendTo.close();
			} catch (IOException e1) {
				Main.log(e1);
			}
			
			Main.error("Not connected with client!", "It seems like a client tried to connect, but not connected successfully\n%e%", e);
			isAborted = true;
			
			try {
				remoteAddr = new InetSocketAddress(InetAddress.getLocalHost(), -1);
			} catch (UnknownHostException e1) {
				Main.log(e1);
			}
			
			return;
		}
			
		taskInfo = "Server|Connection[" + getIP() + ":" + getPort() + "] ";
		Main.log(taskInfo + "Connected to " + getIP());
		
	}
	
	public void setFuture(Future<?> f) {
		this.future = f;
		if(isAborted) {
			Main.error("Connection lost!", "Not connected to client!", null);
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
			
			Main.log(taskInfo + "Sending metadata of " +files[i].getName());
			/* Send metadata */
			ByteBuffer nameBuf = Main.charset.encode(files[i].getName());
			
			int nameBufSize = nameBuf.limit();
			lenBuf.asLongBuffer().put(nameBufSize);
			lenBuf.asLongBuffer().put(files[i].length());
			
			try {
				
				long sent = 0L;
				while ((sent += sendTo.write(lenBuf)) != Main.lenBufSize);
				
				sent = 0L;
				while ((sent += sendTo.write(nameBuf)) != nameBufSize);
				
				Main.readFromChannel(sendTo, clientResponse, "Tried to read response from client, but client disconnected!");
				
			} catch (IOException e1) {
				String str = "Cannot send metadata :" + files[i].getAbsolutePath() + files[i].getName() + "\n";
				if(isAborted) {
					Main.log(taskInfo + str +"Thread interrupted while connecting with :" + remoteAddr.toString() + ", and download aborted!\n");
				}
				Main.error(taskInfo + "Failed to send metadata!", str + "%e%", e1);
				status = "ERROR!";
				return;
			}
			
			if(clientResponse.get() == 0) { /* User don't want to download this file, skip it. */
				Main.log(taskInfo + "Skip " +files[i].getName() + " because client wanted to.");
				continue;
			}

			Main.log(taskInfo + "Sending " +files[i].getName());
			status = "Sending...";
			progress = 0;
			sizeOfNowSendingFile = files[i].length();
			totalBytesTransfered = 0L;

			try (FileChannel srcFile = FileChannel.open(files[i].toPath(), StandardOpenOption.READ)) {

				while (totalBytesTransfered < sizeOfNowSendingFile) {
					long transferFromByteCount;

					transferFromByteCount = srcFile.transferTo(totalBytesTransfered,
								Math.min(Main.transferChunk, sizeOfNowSendingFile - totalBytesTransfered), sendTo);


					if (transferFromByteCount < 0) {
						/** Probably dead code, since if either socket is closed, <code>FileChannel#transferTo</code> throws ClosedChannelException */
						throw new IOException("Client disconnected! (socketChannel.write returned with -1)");
					}
					totalBytesTransfered += transferFromByteCount;
					progress = (int) (100.0 * totalBytesTransfered / sizeOfNowSendingFile);
					Main.log(taskInfo + "Sent " + transferFromByteCount + "byte (" + progress + "%) from " + files[i].getName() + " to " + getIP());
					status = "Uploading... (" + (i + 1) + "/" + files.length + ")";
					ClientListTableModel.getinstance().updated(this);
				}

			} catch (IOException e) {
				String str = "Cannot send file :" + files[i].getAbsolutePath() + files[i].getName() + " ("
						+ (int) (100.0 * totalBytesTransfered / sizeOfNowSendingFile) + "%)\n";
				if(isAborted) { //TODO : disconnect는 유저 선택임을 assure
					Main.log(taskInfo + "Thread interrupted while connecting with :" + getIP() + ":" + getPort() + ", and download aborted!\n" + str);
				}
				Main.error(taskInfo + "Failed to send file!", str, e);
				status = "ERROR!";
				return;
			}
			Main.log(taskInfo + "Sent " +files[i].getName() + " successfully!");
			
		} //for end
		
		try {
			sendTo.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
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
		future.cancel(false); //cancel the task
	}

	public String getStatus() {
		return status;
	}


}


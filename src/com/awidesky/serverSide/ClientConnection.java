package com.awidesky.serverSide;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.ConcurrentLinkedQueue;
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
public class ClientConnection implements Runnable {

	private SocketChannel sendTo;
	private InetSocketAddress remoteAddr;
	
	private ConcurrentLinkedQueue<File> fileQueue;
	
	private Future<?> future;
	private boolean isAborted = false;
	
	private int progress = 0;
	private long fileSize = 0L;
	private long total = 0L;
	private String status = "";
	
	private ByteBuffer lenBuf = ByteBuffer.allocate(Main.lenBufSize);
	private ByteBuffer nameBuf = ByteBuffer.allocate(128);
	
	private File curFile;
	
	private TaskLogger logger;
	
	public ClientConnection(TaskLogger logger, SocketChannel socket, InetSocketAddress remoteAddr, ConcurrentLinkedQueue<File> fileQueue) {
		this.sendTo = socket;
		this.remoteAddr = remoteAddr;
		this.fileQueue = fileQueue;
		this.status = "Preparing...";
		this.logger = logger;
		
		logger.log("Connected to " + remoteAddr);
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
	 *  since it has no idea which position of the curFile it should send first.<br>
	 *   
	 * @return <code>true</code> if all the sending process went well. if not, <code>false</code>
	 * 
	 * */
	public void run() {
		while((curFile = fileQueue.poll()) != null) {
			send();
		}
		
		logger.log("Transport finished. Try connection close...");
		try {
			lenBuf.clear().asLongBuffer().put(-1).put(-1).flip();
			while(lenBuf.hasRemaining()) sendTo.write(lenBuf);
			sendTo.close();
		} catch (IOException e) {
			SwingDialogs.error("Failed to close connection with client!", "%e%", e, false);
		}
		logger.log("Connection closed. Tasked completed.");
	}
	
	private void send() {
		status = "Starting...";

		lenBuf.clear();
		nameBuf.clear();

		logger.log("Sending metadata of \"" + curFile.getName() + "\"");
		/* Send metadata */

		byte response;
		try {
			response = sendMetadata(curFile);
		} catch (Exception e1) {
			String str = "Cannot send metadata : " + curFile.getAbsolutePath() + "\n";
			if(isAborted) {
				logger.log(str +"Thread interrupted while connecting with : " + remoteAddr.toString() + ", and download aborted!\n");
			}
			SwingDialogs.error("Failed to send metadata!", str + "%e%", e1, false);
			status = "ERROR!";
			return;
		}

		logger.log("Client response : " + (int)response);

		if(response == 0) { /* User don't want to download this curFile, skip it. */
			logger.log("Skip " +curFile.getName() + " because client wanted to.");
			return;
		}

		logger.log("Sending " + curFile.getAbsolutePath());
		status = "Sending...";
		progress = 0;
		fileSize = curFile.length();
		total = 0L;

		try (FileChannel srcFile = FileChannel.open(curFile.toPath(), StandardOpenOption.READ)) {

			while (total < fileSize) {
				logger.log("Try transfer to " + remoteAddr); // TODO : debug level

				long read = srcFile.transferTo(total, Math.min(Main.transferChunk, fileSize), sendTo);
				total += read;
				logger.log("Transferred %s (total : %s of %s) to %s" // TODO : debug level
						.formatted(Main.formatFileSize(read), Main.formatFileSize(total), Main.formatFileSize(fileSize), remoteAddr));

				progress = (int) Math.round(100.0 * total / fileSize);
				logger.log("Sent " + total + "byte (" + progress + "%) from " + curFile.getName() + " to " + remoteAddr);
				ClientListTableModel.getinstance().updated(this);
			}

		} catch (Exception e) {

			String errStr = "Cannot send curFile : " + curFile.getAbsolutePath() + " ("
					+ (int) Math.round(100.0 * total / fileSize) + "%)\n";
			if(isAborted) { 
				logger.log("Thread interrupted while connecting with : " + getIP() + ":" + getPort() + ", and download aborted!\n" + errStr);
			}
			SwingDialogs.error("Failed to send curFile!", errStr + "%e%", e, false);

			status = "ERROR!";
			return;

		}
		logger.log("Sent " +curFile.getName() + " successfully!");
		status = "Completed!";

		return;
	}
	
	private byte sendMetadata(File f) throws IOException {
		ByteBuffer clientResponse = ByteBuffer.allocate(1); //if client chose to receive this curFile, this buffer will contain 1, otherwise 0.
		lenBuf.clear();
		nameBuf.clear();
		
		byte[] name = f.getName().getBytes(Main.charset);
		if (nameBuf.remaining() < name.length) { //resize nameBuf if needed
			int newSize = nameBuf.capacity();
			while (newSize < name.length)
				newSize *= 2;
			nameBuf = ByteBuffer.allocate(newSize);
		}
		nameBuf.put(name).flip();
		lenBuf.asLongBuffer().put(nameBuf.remaining()).put(f.length()).flip();

		while (lenBuf.hasRemaining()) {
			sendTo.write(lenBuf);
		}
		
		while (nameBuf.hasRemaining()) {
			sendTo.write(nameBuf);
		}
		logger.log("Metadata sent. Listen for client response...");
		
		if(!Main.readFromChannel(sendTo, clientResponse)) {
			System.out.println("read response return false, clientResponseBuf position : " + clientResponse.position());
			logger.log("Tried to read response from client, but client disconnected");
			return 0;
		}
		
		return clientResponse.flip().get();
	}

	public File getNowSendingFile() {
		return curFile;
	}
	
	public int getProgress() {
		return progress;
	}

	public String getProgressString() {
		return progress + "% (" + Main.formatFileSize(total) + " / " +  Main.formatFileSize(fileSize) + ")";
	}
	
	public boolean isFinished() {
		return progress == 100;
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


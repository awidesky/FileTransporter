package serverSide;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.StandardOpenOption;

import main.Main;


/**
 * 
 *  Represent single connection to client(receiver).
 *  
 *  
 * */
public class SendingConnection {

	private File[] files;
	private SocketChannel sendTo;
	
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
		
		taskInfo = " Connection[" + getIP() + ":" + getPort() + "] ";
		Main.log(taskInfo + "Connected to " + getIP());
		
	}
	
	public String getIP() {
		try {
			return sendTo.socket().getInetAddress().toString();
		} catch (NullPointerException e) {
			return "NOT-CONNECTED";
		}
	}

	public int getPort() {
		try {
			return ((InetSocketAddress)sendTo.getRemoteAddress()).getPort();
		} catch (IOException e) {
			return -1;
		}
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
	public boolean start() {

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
				Main.error(taskInfo + "Failed to send metadata!", "Cannot send metadata :" + files[i].getAbsolutePath() + files[i].getName() + "\n%e%", e1);
				status = "ERROR!";
				return false;
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

					try {
						transferFromByteCount = srcFile.transferTo(totalBytesTransfered,
								Math.min(Main.transferChunk, sizeOfNowSendingFile - totalBytesTransfered), sendTo);
					} catch (IOException e) {
						Main.error(taskInfo + "Failed to send file!", "Cannot send file :" + files[i].getAbsolutePath() + files[i].getName() + " ("
								+ (int) (100.0 * totalBytesTransfered / sizeOfNowSendingFile) + "%)\n%e%", e);
						status = "ERROR!";
						return false;
					}

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
				Main.error(taskInfo + "Failed to send file!", "Cannot send file :" + files[i].getAbsolutePath() + files[i].getName() + "\n%e%", e);
				status = "ERROR!";
				return false;
			}
			Main.log(taskInfo + "Sent " +files[i].getName() + " successfully!");
			
		} //for end
		
		disconnect();
		status = "Completed!";

		return true;
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
	 * This method is invoked in EDT
	 * */
	public void disconnect() {
		
		try {
			sendTo.close();
		} catch (IOException e) {
			Main.error("Cannot close connection!", "Can't close connection to " + getIP() + "\n%e%", e);
		}
		
	}

	public String getStatus() {
		return status;
	}


}


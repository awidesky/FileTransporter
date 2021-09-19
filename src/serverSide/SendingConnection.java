package serverSide;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.FileChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.StandardOpenOption;

import main.Main;
import main.TwoConsumer;


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
	
	public SendingConnection(SocketChannel ch, File[] f) {
		
		sendTo= ch;
		files = f;
		
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
	 * Start process of sending files.
	 * If the process somehow aborted in the middle of sending, it will throw a Exception.
	 * When you try to re-call this method, it can't restart sending and it will throw a Exception,
	 *  since it has no idea which position of the file it should send first.
	 *   
	 * @return <code>true</code> if all the sending process went well. if not, <code>false</code>
	 * 
	 * */
	public boolean start(TwoConsumer<File, Integer> updateCallback) {

		if(i != 0) new Exception("Connection to " + getIP() + " in port " + getPort() + "has aborted while sending " + getNowSendingFile().getAbsolutePath() + "!");
		
		for(; i < files.length ; i++) {
			
			progress = 0;
			long size = files[i].length();
			long totalBytesTransfered = 0L;
			
			
			try (FileChannel srcFile = FileChannel.open(files[i].toPath(), StandardOpenOption.READ)) {

				while (totalBytesTransfered < size) {
					long transferFromByteCount;

					try {
						transferFromByteCount = srcFile.transferTo(totalBytesTransfered,
								Math.min(Main.transferChunk, size - totalBytesTransfered), sendTo);
					} catch (IOException e) {
						Main.error("Failed to send file!", "Cannot send file :" + files[i].getAbsolutePath() + " ("
								+ (int) (100.0 * totalBytesTransfered / size) + "%)\n%e%", e);
						return false;
					}

					if (transferFromByteCount < 0) {
						break;
					}
					totalBytesTransfered += transferFromByteCount;
					progress = (int) (100.0 * totalBytesTransfered / size);
					updateCallback.consume(files[i], progress);
				}

			} catch (IOException e) {
				Main.error("Failed to handle file!", "Cannot handle file :" + files[i].getAbsolutePath() + "\n%e%", e);
				return false;
			}
		    
			
		} //for end
		
		return true;
	}

	public File getNowSendingFile() {
		return files[i];
	}

	public int getProgress() {
		return progress;
	}

	public boolean isFinished() {
		return i == files.length && progress == 100;
	}

	public void disconnect() {
		// TODO Auto-generated method stub
		
	}
}


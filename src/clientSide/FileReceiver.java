package clientSide;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

import javax.swing.SwingUtilities;

import main.Main;

public class FileReceiver implements Runnable{

	
	private int progress = 0;
	private boolean isDone = false;
	private String status = ""; //TODO : staus도 GUI에 나오게..
	
	private String ip;
	private int port;
	
	private ByteBuffer lenBuf = ByteBuffer.allocate(Main.lenBufSize);
	
	public FileReceiver(String ip, String port) {
		
		this.ip = ip;
		this.port = Integer.parseInt(port);
		
	}
	
	
	public boolean isFinished() {
		return isDone;
	}

	public int getProgress() {
		return progress;
	}

	public String getDest() {
		// TODO Auto-generated method stub
		return null;
	}

	public void disconnect() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void run() {

		InetSocketAddress address = new InetSocketAddress(ip, port);
		boolean gotMetadata = false; // is metadata received?
		try (SocketChannel ch = SocketChannel.open()) {

			Main.log("Connecting to Server...");
			ch.configureBlocking(true);
			ch.connect(address);
			// 연결댔으면 "FileTransporter client " + Main.version + ip, port

			DownloadingListTableModel.getinstance().addTask(this);
			
			while (!isDone) {
				
				gotMetadata = false;
				long received = 0L;
				long[] lenData = new long[2]; // first is length of file name, and second is length of the file(both
												// counted in byte).

				while ((received += ch.read(lenBuf)) != Main.lenBufSize)
					;

				lenBuf.asLongBuffer().get(lenData);

				ByteBuffer nameBuf = ByteBuffer.allocate((int) lenData[0]);
				received = 0L;
				while ((received += ch.read(nameBuf)) != (int) lenData[0]);

				String fileName = Main.charset.decode(nameBuf.flip()).toString();

				gotMetadata = true;

				if(!chooseSaveDest(fileName, lenData[1])) continue;
				
			}
			

		} catch (IOException e) {
			status = "ERROR!";
			if(!gotMetadata) Main.error("Failed to receive metadata!", "Cannot receive metadata from :" + address.toString() + "\n%e%", e);
		}

	}


	/**
	 * Ask user where to save the received file.
	 * This method calls <code>SwingUtilities#invokeAndWait</code>.
	 * So never call this method in EDT! 
	 * 
	 * @return false when user don't want to download file, otherwise true.
	 * */
	private boolean chooseSaveDest(String fileName, long size) {
		
		if(!Main.confirm("Download file?", "Download " + fileName + "(" + Main.formatFileSize(size) +")")) {
			return false;
		}
		
		try {
			SwingUtilities.invokeAndWait(() -> {
				
				//TODO : Jfilechooser
				
			});
			return true;
		} catch (Exception e) {
			Main.error("Exception in Thread working(SwingUtilities.invokeAndWait)",
					e.getClass().getName() + "-%e%\nI'll consider you don't want to download this file", e);
		}
		
		return false;
		
	}



}

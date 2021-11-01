package clientSide;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

import javax.swing.SwingUtilities;

import main.Main;

public class FileReceiver implements Runnable{

	
	private int progress = 0;
	private long totalBytesTransfered;
	private long sizeOfNowReceivingFile;
	private boolean isDone = false;
	private String status = ""; 
	private File destination;
	
	private String ip;
	private int port;
	
	private ByteBuffer lenBuf = ByteBuffer.allocate(Main.lenBufSize);
	private ByteBuffer responseBuf = ByteBuffer.allocate(1);
	
	public FileReceiver(String ip, String port) {
		
		this.ip = ip;
		this.port = Integer.parseInt(port);
		
	}
	
	
	public boolean isFinished() {
		return isDone;
	}

	public String getProgressString() {
		return progress + "% (" + Main.formatFileSize(totalBytesTransfered) + " / " +  Main.formatFileSize(sizeOfNowReceivingFile) + ")";
	}
	
	public String getStaus() {
		
		if(status.equals("Downloading...")) {
			return status + " (" + progress + "%)";
		}
		return status;
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

			DownloadingListTableModel.getinstance().addTask(this);
			
			while (!isDone) {
				
				lenBuf.clear();
				responseBuf.clear();
				
				gotMetadata = false;
				long transferred = 0L;
				long[] lenData = new long[2]; // first is length of file name, and second is length of the file(both
												// counted in byte).

				while ((transferred += ch.read(lenBuf)) != Main.lenBufSize);

				lenBuf.asLongBuffer().get(lenData);

				ByteBuffer nameBuf = ByteBuffer.allocate((int) lenData[0]);
				transferred = 0L;
				while ((transferred += ch.read(nameBuf)) != (int) lenData[0]);

				String fileName = Main.charset.decode(nameBuf.flip()).toString();

				gotMetadata = true;

				if(Main.confirm("Download file?", "Download " + fileName + "(" + Main.formatFileSize(lenData[1]) +")")) { // Ask user where to save the received file.
					destination = chooseSaveDest(fileName, lenData[1]);
					if(destination != null) {
						responseBuf.put((byte) 1);
						transferred = 0L;
						while ((transferred += ch.write(responseBuf)) != 1);
						download();
					}
				}
				
				responseBuf.put((byte) 0);
				transferred = 0L;
				while ((transferred += ch.write(responseBuf)) != 1);
				continue;// user don't want to download this file.
				
			}
			

		} catch (IOException e) {
			status = "ERROR!";
			if(!gotMetadata) Main.error("Failed to receive metadata!", "Cannot receive metadata from :" + address.toString() + "\n%e%", e);
		}

	}


	private void download() {
		// TODO Auto-generated method stub
		status = "Downloading...";
		
	}


	/**
	 * 
	 * This method calls <code>SwingUtilities#invokeAndWait</code>. <br>
	 * So never call this method in EDT! <br>
	 * If there's already same file exist in chosen path, ask to overwrite it or not.
	 * 
	 * @return <code>File</code> object that represents the destination file. created before returning.
	 * 
	 * */
	private File chooseSaveDest(String fileName, long size) {
		
		try {
			while (true) {
				SwingUtilities.invokeAndWait(() -> {

					// TODO : Jfilechooser

				});

				File dest = new File("");
				if (dest.exists()) {
					if (Main.confirm("File already exists!",
							dest.getAbsolutePath() + " already exists! Want to overite?")) {
						dest.delete();
						dest.createNewFile();
					} else {
						continue;
					}
				}
				return dest;
			}
		} catch (Exception e) {
			Main.error("Exception in Creating file!",
					e.getClass().getName() + "%e%\nThis file will be skipped.", e);
		}
		
		return null;
		
	}


	public String connectedTo() {
		return ip + ":" + port;
	}




}

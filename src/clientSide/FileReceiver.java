package clientSide;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.SwingUtilities;

import main.Main;

public class FileReceiver implements Runnable{

	
	private int progress = 0;
	private long totalBytesTransfered;
	private long sizeOfNowReceivingFile;
	private boolean isDone = false;
	private boolean isAborted = false;
	private String status = ""; 
	private File destination;
	
	private static JFileChooser chooser = new JFileChooser((String)null);
	private static final JDialog dialog = new JDialog();
	
	private String ip;
	private int port;
	
	private ByteBuffer lenBuf = ByteBuffer.allocate(Main.lenBufSize);
	private ByteBuffer responseBuf = ByteBuffer.allocate(1);
	private String taskInfo;
	private Future<?> future;
	/** called when process aborted/cancelled, so that the reset GUI to initial state */
	private Runnable resetCallback;
	private ByteBuffer dataBuf = ByteBuffer.allocateDirect(Main.transferChunk);
	
	static {
		dialog.setAlwaysOnTop(true);
		chooser.setMultiSelectionEnabled(false);
		chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
	}
	
	public FileReceiver(String ip, int port, Runnable resetCallback) {
		
		this.ip = ip;
		this.port = port;
		this.resetCallback = resetCallback;
		taskInfo = "Client|Connection[" + ip + ":" + port + "] ";
		
	}
	
	public void setFuture(Future<?> f) {
		this.future = f;
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
		return destination.getAbsolutePath();
	}

	public void disconnect() {
		isAborted = true;
		future.cancel(true); //cancel the task
	}

	@Override
	public void run() {

		taskInfo = Thread.currentThread().toString() + taskInfo;
		String fileName = null;
		InetSocketAddress address = new InetSocketAddress(ip, port);
		boolean gotMetadata = false; // is metadata received?
		try (AsynchronousSocketChannel ch = AsynchronousSocketChannel.open(Main.channelGroup)) {

			Main.log(taskInfo + "Connecting to Server...");
			ch.connect(address).get();
			
			DownloadingListTableModel.getinstance().addTask(this);
			
			while (!isAborted) {
				
				lenBuf.clear();
				responseBuf.clear();
				
				gotMetadata = false;
				long[] lenData = new long[2]; // first is length of file name, and second is length of the file(both
												// counted in byte).
				
				Main.readFromChannel(ch, lenBuf, "Server disconnected while reading length of the file!");

				lenBuf.asLongBuffer().get(lenData);

				ByteBuffer nameBuf = ByteBuffer.allocate((int) lenData[0]);
				Main.readFromChannel(ch, nameBuf, "Server disconnected while reading name of the file!");

				fileName = Main.charset.decode(nameBuf.flip()).toString();

				gotMetadata = true;

				if(Main.confirm(taskInfo + "Download file?", "Download " + fileName + "(" + Main.formatFileSize(lenData[1]) +")")) { // Ask user where to save the received file.
					
					if((destination = chooseSaveDest(fileName)) != null) {
						responseBuf.put((byte) 1);
						while (responseBuf.hasRemaining()) {
							ch.write(responseBuf).get();
						}
						if(!download(ch)) { //download failed!
							throw new IOException("Download aborted while downloading " + destination.getAbsolutePath());
						}
					}
					
				} else { // user don't want to download this file.

					responseBuf.put((byte) 0);
					while (responseBuf.hasRemaining()) {
						ch.write(responseBuf).get();
					}
					
				}
				
			}
			

		} catch (ClosedByInterruptException inter) {
			status = "ERROR!";
			if(isAborted) Main.error(taskInfo + "Failed to receive files!", "Cannot receive file " + fileName + " from :" + address.toString() + ", and download aborted!\n%e%", inter);
			else Main.error(taskInfo + "Failed to receive files!", "Thread interrupted while connecting with : " + address.toString() + ", and download aborted!\n%e%", inter);
		} catch (Exception e) {
			status = "ERROR!";
			if(!gotMetadata) Main.error(taskInfo + "Failed to receive metadata!", "Cannot receive metadata from :" + address.toString() + "\n%e%", e); 
			else Main.error(taskInfo + "Failed to receive files!", "Cannot receive file from : " + address.toString() + "\n%e%", e);
		}

		if (!"ERROR!".equals(status)) {
			status = "Done!";
		}
		isDone = true;
		resetCallback.run();
		
	}


	/**
	 * 
	 * @return <code>true</code> if download finished.
	 * */
	private boolean download(AsynchronousSocketChannel ch) {

		Main.log("Downloading");
		status = "Downloading...";
		progress = 0;
		long sizeOfNowSendingFile = destination.length();
		
		try (FileChannel srcFile = FileChannel.open(destination.toPath(), StandardOpenOption.WRITE)) {

			totalBytesTransfered = 0L;

			while (totalBytesTransfered < sizeOfNowSendingFile) {
				
				long transferFromByteCount = 0L;
				try {
					
					while (dataBuf.hasRemaining() && (ch.read(dataBuf).get() != -1)) {}
					dataBuf.flip();

					while (dataBuf.hasRemaining()) {
						totalBytesTransfered += srcFile.write(dataBuf);
					}

				} catch (Exception e) {
					Main.error(taskInfo + "Failed to receive file!",
							"Cannot receive file : " + destination.getAbsolutePath() + destination.getName() + " ("
									+ (int) Math.round(100.0 * totalBytesTransfered / sizeOfNowSendingFile) + "%)\n%e%",
							e);
					return false;
				}

				totalBytesTransfered += transferFromByteCount;
				progress = (int) Math.round(100.0 * totalBytesTransfered / sizeOfNowSendingFile);
				Main.log(taskInfo + "Sent " + transferFromByteCount + "byte (" + progress + "%) from " + destination.getName() + " to " + ip);
				status = "Downloading...";
				DownloadingListTableModel.getinstance().updated(this);
			}

		} catch (IOException e) {
			Main.error(taskInfo + "Failed to handle file!", "Cannot handle file : " + destination.getAbsolutePath() + destination.getName() + "\n%e%", e);
			return false;
		}
		
		return true;
		
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
	private File chooseSaveDest(String fileName) {
		
		try {
			while (true) {

				final AtomicReference<File> result = new AtomicReference<>();
				
				SwingUtilities.invokeAndWait(() -> {

					chooser.setDialogTitle(taskInfo + "Choose a directory to download " + fileName);
					if (chooser.showOpenDialog(dialog) != JFileChooser.APPROVE_OPTION);
					File dest1 = chooser.getSelectedFile();
					result.set(dest1);
					chooser.setCurrentDirectory(dest1);

				});

				File dest = new File(result.get().getAbsolutePath() + File.separator + fileName);
				
				if (dest.exists()) {
					if (Main.confirm(taskInfo + "File already exists!",
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
			Main.error(taskInfo + "Exception in Creating file!",
					e.getClass().getName() + "%e%\nThis file will be skipped.", (e instanceof InvocationTargetException) ? (Exception)e.getCause() : e);
		}
		
		return null;
		
	}


	public String connectedTo() {
		return ip + ":" + port;
	}

	public int getProgress() {
		return progress;
	}



}

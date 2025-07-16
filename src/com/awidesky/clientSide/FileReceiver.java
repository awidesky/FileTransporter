package com.awidesky.clientSide;

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

import com.awidesky.Main;
import com.awidesky.util.SwingDialogs;
import com.awidesky.util.TaskLogger;

public class FileReceiver implements Runnable{

	private static final FileReceiver instance = new FileReceiver();

	private TaskLogger logger;
	
	private long totalBytesTransfered;
	private boolean isAborted = false;
	
	private static JFileChooser chooser = new JFileChooser((String)null);
	private static final JDialog dialog = new JDialog();
	
	private String ip;//TODO : needed?
	private int port;
	private InetSocketAddress remotAddress;
	
	private ByteBuffer lenBuf = ByteBuffer.allocate(Main.lenBufSize);
	private ByteBuffer responseBuf = ByteBuffer.allocate(1);
	private String taskInfo;
	private Future<?> future;
	/** called when process aborted/cancelled, so that the reset GUI to initial state */
	private Runnable resetCallback;
	private ByteBuffer dataBuf = ByteBuffer.allocateDirect(Main.transferChunk);

	private File destination;

	
	static {
		dialog.setAlwaysOnTop(true);
		chooser.setMultiSelectionEnabled(false);
		chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
	}
	
	public FileReceiver() {}	
	
	public static FileReceiver getInstance(String ip, int port, Runnable resetCallback, TaskLogger logger) { //TODO : just get a new logger from main..?
		
		instance.ip = ip;
		instance.port = port;
		instance.resetCallback = resetCallback;
		instance.taskInfo = "Client connected to [" + ip + ":" + port + "] ";
		instance.logger = logger;
		
		return instance;
		
	}
	
	public static FileReceiver getInstance() {
		return instance;
	}

	
	public void setFuture(Future<?> f) {
		this.future = f;
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
		boolean completed = false; // is connection completed without problem?
		try (AsynchronousSocketChannel ch = AsynchronousSocketChannel.open(Main.channelGroup)) {

			logger.log(taskInfo + "Connecting to Server...");
			ch.connect(address).get();
			remotAddress = (InetSocketAddress) ch.getRemoteAddress();
			logger.log("Connected to : " + remotAddress);
			SwingDialogs.information("Connected to the Server!", "Connected to " + remotAddress.getHostString() + ":" + remotAddress.getPort(), true);
			
			while (!isAborted) {
				
				lenBuf.clear();
				responseBuf.clear();
				
				gotMetadata = false;
				long[] lenData = new long[2]; // first is length of file name, and second is length of the file(both
												// counted in byte).
				
				if(Main.readFromChannel(ch, lenBuf, "Server disconnected while reading length of the file!") == false) {
					System.out.println("read length return false");
					completed = true;
					break;
				}

				lenBuf.flip().asLongBuffer().get(lenData);
				logger.log("File name lenght : " + lenData[0] + ", length of file : " + Main.formatFileSize(lenData[1]));

				ByteBuffer nameBuf = ByteBuffer.allocate((int) lenData[0]);
				Main.readFromChannel(ch, nameBuf, "Server disconnected while reading name of the file!");

				fileName = Main.charset.decode(nameBuf.flip()).toString();

				gotMetadata = true;

				if(SwingDialogs.confirm(taskInfo + "Download file?", "Download " + fileName + "(" + Main.formatFileSize(lenData[1]) +")")) { // Ask user where to save the received file.
					
					destination = chooseSaveDest(fileName);
					DonwloadingStatus dstat = new DonwloadingStatus(destination.getAbsolutePath(), lenData[1]);
					DownloadingListTableModel.getinstance().addTask(dstat);
					System.out.println("11 : " + ch.isOpen());
					if(destination != null) {
						responseBuf.put((byte) 1).flip();
						while (responseBuf.hasRemaining()) {
							ch.write(responseBuf).get();
						}
						System.out.println("22 : " + ch.isOpen());
						if(!download(ch, lenData[1], dstat)) { //download failed!
							dstat.setStatus("ERROR!");
							throw new IOException("Download aborted while downloading " + destination.getAbsolutePath());
						}
						dstat.setStatus("Done!");
					}
					
				} else { // user don't want to download this file.

					responseBuf.put((byte) 0).flip();
					while (responseBuf.hasRemaining()) {
						ch.write(responseBuf).get();
					}
					
				}
				
			}
			

		} catch (ClosedByInterruptException inter) {
			if(isAborted) SwingDialogs.error(taskInfo + "Failed to receive files!", "Cannot receive file " + fileName + " from :" + address.toString() + ", and download aborted!\n%e%", inter, true);
			else SwingDialogs.error(taskInfo + "Failed to receive files!", "Thread interrupted while connecting with : " + address.toString() + ", and download aborted!\n%e%", inter, true);
		} catch (Exception e) {
			if(!gotMetadata) SwingDialogs.error(taskInfo + "Failed to receive metadata!", "Cannot receive metadata from :" + address.toString() + "\n%e%", e, true); 
			else SwingDialogs.error(taskInfo + "Failed to receive files!", "Cannot receive file from : " + address.toString() + "\n%e%", e, true);
		} finally {
			SwingDialogs.information("Connection has closed", "Connection from :" + address.toString() + " has closed with" + (completed ? "out" : " a") + " error(s)!", true);
			resetCallback.run();
		}
		
	}


	/**
	 * 
	 * @param len 
	 * @param dstat 
	 * @return <code>true</code> if download finished.
	 * */
	private boolean download(AsynchronousSocketChannel ch, long len, DonwloadingStatus dstat) {

		logger.log("Downloading");
		dstat.setStatus("Downloading...");
		long sizeOfNowSendingFile = len;
		totalBytesTransfered = 0L;
		
		try (FileChannel destFile = FileChannel.open(destination.toPath(), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
			while (totalBytesTransfered < sizeOfNowSendingFile) {
				dataBuf.clear();
				logger.log("Try read " + Main.formatFileSize(dataBuf.remaining()) + " from " + remotAddress); // TODO : debug level
				System.out.println("33 : " + ch.isOpen());
				try {
					while (dataBuf.hasRemaining()) {
						int read = ch.read(dataBuf).get();
						logger.log("Read from server : " + Main.formatFileSize(read)); // TODO : debug level
						if(read == -1) {
							logger.log("Socket EOF reached!"); // TODO : better connection close logic
							//throw new Exception("Server connection closed(EOF reached)! IsOpen : " + ch.isOpen());
						}
					}

					dataBuf.flip();
					logger.log("Try write " + Main.formatFileSize(dataBuf.remaining()) + " to " + destination); // TODO : debug level


					while (dataBuf.hasRemaining()) {
						long written = destFile.write(dataBuf);
						logger.log("Written to file : " + Main.formatFileSize(written)); // TODO : debug level
						totalBytesTransfered += written;
					}
				} catch (Exception e) {
					SwingDialogs.error(taskInfo + "Failed to receive file!",
							"Cannot receive file : " + destination.getAbsolutePath() + " ("
									+ (int) Math.round(100.0 * totalBytesTransfered / sizeOfNowSendingFile) + "%)\n%e%",
							e, true);
					return false;
				}

				dstat.setProgress((int) Math.round(100.0 * totalBytesTransfered / sizeOfNowSendingFile));
				logger.log(taskInfo + "Received " + totalBytesTransfered + "byte (" + dstat.getProgress() + "%) from " + ip + " to " + destination.getName());
				DownloadingListTableModel.getinstance().updated(dstat);
			}

		} catch (IOException e) {
			SwingDialogs.error(taskInfo + "Failed to recieve file!", "Cannot recieve file : " + destination.getAbsolutePath() + destination.getName() + "\n%e%", e, true);
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

				File dest = new File(result.get(), fileName);
				
				if (dest.exists()) {
					if (SwingDialogs.confirm(taskInfo + "File already exists!",
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
			SwingDialogs.error(taskInfo + "Exception in Creating file!",
					e.getClass().getName() + "%e%\nThis file will be skipped.", (e instanceof InvocationTargetException) ? (Exception)e.getCause() : e, true);
		}
		
		return null;
		
	}


	public String connectedTo() {
		return ip + ":" + port;
	}



}

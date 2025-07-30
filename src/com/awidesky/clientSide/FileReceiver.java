package com.awidesky.clientSide;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.FileChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.StandardOpenOption;
import java.util.UUID;
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
	
	private long total;
	private boolean isAborted = false;
	
	private static JFileChooser chooser = new JFileChooser((String)null);
	private static final JDialog dialog = new JDialog();
	
	private String ip;//TODO : needed?
	private int port;
	private InetSocketAddress remotAddress;
	private UUID uuid;
	
	private ByteBuffer lenBuf = ByteBuffer.allocate(Main.lenBufSize);
	private ByteBuffer nameBuf = ByteBuffer.allocate(128);
	private ByteBuffer responseBuf = ByteBuffer.allocate(1);
	private String taskInfo;
	private Future<?> future;
	/** called when process aborted/cancelled, so that the reset GUI to initial state */
	private Runnable resetCallback;

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

		taskInfo = Thread.currentThread().getName() + " " + taskInfo;
		String fileName = null;
		boolean gotMetadata = false; // is metadata received?
		boolean completed = false; // is connection completed without problem?
		try (SocketChannel ch = SocketChannel.open()) {

			logger.log(taskInfo + "Connecting to Server...");
			ch.connect(new InetSocketAddress(ip, port));
			remotAddress = (InetSocketAddress) ch.getRemoteAddress();
			logger.log("Connected to : " + remotAddress);

			getUUID(ch);
			
			SwingDialogs.information("Connected to the Server!", "Connected to " + remotAddress.getHostString() + ":" + remotAddress.getPort(), true);
			
			while (!isAborted) {
				
				lenBuf.clear();
				nameBuf.clear();
				responseBuf.clear();
				
				gotMetadata = false;
				long[] lenData = new long[2]; // first is length of file name, and second is length of the file(both
												// counted in byte).
				
				if(!Main.readFromChannel(ch, lenBuf)) {
					System.out.println("read length return false, lenBuf position : " + lenBuf.position());
					completed = false;
					break;
				}

				lenBuf.flip().asLongBuffer().get(lenData);
				if(lenData[0] == -1 && lenData[1] == -1) {
					logger.log("File transfer finished! Closing connection...");
					completed = true;
					break;
				}
				
				logger.log("New file metadate recieved!");
				logger.log("File name length : " + lenData[0] + ", length of file : " + Main.formatFileSize(lenData[1]) + "(" + lenData[1] + "byte)");

				if(nameBuf.remaining() < lenData[0]) { //resize nameBuf if needed
					int newSize = nameBuf.capacity();
					while(newSize < lenData[0]) newSize *= 2;
					nameBuf = ByteBuffer.allocate(newSize);
				}
				nameBuf.limit((int) lenData[0]);
				
				if(!Main.readFromChannel(ch, nameBuf)) {
					System.out.println("read name return false, nameBuf position : " + nameBuf.position());
					completed = false;
					break;
				}

				fileName = Main.charset.decode(nameBuf.flip()).toString();
				logger.log("File name : " + fileName);
				gotMetadata = true;

				if(SwingDialogs.confirm(taskInfo + "Download file?", "Download \"" + fileName + "\" (" + Main.formatFileSize(lenData[1]) +")")) { // Ask user where to save the received file.
					destination = chooseSaveDest(fileName);
					DonwloadingStatus dstat = new DonwloadingStatus(destination.getAbsolutePath(), lenData[1]);
					DownloadingListTableModel.getinstance().addTask(dstat);
					if(destination != null) {
						logger.log("Sending download request...");
						responseBuf.put((byte) 1).flip();
						while (responseBuf.hasRemaining()) {
							ch.write(responseBuf);
						}
						
						logger.log("Initiate downloading " + fileName);
						if(!download(ch, lenData[1], dstat)) { //download failed!
							dstat.setStatus("ERROR!");
							throw new IOException("Download aborted while downloading " + destination.getAbsolutePath());
						}
						dstat.setStatus("Done!");
					}
				} else { // user don't want to download this file.
					responseBuf.put((byte) 0).flip();
					while (responseBuf.hasRemaining()) {
						ch.write(responseBuf);
					}
				}
				
				logger.log("Download Success!\n");
			}
		} catch (ClosedByInterruptException inter) {
			if(isAborted) SwingDialogs.error(taskInfo + "Failed to receive files!", "Cannot receive file " + fileName + " from :" + remotAddress + ", and download aborted!\n%e%", inter, true);
			else SwingDialogs.error(taskInfo + "Failed to receive files!", "Thread interrupted while connecting with : " + remotAddress + ", and download aborted!\n%e%", inter, true);
		} catch (Exception e) {
			if(!gotMetadata) SwingDialogs.error(taskInfo + "Failed to receive metadata!", "Cannot receive metadata from :" + remotAddress + "\n%e%", e, true); 
			else SwingDialogs.error(taskInfo + "Failed to receive files!", "Cannot receive file from : " + remotAddress + "\n%e%", e, true);
		} finally {
			SwingDialogs.information("Connection has closed", "Connection from :" + remotAddress + " has closed " + (completed ? "successfully!" : "with error(s)!"), true);
			resetCallback.run();
		}
		
	}
	
	private void getUUID(SocketChannel ch) throws IOException {
		logger.log("Send empty UUID for identification..."); //TODO : multiple connection?
		
		ByteBuffer buf = ByteBuffer.allocate(16);
		buf.asLongBuffer().put(0).put(0).flip();
		
		while(buf.hasRemaining()) ch.write(buf);
		
		buf.clear();
		
		while(buf.hasRemaining()) ch.read(buf);
		long[] bits = new long[2];
		buf.flip().asLongBuffer().get(bits);
		this.uuid = new UUID(bits[0], bits[1]);
		logger.log("Recieved UUID : " + uuid);
	}


	/**
	 * 
	 * @param len 
	 * @param dstat 
	 * @return <code>true</code> if download finished.
	 * */
	private boolean download(SocketChannel ch, long len, DonwloadingStatus dstat) {

		dstat.setStatus("Downloading...");
		long fileSize = len;
		total = 0L;
		
		try (FileChannel dest = FileChannel.open(destination.toPath(), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
			while (total < fileSize) {
				logger.log("Try transfer from " + remotAddress + " to " + destination.getName()); // TODO : debug level, relativize

				long read = dest.transferFrom(ch, total, Math.min(Main.transferChunk, fileSize - total));
				total += read;
				logger.log("Transferred %s (total : %s of %s) to %s" // TODO : debug level
						.formatted(Main.formatFileSize(read), Main.formatFileSize(total), Main.formatFileSize(fileSize), remotAddress));

				dstat.setProgress((int) Math.round(100.0 * total / fileSize));
				logger.log("Downloaded " + total + "byte (" + dstat.getProgress() + "%) from " + remotAddress + " to " + destination.getName());
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

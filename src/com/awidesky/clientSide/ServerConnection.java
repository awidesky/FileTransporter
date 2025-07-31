package com.awidesky.clientSide;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.FileChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.Future;

import com.awidesky.Main;
import com.awidesky.util.SwingDialogs;
import com.awidesky.util.TaskLogger;

public class ServerConnection implements Runnable{

	private TaskLogger logger;
	
	private long total;
	private boolean isAborted = false;

	private File destDir;
	private File destFile;
	
	private String ip;//TODO : needed?
	private int port;
	private InetSocketAddress remoteAddress;
	
	private ByteBuffer lenBuf = ByteBuffer.allocate(Main.lenBufSize);
	private ByteBuffer nameBuf = ByteBuffer.allocate(128);
	private ByteBuffer responseBuf = ByteBuffer.allocate(1);
	private String taskInfo;
	private Future<?> future;
	/** called when process aborted/cancelled, so that the reset GUI to initial state */

	public ServerConnection(String ip, int port, File destination, TaskLogger logger) { //TODO : just get a new logger from main..?
		
		this.ip = ip; //ip & port that entered by user
		this.port = port;
		this.destDir = destination;
		this.taskInfo = "Client connecting [" + ip + ":" + port + "] ";
		this.logger = logger;

	}
	
	public void setFuture(Future<?> f) {
		this.future = f;
	}
	
	public void disconnect() {
		if(!future.isDone()) {
			isAborted = true;
			future.cancel(true); //cancel the task
		}
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
			remoteAddress = (InetSocketAddress) ch.getRemoteAddress();
			logger.log("Connected to : " + remoteAddress);
			taskInfo = "Client connected to [" + remoteAddress + "] ";

			FileReciever.sendUUID(ch);
			
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

				destFile = new File(destDir, fileName);
				DonwloadingStatus dstat = new DonwloadingStatus(destFile.getAbsolutePath(), lenData[1], this);
				DownloadingListTableModel.getinstance().addTask(dstat);
				if(destFile != null) {
					logger.log("Sending download request...");
					responseBuf.put((byte) 1).flip();
					while (responseBuf.hasRemaining()) {
						ch.write(responseBuf);
					}

					logger.log("Initiate downloading " + fileName);
					if(!download(ch, lenData[1], dstat)) { //download failed!
						dstat.setStatus("ERROR!");
						throw new IOException("Download aborted while downloading " + destFile.getAbsolutePath());
					}
					dstat.setStatus("Done!");
				}

				logger.log("Download Success!\n");
			}
		} catch (ClosedByInterruptException inter) {
			if(isAborted) SwingDialogs.error(taskInfo + "Failed to receive files!", "Cannot receive file " + fileName + " from :" + remoteAddress + ", and download aborted!\n%e%", inter, true);
			else SwingDialogs.error(taskInfo + "Failed to receive files!", "Thread interrupted while connecting with : " + remoteAddress + ", and download aborted!\n%e%", inter, true);
		} catch (Exception e) {
			if(!gotMetadata) SwingDialogs.error(taskInfo + "Failed to receive metadata!", "Cannot receive metadata from :" + remoteAddress + "\n%e%", e, true); 
			else SwingDialogs.error(taskInfo + "Failed to receive files!", "Cannot receive file from : " + remoteAddress + "\n%e%", e, true);
		} finally {
			SwingDialogs.information("Connection has closed", "Connection to :" + remoteAddress + " has closed " + (completed ? "successfully!" : "with error(s)!"), true);
		}
		
	}

	public InetSocketAddress getRemoteAddress() {
		return remoteAddress;
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
		
		try (FileChannel dest = FileChannel.open(destFile.toPath(), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
			while (total < fileSize) {
				logger.log("Try transfer from " + remoteAddress + " to " + destFile.getName()); // TODO : debug level, relativize

				long read = dest.transferFrom(ch, total, Math.min(Main.transferChunk, fileSize - total));
				total += read;
				logger.log("Transferred %s (total : %s of %s) to %s" // TODO : debug level
						.formatted(Main.formatFileSize(read), Main.formatFileSize(total), Main.formatFileSize(fileSize), remoteAddress));

				dstat.setProgress((int) Math.round(100.0 * total / fileSize));
				logger.log("Downloaded " + total + "byte (" + dstat.getProgress() + "%) from " + remoteAddress + " to " + destFile.getName());
				DownloadingListTableModel.getinstance().updated(dstat);
			}
		} catch (IOException e) {
			SwingDialogs.error(taskInfo + "Failed to recieve file!", "Cannot recieve file : " + destFile.getAbsolutePath() + destFile.getName() + "\n%e%", e, true);
			return false;
		}
		
		return true;
	}

}

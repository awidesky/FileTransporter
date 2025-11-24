package io.github.awidesky.serverSide.connection;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.StandardOpenOption;

import io.github.awidesky.Main;
import io.github.awidesky.guiUtil.SwingDialogs;
import io.github.awidesky.serverSide.selectedFile.SelectedFile;


/**
 * 
 *  Represent single connection to client(receiver).
 *  
 *  
 * */
public class PlainConnection extends ClientConnection {

	private long fileSize = 0L;
	private long total = 0L;
	
	private ByteBuffer lenBuf = ByteBuffer.allocate(Main.lenBufSize);
	private ByteBuffer nameBuf = ByteBuffer.allocate(128);

	private void sendMetadata(SelectedFile f) throws IOException {
		lenBuf.clear();
		nameBuf.clear();
		
		byte[] name = f.relative().getBytes(Main.charset);
		if (nameBuf.remaining() < name.length) { //resize nameBuf if needed
			int newSize = nameBuf.capacity();
			while (newSize < name.length)
				newSize *= 2;
			nameBuf = ByteBuffer.allocate(newSize);
		}
		nameBuf.put(name).flip();
		lenBuf.asLongBuffer().put(nameBuf.remaining()).put(f.actual().length()).flip();

		sendTo.write(lenBuf);
		sendTo.write(nameBuf);
		
		logger.info("Metadata sent.");
	}

	@Override
	protected boolean sendFile(SelectedFile f) {
		File curFile = f.actual();
		logger.info("Sending metadata of \"" + curFile.getName() + "\"");
		
		try {
			/* Send metadata */
			sendMetadata(f);
		} catch (Exception e1) {
			String str = "Cannot send metadata : " + f + "\n";
			if(isAborted) {
				logger.info(str + "Thread interrupted while connecting with : " + remoteAddr.toString() + ", and download aborted!\n");
			}
			SwingDialogs.error("Failed to send metadata!", str + "%e%", e1, false);
			fileProgress.setStatus("ERROR!");
			return false;
		}


		logger.info("Sending " + f);
		fileSize = curFile.length();
		total = 0L;

		try (FileChannel srcFile = FileChannel.open(curFile.toPath(), StandardOpenOption.READ)) {

			while (total < fileSize) {
				logger.debug("Try transfer to " + remoteAddr);

				long read = srcFile.transferTo(total, Math.min(Main.getTransferChunk(), fileSize), sendTo);
				total += read;
				logger.info("Transferred %s (total : %s of %s) to %s" // TODO : debug level
						.formatted(Main.formatFileSize(read), Main.formatFileSize(total), Main.formatFileSize(fileSize), remoteAddr));

				fileProgress.setProgress((int) Math.round(100.0 * total / fileSize));
				fileProgress.setProgressString(fileProgress.getProgress() + "% (" + Main.formatFileSize(total) + " / " +  Main.formatFileSize(fileSize) + "), Connection : " + getAddress());
				logger.info("Sent " + total + "byte (" + fileProgress.getProgress() + "%) from " + curFile.getName() + " to " + remoteAddr);
			}

		} catch (Exception e) {

			String errStr = "Cannot send file : " + curFile.getAbsolutePath() + " ("
					+ (int) Math.round(100.0 * total / fileSize) + "%)\n";
			if(isAborted) { 
				logger.info("Thread interrupted while connecting with : " + remoteAddr + ", and download aborted!\n" + errStr);
			}
			SwingDialogs.error("Failed to send curFile!", errStr + "%e%", e, false);

			fileProgress.setStatus("ERROR!");
			return false;

		}
		logger.info("Sent " + curFile.getName() + " successfully!");
		fileProgress.setProgress(100);

		return true;
	}

	@Override
	protected void closeConnection() {
		lenBuf.clear().asLongBuffer().put(-1).put(-1).flip();
		try (SocketChannel s = sendTo) {
			s.write(lenBuf);
			s.close();
		} catch (IOException e) {
			SwingDialogs.error("Failed to close connection with client!", "%e%", e, false);
		}
	}


}


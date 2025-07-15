package com.awidesky.main;

import java.awt.Image;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousChannelGroup;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

import com.awidesky.util.LoggerThread;
import com.awidesky.util.SwingDialogs;
import com.awidesky.util.TaskLogger;


public class Main {

	private static LoggerThread logThread = null;
	private static TaskLogger mainLogger;
	private static boolean isStop = false;
	private static ExecutorService threadPool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
	public static AsynchronousChannelGroup channelGroup;
	public static int transferChunk = 0;
	
	public static final String version = "v1.0.0";
	
	public static final int lenBufSize = 16;
	public static final Charset charset = Charset.forName("UTF-8");
	
	public static final Image icon = null; //TODO : use images
	
	private static JFrame frame;
	
	static {
		 try {
			channelGroup = AsynchronousChannelGroup.withThreadPool(Main.getThreadPool());
		} catch (IOException e) {
			SwingDialogs.error("Cannot generate Thread Pool!!", "%e%", e, true);
			System.exit(1);
		}
	}
	
	public static void main(String[] args) {
		
		try {
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		} catch (ClassNotFoundException | InstantiationException | IllegalAccessException
				| UnsupportedLookAndFeelException e) {
			SwingDialogs.error("Error while setting window look&feel", "%e%", e, true);
		}
		
		prepareLogFile();
		
		SwingUtilities.invokeLater(InitFrame::new);
		
	}
	
	public static void setFrame(JFrame frame) {
		Main.frame = frame;
	}
	
	/**
	 * @return <code>true</code> if argument is valid
	 * */
	public static void setTransferChunk(String tpCh) {
		
		if (tpCh.substring(tpCh.length() - 2).equalsIgnoreCase("kb")) {
			transferChunk = Integer.parseInt(tpCh.substring(0, tpCh.length() - 2)) * 1024;
		} else if (tpCh.substring(tpCh.length() - 2).equalsIgnoreCase("mb")) {
			transferChunk = Integer.parseInt(tpCh.substring(0, tpCh.length() - 2)) * 1024 * 1024;
		} else if (tpCh.substring(tpCh.length() - 2).equalsIgnoreCase("gb")) {
			transferChunk = Integer.parseInt(tpCh.substring(0, tpCh.length() - 2)) * 1024 * 1024 * 1024;
		} else if (tpCh.substring(tpCh.length() - 1).equalsIgnoreCase("b")) {
			transferChunk = Integer.parseInt(tpCh.substring(0, tpCh.length() - 1));
		} 
		
		if(transferChunk == 0L) {// TODO Errorcheck from caller
			System.err.println("invalid argument for TransferChunk : " + tpCh);
			System.err.println("TransferChunk sets size of a chunk to send at a time(affect to sending progress bar), type like \"512B\" or \"256mb\" (b/kb/mb/gb. case ignored)");
		} else {
			mainLogger.log("transferChunk = " + Main.formatFileSize(Main.transferChunk) + "byte"); //log might queued but not be printed
		}
	}
	
	
	private static void prepareLogFile() {
		
		try {
			File logFolder = new File(new File(".").getAbsoluteFile().getParent() + File.separator + "logs");
			File logFile = new File(logFolder.getAbsolutePath() + File.separator + "log-" + new SimpleDateFormat("yyyy-MM-dd-kk-mm-ss.SSS").format(new Date()) + ".txt");
			logFolder.mkdirs();
			logFile.createNewFile();
			logThread = new LoggerThread(new FileOutputStream(logFile), true);
		} catch (IOException e) {
			logThread = new LoggerThread(System.out, true);
			SwingDialogs.error("Error when creating log flie", "%e%", e, true);
		} finally {
			Main.mainLogger = logThread.getLogger();
			SwingDialogs.setLogger(logThread.getLogger());
			logThread.start();
		}
	}

	public static TaskLogger getLogger(String prefix) {
		return logThread.getLogger(prefix);
	}
	
	public static void kill(int i) {

		isStop = true;
		
		if (SwingUtilities.isEventDispatchThread()) {
			disposeAll();
		} else {
			try {
				SwingUtilities.invokeAndWait(Main::disposeAll);
			} catch (InvocationTargetException e) {
				e.getCause().printStackTrace();
			} catch (InterruptedException e1) {
				e1.printStackTrace();
			}
		}

		mainLogger.log("killed with exit code : " + i);
		logThread.kill(3000);
		
		System.exit(i);
		
	}

	private static void disposeAll() {
		if(frame != null) frame.dispose();
	}
	/**
	 * Submits a job to main worker thread pool
	 * */
	public static Future<?> queueJob(Runnable job) {

		return threadPool.submit(job);
		
	}

	public static String formatFileSize(long length) {
		
		if(length == 0L) return "0.00 byte";
		
		switch ((int)(Math.log(length) / Math.log(1024))) {
		
		case 0:
			return String.format("%d", length) + " byte";
		case 1:
			return String.format("%.2f", length / 1024.0) + " KB";
		case 2:
			return String.format("%.2f", length / (1024.0 * 1024)) + " MB";
		case 3:
			return String.format("%.2f", length / (1024.0 * 1024 * 1024)) + " GB";
		}
		return String.format("%.2f", length / (1024.0 * 1024 * 1024 * 1024)) + " TB";
	}

	public static boolean isAppStopped() {
		return isStop;
	}
	
	/**
	 * <p>A utility function that fills a <code>ByteBuffer</code> by reading bytes from <code>ReadableByteChannel</code>.
	 * <p>This method does not check about buffer's size.
	 * <p>This method closes <code>ch</code> when the peer is disconnected.</p>
	 * 
	 * @param readFrom A <code>AsynchronousSocketChannel</code> to read from.
	 * @param buf <code>ByteBuffer</code> to store read bytes.
	 * @param errorString When Exception occurred, this method throws <code>new IOException(whenClosed)</code>.
	 * 
	 * @return If peer has closed the stream, return <code>false</code>.
	 * 
	 * @throws Exception 
	 * */
	public static boolean readFromChannel(AsynchronousSocketChannel readFrom, ByteBuffer buf, String errorString) throws Exception {
		try {
			while (buf.hasRemaining()) {
				if (readFrom.read(buf).get() == -1) {
					readFrom.close();
					return false;
				}
			}
			return true;
		} catch (Exception e) {
			throw new Exception(errorString, e);
		}
	}

	public static ExecutorService getThreadPool() {
		return threadPool;
	}


}

package main;

import java.awt.EventQueue;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousChannelGroup;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;



public class Main {

	private static Thread logger;
	private static LinkedBlockingQueue<Runnable> loggerQueue = new LinkedBlockingQueue<>();
	private static boolean isStop = false;
	private static PrintWriter logTo;
	private static ExecutorService threadPool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
	public static AsynchronousChannelGroup channelGroup;
	public static int transferChunk = 0;
	
	public static final String version = "v1.0.0";
	
	public static final int lenBufSize = 16;
	public static final Charset charset = Charset.forName("UTF-8");
	
	private static final JDialog confirmDialogParent = new JDialog();
	
	private static JFrame frame;
	
	static {
		 try {
			channelGroup = AsynchronousChannelGroup.withThreadPool(Main.getThreadPool());
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public static void main(String[] args) {
		
		try {
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		} catch (ClassNotFoundException | InstantiationException | IllegalAccessException
				| UnsupportedLookAndFeelException e) {
			error("Error while setting window look&feel", "%e%", e);
		}
		
		prepareLogFile();
		
		SwingUtilities.invokeLater(InitFrame::new);
		
	}
	
	public static void setFrame(JFrame frame) {
		Main.frame = frame;
	}
	
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
		
		if(transferChunk == 0L) {
			System.err.println("invalid argument for TransferChunk : " + tpCh);
			System.err.println("TransferChunk sets size of a chunk to send at a time(affect to sending progress bar), type like \"512B\" or \"256mb\" (b/kb/mb/gb. case ignored)");
		} else {
			Main.log("transferChunk = " + Main.formatFileSize(Main.transferChunk) + "byte"); //log might queued but not be printed
		}
	}
	
	/**
	 * show error dialog.
	 * String <code>"%e%"</code> in <code>content</code> will replaced by error message of given <code>Exception</code> if it's not <code>null</code>
	 * */
	public static void error(String title, String content, Exception e) {

		Main.log("\n");
		String co = content.replace("%e%", (e == null) ? "null" : e.getMessage());
		SwingUtilities.invokeLater(() -> {
			
			final JDialog dialog = new JDialog();
			dialog.setAlwaysOnTop(true);  
			JOptionPane.showMessageDialog(dialog, co.replace("\n", System.lineSeparator()), title.replace("\n", System.lineSeparator()), JOptionPane.ERROR_MESSAGE);
			
		});
		
		Main.log("[GUI.error] " + title + "\n\t" + co);
		if(e != null) Main.log(e);
		
	}
	

	/**
	 * show warning dialog.
	 * String <code>"%e%"</code> in <code>content</code> will replaced by warning message of given <code>Exception</code> if it's not <code>null</code>
	 * 
	 * */
	public static void warning(String title, String content, Exception e) {

		Main.log("\n");
		String co = content.replace("%e%", (e == null) ? "null" : e.getMessage());
		SwingUtilities.invokeLater(() -> {

			final JDialog dialog = new JDialog();
			dialog.setAlwaysOnTop(true);  
			JOptionPane.showMessageDialog(dialog, co.replace("\n", System.lineSeparator()), title.replace("\n", System.lineSeparator()), JOptionPane.WARNING_MESSAGE);
			
		});
		
		Main.log("[GUI.warning] " + title + "\n\t" + co);
		if(e != null) Main.log(e);
		
	}
	
	/**
	 * show information dialog.
	 * 
	 * */
	public static void information(String title, String content) {

		Main.log("\n");
		SwingUtilities.invokeLater(() -> {

			final JDialog dialog = new JDialog();
			dialog.setAlwaysOnTop(true);  
			JOptionPane.showMessageDialog(dialog, content.replace("\n", System.lineSeparator()), title.replace("\n", System.lineSeparator()), JOptionPane.INFORMATION_MESSAGE);
			
		});
		
		Main.log("[GUI.information] " + title + "\n\t" + content);
		
	}

	/**
	 * Ask user to do confirm something with <code>JOptionPane{@link #showConfirmDialog(String, String, JDialog)}</code>. <br>
	 * This method checks if current thread is EDT or not, so you don't have to check it or avoid thread deadlock manually.
	 * */
	public static boolean confirm(String title, String message) {

		confirmDialogParent.setAlwaysOnTop(true);
		
		if (EventQueue.isDispatchThread()) {

			return showConfirmDialog(title, message, confirmDialogParent);
			
		} else {
			
			final AtomicReference<Boolean> result = new AtomicReference<>();

			try {

				SwingUtilities.invokeAndWait(() -> {
					result.set(showConfirmDialog(title, message, confirmDialogParent));
				});
				
				return result.get();

			} catch (Exception e) {
				error("Exception in Thread working(SwingWorker)",
						e.getClass().getName() + "-%e%\nI'll consider you chose \"no\"", (e instanceof InvocationTargetException) ? (Exception)e.getCause() : e);
			}

			return false;

		}

	}
	
	private static boolean showConfirmDialog(String title, String message, JDialog dialog) {
		return JOptionPane.showConfirmDialog(dialog, message, title,JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION;
	} 
	
	private static void prepareLogFile() {
		
		logger = new Thread(() -> {

			while (true) {

				if (loggerQueue.isEmpty() && isStop) {
					return;
				}

				try {
					loggerQueue.take().run();
				} catch (InterruptedException e) {
					logTo.println("LoggerThread Interrupted! : " + e.getMessage());
				}
			}

		});
		
		try {
			
			File logFolder = new File(new File(".").getAbsoluteFile().getParent() + File.separator + "logs");
			File logFile = new File(logFolder.getAbsolutePath() + File.separator + "log-" + new SimpleDateFormat("yyyy-MM-dd-kk-mm-ss").format(new Date()) + ".txt");
			logFolder.mkdirs();
			logFile.createNewFile();
			
			logTo = new PrintWriter(new FileOutputStream(logFile), true);
			
		} catch (IOException e) {

			logTo = new PrintWriter(System.out, true);
			Main.error("Error when creating log flie", "%e%", e);
			
		} finally {
			logger.start();
		}
		
	}
	
	public static void log(String data) {

		loggerQueue.offer(() -> {
			logTo.println(data.replace("\n", System.lineSeparator()));
		});
		
	}
	
	public static void log(Exception e) {
		
		loggerQueue.offer(() -> {
			e.printStackTrace(logTo);
		});
		
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

		log("killed with exit code : " + i);
		System.exit(i);
		
	}

	private static void disposeAll() {
		if(frame != null) frame.dispose();
		if(confirmDialogParent != null) confirmDialogParent.dispose();
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
			return String.format("%.2f", length) + " byte";
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
	 * @param sendTo A <code>AsynchronousSocketChannel</code> to read from.
	 * @param buf <code>ByteBuffer</code> to store read bytes.
	 * @param whenClosed When peer disconnected, this method throws <code>new IOException(whenClosed)</code>.
	 * 
	 * @throws IOException 
	 * @throws ExecutionException 
	 * @throws InterruptedException 
	 * */
	public static void readFromChannel(AsynchronousSocketChannel sendTo, ByteBuffer buf, String whenClosed) throws IOException, InterruptedException, ExecutionException {
		int total = 0;
		int size = buf.remaining();
		while(true) {
			int l = sendTo.read(buf).get();
			if(l == -1) {
				sendTo.close();
				throw new IOException(whenClosed);
			}
			if((total += l) == size) return;
		}
	}

	public static ExecutorService getThreadPool() {
		return threadPool;
	}


}

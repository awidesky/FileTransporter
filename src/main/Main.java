package main;

import java.awt.EventQueue;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

import clientSide.ClientFrame;
import serverSide.ServerFrame;



public class Main {

	private static Thread logger;
	private static LinkedBlockingQueue<Runnable> loggerQueue = new LinkedBlockingQueue<>();
	private static boolean isStop = false;
	private static PrintWriter logTo;
	private static ExecutorService threadPool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
	public static long transferChunk;
	
	public static final String version = "v1.0.0";
	
	private static final JDialog confirmDialogParent = new JDialog();
	
	private static JFrame frame;
	
	public static void main(String[] args) {
		
		if(args.length == 0) {
			printUsageAndKill();
		}

		boolean isServer = false;
		
		for(int i = 0; i < args.length ; i++) {
			
			if(args[i].startsWith("--transferChunk=")) {
				String str = args[i].substring("--transferChunk=".length(), args[i].length());
				
				if(str.substring(str.length() - 1).equalsIgnoreCase("b")) { 
					Main.transferChunk = Integer.parseInt(str.substring(0, str.length() - 1));
				} else if(str.substring(str.length() - 2).equalsIgnoreCase("kb")) {  
					Main.transferChunk = Integer.parseInt(str.substring(0, str.length() - 2)) * 1024;
				} else if(str.substring(str.length() - 2).equalsIgnoreCase("mb")) { 
					Main.transferChunk = Integer.parseInt(str.substring(0, str.length() - 2)) * 1024 * 1024;
				} else if(str.substring(str.length() - 2).equalsIgnoreCase("gb")) {  
					Main.transferChunk = Integer.parseInt(str.substring(0, str.length() - 2)) * 1024 * 1024 * 1024;
				} else {
					System.out.println("invalid argument : " +  args[i]);
					printUsageAndKill();
				}
				
				Main.log("transferChunk = " + Main.transferChunk + "byte"); //log might queued but not be printed
				
			} else if (args[i].equals("--server")) {
				
				isServer = true;
			
			} else if (args[i].equals("--client")) {
				
				isServer = false;

			} else {
				System.out.println("invalid argument : " +  args[i]);
				printUsageAndKill();
			}
			
		}
		
		
		try {
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		} catch (ClassNotFoundException | InstantiationException | IllegalAccessException
				| UnsupportedLookAndFeelException e) {
			error("Error while setting window look&feel", "%e%", e);
		}
		
		prepareLogFile();
		
		if(isServer) {
			SwingUtilities.invokeLater(() -> frame = new ServerFrame());
		} else {
			SwingUtilities.invokeLater(() -> frame = new ClientFrame());
		}
	}
	
	public static void printUsageAndKill() {
		
		System.out.println("usage : java -jar FileTransporter.jar [option]");
		System.out.println();
		System.out.println("option :");
		System.out.printf("  %s\t%s%n", "--server","Run as server(sender)");
		System.out.printf("  %s\t%s%n", "--client","Run as client(receiver)");
		System.out.printf("  %s\t%s%n%s%n", "--transferChunk",	"Set size of a chunk to send at a time(affect to sending progress bar)",
																"Can used like \"--transferChunk=512B\" or \"--transferChunk=256mb\" (b/kb/mb/gb. case ignored)");
		kill(1);
		
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

			} catch (InterruptedException | InvocationTargetException e) {
				error("Exception in Thread working(SwingWorker)",
						e.getClass().getName() + "-%e%\nI'll consider you chose \"no\"", e);
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

		try {
			SwingUtilities.invokeAndWait(() -> {
				frame.dispose();
				confirmDialogParent.dispose();
			});
		} catch (InvocationTargetException | InterruptedException e) {
			e.printStackTrace();
		}
		
		log("killed with exit code : " + i);
		System.exit(i);
		
	}

	public static void queueJob(Runnable job) {

		threadPool.submit(job);
	}
	
	
}

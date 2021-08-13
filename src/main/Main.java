package main;

import java.awt.EventQueue;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.text.SimpleDateFormat;
import java.util.Date;
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
	
	public static final String version = "v1.0.0";
	
	private static final JDialog confirmDialogParent = new JDialog();
	
	private static JFrame frame;
	
	public static void main(String[] args) {
		
		if(args.length == 0) {
			printUsageAndKill();
		}
		
		try {
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		} catch (ClassNotFoundException | InstantiationException | IllegalAccessException
				| UnsupportedLookAndFeelException e) {
			error("Error while setting window look&feel", "%e%", e);
		}
		
		prepareLogFile();
		
		if(args[0].equals("--server")) {
		
			SwingUtilities.invokeLater(() -> frame = new ServerFrame());
		
		} else if(args[0].equals("--client")) {
			
			SwingUtilities.invokeLater(() -> frame = new ClientFrame());

		} else {
			printUsageAndKill();
		}

		
	}
	
	public static void printUsageAndKill() {
		
		System.out.println("usage : java -jar FileTransporter.jar [option]");
		System.out.println();
		System.out.println("option :");
		System.out.printf("  %s\t%s%n", "--server","Run as server(sender)");
		System.out.printf("  %s\t%s%n", "--client","Run as client(receiver)");
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
			JOptionPane.showMessageDialog(dialog, co, title, JOptionPane.ERROR_MESSAGE);
			
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
			JOptionPane.showMessageDialog(dialog, co, title, JOptionPane.WARNING_MESSAGE);
			
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
		// TODO Auto-generated method stub
		
	}
	
	
}

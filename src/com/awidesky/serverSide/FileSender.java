package com.awidesky.serverSide;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.util.concurrent.Future;

import com.awidesky.main.Main;
import com.awidesky.util.SwingDialogs;
import com.awidesky.util.TaskLogger;

public class FileSender implements Runnable {

	private int port;
	private File[] files;
	private Runnable resetCallback;
	private Future<?> future;
	private boolean aborted = false;
	private static String thisIP = null;

	private AsynchronousServerSocketChannel server = null;
	
	private TaskLogger logger;
	
	public FileSender(int port, File[] files, Runnable resetCallback, TaskLogger logger) {

		this.port = port;
		this.files = files;
		this.resetCallback = resetCallback;
		this.logger = logger;

	}

	public String getselfIP() {

		if(thisIP != null) return thisIP;
		
		try (BufferedReader in = new BufferedReader(
				new InputStreamReader(new URL("http://checkip.amazonaws.com").openStream()))) {

			return (thisIP = in.readLine());

		} catch (IOException e) {

			SwingDialogs.error("Can't get ip address of this computer!", "%e%", e, false);
			return "";

		}
	}

	@Override
	public void run() {

		try {
			
			server = AsynchronousServerSocketChannel.open(Main.channelGroup);

			server.bind(new InetSocketAddress(port));
			SwingDialogs.information("Server opened!", "Server is wating connection from " + getselfIP() + ":" + port, false);
			
			while (!Main.isAppStopped() && !future.isCancelled()) {

				logger.log("Server|Ready for connection...");
				Future<AsynchronousSocketChannel> fu = server.accept();
				SendingConnection sc = new SendingConnection(fu.get(), files);
				ClientListTableModel.getinstance().addConnection(sc);
				SwingDialogs.information("Connected to a Client!", "Connected to " + sc.getIP() + ":" + sc.getPort(), true);

				sc.setFuture(Main.queueJob(sc));

			}

			logger.log("Server stopped. closing server...");

		} catch (Exception e) {
			if(aborted)	SwingDialogs.information("Server is stopped!", "Server is stopped by user, or server thread was interrupted!\nException message : " + e.getMessage(), true);
			else SwingDialogs.error("Failed to connect!", "Failed to connect with an client!\n%e%", e, true);
		} finally {
			if(server != null) { 
				try {
					server.close();
					server = null;
				} catch (IOException e) {
					SwingDialogs.error("Failed to close server!", "%e%", e, true);
				}
			}
			
			resetCallback.run();
		}

	}

	public void disconnect() {
		aborted = true;
		future.cancel(true);
		if(server != null) {
			try {
				server.close();
			} catch (IOException e) {
				SwingDialogs.error("Failed to close server!", "%e%" , e, true);
			}
		}
	}

	public void setFuture(Future<?> future) {
		this.future = future;
	}
}

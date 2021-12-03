package serverSide;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.util.concurrent.Future;

import main.Main;

public class FileSender implements Runnable {

	private int port;
	private File[] files;
	private Future<?> future;
	private boolean aborted = false;
	private static String thisIP = null;

	private AsynchronousServerSocketChannel server = null;
	
	public FileSender(int port, File[] files) {

		this.port = port;
		this.files = files;

	}

	public String getselfIP() {

		if(thisIP != null) return thisIP;
		
		try (BufferedReader in = new BufferedReader(
				new InputStreamReader(new URL("http://checkip.amazonaws.com").openStream()))) {

			return (thisIP = in.readLine());

		} catch (IOException e) {

			Main.error("Can't get ip address of this computer!", "%e%", e);
			return "";

		}
	}

	@Override
	public void run() {

		try {
			
			server = AsynchronousServerSocketChannel.open(Main.channelGroup);

			server.bind(new InetSocketAddress(port));
			Main.information("Server opened!", "Server is wating connection from " + getselfIP() + ":" + port);
			
			while (!Main.isAppStopped() && !future.isCancelled()) {

				Main.log("Server|Ready for connection...");
				Future<AsynchronousSocketChannel> fu = server.accept();
				SendingConnection sc = new SendingConnection(fu.get(), files);
				ClientListTableModel.getinstance().addConnection(sc);
				Main.information("Connected to a client!", "Connected to " + sc.getIP() + ":" + sc.getPort());

				sc.setFuture(Main.queueJob(sc));

			}

			Main.log("Server stopped. closing server...");

		} catch (Exception e) {
			if(aborted)	Main.information("Server is stopped!", "Server is stopped by user, or server thread was interrupted!\nException message : " + e.getMessage());
			else Main.error("Failed to connect!", "Failed to connect with an client!\n%e%", e);
		} finally {
			if(server != null) { 
				try {
					server.close();
				} catch (IOException e) {
					Main.error("Failed to close server!", "%e%", e);
				}
			}
		}

	}

	public void disconnect() {
		aborted = true;
		future.cancel(true);
		if(server != null) {
			try {
				server.close();
			} catch (IOException e) {
				Main.error("Failed to close server!", "%e%" , e);
			}
		}
	}

	public void setFuture(Future<?> future) {
		this.future = future;
	}
}

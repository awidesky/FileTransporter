package io.github.awidesky.serverSide;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.stream.Stream;

import io.github.awidesky.Main;
import io.github.awidesky.guiUtil.SwingDialogs;
import io.github.awidesky.guiUtil.TaskLogger;

public class Server implements Runnable {

	private int port;
	private File[] files;
	private Runnable resetCallback;
	private Future<?> future;
	private boolean aborted = false;
	private static String thisIP = null;

	private ServerSocketChannel server = null;
	
	private ConcurrentHashMap<UUID, ConnectedClient> clients = new ConcurrentHashMap<>();
	
	private TaskLogger logger;
	
	public Server(int port, File[] files, Runnable resetCallback, TaskLogger logger) {

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
			server = ServerSocketChannel.open();

			server.bind(new InetSocketAddress(port));
			SwingDialogs.information("Server opened!", "Server is wating connection from " + getselfIP() + ":" + port, false);
			
			while (!Main.isAppStopped() && !future.isCancelled()) {
				logger.info("Server|Ready for connection...");
				
				ClientConnection sc = connectClient(server.accept());
				if(sc == null) continue;
				
				ClientListTableModel.getinstance().addConnection(sc); //TODO : use tabbedpane per client
				sc.setFuture(Main.queueJob(sc));
			}

			logger.info("Server stopped. closing server...");

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

	private ClientConnection connectClient(SocketChannel accepted) {
		try {
			InetSocketAddress remotAddress = (InetSocketAddress)accepted.getRemoteAddress();
			ByteBuffer buf = ByteBuffer.allocate(16);

			logger.info("Accepted Conection : " + remotAddress);
			logger.info("Recieving UUID...");
			while(buf.hasRemaining()) accepted.read(buf);
			long[] bits = new long[2];
			buf.flip().asLongBuffer().get(bits);
			
			UUID uu;
			if(bits[0] == 0 && bits[1] == 0) {
				uu = Stream.generate(UUID::randomUUID)
						.filter(u -> !clients.keySet().contains(u))
						.filter(u -> u.getMostSignificantBits() != 0 || u.getLeastSignificantBits() != 0)
						.findFirst().get();
				logger.info("Empty UUID, send a new one : " + uu);

				SwingDialogs.information("Connected to a Client!", "Connection from " + remotAddress + ", UUID : " + uu, false); //TODO : confirm?
				
				buf.clear().asLongBuffer().put(uu.getMostSignificantBits()).put(uu.getLeastSignificantBits()).flip();
				while(buf.hasRemaining()) accepted.write(buf);
				
				clients.put(uu, new ConnectedClient(uu, files));
				logger.info("UUID sent. close connection..."); //TODO : additional metadata like maximum connections?
				accepted.close();
				return null;
			} else {
				uu = new UUID(bits[0], bits[1]);
				logger.info("Recieved client UUID : " + uu + ", exist : " + clients.contains(uu)); //TODO : if false, NullPointerException...
			}

			ConnectedClient client = clients.computeIfAbsent(uu, u -> new ConnectedClient(u, files));
			return client.addChannel(accepted, remotAddress);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null; //TODO : null
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

package io.github.awidesky.clientSide;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import javax.swing.SwingUtilities;

import io.github.awidesky.Main;
import io.github.awidesky.guiUtil.Logger;
import io.github.awidesky.guiUtil.SwingDialogs;

public class FileReciever {

	private static UUID uuid = null;
	private static List<ServerConnection> connList = new LinkedList<>();

	private static Logger logger = Main.getLogger("[FileReciever] ");
	private static Runnable resetCallback;

	public static void startConnections(int n, String ip, int port, File destination, Runnable resetCallback) {
		FileReciever.resetCallback = resetCallback;
		
		InetSocketAddress addr = new InetSocketAddress(ip, port);
		try (SocketChannel ch = SocketChannel.open()) {
			logger.info("Connecting to : " + addr.toString());
			ch.connect(addr);
			InetSocketAddress remoteAddress = (InetSocketAddress) ch.getRemoteAddress();
			logger.info("Connected to : " + remoteAddress);
			SwingDialogs.information("Connected to the Server!", "Connected to " + remoteAddress.getHostString() + ":" + remoteAddress.getPort(), true);
			
			sendUUID(ch);
			
			logger.info("Identification established. closing main connection and generate transfer connections...");
		} catch (IOException e) {
			SwingDialogs.error("Failed to connect to Server", "Cannot connect to " + addr.toString() + "\n%e%", e, true);
			return;
		}
		
		for(int i = 1; i <= n; i++) {
			ServerConnection fr = new ServerConnection(i, ip, port, destination);
			connList.add(fr);
			fr.setFuture(Main.queueJob(fr));
		}
	}
	
	public static void disconnectAll() {
		connList.forEach(ServerConnection::disconnect);
		uuid = null;
		SwingUtilities.invokeLater(resetCallback);
	}

	
	public static void sendUUID(SocketChannel ch) throws IOException {
		ByteBuffer buf = ByteBuffer.allocate(16);

		if(uuid == null) {
			logger.info("Send empty UUID for identification...");
			buf.asLongBuffer().put(0).put(0).flip();
		} else {
			logger.info("Send existing UUID : " + uuid);
			buf.asLongBuffer().put(uuid.getMostSignificantBits()).put(uuid.getLeastSignificantBits()).flip();
		}
		
		while(buf.hasRemaining()) ch.write(buf);

		if(uuid == null) {
			buf.clear();

			while(buf.hasRemaining()) ch.read(buf);
			long[] bits = new long[2];
			buf.flip().asLongBuffer().get(bits);
			FileReciever.uuid = new UUID(bits[0], bits[1]);
			logger.info("Recieved new UUID : " + uuid);
		}
	}

}

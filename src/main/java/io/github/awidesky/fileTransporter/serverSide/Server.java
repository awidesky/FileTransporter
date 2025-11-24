package io.github.awidesky.fileTransporter.serverSide;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.CharBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.stream.Stream;

import io.github.awidesky.fileTransporter.Main;
import io.github.awidesky.fileTransporter.serverSide.connection.CipherClientConnection;
import io.github.awidesky.fileTransporter.serverSide.connection.ClientConnection;
import io.github.awidesky.fileTransporter.serverSide.connection.PlainConnection;
import io.github.awidesky.guiUtil.SwingDialogs;
import io.github.awidesky.guiUtil.TaskLogger;
import io.github.awidesky.jCipherUtil.cipher.symmetric.SymmetricCipherUtil;
import io.github.awidesky.jCipherUtil.cipher.symmetric.aes.AESKeySize;
import io.github.awidesky.jCipherUtil.cipher.symmetric.aes.AES_GCMCipherUtil;
import io.github.awidesky.jCipherUtil.key.keyExchange.EllipticCurveKeyExchanger;
import io.github.awidesky.jCipherUtil.key.keyExchange.ecdh.ECDHCurves;
import io.github.awidesky.jCipherUtil.key.keyExchange.ecdh.ECDHKeyExchanger;
import io.github.awidesky.jCipherUtil.messageInterface.InPut;

public class Server implements Runnable {

	private int port;
	private ServerFrame frame;
	private Future<?> future;
	private boolean aborted = false;
	private static String thisIP = null;

	private ServerSocketChannel server = null;
	
	private ConcurrentHashMap<UUID, ConnectedClient> clients = new ConcurrentHashMap<>();
	
	private final boolean encrypted;
	private final boolean sendHash;
	private final char[] password;
	
	private final EllipticCurveKeyExchanger k = new ECDHKeyExchanger(ECDHCurves.secp521r1);
	private final ByteBuffer pkLenBuf = ByteBuffer.allocate(Integer.BYTES);
	
	private TaskLogger logger;
	
	public Server(int port, ServerFrame serverFrame, TaskLogger logger, boolean sendHash, boolean encrypted, char[] password) {
		this.port = port;
		this.frame = serverFrame;
		this.logger = logger;
		this.sendHash = sendHash;
		this.encrypted = encrypted;
		this.password = password;
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
				logger.info("Ready for connection...");
				
				ClientConnection sc = connectClient(server.accept());
				if(sc == null) continue;
				sc.setFuture(Main.queueJob(sc));
			}

			logger.info("Server stopped. closing server...");

		} catch (Exception e) { //TODO : find out if interrupted
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
			
			frame.resetGUI(true);
		}

	}

	private ClientConnection connectClient(SocketChannel accepted) {
		try {
			InetSocketAddress remotAddress = (InetSocketAddress)accepted.getRemoteAddress();
			logger.info("Accepted Conection : " + remotAddress);
			
			SymmetricCipherUtil cipher = exchangeKey(accepted);
			
			if(password != null && !checkPassword(accepted, cipher)) return null;
			
			logger.info("Recieving UUID...");
			long[] bits = new long[2];
			ByteBuffer.wrap(receiveSecurePacket(accepted, cipher)).flip().asLongBuffer().get(bits);
			
			UUID uu;
			if(bits[0] == 0 && bits[1] == 0) { //TODO : security danger?
				uu = Stream.generate(UUID::randomUUID)
						.filter(u -> !clients.keySet().contains(u))
						.filter(u -> u.getMostSignificantBits() != 0 || u.getLeastSignificantBits() != 0)
						.findFirst().get();
				logger.info("Empty UUID, send a new one : " + uu);

				SwingDialogs.information("Connected to a Client!", "Connection from " + remotAddress + ", UUID : " + uu, false); //TODO : confirm?
				
				sendSecurePacket(accepted, cipher,
						ByteBuffer.allocate(16).putLong(uu.getMostSignificantBits()).putLong(uu.getLeastSignificantBits()).flip());
				
				ConnectedClient c = new ConnectedClient(uu);
				clients.put(uu, c);
				frame.addClient(c);
				logger.info("UUID sent. close connection..."); //TODO : additional metadata like maximum connections?
				accepted.close();
				return null;
			} else {
				uu = new UUID(bits[0], bits[1]);
				logger.info("Recieved client UUID : " + uu + ", exist : " + clients.contains(uu)); //TODO : if false, NullPointerException...
			}

			ConnectedClient client = clients.computeIfAbsent(uu, ConnectedClient::new);
			ClientConnection connection;
			if(encrypted) {
				connection = new CipherClientConnection(cipher);
			} else {
				connection = new PlainConnection();
			}
			connection.init(accepted, remotAddress, uu.toString().substring(0, 8), sendHash);
			client.addConnection(connection);
			return connection;
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null; //TODO : null
		}
	}

	private SymmetricCipherUtil exchangeKey(SocketChannel accepted) throws IOException {
		ByteBuffer pkBuf = ByteBuffer.wrap(k.init().getEncoded());
		pkLenBuf.clear().putInt(pkBuf.limit()).flip();
		logger.debug("ECDH public key size : " + pkBuf.limit());
		logger.debug("Send ECDH public key : " + Base64.getEncoder().encodeToString(pkBuf.array()));
		while(pkLenBuf.hasRemaining()) accepted.write(pkLenBuf);
		while(pkBuf.hasRemaining()) accepted.write(pkBuf);
		logger.debug("ECDH public key sent!");
		
		pkLenBuf.clear();
		pkBuf.clear();
		while(pkLenBuf.hasRemaining()) accepted.read(pkLenBuf);
		int pkBufsize = pkLenBuf.flip().getInt();
		logger.debug("Peer ECDH public key size : " + pkBufsize);
		if(pkBufsize > pkBuf.capacity()) pkBuf = ByteBuffer.allocate(pkBufsize);
		pkBuf.limit(pkBufsize);
		while(pkBuf.hasRemaining()) accepted.read(pkBuf);
		byte[] otherPk = new byte[pkBuf.flip().limit()];
		pkBuf.get(otherPk);
		logger.debug("ECDH public key received : " + Base64.getEncoder().encodeToString(otherPk));
		return new AES_GCMCipherUtil.Builder(AESKeySize.SIZE_256).build(k.exchangeKey(k.decodePublicKey(otherPk)));
	}

	private boolean checkPassword(SocketChannel accepted, SymmetricCipherUtil cipher) throws IOException {
		CharBuffer charBuffer = ByteBuffer.wrap(receiveSecurePacket(accepted, cipher)).order(ByteOrder.BIG_ENDIAN)
				.asCharBuffer();
		char[] ps = new char[charBuffer.remaining()];
		charBuffer.get(ps);
		boolean ret = Arrays.equals(ps, password);
		logger.info("Password check : " + ret);
		for (int i = 0; i < ps.length; i++) ps[i] = '\0';
		charBuffer.clear().put(ps);
		return ret;
	}
	
	ByteBuffer securePacketLen = ByteBuffer.allocate(Integer.BYTES);
	private void sendSecurePacket(SocketChannel accepted, SymmetricCipherUtil cipher, ByteBuffer buf) throws IOException {
		byte[] b = new byte[buf.remaining()];
		buf.get(b);
		b = cipher.encryptToSingleBuffer(InPut.from(b));
		securePacketLen.clear().putInt(b.length).flip();
		while(securePacketLen.hasRemaining()) accepted.write(securePacketLen);

		buf = ByteBuffer.wrap(b);
		while(buf.hasRemaining()) accepted.write(buf);
	}
	private byte[] receiveSecurePacket(SocketChannel accepted, SymmetricCipherUtil cipher) throws IOException {
		while(securePacketLen.hasRemaining()) accepted.read(securePacketLen);
		int len = securePacketLen.flip().getInt();
		ByteBuffer b = ByteBuffer.allocate(len);
		while(b.hasRemaining()) accepted.read(b);
		return cipher.decryptToSingleBuffer(InPut.from(b.array()));
	}

	public boolean disconnect() {
		if(!clients.values().stream().allMatch(ConnectedClient::disconnect))
			return false;
		
		aborted = true;
		future.cancel(true);
		if(server != null) {
			try {
				server.close();
			} catch (IOException e) {
				SwingDialogs.error("Failed to close server!", "%e%" , e, true);
			}
		}
		return true;
	}

	public void setFuture(Future<?> future) {
		this.future = future;
	}

	public boolean isAllConncectionCompleted() {
		return clients.values().stream().allMatch(ConnectedClient::isCompleted);
	}
}

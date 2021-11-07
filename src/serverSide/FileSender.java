package serverSide;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.channels.ServerSocketChannel;
import java.util.concurrent.Future;

import main.Main;

public class FileSender implements Runnable {

	private int port;
	private File[] files;

	public FileSender(int port, File[] files) {

		this.port = port;
		this.files = files;

	}

	public String getselfIP() {

		try (BufferedReader in = new BufferedReader(
				new InputStreamReader(new URL("http://checkip.amazonaws.com").openStream()))) {

			return in.readLine();

		} catch (IOException e) {

			Main.error("Can't get ip address of this computer!", "%e%", e);
			return "";

		}
	}

	@Override
	public void run() {

		try (ServerSocketChannel server = ServerSocketChannel.open()) {

			server.bind(new InetSocketAddress(port));

			while (!Main.isAppStopped()) {

				Main.log("Ready for connection...");

				try {
					SendingConnection sc = new SendingConnection(server.accept(), files);
					ClientListTableModel.getinstance().addConnection(sc);

					Future<?> f = Main.queueJob(sc);
					sc.setFuture(f);
				} catch (IOException e) {
					Main.error("Failed to connect!", "Failed to connect with an client!", e);
				}

			}

			Main.log("Server stopped. closing server...");

		} catch (IOException e) {
			e.printStackTrace();
		}

	}
}

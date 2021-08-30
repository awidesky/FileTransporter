package serverSide;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Arrays;

import main.Main;

public class FileSender implements Runnable {

	private int[] port;
	private File[] files;
	
	public FileSender(String ports, File[] f) {

		port = Arrays.stream(ports.split(":")).mapToInt(Integer::parseInt).toArray();
		files = f;
		
		
		// TODO
		// SendingConnection 배열 초기화
	}

	public String getIP() {
		
		try (BufferedReader in = new BufferedReader(new InputStreamReader(new URL("http://checkip.amazonaws.com").openStream()));) {
			
			return in.readLine();
			
		} catch (IOException e) {
			
			Main.error("Can't get ip address of this computer!", "%e%", e);
			return "";
			
		}
	}

	public String getNOwSendingFile(SendingConnection what) {
		// TODO Auto-generated method stub
		return null;
	}

	public int getProgress(SendingConnection what) {
		// TODO Auto-generated method stub
		return 0;
	}

	public boolean isFinished(SendingConnection what) {
		// TODO Auto-generated method stub
		return false;
	}

	public void disconnect(SendingConnection what) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void run() {
		// TODO Auto-generated method stub
		//connection Listening 코드
	}
}

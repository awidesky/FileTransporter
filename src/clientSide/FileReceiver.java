package clientSide;

public class FileReceiver implements Runnable{

	
	private int progress;
	private boolean isDone = false;
	
	public FileReceiver(String ip, String port) {
		// TODO Auto-generated constructor stub
		progress = 0;
		
	}
	
	
	public boolean isFinished() {
		return isDone;
	}

	public int getProgress() {
		return progress;
	}

	public void setProgress(int status) {
		this.progress = status;
	}


	public String getDest() {
		// TODO Auto-generated method stub
		return null;
	}

	public void disconnect() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void run() {
		// TODO Auto-generated method stub
		// ø¨∞·¥Ú¿∏∏È "FileTransporter client " + Main.version + ip, port
	}

}

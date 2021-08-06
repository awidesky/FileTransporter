package clientSide;

public class ReceiveData {

	private FileReceiver fr;
	private int progress;
	private boolean isDone = false;
	
	public ReceiveData(FileReceiver freceiver) {

		fr = freceiver;
		setProgress(0);
		
	}
	
	public void done() {
		isDone = true;
	}
	
	public boolean isFinished() {
		return isDone;
	}

	public String getDest() {
		return fr.getDest();
	}

	public int getProgress() {
		return progress;
	}

	public void setProgress(int status) {
		this.progress = status;
	}

	public void disconnect() {

		fr.disconnect();
		
	}
	
}

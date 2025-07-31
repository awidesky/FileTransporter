package io.github.awidesky.clientSide;

import io.github.awidesky.Main;

public class DonwloadingStatus {

	private String status = ""; 
	private String dest;
	private int progress;
	
	private ServerConnection fileReceiver;
	private long fileLength;
	private long transferred;
	
	public DonwloadingStatus(String destination, long length, ServerConnection fileReceiver) {
		this.dest = destination;
		this.fileLength = length;
		this.progress = 0;
		this.fileReceiver = fileReceiver;
	}
	
	public String getDest() {
		return dest;
	}
	
	public int getProgress() {
		return progress;
	}

	public void setProgress(int progress) {
		this.progress = progress;
	}

	public ServerConnection getFileReceiver() {
		return fileReceiver;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public boolean isFinished() {
		return progress == 100;
	}

	public String getProgressString() {
		return progress + "% (" + Main.formatFileSize(transferred) + " / " +  Main.formatFileSize(fileLength) + ")";
	}
	
	public String getStaus() {
		
		if(status.equals("Downloading...")) {
			return status + " (" + progress + "%)";
		}
		return status;
	}

}

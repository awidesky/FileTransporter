package io.github.awidesky.serverSide;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

import javax.swing.SwingUtilities;
import javax.swing.table.AbstractTableModel;

import io.github.awidesky.guiUtil.SwingDialogs;

public class ClientListTableModel extends AbstractTableModel {

	private static final long serialVersionUID = 3855477049816688267L;
	
	public class FileProgress {
		private final SelectedFile file;
		private int progress;
		private String progressString;
		private String status;
		
		public FileProgress(SelectedFile f) {
			this.file = f;
			this.setProgress(0);
			this.setProgressString("0%");
			this.setStatus("");
		}
		
		public String getStatus() {
			return status;
		}
		public void setStatus(String status) {
			this.status = status;
			updated(this); //TODO : if performance bottleneck on table update, pre-calculate indexOf(this) in constructor
		}
		public String getProgressString() {
			return progressString;
		}
		public void setProgressString(String progressString) {
			this.progressString = progressString;
			updated(this);
		}
		public int getProgress() {
			return progress;
		}
		public void setProgress(int progress) {
			this.progress = progress;
			updated(this);
		}
		public File getFile() {
			return file.actual();
		}
		public String getRelativePath() {
			return file.relative();
		}
	}
	
	private List<FileProgress> rows = new ArrayList<>();
	private ConcurrentLinkedQueue<FileProgress> fileQueue;
	private ConnectedClient client;
	
	public ClientListTableModel(List<SelectedFile> list, ConnectedClient client) {
		this.client = client;
		list.stream().map(FileProgress::new).forEach(rows::add);
		fileQueue = new ConcurrentLinkedQueue<>(rows);
		fireTableDataChanged();
	}
	
	public ConcurrentLinkedQueue<FileProgress> getFileQueue() {
		return fileQueue;
	}
	
	public ConnectedClient getClient() {
		return client;
	}

	@Override
	public int getRowCount() {
		return rows.size();
	}
	
	@Override
	public int getColumnCount() {
		return 3;
	}

	@Override
	public String getColumnName(int columnIndex) {
		if(columnIndex == 0)
			return "Status";
		else if(columnIndex == 1)
			return "File";
		else if(columnIndex == 2)
			return "Progress";
		else {
			SwingDialogs.error("Invalid column index!", "Invalid column index in UploadListTableModel : " + columnIndex, null, false);
			return "null"; // this should not happen!
		}
	}


	@Override
	public Object getValueAt(int rowIndex, int columnIndex) { 
		
		switch (columnIndex) {
		case 0: // Status
			return rows.get(rowIndex).getStatus();
		case 1: // File
			return rows.get(rowIndex).getFile().getName();
		case 2: // Progress
			return rows.get(rowIndex).getProgress(); //TODO: was getNowSendingFileString
		}
		
		SwingDialogs.error("Invalid column index!", "Invalid column index in DownloadingListTableModel : " + columnIndex, null, false);
		return null; // this should not happen!
	}



	public void clearDone() {
		rows.removeIf(p -> p.getProgress() == 100);
		fireTableDataChanged();
	}

	void updated(FileProgress fp) {
		int idx = rows.indexOf(fp);
		SwingUtilities.invokeLater(() -> { fireTableRowsUpdated(idx, idx); });
	}
	
	public List<FileProgress> getData() {
		return rows;
	}

	public FileProgress poll() {
		return fileQueue.poll();
	}
	
}

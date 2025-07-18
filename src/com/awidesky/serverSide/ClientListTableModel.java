package com.awidesky.serverSide;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import javax.swing.SwingUtilities;
import javax.swing.table.AbstractTableModel;

import com.awidesky.util.SwingDialogs;

public class ClientListTableModel extends AbstractTableModel {


	private static final long serialVersionUID = 3855477049816688267L;
	private final static ClientListTableModel instance = new ClientListTableModel();
	private List<ClientConnection> rows = new ArrayList<>(); 
	
	private ClientListTableModel() {}

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
			return "Client";
		else if(columnIndex == 1)
			return "Now sending...";
		else if (columnIndex == 2) {
			return "Progress";
		} else {
			SwingDialogs.error("Invalid column index!", "Invalid column index in UploadListTableModel : " + columnIndex, null, false);
			return "null"; // this should not happen!
		}
		
	}


	@Override
	public Object getValueAt(int rowIndex, int columnIndex) { 
		
		switch (columnIndex) {
		case 0: // Client
			return rows.get(rowIndex).getIP();
		case 1: // Now sending...
			return rows.get(rowIndex).getNowSendingFile(); //TODO: was getNowSendingFileString
		case 2: // Progress
			return rows.get(rowIndex).getProgress();
		}
		
		SwingDialogs.error("Invalid column index!", "Invalid column index in DownloadingListTableModel : " + columnIndex, null, false);
		return null; // this should not happen!
	}



	public void clearDone() {

		rows.removeIf((r) -> r.isFinished());
		fireTableDataChanged();

	}
	
	public void disconectSelected(int[] selected) {

		for (int r : selected) {
			if (rows.get(r).isFinished()) { 
				continue;
			} else {
				if (SwingDialogs.confirm("Before clearing!", "Some task(s) are not done!\nDisconnect connection(s)?")) {
					break;
				} else { return; }
			}
		}
		
		LinkedList<ClientConnection> temp = new LinkedList<>();
		for (int r : selected) temp.add(rows.get(r));
		rows.removeAll(temp);
		
		fireTableDataChanged();
			
	}

	
	/**
	 * @return if user agreed to disconnect or all work queued were done.
	 * */
	public boolean clearAll() {

		if(rows.isEmpty()) return true;
		
		rows.removeIf(ClientConnection::isFinished);
		
		if (!rows.isEmpty()) {
			if (!SwingDialogs.confirm("Before clearing!",
					"Some task(s) are not done!\nDisconnect all connection(s) and clear list?"))
				return false;

			rows.forEach((s) -> {
				s.disconnect();
			});
		}
		
		rows.clear();
		fireTableDataChanged();
		return true;

	}

	public void addConnection(ClientConnection r) {

		SwingUtilities.invokeLater(() -> {
			rows.add(r);
			fireTableRowsInserted(rows.size() - 1, rows.size() - 1);
		});

	}

	public void updated(ClientConnection r) {

		SwingUtilities.invokeLater(() -> { fireTableRowsUpdated(rows.indexOf(r), rows.indexOf(r)); });

	}
	
	public List<ClientConnection> getData() {
		return rows;
	}
	
	public static ClientListTableModel getinstance() {
		return instance;
	}	

}

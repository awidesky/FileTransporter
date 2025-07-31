package io.github.awidesky.clientSide;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import javax.swing.SwingUtilities;
import javax.swing.table.AbstractTableModel;

import io.github.awidesky.guiUtil.SwingDialogs;


public class DownloadingListTableModel extends AbstractTableModel {

	/**
	 * 
	 */
	private static final long serialVersionUID = -1301962023940227000L;
	
	private static DownloadingListTableModel instance = new DownloadingListTableModel();
	private List<DonwloadingStatus> rows = new ArrayList<>();
	
	private DownloadingListTableModel() {}

	@Override
	public int getRowCount() {
		return rows.size();
	}

	@Override
	public int getColumnCount() {
		return 2;
	}

	@Override
	public String getColumnName(int columnIndex) {
		
		if(columnIndex == 0)
			return "Destination";
		else if(columnIndex == 1)
			return "Progress";
		else {
			SwingDialogs.error("Invalid column index!", "Invalid column index in DownloadingListTableModel : " + columnIndex, null, false);
			return "null"; // this should not happen!
		}
		
	}


	@Override
	public Object getValueAt(int rowIndex, int columnIndex) {
		
		switch (columnIndex) {
		case 0: // destination
			return rows.get(rowIndex).getDest();
		case 1: // Progress
			return rows.get(rowIndex).getProgress();
		}
		
		SwingDialogs.error("Invalid column index!", "Invalid column index in DownloadingListTableModel : " + columnIndex, null, false);
		return null; // this should not happen!
	}



	public void clearDone() {
		if(!SwingUtilities.isEventDispatchThread()) {
			SwingUtilities.invokeLater(this::clearDone);
			return;
		}
		
		rows.removeIf(DonwloadingStatus::isFinished);
		fireTableDataChanged();
	}
	
	public void disconectSelected(int[] selected) {
		if(!SwingUtilities.isEventDispatchThread()) {
			SwingUtilities.invokeLater(() -> disconectSelected(selected));
			return;
		}
		
		for (int r : selected) {
			if (rows.get(r).isFinished()) { 
				continue;
			} else {
				if (SwingDialogs.confirm("Before clearing!", "Some task(s) are not done!\nDisconnect connection(s)?")) {
					break;
				} else { return; }
			}
		}
		
		LinkedList<DonwloadingStatus> temp = new LinkedList<>();
		for (int r : selected) temp.add(rows.get(r));
		rows.removeAll(temp);
		
		fireTableDataChanged();
			
	}

	
	/**
	 * @return if user agreed to disconnect or all work queued were done.
	 * */
	public boolean clearAll() {
		if(rows.isEmpty()) return true;
		
		rows.removeIf(DonwloadingStatus::isFinished);

		if (!rows.isEmpty()) {
			if (!SwingDialogs.confirm("Before clearing!",
					"Some task(s) are not done!\nDisconnect all connection(s) and clear list?"))
				return false;

			rows.forEach((r) -> {
				r.getFileReceiver().disconnect(); //TODO : threading?
			});
		}
		
		rows.clear();
		fireTableDataChanged();
		return true;

	}

	public void addTask(DonwloadingStatus r) {
		SwingUtilities.invokeLater(() -> {
			rows.add(r);
			fireTableRowsInserted(rows.size() - 1, rows.size() - 1);
		});
	}


	public List<DonwloadingStatus> getData() {
		return rows;
	}

	public void updated(DonwloadingStatus r) {
		SwingUtilities.invokeLater(() -> { fireTableRowsUpdated(rows.indexOf(r), rows.indexOf(r)); });
	}
	public static DownloadingListTableModel getinstance() {

		return instance ;
		
	}

}

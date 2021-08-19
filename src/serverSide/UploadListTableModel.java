package serverSide;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import javax.swing.SwingUtilities;
import javax.swing.table.AbstractTableModel;

import main.Main;

public class UploadListTableModel extends AbstractTableModel {


	private static final long serialVersionUID = -2134183294768869129L;

	private final static UploadListTableModel instance = new UploadListTableModel();

	private List<File> rows = new ArrayList<>();
	
	private UploadListTableModel() {}

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
			return "File";
		else if(columnIndex == 1)
			return "Size";
		else {
			Main.error("Invalid column index!", "Invalid column index in UploadListTableModel : " + columnIndex, null);
			return "null"; // this should not happen!
		}
		
	}


	@Override
	public Object getValueAt(int rowIndex, int columnIndex) {
		
		switch (columnIndex) {
		case 0: // File
			return rows.get(rowIndex).getName();
			
		case 1: // Size
			
			long size = rows.get(rowIndex).length();
			long loged = (long) Math.log(size);
			if (loged < 4) {
				return size + " byte";
			} else if (loged < 7) {
				return size / 1024 + " KB";
			} else if (loged < 10) {
				return size / (1024 * 1024) + " MB";
			} else {
				return size / (1024 * 1024 * 1024) + " GB";
			}
		}
		
		Main.error("Invalid column index!", "Invalid column index : " + columnIndex, null);
		return null; // this should not happen!
	}

	
	public void deleteSelected(int[] selected) {
		
		LinkedList<File> temp = new LinkedList<>();
		for (int r : selected) temp.add(rows.get(r));
		rows.removeAll(temp);
		
		fireTableDataChanged();
			
	}


	public void addFile(File f) {

		SwingUtilities.invokeLater(() -> {
			rows.add(f);
			fireTableRowsInserted(rows.size() - 1, rows.size() - 1);
		});

	}

	public List<File> getData() {
		return rows;
	}

	public static UploadListTableModel getinstance() {
		return instance;
	}

}

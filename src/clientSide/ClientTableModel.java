package clientSide;

import java.util.ArrayList;
import java.util.List;

import javax.swing.SwingUtilities;
import javax.swing.table.AbstractTableModel;

import main.Main;


public class ClientTableModel extends AbstractTableModel {

	/**
	 * 
	 */
	private static final long serialVersionUID = -1301962023940227000L;
	
	private static ClientTableModel instance = new ClientTableModel();
	private List<ReceiveData> rows = new ArrayList<>();
	
	private ClientTableModel() {}

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
			Main.error("Invalid column index!", "Invalid column index : " + columnIndex, null);
			return "null"; // this should not happen!
		}
		
	}


	@Override
	public Object getValueAt(int rowIndex, int columnIndex) {
		
		switch (columnIndex) {
		case 0: // destination
			return rows.get(rowIndex).getDest();
		case 1: // status
			return rows.get(rowIndex).getProgress();
		}
		
		Main.error("Invalid column index!", "Invalid column index : " + columnIndex, null);
		return null; // this should not happen!
	}



	public void clearDone() {

		rows.removeIf((r) -> r.isFinished());
		fireTableDataChanged();

	}
	
	public void disconectSelected(int row) {
		
		if(!rows.get(row).isFinished() && !Main.confirm("Before clearing!", "This task is not done!\nDisconnect this connection?")) {
			return;
		}
		
		rows.remove(row);
		fireTableDataChanged();

	}

	
	/**
	 * @return if user agreed to disconnect or all work queued were done.
	 * */
	public boolean clearAll() {

		rows.removeIf(ReceiveData::isFinished);
		
		if (rows.isEmpty() || !Main.confirm("Before clearing!", "Some task(s) are not done!\nDisconnect all connection(s) and clear list?"))
				return false;

		rows.forEach((r) -> {
			r.disconnect();
		});
		rows.clear();
		fireTableDataChanged();
		return true;

	}

	public void updated(ReceiveData r) {

		fireTableRowsUpdated(rows.indexOf(r), rows.indexOf(r));

	}

	public void addTask(ReceiveData r) {

		SwingUtilities.invokeLater(() -> {
			rows.add(r);
			fireTableRowsInserted(rows.size() - 1, rows.size() - 1);
		});

	}



	public static ClientTableModel getinstance() {

		return instance ;
		
	}

}

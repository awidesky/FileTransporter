package serverSide;

import javax.swing.table.AbstractTableModel;

import clientSide.ClientTableModel;

public class ServerSendTableModel extends AbstractTableModel {

	private final static ServerSendTableModel instance = new ServerSendTableModel();

	@Override
	public int getRowCount() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int getColumnCount() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public Object getValueAt(int rowIndex, int columnIndex) {
		// TODO Auto-generated method stub
		return null;
	}

	public static ServerSendTableModel getinstance() {
		return instance;
	}

	public boolean clearAll() {
		// TODO Auto-generated method stub
		return false;
	}

}

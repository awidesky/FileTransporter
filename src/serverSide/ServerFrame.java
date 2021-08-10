package serverSide;

import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;

import main.Main;
import main.ProgressRenderer;

public class ServerFrame extends JFrame {

	private JLabel ip = new JLabel("IP : ");
	private JLabel port = new JLabel("Port : ");
	private JLabel total= new JLabel("Total : ");

	private JTextField ip_t = new JTextField();
	private JTextField port_t = new JTextField();
	
	private JButton start = new JButton("start server");
	private JButton cleanCompleted = new JButton("clean completed");
	private JButton clearAll = new JButton("clear all");
	private JButton deleteSelected = new JButton("delete selected");
	private JButton addFile = new JButton("add file...");
	
	private JTable fileListTable = new JTable() {
		
		private static final long serialVersionUID = -4271449717757183126L;

		@Override
		public String getToolTipText(MouseEvent e) { 
			int row = rowAtPoint(e.getPoint());
			int column = columnAtPoint(e.getPoint());
			if (row == -1) return "";
			if (column == 0) return UploadListTableModel.getinstance().getData().get(row).getAbsolutePath();
			else return UploadListTableModel.getinstance().getData().get(row).length() + "bytes";
		}
		
	};
	private JTable clientListTable = new JTable();
	
	public ServerFrame() {

		setTitle("FileTransporter server " + Main.version);
		//setIconImage(new ImageIcon().getImage());
		setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		addWindowListener(new WindowAdapter() {

			@Override
			public void windowClosing(WindowEvent e) {
				
				if(!ClientListTableModel.getinstance().clearAll()) return;
				e.getWindow().dispose();
				Main.log("ClientFrame was closed");
				Main.kill(0);

			}

		});
		setSize(331, 423); //add more height than fxml because it does not think about title length
		Dimension dim = Toolkit.getDefaultToolkit().getScreenSize();
		setLocation(dim.width/2-getSize().width/2, dim.height/2-getSize().height/2);
		setLayout(null);
		setResizable(false);

		ip.setBounds(14, 14, ip.getPreferredSize().width, ip.getPreferredSize().height);
		port.setBounds(180, 44, port.getPreferredSize().width, port.getPreferredSize().height);
		total.setBounds(10, 170, total.getPreferredSize().width, total.getPreferredSize().height);
		
		ip_t.setBounds(35, 10, ip_t.getPreferredSize().width, ip_t.getPreferredSize().height);
		port_t.setBounds(220, 10, port_t.getPreferredSize().width, port_t.getPreferredSize().height);
		
		start.setBounds(128, 202, start.getPreferredSize().width, start.getPreferredSize().height);
		cleanCompleted.setBounds(14, 367, cleanCompleted.getPreferredSize().width, cleanCompleted.getPreferredSize().height);
		clearAll.setBounds(261, 367, clearAll.getPreferredSize().width, clearAll.getPreferredSize().height);
		deleteSelected.setBounds(225, 166, deleteSelected.getPreferredSize().width, deleteSelected.getPreferredSize().height);
		addFile.setBounds(154, 166, addFile.getPreferredSize().width, addFile.getPreferredSize().height);
		//TODO: add listeners

		fileListTable.setModel(UploadListTableModel.getinstance());
		fileListTable.setAutoCreateColumnsFromModel(false);
		fileListTable.setFillsViewportHeight(true);
		fileListTable.getColumnModel().getColumn(0).setPreferredWidth(224);
		fileListTable.getColumnModel().getColumn(1).setPreferredWidth(80);
		JScrollPane scrollPane = new JScrollPane(fileListTable, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		scrollPane.setBounds(13, 45, 305, 117);
		
		clientListTable.setModel(ClientListTableModel.getinstance());
		clientListTable.setAutoCreateColumnsFromModel(false);
		clientListTable.getColumn("Progress").setCellRenderer(new ProgressRenderer());
		clientListTable.setFillsViewportHeight(true);
		clientListTable.getColumnModel().getColumn(0).setPreferredWidth(92);
		clientListTable.getColumnModel().getColumn(1).setPreferredWidth(131);
		clientListTable.getColumnModel().getColumn(1).setPreferredWidth(80);
		JScrollPane scrollPane1 = new JScrollPane(clientListTable, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		scrollPane1.setBounds(16, 231, 305, 127);
		
		
		
		add(ip);
		add(port);
		add(total);
		add(ip_t);
		add(port_t);
		add(start);
		add(cleanCompleted);
		add(clearAll);
		add(deleteSelected);
		add(addFile);
		add(cleanCompleted);
		add(scrollPane);
		add(scrollPane1);
		
		setVisible(true);
	}
}

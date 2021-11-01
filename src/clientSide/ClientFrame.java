package clientSide;

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

public class ClientFrame extends JFrame {

	/**
	 * 
	 */
	private static final long serialVersionUID = -7368753427595623846L;
	
	private JLabel ip = new JLabel("IP :");
	private JLabel port = new JLabel("Port :");
	
	private JTextField ip_t = new JTextField("localhost");
	private JTextField port_t = new JTextField();
	
	private JButton connect = new JButton("Connect");
	private JButton cleanCompleted = new JButton("clean completed");
	private JButton disconnectSelected = new JButton("disconnect selected");
	private JButton clearAll = new JButton("clear all");
	
	private JTable table = new JTable(){
		
		private static final long serialVersionUID = -4271449717757183126L;

		@Override
		public String getToolTipText(MouseEvent e) { 
			int row = rowAtPoint(e.getPoint());
			int column = columnAtPoint(e.getPoint());
			if (row == -1) return "";
			if (column == 0) {
				return DownloadingListTableModel.getinstance().getData().get(row).getDest();
			} else {
				FileReceiver r = DownloadingListTableModel.getinstance().getData().get(row);
				return r.getProgressString() + ", Connected to " + r.connectedTo();
			}
		}
		
	};
	
	public ClientFrame() {
		
		setTitle("FileTransporter client " + Main.version);
		//setIconImage(new ImageIcon().getImage());
		setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		addWindowListener(new WindowAdapter() {

			@Override
			public void windowClosing(WindowEvent e) {
				
				if(!DownloadingListTableModel.getinstance().clearAll()) return;
				e.getWindow().dispose();
				Main.log("ClientFrame was closed");
				Main.kill(0);

			}

		});
		setSize(331, 256); //add more height than fxml because it does not think about title length
		Dimension dim = Toolkit.getDefaultToolkit().getScreenSize();
		setLocation(dim.width/2-getSize().width/2, dim.height/2-getSize().height/2);
		setLayout(null);
		setResizable(false);

		
		ip.setBounds(14, 14, ip.getPreferredSize().width, ip.getPreferredSize().height);
		port.setBounds(180, 44, port.getPreferredSize().width, port.getPreferredSize().height);
		
		ip_t.setBounds(35, 10, ip_t.getPreferredSize().width, ip_t.getPreferredSize().height);
		port_t.setBounds(220, 10, port_t.getPreferredSize().width, port_t.getPreferredSize().height);

		connect.setBounds(136, 47, connect.getPreferredSize().width, connect.getPreferredSize().height);
		connect.addActionListener((e) -> {
			
			/*
			ip.setEnabled(false);
			port.setEnabled(false);
			ip_t.setEnabled(false);
			port_t.setEnabled(false);
			*/
			
			Main.queueJob(new FileReceiver(ip_t.getText(), port_t.getText()));
			
		});
		cleanCompleted.setBounds(13, 206, cleanCompleted.getPreferredSize().width, cleanCompleted.getPreferredSize().height);
		cleanCompleted.addActionListener((e) -> {
			DownloadingListTableModel.getinstance().clearDone();
		});
		disconnectSelected.setBounds(129, 206, disconnectSelected.getPreferredSize().width, disconnectSelected.getPreferredSize().height);
		disconnectSelected.addActionListener((e) -> {
			DownloadingListTableModel.getinstance().disconectSelected(table.getSelectedRows());
		});
		clearAll.setBounds(261, 206, clearAll.getPreferredSize().width, clearAll.getPreferredSize().height);
		clearAll.addActionListener((e) -> {
			DownloadingListTableModel.getinstance().clearAll();
		});
		
		table.setModel(DownloadingListTableModel.getinstance());
		table.setAutoCreateColumnsFromModel(false);
		table.getColumn("Progress").setCellRenderer(new ProgressRenderer());
		table.setFillsViewportHeight(true);
		table.getColumnModel().getColumn(0).setPreferredWidth(224);
		table.getColumnModel().getColumn(1).setPreferredWidth(80);
		JScrollPane scrollPane = new JScrollPane(table, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		scrollPane.setBounds(13, 82, 305, 117);

		add(ip);
		add(port);
		add(ip_t);
		add(port_t);
		add(connect);
		add(cleanCompleted);
		add(disconnectSelected);
		add(clearAll);
		add(scrollPane);
		
		setVisible(true);
	}
}

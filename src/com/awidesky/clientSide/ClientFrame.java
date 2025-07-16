package com.awidesky.clientSide;

import java.awt.Dimension;
import java.awt.Insets;
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
import javax.swing.SwingUtilities;

import com.awidesky.Main;
import com.awidesky.gui.ProgressRenderer;
import com.awidesky.util.SwingDialogs;
import com.awidesky.util.TaskLogger;

public class ClientFrame extends JFrame {

	
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
		public String getToolTipText(MouseEvent e) {  table.getToolTipText();
			int row = rowAtPoint(e.getPoint());
			int column = columnAtPoint(e.getPoint());
			if (row == -1) return null; //TODO : check if returning null cause problem.
			
			DonwloadingStatus d = DownloadingListTableModel.getinstance().getData().get(row);
			if (column == 0) {
				return d.getDest();
			} else {
				return d.getProgressString() + ", Connected to " + FileReceiver.getInstance().connectedTo(); //TODO : \n here?
			}
		}
		
	};
	
	private final TaskLogger logger = Main.getLogger("[Client]");
	
	public ClientFrame() {
		
		setTitle("FileTransporter(client) " + Main.version);
		//setIconImage(new ImageIcon().getImage());
		setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		addWindowListener(new WindowAdapter() {

			@Override
			public void windowClosing(WindowEvent e) {
				
				if(!DownloadingListTableModel.getinstance().clearAll()) return;
				e.getWindow().dispose();
				logger.log("ClientFrame was closed");
				Main.kill(0);

			}

		});
		setSize(350, 270); //add more height than fxml because it does not think about title length
		Dimension dim = Toolkit.getDefaultToolkit().getScreenSize();
		setLocation(dim.width/2-getSize().width/2, dim.height/2-getSize().height/2);
		setLayout(null);
		setResizable(false);

		
		ip.setBounds(14, 14, ip.getPreferredSize().width, ip.getPreferredSize().height);
		port.setBounds(180, 14, port.getPreferredSize().width, port.getPreferredSize().height);
		
		ip_t.setBounds(35, 10, 120, 22);
		port_t.setBounds(220, 10, 97, 22);

		connect.setBounds(136, 47, 65, 22);
		connect.setMargin(new Insets(0, 0, 0, 0));
		
		cleanCompleted.setBounds(10, 206, 115, 22);
		cleanCompleted.setMargin(new Insets(0, 0, 0, 0));
		disconnectSelected.setBounds(130, 206, 125, 22);
		disconnectSelected.setMargin(new Insets(0, 0, 0, 0));
		clearAll.setBounds(260, 206, 65, 22);
		clearAll.setMargin(new Insets(0, 0, 0, 0));

		connect.addActionListener((e) -> {

			if(ip_t.getText().equals("")) {
		    	SwingDialogs.error("invalid ip!", "Invalid ip!", null, true);
		    	return;
			}
			
			int i;
			try {
		        i = Integer.parseInt(port_t.getText());
		    } catch (NumberFormatException nfe) {
		    	SwingDialogs.error("invalid port number!", "Invalid port number!\n%e%", nfe, true);
		    	return;
		    }
			
			ip.setEnabled(false);
			port.setEnabled(false);
			ip_t.setEnabled(false);
			port_t.setEnabled(false);
			
			connect.setEnabled(false);
			
			FileReceiver fr = FileReceiver.getInstance(ip_t.getText(), i, this::guiResetCallback, logger);
			fr.setFuture(Main.queueJob(fr));
			
		});
		cleanCompleted.addActionListener((e) -> {
			DownloadingListTableModel.getinstance().clearDone();
		});
		disconnectSelected.addActionListener((e) -> {
			DownloadingListTableModel.getinstance().disconectSelected(table.getSelectedRows());
		});
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
	
	public void guiResetCallback() {
		SwingUtilities.invokeLater(() -> {
			ip.setEnabled(true);
			port.setEnabled(true);
			ip_t.setEnabled(true);
			port_t.setEnabled(true);

			connect.setEnabled(true);

			DownloadingListTableModel.getinstance().clearAll();
		});
	}

}

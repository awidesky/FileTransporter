package serverSide;

import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.JTextField;

import main.Main;

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
	
	private JTable fileListTable = new JTable();
	private JTable clientListTable = new JTable();
	
	public ServerFrame() {

		setTitle("FileTransporter server " + Main.version);
		//setIconImage(new ImageIcon().getImage());
		setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		addWindowListener(new WindowAdapter() {

			@Override
			public void windowClosing(WindowEvent e) {
				
				if(!ServerSendTableModel.getinstance().clearAll()) return;
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

	}
}

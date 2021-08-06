package clientSide;

import java.awt.Dimension;
import java.awt.Toolkit;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;

import main.Main;
import main.ProgressRenderer;

public class ClientFrame extends JFrame {

	private JLabel ip = new JLabel("IP :");
	private JLabel port = new JLabel("Port :");
	
	private JTextField ip_t = new JTextField("localhost");
	private JTextField port_t = new JTextField();
	
	private JButton connect = new JButton("Connect");
	
	private JTable table = new JTable();
	
	public ClientFrame() {
		
		setTitle("FileTransporter client " + Main.version);
		//setIconImage(new ImageIcon().getImage());
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setSize(331, 230); //add more height than fxml because it does not think about title length
		Dimension dim = Toolkit.getDefaultToolkit().getScreenSize();
		setLocation(dim.width/2-getSize().width/2, dim.height/2-getSize().height/2);
		setLayout(null);
		setResizable(false);

		
		ip.setBounds(14, 14, ip.getPreferredSize().width, ip.getPreferredSize().height);
		port.setBounds(180, 44, port.getPreferredSize().width, port.getPreferredSize().height);
		
		ip_t.setBounds(35, 10, ip_t.getPreferredSize().width, ip_t.getPreferredSize().height);
		port_t.setBounds(220, 10, port_t.getPreferredSize().width, port_t.getPreferredSize().height);

		connect.setBounds(136, 47, connect.getPreferredSize().width, connect.getPreferredSize().height);
		
		table.setModel(ClientTableModel.getinstance());
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
		add(scrollPane);
		
		setVisible(true);
	}
}

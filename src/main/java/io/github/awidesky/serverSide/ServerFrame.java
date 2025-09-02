package io.github.awidesky.serverSide;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Toolkit;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;

import io.github.awidesky.Main;
import io.github.awidesky.gui.ProgressRenderer;
import io.github.awidesky.guiUtil.SwingDialogs;
import io.github.awidesky.guiUtil.TaskLogger;

public class ServerFrame extends JFrame {

	private static final long serialVersionUID = 8677739105321293193L;

	private Server server = null;
	private boolean isStarted = false;

	private TaskLogger logger = Main.getLogger("[Server] ");

	private JDialog dialog = new JDialog();

	private JLabel ip = new JLabel("IP : ");
	private JLabel port = new JLabel("Port : ");

	private JTextField ip_t = new JTextField("localhost");
	private JTextField port_t = new JTextField(6);
	
	private JCheckBox encryptChannetcb = new JCheckBox("Encrypt channel");
	private JCheckBox passwordcb = new JCheckBox("Password");
	private JPasswordField passwordField = new JPasswordField(15);
	private JCheckBox checkFileHashcb = new JCheckBox("Check file hash");

	private JButton start = new JButton("START server");

	private JButton cleanCompleted = new JButton("clean completed");
	private JButton disconnectSelected = new JButton("disconnect selected");
	private JButton disconnectAll = new JButton("clear all");

	private VirtualFolderTree folderTree;
	JTabbedPane clientTab = new JTabbedPane();
	
	public ServerFrame() {
		setTitle("FileTransporter(server) " + Main.version);
		setIconImage(Main.icon);
		setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				if(!stopServer()) return;
				e.getWindow().dispose();
				logger.info("ServerFrame was closed");
				Main.kill(0);
			}
		});

		setSize(650, 450);
		Dimension dim = Toolkit.getDefaultToolkit().getScreenSize();
		setLocation(dim.width/2-getSize().width/2, dim.height/2-getSize().height/2);

		setLayout(new BorderLayout());
		setResizable(true);

		dialog.setAlwaysOnTop(true);

		JPanel mainTab = initMainTab();
		JPanel controlTab = initControlTab();

		JTabbedPane tabPane = new JTabbedPane();
		tabPane.addTab("Main", mainTab);
		tabPane.addTab("Control", controlTab);

		add(tabPane);

		setVisible(true);
	}

	private JPanel initMainTab() {
		JPanel mainTab = new JPanel(new BorderLayout());
		JPanel top = new JPanel();
		top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
		JPanel addr = new JPanel(new FlowLayout());
		addr.add(ip);
		addr.add(ip_t);
		addr.add(Box.createHorizontalStrut(20));
		addr.add(port);
		addr.add(port_t);
		JPanel options = new JPanel();
		passwordField.setEnabled(false);
		passwordcb.addActionListener(e -> passwordField.setEnabled(passwordcb.isSelected()));
		options.add(passwordcb);
		options.add(passwordField);
		options.add(Box.createHorizontalStrut(20));
		options.add(encryptChannetcb);
		options.add(checkFileHashcb);
		top.add(addr);
		top.add(options);
		mainTab.add(top, BorderLayout.NORTH);
		
		start.addActionListener((e) -> {
			if(isStarted) stopServer();
			else startServer();
			isStarted = !isStarted;
		});
		folderTree = new VirtualFolderTree(start);
		mainTab.add(folderTree, BorderLayout.CENTER);

		return mainTab;
	}


	private JPanel initControlTab() {
		JPanel controlTab = new JPanel(new BorderLayout());

		JPanel buttons = new JPanel();
		cleanCompleted.setEnabled(false);
		disconnectAll.setEnabled(false);
		disconnectSelected.setEnabled(false);
		buttons.add(cleanCompleted);
		buttons.add(disconnectAll);
		buttons.add(disconnectSelected);

		controlTab.add(buttons, BorderLayout.SOUTH);

		JScrollPane clientScroll = new JScrollPane(clientTab, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		clientScroll.setPreferredSize(new Dimension(305, 127));
		controlTab.add(clientScroll, BorderLayout.CENTER);

		return controlTab;
	}


	public void startServer() {
		if(ip_t.getText().equals("")) {
			SwingDialogs.error("invalid ip!", "Invalid ip!", null, true);
			return;
		}
		if(folderTree.isEmpty()) {
			SwingDialogs.error("No file is chosen!", "There's no file to send!", null, true);
			return;
		}

		int i;
		try {
			i = Integer.parseInt(port_t.getText());
			if(i < 0 || 65535 < i)
				throw new NumberFormatException("port number must be in between 0 ~ 65535");
		} catch (NumberFormatException nfe) {
			SwingDialogs.error("invalid port number!", "Invalid port number!\n%e%", nfe, true);
			return;
		}

		resetGUI(false);

		server = new Server(i, this, logger);
		server.setFuture(Main.queueJob(server));
	}

	public boolean stopServer() {
		if(server != null) {
			if(!server.disconnect())
				return false;
		}
		resetGUI(true);
		return true;
	}

	public void resetGUI(boolean enabled) {
		ip.setEnabled(enabled);
		port.setEnabled(enabled);
		ip_t.setEnabled(enabled);
		port_t.setEnabled(enabled);
		
		start.setText(enabled ? "START server" : "STOP server");
		
		passwordField.setEnabled(enabled && passwordcb.isSelected());
		encryptChannetcb.setEnabled(enabled);
		passwordcb.setEnabled(enabled);
		checkFileHashcb.setEnabled(enabled);

		folderTree.setEnableAll(enabled);

		if(enabled) clientTab.removeAll();
		cleanCompleted.setEnabled(!enabled);
		disconnectSelected.setEnabled(!enabled);
		disconnectAll.setEnabled(!enabled);
	}

	void addClient(ConnectedClient client) {
		ClientListTableModel model = new ClientListTableModel(folderTree.getSelectedFiles(), client);
		JTable clientListTable = new JTable() {

			private static final long serialVersionUID = 8873559199947424949L;

			@Override
			public String getToolTipText(MouseEvent e) {
				int row = rowAtPoint(e.getPoint());
				int column = columnAtPoint(e.getPoint());
				if (row == -1)
					return "";
				if (column == 0)
					return model.getData().get(row).getStatus();
				else if (column == 1) {
					File f = model.getData().get(row).getFile();
					if(f == null)
						return "-1";
					else
						return f.getAbsolutePath();
				}
				else
					return model.getData().get(row).getProgressString();
			}
		};

		clientListTable.setModel(model);
		clientListTable.setAutoCreateColumnsFromModel(false);
		clientListTable.getColumn("Progress").setCellRenderer(new ProgressRenderer());
		clientListTable.setFillsViewportHeight(true);
		clientListTable.getColumnModel().getColumn(0).setPreferredWidth(92);
		clientListTable.getColumnModel().getColumn(1).setPreferredWidth(131);
		clientListTable.getColumnModel().getColumn(2).setPreferredWidth(80);

		client.setFileQueue(model.getFileQueue());
		clientTab.addTab(client.getUUID().substring(0, 8), null, clientListTable, "Client UUID : " + client.getUUID());
	}
}

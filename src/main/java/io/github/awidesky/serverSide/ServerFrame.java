package io.github.awidesky.serverSide;

import java.awt.Dimension;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.util.Arrays;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.filechooser.FileFilter;

import io.github.awidesky.Main;
import io.github.awidesky.gui.ImageViewer;
import io.github.awidesky.gui.ProgressRenderer;
import io.github.awidesky.guiUtil.SwingDialogs;
import io.github.awidesky.guiUtil.TaskLogger;

public class ServerFrame extends JFrame {

	private static final long serialVersionUID = 8677739105321293193L;
	
	private Server server = null;
	private boolean isStarted = false;
	
	private TaskLogger logger = Main.getLogger("[Server]");
	
	private JFileChooser chooser = new JFileChooser();;
	private JDialog dialog = new JDialog();;
	
	private JLabel ip = new JLabel("IP : ");
	private JLabel port = new JLabel("Port : ");
	private JLabel total= new JLabel("Total : 0.00 byte");

	private JTextField ip_t = new JTextField("localhost");
	private JTextField port_t = new JTextField();
	
	private JButton start = new JButton("start server");
	private JButton addFile = new JButton("add file...");
	private JButton deleteSelectedFile = new JButton("delete selected");

	private JButton cleanCompleted = new JButton("clean completed");
	private JButton disconnectSelected = new JButton("disconnect selected");
	private JButton disconnectAll = new JButton("clear all");
	
	private JTable fileListTable = new JTable() {
		
		private static final long serialVersionUID = -4271449717757183126L;

		@Override
		public String getToolTipText(MouseEvent e) { 
			int row = rowAtPoint(e.getPoint());
			int column = columnAtPoint(e.getPoint());
			if (row == -1) return "";
			if (column == 0) return UploadListTableModel.getinstance().getData().get(row).getAbsolutePath();
			else return Main.formatFileSize(UploadListTableModel.getinstance().getData().get(row).length());
		}
		
	};
	private JTable clientListTable = new JTable() {
		
		private static final long serialVersionUID = 8873559199947424949L;

		@Override
		public String getToolTipText(MouseEvent e) { 
			int row = rowAtPoint(e.getPoint());
			int column = columnAtPoint(e.getPoint());
			if (row == -1) return "";
			if (column == 2) {
				File f = ClientListTableModel.getinstance().getData().get(row).getNowSendingFile();
				if(f == null) return "-1";
				else return Main.formatFileSize(f.length());
			} else return ClientListTableModel.getinstance().getData().get(row).getProgressString();
		}
		
	};
	
	public ServerFrame() {

		setTitle("FileTransporter(server) " + Main.version);
		setIconImage(Main.icon);
		setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		addWindowListener(new WindowAdapter() {

			@Override
			public void windowClosing(WindowEvent e) {
				
				if(!ClientListTableModel.getinstance().clearAll()) return;
				stopServer();
				e.getWindow().dispose();
				logger.info("ServerFrame was closed");
				Main.kill(0);

			}

		});
		setSize(350, 450); //add more height than fxml because it does not think about title length
		Dimension dim = Toolkit.getDefaultToolkit().getScreenSize();
		setLocation(dim.width/2-getSize().width/2, dim.height/2-getSize().height/2);
		setLayout(null);
		setResizable(false);

		
		dialog.setAlwaysOnTop(true);
		
		ImageViewer temp1 = new ImageViewer(chooser);
		chooser.setMultiSelectionEnabled(true);
		chooser.setAccessory(temp1);
		chooser.addComponentListener(new ComponentAdapter() {
		    public void componentResized(ComponentEvent e) {
		    	temp1.dialogSizeChange();
		    }
		});
		chooser.addChoosableFileFilter(new FileFilter() {
			
			public boolean accept(File f) {
				if (f.isDirectory()
						|| f.getName().endsWith(".jpeg")
						|| f.getName().endsWith(".jpg")
						|| f.getName().endsWith(".bmp")
						|| f.getName().endsWith(".png"))
					return true;
				else
					return false;
			}

			public String getDescription() {
				return "Picture files (*.jpeg, *.jpg, *.png, *.bmp)";
			}
		});
		
		chooser.addChoosableFileFilter(new FileFilter() {
			
			public boolean accept(File f) {
				if (f.isDirectory()
						|| f.getName().endsWith(".pdf")
						|| f.getName().endsWith(".docx")
						|| f.getName().endsWith(".hwp")
						|| f.getName().endsWith(".xlsx")
						|| f.getName().endsWith(".pptx"))
					return true;
				else
					return false;
			}

			public String getDescription() {
				return "Document files (*.pdf, *.docx, *.hwp, *.xlsx, *.pptx)";
			}
		});
		
		
		ip.setBounds(14, 14, ip.getPreferredSize().width, ip.getPreferredSize().height);
		port.setBounds(180, 14, port.getPreferredSize().width, port.getPreferredSize().height);
		total.setBounds(10, 170, 120, total.getPreferredSize().height);
		
		ip_t.setBounds(35, 10, 120, 22);
		port_t.setBounds(220, 10, 97, 22);
		
		//Font f = UIManager.getDefaults().getFont("Button.font").deriveFont((float) 12.0);
		
		start.setBounds(128, 202, 80, 22);
		start.setMargin(new Insets(0, 0, 0, 0));
		
		cleanCompleted.setBounds(10, 367, 115, 22);
		cleanCompleted.setMargin(new Insets(0, 0, 0, 0));
		cleanCompleted.setEnabled(false);
		disconnectAll.setBounds(260, 367, 65, 22);
		disconnectAll.setMargin(new Insets(0, 0, 0, 0));
		disconnectAll.setEnabled(false);
		disconnectSelected.setBounds(130, 367, 125, 22);
		disconnectSelected.setMargin(new Insets(0, 0, 0, 0));
		disconnectSelected.setEnabled(false);
		addFile.setBounds(143, 166, 75, 22);
		addFile.setMargin(new Insets(0, 0, 0, 0));
		deleteSelectedFile.setBounds(225, 166, 100, 22);
		deleteSelectedFile.setMargin(new Insets(0, 0, 0, 0));
		
		start.addActionListener((e) -> {
			
			if(isStarted) {
				stopServer();
			} else {
				startServer();
			}
			isStarted = !isStarted;
			
		});
		cleanCompleted.addActionListener((e) -> {
			
			ClientListTableModel.getinstance().clearDone();
			
		});
		disconnectAll.addActionListener((e) -> {
			
			ClientListTableModel.getinstance().clearAll();
			
		});
		deleteSelectedFile.addActionListener((e) -> {
			
			UploadListTableModel.getinstance().deleteSelected(fileListTable.getSelectedRows());
			total.setText("Total : " + Main.formatFileSize(UploadListTableModel.getinstance().getFileSizeTotal()));
			
		});
		addFile.addActionListener((e) -> {
			
			if (chooser.showOpenDialog(dialog) != JFileChooser.APPROVE_OPTION) return;
			List<File> temp = Arrays.asList(chooser.getSelectedFiles());
			chooser.setCurrentDirectory(temp.get(0).getAbsoluteFile().getParentFile());
			UploadListTableModel.getinstance().addFiles(temp);
			total.setText("Total : " + Main.formatFileSize(UploadListTableModel.getinstance().getFileSizeTotal()));
		});
		disconnectSelected.addActionListener((e) -> {
			
			ClientListTableModel.getinstance().disconectSelected(clientListTable.getSelectedRows());
			
		});
		
		fileListTable.setModel(UploadListTableModel.getinstance());
		fileListTable.setAutoCreateColumnsFromModel(false);
		fileListTable.setFillsViewportHeight(true);
		fileListTable.getColumnModel().getColumn(0).setPreferredWidth(200);
		fileListTable.getColumnModel().getColumn(1).setPreferredWidth(80);
		JScrollPane scrollPane = new JScrollPane(fileListTable, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		scrollPane.setBounds(13, 45, 305, 117);
		
		clientListTable.setModel(ClientListTableModel.getinstance());
		clientListTable.setAutoCreateColumnsFromModel(false);
		clientListTable.getColumn("Progress").setCellRenderer(new ProgressRenderer());
		clientListTable.setFillsViewportHeight(true);
		clientListTable.getColumnModel().getColumn(0).setPreferredWidth(92);
		clientListTable.getColumnModel().getColumn(1).setPreferredWidth(131);
		clientListTable.getColumnModel().getColumn(2).setPreferredWidth(80);
		JScrollPane scrollPane1 = new JScrollPane(clientListTable, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		scrollPane1.setBounds(16, 231, 305, 127);
		clientListTable.setEnabled(false);
		
		
		add(ip);
		add(port);
		add(total);
		add(ip_t);
		add(port_t);
		add(start);
		add(cleanCompleted);
		add(disconnectAll);
		add(deleteSelectedFile);
		add(addFile);
		add(disconnectSelected);
		add(scrollPane);
		add(scrollPane1);
		
		setVisible(true);
	}
	
	public void startServer() {
		
		if(ip_t.getText().equals("")) {
			SwingDialogs.error("invalid ip!", "Invalid ip!", null, true);
	    	return;
		}
		
		if(UploadListTableModel.getinstance().getData().isEmpty()) {
			SwingDialogs.error("No file is chosen!", "There's no file to send!", null, true);
	    	return;
		}
		
		int i;
		try {
	        i = Integer.parseInt(port_t.getText());
	        if(i < 0 || 65535 < i) throw new NumberFormatException("port number must be in between 0 ~ 65535");
	    } catch (NumberFormatException nfe) {
	    	SwingDialogs.error("invalid port number!", "Invalid port number!\n%e%", nfe, true);
	    	return;
	    }
		
		ip.setEnabled(false);
		port.setEnabled(false);
		ip_t.setEnabled(false);
		port_t.setEnabled(false);
		
		start.setText("stop server");
		fileListTable.setEnabled(false);
		addFile.setEnabled(false);
		deleteSelectedFile.setEnabled(false);
		
		clientListTable.setEnabled(true);
		
		cleanCompleted.setEnabled(true);
		disconnectSelected.setEnabled(true);
		disconnectAll.setEnabled(true);

		server = new Server(i, UploadListTableModel.getinstance().getData().toArray(new File[]{}), this::guiResetCallback, logger);
		server.setFuture(Main.queueJob(server));
		
	}
	
	public void stopServer() {

		if(!ClientListTableModel.getinstance().getData().isEmpty() && !SwingDialogs.confirm("Stop server?", "Really want to stop the server?\nconnections may be lost!")) {
			return;
		}
		
		if(server != null) server.disconnect();
		
		guiResetCallback();

	}
	
	public void guiResetCallback() {
		
		ip.setEnabled(true);
		port.setEnabled(true);
		ip_t.setEnabled(true);
		port_t.setEnabled(true);
		
		start.setText("start server");
		fileListTable.setEnabled(true);
		addFile.setEnabled(true);
		deleteSelectedFile.setEnabled(true);
		
		clientListTable.setEnabled(false);
		
		cleanCompleted.setEnabled(false);
		disconnectSelected.setEnabled(false);
		disconnectAll.setEnabled(false);
		
		ClientListTableModel.getinstance().clearAll();
		
	}
}

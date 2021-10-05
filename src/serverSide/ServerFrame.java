package serverSide;

import java.awt.Dimension;
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

import main.ImageViewer;
import main.Main;
import main.ProgressRenderer;

public class ServerFrame extends JFrame {

	private static final long serialVersionUID = 8677739105321293193L;
	
	private JFileChooser chooser = new JFileChooser();;
	private JDialog dialog;
	
	private JLabel ip = new JLabel("IP : ");
	private JLabel port = new JLabel("Port : ");
	private JLabel total= new JLabel("Total : ");

	private JTextField ip_t = new JTextField();
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
			if (column == 2) return Main.formatFileSize(ClientListTableModel.getinstance().getData().get(row).getNowSendingFile().length());
			else return ClientListTableModel.getinstance().getData().get(row).getStatus();
		}
		
	};
	
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
		port.setBounds(180, 44, port.getPreferredSize().width, port.getPreferredSize().height);
		total.setBounds(10, 170, total.getPreferredSize().width, total.getPreferredSize().height);
		
		ip_t.setBounds(35, 10, ip_t.getPreferredSize().width, ip_t.getPreferredSize().height);
		port_t.setBounds(220, 10, port_t.getPreferredSize().width, port_t.getPreferredSize().height);
		
		start.setBounds(128, 202, start.getPreferredSize().width, start.getPreferredSize().height);
		cleanCompleted.setBounds(14, 367, cleanCompleted.getPreferredSize().width, cleanCompleted.getPreferredSize().height);
		disconnectAll.setBounds(261, 367, disconnectAll.getPreferredSize().width, disconnectAll.getPreferredSize().height);
		deleteSelectedFile.setBounds(225, 166, deleteSelectedFile.getPreferredSize().width, deleteSelectedFile.getPreferredSize().height);
		addFile.setBounds(154, 166, addFile.getPreferredSize().width, addFile.getPreferredSize().height);
		disconnectSelected.setBounds(130, 367, disconnectSelected.getPreferredSize().width, disconnectSelected.getPreferredSize().height);
		
		start.addActionListener((e) -> {
			
			start.setEnabled(false);
			fileListTable.setEnabled(false);
			addFile.setEnabled(false);
			deleteSelectedFile.setEnabled(false);
			
			
			clientListTable.setEnabled(true);
			
			cleanCompleted.setEnabled(true);
			disconnectSelected.setEnabled(true);
			disconnectAll.setEnabled(true);
			
			Main.queueJob(new FileSender(Integer.parseInt(port_t.getText()), UploadListTableModel.getinstance().getData().toArray(new File[]{})));
			
		});
		cleanCompleted.addActionListener((e) -> {
			
			ClientListTableModel.getinstance().clearDone();
			
		});
		disconnectAll.addActionListener((e) -> {
			
			ClientListTableModel.getinstance().clearAll();
			
		});
		deleteSelectedFile.addActionListener((e) -> {
			
			UploadListTableModel.getinstance().deleteSelected(fileListTable.getSelectedRows());
			
		});
		addFile.addActionListener((e) -> {
			
			if (chooser.showOpenDialog(dialog) != JFileChooser.APPROVE_OPTION) return;
			List<File> temp = Arrays.asList(chooser.getSelectedFiles());
			chooser.setCurrentDirectory(temp.get(0).getAbsoluteFile().getParentFile());
			UploadListTableModel.getinstance().addFiles(temp);
			
		});
		disconnectSelected.addActionListener((e) -> {
			
			ClientListTableModel.getinstance().disconectSelected(clientListTable.getSelectedRows());
			
		});
		
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
		add(cleanCompleted);
		add(disconnectSelected);
		add(scrollPane);
		add(scrollPane1);
		
		setVisible(true);
	}
}

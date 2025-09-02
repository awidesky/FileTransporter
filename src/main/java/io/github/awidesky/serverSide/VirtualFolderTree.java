package io.github.awidesky.serverSide;

import java.awt.BorderLayout;
import java.awt.Point;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.stream.Collectors;

import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.ToolTipManager;
import javax.swing.filechooser.FileFilter;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;

import io.github.awidesky.Main;
import io.github.awidesky.gui.ImageViewer;

public class VirtualFolderTree extends JPanel {
	
	private static final long serialVersionUID = 2135376001154602321L;
	private JTree tree;
	private DefaultTreeModel treeModel;
	private DefaultMutableTreeNode root;

	private File chooseDir = new File(System.getProperty("user.home"));
	private JButton addFileBtn;
	private JButton deleteBtn;
	private JCheckBox selectHidden;
	private JFileChooser chooser;
	private JLabel total = new JLabel("Total : 0.00 byte");
	
	
	public VirtualFolderTree(JButton startButton) {
		setLayout(new BorderLayout());
		chooser = new JFileChooser(chooseDir);
		chooser.setMultiSelectionEnabled(true);
		chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
		JPanel accessory = new JPanel(new BorderLayout());
		ImageViewer temp1 = new ImageViewer(chooser);
		selectHidden = new JCheckBox("Select hidden files");
		selectHidden.addActionListener(e -> chooser.setFileHidingEnabled(!selectHidden.isSelected()));
		accessory.add(temp1, BorderLayout.CENTER);
		accessory.add(selectHidden, BorderLayout.SOUTH);
		chooser.setAccessory(accessory);
		chooser.addComponentListener(new ComponentAdapter() {
			public void componentResized(ComponentEvent e) {
				temp1.dialogSizeChange();
			}
		});
		chooser.addChoosableFileFilter(new FileFilter() {
			public boolean accept(File f) {
				if (f.isDirectory() || f.getName().endsWith(".jpeg") || f.getName().endsWith(".jpg") || f.getName().endsWith(".bmp") || f.getName().endsWith(".png"))
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
				if (f.isDirectory() || f.getName().endsWith(".pdf") || f.getName().endsWith(".docx") || f.getName().endsWith(".hwp") || f.getName().endsWith(".xlsx") || f.getName().endsWith(".pptx"))
					return true;
				else
					return false;
			}
			public String getDescription() {
				return "Document files (*.pdf, *.docx, *.hwp, *.xlsx, *.pptx)";
			}
		});

		root = new DefaultMutableTreeNode("root");
		treeModel = new DefaultTreeModel(root);
		tree = new JTree(treeModel) {
		    private static final long serialVersionUID = -3171345745110920282L;
			@Override
		    public String getToolTipText(MouseEvent e) {
		    	Point p = e.getPoint();
		        TreePath path = getPathForLocation(p.x, p.y);
		        if (path == null) return null;

		        Object o = ((DefaultMutableTreeNode)path.getLastPathComponent()).getUserObject();
				if (o instanceof SelectedFile s) {
					File f = s.actual();
					return f.getAbsolutePath() + " (" + Main.formatFileSize(f.length()) + ")";
				} else return o.toString();
			}
		};
		ToolTipManager.sharedInstance().registerComponent(tree);
		tree.setShowsRootHandles(true);
		tree.setEditable(false);

		JScrollPane scrollPane = new JScrollPane(tree);

		JPanel bottom = new JPanel(new BorderLayout());
		JPanel bottomLeft = new JPanel();
		addFileBtn = new JButton("add files");
		deleteBtn = new JButton("delete selected");
		bottomLeft.add(addFileBtn);
		bottomLeft.add(deleteBtn);
		bottomLeft.add(total);
		
		bottom.add(bottomLeft, BorderLayout.WEST);
		bottom.add(Box.createHorizontalStrut(40));
		bottom.add(startButton, BorderLayout.EAST);
		

		addFileBtn.addActionListener(e -> selectUploadFile());
		deleteBtn.addActionListener(e -> deleteSelectedNode());

		add(scrollPane, BorderLayout.CENTER);
		add(bottom, BorderLayout.SOUTH);
	}
	
	private List<SelectedFile> selectedFiles = null;
	public List<SelectedFile> getSelectedFiles() {
		if(selectedFiles == null) return evaluateSelectedFiles();
		else return selectedFiles;
	}
	private List<SelectedFile> evaluateSelectedFiles() {
		Enumeration<TreeNode> e = root.depthFirstEnumeration();
		List<SelectedFile> list = new ArrayList<>(root.getLeafCount());
		while (e.hasMoreElements()) {
			DefaultMutableTreeNode node = (DefaultMutableTreeNode) e.nextElement();
			Object userObject = node.getUserObject();
			if(userObject instanceof SelectedFile s) list.add(s);
		}
		return list;
	}

	public boolean isEmpty() {
		return root.getChildCount() == 0;
	}
	private void selectUploadFile() {
		TreePath path = tree.getSelectionPath();
		DefaultMutableTreeNode parent = path == null ? root : (DefaultMutableTreeNode) path.getLastPathComponent();
		if(parent != root && parent.isLeaf()) parent = (DefaultMutableTreeNode) parent.getParent();

		chooser.setCurrentDirectory(chooseDir);
		if(chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
		File[] arr = chooser.getSelectedFiles();
		Arrays.sort(arr, Comparator.comparing(File::getName));
		
		chooseDir = arr[0].getParentFile();
		
		for(File f : arr) insertFileRecursive(f, buildVirtualPath(parent), parent);

		total.setText("Total : " + Main.formatFileSize(getSelectedFiles().stream().map(SelectedFile::actual).mapToLong(File::length).sum()));
	}
	
	private void insertFileRecursive(File file, String parentVirtualPath, DefaultMutableTreeNode parentNode) {
		if(!selectHidden.isSelected() && file.isHidden()) return;
		String relativePath = parentVirtualPath + file.getName();
		
		if (file.isDirectory()) {
			DefaultMutableTreeNode folderNode = new DefaultMutableTreeNode(file.getName());
			treeModel.insertNodeInto(folderNode, parentNode, parentNode.getChildCount());

			File[] arr = file.listFiles();
			Arrays.sort(arr, Comparator.comparing(File::getName));
			for (File child : arr) {
				insertFileRecursive(child, relativePath + "/", folderNode);
			}
		} else {
			SelectedFile selectedFile = new SelectedFile(file, relativePath);
			DefaultMutableTreeNode fileNode = new DefaultMutableTreeNode(selectedFile);
			treeModel.insertNodeInto(fileNode, parentNode, parentNode.getChildCount());
		}
	}


	private String buildVirtualPath(DefaultMutableTreeNode node) {
		TreeNode[] path = node.getPath();
		
		if(path.length == 1) return "";
		return Arrays.stream(path, 1, path.length)
				.map(p -> ((DefaultMutableTreeNode) p).getUserObject())
				.map(o -> (o instanceof SelectedFile s) ? s.actual().getName() : o.toString())
				.collect(Collectors.joining("/")) + "/";
	}

	private void deleteSelectedNode() {
		TreePath[] selectedPaths = tree.getSelectionPaths();
		if (selectedPaths == null) {
			JOptionPane.showMessageDialog(this, "Select a node to delete!");
			return;
		}
		for(TreePath selectedPath : selectedPaths) {
			DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) selectedPath.getLastPathComponent();
			if (selectedNode == root) {
				JOptionPane.showMessageDialog(this, "Cannot delete root node!");
				return;
			}

			if (!selectedNode.isLeaf()
					&& JOptionPane.showConfirmDialog(this, "This is a folder. Delete all contained files and subfolders?",
							"Confirm Folder Deletion", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION)
				return;

			DefaultMutableTreeNode parent = (DefaultMutableTreeNode) selectedNode.getParent();
			treeModel.removeNodeFromParent(selectedNode);
			tree.setSelectionPath(new TreePath(parent.getPath())); // reset selection
		}
		total.setText("Total : " + Main.formatFileSize(getSelectedFiles().stream().map(SelectedFile::actual).mapToLong(File::length).sum()));
	}

	public void setEnableAll(boolean enable) {
		//tree.setEnabled(b);
		addFileBtn.setEnabled(enable);
		deleteBtn.setEnabled(enable);
		selectHidden.setEnabled(enable);
		
		selectedFiles = enable ? null : evaluateSelectedFiles();
	}

}

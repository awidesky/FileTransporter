package io.github.awidesky.serverSide;

import java.awt.BorderLayout;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.stream.Collectors;

import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;

public class VirtualFolderTree extends JFrame {
	
	private static final long serialVersionUID = 2135376001154602321L;
	private JTree tree;
	private DefaultTreeModel treeModel;
	private DefaultMutableTreeNode root;

	private File chooseDir = new File(System.getProperty("user.home"));
	private JButton addFileBtn;
	private JButton deleteBtn;
	private JFileChooser chooser;
	
	
	public VirtualFolderTree() {
		setTitle("Upload folder tree");
		setSize(500, 400);
		setDefaultCloseOperation(EXIT_ON_CLOSE);
		
		chooser = new JFileChooser(chooseDir);
		chooser.setMultiSelectionEnabled(false);
		chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);

		root = new DefaultMutableTreeNode("root");
		treeModel = new DefaultTreeModel(root);
		tree = new JTree(treeModel);
		tree.setShowsRootHandles(true);
		tree.setEditable(false);

		JScrollPane scrollPane = new JScrollPane(tree);

		JPanel panel = new JPanel();
		addFileBtn = new JButton("add files");
		deleteBtn = new JButton("delete selected");

		panel.add(addFileBtn);
		panel.add(deleteBtn);

		addFileBtn.addActionListener(e -> selectUploadFile());
		deleteBtn.addActionListener(e -> deleteSelectedNode());

		getContentPane().add(scrollPane, BorderLayout.CENTER);
		getContentPane().add(panel, BorderLayout.SOUTH);
	}
	
	public List<SelectedFile> getSelectedFiles() {
		Enumeration<TreeNode> e = root.depthFirstEnumeration();
		List<SelectedFile> list = new ArrayList<>(root.getLeafCount());
		while (e.hasMoreElements()) {
			DefaultMutableTreeNode node = (DefaultMutableTreeNode) e.nextElement();
			Object userObject = node.getUserObject();
			if(userObject instanceof SelectedFile s) list.add(s);
		}
		return list;
	}

	private void selectUploadFile() {
		TreePath path = tree.getSelectionPath();
		DefaultMutableTreeNode parent = path == null ? root : (DefaultMutableTreeNode) path.getLastPathComponent();
		if(parent != root && parent.isLeaf()) parent = (DefaultMutableTreeNode) parent.getParent();

		chooser.setCurrentDirectory(chooseDir);
		chooser.showOpenDialog(this);
		File f = chooser.getSelectedFile();
		
		chooseDir = f.getParentFile();
		
		insertFileRecursive(f, buildVirtualPath(parent), parent);
	}
	
	private void insertFileRecursive(File file, String parentVirtualPath, DefaultMutableTreeNode parentNode) {
		String relativePath = parentVirtualPath + file.getName();
		
		if (file.isDirectory()) {
			DefaultMutableTreeNode folderNode = new DefaultMutableTreeNode(file.getName());
			treeModel.insertNodeInto(folderNode, parentNode, parentNode.getChildCount());

			for (File child : file.listFiles()) {
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
	}

	public static void main(String[] args) { //TODO : remove
		SwingUtilities.invokeLater(() -> {
			new VirtualFolderTree().setVisible(true);
		});
	}
}

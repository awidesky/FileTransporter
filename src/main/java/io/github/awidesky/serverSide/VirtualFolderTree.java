package io.github.awidesky.serverSide;

import java.awt.BorderLayout;
import java.io.File;
import java.util.Arrays;
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
	
	public VirtualFolderTree() {
		setTitle("Upload folder tree");
		setSize(500, 400);
		setDefaultCloseOperation(EXIT_ON_CLOSE);

		root = new DefaultMutableTreeNode("root");
		treeModel = new DefaultTreeModel(root);
		tree = new JTree(treeModel);
		tree.setShowsRootHandles(true);
		tree.setEditable(false);

		JScrollPane scrollPane = new JScrollPane(tree);

		JPanel panel = new JPanel();
		JButton addFileBtn = new JButton("add files");

		panel.add(addFileBtn);

		addFileBtn.addActionListener(e -> selectUploadFile());

		getContentPane().add(scrollPane, BorderLayout.CENTER);
		getContentPane().add(panel, BorderLayout.SOUTH);
	}

	private void selectUploadFile() {
		TreePath path = tree.getSelectionPath();
		if (path == null) {
			JOptionPane.showMessageDialog(this, "Select folder to add file in folder tree!");
			return;
		}
		
		JFileChooser chooser = new JFileChooser(chooseDir);
		chooser.setMultiSelectionEnabled(false);
		chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
		
		chooser.showOpenDialog(this);
		File f = chooser.getSelectedFile();
		
		chooseDir = f.isDirectory() ? f : f.getParentFile();
		
		DefaultMutableTreeNode parent = (DefaultMutableTreeNode) path.getLastPathComponent();
		insertFileRecursive(f, buildVirtualPath(parent), parent);
	}
	
	private void insertFileRecursive(File file, String parentVirtualPath, DefaultMutableTreeNode parentNode) {
		String relativePath = parentVirtualPath + file.getName();
		
		if (file.isDirectory()) {
			DefaultMutableTreeNode folderNode = new DefaultMutableTreeNode(file.getName());
			treeModel.insertNodeInto(folderNode, parentNode, parentNode.getChildCount());

			// For each child, recurse
			for (File child : file.listFiles()) {
				insertFileRecursive(child, relativePath + "/", folderNode);
			}
		} else {
			// Leaf file, wrap in SelectedFile
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

	public static void main(String[] args) {
		SwingUtilities.invokeLater(() -> {
			new VirtualFolderTree().setVisible(true);
		});
	}
}


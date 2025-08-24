package io.github.awidesky;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Enumeration;

import javax.swing.tree.DefaultMutableTreeNode;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.awidesky.serverSide.SelectedFile;
import io.github.awidesky.serverSide.VirtualFolderTree;
/**
 * root  
 * ├ fileRoot.txt  
 * ├ folderA
 * │   ├ a1.txt  
 * │   ├ a2.txt  
 * │   ├ subfolder1  
 * │   │   ├ sub1.txt  
 * │   │   └ sub2.txt  
 * │   └ subfolder2  # different absolute path with folderA
 * │       ├ anotherSub1.txt  
 * │       └ anotherSub2.txt  
 * └ folderB
 *     └ b1.txt
 */
public class VirtualFolderTreeTest {

	private VirtualFolderTree treeUI;
	private DefaultMutableTreeNode root;
	private Method insertMethod;
	private Method buildPathMethod;
	private File tempRoot;

	@BeforeEach
	void setup() throws Exception {
		treeUI = new VirtualFolderTree();

		var rootField = VirtualFolderTree.class.getDeclaredField("root");
		rootField.setAccessible(true);
		root = (DefaultMutableTreeNode) rootField.get(treeUI);

		insertMethod = VirtualFolderTree.class.getDeclaredMethod(
			"insertFileRecursive", File.class, String.class, DefaultMutableTreeNode.class);
		insertMethod.setAccessible(true);

		buildPathMethod = VirtualFolderTree.class.getDeclaredMethod(
			"buildVirtualPath", DefaultMutableTreeNode.class);
		buildPathMethod.setAccessible(true);
		
		tempRoot = Files.createTempDirectory("VirtualFolderTreeTest").toFile();
	}
	
	@AfterEach
	void cleanUp() throws IOException {
		deleteDirectoryRecursion(tempRoot.toPath());
	}
	private void deleteDirectoryRecursion(Path path) throws IOException {
		if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
			try (DirectoryStream<Path> entries = Files.newDirectoryStream(path)) {
				for (Path entry : entries) {
					if(Files.isRegularFile(entry)) { Files.delete(entry); }
					else if(Files.isDirectory(entry)) deleteDirectoryRecursion(entry);
				}
			}
		}
		if(Files.exists(path)) Files.delete(path);
	}

	@Test
	void testInsertRecursiveWithProperStructure() throws Exception {
		// Prepare file objects (no actual files needed)
		File fileRoot = new File(tempRoot, "fileRoot.txt"); fileRoot.createNewFile();

		File folderA = new File(tempRoot, "folderA"); folderA.mkdir();
		File a1 = new File(folderA, "a1.txt"); a1.createNewFile();
		File a2 = new File(folderA, "a2.txt"); a2.createNewFile();

		File subfolder1 = new File(folderA, "subfolder1"); subfolder1.mkdir();
		File sub1 = new File(subfolder1, "sub1.txt"); sub1.createNewFile();
		File sub2 = new File(subfolder1, "sub2.txt"); sub2.createNewFile();

		File subfolder2 = new File(tempRoot, "subfolder2"); subfolder2.mkdir();
		File anotherSub1 = new File(subfolder2, "anotherSub1.txt"); anotherSub1.createNewFile();
		File anotherSub2 = new File(subfolder2, "anotherSub2.txt"); anotherSub2.createNewFile();

		File folderB = new File(tempRoot, "folderB"); folderB.mkdir();
		File b1 = new File(folderB, "b1.txt"); b1.createNewFile();

		// Insert fileRoot.txt at root
		insertMethod.invoke(treeUI, fileRoot, buildPathMethod.invoke(treeUI, root), root);

		// Add folderA
		insertMethod.invoke(treeUI, folderA, buildPathMethod.invoke(treeUI, root), root);

		//  Add subfolder2/ under folderA 
		DefaultMutableTreeNode folderANode = findChild(root, "folderA");
		insertMethod.invoke(treeUI, subfolder2, buildPathMethod.invoke(treeUI, folderANode), folderANode);

		//  Add folderB/ 
		insertMethod.invoke(treeUI, folderB, buildPathMethod.invoke(treeUI, root), root);


		// root: fileRoot.txt, folderA/, folderB/
		assertEquals(3, root.getChildCount());

		// Validate fileRoot.txt
		DefaultMutableTreeNode fileRootNode = findChild(root, "fileRoot.txt");
		SelectedFile fr = (SelectedFile) fileRootNode.getUserObject();
		assertEquals(fileRoot.getAbsolutePath(), fr.actual().getAbsolutePath());
		assertEquals(fileRoot.getName(), fr.relative());

		DefaultMutableTreeNode subfolder1Node = findChild(folderANode, "subfolder1");
		DefaultMutableTreeNode subfolder2Node = findChild(folderANode, "subfolder2");
		DefaultMutableTreeNode folderBNode = findChild(root, "folderB");

		// folderA: a1.txt, a2.txt, subfolder1/, subfolder2/
		assertSelectedFile(findChild(folderANode, "a1.txt"), a1, "folderA/a1.txt");
		assertSelectedFile(findChild(folderANode, "a2.txt"), a2, "folderA/a2.txt");

		// subfolder1: sub1.txt, sub2.txt
		assertSelectedFile(findChild(subfolder1Node, "sub1.txt"), sub1, "folderA/subfolder1/sub1.txt");
		assertSelectedFile(findChild(subfolder1Node, "sub2.txt"), sub2, "folderA/subfolder1/sub2.txt");

		// subfolder2: anotherSub1.txt, anotherSub2.txt
		assertSelectedFile(findChild(subfolder2Node, "anotherSub1.txt"), anotherSub1, "folderA/subfolder2/anotherSub1.txt");
		assertSelectedFile(findChild(subfolder2Node, "anotherSub2.txt"), anotherSub2, "folderA/subfolder2/anotherSub2.txt");

		// folderB: b1.txt
		assertSelectedFile(findChild(folderBNode, "b1.txt"), b1, "folderB/b1.txt");
	}

	private DefaultMutableTreeNode findChild(DefaultMutableTreeNode parent, String displayName) {
		Enumeration<?> e = parent.children();
		while (e.hasMoreElements()) {
			DefaultMutableTreeNode node = (DefaultMutableTreeNode) e.nextElement();
			Object userObj = node.getUserObject();
			if (userObj instanceof String && displayName.equals(userObj)) return node;
			if (userObj instanceof SelectedFile && displayName.equals(((SelectedFile) userObj).actual().getName()))
				return node;
		}
		fail("Child node '" + displayName + "' not found in " + parent.getUserObject());
		//Collections.list(root.children()).stream().map(t -> (DefaultMutableTreeNode)t)
		//	.map(DefaultMutableTreeNode::getUserObject).map(Object::toString).collect(Collectors.joining("\t\n")))
		return null;
	}

	private void assertSelectedFile(DefaultMutableTreeNode node, File expectedFile, String expectedRelativePath) {
		assertNotNull(node, "Expected node not found: " + expectedFile.getName());
		Object obj = node.getUserObject();
		assertTrue(obj instanceof SelectedFile, "Node should contain SelectedFile: " + expectedFile.getName());
		SelectedFile sf = (SelectedFile) obj;
		assertEquals(expectedFile.getAbsolutePath(), sf.actual().getAbsolutePath(), "Actual file mismatch");
		assertEquals(expectedRelativePath, sf.relative(), "Relative path mismatch");
	}
}

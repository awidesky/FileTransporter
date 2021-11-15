package main;

import java.awt.Dimension;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JTextField;

import clientSide.ClientFrame;
import serverSide.ServerFrame;

public class InitFrame extends JFrame {

	/**
	 * 
	 */
	private static final long serialVersionUID = 7671613620610932124L;
	private JButton server = new JButton("Start a Server");
	private JButton client = new JButton("Start a Client");

	private JLabel tpChunk = new JLabel("TransferChunk : ");
	private JTextField tpChunk_t = new JTextField("1mb");
	
	public InitFrame() {
		
			setTitle("FileTransporter " + Main.version);
			//setIconImage(new ImageIcon().getImage());
			setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
			addWindowListener(new WindowAdapter() {

				@Override
				public void windowClosing(WindowEvent e) {
					
					e.getWindow().dispose();
					Main.log("InitFrame was closed");
					Main.kill(1);

				}

			});
			setSize(270, 140); //add more height than fxml because it does not think about title length
			Dimension dim = Toolkit.getDefaultToolkit().getScreenSize();
			setLocation(dim.width/2-getSize().width/2, dim.height/2-getSize().height/2);
			setLayout(null);
			setResizable(false);

			
			tpChunk.setBounds(14, 100, tpChunk.getPreferredSize().width, tpChunk.getPreferredSize().height);
			tpChunk_t.setBounds(110, 100, 140, 22);

			server.setBounds(90, 20, 90, 22);
			server.setMargin(new Insets(0, 0, 0, 0));
			
			client.setBounds(90, 60, 90, 22);
			client.setMargin(new Insets(0, 0, 0, 0));
			
			server.addActionListener((e) -> {
				checkArguments();
				dispose();
				Main.setFrame(new ServerFrame());
			});
			client.addActionListener((e) -> {
				checkArguments();
				dispose();
				Main.setFrame(new ClientFrame());
			});
		
	}

	/**
	 * Check it all argument is valid, and send them to <code>Main</code>
	 * */
	private void checkArguments() {
		
		Main.setTransferChunk(tpChunk_t.getText());

	}
}

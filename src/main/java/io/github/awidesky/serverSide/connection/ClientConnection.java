package io.github.awidesky.serverSide.connection;

import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Future;
import java.util.function.Consumer;

import io.github.awidesky.Main;
import io.github.awidesky.guiUtil.SwingDialogs;
import io.github.awidesky.guiUtil.TaskLogger;
import io.github.awidesky.serverSide.ClientListTableModel.FileProgress;
import io.github.awidesky.serverSide.selectedFile.SelectedFile;


/**
 * 
 *  Represent single connection to client(receiver).
 *  
 *  
 * */
public abstract class ClientConnection implements Runnable {

	//protected SocketChannelWrapper sendTo;
	protected SocketChannel sendTo;
	protected InetSocketAddress remoteAddr;
	
	protected ConcurrentLinkedQueue<FileProgress> fileQueue;
	protected Map<SelectedFile, String> hashes;
	protected boolean sendHash;
	
	protected boolean isAborted = false; //TODO : cancel logic, use future.iscanceled
	
	private Future<?> future;
	private Consumer<ClientConnection> finishCallback;
	
	protected FileProgress fileProgress;
	
	protected TaskLogger logger;
	
	public void init(SocketChannel socket, InetSocketAddress remoteAddr, String id, boolean sendHash) {
		this.sendTo = socket;
		this.remoteAddr = remoteAddr;
		this.sendHash = sendHash;
		this.logger = Main.getLogger("[%s-%s] ".formatted(id, remoteAddr.toString()));
	}
	
	public void setFinishCallback(Consumer<ClientConnection> finishCallback) {
		this.finishCallback = finishCallback;
	}
	
	public void setFileQueue(ConcurrentLinkedQueue<FileProgress> fileQueue) {
		this.fileQueue = fileQueue;
	}
	
	public void setHashes(Map<SelectedFile, String> hashes) {
		this.hashes = hashes;
	}
	
	public void setFuture(Future<?> f) {
		this.future = f;
		if(isAborted) {
			SwingDialogs.error("Connection lost!", "Not connected to client!", null, true);
			disconnect();
		}
	}
	
	
	/**
	 * Start process of sending files.<br>
	 * If the process somehow aborted in the middle of sending, it will throw a Exception.<br>
	 * When you try to re-call this method, it can't restart sending and it will throw a Exception,<br>
	 *  since it has no idea which position of the curFile it should send first.<br>
	 *   
	 * @return <code>true</code> if all the sending process went well. if not, <code>false</code>
	 * 
	 * */
	public void run() {
		while((fileProgress = fileQueue.poll()) != null) {
			send();
		}
		
		logger.info("Transport finished. Try connection close...");
		closeConnection();
		
		logger.info("Connection closed. Tasked completed.");
		finishCallback.accept(this);
	}
	
	private void send() {
		fileProgress.setStatus("Starting...");

		SelectedFile curFile = fileProgress.getFile();
		logger.info("Start sending : " + curFile);
		if(fileProgress.getRetry() != 0) logger.info("Retry #" + fileProgress.getRetry());

		fileProgress.setStatus("Sending...");
		if (sendFile(curFile)) {
			if(fileProgress.retry()) {
				send();
			} else {
				SwingDialogs.error("Maximun retry #" + Main.maxRetry + " failed", "Skip file :\n" + curFile, null, true);
			}
			return;
		}
		
		logger.info("Sent " + curFile.actual().getName() + " successfully!");
		fileProgress.setProgress(100);
		fileProgress.setStatus("Completed!");

		return;
	}
	
	protected abstract boolean sendFile(SelectedFile f);
	protected abstract void closeConnection();


	/**
	 * note : This method is invoked in EDT
	 * */
	public void disconnect() {
		isAborted = true; //TODO : Atomic, and precise name
		future.cancel(true); //cancel the task
	}

	public String getAddress() {
		return remoteAddr.toString();
	}

}


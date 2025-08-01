/*
 * Copyright (c) 2025 Eugene Hong
 *
 * This software is distributed under license. Use of this software
 * implies agreement with all terms and conditions of the accompanying
 * software license.
 * Please refer to LICENSE
 * */

package io.github.awidesky.serverSide;

import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;
import java.util.UUID;
import java.util.Vector;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

import io.github.awidesky.guiUtil.SwingDialogs;
import io.github.awidesky.serverSide.ClientListTableModel.FileProgress;

public class ConnectedClient {
	
	private Vector<ClientConnection> connList = new Vector<>();
	private final UUID uuid; //TODO : needed?
	private ConcurrentLinkedQueue<FileProgress> fileQueue;
	
	
	public ConnectedClient(UUID uuid) {
		this.uuid = uuid;
	}
	
	public ClientConnection addChannel(SocketChannel channel, InetSocketAddress remoteAddr) {
		ClientConnection connection = new ClientConnection(channel, remoteAddr, uuid.toString().substring(0, 8), fileQueue, connList::remove);
		connList.add(connection);
		return connection;
	}

	public String getUUID() {
		return uuid.toString();
	}

	public void setFileQueue(ConcurrentLinkedQueue<FileProgress> fileQueue) {
		this.fileQueue = fileQueue;
	}
	
	public boolean isCompleted() {
		return connList.isEmpty();
	}
	
	public boolean disconnect() {
		if (connList.isEmpty() || SwingDialogs.confirm("Before Disconnecting!",
				"Some connection(s) are not closed yet!\nDisconnect all connection(s) and remove client?\n\nOpened connection(s) :\n"
					+ connList.stream().map(ClientConnection::getAddress).collect(Collectors.joining("\n")))) {
			connList.forEach(ClientConnection::disconnect);
			return true;
		} else return false;
	}
}

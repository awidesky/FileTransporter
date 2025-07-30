/*
 * Copyright (c) 2025 Eugene Hong
 *
 * This software is distributed under license. Use of this software
 * implies agreement with all terms and conditions of the accompanying
 * software license.
 * Please refer to LICENSE
 * */

package com.awidesky.serverSide;

import java.io.File;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.awidesky.Main;
import com.awidesky.util.TaskLogger;

public class ConnectedClient {
	
	private List<ClientConnection> connList = new LinkedList<>();
	private ConcurrentLinkedQueue<File> fileQueue;
	private final UUID uuid; //TODO : needed?
	
	
	public ConnectedClient(UUID uuid, File[] files) {
		this.uuid = uuid;
		this.fileQueue = new ConcurrentLinkedQueue<>(Arrays.asList(files));
	}
	
	
	public ClientConnection addChannel(SocketChannel channel, InetSocketAddress remoteAddr) {
		TaskLogger logger = Main.getLogger("[%s_%s] ".formatted(uuid.toString().substring(0, 9), remoteAddr.toString()));
		ClientConnection connection = new ClientConnection(logger, channel, remoteAddr, fileQueue);
		connList.add(connection);
		return connection;
	}

}

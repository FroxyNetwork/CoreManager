package com.froxynetwork.coremanager.server;

import java.util.HashMap;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.froxynetwork.coremanager.scheduler.Scheduler;
import com.froxynetwork.coremanager.websocket.WebSocketServerImpl;

import lombok.Getter;
import lombok.Setter;

/**
 * MIT License
 *
 * Copyright (c) 2020 FroxyNetwork
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 * 
 * @author 0ddlyoko
 */
/**
 * Represent an instance of a VPS
 */
public class VPS {
	private final Logger LOG = LoggerFactory.getLogger(getClass());
	@Getter
	private String id;
	@Getter
	private String url;
	@Getter
	private int port;
	@Getter
	private int maxServers;
	private HashMap<String, Server> servers;
	private HashMap<String, TempServer> tempServers;
	private boolean close;
	@Setter
	private WebSocketServerImpl webSocket;

	public VPS(String id, String url, int port, int maxServers) {
		this.id = id;
		this.url = url;
		this.port = port;
		this.maxServers = maxServers;
		servers = new HashMap<>();
		tempServers = new HashMap<>();
		this.close = false;
	}

	public Server getServer(String id) {
		return servers.get(id);
	}

	public void openServer(String type, Consumer<Server> then, Consumer<Error> error) {
		// Check if there is a WebSocket connection
		if (!isLinked()) {
			Scheduler.add(() -> true, null);
		}
	}
	
	private boolean _openServer(String type, Consumer<Server> then, Consumer<Error> error) {
		
	}

	public void closeServer(String id) {
		// TODO
	}

	/**
	 * Return the score of this VPS or 0 if full<br />
	 * 1 + number of servers + (2 * number of temp servers)<br />
	 * <b>2 * number of temp servers</b> is used to avoid creating a lot of servers
	 * at the same time for the same machine<br />
	 * We add 1 to avoid returning 0 if there is not servers running on this vps<br />
	 * Returns 0 if VPS is full or there is not WebSocket connection
	 * 
	 * @return 1 + number of servers + 2 * number of temp servers
	 */
	public int getScore() {
		if ((servers.size() + tempServers.size()) >= maxServers)
			return 0;
		// Do not create a new server if it's not linked
		if (!isLinked())
			return 0;
		return 1 + servers.size() + 2 * tempServers.size();
	}

	/**
	 * Send a message throw WebSocket to this VPS
	 * 
	 * @param message The message to send
	 */
	public void sendMessage(String message) {
		// TODO
	}

	/**
	 * Called when this VPS has send a message to the Core
	 * 
	 * @param message The message sent by this VPS
	 */
	public void onMessage(String message) {
		// TODO
	}

	public boolean isLinked() {
		return webSocket != null && webSocket.isConnected();
	}

	/**
	 * Unload this vps and close WebSocket connection
	 */
	public void unload() {
		// Avoid unloading multiple time
		if (close)
			return;
		LOG.info("Unloading vps {}", id);
		close = true;
		if (webSocket != null)
			webSocket.disconnect();
		LOG.info("VPS {} unloaded", id);
	}
}

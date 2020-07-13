package com.froxynetwork.coremanager.server;

import java.util.HashMap;
import java.util.UUID;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.froxynetwork.coremanager.Main;
import com.froxynetwork.coremanager.scheduler.Scheduler;
import com.froxynetwork.coremanager.server.config.ServerConfig;
import com.froxynetwork.coremanager.server.config.ServerVps;
import com.froxynetwork.froxynetwork.network.output.Callback;
import com.froxynetwork.froxynetwork.network.output.RestException;
import com.froxynetwork.froxynetwork.network.output.data.server.ServerDataOutput;
import com.froxynetwork.froxynetwork.network.websocket.WebSocketServerImpl;

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
	private ServerVps vps;
	@Getter
	@Setter
	private Server bungee;
	private boolean creatingBungee = false;
	private HashMap<String, Server> servers;
	private HashMap<UUID, TempServer> tempServers;
	private boolean close;
	@Getter
	@Setter
	private WebSocketServerImpl webSocket;

	private Thread vpsThread;

	public VPS(ServerVps vps) {
		this.id = vps.getId();
		this.vps = vps;
		servers = new HashMap<>();
		tempServers = new HashMap<>();
		this.close = false;
		vpsThread = new Thread(() -> {
			// This thread will start servers if there is not required servers
			// Limit at 5 starts every 10 seconds
			int limit = 5;
			while (!close) {
				try {
					// Sleep 10 seconds
					Thread.sleep(1000 * 10);
				} catch (InterruptedException ex) {
					LOG.info("Got an interruptedException");
					break;
				}
				// Don't check if VPS is not linked
				if (!isLinked()) {
					LOG.error("VPS {} is not linked !", id);
					continue;
				}
				// Check bungee
				if (bungee == null && !creatingBungee) {
					// Ask to start the bungee
					creatingBungee = true;
					openServer("BUNGEE", bungee -> {
						LOG.info("Bungee started on VPS {}", id);
						creatingBungee = false;
					}, () -> {
						LOG.error("Error while starting server type BUNGEE on vps {}", id);
						creatingBungee = false;
					}, false);
				}

//				// Check if the maximum amount of running server has been reached
//				if (vps.getMaxServers() >= (servers.size() + tempServers.size()))
//					continue;
				int nbr = 0;
				for (ServerConfig sc : Main.get().getServerConfigManager().getAll()) {
					if (nbr >= limit)
						break;
					String type = sc.getType();
					int min = sc.getMin();
					int amount = 0;
					for (Server srv : servers.values())
						if (srv.getType().equalsIgnoreCase(type))
							amount++;
					for (TempServer srv : tempServers.values())
						if (srv.getType().equalsIgnoreCase(type))
							amount++;

					if (amount < min) {
						// Start servers
						for (int i = 0; i < min - amount && nbr < limit; i++, nbr++)
							openServer(type, srv -> {
								LOG.info("Server id {} of type {} started !", srv.getId(), srv.getType());
							}, () -> {
								LOG.error("Error while starting server type {} on vps {}", type, id);
							}, false);
					}
				}
			}
		});
		vpsThread.start();
	}

	public Server getServer(String id) {
		return servers.get(id);
	}

	public void openServer(String type, Consumer<Server> then, Runnable error, boolean force) {
		if (force) {
			// Run _openServer every seconds until the action is executed
			Scheduler.add(() -> _openServer(type, then, error), error);
		} else {
			_openServer(type, then, error);
		}
	}

	private boolean _openServer(String type, Consumer<Server> then, Runnable error) {
		if (!isLinked()) {
			LOG.error(Error.NOTCONNECTED.getError(), id);
			return false;
		}
		// Generate unique id
		UUID randomUUID = UUID.randomUUID();
		// In theory, this is not possible but we check to be sure
		while (tempServers.containsKey(randomUUID))
			randomUUID = UUID.randomUUID();
		// Save
		TempServer ts = new TempServer(randomUUID, type, then, error);
		tempServers.put(randomUUID, ts);
		// Send message to VPS
		LOG.debug("Trying to open server type {} with uuid {}", type, randomUUID.toString());
		sendMessage("start", randomUUID.toString() + " " + type);
		return true;
	}

	public void closeServer(String id, Runnable error) {
		Scheduler.add(() -> _closeServer(id), error);
	}

	private boolean _closeServer(String id) {
		if (!isLinked()) {
			LOG.error(Error.NOTCONNECTED.getError(), id);
			return false;
		}
		// Send message to VPS
		sendMessage("stop", id);
		servers.remove(id);
		return true;
	}

	public void registerServer(Server srv) {
		servers.put(srv.getId(), srv);
	}

	public void unregisterServer(String id) {
		servers.remove(id);
	}

	/**
	 * Return the score of this VPS or 0<br />
	 * 1 + number of servers + (2 * number of temp servers)<br />
	 * <b>2 * number of temp servers</b> is used to avoid creating a lot of servers
	 * at the same time for the same machine<br />
	 * We add 1 to avoid returning 0 if there is not servers running on this
	 * vps<br />
	 * Returns 0 if there is not WebSocket connection, VPS is full or vps has
	 * reached maximum type
	 * 
	 * @return 1 + number of servers + 2 * number of temp servers
	 */
	public int getScore(String type) {
		if ((servers.size() + tempServers.size()) >= vps.getMaxServers())
			return 0;
		// Do not create a new server if it's not linked
		if (!isLinked())
			return 0;
		// Do not create a server if maximum type is reached
		int max = vps.getMax(type);
		int count = count(type);
		if (count >= max)
			return 0;
		return 1 + servers.size() + 2 * tempServers.size();
	}

	/**
	 * Send a message throw WebSocket to this VPS
	 * 
	 * @param message The message to send
	 */
	public void sendMessage(String channel, String message) {
		Scheduler.add(() -> {
			if (!isLinked() || !webSocket.isAuthenticated())
				return false;
			try {
				webSocket.sendCommand(channel, message);
			} catch (Exception ex) {
				LOG.error("Error while sending a message to VPS {} with channel {}", id, channel);
				LOG.error("", ex);
				return false;
			}
			return true;
		}, null);
	}

	/**
	 * Check if this VPS is linked with the CoreManager
	 * 
	 * @return true if there is a WebSocket connection between the CoreManager and
	 *         this VPS
	 */
	public boolean isLinked() {
		return webSocket != null && webSocket.isConnected();
	}

	/**
	 * Check if specific id is linked to a registered server and this server is
	 * linked with this VPS
	 * 
	 * @param id The id of the server to check
	 * @return true if this id is linked to a server that is in this VPS
	 */
	public boolean has(String id) {
		return servers.containsKey(id);
	}

	/**
	 * Check if specific uuid is a temp server created by this VPS
	 * 
	 * @param uuid The uuid of the temp server
	 * @return true if this uuid is a temp server of this VPS
	 */
	public boolean hasTemp(UUID uuid) {
		return tempServers.containsKey(uuid);
	}

	/**
	 * Count the number of running and temp servers that is of specific type
	 * 
	 * @param type The type
	 * @return The number of running and temp servers that are of specific type
	 */
	public int count(String type) {
		int count = 0;
		for (Server srv : servers.values())
			if (srv.getType().equalsIgnoreCase(type))
				count++;
		return count;
	}

	/**
	 * Called by "register" request<br />
	 * Send an "error" message if uuid or id isn't linked to a server
	 * 
	 * @param uuid
	 */
	public void onRegister(UUID uuid, String id) {
		LOG.debug("newServer: id = {}, uuid {}", id, uuid.toString());
		TempServer ts = tempServers.remove(uuid);
		if (ts == null) {
			LOG.error("Got new server with id = {} and uuid = {} but this uuid isn't listed, stopping this server", id,
					uuid.toString());
			// Send stop command
			sendMessage("stop", id);
			return;
		}
		// Get id from REST
		Main.get().getNetworkManager().getNetwork().getServerService().asyncGetServer(id,
				new Callback<ServerDataOutput.Server>() {

					@Override
					public void onResponse(ServerDataOutput.Server response) {
						// Okay, this server is now loaded (Let's check to be sure the VPS of this
						// server)
						if (!response.getVps().equalsIgnoreCase(getId())) {
							LOG.error("Server {} doesn't have vps id {} but has {}", id, getId(), response.getVps());
							sendMessage("stop", id);
							return;
						}
						LOG.info("newServer: id = {}", id);
						Server server = new Server(response, VPS.this);
						if ("BUNGEE".equalsIgnoreCase(ts.getType()))
							bungee = server;
						else
							servers.put(id, server);
						// Notify all servers
						for (VPS vps : Main.get().getServerManager().getVps())
							vps.sendMessage("register", id + " " + response.getType());
						// Execute then action
						ts.then(server);
					}

					@Override
					public void onFailure(RestException ex) {
						LOG.error("Failure #{} while getting server {}", ex.getError().getErrorId(), id);
						LOG.error("", ex);
						sendMessage("stop", id);
					}

					@Override
					public void onFatalFailure(Throwable t) {
						LOG.error("Fatal Failure while getting server {}", id);
						LOG.error("", t);
						sendMessage("stop", id);
					}
				});
	}

	public void error(UUID uuid) {
		TempServer ts = tempServers.remove(uuid);
		if (ts == null)
			return;
		LOG.debug("newServer error on vps {}: uuid {}", id, uuid.toString());
		ts.error();
	}

	public void onUnregister(String id, String type) {
		// Remove from VPS
		if ("BUNGEE".equalsIgnoreCase(type)) {
			if (bungee != null && id.equalsIgnoreCase(bungee.getId()))
				bungee = null;
		} else {
			servers.remove(id);
		}
		// Send a close request
		for (VPS v : Main.get().getServerManager().getVps()) {
			v.unregisterServer(id);
			v.sendMessage("unregister", id + " " + type);
		}
	}

	/**
	 * Unload this vps and close WebSocket connection
	 */
	public void unload() {
		// Avoid unloading multiple time
		if (close)
			return;
		close = true;
		LOG.info("Unloading vps {}", id);
		if (webSocket != null)
			webSocket.disconnect();
		vpsThread.interrupt();
		LOG.info("VPS {} unloaded", id);
	}

	public int getMaxServers() {
		return vps.getMaxServers();
	}
}

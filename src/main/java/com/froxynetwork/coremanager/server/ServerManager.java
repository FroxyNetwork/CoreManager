package com.froxynetwork.coremanager.server;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.froxynetwork.coremanager.Main;
import com.froxynetwork.coremanager.server.config.ServerVps;

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
public class ServerManager {
	private final Logger LOG = LoggerFactory.getLogger(getClass());

	private HashMap<String, VPS> vps;

	public ServerManager() {
		vps = new HashMap<>();
	}

	/**
	 * Remove WebSocket connection for all VPS, unload VPS and
	 */
	public void reload() {
		LOG.info("Reloading ...");
		LOG.info("Unloading all VPS");
		for (VPS vps : this.vps.values()) {
			vps.unload();
		}
		for (ServerVps vps : Main.get().getServerConfigManager().getVps()) {
			VPS v = new VPS(vps.getId(), vps.getHost(), vps.getPort(), vps.getMaxServers());
			this.vps.put(vps.getId(), v);
		}
	}

	/**
	 * Find an optimal VPS and call
	 * {@link VPS#openServer(String, Consumer, Runnable)} on this VPS<br />
	 * If no vps has been found or type is not a valid type, call error variable
	 * 
	 * @param type
	 * @param then
	 * @param error
	 */
	public void openServer(String type, Consumer<Server> then, Consumer<Error> error) {
		// Check if type is a valid type
		if (!Main.get().getServerConfigManager().exist(type)) {
			LOG.error(Error.TYPENOTFOUND.getError(), type);
			error.accept(Error.TYPENOTFOUND);
			return;
		}
		// Find an optimal VPS
		VPS vps = findOptimalVPS();
		if (vps == null) {
			LOG.error(Error.FULL.getError(), type);
			error.accept(Error.FULL);
			return;
		}
		vps.openServer(type, then, () -> {
			LOG.info("Unknown error while opening server type {} on vps {}", type, vps.getId());
			error.accept(Error.UNKNOWN);
		});
	}

	public void closeServer(String id, Runnable error) {
		// Find which vps has this id
		for (VPS v : this.vps.values())
			if (v.has(id))
				v.closeServer(id, () -> {
					LOG.error("Unknown error while closing server {} on vps {}", id, v.getId());
					error.run();
				});
	}

	/**
	 * Iterate over each VPS to find the specific server and returns it. Returns
	 * null if not found
	 * 
	 * @param id The id of the server
	 * @return The server associated with the id or null if not found
	 */
	public Server getServer(String id) {
		Server srv = null;
		for (VPS vps : vps.values())
			if ((srv = vps.getServer(id)) != null)
				break;
		return srv;
	}

	public VPS getVPS(String id) {
		return vps.get(id);
	}

	/**
	 * Iterate over each VPS to find an optimal VPS that'll be used to create a new
	 * server on this VPS
	 * 
	 * @return An optimal server
	 */
	public VPS findOptimalVPS() {
		VPS v = null;
		int score = 0;
		for (VPS vps : this.vps.values()) {
			int vpsScore = vps.getScore();
			if (vpsScore != 0 && (score == 0 || vpsScore < score)) {
				v = vps;
				score = vpsScore;
			}
		}
		return v;
	}

	public List<VPS> getVps() {
		return new ArrayList<>(vps.values());
	}
}

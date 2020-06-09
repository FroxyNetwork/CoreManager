package com.froxynetwork.coremanager.websocket.commands;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.froxynetwork.coremanager.Main;
import com.froxynetwork.coremanager.server.VPS;
import com.froxynetwork.froxynetwork.network.websocket.IWebSocketCommander;
import com.froxynetwork.froxynetwork.network.websocket.WebSocketServerImpl;

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
public class ServerErrorCommand implements IWebSocketCommander {
	private final Logger LOG = LoggerFactory.getLogger(getClass());
	private WebSocketServerImpl webSocket;

	public ServerErrorCommand(WebSocketServerImpl webSocket) {
		this.webSocket = webSocket;
	}

	@Override
	public String name() {
		return "error";
	}

	@Override
	public String description() {
		return "When an error occured while creating a server";
	}

	@Override
	public void onReceive(String message) {
		// error <uuid>
		if (message == null)
			return;
		if (!webSocket.isAuthenticated()) {
			LOG.error("Got command \"error\" but this WebSocket is not authentified");
			return;
		}
		UUID uuid = null;
		try {
			uuid = UUID.fromString(message);
		} catch (Exception ex) {
			LOG.warn("{} is not a valid uuid", message);
			return;
		}
		for (VPS vps : Main.get().getServerManager().getVps())
			vps.error(uuid);
	}
}

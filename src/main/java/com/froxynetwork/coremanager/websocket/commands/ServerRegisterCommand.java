package com.froxynetwork.coremanager.websocket.commands;

import java.util.UUID;

import org.java_websocket.framing.CloseFrame;
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
public class ServerRegisterCommand implements IWebSocketCommander {
	private final Logger LOG = LoggerFactory.getLogger(getClass());
	private WebSocketServerImpl webSocket;

	public ServerRegisterCommand(WebSocketServerImpl webSocket) {
		this.webSocket = webSocket;
	}

	@Override
	public String name() {
		return "register";
	}

	@Override
	public String description() {
		return "New server registered";
	}

	@Override
	public void onReceive(String message) {
		// register <uuid> <id>
		if (message == null)
			return;
		if (!webSocket.isAuthenticated()) {
			LOG.error("Got command \"register\" but this WebSocket is not authentified");
			return;
		}
		String[] args = message.split(" ");
		if (args.length != 2) {
			LOG.warn("Invalid \"register\" command ! Got {}", message);
			return;
		}
		UUID uuid = null;
		try {
			uuid = UUID.fromString(args[0]);
		} catch (Exception ex) {
			LOG.warn("{} is not a valid uuid", message);
			return;
		}
		String id = args[1];
		// Check if uuid is registered
		VPS vps = Main.get().getWebSocketManager().get(webSocket);
		if (vps == null) {
			// WTF ?
			LOG.error("No VPS found for webSocket ! Closing it");
			webSocket.disconnect(CloseFrame.NORMAL, "No VPS link Found");
			return;
		}
		vps.newServer(uuid, id);
	}
}

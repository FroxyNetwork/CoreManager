package com.froxynetwork.coremanager.websocket;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.HashMap;

import org.java_websocket.framing.CloseFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.froxynetwork.coremanager.Main;
import com.froxynetwork.coremanager.server.VPS;
import com.froxynetwork.coremanager.websocket.commands.ServerErrorCommand;
import com.froxynetwork.coremanager.websocket.commands.ServerRegisterCommand;
import com.froxynetwork.coremanager.websocket.commands.ServerUnregisterCommand;
import com.froxynetwork.froxynetwork.network.websocket.WebSocketFactory;
import com.froxynetwork.froxynetwork.network.websocket.WebSocketServer;
import com.froxynetwork.froxynetwork.network.websocket.WebSocketServerImpl;
import com.froxynetwork.froxynetwork.network.websocket.auth.WebSocketTokenAuthentication;

import lombok.Getter;

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
public class WebSocketManager {
	private final Logger LOG = LoggerFactory.getLogger(getClass());
	@Getter
	private WebSocketServer webSocketServer;
	private HashMap<WebSocketServerImpl, VPS> links;

	public WebSocketManager(String url, int port) {
		links = new HashMap<>();
		webSocketServer = WebSocketFactory.server(new InetSocketAddress(url, port),
				new WebSocketTokenAuthentication(Main.get().getNetworkManager()));
		webSocketServer.registerWebSocketConnection(this::onNewConnection);
		webSocketServer.start();
	}

	private void onNewConnection(WebSocketServerImpl wssi) {
		wssi.registerWebSocketAuthentication(() -> {
			Object obj = wssi.get(WebSocketTokenAuthentication.TOKEN);
			String id = obj == null ? null : obj.toString();
			if (id == null || "".equalsIgnoreCase(id.trim())) {
				// Wtf ?
				LOG.error("WebSocket is authentified but doesn't have an id ! Closing it");
				wssi.disconnect(CloseFrame.NORMAL, "Id doesn't exist");
				return;
			}
			VPS vps = Main.get().getServerManager().getVPS(id);
			if (vps == null) {
				LOG.error("WebSocket tried to authenticate as vps {} but this vps doesn't exist", id);
				wssi.disconnect(CloseFrame.NORMAL, "Vps doesn't exist");
				return;
			}
			if (vps.getWebSocket() != null && vps.getWebSocket().isConnected()) {
				LOG.error("WebSocket tried to authenticate as vps {} but there is already a link", id);
				wssi.disconnect(CloseFrame.NORMAL, "Vps already connected");
				return;
			}
			vps.setWebSocket(wssi);
			links.put(wssi, vps);
		});
		wssi.registerCommand(new ServerErrorCommand(wssi));
		wssi.registerCommand(new ServerRegisterCommand(wssi));
		wssi.registerCommand(new ServerUnregisterCommand(wssi));
		wssi.registerWebSocketDisconnection(remote -> {
			links.remove(wssi);
			Object obj = wssi.get(WebSocketTokenAuthentication.TOKEN);
			if (obj == null)
				return;
			VPS vps = Main.get().getServerManager().getVPS(obj.toString());
			if (vps == null)
				return;
			vps.setWebSocket(null);
		});
	}

	public VPS get(WebSocketServerImpl wssi) {
		return links.get(wssi);
	}

	public void stop() {
		for (WebSocketServerImpl wssi : links.keySet())
			wssi.closeAll();
		try {
			webSocketServer.stop();
		} catch (IOException ex) {
			ex.printStackTrace();
		} catch (InterruptedException ex) {
			ex.printStackTrace();
		}
	}
}

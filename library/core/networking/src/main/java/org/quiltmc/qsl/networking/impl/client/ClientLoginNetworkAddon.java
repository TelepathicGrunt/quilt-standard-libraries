/*
 * Copyright 2022 QuiltMC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.quiltmc.qsl.networking.impl.client;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientLoginNetworkHandler;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.c2s.login.LoginQueryResponseC2SPacket;
import net.minecraft.network.packet.s2c.login.LoginQueryRequestS2CPacket;
import net.minecraft.util.Identifier;

import org.quiltmc.qsl.networking.api.FutureListeners;
import org.quiltmc.qsl.networking.api.PacketByteBufs;
import org.quiltmc.qsl.networking.api.client.ClientLoginConnectionEvents;
import org.quiltmc.qsl.networking.api.client.ClientLoginNetworking;
import org.quiltmc.qsl.networking.impl.AbstractNetworkAddon;

@ApiStatus.Internal
@Environment(EnvType.CLIENT)
public final class ClientLoginNetworkAddon extends AbstractNetworkAddon<ClientLoginNetworking.QueryRequestReceiver> {
	private final ClientLoginNetworkHandler handler;
	private final MinecraftClient client;
	private boolean firstResponse = true;

	public ClientLoginNetworkAddon(ClientLoginNetworkHandler handler, MinecraftClient client) {
		super(ClientNetworkingImpl.LOGIN, "ClientLoginNetworkAddon for Client");
		this.handler = handler;
		this.client = client;

		ClientLoginConnectionEvents.INIT.invoker().onLoginStart(this.handler, this.client);
		this.receiver.startSession(this);
	}

	public boolean handlePacket(LoginQueryRequestS2CPacket packet) {
		return handlePacket(packet.getQueryId(), packet.getChannel(), packet.getPayload());
	}

	private boolean handlePacket(int queryId, Identifier channelName, PacketByteBuf originalBuf) {
		this.logger.debug("Handling inbound login response with id {} and channel with name {}", queryId, channelName);

		if (this.firstResponse) {
			// Register global handlers
			for (Map.Entry<Identifier, ClientLoginNetworking.QueryRequestReceiver> entry : ClientNetworkingImpl.LOGIN.getReceivers().entrySet()) {
				ClientLoginNetworking.registerReceiver(entry.getKey(), entry.getValue());
			}

			ClientLoginConnectionEvents.QUERY_START.invoker().onLoginQueryStart(this.handler, this.client);
			this.firstResponse = false;
		}

		@Nullable ClientLoginNetworking.QueryRequestReceiver handler = this.getHandler(channelName);

		if (handler == null) {
			return false;
		}

		PacketByteBuf buf = PacketByteBufs.slice(originalBuf);
		var futureListeners = new ArrayList<GenericFutureListener<? extends Future<? super Void>>>();

		try {
			CompletableFuture<@Nullable PacketByteBuf> future = handler.receive(this.client, this.handler, buf, futureListeners::add);
			future.thenAccept(result -> {
				var packet = new LoginQueryResponseC2SPacket(queryId, result);
				GenericFutureListener<? extends Future<? super Void>> listener = null;

				for (GenericFutureListener<? extends Future<? super Void>> each : futureListeners) {
					listener = FutureListeners.union(listener, each);
				}

				this.handler.getConnection().send(packet, listener);
			});
		} catch (Throwable ex) {
			this.logger.error("Encountered exception while handling in channel with name \"{}\"", channelName, ex);
			throw ex;
		}

		return true;
	}

	@Override
	protected void handleRegistration(Identifier channelName) {
	}

	@Override
	protected void handleUnregistration(Identifier channelName) {
	}

	@Override
	protected void invokeDisconnectEvent() {
		ClientLoginConnectionEvents.DISCONNECT.invoker().onLoginDisconnect(this.handler, this.client);
		this.receiver.endSession(this);
	}

	public void handlePlayTransition() {
		this.receiver.endSession(this);
	}

	@Override
	protected boolean isReservedChannel(Identifier channelName) {
		return false;
	}
}

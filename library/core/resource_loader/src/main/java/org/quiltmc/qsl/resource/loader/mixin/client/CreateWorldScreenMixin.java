/*
 * Copyright 2021-2022 QuiltMC
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

package org.quiltmc.qsl.resource.loader.mixin.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.gui.screen.world.CreateWorldScreen;
import net.minecraft.resource.pack.DataPackSettings;
import net.minecraft.resource.pack.ResourcePackManager;
import net.minecraft.server.WorldStem;

import org.quiltmc.qsl.resource.loader.api.ResourceLoaderEvents;
import org.quiltmc.qsl.resource.loader.impl.ModResourcePackUtil;

@Environment(EnvType.CLIENT)
@Mixin(CreateWorldScreen.class)
public class CreateWorldScreenMixin {
	@Redirect(
			method = "create",
			at = @At(value = "FIELD", target = "Lnet/minecraft/resource/pack/DataPackSettings;SAFE_MODE:Lnet/minecraft/resource/pack/DataPackSettings;")
	)
	private static DataPackSettings replaceDefaultSettings() {
		return ModResourcePackUtil.DEFAULT_SETTINGS;
	}

	@ModifyArg(
			method = "createFromExisting",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/gui/screen/world/CreateWorldScreen;<init>(Lnet/minecraft/client/gui/screen/Screen;Lnet/minecraft/resource/pack/DataPackSettings;Lnet/minecraft/client/gui/screen/world/MoreOptionsDialog;)V"
			),
			index = 1
	)
	private static DataPackSettings onNew(DataPackSettings settings) {
		return ModResourcePackUtil.createDefaultDataPackSettings(settings);
	}

	@Inject(
			method = "applyDataPacks",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/server/WorldStem;load(Lnet/minecraft/server/WorldStem$InitConfig;Lnet/minecraft/server/WorldStem$Supplier;Lnet/minecraft/server/WorldStem$WorldDataSupplier;Ljava/util/concurrent/Executor;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"
			)
	)
	private void onStartDataPackLoading(ResourcePackManager dataPackManager, CallbackInfo ci) {
		ResourceLoaderEvents.START_DATA_PACK_RELOAD.invoker().onStartDataPackReload(null, null);
	}

	// Lambda method in CreateWorldScreen#applyDataPacks, at CompletableFuture#thenAcceptAsync.
	// Take a ServerResourceManager parameter.
	@SuppressWarnings("target")
	@Inject(
			method = "method_37088(Lnet/minecraft/resource/pack/DataPackSettings;Lnet/minecraft/server/WorldStem;)V",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/server/WorldStem;close()V"
			)
	)
	private void onEndDataPackLoading(DataPackSettings dataPackSettings, WorldStem arg, CallbackInfo ci) {
		ResourceLoaderEvents.END_DATA_PACK_RELOAD.invoker().onEndDataPackReload(null, arg.resourceManager(), null);
	}

	// Lambda method in CreateWorldScreen#applyDataPacks, at CompletableFuture#handle.
	// Take Void and Throwable parameters.
	@SuppressWarnings("target")
	@Inject(
			method = "method_37089(Ljava/lang/Void;Ljava/lang/Throwable;)Ljava/lang/Object;",
			at = @At(
					value = "INVOKE",
					target = "Lorg/slf4j/Logger;warn(Ljava/lang/String;Ljava/lang/Throwable;)V",
					remap = false
			)
	)
	private void onFailDataPackLoading(Void unused, Throwable throwable, CallbackInfoReturnable<Object> cir) {
		ResourceLoaderEvents.END_DATA_PACK_RELOAD.invoker().onEndDataPackReload(null, null, throwable);
	}
}

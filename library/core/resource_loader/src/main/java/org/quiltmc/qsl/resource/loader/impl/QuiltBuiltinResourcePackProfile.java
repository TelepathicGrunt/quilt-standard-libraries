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

package org.quiltmc.qsl.resource.loader.impl;

import java.io.IOException;

import com.mojang.logging.LogUtils;
import org.jetbrains.annotations.ApiStatus;
import org.slf4j.Logger;

import net.minecraft.resource.ResourcePackCompatibility;
import net.minecraft.resource.ResourcePackProfile;
import net.minecraft.resource.metadata.PackResourceMetadata;

import org.quiltmc.qsl.resource.loader.api.ResourcePackActivationType;

@ApiStatus.Internal
public final class QuiltBuiltinResourcePackProfile extends ResourcePackProfile {
	private static final Logger LOGGER = LogUtils.getLogger();

	static QuiltBuiltinResourcePackProfile of(ModNioResourcePack pack) {
		try {
			PackResourceMetadata metadata = pack.parseMetadata(PackResourceMetadata.READER);
			if (metadata == null) {
				LOGGER.warn("Couldn't find pack meta for pack {}", pack.getName());
				return null;
			}

			return new QuiltBuiltinResourcePackProfile(pack, metadata);
		} catch (IOException e) {
			LOGGER.warn("Couldn't get pack info for: {}", e.toString());
			return null;
		}
	}

	private QuiltBuiltinResourcePackProfile(ModNioResourcePack pack, PackResourceMetadata metadata) {
		super(
				pack.getName(),
				pack.getDisplayName(),
				pack.getActivationType() == ResourcePackActivationType.ALWAYS_ENABLED,
				() -> pack,
				metadata,
				pack.type,
				ResourcePackProfile.InsertionPosition.TOP,
				ModResourcePackProvider.PACK_SOURCE_MOD_BUILTIN
		);
	}

	@Override
	public ResourcePackCompatibility getCompatibility() {
		// This is to ease multi-version mods whose built-in packs actually work across versions.
		return ResourcePackCompatibility.COMPATIBLE;
	}
}

/*
 * Copyright 2021 QuiltMC
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

package org.quiltmc.qsl.resource.loader.api;

import org.jetbrains.annotations.ApiStatus;

import net.minecraft.resource.ResourceType;

import org.quiltmc.qsl.resource.loader.api.reloader.IdentifiableResourceReloader;
import org.quiltmc.qsl.resource.loader.impl.ResourceLoaderImpl;

/**
 * Represents the resource loader. Contains different register methods.
 */
@ApiStatus.NonExtendable
public interface ResourceLoader {
	/**
	 * Get the resource loader instance for a given resource type.
	 * A resource loader instance may be used to register resource reloaders.
	 *
	 * @param type the given resource type
	 * @return the resource loader instance
	 */
	static ResourceLoader get(ResourceType type) {
		return ResourceLoaderImpl.get(type);
	}

	/**
	 * Register a resource reloader for a given resource manager type.
	 *
	 * @param resourceReloader the resource reloader
	 */
	void registerReloader(IdentifiableResourceReloader resourceReloader);
}

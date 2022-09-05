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

package org.quiltmc.qsl.resource.loader.api;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.google.common.base.Suppliers;
import com.mojang.logging.LogUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;
import org.slf4j.Logger;

import net.minecraft.resource.ResourceType;
import net.minecraft.resource.pack.AbstractFileResourcePack;
import net.minecraft.resource.pack.ResourcePack;
import net.minecraft.resource.pack.metadata.ResourceMetadataReader;
import net.minecraft.util.Identifier;

import org.quiltmc.loader.api.QuiltLoader;
import org.quiltmc.qsl.base.api.util.TriState;

/**
 * Represents an in-memory resource pack.
 * <p>
 * The resources of this pack are stored in memory instead of it being on-disk.
 */
public abstract class InMemoryResourcePack implements MutableResourcePack {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final boolean DUMP = TriState.fromProperty("quilt.resource_loader.debug.pack.dump_from_in_memory")
			.toBooleanOrElse(QuiltLoader.isDevelopmentEnvironment());
	private final Map<Identifier, Supplier<byte[]>> assets = new ConcurrentHashMap<>();
	private final Map<Identifier, Supplier<byte[]>> data = new ConcurrentHashMap<>();
	private final Map<String, Supplier<byte[]>> root = new ConcurrentHashMap<>();

	@Override
	public @Nullable InputStream openRoot(String fileName) {
		if (!fileName.contains("/") && !fileName.contains("\\")) {
			return this.openResource(this.root, fileName);
		} else {
			throw new IllegalArgumentException("Root resources can only be filenames, not paths (no / allowed!)");
		}
	}

	@Override
	public InputStream open(ResourceType type, Identifier id) throws IOException {
		var stream = this.openResource(this.getResourceMap(type), id);

		if (stream == null) {
			throw new FileNotFoundException("Could not find resource \"" + id + "\" (" + type.getDirectory() + ") in pack " + this.getName() + ".");
		}

		return stream;
	}

	protected @Nullable <T> InputStream openResource(Map<T, Supplier<byte[]>> map, @NotNull T key) {
		var supplier = map.get(key);

		if (supplier == null) {
			return null;
		}

		byte[] bytes = supplier.get();

		if (bytes == null) {
			return null;
		}

		return new ByteArrayInputStream(bytes);
	}

	@Override
	public Collection<Identifier> findResources(ResourceType type, String namespace, String startingPath, Predicate<Identifier> pathFilter) {
		return this.getResourceMap(type).keySet().stream()
				.filter(id -> id.getNamespace().equals(namespace) && id.getPath().startsWith(startingPath))
				.filter(pathFilter)
				.collect(Collectors.toList());
	}

	@Override
	public boolean contains(ResourceType type, Identifier id) {
		return this.getResourceMap(type).containsKey(id);
	}

	@Override
	public @Unmodifiable Set<String> getNamespaces(ResourceType type) {
		return this.getResourceMap(type).keySet().stream()
				.map(Identifier::getNamespace)
				.collect(Collectors.toUnmodifiableSet());
	}

	@Override
	public <T> @Nullable T parseMetadata(ResourceMetadataReader<T> metaReader) throws IOException {
		try (var stream = this.openRoot(ResourcePack.PACK_METADATA_NAME)) {
			return AbstractFileResourcePack.parseMetadata(metaReader, stream);
		}
	}

	@Override
	public void close() {
		if (DUMP) {
			this.dumpAll();
		}
	}

	@Override
	public void putResource(@NotNull String fileName, byte @NotNull [] resource) {
		this.root.put(fileName, () -> resource);
	}

	@Override
	public void putResource(@NotNull ResourceType type, @NotNull Identifier id, byte @NotNull [] resource) {
		this.getResourceMap(type).put(id, () -> resource);
	}

	@Override
	public void putResource(@NotNull String fileName, @NotNull Supplier<byte[]> resource) {
		this.root.put(fileName, Suppliers.memoize(resource::get));
	}

	@Override
	public void putResource(@NotNull ResourceType type, @NotNull Identifier id, @NotNull Supplier<byte @NotNull []> resource) {
		this.getResourceMap(type).put(id, Suppliers.memoize(resource::get));
	}

	@Override
	public void clearResources() {
		this.root.clear();
		this.assets.clear();
		this.data.clear();
	}

	/**
	 * Dumps the content of this resource pack into the given path.
	 *
	 * @param path the path to dump the resources into
	 */
	public void dumpTo(@NotNull Path path) {
		try {
			Files.createDirectories(path);

			this.root.forEach((p, resource) -> this.dumpResource(path, p, resource.get()));
			this.assets.forEach((p, resource) ->
					this.dumpResource(path, QuiltResourcePack.getResourcePath(ResourceType.CLIENT_RESOURCES, p), resource.get()));
			this.data.forEach((p, resource) ->
					this.dumpResource(path, QuiltResourcePack.getResourcePath(ResourceType.SERVER_DATA, p), resource.get()));
		} catch (IOException e) {
			LOGGER.error("Failed to write resource pack dump from pack {} to {}.", this.getName(), path, e);
		}
	}

	protected void dumpAll() {
		this.dumpTo(Paths.get("debug", "packs", this.getName()));
	}

	protected void dumpResource(Path parentPath, String resourcePath, byte[] resource) {
		try {
			var p = parentPath.resolve(resourcePath);
			Files.createDirectories(p.getParent());
			Files.write(p, resource, StandardOpenOption.CREATE, StandardOpenOption.WRITE,
					StandardOpenOption.TRUNCATE_EXISTING);
		} catch (IOException e) {
			LOGGER.error("Failed to write resource pack dump from pack {}.", this.getName(), e);
		}
	}

	private Map<Identifier, Supplier<byte[]>> getResourceMap(ResourceType type) {
		return switch (type) {
			case CLIENT_RESOURCES -> this.assets;
			case SERVER_DATA -> this.data;
		};
	}
}

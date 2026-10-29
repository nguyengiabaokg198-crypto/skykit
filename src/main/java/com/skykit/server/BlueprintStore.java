package com.skykit.server;

import com.skykit.SkyKit;

import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Blueprints are saved as compressed NBT files in config/skykit/, which lives
 * OUTSIDE any world save folder - so a build captured in one world can be
 * rebuilt in another world (perfect for skyblock copies).
 */
public final class BlueprintStore {
	private BlueprintStore() {}

	private static final Pattern UNSAFE = Pattern.compile("[^a-z0-9_-]");

	private static Path dir() {
		return FabricLoader.getInstance().getConfigDir().resolve("skykit");
	}

	private static Path file(String name) {
		String safe = UNSAFE.matcher(name.toLowerCase(Locale.ROOT)).replaceAll("-").trim();
		if (safe.isEmpty()) {
			safe = "ban-ve";
		}
		return dir().resolve(safe + ".nbt");
	}

	public static void save(String name, CompoundTag tag) throws IOException {
		Files.createDirectories(dir());
		NbtIo.writeCompressed(tag, file(name));
	}

	public static CompoundTag load(String name) {
		Path f = file(name);
		if (!Files.isRegularFile(f)) {
			return null;
		}
		try {
			return NbtIo.readCompressed(f, NbtAccounter.create(8_000_000L));
		} catch (IOException e) {
			SkyKit.LOGGER.error("[SkyKit] cannot read blueprint {}: {}", name, e.toString());
			return null;
		}
	}

	public static boolean delete(String name) {
		try {
			return Files.deleteIfExists(file(name));
		} catch (IOException e) {
			return false;
		}
	}

	/** Saved names, sorted, without the .nbt suffix. */
	public static List<String> list() {
		List<String> names = new ArrayList<>();
		try (DirectoryStream<Path> files = Files.newDirectoryStream(dir(), "*.nbt")) {
			for (Path p : files) {
				String n = p.getFileName().toString();
				names.add(n.substring(0, n.length() - 4));
			}
		} catch (IOException ignored) {
			// no folder yet
		}
		names.sort(Comparator.naturalOrder());
		return names;
	}

	/** First free auto name: ban-ve-1, ban-ve-2, ... (phone friendly: no typing). */
	public static String autoName() {
		List<String> used = new ArrayList<>(list());
		for (int i = 1; i < 1000; i++) {
			String candidate = "ban-ve-" + i;
			if (!used.contains(candidate)) {
				return candidate;
			}
		}
		return "ban-ve-" + System.currentTimeMillis();
	}
}

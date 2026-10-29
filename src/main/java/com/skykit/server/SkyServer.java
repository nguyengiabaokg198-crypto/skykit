package com.skykit.server;

import com.skykit.SkyKit;
import com.skykit.networking.SkyActionPayload;
import com.skykit.networking.SkyStatePayload;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Server-side logic for SkyKit: bridge building, auto replant, blueprint
 * capture/store and the auto-build engine that fills a blueprint frame while
 * the player walks near it holding the right material.
 */
public final class SkyServer {
	private SkyServer() {}

	/** Max capture region: 48 blocks per axis, 16000 total volume. */
	public static final int MAX_AXIS = 48;
	public static final int MAX_TOTAL = 16000;
	/** The player must be within this range for the engine to place a block. */
	private static final double PLACE_RANGE = 6.0;

	/** Per-player session state (survives while online). */
	private static final class Session {
		boolean replant = true;
		boolean c1, c2;
		int c1x, c1y, c1z, c2x, c2y, c2z;
		String armed;   // blueprint chosen in the paper GUI, waiting for a tap
		BuildJob job;   // active auto-build
	}

	/** A blueprint being rebuilt in the world. */
	private static final class BuildJob {
		final String name;
		final BlockState[] states;
		final BlockPos[] positions;
		final boolean[] open;
		int remaining;
		int placed;
		int cursor;
		final long deadline;

		BuildJob(String name, List<BlockState> states, List<BlockPos> positions) {
			this.name = name;
			this.states = states.toArray(new BlockState[0]);
			this.positions = positions.toArray(new BlockPos[0]);
			this.open = new boolean[this.states.length];
			Arrays.fill(this.open, true);
			this.remaining = this.states.length;
			this.deadline = System.currentTimeMillis() + 15 * 60_000L;
		}
	}

	private static final Map<UUID, Session> SESSIONS = new HashMap<>();

	private static Session session(ServerPlayer p) {
		return SESSIONS.computeIfAbsent(p.getUUID(), k -> new Session());
	}

	/** Clears a session when the player disconnects. */
	public static void clear(UUID id) {
		SESSIONS.remove(id);
	}

	// ---------- payload ops ----------

	public static void handle(ServerPlayer player, SkyActionPayload payload) {
		switch (payload.op()) {
			case SkyActionPayload.OP_REQ_STATE -> sendState(player, payload.arg(), true);
			case SkyActionPayload.OP_TOGGLE_REPLANT -> toggleReplant(player);
			case SkyActionPayload.OP_CAM_SAVE -> camSave(player);
			case SkyActionPayload.OP_CAM_DELETE -> camDelete(player, payload.name());
			case SkyActionPayload.OP_PAPER_SELECT -> paperSelect(player, payload.name());
			case SkyActionPayload.OP_PAPER_CANCEL -> paperCancel(player);
			default -> {}
		}
	}

	private static void toggleReplant(ServerPlayer p) {
		Session s = session(p);
		s.replant = !s.replant;
		p.sendSystemMessage(Component.literal(s.replant
			? "[SkyKit] Tự trồng lại: BẬT ✓" : "[SkyKit] Tự trồng lại: TẮT"));
		sendState(p, SkyStatePayload.MODE_CONTROLLER, true);
	}

	// ---------- bridge ----------

	/**
	 * Builds an 8-block walkway continuing from the tapped block in the
	 * direction the player is facing, at the same height (perfect for
	 * skyblock bridging without sneaking backwards). Uses the most common
	 * BlockItem in the hotbar.
	 */
	public static void bridge(ServerPlayer p, BlockPos clicked) {
		ServerLevel level = p.level();
		Inventory inv = p.getInventory();

		// most common BlockItem in the hotbar
		Map<Item, Integer> counts = new HashMap<>();
		for (int i = 0; i < 9; i++) {
			ItemStack st = inv.getItem(i);
			if (!st.isEmpty() && st.getItem() instanceof BlockItem) {
				counts.merge(st.getItem(), st.getCount(), Integer::sum);
			}
		}
		Item best = null;
		int bestN = 0;
		for (var e : counts.entrySet()) {
			if (e.getValue() > bestN) {
				best = e.getKey();
				bestN = e.getValue();
			}
		}
		if (best == null) {
			p.sendSystemMessage(Component.literal("[SkyKit] Không có block nào trong hotbar để dựng cầu!"));
			return;
		}
		Block block = ((BlockItem) best).getBlock();
		Direction dir = p.getDirection();
		int placed = 0;
		for (int i = 1; i <= 8; i++) {
			BlockPos pos = clicked.relative(dir, i);
			BlockState existing = level.getBlockState(pos);
			if (existing.isAir() || existing.canBeReplaced()) {
				if (!hasItem(p, best)) {
					break; // out of material
				}
				level.setBlock(pos, block.defaultBlockState(), 3, 512);
				consume(p, best);
				placed++;
			}
		}
		if (placed == 0) {
			p.sendSystemMessage(Component.literal("[SkyKit] Cầu: không đặt được khối nào (chỗ đó có đồ hoặc hết block)"));
		} else {
			p.sendSystemMessage(Component.literal("[SkyKit] 🌉 Cầu: đã đặt " + placed + "/8 khối (dùng "
				+ best.toString() + ")"));
		}
	}

	private static boolean hasItem(ServerPlayer p, Item item) {
		Inventory inv = p.getInventory();
		int size = inv.getNonEquipmentItems().size();
		for (int i = 0; i < size; i++) {
			ItemStack st = inv.getItem(i);
			if (st.is(item)) {
				return true;
			}
		}
		return false;
	}

	private static void consume(ServerPlayer p, Item item) {
		if (p.getAbilities().instabuild) {
			return;
		}
		Inventory inv = p.getInventory();
		int size = inv.getNonEquipmentItems().size();
		for (int i = 0; i < size; i++) {
			ItemStack st = inv.getItem(i);
			if (st.is(item)) {
				st.shrink(1);
				return;
			}
		}
	}

	// ---------- camera (blueprint capture) ----------

	public static void cameraCorner(ServerPlayer p, int corner, BlockPos pos) {
		Session s = session(p);
		if (corner == 1) {
			s.c1 = true;
			s.c1x = pos.getX();
			s.c1y = pos.getY();
			s.c1z = pos.getZ();
			p.sendSystemMessage(Component.literal("[SkyKit] 📐 Góc 1: " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ()));
		} else {
			s.c2 = true;
			s.c2x = pos.getX();
			s.c2y = pos.getY();
			s.c2z = pos.getZ();
			p.sendSystemMessage(Component.literal("[SkyKit] 📐 Góc 2: " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ()));
		}
		if (s.c1 && s.c2) {
			int w = Math.abs(s.c1x - s.c2x) + 1;
			int h = Math.abs(s.c1y - s.c2y) + 1;
			int d = Math.abs(s.c1z - s.c2z) + 1;
			p.sendSystemMessage(Component.literal("[SkyKit] Vùng: " + w + "×" + h + "×" + d
				+ " — cầm Máy chụp, tap phải không khí để mở GUI và LƯU"));
		}
	}

	private static void camSave(ServerPlayer p) {
		Session s = session(p);
		if (!s.c1 || !s.c2) {
			p.sendSystemMessage(Component.literal(
				"[SkyKit] Chưa đủ 2 góc! Cầm Máy chụp: tap TRÁI block = Góc 1, tap PHẢI block = Góc 2"));
			return;
		}
		ServerLevel level = p.level();
		int minX = Math.min(s.c1x, s.c2x);
		int minY = Math.min(s.c1y, s.c2y);
		int minZ = Math.min(s.c1z, s.c2z);
		int w = Math.abs(s.c1x - s.c2x) + 1;
		int h = Math.abs(s.c1y - s.c2y) + 1;
		int d = Math.abs(s.c1z - s.c2z) + 1;
		if (w > MAX_AXIS || h > MAX_AXIS || d > MAX_AXIS || (long) w * h * d > MAX_TOTAL) {
			p.sendSystemMessage(Component.literal("[SkyKit] Vùng quá lớn (tối đa " + MAX_AXIS
				+ " mỗi chiều, " + MAX_TOTAL + " khối). Đây là " + w + "×" + h + "×" + d));
			return;
		}

		// palette (index 0 = air) + flat data in order y, z, x
		Map<BlockState, Integer> paletteMap = new HashMap<>();
		ListTag palette = new ListTag();
		palette.addAndUnwrap(NbtUtils.writeBlockState(Blocks.AIR.defaultBlockState()));
		int[] data = new int[w * h * d];
		int i = 0;
		int solid = 0;
		for (int y = minY; y < minY + h; y++) {
			for (int z = minZ; z < minZ + d; z++) {
				for (int x = minX; x < minX + w; x++) {
					BlockState st = level.getBlockState(new BlockPos(x, y, z));
					Integer idx = paletteMap.get(st);
					if (idx == null) {
						idx = palette.size();
						paletteMap.put(st, idx);
						palette.addAndUnwrap(NbtUtils.writeBlockState(st));
					}
					if (idx != 0) {
						solid++;
					}
					data[i++] = idx;
				}
			}
		}
		CompoundTag tag = new CompoundTag();
		tag.putInt("w", w);
		tag.putInt("h", h);
		tag.putInt("d", d);
		tag.put("palette", palette);
		tag.putIntArray("data", data);

		String name = BlueprintStore.autoName();
		try {
			BlueprintStore.save(name, tag);
		} catch (IOException e) {
			SkyKit.LOGGER.error("[SkyKit] save failed: {}", e.toString());
			p.sendSystemMessage(Component.literal("[SkyKit] Lỗi lưu file: " + e));
			return;
		}
		s.c1 = false;
		s.c2 = false;
		p.sendSystemMessage(Component.literal("[SkyKit] 💾 Đã lưu '" + name + "' (" + w + "×" + h + "×" + d
			+ ", " + solid + " khối). BẢN VẼ DÙNG ĐƯỢC MỌI THẾ GIỚI! Lấy Giấy bản vẽ: tap phải không khí → chọn bản vẽ → tap giấy vào block để đặt gốc"));
		sendState(p, SkyStatePayload.MODE_CAMERA, true);
	}

	private static void camDelete(ServerPlayer p, String name) {
		if (BlueprintStore.delete(name)) {
			p.sendSystemMessage(Component.literal("[SkyKit] 🗑 Đã xóa '" + name + "'"));
		} else {
			p.sendSystemMessage(Component.literal("[SkyKit] Không tìm thấy '" + name + "'"));
		}
		sendState(p, SkyStatePayload.MODE_CAMERA, true);
	}

	// ---------- paper (blueprint rebuild) ----------

	private static void paperSelect(ServerPlayer p, String name) {
		CompoundTag tag = BlueprintStore.load(name);
		if (tag == null) {
			p.sendSystemMessage(Component.literal("[SkyKit] Không đọc được '" + name + "'"));
			return;
		}
		session(p).armed = name;
		p.sendSystemMessage(Component.literal("[SkyKit] 📄 Đã chọn '" + name
			+ "' — giờ tap GIẤY vào 1 block để đặt gốc bản vẽ"));
		sendState(p, SkyStatePayload.MODE_PAPER, true);
	}

	public static void paperPlace(ServerPlayer p, BlockPos clicked) {
		Session s = session(p);
		if (s.armed == null) {
			p.sendSystemMessage(Component.literal(
				"[SkyKit] Chưa chọn bản vẽ! Cầm Giấy, tap phải không khí để mở GUI và chọn trước"));
			return;
		}
		String name = s.armed;
		CompoundTag tag = BlueprintStore.load(name);
		if (tag == null) {
			s.armed = null;
			p.sendSystemMessage(Component.literal("[SkyKit] Bản vẽ '" + name + "' không còn tồn tại"));
			return;
		}
		BuildJob job = decode(tag, name, clicked, p.level());
		if (job == null) {
			p.sendSystemMessage(Component.literal("[SkyKit] Bản vẽ '" + name + "' hỏng định dạng"));
			return;
		}
		s.job = job;
		p.sendSystemMessage(Component.literal("[SkyKit] 🏗 Bắt đầu xây '" + name + "': " + job.remaining
			+ " khối. Cầm block ĐÚNG LOẠI trên tay và đi quanh khu này (≤6 block) - khối tự lấp theo bản vẽ!"));
		sendState(p, SkyStatePayload.MODE_PAPER, true);
	}

	private static void paperCancel(ServerPlayer p) {
		Session s = session(p);
		if (s.job != null) {
			p.sendSystemMessage(Component.literal("[SkyKit] 🛑 Đã dừng xây '" + s.job.name + "' ("
				+ s.job.placed + "/" + s.job.states.length + " khối)"));
		}
		s.armed = null;
		s.job = null;
		sendState(p, SkyStatePayload.MODE_PAPER, true);
	}

	/** Reads the saved tag into a build job anchored at the origin block. */
	private static BuildJob decode(CompoundTag tag, String name, BlockPos origin, ServerLevel level) {
		int w = tag.getIntOr("w", 0);
		int h = tag.getIntOr("h", 0);
		int d = tag.getIntOr("d", 0);
		ListTag palette = tag.getList("palette").orElse(null);
		int[] data = tag.getIntArray("data").orElse(null);
		if (palette == null || data == null || w <= 0 || h <= 0 || d <= 0
				|| data.length != w * h * d || palette.size() > Short.MAX_VALUE) {
			return null;
		}
		HolderGetter<Block> blocks = level.registryAccess().lookupOrThrow(Registries.BLOCK);
		BlockState[] pal = new BlockState[palette.size()];
		for (int i = 0; i < palette.size(); i++) {
			pal[i] = NbtUtils.readBlockState(blocks, palette.getCompoundOrEmpty(i));
		}
		List<BlockState> states = new ArrayList<>();
		List<BlockPos> positions = new ArrayList<>();
		for (int i = 0; i < data.length; i++) {
			int idx = data[i];
			if (idx <= 0 || idx >= pal.length) {
				continue; // air
			}
			BlockState st = pal[idx];
			if (st.isAir()) {
				continue;
			}
			int rx = i % w;
			int zz = (i / w) % d;
			int yy = i / (w * d);
			states.add(st);
			positions.add(origin.offset(rx, yy, zz));
		}
		if (states.isEmpty()) {
			return null;
		}
		return new BuildJob(name, states, positions);
	}

	// ---------- auto replant ----------

	private static final Map<Block, Item> SEEDS = new HashMap<>();

	static {
		SEEDS.put(Blocks.WHEAT, Items.WHEAT_SEEDS);
		SEEDS.put(Blocks.CARROTS, Items.CARROT);
		SEEDS.put(Blocks.POTATOES, Items.POTATO);
		SEEDS.put(Blocks.BEETROOTS, Items.BEETROOT_SEEDS);
		SEEDS.put(Blocks.NETHER_WART, Items.NETHER_WART);
	}

	public static void afterCropBreak(ServerPlayer p, Level level, BlockPos pos, BlockState state) {
		Session s = session(p);
		if (!s.replant) {
			return;
		}
		Block block = state.getBlock();
		Item seed = SEEDS.get(block);
		if (seed == null) {
			return;
		}
		Block below = level.getBlockState(pos.below()).getBlock();
		boolean groundOk = (block == Blocks.NETHER_WART)
			? below == Blocks.SOUL_SAND
			: below == Blocks.FARMLAND;
		if (!groundOk) {
			return;
		}
		// consume 1 seed from anywhere in the inventory, replant young crop
		Inventory inv = p.getInventory();
		int size = inv.getNonEquipmentItems().size();
		for (int i = 0; i < size; i++) {
			ItemStack st = inv.getItem(i);
			if (st.is(seed)) {
				if (!p.getAbilities().instabuild) {
					st.shrink(1);
				}
				level.setBlock(pos, block.defaultBlockState(), 3, 512);
				return;
			}
		}
	}

	// ---------- blueprint auto-build engine ----------

	/** Called every server tick: advance every player's active build job. */
	public static void tick(MinecraftServer server) {
		if (server.getTickCount() % 2 != 0) {
			return; // every 2 ticks
		}
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			Session s = SESSIONS.get(p.getUUID());
			if (s == null || s.job == null) {
				continue;
			}
			BuildJob job = s.job;
			if (System.currentTimeMillis() > job.deadline) {
				p.sendSystemMessage(Component.literal("[SkyKit] ⏱ Hết 15 phút, tạm dừng xây '" + job.name
					+ "' (" + job.placed + "/" + job.states.length + " khối). Chọn lại bản vẽ để xây tiếp"));
				s.job = null;
				continue;
			}
			advance(p, job);
		}
	}

	private static void advance(ServerPlayer p, BuildJob job) {
		ServerLevel level = p.level();
		int total = job.states.length;
		int scanned = 0;
		while (scanned < 256 && job.remaining > 0) {
			int i = job.cursor % total;
			job.cursor++;
			scanned++;
			if (!job.open[i]) {
				continue;
			}
			BlockPos pos = job.positions[i];
			if (p.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > PLACE_RANGE * PLACE_RANGE) {
				continue; // too far: wait until the player walks closer
			}
			BlockState existing = level.getBlockState(pos);
			if (!existing.isAir() && !existing.canBeReplaced()) {
				job.open[i] = false; // occupied by the world: can never place here
				job.remaining--;
				continue;
			}
			BlockState needed = job.states[i];
			ItemStack held = p.getMainHandItem();
			if (held.getItem() instanceof BlockItem bi && bi.getBlock() == needed.getBlock()) {
				level.setBlock(pos, needed, 3, 512);
				if (!p.getAbilities().instabuild) {
					held.shrink(1);
				}
				job.open[i] = false;
				job.remaining--;
				job.placed++;
				if (job.placed % 32 == 0) {
					p.sendSystemMessage(Component.literal("[SkyKit] '" + job.name + "': đã đặt "
						+ job.placed + "/" + total + " khối, còn " + job.remaining));
				}
			}
		}
		if (job.remaining == 0) {
			p.sendSystemMessage(Component.literal("[SkyKit] 🎉 HOÀN THÀNH '" + job.name + "': đủ "
				+ job.placed + " khối theo bản vẽ!"));
			session(p).job = null;
		}
	}

	// ---------- state to client ----------

	public static void sendState(ServerPlayer p, byte mode, boolean open) {
		Session s = session(p);
		StringBuilder info = new StringBuilder();
		if (s.job != null) {
			info.append("Đang xây '").append(s.job.name).append("': còn ").append(s.job.remaining)
				.append('/').append(s.job.states.length).append(" khối");
		} else if (s.armed != null) {
			info.append("Đã chọn: ").append(s.armed).append(" — tap giấy vào block");
		}
		ServerPlayNetworking.send(p, new SkyStatePayload(
			mode,
			(byte) (open ? 1 : 0),
			(byte) (s.replant ? 1 : 0),
			(byte) (s.c1 ? 1 : 0),
			(byte) (s.c2 ? 1 : 0),
			s.c1x, s.c1y, s.c1z,
			s.c2x, s.c2y, s.c2z,
			String.join(";", BlueprintStore.list()),
			info.toString()));
	}
}

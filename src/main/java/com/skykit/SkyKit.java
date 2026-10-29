package com.skykit;

import com.mojang.logging.LogUtils;
import com.skykit.commands.SkyCommands;
import com.skykit.networking.SkyActionPayload;
import com.skykit.networking.SkyStatePayload;
import com.skykit.registry.ModItems;
import com.skykit.server.SkyServer;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;

import org.slf4j.Logger;

/**
 * SkyKit: skyblock survival kit.
 * - Bridge: tap the controller on a block -> auto-places a 8-block walkway
 *   using the most common BlockItem in your hotbar.
 * - AutoReplant: harvested crops replant themselves from your inventory.
 * - Blueprints: capture a build with the camera (two corners, <= 24^3), saved
 *   to a shared file so OTHER WORLDS can load it; place it with the paper and
 *   just walk around holding the right material - the frame fills itself.
 */
public class SkyKit implements ModInitializer {
	public static final String MOD_ID = "skykit";
	public static final Logger LOGGER = LogUtils.getLogger();

	@Override
	public void onInitialize() {
		ModItems.init();
		SkyCommands.init();

		PayloadTypeRegistry.serverboundPlay().register(SkyActionPayload.TYPE, SkyActionPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(SkyStatePayload.TYPE, SkyStatePayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(SkyActionPayload.TYPE, (payload, ctx) ->
			ctx.server().executeIfPossible(() -> SkyServer.handle((ServerPlayer) ctx.player(), payload)));

		// -- camera: tap LEFT a block = corner 1 (cancel the punch) --
		AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
			if (player.getMainHandItem().getItem() != ModItems.CAMERA) {
				return InteractionResult.PASS;
			}
			if (world.isClientSide()) {
				return InteractionResult.FAIL;
			}
			if (player instanceof ServerPlayer sp) {
				SkyServer.cameraCorner(sp, 1, pos);
			}
			return InteractionResult.FAIL;
		});

		// -- tap RIGHT blocks with camera / paper / controller --
		UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
			var held = player.getMainHandItem();
			if (held.getItem() == ModItems.CAMERA) {
				if (world.isClientSide()) {
					return InteractionResult.SUCCESS;
				}
				if (player instanceof ServerPlayer sp) {
					SkyServer.cameraCorner(sp, 2, hit.getBlockPos());
				}
				return InteractionResult.SUCCESS;
			}
			if (held.getItem() == ModItems.PAPER) {
				if (world.isClientSide()) {
					return InteractionResult.SUCCESS;
				}
				if (player instanceof ServerPlayer sp) {
					SkyServer.paperPlace(sp, hit.getBlockPos());
				}
				return InteractionResult.SUCCESS;
			}
			if (held.getItem() == ModItems.CONTROLLER) {
				// bridge: auto build a walkway from the clicked block forward
				if (world.isClientSide()) {
					return InteractionResult.SUCCESS;
				}
				if (player instanceof ServerPlayer sp) {
					SkyServer.bridge(sp, hit.getBlockPos());
				}
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.PASS;
		});

		// -- tap RIGHT the AIR: open the SkyKit GUI of the held item --
		UseItemCallback.EVENT.register((player, world, hand) -> {
			var held = player.getItemInHand(hand);
			byte mode;
			if (held.getItem() == ModItems.CONTROLLER) {
				mode = SkyStatePayload.MODE_CONTROLLER;
			} else if (held.getItem() == ModItems.CAMERA) {
				mode = SkyStatePayload.MODE_CAMERA;
			} else if (held.getItem() == ModItems.PAPER) {
				mode = SkyStatePayload.MODE_PAPER;
			} else {
				return InteractionResult.PASS;
			}
			if (world.isClientSide()) {
				return InteractionResult.SUCCESS;
			}
			if (player instanceof ServerPlayer sp) {
				SkyServer.sendState(sp, mode, true);
			}
			return InteractionResult.SUCCESS;
		});

		// -- auto replant harvested crops --
		net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
			if (!world.isClientSide() && player instanceof ServerPlayer sp) {
				SkyServer.afterCropBreak(sp, world, pos, state);
			}
		});

		// -- blueprint auto-build engine --
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server ->
			SkyServer.tick(server));

		// -- drop per-player sessions on disconnect --
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
			SkyServer.clear(handler.getPlayer().getUUID()));

		LOGGER.info("[SkyKit] initialized: bridge / auto-replant / cross-world blueprints ready");
	}
}

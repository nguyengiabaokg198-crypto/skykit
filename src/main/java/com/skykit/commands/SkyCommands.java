package com.skykit.commands;

import com.skykit.registry.ModItems;

import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.item.ItemStack;

/** /skykit kit: hands the player all three SkyKit tools. */
public final class SkyCommands {
	private SkyCommands() {}

	public static void init() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
			dispatcher.register(Commands.literal("skykit")
				.requires(src -> src.permissions().hasPermission(Permissions.COMMANDS_MODERATOR))
				.then(Commands.literal("kit").executes(ctx -> kit(ctx.getSource())))));
	}

	private static int kit(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		player.getInventory().add(new ItemStack(ModItems.CONTROLLER));
		player.getInventory().add(new ItemStack(ModItems.CAMERA));
		player.getInventory().add(new ItemStack(ModItems.PAPER));
		source.sendSuccess(() -> Component.literal(
			"[SkyKit] Đã gửi 3 đồ: 🌉 Bộ Điều Khiển (cầu) + 📐 Máy Chụp Bản Vẽ + 📄 Giấy Bản Vẽ."
			+ " Cầm item rồi TAP PHẢI KHÔNG KHÍ để mở GUI hướng dẫn"), false);
		return 1;
	}
}

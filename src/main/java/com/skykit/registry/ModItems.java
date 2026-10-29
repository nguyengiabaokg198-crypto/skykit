package com.skykit.registry;

import com.skykit.SkyKit;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;

/** SkyKit items: controller (GUI), blueprint camera (capture), blueprint paper (rebuild). */
public final class ModItems {
	private ModItems() {}

	public static Item CONTROLLER;
	public static Item CAMERA;
	public static Item PAPER;

	private static Item register(String path, Rarity rarity) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM,
			Identifier.fromNamespaceAndPath(SkyKit.MOD_ID, path));
		Item item = new Item(new Item.Properties().stacksTo(1).rarity(rarity).setId(key));
		Registry.register(BuiltInRegistries.ITEM, key, item);
		return item;
	}

	public static final ResourceKey<CreativeModeTab> TAB_KEY = ResourceKey.create(
		Registries.CREATIVE_MODE_TAB,
		Identifier.fromNamespaceAndPath(SkyKit.MOD_ID, "main"));

	public static final CreativeModeTab TAB = CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0)
		.title(Component.translatable("itemGroup.skykit.main"))
		.icon(() -> new ItemStack(CONTROLLER))
		.displayItems((parameters, output) -> {
			output.accept(CONTROLLER);
			output.accept(CAMERA);
			output.accept(PAPER);
		})
		.build();

	public static void init() {
		CONTROLLER = register("skykit_controller", Rarity.RARE);
		CAMERA = register("blueprint_camera", Rarity.RARE);
		PAPER = register("blueprint_paper", Rarity.RARE);
		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, TAB_KEY, TAB);
	}
}

package com.skykit.client;

import com.skykit.networking.SkyStatePayload;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.Minecraft;

@Environment(EnvType.CLIENT)
public class SkyKitClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClientPlayNetworking.registerGlobalReceiver(SkyStatePayload.TYPE, (payload, ctx) ->
			ctx.client().execute(() -> {
				if (payload.open() == 1) {
					Minecraft.getInstance().setScreenAndShow(new SkyKitScreen(payload));
				}
			}));
	}
}

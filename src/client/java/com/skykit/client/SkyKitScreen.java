package com.skykit.client;

import com.skykit.networking.SkyActionPayload;
import com.skykit.networking.SkyStatePayload;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The single SkyKit GUI - three modes driven by the server state payload:
 * controller (bridge + auto-replant toggle), camera (save/delete blueprints)
 * and paper (select blueprint / cancel the active build).
 */
@Environment(EnvType.CLIENT)
public class SkyKitScreen extends Screen {
	private static final int PANEL_W = 250;
	private static final int PANEL_H = 226;

	private final SkyStatePayload state;
	private final List<String> names = new ArrayList<>();
	private int bx, by;

	public SkyKitScreen(SkyStatePayload state) {
		super(Component.literal("SkyKit"));
		this.state = state;
		for (String n : state.names().split(";")) {
			if (!n.isEmpty()) {
				names.add(n);
			}
		}
		if (names.size() > 8) {
			names.subList(8, names.size()).clear();
		}
	}

	@Override
	protected void init() {
		super.init();
		bx = (this.width - PANEL_W) / 2;
		by = Math.max(6, (this.height - PANEL_H) / 2);
		byte mode = state.mode();

		if (mode == SkyStatePayload.MODE_CONTROLLER) {
			addRenderableWidget(Button.builder(
					Component.literal(state.replant() == 1 ? "🌾 TỰ TRỒNG LẠI: BẬT" : "🌾 TỰ TRỒNG LẠI: TẮT"),
					b -> send(SkyActionPayload.OP_TOGGLE_REPLANT, null))
				.bounds(bx + 8, by + 40, 234, 20).build());
		} else if (mode == SkyStatePayload.MODE_CAMERA) {
			addRenderableWidget(Button.builder(Component.literal("💾 LƯU BẢN VẼ (tự đặt tên)"),
					b -> send(SkyActionPayload.OP_CAM_SAVE, null))
				.bounds(bx + 8, by + 44, 234, 20).build());
			int rowY = by + 74;
			for (int i = 0; i < names.size(); i++) {
				String name = names.get(i);
				addRenderableWidget(Button.builder(Component.literal("🗑 XÓA"),
						b -> send(SkyActionPayload.OP_CAM_DELETE, name))
					.bounds(bx + PANEL_W - 78, rowY + i * 18, 70, 16).build());
			}
		} else {
			int rowY = by + 40;
			for (int i = 0; i < names.size(); i++) {
				String name = names.get(i);
				addRenderableWidget(Button.builder(Component.literal("📄 " + name + "  —  CHỌN"),
						b -> send(SkyActionPayload.OP_PAPER_SELECT, name))
					.bounds(bx + 8, rowY + i * 20, 234, 18).build());
			}
			addRenderableWidget(Button.builder(Component.literal("🛑 HỦY ĐANG XÂY / BỎ CHỌN"),
					b -> send(SkyActionPayload.OP_PAPER_CANCEL, null))
				.bounds(bx + 8, by + PANEL_H - 46, 234, 20).build());
		}

		addRenderableWidget(Button.builder(Component.literal("ĐÓNG"), b -> onClose())
			.bounds(bx + PANEL_W / 2 - 50, by + PANEL_H - 22, 100, 18).build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		g.fill(bx, by, bx + PANEL_W, by + PANEL_H, 0xF0102430);
		g.outline(bx, by, PANEL_W, PANEL_H, 0xFF37D06C);
		super.extractRenderState(g, mouseX, mouseY, delta);

		byte mode = state.mode();
		String title = mode == SkyStatePayload.MODE_CONTROLLER ? "SKYKIT - TIỆN ÍCH SKYBLOCK"
			: mode == SkyStatePayload.MODE_CAMERA ? "SKYKIT - MÁY CHỤP BẢN VẼ" : "SKYKIT - GIẤY BẢN VẼ";
		g.centeredText(this.font, title, bx + PANEL_W / 2, by + 7, 0xFF37D06C);

		if (mode == SkyStatePayload.MODE_CONTROLLER) {
			g.text(this.font, "🌉 CẦU: cầm Bộ Điều Khiển, tap vào block dưới chân", bx + 8, by + 70, 0xFFECECEC);
			g.text(this.font, "   → tự dựng cầu 8 khối theo hướng nhìn (block lấy", bx + 8, by + 81, 0xFFBECFE0);
			g.text(this.font, "   từ loại block PHỔ BIẾN NHẤT trong hotbar)", bx + 8, by + 92, 0xFFBECFE0);
			g.text(this.font, "🌾 TỰ TRỒNG LẠI: thu hoạch lúa/củ/cà rốt/khoai/củ dền", bx + 8, by + 110, 0xFFECECEC);
			g.text(this.font, "   /nether wart → tự trồng lại hạt từ túi (bật/tắt ở trên)", bx + 8, by + 121, 0xFFBECFE0);
			g.text(this.font, "📄 BẢN VẼ: cầm Máy chụp → tap TRÁI block = Góc 1,", bx + 8, by + 139, 0xFFECECEC);
			g.text(this.font, "   tap PHẢI block = Góc 2, tap PHẢI không khí = GUI lưu.", bx + 8, by + 150, 0xFFBECFE0);
			g.text(this.font, "   Cầm Giấy → chọn bản vẽ → tap giấy vào block = gốc,", bx + 8, by + 161, 0xFFBECFE0);
			g.text(this.font, "   cầm đúng block đi lại gần → tự lấp theo bản vẽ!", bx + 8, by + 172, 0xFFBECFE0);
		} else if (mode == SkyStatePayload.MODE_CAMERA) {
			String c1 = state.c1Set() == 1 ? "(" + state.c1x() + ", " + state.c1y() + ", " + state.c1z() + ")" : "chưa có";
			String c2 = state.c2Set() == 1 ? "(" + state.c2x() + ", " + state.c2y() + ", " + state.c2z() + ")" : "chưa có";
			g.text(this.font, "Góc 1 (tap TRÁI block): " + c1, bx + 8, by + 22, 0xFF9FE7FF);
			g.text(this.font, "Góc 2 (tap PHẢI block): " + c2, bx + 8, by + 32, 0xFF9FE7FF);
			if (names.isEmpty()) {
				g.text(this.font, "Chưa có bản vẽ nào được lưu.", bx + 8, by + 74, 0xFF90A0B5);
			} else {
				for (int i = 0; i < names.size(); i++) {
					g.text(this.font, "📄 " + names.get(i), bx + 8, by + 78 + i * 18, 0xFFECECEC);
				}
			}
			g.text(this.font, "Bản vẽ lưu ngoài thế giới → dùng lại mọi thế giới!", bx + 8, by + PANEL_H - 32, 0xFF37D06C);
		} else {
			if (names.isEmpty()) {
				g.text(this.font, "Chưa có bản vẽ nào.", bx + 8, by + 40, 0xFF90A0B5);
				g.text(this.font, "Hãy chụp bằng Máy chụp bản vẽ trước!", bx + 8, by + 52, 0xFF90A0B5);
			}
			if (!state.info().isEmpty()) {
				g.text(this.font, state.info(), bx + 8, by + PANEL_H - 58, 0xFF9FE7FF);
			}
			g.text(this.font, "Sau khi CHỌN: tap GIẤY vào 1 block = đặt gốc bản vẽ.", bx + 8, by + PANEL_H - 70, 0xFF90A0B5);
			g.text(this.font, "Cầm đúng block trên tay, đi lại gần (≤6 block)", bx + 8, by + PANEL_H - 82, 0xFF90A0B5);
			g.text(this.font, "→ khối tự lấp theo bản vẽ. Chỉ thay chỗ trống.", bx + 8, by + PANEL_H - 94, 0xFF90A0B5);
		}

		g.text(this.font, "tap phải không khí khi cầm item SkyKit = mở bảng này",
			bx + 8, by + PANEL_H - 10, 0xFF7E8CA0);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private static void send(byte op, String name) {
		ClientPlayNetworking.send(new SkyActionPayload(op, (byte) 0, name == null ? "" : name));
	}
}

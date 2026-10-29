package com.skykit.networking;

import com.skykit.SkyKit;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client -> server request for SkyKit features. */
public record SkyActionPayload(byte op, byte arg, String name) implements CustomPacketPayload {
	public static final byte OP_REQ_STATE = 0;
	public static final byte OP_TOGGLE_REPLANT = 1;
	public static final byte OP_BRIDGE = 2;
	public static final byte OP_CAM_CORNER1 = 3;
	public static final byte OP_CAM_CORNER2 = 4;
	public static final byte OP_CAM_SAVE = 5;
	public static final byte OP_CAM_DELETE = 6;
	public static final byte OP_PAPER_SELECT = 7;
	public static final byte OP_PAPER_CANCEL = 8;

	public static final CustomPacketPayload.Type<SkyActionPayload> TYPE =
		new CustomPacketPayload.Type<>(
			Identifier.fromNamespaceAndPath(SkyKit.MOD_ID, "action"));

	public static final StreamCodec<ByteBuf, SkyActionPayload> CODEC = StreamCodec.of(
		(buf, p) -> {
			buf.writeByte(p.op());
			buf.writeByte(p.arg());
			net.minecraft.network.codec.ByteBufCodecs.STRING_UTF8.encode(buf, p.name());
		},
		buf -> new SkyActionPayload(
			buf.readByte(), buf.readByte(),
			net.minecraft.network.codec.ByteBufCodecs.STRING_UTF8.decode(buf)));

	@Override
	public CustomPacketPayload.Type<SkyActionPayload> type() {
		return TYPE;
	}
}

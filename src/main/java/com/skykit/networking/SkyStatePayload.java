package com.skykit.networking;

import com.skykit.SkyKit;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Server -> client state for the SkyKit GUI. */
public record SkyStatePayload(
		byte mode,       // 0 = controller, 1 = camera (save/delete list), 2 = paper (select list)
		byte open,       // 1 = open the screen on arrival
		byte replant,    // 1 = auto replant enabled
		byte c1Set,
		byte c2Set,
		int c1x, int c1y, int c1z,
		int c2x, int c2y, int c2z,
		String names,    // saved blueprint names joined by ';'
		String info) implements CustomPacketPayload {

	public static final byte MODE_CONTROLLER = 0;
	public static final byte MODE_CAMERA = 1;
	public static final byte MODE_PAPER = 2;

	public static final CustomPacketPayload.Type<SkyStatePayload> TYPE =
		new CustomPacketPayload.Type<>(
			Identifier.fromNamespaceAndPath(SkyKit.MOD_ID, "state"));

	public static final StreamCodec<ByteBuf, SkyStatePayload> CODEC = StreamCodec.of(
		(buf, p) -> {
			buf.writeByte(p.mode());
			buf.writeByte(p.open());
			buf.writeByte(p.replant());
			buf.writeByte(p.c1Set());
			buf.writeByte(p.c2Set());
			buf.writeInt(p.c1x());
			buf.writeInt(p.c1y());
			buf.writeInt(p.c1z());
			buf.writeInt(p.c2x());
			buf.writeInt(p.c2y());
			buf.writeInt(p.c2z());
			ByteBufCodecs.STRING_UTF8.encode(buf, p.names());
			ByteBufCodecs.STRING_UTF8.encode(buf, p.info());
		},
		buf -> new SkyStatePayload(
			buf.readByte(), buf.readByte(), buf.readByte(), buf.readByte(), buf.readByte(),
			buf.readInt(), buf.readInt(), buf.readInt(),
			buf.readInt(), buf.readInt(), buf.readInt(),
			ByteBufCodecs.STRING_UTF8.decode(buf),
			ByteBufCodecs.STRING_UTF8.decode(buf)));

	@Override
	public CustomPacketPayload.Type<SkyStatePayload> type() {
		return TYPE;
	}
}

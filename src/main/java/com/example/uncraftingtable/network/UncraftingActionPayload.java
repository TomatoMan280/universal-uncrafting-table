package com.example.uncraftingtable.network;

import com.example.uncraftingtable.UncraftingTableMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record UncraftingActionPayload(BlockPos pos) implements CustomPacketPayload {
    public static final Type<UncraftingActionPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(UncraftingTableMod.MOD_ID, "uncraft_action"));

    public static final StreamCodec<FriendlyByteBuf, UncraftingActionPayload> STREAM_CODEC = StreamCodec.ofMember(
            UncraftingActionPayload::write,
            UncraftingActionPayload::new
    );

    public UncraftingActionPayload(FriendlyByteBuf buf) {
        this(buf.readBlockPos());
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

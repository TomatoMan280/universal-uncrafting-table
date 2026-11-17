package com.example.uncraftingtable.network;

import com.example.uncraftingtable.UncraftingTableMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record UncraftingSettingsPayload(
        BlockPos pos,
        boolean autoUncraft,
        int speed,
        boolean muteSounds,
        boolean muteParticles,
        boolean enableGrindstone,
        boolean enableSmoker,
        boolean enableBlastFurnace,
        boolean enableBrewingStand,
        boolean waitForUnconfiguredChoice
) implements CustomPacketPayload {
    public static final Type<UncraftingSettingsPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(UncraftingTableMod.MOD_ID, "uncraft_settings"));

    public static final StreamCodec<FriendlyByteBuf, UncraftingSettingsPayload> STREAM_CODEC = StreamCodec.ofMember(
            UncraftingSettingsPayload::write,
            UncraftingSettingsPayload::new
    );

    public UncraftingSettingsPayload(FriendlyByteBuf buf) {
        this(
                buf.readBlockPos(),
                buf.readBoolean(),
                buf.readInt(),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readBoolean()
        );
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeBoolean(autoUncraft);
        buf.writeInt(speed);
        buf.writeBoolean(muteSounds);
        buf.writeBoolean(muteParticles);
        buf.writeBoolean(enableGrindstone);
        buf.writeBoolean(enableSmoker);
        buf.writeBoolean(enableBlastFurnace);
        buf.writeBoolean(enableBrewingStand);
        buf.writeBoolean(waitForUnconfiguredChoice);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

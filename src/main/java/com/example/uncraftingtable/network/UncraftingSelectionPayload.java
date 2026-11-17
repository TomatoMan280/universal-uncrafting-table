package com.example.uncraftingtable.network;

import com.example.uncraftingtable.UncraftingTableMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Adds, updates, or removes a saved output recipe filter rule on an Uncrafting Table.
 */
public record UncraftingSelectionPayload(
        BlockPos pos,
        Action action,
        ResourceLocation itemId,
        ResourceLocation recipeId,
        List<ResourceLocation> chosenMaterialIds
) implements CustomPacketPayload {

    public enum Action {
        SAVE,
        REMOVE
    }

    public static final Type<UncraftingSelectionPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(UncraftingTableMod.MOD_ID, "uncraft_selection"));

    public static final StreamCodec<FriendlyByteBuf, UncraftingSelectionPayload> STREAM_CODEC = StreamCodec.ofMember(
            UncraftingSelectionPayload::write,
            UncraftingSelectionPayload::new
    );

    public UncraftingSelectionPayload(FriendlyByteBuf buf) {
        this(
                buf.readBlockPos(),
                buf.readEnum(Action.class),
                buf.readResourceLocation(),
                buf.readResourceLocation(),
                readMaterials(buf)
        );
    }

    private static List<ResourceLocation> readMaterials(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        List<ResourceLocation> list = new ArrayList<>(Math.min(size, 64));
        for (int i = 0; i < size; i++) {
            list.add(buf.readResourceLocation());
        }
        return list;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeEnum(action);
        buf.writeResourceLocation(itemId);
        buf.writeResourceLocation(recipeId);
        buf.writeVarInt(chosenMaterialIds.size());
        for (ResourceLocation id : chosenMaterialIds) {
            buf.writeResourceLocation(id);
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

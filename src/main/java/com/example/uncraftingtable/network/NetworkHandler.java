package com.example.uncraftingtable.network;

import com.example.uncraftingtable.UncraftingTableMod;
import com.example.uncraftingtable.block.entity.UncraftingTableBlockEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = UncraftingTableMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public class NetworkHandler {

    @SubscribeEvent
    public static void register(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar(UncraftingTableMod.MOD_ID);

        registrar.playToServer(
                UncraftingActionPayload.TYPE,
                UncraftingActionPayload.STREAM_CODEC,
                NetworkHandler::handleAction
        );

        registrar.playToServer(
                UncraftingSettingsPayload.TYPE,
                UncraftingSettingsPayload.STREAM_CODEC,
                NetworkHandler::handleSettings
        );

        registrar.playToServer(
                UncraftingSelectionPayload.TYPE,
                UncraftingSelectionPayload.STREAM_CODEC,
                NetworkHandler::handleSelection
        );
    }

    public static void handleAction(final UncraftingActionPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                Level level = player.level();
                if (level.isLoaded(payload.pos())) {
                    BlockEntity be = level.getBlockEntity(payload.pos());
                    if (be instanceof UncraftingTableBlockEntity uncraftingTable) {
                        uncraftingTable.manualUncraft();
                    }
                }
            }
        });
    }

    public static void handleSettings(final UncraftingSettingsPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                Level level = player.level();
                if (level.isLoaded(payload.pos())) {
                    BlockEntity be = level.getBlockEntity(payload.pos());
                    if (be instanceof UncraftingTableBlockEntity uncraftingTable) {
                        uncraftingTable.updateSettings(
                                payload.autoUncraft(),
                                payload.speed(),
                                payload.muteSounds(),
                                payload.muteParticles(),
                                payload.enableGrindstone(),
                                payload.enableSmoker(),
                                payload.enableBlastFurnace(),
                                payload.enableBrewingStand(),
                                payload.waitForUnconfiguredChoice()
                        );
                    }
                }
            }
        });
    }

    public static void handleSelection(final UncraftingSelectionPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                Level level = player.level();
                if (level.isLoaded(payload.pos())) {
                    BlockEntity be = level.getBlockEntity(payload.pos());
                    if (be instanceof UncraftingTableBlockEntity uncraftingTable) {
                        if (payload.action() == UncraftingSelectionPayload.Action.REMOVE) {
                            uncraftingTable.removeRecipeRule(payload.itemId());
                        } else {
                            uncraftingTable.saveRecipeRule(
                                    payload.itemId(),
                                    payload.recipeId(),
                                    payload.chosenMaterialIds()
                            );
                        }
                    }
                }
            }
        });
    }
}

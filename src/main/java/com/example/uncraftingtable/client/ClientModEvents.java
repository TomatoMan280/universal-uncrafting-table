package com.example.uncraftingtable.client;

import com.example.uncraftingtable.UncraftingTableMod;
import com.example.uncraftingtable.recipe.UncraftingRecipeResolver;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RecipesUpdatedEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.common.NeoForge;

@EventBusSubscriber(modid = UncraftingTableMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientModEvents {
    private ClientModEvents() {
    }

    @SubscribeEvent
    public static void registerMenuScreens(RegisterMenuScreensEvent event) {
        event.register(UncraftingTableMod.UNCRAFTING_TABLE_MENU.get(), UncraftingTableScreen::new);
        NeoForge.EVENT_BUS.addListener((RecipesUpdatedEvent e) -> UncraftingRecipeResolver.clearCache());
    }
}

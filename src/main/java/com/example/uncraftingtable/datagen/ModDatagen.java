package com.example.uncraftingtable.datagen;

import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.data.event.GatherDataEvent;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;

public class ModDatagen {
    @SubscribeEvent
    public static void gatherData(GatherDataEvent event) {
        event.getGenerator().addProvider(event.includeServer(),
                new ModBlockTagsProvider(
                        event.getGenerator().getPackOutput(),
                        event.getLookupProvider(),
                        com.example.uncraftingtable.UncraftingTableMod.MOD_ID,
                        event.getExistingFileHelper()));
    }
}

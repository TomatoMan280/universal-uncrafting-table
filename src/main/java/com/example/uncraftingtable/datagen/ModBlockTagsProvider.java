package com.example.uncraftingtable.datagen;

import net.neoforged.neoforge.common.data.BlockTagsProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.minecraft.tags.BlockTags;
import java.util.concurrent.CompletableFuture;
import com.example.uncraftingtable.UncraftingTableMod;

public class ModBlockTagsProvider extends BlockTagsProvider {
    public ModBlockTagsProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider, String modId, ExistingFileHelper existingFileHelper) {
        super(output, lookupProvider, modId, existingFileHelper);
    }

    @Override
    protected void addTags(HolderLookup.Provider provider) {
        // Make the Uncrafting Table harvestable by pickaxe, axe, and shovel
        this.tag(BlockTags.MINEABLE_WITH_PICKAXE).add(UncraftingTableMod.UNCRAFTING_TABLE.get());
        this.tag(BlockTags.MINEABLE_WITH_AXE).add(UncraftingTableMod.UNCRAFTING_TABLE.get());
        this.tag(BlockTags.MINEABLE_WITH_SHOVEL).add(UncraftingTableMod.UNCRAFTING_TABLE.get());
    }
}

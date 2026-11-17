package com.example.uncraftingtable.command;

import com.example.uncraftingtable.UncraftingTableMod;
import com.example.uncraftingtable.block.entity.UncraftingTableBlockEntity;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.ArrayList;
import java.util.List;

@EventBusSubscriber(modid = UncraftingTableMod.MOD_ID)
public class UncraftingTestCommand {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("testuncrafting")
                .requires(source -> source.hasPermission(2))
                .executes(UncraftingTestCommand::execute));
    }

    private static int execute(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        source.sendSuccess(() -> Component.literal("Starting uncrafting test... Check server log for full details."), false);
        
        try {
            UncraftingTableBlockEntity entity = new UncraftingTableBlockEntity(
                net.minecraft.core.BlockPos.ZERO,
                UncraftingTableMod.UNCRAFTING_TABLE.get().defaultBlockState()
            );
            entity.setLevel(source.getLevel());

            List<Item> successItems = new ArrayList<>();
            List<Item> failItems = new ArrayList<>();
            List<Item> skippedItems = new ArrayList<>();

            for (Item item : BuiltInRegistries.ITEM) {
                if (item == Items.AIR) continue;
                
                // Clear inventory
                for (int i = 0; i < entity.itemHandler.getSlots(); i++) {
                    entity.itemHandler.setStackInSlot(i, ItemStack.EMPTY);
                }
                
                // Enable modifiers by inserting the items and keeping flags disabled
                entity.enableGrindstone = false;
                entity.enableSmoker = false;
                entity.enableBlastFurnace = false;
                entity.enableBrewingStand = false;
                
                entity.itemHandler.setStackInSlot(UncraftingTableBlockEntity.GRINDSTONE_SLOT, new ItemStack(Items.GRINDSTONE));
                entity.itemHandler.setStackInSlot(UncraftingTableBlockEntity.SMOKER_SLOT, new ItemStack(Items.SMOKER));
                entity.itemHandler.setStackInSlot(UncraftingTableBlockEntity.BLAST_FURNACE_SLOT, new ItemStack(Items.BLAST_FURNACE));
                entity.itemHandler.setStackInSlot(UncraftingTableBlockEntity.BREWING_STAND_SLOT, new ItemStack(Items.BREWING_STAND));
                
                entity.itemHandler.setStackInSlot(UncraftingTableBlockEntity.INPUT_START_SLOT, new ItemStack(item, item.getDefaultMaxStackSize()));
                
                if (item == Items.POTION || item == Items.SPLASH_POTION || item == Items.LINGERING_POTION) {
                    entity.itemHandler.setStackInSlot(UncraftingTableBlockEntity.INPUT_START_SLOT + 1, new ItemStack(item, 1));
                    entity.itemHandler.setStackInSlot(UncraftingTableBlockEntity.INPUT_START_SLOT + 2, new ItemStack(item, 1));
                }

                // Fill energy (in case energy is required eventually)
                entity.energyStorage.receiveEnergy(1000000, false);
                
                // Track output contents before
                boolean success = entity.tryUncraft();
                
                // Check if any output generated
                boolean hasOutput = false;
                for(int i = UncraftingTableBlockEntity.MATERIAL_START_SLOT; i <= UncraftingTableBlockEntity.MATERIAL_END_SLOT; i++) {
                    if (!entity.itemHandler.getStackInSlot(i).isEmpty()) {
                        hasOutput = true;
                        break;
                    }
                }
                
                if (success && hasOutput) {
                    successItems.add(item);
                } else if (!success && !hasOutput) {
                    // Check if it got left in the input slot
                    if (!entity.itemHandler.getStackInSlot(UncraftingTableBlockEntity.INPUT_START_SLOT).isEmpty()) {
                        failItems.add(item);
                    } else {
                        skippedItems.add(item); 
                    }
                }
            }
            
            source.sendSuccess(() -> Component.literal(
                String.format("Test Complete! Success: %d, Failed/Rejected: %d, Skipped: %d", 
                successItems.size(), failItems.size(), skippedItems.size())
            ), true);
            
            System.out.println("====== UNCRAFTING TEST REPORT ======");
            System.out.println("Successful uncrafting: " + successItems.size());
            System.out.println("Rejected/Failed uncrafting: " + failItems.size());
            System.out.println("Skipped uncrafting (no output, no reject): " + skippedItems.size());
            
        } catch (Exception e) {
            source.sendFailure(Component.literal("Test failed with exception: " + e.getMessage()));
            e.printStackTrace();
        }

        return 1;
    }
}

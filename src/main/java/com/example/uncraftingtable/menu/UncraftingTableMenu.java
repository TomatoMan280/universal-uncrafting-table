package com.example.uncraftingtable.menu;

import com.example.uncraftingtable.UncraftingTableMod;
import com.example.uncraftingtable.block.entity.UncraftingTableBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

public class UncraftingTableMenu extends AbstractContainerMenu {
    private static final int PLAYER_INVENTORY_START = UncraftingTableBlockEntity.SLOT_COUNT;
    private static final int PLAYER_HOTBAR_END = PLAYER_INVENTORY_START + 36;

    private final ContainerLevelAccess access;
    private final IItemHandler tableItems;
    public final UncraftingTableBlockEntity blockEntity;

    private boolean tableSlotsActive = true;
    private boolean playerSlotsActive = true;

    public UncraftingTableMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf data) {
        this(containerId, playerInventory, getBlockEntity(playerInventory, data.readBlockPos()));
    }

    public UncraftingTableMenu(int containerId, Inventory playerInventory, UncraftingTableBlockEntity blockEntity) {
        super(UncraftingTableMod.UNCRAFTING_TABLE_MENU.get(), containerId);
        this.access = ContainerLevelAccess.create(blockEntity.getLevel(), blockEntity.getBlockPos());
        this.tableItems = blockEntity.itemHandler;
        this.blockEntity = blockEntity;

        addTableSlots();
        addPlayerInventorySlots(playerInventory);

        addDataSlot(new DataSlot() {
            @Override public int get() { return blockEntity.energyStorage.getEnergyStored(); }
            @Override public void set(int value) { blockEntity.energyStorage.setEnergy(value); }
        });
        addDataSlot(new DataSlot() {
            @Override public int get() { return blockEntity.autoUncraft ? 1 : 0; }
            @Override public void set(int value) { blockEntity.autoUncraft = value == 1; }
        });
        addDataSlot(new DataSlot() {
            @Override public int get() { return blockEntity.muteSounds ? 1 : 0; }
            @Override public void set(int value) { blockEntity.muteSounds = value == 1; }
        });
        addDataSlot(new DataSlot() {
            @Override public int get() { return blockEntity.muteParticles ? 1 : 0; }
            @Override public void set(int value) { blockEntity.muteParticles = value == 1; }
        });
        addDataSlot(new DataSlot() {
            @Override public int get() { return blockEntity.enableGrindstone ? 1 : 0; }
            @Override public void set(int value) { blockEntity.enableGrindstone = value == 1; }
        });
        addDataSlot(new DataSlot() {
            @Override public int get() { return blockEntity.enableSmoker ? 1 : 0; }
            @Override public void set(int value) { blockEntity.enableSmoker = value == 1; }
        });
        addDataSlot(new DataSlot() {
            @Override public int get() { return blockEntity.enableBlastFurnace ? 1 : 0; }
            @Override public void set(int value) { blockEntity.enableBlastFurnace = value == 1; }
        });
        addDataSlot(new DataSlot() {
            @Override public int get() { return blockEntity.enableBrewingStand ? 1 : 0; }
            @Override public void set(int value) { blockEntity.enableBrewingStand = value == 1; }
        });
        addDataSlot(new DataSlot() {
            @Override public int get() { return blockEntity.waitForUnconfiguredChoice ? 1 : 0; }
            @Override public void set(int value) { blockEntity.waitForUnconfiguredChoice = value == 1; }
        });
    }

    public void setSlotVisibility(boolean tableActive, boolean playerActive) {
        this.tableSlotsActive = tableActive;
        this.playerSlotsActive = playerActive;
    }

    private static UncraftingTableBlockEntity getBlockEntity(Inventory playerInventory, BlockPos pos) {
        Level level = playerInventory.player.level();
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof UncraftingTableBlockEntity uncraftingTable) {
            return uncraftingTable;
        }
        throw new IllegalStateException("Expected uncrafting table block entity at " + pos);
    }

    private void addTableSlots() {
        // 4x4 Input Grid (slots 0..15)
        for (int row = 0; row < UncraftingTableBlockEntity.INPUT_ROWS; row++) {
            for (int col = 0; col < UncraftingTableBlockEntity.INPUT_COLS; col++) {
                int slot = UncraftingTableBlockEntity.INPUT_START_SLOT + col + row * UncraftingTableBlockEntity.INPUT_COLS;
                addSlot(new ToggleableSlotItemHandler(tableItems, slot, 8 + col * 18, 20 + row * 18));
            }
        }

        // 4x4 Output Grid (slots 16..31)
        for (int row = 0; row < UncraftingTableBlockEntity.OUTPUT_ROWS; row++) {
            for (int col = 0; col < UncraftingTableBlockEntity.OUTPUT_COLS; col++) {
                int slot = UncraftingTableBlockEntity.MATERIAL_START_SLOT + col + row * UncraftingTableBlockEntity.OUTPUT_COLS;
                addSlot(new OutputSlot(tableItems, slot, 164 + col * 18, 20 + row * 18));
            }
        }

        // Book Slot (32)
        addSlot(new ToggleableSlotItemHandler(tableItems, UncraftingTableBlockEntity.BOOK_SLOT, 16, 105) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(Items.BOOK);
            }
        });

        // Upgrade / Speed Card Slot (33)
        addSlot(new ToggleableSlotItemHandler(tableItems, UncraftingTableBlockEntity.UPGRADE_SLOT, 52, 105) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return isSpeedCard(stack);
            }
        });

        // 4 Modifier Slots (34..37)
        addSlot(new ModifierSlot(tableItems, UncraftingTableBlockEntity.GRINDSTONE_SLOT, 164, 105, Items.GRINDSTONE));
        addSlot(new ModifierSlot(tableItems, UncraftingTableBlockEntity.SMOKER_SLOT, 182, 105, Items.SMOKER));
        addSlot(new ModifierSlot(tableItems, UncraftingTableBlockEntity.BLAST_FURNACE_SLOT, 200, 105, Items.BLAST_FURNACE));
        addSlot(new ModifierSlot(tableItems, UncraftingTableBlockEntity.BREWING_STAND_SLOT, 218, 105, Items.BREWING_STAND));
    }

    private void addPlayerInventorySlots(Inventory playerInventory) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new PlayerToggleableSlot(playerInventory, column + row * 9 + 9, 47 + column * 18, 145 + row * 18));
            }
        }

        for (int column = 0; column < 9; column++) {
            addSlot(new PlayerToggleableSlot(playerInventory, column, 47 + column * 18, 203));
        }
    }

    private static boolean isSpeedCard(ItemStack stack) {
        return stack.is(UncraftingTableMod.BASIC_SPEED_CARD.get()) ||
               stack.is(UncraftingTableMod.ADVANCED_SPEED_CARD.get()) ||
               stack.is(UncraftingTableMod.ELITE_SPEED_CARD.get()) ||
               stack.is(UncraftingTableMod.ULTIMATE_SPEED_CARD.get()) ||
               stack.is(UncraftingTableMod.CREATIVE_SPEED_CARD.get());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = slots.get(index);

        if (slot != null && slot.hasItem()) {
            ItemStack stack = slot.getItem();
            result = stack.copy();

            if (index < PLAYER_INVENTORY_START) {
                if (!moveItemStackTo(stack, PLAYER_INVENTORY_START, PLAYER_HOTBAR_END, true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                boolean moved = false;
                if (stack.is(Items.BOOK)) {
                    moved = moveItemStackTo(stack, UncraftingTableBlockEntity.BOOK_SLOT, UncraftingTableBlockEntity.BOOK_SLOT + 1, false);
                } else if (isSpeedCard(stack)) {
                    moved = moveItemStackTo(stack, UncraftingTableBlockEntity.UPGRADE_SLOT, UncraftingTableBlockEntity.UPGRADE_SLOT + 1, false);
                } else if (stack.is(Items.GRINDSTONE)) {
                    moved = moveItemStackTo(stack, UncraftingTableBlockEntity.GRINDSTONE_SLOT, UncraftingTableBlockEntity.GRINDSTONE_SLOT + 1, false);
                } else if (stack.is(Items.SMOKER)) {
                    moved = moveItemStackTo(stack, UncraftingTableBlockEntity.SMOKER_SLOT, UncraftingTableBlockEntity.SMOKER_SLOT + 1, false);
                } else if (stack.is(Items.BLAST_FURNACE)) {
                    moved = moveItemStackTo(stack, UncraftingTableBlockEntity.BLAST_FURNACE_SLOT, UncraftingTableBlockEntity.BLAST_FURNACE_SLOT + 1, false);
                } else if (stack.is(Items.BREWING_STAND)) {
                    moved = moveItemStackTo(stack, UncraftingTableBlockEntity.BREWING_STAND_SLOT, UncraftingTableBlockEntity.BREWING_STAND_SLOT + 1, false);
                }

                if (!moved && !stack.isEmpty()) {
                    if (!moveItemStackTo(stack, UncraftingTableBlockEntity.INPUT_START_SLOT, UncraftingTableBlockEntity.INPUT_END_SLOT + 1, false)) {
                        return ItemStack.EMPTY;
                    }
                }
            }

            if (stack.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }

            if (stack.getCount() == result.getCount()) {
                return ItemStack.EMPTY;
            }
            slot.onTake(player, stack);
        }

        return result;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, UncraftingTableMod.UNCRAFTING_TABLE.get());
    }

    private class ToggleableSlotItemHandler extends SlotItemHandler {
        ToggleableSlotItemHandler(IItemHandler itemHandler, int index, int xPosition, int yPosition) {
            super(itemHandler, index, xPosition, yPosition);
        }

        @Override
        public boolean isActive() {
            return tableSlotsActive;
        }
    }

    private class OutputSlot extends ToggleableSlotItemHandler {
        OutputSlot(IItemHandler itemHandler, int index, int xPosition, int yPosition) {
            super(itemHandler, index, xPosition, yPosition);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }
    }

    private class ModifierSlot extends ToggleableSlotItemHandler {
        private final Item requiredItem;

        ModifierSlot(IItemHandler itemHandler, int index, int xPosition, int yPosition, Item requiredItem) {
            super(itemHandler, index, xPosition, yPosition);
            this.requiredItem = requiredItem;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return stack.is(requiredItem);
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }

    private class PlayerToggleableSlot extends Slot {
        PlayerToggleableSlot(Inventory inv, int index, int xPosition, int yPosition) {
            super(inv, index, xPosition, yPosition);
        }

        @Override
        public boolean isActive() {
            return playerSlotsActive;
        }
    }
}

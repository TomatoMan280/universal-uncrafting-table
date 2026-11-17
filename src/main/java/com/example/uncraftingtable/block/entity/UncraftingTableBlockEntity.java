package com.example.uncraftingtable.block.entity;

import com.example.uncraftingtable.UncraftingTableMod;
import com.example.uncraftingtable.menu.UncraftingTableMenu;
import com.example.uncraftingtable.recipe.UncraftingRecipeResolver;
import com.example.uncraftingtable.recipe.UncraftingRecipeResolver.IngredientGroup;
import com.example.uncraftingtable.recipe.UncraftingRecipeResolver.ResolvedRecipe;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class UncraftingTableBlockEntity extends BlockEntity implements MenuProvider {

    // Expanded 4x4 Input (0-15) + 4x4 Output (16-31) + Book + Speed + 4 Modifiers = 38 slots
    public static final int INPUT_COLS = 4;
    public static final int INPUT_ROWS = 4;
    public static final int OUTPUT_COLS = 4;
    public static final int OUTPUT_ROWS = 4;
    public static final int INPUT_SLOTS = INPUT_COLS * INPUT_ROWS;
    public static final int OUTPUT_SLOTS = OUTPUT_COLS * OUTPUT_ROWS;

    public static final int SLOT_COUNT = 38;
    public static final int INPUT_START_SLOT = 0;
    public static final int INPUT_END_SLOT = 15;
    public static final int MATERIAL_START_SLOT = 16;
    public static final int MATERIAL_END_SLOT = 31;
    public static final int BOOK_SLOT = 32;
    public static final int UPGRADE_SLOT = 33;
    public static final int GRINDSTONE_SLOT = 34;
    public static final int SMOKER_SLOT = 35;
    public static final int BLAST_FURNACE_SLOT = 36;
    public static final int BREWING_STAND_SLOT = 37;

    public static final TagKey<Item> BLACKLIST_TAG =
            ItemTags.create(ResourceLocation.fromNamespaceAndPath("uncrafting_table", "blacklist"));

    private static final int ENERGY_CAPACITY = 10000;
    private static final ResourceLocation AIR_ID = ResourceLocation.withDefaultNamespace("air");

    public int getEnergyCost() {
        ItemStack card = itemHandler.getStackInSlot(UPGRADE_SLOT);
        if (card.is(UncraftingTableMod.CREATIVE_SPEED_CARD.get())) return 0;
        if (card.is(UncraftingTableMod.ULTIMATE_SPEED_CARD.get())) return 800;
        if (card.is(UncraftingTableMod.ELITE_SPEED_CARD.get())) return 600;
        if (card.is(UncraftingTableMod.ADVANCED_SPEED_CARD.get())) return 400;
        if (card.is(UncraftingTableMod.BASIC_SPEED_CARD.get())) return 200;
        return 100;
    }

    public int getSpeedDelay() {
        ItemStack card = itemHandler.getStackInSlot(UPGRADE_SLOT);
        if (card.is(UncraftingTableMod.CREATIVE_SPEED_CARD.get())) return 1;
        if (card.is(UncraftingTableMod.ULTIMATE_SPEED_CARD.get())) return 1;
        if (card.is(UncraftingTableMod.ELITE_SPEED_CARD.get())) return 5;
        if (card.is(UncraftingTableMod.ADVANCED_SPEED_CARD.get())) return 10;
        if (card.is(UncraftingTableMod.BASIC_SPEED_CARD.get())) return 20;
        return 40;
    }

    public final CustomEnergyStorage energyStorage =
            new CustomEnergyStorage(ENERGY_CAPACITY, 1000, 0, this::setChanged);

    public static class CustomEnergyStorage extends EnergyStorage {
        private final Runnable onEnergyChanged;

        public CustomEnergyStorage(int capacity, int maxReceive, int maxExtract, Runnable onEnergyChanged) {
            super(capacity, maxReceive, maxExtract);
            this.onEnergyChanged = onEnergyChanged;
        }

        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            int r = super.receiveEnergy(maxReceive, simulate);
            if (r > 0 && !simulate) onEnergyChanged.run();
            return r;
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            int e = super.extractEnergy(maxExtract, simulate);
            if (e > 0 && !simulate) onEnergyChanged.run();
            return e;
        }

        public void setEnergy(int energy) {
            this.energy = Math.max(0, Math.min(capacity, energy));
        }

        public int getCapacity() {
            return capacity;
        }
    }

    public boolean autoUncraft = false;
    public boolean muteSounds = false;
    public boolean muteParticles = false;

    public boolean enableGrindstone = true;
    public boolean enableSmoker = true;
    public boolean enableBlastFurnace = true;
    public boolean enableBrewingStand = true;
    public boolean waitForUnconfiguredChoice = false;

    public record RecipeRule(
            ResourceLocation itemId,
            ResourceLocation recipeId,
            List<ResourceLocation> chosenMaterialIds
    ) {
    }

    // Ordered configurable list of output recipe rules (Pipez-style filter rules)
    private final Map<ResourceLocation, RecipeRule> recipeRules = new LinkedHashMap<>();

    private int tickCounter = 0;

    public final ItemStackHandler itemHandler = new ItemStackHandler(SLOT_COUNT) {
        @Override
        public int getSlotLimit(int slot) {
            if (slot == GRINDSTONE_SLOT || slot == SMOKER_SLOT || slot == BLAST_FURNACE_SLOT || slot == BREWING_STAND_SLOT) {
                return 1;
            }
            return super.getSlotLimit(slot);
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            if (slot == BOOK_SLOT) return stack.is(Items.BOOK);
            if (slot == UPGRADE_SLOT) {
                return stack.is(UncraftingTableMod.BASIC_SPEED_CARD.get()) ||
                       stack.is(UncraftingTableMod.ADVANCED_SPEED_CARD.get()) ||
                       stack.is(UncraftingTableMod.ELITE_SPEED_CARD.get()) ||
                       stack.is(UncraftingTableMod.ULTIMATE_SPEED_CARD.get()) ||
                       stack.is(UncraftingTableMod.CREATIVE_SPEED_CARD.get());
            }
            if (slot == GRINDSTONE_SLOT) return stack.is(Items.GRINDSTONE);
            if (slot == SMOKER_SLOT) return stack.is(Items.SMOKER);
            if (slot == BLAST_FURNACE_SLOT) return stack.is(Items.BLAST_FURNACE);
            if (slot == BREWING_STAND_SLOT) return stack.is(Items.BREWING_STAND);
            return true;
        }
    };

    public final IItemHandler automationItemHandler = new IItemHandler() {
        @Override
        public int getSlots() {
            return SLOT_COUNT;
        }

        @Override
        public @NotNull ItemStack getStackInSlot(int slot) {
            return itemHandler.getStackInSlot(slot);
        }

        @Override
        public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
            // Only accept automated insertion into Input grid and Book slot (protect Upgrade/Modifiers/Output)
            boolean isInput = slot >= INPUT_START_SLOT && slot <= INPUT_END_SLOT;
            boolean isBook = slot == BOOK_SLOT && stack.is(Items.BOOK);
            if (!isInput && !isBook) {
                return stack;
            }
            return itemHandler.insertItem(slot, stack, simulate);
        }

        @Override
        public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot < MATERIAL_START_SLOT || slot > MATERIAL_END_SLOT) {
                return ItemStack.EMPTY;
            }
            return itemHandler.extractItem(slot, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return itemHandler.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            boolean isInput = slot >= INPUT_START_SLOT && slot <= INPUT_END_SLOT;
            boolean isBook = slot == BOOK_SLOT && stack.is(Items.BOOK);
            return (isInput || isBook) && itemHandler.isItemValid(slot, stack);
        }
    };

    public IItemHandler getItemHandler(@Nullable Direction side) {
        return automationItemHandler;
    }

    public UncraftingTableBlockEntity(BlockPos pos, BlockState state) {
        super(UncraftingTableMod.UNCRAFTING_TABLE_BLOCK_ENTITY.get(), pos, state);
    }

    public boolean hasGrindstoneModifier() {
        return !enableGrindstone || itemHandler.getStackInSlot(GRINDSTONE_SLOT).is(Items.GRINDSTONE);
    }

    public boolean hasSmokerModifier() {
        return !enableSmoker || itemHandler.getStackInSlot(SMOKER_SLOT).is(Items.SMOKER);
    }

    public boolean hasBlastFurnaceModifier() {
        return !enableBlastFurnace || itemHandler.getStackInSlot(BLAST_FURNACE_SLOT).is(Items.BLAST_FURNACE);
    }

    public boolean hasBrewingStandModifier() {
        return !enableBrewingStand || itemHandler.getStackInSlot(BREWING_STAND_SLOT).is(Items.BREWING_STAND);
    }

    public void tick() {
        if (level == null || level.isClientSide()) return;

        tickCounter++;
        if (tickCounter % getSpeedDelay() != 0) return;

        if (autoUncraft) {
            int currentCost = getEnergyCost();
            if (energyStorage.getEnergyStored() >= currentCost) {
                try {
                    if (tryUncraft(true)) {
                        energyStorage.setEnergy(energyStorage.getEnergyStored() - currentCost);
                        setChanged();
                    }
                } catch (Exception e) {
                    System.err.println("UNCRAFT_ERROR: Exception in tick: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }
    }

    public void manualUncraft() {
        if (tryUncraft(false)) {
            setChanged();
        }
    }

    public void updateSettings(
            boolean autoUncraft,
            int speed,
            boolean muteSounds,
            boolean muteParticles,
            boolean enableGrindstone,
            boolean enableSmoker,
            boolean enableBlastFurnace,
            boolean enableBrewingStand,
            boolean waitForUnconfiguredChoice
    ) {
        this.autoUncraft = autoUncraft;
        this.muteSounds = muteSounds;
        this.muteParticles = muteParticles;
        this.enableGrindstone = enableGrindstone;
        this.enableSmoker = enableSmoker;
        this.enableBlastFurnace = enableBlastFurnace;
        this.enableBrewingStand = enableBrewingStand;
        this.waitForUnconfiguredChoice = waitForUnconfiguredChoice;
        syncToClients();
    }

    public void saveRecipeRule(ResourceLocation itemId, ResourceLocation recipeId, List<ResourceLocation> chosenMaterialIds) {
        if (itemId == null || recipeId == null) return;
        recipeRules.put(itemId, new RecipeRule(itemId, recipeId, List.copyOf(chosenMaterialIds)));
        syncToClients();
    }

    public void removeRecipeRule(ResourceLocation itemId) {
        if (itemId != null && recipeRules.remove(itemId) != null) {
            syncToClients();
        }
    }

    public List<RecipeRule> getRecipeRules() {
        return new ArrayList<>(recipeRules.values());
    }

    public boolean hasRuleFor(ItemStack inputStack) {
        if (inputStack.isEmpty()) return false;
        return recipeRules.containsKey(BuiltInRegistries.ITEM.getKey(inputStack.getItem()));
    }

    @Nullable
    public RecipeRule getRuleFor(ItemStack inputStack) {
        if (inputStack.isEmpty()) return null;
        return recipeRules.get(BuiltInRegistries.ITEM.getKey(inputStack.getItem()));
    }

    public int getSelectedRecipeIndex(ItemStack inputStack, List<ResolvedRecipe> recipes) {
        if (recipes.isEmpty() || inputStack.isEmpty()) return 0;
        RecipeRule rule = getRuleFor(inputStack);
        if (rule != null) {
            for (int i = 0; i < recipes.size(); i++) {
                if (recipes.get(i).id().equals(rule.recipeId())) {
                    return i;
                }
            }
        }
        return 0;
    }

    public int getSelectedVariantIndex(
            ItemStack inputStack,
            ResolvedRecipe recipe,
            int groupIndex,
            IngredientGroup group
    ) {
        if (group.variants().size() <= 1 || inputStack.isEmpty() || recipe == null) return 0;
        RecipeRule rule = getRuleFor(inputStack);
        if (rule == null || !rule.recipeId().equals(recipe.id()) || groupIndex >= rule.chosenMaterialIds().size()) {
            return 0;
        }
        ResourceLocation savedMat = rule.chosenMaterialIds().get(groupIndex);
        if (savedMat == null) return 0;

        List<ItemStack> variants = group.variants();
        for (int i = 0; i < variants.size(); i++) {
            if (BuiltInRegistries.ITEM.getKey(variants.get(i).getItem()).equals(savedMat)) {
                return i;
            }
        }
        return 0;
    }

    private void syncToClients() {
        setChanged();
        if (level != null && !level.isClientSide()) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, 3);
        }
    }

    public boolean tryUncraft() {
        return tryUncraft(false);
    }

    public boolean tryUncraft(boolean fromAuto) {
        if (level == null) return false;

        boolean hasGrindstone = hasGrindstoneModifier();
        boolean hasSmoker = hasSmokerModifier();
        boolean hasBlastFurnace = hasBlastFurnaceModifier();
        boolean hasBrewingStand = hasBrewingStandModifier();

        for (int slot = INPUT_START_SLOT; slot <= INPUT_END_SLOT; slot++) {
            ItemStack inputStack = itemHandler.getStackInSlot(slot);
            if (inputStack.isEmpty()) continue;

            if (inputStack.is(BLACKLIST_TAG)) {
                rejectItem(slot);
                continue;
            }

            // Enchantment Check
            boolean hasEnchants = inputStack.has(DataComponents.ENCHANTMENTS)
                    && !inputStack.get(DataComponents.ENCHANTMENTS).isEmpty();
            if (hasEnchants && !hasGrindstone) {
                continue;
            }

            // Potion Check (requires 3 identical potions across input slots)
            if (inputStack.is(Items.POTION) || inputStack.is(Items.SPLASH_POTION) || inputStack.is(Items.LINGERING_POTION)) {
                List<Integer> potionSlots = new ArrayList<>();
                for (int pSlot = INPUT_START_SLOT; pSlot <= INPUT_END_SLOT; pSlot++) {
                    ItemStack pStack = itemHandler.getStackInSlot(pSlot);
                    if (ItemStack.isSameItemSameComponents(inputStack, pStack)) {
                        potionSlots.add(pSlot);
                    }
                }
                if (potionSlots.size() >= 3 && handlePotionUncrafting(potionSlots, inputStack, hasBrewingStand)) {
                    return true;
                }
                continue;
            }

            List<ResolvedRecipe> recipes = UncraftingRecipeResolver.findRecipes(
                    level, inputStack, hasSmoker, hasBlastFurnace);

            if (recipes.isEmpty()) {
                rejectItem(slot);
                continue;
            }

            // In Auto mode with "Wait for Filter" enabled, pause unconfigured multi-recipe items safely
            if (fromAuto && waitForUnconfiguredChoice && !hasRuleFor(inputStack)
                    && UncraftingRecipeResolver.hasMultipleChoices(recipes)) {
                continue;
            }

            int recipeIdx = getSelectedRecipeIndex(inputStack, recipes);
            ResolvedRecipe recipe = recipes.get(recipeIdx);

            if (inputStack.getCount() >= recipe.inputCost() && hasRoomForOutput(recipe, inputStack)) {
                processUncrafting(slot, recipe, inputStack);
                playUncraftEffects(inputStack, recipe.sourceLabel());
                return true;
            }
        }
        return false;
    }

    private void playUncraftEffects(ItemStack inputStack, String sourceLabel) {
        if (!(level instanceof ServerLevel serverLevel)) return;

        boolean hasEnchants = inputStack.has(DataComponents.ENCHANTMENTS)
                && !inputStack.get(DataComponents.ENCHANTMENTS).isEmpty();

        SoundEvent sound = SoundEvents.SMITHING_TABLE_USE;
        net.minecraft.core.particles.ParticleOptions particle = net.minecraft.core.particles.ParticleTypes.CRIT;

        if (hasEnchants) {
            sound = SoundEvents.GRINDSTONE_USE;
            particle = net.minecraft.core.particles.ParticleTypes.ENCHANT;
        } else if ("Smoking".equals(sourceLabel) || "Campfire".equals(sourceLabel)) {
            sound = SoundEvents.SMOKER_SMOKE;
            particle = net.minecraft.core.particles.ParticleTypes.SMOKE;
        } else if ("Blasting".equals(sourceLabel)) {
            sound = SoundEvents.BLASTFURNACE_FIRE_CRACKLE;
            particle = net.minecraft.core.particles.ParticleTypes.LAVA;
        } else if (inputStack.is(Items.POTION) || inputStack.is(Items.SPLASH_POTION) || inputStack.is(Items.LINGERING_POTION)) {
            sound = SoundEvents.BREWING_STAND_BREW;
            particle = net.minecraft.core.particles.ParticleTypes.BUBBLE_POP;
        }

        if (!muteSounds) {
            serverLevel.playSound(null, getBlockPos(), sound, SoundSource.BLOCKS, 0.6F, 1.2F);
        }
        if (!muteParticles) {
            serverLevel.sendParticles(
                    particle,
                    getBlockPos().getX() + 0.5D,
                    getBlockPos().getY() + 1.2D,
                    getBlockPos().getZ() + 0.5D,
                    12, 0.3D, 0.3D, 0.3D, 0.1D
            );
        }
    }

    private boolean handlePotionUncrafting(List<Integer> inputSlots, ItemStack inputStack, boolean hasBrewingStand) {
        if (!hasBrewingStand) return false;

        java.util.Optional<UncraftingRecipeResolver.PotionStep> stepOpt =
                UncraftingRecipeResolver.resolvePotionOutputs(level, inputStack);
        if (stepOpt.isEmpty()) {
            return false;
        }

        ItemStack bottle = stepOpt.get().baseBottles().copy();
        ItemStack wart = stepOpt.get().reagent().copy();

        ItemStack[] simulatedOutput = createSimulatedOutput();
        if (!simulateInsert(simulatedOutput, bottle) || !simulateInsert(simulatedOutput, wart)) {
            return false;
        }

        int consumed = 0;
        for (int pSlot : inputSlots) {
            if (consumed >= 3) break;
            itemHandler.extractItem(pSlot, 1, false);
            consumed++;
        }

        insertIntoOutput(bottle);
        insertIntoOutput(wart);

        playUncraftEffects(inputStack, "Brewing");
        return true;
    }

    private ItemStack[] createSimulatedOutput() {
        ItemStack[] simulatedOutput = new ItemStack[OUTPUT_SLOTS];
        for (int i = 0; i < OUTPUT_SLOTS; i++) {
            simulatedOutput[i] = itemHandler.getStackInSlot(MATERIAL_START_SLOT + i).copy();
        }
        return simulatedOutput;
    }

    private boolean simulateInsert(ItemStack[] simulatedOutput, ItemStack stackToInsert) {
        if (stackToInsert.isEmpty()) return true;
        ItemStack remaining = stackToInsert.copy();
        while (!remaining.isEmpty()) {
            int chunk = Math.min(remaining.getCount(), remaining.getMaxStackSize());
            ItemStack singleStack = remaining.copyWithCount(chunk);
            boolean placed = false;

            for (int i = 0; i < simulatedOutput.length; i++) {
                if (simulatedOutput[i].isEmpty()) {
                    simulatedOutput[i] = singleStack.copy();
                    singleStack.setCount(0);
                    placed = true;
                    break;
                } else if (ItemStack.isSameItemSameComponents(simulatedOutput[i], singleStack)) {
                    int space = simulatedOutput[i].getMaxStackSize() - simulatedOutput[i].getCount();
                    if (space > 0) {
                        int move = Math.min(space, singleStack.getCount());
                        simulatedOutput[i].grow(move);
                        singleStack.shrink(move);
                        if (singleStack.isEmpty()) {
                            placed = true;
                            break;
                        }
                    }
                }
            }
            if (!placed && !singleStack.isEmpty()) {
                return false;
            }
            remaining.shrink(chunk);
        }
        return true;
    }

    private void insertIntoOutput(ItemStack stack) {
        if (stack.isEmpty()) return;
        ItemStack remaining = stack.copy();
        while (!remaining.isEmpty()) {
            int chunkSize = Math.min(remaining.getCount(), remaining.getMaxStackSize());
            ItemStack chunk = remaining.copyWithCount(chunkSize);
            for (int outSlot = MATERIAL_START_SLOT; outSlot <= MATERIAL_END_SLOT; outSlot++) {
                chunk = itemHandler.insertItem(outSlot, chunk, false);
                if (chunk.isEmpty()) break;
            }
            remaining.shrink(chunkSize);
        }
    }

    private boolean hasRoomForOutput(ResolvedRecipe recipe, ItemStack inputStack) {
        ItemStack[] simulatedOutput = createSimulatedOutput();

        List<IngredientGroup> groups = recipe.groups();
        for (int g = 0; g < groups.size(); g++) {
            IngredientGroup group = groups.get(g);
            int varIdx = getSelectedVariantIndex(inputStack, recipe, g, group);
            ItemStack baseVariant = group.pickVariant(varIdx);
            if (!baseVariant.isEmpty() && !baseVariant.is(BLACKLIST_TAG)) {
                ItemStack toInsert = baseVariant.copyWithCount(group.count());
                if (!simulateInsert(simulatedOutput, toInsert)) {
                    return false;
                }
            }
        }

        ItemEnchantments enchantments = inputStack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        if (!enchantments.isEmpty()) {
            ItemStack existingBook = itemHandler.getStackInSlot(BOOK_SLOT);
            if (existingBook.isEmpty() || !existingBook.is(Items.BOOK)) {
                return false;
            }
            ItemStack enchantedBook = new ItemStack(Items.ENCHANTED_BOOK, 1);
            enchantedBook.set(DataComponents.STORED_ENCHANTMENTS, enchantments);
            if (!simulateInsert(simulatedOutput, enchantedBook)) {
                return false;
            }
        }

        return true;
    }

    private void processUncrafting(int inputSlot, ResolvedRecipe recipe, ItemStack inputStack) {
        ItemEnchantments enchantments = inputStack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        if (!enchantments.isEmpty()) {
            ItemStack enchantedBook = new ItemStack(Items.ENCHANTED_BOOK);
            enchantedBook.set(DataComponents.STORED_ENCHANTMENTS, enchantments);
            itemHandler.extractItem(BOOK_SLOT, 1, false);
            insertIntoOutput(enchantedBook);
        }

        itemHandler.extractItem(inputSlot, recipe.inputCost(), false);

        List<IngredientGroup> groups = recipe.groups();
        for (int g = 0; g < groups.size(); g++) {
            IngredientGroup group = groups.get(g);
            int varIdx = getSelectedVariantIndex(inputStack, recipe, g, group);
            ItemStack baseVariant = group.pickVariant(varIdx);
            if (!baseVariant.isEmpty() && !baseVariant.is(BLACKLIST_TAG)) {
                insertIntoOutput(baseVariant.copyWithCount(group.count()));
            }
        }
    }

    private void rejectItem(int inputSlot) {
        ItemStack inputStack = itemHandler.getStackInSlot(inputSlot);
        if (inputStack.isEmpty()) return;

        ItemStack remaining = inputStack.copy();
        for (int i = MATERIAL_START_SLOT; i <= MATERIAL_END_SLOT; i++) {
            remaining = itemHandler.insertItem(i, remaining, false);
            if (remaining.isEmpty()) {
                break;
            }
        }
        itemHandler.setStackInSlot(inputSlot, remaining);
    }

    private void savePreferences(CompoundTag tag) {
        ListTag rulesList = new ListTag();
        for (RecipeRule rule : recipeRules.values()) {
            CompoundTag entry = new CompoundTag();
            entry.putString("item", rule.itemId().toString());
            entry.putString("recipe", rule.recipeId().toString());
            ListTag mats = new ListTag();
            for (ResourceLocation matId : rule.chosenMaterialIds()) {
                mats.add(StringTag.valueOf(matId.toString()));
            }
            entry.put("materials", mats);
            rulesList.add(entry);
        }
        tag.put("recipeRules", rulesList);
    }

    private void loadPreferences(CompoundTag tag) {
        recipeRules.clear();
        if (tag.contains("recipeRules", Tag.TAG_LIST)) {
            ListTag rulesList = tag.getList("recipeRules", Tag.TAG_COMPOUND);
            for (int i = 0; i < rulesList.size(); i++) {
                CompoundTag entry = rulesList.getCompound(i);
                ResourceLocation itemId = ResourceLocation.tryParse(entry.getString("item"));
                ResourceLocation recipeId = ResourceLocation.tryParse(entry.getString("recipe"));
                if (itemId == null || recipeId == null) continue;

                List<ResourceLocation> mats = new ArrayList<>();
                ListTag matsTag = entry.getList("materials", Tag.TAG_STRING);
                for (int m = 0; m < matsTag.size(); m++) {
                    ResourceLocation matId = ResourceLocation.tryParse(matsTag.getString(m));
                    mats.add(matId != null ? matId : AIR_ID);
                }
                recipeRules.put(itemId, new RecipeRule(itemId, recipeId, List.copyOf(mats)));
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("v2Slots", true);
        tag.put("inventory", itemHandler.serializeNBT(registries));
        tag.putInt("energy", energyStorage.getEnergyStored());
        tag.putBoolean("autoUncraft", autoUncraft);
        tag.putBoolean("muteSounds", muteSounds);
        tag.putBoolean("muteParticles", muteParticles);
        tag.putBoolean("enableGrindstone", enableGrindstone);
        tag.putBoolean("enableSmoker", enableSmoker);
        tag.putBoolean("enableBlastFurnace", enableBlastFurnace);
        tag.putBoolean("enableBrewingStand", enableBrewingStand);
        tag.putBoolean("waitForUnconfiguredChoice", waitForUnconfiguredChoice);
        savePreferences(tag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);

        CompoundTag invTag = tag.getCompound("inventory");
        // Migrate legacy 24-slot (3x3) saves cleanly into 38-slot (4x4) layout
        if (!tag.getBoolean("v2Slots") && invTag.contains("Size", Tag.TAG_INT) && invTag.getInt("Size") <= 24) {
            ItemStackHandler legacy = new ItemStackHandler(24);
            legacy.deserializeNBT(registries, invTag);
            for (int i = 0; i < SLOT_COUNT; i++) {
                itemHandler.setStackInSlot(i, ItemStack.EMPTY);
            }
            for (int i = 0; i <= 8; i++) itemHandler.setStackInSlot(INPUT_START_SLOT + i, legacy.getStackInSlot(i));
            for (int i = 0; i <= 8; i++) itemHandler.setStackInSlot(MATERIAL_START_SLOT + i, legacy.getStackInSlot(9 + i));
            itemHandler.setStackInSlot(BOOK_SLOT, legacy.getStackInSlot(18));
            itemHandler.setStackInSlot(UPGRADE_SLOT, legacy.getStackInSlot(19));
            itemHandler.setStackInSlot(GRINDSTONE_SLOT, legacy.getStackInSlot(20));
            itemHandler.setStackInSlot(SMOKER_SLOT, legacy.getStackInSlot(21));
            itemHandler.setStackInSlot(BLAST_FURNACE_SLOT, legacy.getStackInSlot(22));
            itemHandler.setStackInSlot(BREWING_STAND_SLOT, legacy.getStackInSlot(23));
        } else {
            if (invTag.contains("Size", Tag.TAG_INT) && invTag.getInt("Size") < SLOT_COUNT) {
                invTag.putInt("Size", SLOT_COUNT);
            }
            itemHandler.deserializeNBT(registries, invTag);
        }

        energyStorage.setEnergy(tag.getInt("energy"));
        autoUncraft = tag.getBoolean("autoUncraft");
        muteSounds = tag.getBoolean("muteSounds");
        muteParticles = tag.getBoolean("muteParticles");
        if (tag.contains("enableGrindstone")) {
            enableGrindstone = tag.getBoolean("enableGrindstone");
            enableSmoker = tag.getBoolean("enableSmoker");
            enableBlastFurnace = tag.getBoolean("enableBlastFurnace");
            enableBrewingStand = tag.getBoolean("enableBrewingStand");
        }
        waitForUnconfiguredChoice = tag.getBoolean("waitForUnconfiguredChoice");
        loadPreferences(tag);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putBoolean("autoUncraft", autoUncraft);
        tag.putBoolean("muteSounds", muteSounds);
        tag.putBoolean("muteParticles", muteParticles);
        tag.putBoolean("enableGrindstone", enableGrindstone);
        tag.putBoolean("enableSmoker", enableSmoker);
        tag.putBoolean("enableBlastFurnace", enableBlastFurnace);
        tag.putBoolean("enableBrewingStand", enableBrewingStand);
        tag.putBoolean("waitForUnconfiguredChoice", waitForUnconfiguredChoice);
        savePreferences(tag);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        loadPreferences(tag);
        if (tag.contains("autoUncraft")) {
            autoUncraft = tag.getBoolean("autoUncraft");
            muteSounds = tag.getBoolean("muteSounds");
            muteParticles = tag.getBoolean("muteParticles");
        }
        if (tag.contains("enableGrindstone")) {
            enableGrindstone = tag.getBoolean("enableGrindstone");
            enableSmoker = tag.getBoolean("enableSmoker");
            enableBlastFurnace = tag.getBoolean("enableBlastFurnace");
            enableBrewingStand = tag.getBoolean("enableBrewingStand");
        }
        if (tag.contains("waitForUnconfiguredChoice")) {
            waitForUnconfiguredChoice = tag.getBoolean("waitForUnconfiguredChoice");
        }
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt, HolderLookup.Provider registries) {
        CompoundTag tag = pkt.getTag();
        if (tag != null) {
            handleUpdateTag(tag, registries);
        }
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.uncrafting_table.uncrafting_table");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new UncraftingTableMenu(containerId, playerInventory, this);
    }
}

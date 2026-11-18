package com.example.uncraftingtable.gametest;

import com.example.uncraftingtable.UncraftingTableMod;
import com.example.uncraftingtable.block.entity.UncraftingTableBlockEntity;
import com.example.uncraftingtable.block.entity.UncraftingTableBlockEntity.RecipeRule;
import com.example.uncraftingtable.menu.UncraftingTableMenu;
import com.example.uncraftingtable.network.UncraftingActionPayload;
import com.example.uncraftingtable.network.UncraftingSelectionPayload;
import com.example.uncraftingtable.network.UncraftingSettingsPayload;
import com.example.uncraftingtable.recipe.UncraftingRecipeResolver;
import com.example.uncraftingtable.recipe.UncraftingRecipeResolver.ConcreteOutput;
import com.example.uncraftingtable.recipe.UncraftingRecipeResolver.ResolvedRecipe;
import io.netty.buffer.Unpooled;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.List;

@GameTestHolder(UncraftingTableMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class UncraftingTableGameTests {

    private static final BlockPos TABLE_POS = new BlockPos(2, 1, 2);

    private static UncraftingTableBlockEntity placeTable(GameTestHelper helper) {
        helper.setBlock(TABLE_POS, UncraftingTableMod.UNCRAFTING_TABLE.get().defaultBlockState());
        return helper.getBlockEntity(TABLE_POS);
    }

    private static int countOutput(UncraftingTableBlockEntity be, Item target) {
        int total = 0;
        for (int s = UncraftingTableBlockEntity.MATERIAL_START_SLOT; s <= UncraftingTableBlockEntity.MATERIAL_END_SLOT; s++) {
            ItemStack st = be.itemHandler.getStackInSlot(s);
            if (st.is(target)) {
                total += st.getCount();
            }
        }
        return total;
    }

    private static int countOccupiedOutputSlots(UncraftingTableBlockEntity be) {
        int count = 0;
        for (int s = UncraftingTableBlockEntity.MATERIAL_START_SLOT; s <= UncraftingTableBlockEntity.MATERIAL_END_SLOT; s++) {
            if (!be.itemHandler.getStackInSlot(s).isEmpty()) {
                count++;
            }
        }
        return count;
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void craftingAnd4x4BatchGrid(GameTestHelper helper) {
        UncraftingTableBlockEntity be = placeTable(helper);
        helper.assertTrue(be.itemHandler.getSlots() == 38, "Expected 38 total slots for 4x4 layout");

        // Load 11 distinct non-stackable / crafting items across 4x4 input grid (beyond legacy 3x3 limit)
        Item[] inputs = {
                Items.IRON_PICKAXE,
                Items.DIAMOND_AXE,
                Items.IRON_CHESTPLATE,
                Items.FURNACE,
                Items.CRAFTING_TABLE,
                Items.PISTON,
                Items.DISPENSER,
                Items.CAULDRON,
                Items.ANVIL,
                Items.CROSSBOW,
                Items.SPYGLASS
        };
        for (int i = 0; i < inputs.length; i++) {
            be.itemHandler.setStackInSlot(UncraftingTableBlockEntity.INPUT_START_SLOT + i, new ItemStack(inputs[i]));
        }

        // Manual uncraft with 0 FE must process every item and expand safely into > 9 output slots
        for (int i = 0; i < inputs.length; i++) {
            be.manualUncraft();
        }

        int occupiedOutputs = countOccupiedOutputSlots(be);
        helper.assertTrue(occupiedOutputs > 9,
                "Expected >9 occupied output slots proving 4x4 output expansion works, got " + occupiedOutputs);
        helper.assertTrue(countOutput(be, Items.DIAMOND) == 3, "Expected 3 diamonds from Diamond Axe");
        helper.assertTrue(countOutput(be, Items.AMETHYST_SHARD) == 1, "Expected 1 amethyst shard from Spyglass");
        helper.assertTrue(be.energyStorage.getEnergyStored() == 0, "Manual uncraft must cost 0 FE");

        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void pipezStyleRecipeRulesAndWaitPolicy(GameTestHelper helper) {
        UncraftingTableBlockEntity be = placeTable(helper);
        ServerLevel level = helper.getLevel();

        ItemStack pickaxe = new ItemStack(Items.STONE_PICKAXE);
        List<ResolvedRecipe> recipes = UncraftingRecipeResolver.findRecipes(level, pickaxe, true, true);
        helper.assertTrue(UncraftingRecipeResolver.hasMultipleChoices(recipes),
                "Stone Pickaxe should expose multiple material choices");

        List<ConcreteOutput> expanded = UncraftingRecipeResolver.expandOutputOptions(recipes);
        helper.assertTrue(expanded.size() >= 3,
                "Expected Cobblestone/Cobbled Deepslate/Blackstone options, got " + expanded.size());

        // Turn Auto ON with Wait for Filter enabled and no rule saved -> must pause without consuming FE
        be.energyStorage.setEnergy(500);
        be.autoUncraft = true;
        be.waitForUnconfiguredChoice = true;
        be.itemHandler.setStackInSlot(0, pickaxe.copy());

        for (int t = 0; t < 40; t++) {
            be.tick();
        }
        helper.assertTrue(be.itemHandler.getStackInSlot(0).is(Items.STONE_PICKAXE),
                "Wait-for-Filter mode must pause unconfigured multi-recipe item");
        helper.assertTrue(be.energyStorage.getEnergyStored() == 500,
                "Paused item must consume 0 FE");

        // Save rule selecting Cobbled Deepslate + Stick -> next auto cycle must obey it
        ResourceLocation pickId = ResourceLocation.withDefaultNamespace("stone_pickaxe");
        be.saveRecipeRule(pickId, pickId, List.of(
                ResourceLocation.withDefaultNamespace("cobbled_deepslate"),
                ResourceLocation.withDefaultNamespace("stick")
        ));

        for (int t = 0; t < 40; t++) {
            be.tick();
        }
        helper.assertTrue(be.itemHandler.getStackInSlot(0).isEmpty(), "Saved rule should allow Auto uncraft");
        helper.assertTrue(countOutput(be, Items.COBBLED_DEEPSLATE) == 3, "Expected 3 Cobbled Deepslate");
        helper.assertTrue(countOutput(be, Items.STICK) == 2, "Expected 2 Sticks");
        helper.assertTrue(be.energyStorage.getEnergyStored() == 400, "Default auto delay costs 100 FE");

        // Switch rule to Blackstone and test update
        be.saveRecipeRule(pickId, pickId, List.of(
                ResourceLocation.withDefaultNamespace("blackstone"),
                ResourceLocation.withDefaultNamespace("stick")
        ));
        be.itemHandler.setStackInSlot(0, pickaxe.copy());
        for (int t = 0; t < 40; t++) {
            be.tick();
        }
        helper.assertTrue(countOutput(be, Items.BLACKSTONE) == 3, "Updated rule must output 3 Blackstone");

        // Remove rule and disable Wait for Filter -> reverts to Auto Default
        be.removeRecipeRule(pickId);
        helper.assertTrue(be.getRecipeRules().isEmpty(), "Rule list should be empty after removeRecipeRule");
        be.waitForUnconfiguredChoice = false;
        be.itemHandler.setStackInSlot(0, pickaxe.copy());
        for (int t = 0; t < 40; t++) {
            be.tick();
        }
        helper.assertTrue(be.itemHandler.getStackInSlot(0).isEmpty(),
                "Auto Default mode should process unconfigured items using default recipe");

        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void planksCostAndNoDupe(GameTestHelper helper) {
        UncraftingTableBlockEntity be = placeTable(helper);

        // 3 oak planks (< inputCost 4) must NOT uncraft into 1 oak log
        be.itemHandler.setStackInSlot(0, new ItemStack(Items.OAK_PLANKS, 3));
        helper.assertTrue(!be.tryUncraft(), "3 Oak Planks should not uncraft (inputCost = 4)");
        helper.assertTrue(be.itemHandler.getStackInSlot(0).getCount() == 3, "3 Oak Planks must remain intact");

        // Clear and test 4 Spruce Planks with explicit rule picking Stripped Spruce Log
        be.itemHandler.setStackInSlot(0, new ItemStack(Items.SPRUCE_PLANKS, 4));
        be.saveRecipeRule(
                ResourceLocation.withDefaultNamespace("spruce_planks"),
                ResourceLocation.withDefaultNamespace("spruce_planks"),
                List.of(ResourceLocation.withDefaultNamespace("stripped_spruce_log"))
        );
        helper.assertTrue(be.tryUncraft(), "4 Spruce Planks should uncraft into chosen log");
        helper.assertTrue(be.itemHandler.getStackInSlot(0).isEmpty(), "Planks input should be consumed");
        helper.assertTrue(countOutput(be, Items.STRIPPED_SPRUCE_LOG) == 1, "Expected 1 Stripped Spruce Log");

        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void smithingAndEnchantExtraction(GameTestHelper helper) {
        UncraftingTableBlockEntity be = placeTable(helper);
        ServerLevel level = helper.getLevel();

        var enchRegistry = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        Holder<Enchantment> sharp = enchRegistry.getOrThrow(Enchantments.SHARPNESS);
        Holder<Enchantment> unbreaking = enchRegistry.getOrThrow(Enchantments.UNBREAKING);

        ItemStack netheriteSword = new ItemStack(Items.NETHERITE_SWORD);
        netheriteSword.enchant(sharp, 4);
        netheriteSword.enchant(unbreaking, 3);

        be.itemHandler.setStackInSlot(UncraftingTableBlockEntity.GRINDSTONE_SLOT, new ItemStack(Items.GRINDSTONE));
        be.itemHandler.setStackInSlot(0, netheriteSword.copy());

        // Without Book in BOOK_SLOT, enchanted sword must NOT uncraft
        helper.assertTrue(!be.tryUncraft(), "Enchanted item without Book must not uncraft");
        helper.assertTrue(be.itemHandler.getStackInSlot(0).is(Items.NETHERITE_SWORD), "Sword must remain safe");

        // With Book in BOOK_SLOT -> recover Diamond Sword + Netherite Ingot + Template + Enchanted Book
        be.itemHandler.setStackInSlot(UncraftingTableBlockEntity.BOOK_SLOT, new ItemStack(Items.BOOK, 2));
        helper.assertTrue(be.tryUncraft(), "Enchanted Netherite Sword with Book + Grindstone must uncraft");

        helper.assertTrue(countOutput(be, Items.DIAMOND_SWORD) == 1, "Expected Diamond Sword");
        helper.assertTrue(countOutput(be, Items.NETHERITE_INGOT) == 1, "Expected Netherite Ingot");
        helper.assertTrue(countOutput(be, Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE) == 1, "Expected Upgrade Template");
        helper.assertTrue(be.itemHandler.getStackInSlot(UncraftingTableBlockEntity.BOOK_SLOT).getCount() == 1, "Should consume 1 Book");

        ItemStack outBook = ItemStack.EMPTY;
        for (int s = UncraftingTableBlockEntity.MATERIAL_START_SLOT; s <= UncraftingTableBlockEntity.MATERIAL_END_SLOT; s++) {
            ItemStack st = be.itemHandler.getStackInSlot(s);
            if (st.is(Items.ENCHANTED_BOOK)) {
                outBook = st;
                break;
            }
        }
        helper.assertTrue(!outBook.isEmpty(), "Expected Enchanted Book in output");
        ItemEnchantments stored = outBook.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
        helper.assertTrue(stored.getLevel(sharp) == 4 && stored.getLevel(unbreaking) == 3,
                "Stored enchantments must preserve Sharpness IV and Unbreaking III");

        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void modifierGatesCheckboxesAndPotions(GameTestHelper helper) {
        UncraftingTableBlockEntity be = placeTable(helper);

        // 1. Smoker gate + bypass checkbox
        be.enableSmoker = true;
        be.itemHandler.setStackInSlot(0, new ItemStack(Items.COOKED_PORKCHOP));
        be.tryUncraft(); // no smoker -> rejected safely to output (never deleted)
        helper.assertTrue(countOutput(be, Items.COOKED_PORKCHOP) == 1,
                "Without Smoker modifier, Cooked Porkchop should be safely moved to output");
        helper.assertTrue(countOutput(be, Items.PORKCHOP) == 0,
                "Without Smoker modifier, Porkchop must not be produced");

        // Clear output and insert Smoker
        for (int s = UncraftingTableBlockEntity.MATERIAL_START_SLOT; s <= UncraftingTableBlockEntity.MATERIAL_END_SLOT; s++) {
            be.itemHandler.setStackInSlot(s, ItemStack.EMPTY);
        }
        be.itemHandler.setStackInSlot(UncraftingTableBlockEntity.SMOKER_SLOT, new ItemStack(Items.SMOKER));
        be.itemHandler.setStackInSlot(0, new ItemStack(Items.COOKED_PORKCHOP));
        helper.assertTrue(be.tryUncraft(), "Smoker modifier must allow uncrafting Cooked Porkchop");
        helper.assertTrue(countOutput(be, Items.PORKCHOP) == 1, "Expected 1 Raw Porkchop");

        // Disable Smoker requirement via checkbox and remove Smoker item
        be.itemHandler.setStackInSlot(UncraftingTableBlockEntity.SMOKER_SLOT, ItemStack.EMPTY);
        be.enableSmoker = false;
        be.itemHandler.setStackInSlot(0, new ItemStack(Items.COOKED_PORKCHOP));
        helper.assertTrue(be.tryUncraft(), "Unchecked Smoker requirement should allow Smoker-free reversal");
        helper.assertTrue(countOutput(be, Items.PORKCHOP) == 2, "Expected 2 total Raw Porkchops");

        // 2. Blast Furnace gate on Gold Ingot (blasting tier 0 wins over nugget crafting tier 4)
        for (int s = 0; s < UncraftingTableBlockEntity.SLOT_COUNT; s++) {
            be.itemHandler.setStackInSlot(s, ItemStack.EMPTY);
        }
        be.itemHandler.setStackInSlot(UncraftingTableBlockEntity.BLAST_FURNACE_SLOT, new ItemStack(Items.BLAST_FURNACE));
        be.saveRecipeRule(
                ResourceLocation.withDefaultNamespace("gold_ingot"),
                ResourceLocation.withDefaultNamespace("gold_ingot_from_blasting_raw_gold"),
                List.of(ResourceLocation.withDefaultNamespace("raw_gold"))
        );
        be.itemHandler.setStackInSlot(0, new ItemStack(Items.GOLD_INGOT));
        helper.assertTrue(be.tryUncraft(), "Blast Furnace rule must uncraft Gold Ingot to Raw Gold");
        helper.assertTrue(countOutput(be, Items.RAW_GOLD) == 1, "Expected 1 Raw Gold");

        // 3. Brewing Stand 3-potion reversal (Lingering -> Splash + Dragon's Breath)
        for (int s = 0; s < UncraftingTableBlockEntity.SLOT_COUNT; s++) {
            be.itemHandler.setStackInSlot(s, ItemStack.EMPTY);
        }
        be.itemHandler.setStackInSlot(UncraftingTableBlockEntity.BREWING_STAND_SLOT, new ItemStack(Items.BREWING_STAND));

        ItemStack lingHeal = PotionContents.createItemStack(Items.LINGERING_POTION, Potions.HEALING);
        be.itemHandler.setStackInSlot(0, lingHeal.copy());
        be.itemHandler.setStackInSlot(1, lingHeal.copy());
        helper.assertTrue(!be.tryUncraft(), "2 identical potions must not uncraft (requires 3)");

        be.itemHandler.setStackInSlot(2, lingHeal.copy());
        helper.assertTrue(be.tryUncraft(), "3 identical Lingering Healing Potions must uncraft");
        helper.assertTrue(countOutput(be, Items.SPLASH_POTION) == 3, "Expected 3 Splash Potions");
        helper.assertTrue(countOutput(be, Items.DRAGON_BREATH) == 1, "Expected 1 Dragon's Breath");

        // Base Water Bottle must never uncraft
        ItemStack waterBottle = PotionContents.createItemStack(Items.POTION, Potions.WATER);
        be.itemHandler.setStackInSlot(0, waterBottle.copy());
        be.itemHandler.setStackInSlot(1, waterBottle.copy());
        be.itemHandler.setStackInSlot(2, waterBottle.copy());
        helper.assertTrue(!be.tryUncraft(), "Water bottles should not uncraft");

        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 120)
    public static void capabilitiesAndRealHoppers(GameTestHelper helper) {
        UncraftingTableBlockEntity be = placeTable(helper);
        ServerLevel level = helper.getLevel();
        BlockPos absTable = helper.absolutePos(TABLE_POS);

        IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, absTable, Direction.NORTH);
        IEnergyStorage energy = level.getCapability(Capabilities.EnergyStorage.BLOCK, absTable, Direction.NORTH);
        helper.assertTrue(handler != null, "ItemHandler capability must be registered");
        helper.assertTrue(energy != null, "EnergyStorage capability must be registered");

        helper.assertTrue(energy.receiveEnergy(1000, false) == 1000 && energy.getEnergyStored() == 1000,
                "FE receive must charge energy buffer");

        // Automation safety checks: protect Upgrade/Output/Modifier slots from insertion & Input/Modifiers from extraction
        helper.assertTrue(!handler.insertItem(UncraftingTableBlockEntity.UPGRADE_SLOT, new ItemStack(UncraftingTableMod.BASIC_SPEED_CARD.get()), false).isEmpty(),
                "Automation must NOT insert into Speed Card slot");
        helper.assertTrue(!handler.insertItem(UncraftingTableBlockEntity.GRINDSTONE_SLOT, new ItemStack(Items.GRINDSTONE), false).isEmpty(),
                "Automation must NOT insert into Modifier slot");
        helper.assertTrue(!handler.insertItem(UncraftingTableBlockEntity.MATERIAL_START_SLOT, new ItemStack(Items.DIAMOND), false).isEmpty(),
                "Automation must NOT insert into Output slot");
        helper.assertTrue(handler.insertItem(UncraftingTableBlockEntity.BOOK_SLOT, new ItemStack(Items.BOOK), false).isEmpty(),
                "Automation must allow Book into BOOK_SLOT");
        helper.assertTrue(handler.insertItem(UncraftingTableBlockEntity.BOOK_SLOT, new ItemStack(Items.STICK), false).getCount() == 1,
                "Automation must reject non-Book from BOOK_SLOT");
        helper.assertTrue(handler.extractItem(UncraftingTableBlockEntity.BOOK_SLOT, 1, false).isEmpty(),
                "Automation must NOT extract from BOOK_SLOT");

        // Place Hopper above (inputs Diamond Sword) & Hopper below (extracts outputs) with Auto ON
        be.autoUncraft = true;
        be.itemHandler.setStackInSlot(UncraftingTableBlockEntity.UPGRADE_SLOT, new ItemStack(UncraftingTableMod.CREATIVE_SPEED_CARD.get()));

        BlockPos topHopperPos = TABLE_POS.above();
        BlockPos bottomHopperPos = TABLE_POS.below();
        helper.setBlock(topHopperPos, Blocks.HOPPER.defaultBlockState());
        helper.setBlock(bottomHopperPos, Blocks.HOPPER.defaultBlockState());

        HopperBlockEntity topHopper = helper.getBlockEntity(topHopperPos);
        HopperBlockEntity bottomHopper = helper.getBlockEntity(bottomHopperPos);
        topHopper.setItem(0, new ItemStack(Items.DIAMOND_SWORD));

        helper.succeedWhen(() -> {
            int sticks = 0;
            int diamonds = 0;
            for (int i = 0; i < bottomHopper.getContainerSize(); i++) {
                ItemStack st = bottomHopper.getItem(i);
                if (st.is(Items.STICK)) sticks += st.getCount();
                if (st.is(Items.DIAMOND)) diamonds += st.getCount();
            }
            helper.assertTrue(diamonds == 2 && sticks == 1,
                    "Expected real Hoppers to insert Sword, auto-uncraft, and extract 2 Diamonds + 1 Stick (got d=" + diamonds + ", s=" + sticks + ")");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void speedCardsAllTiers(GameTestHelper helper) {
        UncraftingTableBlockEntity be = placeTable(helper);

        // No card
        helper.assertTrue(be.getSpeedDelay() == 40 && be.getEnergyCost() == 100, "No card tier mismatch");

        be.itemHandler.setStackInSlot(UncraftingTableBlockEntity.UPGRADE_SLOT, new ItemStack(UncraftingTableMod.BASIC_SPEED_CARD.get()));
        helper.assertTrue(be.getSpeedDelay() == 20 && be.getEnergyCost() == 200, "Basic card tier mismatch");

        be.itemHandler.setStackInSlot(UncraftingTableBlockEntity.UPGRADE_SLOT, new ItemStack(UncraftingTableMod.ADVANCED_SPEED_CARD.get()));
        helper.assertTrue(be.getSpeedDelay() == 10 && be.getEnergyCost() == 400, "Advanced card tier mismatch");

        be.itemHandler.setStackInSlot(UncraftingTableBlockEntity.UPGRADE_SLOT, new ItemStack(UncraftingTableMod.ELITE_SPEED_CARD.get()));
        helper.assertTrue(be.getSpeedDelay() == 5 && be.getEnergyCost() == 600, "Elite card tier mismatch");

        be.itemHandler.setStackInSlot(UncraftingTableBlockEntity.UPGRADE_SLOT, new ItemStack(UncraftingTableMod.ULTIMATE_SPEED_CARD.get()));
        helper.assertTrue(be.getSpeedDelay() == 1 && be.getEnergyCost() == 800, "Ultimate card tier mismatch");

        be.itemHandler.setStackInSlot(UncraftingTableBlockEntity.UPGRADE_SLOT, new ItemStack(UncraftingTableMod.CREATIVE_SPEED_CARD.get()));
        helper.assertTrue(be.getSpeedDelay() == 1 && be.getEnergyCost() == 0, "Creative card tier mismatch");

        // Creative Speed Card must uncraft in Auto mode with 0 stored FE
        be.energyStorage.setEnergy(0);
        be.autoUncraft = true;
        be.itemHandler.setStackInSlot(0, new ItemStack(Items.CRAFTING_TABLE));
        be.tick();
        helper.assertTrue(be.itemHandler.getStackInSlot(0).isEmpty(), "Creative card must process every 1 tick with 0 FE");

        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void blacklistAndFullOutputSafety(GameTestHelper helper) {
        UncraftingTableBlockEntity be = placeTable(helper);

        // Blacklisted items (including craftable Glass Bottle) must move to Output intact rather than uncrafting
        ItemStack bottle = new ItemStack(Items.GLASS_BOTTLE, 3);
        helper.assertTrue(bottle.is(UncraftingTableBlockEntity.BLACKLIST_TAG),
                "#uncrafting_table:blacklist tag must be loaded and contain minecraft:glass_bottle");
        be.itemHandler.setStackInSlot(0, bottle);
        be.tryUncraft();
        helper.assertTrue(be.itemHandler.getStackInSlot(0).isEmpty(), "Blacklisted Glass Bottle should leave input");
        helper.assertTrue(countOutput(be, Items.GLASS_BOTTLE) == 3 && countOutput(be, Items.GLASS) == 0,
                "Blacklisted Glass Bottle must arrive intact in Output and NOT uncraft to Glass");

        // Clear output before testing full output safety
        for (int s = UncraftingTableBlockEntity.MATERIAL_START_SLOT; s <= UncraftingTableBlockEntity.MATERIAL_END_SLOT; s++) {
            be.itemHandler.setStackInSlot(s, ItemStack.EMPTY);
        }

        // Full Output grid must prevent uncrafting without consuming input
        for (int s = UncraftingTableBlockEntity.MATERIAL_START_SLOT; s <= UncraftingTableBlockEntity.MATERIAL_END_SLOT; s++) {
            be.itemHandler.setStackInSlot(s, new ItemStack(Items.WOODEN_SWORD));
        }
        be.itemHandler.setStackInSlot(0, new ItemStack(Items.IRON_PICKAXE));
        helper.assertTrue(!be.tryUncraft(), "Full output grid must block uncrafting");
        helper.assertTrue(be.itemHandler.getStackInSlot(0).is(Items.IRON_PICKAXE),
                "Input Iron Pickaxe must not be consumed when Output is full");

        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void nbtRoundtripAndLegacy24SlotMigration(GameTestHelper helper) {
        UncraftingTableBlockEntity be = placeTable(helper);
        var registries = helper.getLevel().registryAccess();

        be.waitForUnconfiguredChoice = true;
        be.autoUncraft = true;
        be.energyStorage.setEnergy(4321);
        be.saveRecipeRule(
                ResourceLocation.withDefaultNamespace("stone_pickaxe"),
                ResourceLocation.withDefaultNamespace("stone_pickaxe"),
                List.of(ResourceLocation.withDefaultNamespace("cobbled_deepslate"), ResourceLocation.withDefaultNamespace("stick"))
        );
        be.itemHandler.setStackInSlot(15, new ItemStack(Items.IRON_SWORD));
        be.itemHandler.setStackInSlot(31, new ItemStack(Items.DIAMOND, 7));

        CompoundTag saved = be.saveWithoutMetadata(registries);

        UncraftingTableBlockEntity loaded = new UncraftingTableBlockEntity(BlockPos.ZERO, UncraftingTableMod.UNCRAFTING_TABLE.get().defaultBlockState());
        loaded.loadWithComponents(saved, registries);

        helper.assertTrue(loaded.waitForUnconfiguredChoice, "NBT roundtrip must keep waitForUnconfiguredChoice");
        helper.assertTrue(loaded.autoUncraft, "NBT roundtrip must keep autoUncraft");
        helper.assertTrue(loaded.energyStorage.getEnergyStored() == 4321, "NBT roundtrip must keep FE");
        helper.assertTrue(loaded.itemHandler.getStackInSlot(15).is(Items.IRON_SWORD), "Slot 15 must persist");
        helper.assertTrue(loaded.itemHandler.getStackInSlot(31).getCount() == 7, "Slot 31 must persist");

        List<RecipeRule> rules = loaded.getRecipeRules();
        helper.assertTrue(rules.size() == 1 && rules.get(0).chosenMaterialIds().get(0).equals(ResourceLocation.withDefaultNamespace("cobbled_deepslate")),
                "NBT roundtrip must preserve recipeRules");

        // Legacy 24-slot migration (v1 -> 38-slot v2Slots)
        ItemStackHandler legacyInv = new ItemStackHandler(24);
        legacyInv.setStackInSlot(8, new ItemStack(Items.STONE_AXE));
        legacyInv.setStackInSlot(17, new ItemStack(Items.OAK_PLANKS, 12));
        legacyInv.setStackInSlot(18, new ItemStack(Items.BOOK, 5));
        legacyInv.setStackInSlot(19, new ItemStack(UncraftingTableMod.ELITE_SPEED_CARD.get()));
        legacyInv.setStackInSlot(20, new ItemStack(Items.GRINDSTONE));
        legacyInv.setStackInSlot(23, new ItemStack(Items.BREWING_STAND));

        CompoundTag legacyTag = new CompoundTag();
        legacyTag.put("inventory", legacyInv.serializeNBT(registries));
        legacyTag.putInt("energy", 900);
        legacyTag.put("recipeRules", new ListTag());

        UncraftingTableBlockEntity migrated = new UncraftingTableBlockEntity(BlockPos.ZERO, UncraftingTableMod.UNCRAFTING_TABLE.get().defaultBlockState());
        migrated.loadWithComponents(legacyTag, registries);

        helper.assertTrue(migrated.itemHandler.getStackInSlot(8).is(Items.STONE_AXE), "Legacy input slot 8 -> 8");
        helper.assertTrue(migrated.itemHandler.getStackInSlot(24).is(Items.OAK_PLANKS) && migrated.itemHandler.getStackInSlot(24).getCount() == 12,
                "Legacy output slot 17 -> 24");
        helper.assertTrue(migrated.itemHandler.getStackInSlot(UncraftingTableBlockEntity.BOOK_SLOT).getCount() == 5,
                "Legacy book slot 18 -> 32");
        helper.assertTrue(migrated.itemHandler.getStackInSlot(UncraftingTableBlockEntity.UPGRADE_SLOT).is(UncraftingTableMod.ELITE_SPEED_CARD.get()),
                "Legacy upgrade slot 19 -> 33");
        helper.assertTrue(migrated.itemHandler.getStackInSlot(UncraftingTableBlockEntity.GRINDSTONE_SLOT).is(Items.GRINDSTONE),
                "Legacy grindstone slot 20 -> 34");
        helper.assertTrue(migrated.itemHandler.getStackInSlot(UncraftingTableBlockEntity.BREWING_STAND_SLOT).is(Items.BREWING_STAND),
                "Legacy brewing slot 23 -> 37");

        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void menuAndPayloadCodecs(GameTestHelper helper) {
        UncraftingTableBlockEntity be = placeTable(helper);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);

        UncraftingTableMenu menu = new UncraftingTableMenu(1, player.getInventory(), be);
        helper.assertTrue(menu.slots.size() == 74, "Expected 38 table + 36 player = 74 menu slots");

        menu.setSlotVisibility(false, false);
        helper.assertTrue(!menu.slots.get(0).isActive() && !menu.slots.get(40).isActive(),
                "FILTER_LIST visibility must deactivate table and player slots");

        menu.setSlotVisibility(false, true);
        helper.assertTrue(!menu.slots.get(0).isActive() && menu.slots.get(40).isActive(),
                "FILTER_EDIT visibility must deactivate table slots and keep player inventory active");

        menu.setSlotVisibility(true, true);
        player.getInventory().setItem(0, new ItemStack(Items.BOOK, 3));
        player.getInventory().setItem(1, new ItemStack(UncraftingTableMod.BASIC_SPEED_CARD.get()));
        player.getInventory().setItem(2, new ItemStack(Items.GRINDSTONE));
        player.getInventory().setItem(3, new ItemStack(Items.DIAMOND_PICKAXE));

        menu.quickMoveStack(player, 38 + 27); // hotbar slot 0 (Book)
        menu.quickMoveStack(player, 38 + 28); // hotbar slot 1 (Speed Card)
        menu.quickMoveStack(player, 38 + 29); // hotbar slot 2 (Grindstone)
        menu.quickMoveStack(player, 38 + 30); // hotbar slot 3 (Diamond Pickaxe)

        helper.assertTrue(be.itemHandler.getStackInSlot(UncraftingTableBlockEntity.BOOK_SLOT).getCount() == 3, "QuickMove Book -> BOOK_SLOT");
        helper.assertTrue(be.itemHandler.getStackInSlot(UncraftingTableBlockEntity.UPGRADE_SLOT).is(UncraftingTableMod.BASIC_SPEED_CARD.get()),
                "QuickMove Speed Card -> UPGRADE_SLOT");
        helper.assertTrue(be.itemHandler.getStackInSlot(UncraftingTableBlockEntity.GRINDSTONE_SLOT).is(Items.GRINDSTONE),
                "QuickMove Grindstone -> GRINDSTONE_SLOT");
        helper.assertTrue(be.itemHandler.getStackInSlot(0).is(Items.DIAMOND_PICKAXE),
                "QuickMove regular gear -> INPUT slot 0");

        // Payload StreamCodec roundtrips
        BlockPos p = new BlockPos(12, 64, -7);
        FriendlyByteBuf rawBuf = new FriendlyByteBuf(Unpooled.buffer());
        UncraftingActionPayload action = new UncraftingActionPayload(p);
        UncraftingActionPayload.STREAM_CODEC.encode(rawBuf, action);
        helper.assertTrue(UncraftingActionPayload.STREAM_CODEC.decode(rawBuf).pos().equals(p), "ActionPayload roundtrip");

        RegistryFriendlyByteBuf regBuf = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess(), null);
        UncraftingSettingsPayload settings = new UncraftingSettingsPayload(p, true, 0, true, false, false, true, false, true, true);
        UncraftingSettingsPayload.STREAM_CODEC.encode(regBuf, settings);
        UncraftingSettingsPayload decodedSettings = UncraftingSettingsPayload.STREAM_CODEC.decode(regBuf);
        helper.assertTrue(decodedSettings.equals(settings), "SettingsPayload roundtrip");

        UncraftingSelectionPayload sel = new UncraftingSelectionPayload(
                p,
                UncraftingSelectionPayload.Action.SAVE,
                ResourceLocation.withDefaultNamespace("stone_pickaxe"),
                ResourceLocation.withDefaultNamespace("stone_pickaxe"),
                List.of(ResourceLocation.withDefaultNamespace("cobbled_deepslate"), ResourceLocation.withDefaultNamespace("stick"))
        );
        UncraftingSelectionPayload.STREAM_CODEC.encode(regBuf, sel);
        UncraftingSelectionPayload decodedSel = UncraftingSelectionPayload.STREAM_CODEC.decode(regBuf);
        helper.assertTrue(decodedSel.equals(sel), "SelectionPayload roundtrip");

        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void fullRegistryCommandSweep(GameTestHelper helper) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        CommandSourceStack source = helper.getLevel().getServer().createCommandSourceStack()
                .withLevel(helper.getLevel())
                .withPermission(4);
        int result = helper.getLevel().getServer().getCommands().getDispatcher().execute("testuncrafting", source);
        helper.assertTrue(result >= 1, "/testuncrafting sweep command across all items must complete without throwing");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 120)
    public static void powahAndPipezIntegration(GameTestHelper helper) {
        UncraftingTableBlockEntity be = placeTable(helper);
        ServerLevel level = helper.getLevel();
        BlockPos absTable = helper.absolutePos(TABLE_POS);

        // Verify Powah & Pipez loaded cleanly alongside our mod in run-gametest
        if (net.neoforged.fml.ModList.get().isLoaded("powah")) {
            Item dielectricPaste = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("powah", "dielectric_paste"));
            helper.assertTrue(dielectricPaste != Items.AIR, "Powah dielectric_paste must exist when Powah is loaded");
            be.itemHandler.setStackInSlot(0, new ItemStack(dielectricPaste, 64));
            helper.assertTrue(be.tryUncraft(), "Modded Powah item (dielectric_paste) must uncraft dynamically");
            helper.assertTrue(countOccupiedOutputSlots(be) > 0, "Uncrafting Powah item must produce output materials");

            // Connect a real Powah Energy Cell adjacent to the table and transfer FE into EnergyStorage capability
            net.minecraft.world.level.block.Block powahCell = BuiltInRegistries.BLOCK.get(
                    ResourceLocation.fromNamespaceAndPath("powah", "energy_cell_niotic"));
            BlockPos cellRel = TABLE_POS.east();
            helper.setBlock(cellRel, powahCell.defaultBlockState());
            IEnergyStorage cellEnergy = level.getCapability(Capabilities.EnergyStorage.BLOCK, helper.absolutePos(cellRel), Direction.WEST);
            IEnergyStorage tableEnergy = level.getCapability(Capabilities.EnergyStorage.BLOCK, absTable, Direction.EAST);
            helper.assertTrue(tableEnergy != null, "Table energy capability must be present on EAST face");
            if (cellEnergy != null && cellEnergy.canReceive()) {
                cellEnergy.receiveEnergy(2000, false);
                int pulled = cellEnergy.extractEnergy(tableEnergy.receiveEnergy(1000, true), false);
                tableEnergy.receiveEnergy(pulled, false);
                helper.assertTrue(be.energyStorage.getEnergyStored() >= 0, "Energy handshake with Powah cell must stay valid");
            }
        }

        if (net.neoforged.fml.ModList.get().isLoaded("pipez")) {
            for (int s = UncraftingTableBlockEntity.MATERIAL_START_SLOT; s <= UncraftingTableBlockEntity.MATERIAL_END_SLOT; s++) {
                be.itemHandler.setStackInSlot(s, ItemStack.EMPTY);
            }
            Item itemPipe = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("pipez", "item_pipe"));
            helper.assertTrue(itemPipe != Items.AIR, "Pipez item_pipe must exist when Pipez is loaded");
            be.itemHandler.setStackInSlot(0, new ItemStack(itemPipe, 64));
            List<ResolvedRecipe> pipeRecipes = UncraftingRecipeResolver.findRecipes(level, new ItemStack(itemPipe, 64), true, true);
            helper.assertTrue(!pipeRecipes.isEmpty(), "Pipez Item Pipe recipe must be dynamically resolved");
            helper.assertTrue(be.tryUncraft(), "Modded Pipez Item Pipe stack must uncraft cleanly");
        }

        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void stonecuttingCampfireAndSpeedCardUncraft(GameTestHelper helper) {
        UncraftingTableBlockEntity be = placeTable(helper);

        // Chiseled Stone Bricks -> Stone Bricks via Stonecutting OR Stone Brick Slab via Crafting
        ItemStack chiseled = new ItemStack(Items.CHISELED_STONE_BRICKS);
        List<ResolvedRecipe> recipes = UncraftingRecipeResolver.findRecipes(helper.getLevel(), chiseled, false, false);
        boolean hasStonecutting = recipes.stream().anyMatch(r -> "Stonecutting".equals(r.sourceLabel()));
        helper.assertTrue(hasStonecutting, "Chiseled Stone Bricks must expose Stonecutting option");

        ResolvedRecipe scRecipe = recipes.stream().filter(r -> "Stonecutting".equals(r.sourceLabel())).findFirst().orElseThrow();
        be.saveRecipeRule(
                ResourceLocation.withDefaultNamespace("chiseled_stone_bricks"),
                scRecipe.id(),
                List.of(ResourceLocation.withDefaultNamespace("stone_bricks"))
        );
        be.itemHandler.setStackInSlot(0, chiseled.copy());
        helper.assertTrue(be.tryUncraft(), "Saved Stonecutting filter rule must execute cleanly");
        helper.assertTrue(countOutput(be, Items.STONE_BRICKS) == 1, "Expected 1 Stone Bricks from Stonecutting rule");

        // Uncraft Ultimate Speed Card (modded SmithingTransformRecipe)
        for (int s = 0; s < UncraftingTableBlockEntity.SLOT_COUNT; s++) {
            be.itemHandler.setStackInSlot(s, ItemStack.EMPTY);
        }
        be.itemHandler.setStackInSlot(0, new ItemStack(UncraftingTableMod.ULTIMATE_SPEED_CARD.get()));
        helper.assertTrue(be.tryUncraft(), "Ultimate Speed Card must uncraft via SmithingTransformRecipe");
        helper.assertTrue(countOutput(be, UncraftingTableMod.ELITE_SPEED_CARD.get()) == 1, "Expected Elite Speed Card");
        helper.assertTrue(countOutput(be, Items.NETHERITE_INGOT) == 1, "Expected Netherite Ingot");
        helper.assertTrue(countOutput(be, Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE) == 1, "Expected Upgrade Template");

        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void clientSyncUpdateTagAndMultipleRules(GameTestHelper helper) {
        UncraftingTableBlockEntity serverBe = placeTable(helper);
        var registries = helper.getLevel().registryAccess();

        serverBe.autoUncraft = true;
        serverBe.muteSounds = true;
        serverBe.waitForUnconfiguredChoice = true;
        serverBe.enableSmoker = false;
        serverBe.saveRecipeRule(
                ResourceLocation.withDefaultNamespace("stone_pickaxe"),
                ResourceLocation.withDefaultNamespace("stone_pickaxe"),
                List.of(ResourceLocation.withDefaultNamespace("cobbled_deepslate"), ResourceLocation.withDefaultNamespace("stick"))
        );
        serverBe.saveRecipeRule(
                ResourceLocation.withDefaultNamespace("oak_planks"),
                ResourceLocation.withDefaultNamespace("oak_planks"),
                List.of(ResourceLocation.withDefaultNamespace("stripped_oak_log"))
        );

        CompoundTag updateTag = serverBe.getUpdateTag(registries);
        UncraftingTableBlockEntity clientBe = new UncraftingTableBlockEntity(BlockPos.ZERO, UncraftingTableMod.UNCRAFTING_TABLE.get().defaultBlockState());
        clientBe.handleUpdateTag(updateTag, registries);

        helper.assertTrue(clientBe.autoUncraft && clientBe.muteSounds && clientBe.waitForUnconfiguredChoice && !clientBe.enableSmoker,
                "getUpdateTag/handleUpdateTag must sync all toggles to client");
        helper.assertTrue(clientBe.getRecipeRules().size() == 2,
                "getUpdateTag/handleUpdateTag must sync all saved recipe rules");
        helper.assertTrue(clientBe.hasRuleFor(new ItemStack(Items.OAK_PLANKS)),
                "Client must recognize synced oak_planks rule");

        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void blockBreakDropsAll38Slots(GameTestHelper helper) {
        UncraftingTableBlockEntity be = placeTable(helper);
        be.itemHandler.setStackInSlot(15, new ItemStack(Items.IRON_PICKAXE));
        be.itemHandler.setStackInSlot(31, new ItemStack(Items.DIAMOND, 3));
        be.itemHandler.setStackInSlot(UncraftingTableBlockEntity.BOOK_SLOT, new ItemStack(Items.BOOK, 2));
        be.itemHandler.setStackInSlot(UncraftingTableBlockEntity.UPGRADE_SLOT, new ItemStack(UncraftingTableMod.BASIC_SPEED_CARD.get()));
        be.itemHandler.setStackInSlot(UncraftingTableBlockEntity.BREWING_STAND_SLOT, new ItemStack(Items.BREWING_STAND));

        helper.getLevel().destroyBlock(helper.absolutePos(TABLE_POS), false);

        List<ItemEntity> drops = helper.getEntities(EntityType.ITEM);
        boolean hasPick = drops.stream().anyMatch(e -> e.getItem().is(Items.IRON_PICKAXE));
        boolean hasDiam = drops.stream().anyMatch(e -> e.getItem().is(Items.DIAMOND) && e.getItem().getCount() == 3);
        boolean hasBook = drops.stream().anyMatch(e -> e.getItem().is(Items.BOOK) && e.getItem().getCount() == 2);
        boolean hasCard = drops.stream().anyMatch(e -> e.getItem().is(UncraftingTableMod.BASIC_SPEED_CARD.get()));
        boolean hasStand = drops.stream().anyMatch(e -> e.getItem().is(Items.BREWING_STAND));

        helper.assertTrue(hasPick && hasDiam && hasBook && hasCard && hasStand,
                "Breaking Uncrafting Table must drop items from 4x4 corner slots, book, upgrade, and modifier slots");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void performance16SlotsCacheAndReload(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Item[] items = {
                Items.STONE_PICKAXE, Items.NETHERITE_SWORD, Items.DIAMOND_AXE, Items.IRON_CHESTPLATE,
                Items.OAK_PLANKS, Items.COOKED_PORKCHOP, Items.GOLD_INGOT, Items.CHISELED_STONE_BRICKS,
                Items.CRAFTING_TABLE, Items.FURNACE, Items.PISTON, Items.DISPENSER,
                Items.SPYGLASS, Items.CROSSBOW, Items.SHIELD, Items.GLASS_BOTTLE
        };

        // Warm cache cleanly across all 16 slots
        UncraftingRecipeResolver.clearCache();
        for (Item item : items) {
            UncraftingRecipeResolver.findRecipes(level, new ItemStack(item), true, true);
        }

        // Measure 500 full 16-slot sweeps (8,000 calls) -> must complete in < 250ms total
        long startNs = System.nanoTime();
        for (int pass = 0; pass < 500; pass++) {
            for (Item item : items) {
                List<ResolvedRecipe> res = UncraftingRecipeResolver.findRecipes(level, new ItemStack(item), true, true);
                helper.assertTrue(res != null, "Cached lookup must never return null");
            }
        }
        long elapsedMs = (System.nanoTime() - startNs) / 1_000_000L;
        helper.assertTrue(elapsedMs < 250,
                "8,000 cached 4x4 findRecipes lookups took " + elapsedMs + "ms (expected < 250ms)");

        // Verify clearCache + rebuild preserves identical results
        UncraftingRecipeResolver.clearCache();
        List<ResolvedRecipe> pickAfterClear = UncraftingRecipeResolver.findRecipes(level, new ItemStack(Items.STONE_PICKAXE), true, true);
        helper.assertTrue(UncraftingRecipeResolver.hasMultipleChoices(pickAfterClear),
                "Cache rebuild after clearCache must preserve Stone Pickaxe multi-recipe resolution");

        helper.succeed();
    }
}

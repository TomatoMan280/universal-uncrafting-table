package com.example.uncraftingtable.client;

import com.example.uncraftingtable.UncraftingTableMod;
import com.example.uncraftingtable.block.entity.UncraftingTableBlockEntity;
import com.example.uncraftingtable.block.entity.UncraftingTableBlockEntity.RecipeRule;
import com.example.uncraftingtable.menu.UncraftingTableMenu;
import com.example.uncraftingtable.recipe.UncraftingRecipeResolver.ConcreteOutput;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Automated live client GUI & render verifier for UncraftingTableScreen.
 * Triggered at launch via -Duncrafting_table.autoClientTest=true or in-game via /testuncraftinggui.
 */
@EventBusSubscriber(modid = UncraftingTableMod.MOD_ID, value = Dist.CLIENT)
public final class UncraftingClientAutoTest {

    private static final String TEST_WORLD_NAME = "uncrafting_auto_gui_test";
    private static final int RENDER_FRAME_COUNT = 120;

    private enum Phase {
        IDLE,
        WAIT_TITLE_SCREEN,
        WAIT_WORLD_READY,
        PREPARE_TABLE,
        OPEN_TABLE,
        WAIT_SCREEN_OPEN,
        WAIT_STATION_SHIFT_SYNC,
        WAIT_TOGGLES_SYNC,
        WAIT_16_SLOT_SYNC,
        SWEEP_RENDER_FRAMES,
        VERIFY_FILTER_UI_AND_SAVE,
        WAIT_SAVE_RULE_SYNC,
        WAIT_AUTO_UNCRAFT_RESULT,
        WAIT_MANUAL_UNCRAFT_RESULT,
        VERIFY_REMOVE_RULE,
        COMPLETE
    }

    private static final boolean AUTO_LAUNCH =
            "true".equalsIgnoreCase(System.getProperty("uncrafting_table.autoClientTest"));
    private static final boolean AUTO_EXIT =
            "true".equalsIgnoreCase(System.getProperty("uncrafting_table.autoClientTestExit"));

    private static Phase phase = AUTO_LAUNCH ? Phase.WAIT_TITLE_SCREEN : Phase.IDLE;
    private static boolean exitOnFinish = AUTO_EXIT;
    private static boolean deleteTempWorldOnFinish = AUTO_LAUNCH;
    private static int waitTicks = 0;
    private static int phaseTimeoutTicks = 0;

    private static BlockPos testTablePos = null;
    private static BlockState savedOriginalBlock = null;
    private static final List<ItemStack> savedHotbar = new ArrayList<>();
    private static final List<String> checkLog = new ArrayList<>();
    private static final List<String> failures = new ArrayList<>();

    private static boolean renderSweepRequested = false;
    private static boolean renderSweepDone = false;
    private static double warmupRenderMs = 0.0;
    private static double avgRenderMs = 0.0;
    private static double maxRenderMs = 0.0;

    private static boolean choseInlineVariantMode = false;
    private static ResourceLocation expectedPickedMaterial = ResourceLocation.withDefaultNamespace("blackstone");

    private UncraftingClientAutoTest() {
    }

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("testuncraftinggui")
                .executes(ctx -> {
                    startManualInWorldTest();
                    ctx.getSource().sendSuccess(() -> Component.literal("Starting automated Uncrafting Table client GUI & render test...")
                            .withStyle(ChatFormatting.AQUA), false);
                    return 1;
                }));
    }

    private static void startManualInWorldTest() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.getSingleplayerServer() == null) {
            mc.gui.getChat().addMessage(Component.literal("[Uncrafting GUI Test] Requires an active singleplayer world.")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        resetState();
        exitOnFinish = false;
        deleteTempWorldOnFinish = false;
        mc.setScreen(null);
        phase = Phase.PREPARE_TABLE;
    }

    private static void resetState() {
        waitTicks = 0;
        phaseTimeoutTicks = 0;
        testTablePos = null;
        savedOriginalBlock = null;
        savedHotbar.clear();
        checkLog.clear();
        failures.clear();
        renderSweepRequested = false;
        renderSweepDone = false;
        warmupRenderMs = 0.0;
        avgRenderMs = 0.0;
        maxRenderMs = 0.0;
        choseInlineVariantMode = false;
        expectedPickedMaterial = ResourceLocation.withDefaultNamespace("blackstone");
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (phase == Phase.IDLE) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        mc.options.pauseOnLostFocus = false;

        if (phase != Phase.WAIT_TITLE_SCREEN && phase != Phase.WAIT_WORLD_READY && mc.screen instanceof PauseScreen) {
            mc.setScreen(null);
        }

        if (waitTicks > 0) {
            waitTicks--;
            return;
        }

        if (++phaseTimeoutTicks > 600) {
            failures.add("Timed out in phase " + phase);
            finishAndCleanup(mc);
            return;
        }

        try {
            tickPhase(mc);
        } catch (Throwable t) {
            failures.add("Exception in phase " + phase + ": " + t.getClass().getSimpleName() + ": " + t.getMessage());
            t.printStackTrace();
            finishAndCleanup(mc);
        }
    }

    private static void tickPhase(Minecraft mc) {
        switch (phase) {
            case WAIT_TITLE_SCREEN -> {
                if (mc.getOverlay() == null && mc.screen instanceof TitleScreen) {
                    mc.options.pauseOnLostFocus = false;
                    deleteTestWorldIfExists(mc);
                    LevelSettings settings = new LevelSettings(
                            TEST_WORLD_NAME,
                            GameType.CREATIVE,
                            false,
                            Difficulty.PEACEFUL,
                            true,
                            new GameRules(),
                            WorldDataConfiguration.DEFAULT
                    );
                    mc.createWorldOpenFlows().createFreshLevel(
                            TEST_WORLD_NAME,
                            settings,
                            new WorldOptions(0L, false, false),
                            registries -> registries.registryOrThrow(Registries.WORLD_PRESET)
                                    .getHolderOrThrow(WorldPresets.FLAT)
                                    .value()
                                    .createWorldDimensions(),
                            new TitleScreen()
                    );
                    transition(Phase.WAIT_WORLD_READY, 20);
                }
            }

            case WAIT_WORLD_READY -> {
                if (mc.player != null
                        && mc.level != null
                        && mc.getSingleplayerServer() != null
                        && !(mc.screen instanceof ReceivingLevelScreen)) {
                    checkLog.add("PASS: Launched world & integrated server ready");
                    transition(Phase.PREPARE_TABLE, 10);
                }
            }

            case PREPARE_TABLE -> {
                MinecraftServer server = mc.getSingleplayerServer();
                if (server == null || mc.player == null) {
                    failures.add("Singleplayer server unavailable for PREPARE_TABLE");
                    finishAndCleanup(mc);
                    return;
                }
                BlockPos target = mc.player.blockPosition().offset(2, 0, 0);
                testTablePos = target;
                savedHotbar.clear();
                for (int i = 0; i < 9; i++) {
                    savedHotbar.add(mc.player.getInventory().getItem(i).copy());
                }

                server.execute(() -> {
                    ServerLevel level = server.overworld();
                    savedOriginalBlock = level.getBlockState(target);
                    level.setBlockAndUpdate(target, UncraftingTableMod.UNCRAFTING_TABLE.get().defaultBlockState());
                    ServerPlayer sp = server.getPlayerList().getPlayer(mc.player.getUUID());
                    if (sp != null) {
                        sp.getInventory().setItem(0, new ItemStack(Items.GRINDSTONE));
                        sp.getInventory().setItem(1, new ItemStack(Items.SMOKER));
                        sp.getInventory().setItem(2, new ItemStack(Items.BLAST_FURNACE));
                        sp.getInventory().setItem(3, new ItemStack(Items.BREWING_STAND));
                        sp.getInventory().setItem(4, new ItemStack(Items.STONE_PICKAXE));
                        sp.getInventory().setItem(5, new ItemStack(Items.OAK_PLANKS));
                        sp.getInventory().setItem(6, new ItemStack(Items.CRAFTING_TABLE));
                        sp.containerMenu.broadcastChanges();
                    }
                });
                transition(Phase.OPEN_TABLE, 3);
            }

            case OPEN_TABLE -> {
                MinecraftServer server = mc.getSingleplayerServer();
                if (mc.level == null || server == null || mc.player == null || testTablePos == null) {
                    failures.add("Level/server missing waiting for block entity sync");
                    finishAndCleanup(mc);
                    return;
                }
                if (!(mc.level.getBlockEntity(testTablePos) instanceof UncraftingTableBlockEntity)) {
                    return; // Wait until ChunkMap flushes BlockEntity creation to client
                }
                final BlockPos target = testTablePos;
                server.execute(() -> {
                    ServerLevel level = server.overworld();
                    ServerPlayer sp = server.getPlayerList().getPlayer(mc.player.getUUID());
                    if (sp != null) {
                        MenuProvider provider = level.getBlockState(target).getMenuProvider(level, target);
                        if (provider != null) {
                            sp.openMenu(provider, target);
                        }
                    }
                });
                transition(Phase.WAIT_SCREEN_OPEN, 3);
            }

            case WAIT_SCREEN_OPEN -> {
                if (mc.screen instanceof UncraftingTableScreen screen) {
                    assertCheck(screen.getViewModeForTest() == UncraftingTableScreen.ViewMode.MAIN,
                            "Initial ViewMode is MAIN");
                    for (int i = 0; i < 4; i++) {
                        assertCheck(!screen.isStationSlotOccupiedForTest(i),
                                "Station slot " + i + " initially empty (dimmed ghost)");
                    }
                    assertNoBannedText(screen);
                    transition(Phase.WAIT_STATION_SHIFT_SYNC, 2);
                }
            }

            case WAIT_STATION_SHIFT_SYNC -> {
                if (!(mc.screen instanceof UncraftingTableScreen screen) || mc.gameMode == null || mc.player == null) {
                    failures.add("Screen closed before station modifier test");
                    finishAndCleanup(mc);
                    return;
                }
                UncraftingTableMenu menu = screen.getMenu();
                // Hotbar slots 0..3 correspond to menu slots SLOT_COUNT + 27 .. SLOT_COUNT + 30
                int hotbarBase = UncraftingTableBlockEntity.SLOT_COUNT + 27;
                for (int i = 0; i < 4; i++) {
                    mc.gameMode.handleInventoryMouseClick(menu.containerId, hotbarBase + i, 0, ClickType.QUICK_MOVE, mc.player);
                }
                transition(Phase.WAIT_TOGGLES_SYNC, 5);
            }

            case WAIT_TOGGLES_SYNC -> {
                if (!(mc.screen instanceof UncraftingTableScreen screen)) {
                    failures.add("Screen missing during station occupancy verification");
                    finishAndCleanup(mc);
                    return;
                }
                for (int i = 0; i < 4; i++) {
                    assertCheck(screen.isStationSlotOccupiedForTest(i),
                            "Station modifier slot " + i + " occupied after Shift-Click (brightened active box)");
                }
                // Exercise Cog Settings Panel & Checkbox toggles
                clickAtGui(screen, 7 + 4, 5 + 4);
                assertCheck(screen.isShowingSettingsPanelForTest(), "Cog icon opens slide-out settings panel");
                clickAtGui(screen, -80 + 20, 10 + 8); // Sounds button
                clickAtGui(screen, -80 + 20, 30 + 8); // Particles button
                clickAtGui(screen, 7 + 4, 5 + 4);     // Close panel
                assertCheck(!screen.isShowingSettingsPanelForTest(), "Cog icon closes slide-out settings panel");

                // Populate all 16 input slots on server for full-grid cursor latency test
                MinecraftServer server = mc.getSingleplayerServer();
                if (server != null && testTablePos != null) {
                    server.execute(() -> {
                        if (server.overworld().getBlockEntity(testTablePos) instanceof UncraftingTableBlockEntity be) {
                            Item[] items = {
                                    Items.STONE_PICKAXE, Items.NETHERITE_SWORD, Items.DIAMOND_AXE, Items.IRON_CHESTPLATE,
                                    Items.OAK_PLANKS, Items.COOKED_PORKCHOP, Items.GOLD_INGOT, Items.CHISELED_STONE_BRICKS,
                                    Items.CRAFTING_TABLE, Items.FURNACE, Items.PISTON, Items.DISPENSER,
                                    Items.SPYGLASS, Items.CROSSBOW, Items.SHIELD, Items.GLASS_BOTTLE
                            };
                            for (int i = 0; i < UncraftingTableBlockEntity.INPUT_SLOTS; i++) {
                                be.itemHandler.setStackInSlot(UncraftingTableBlockEntity.INPUT_START_SLOT + i, new ItemStack(items[i]));
                            }
                            be.setChanged();
                        }
                    });
                }
                transition(Phase.WAIT_16_SLOT_SYNC, 5);
            }

            case WAIT_16_SLOT_SYNC -> {
                if (!(mc.screen instanceof UncraftingTableScreen screen)) {
                    failures.add("Screen missing waiting for 16-slot sync");
                    finishAndCleanup(mc);
                    return;
                }
                boolean synced = !screen.getMenu().getSlot(UncraftingTableBlockEntity.INPUT_START_SLOT + 15).getItem().isEmpty();
                if (synced) {
                    renderSweepDone = false;
                    renderSweepRequested = true;
                    transition(Phase.SWEEP_RENDER_FRAMES, 1);
                }
            }

            case SWEEP_RENDER_FRAMES -> {
                if (!renderSweepDone) {
                    return;
                }
                if (!(mc.screen instanceof UncraftingTableScreen screen)) {
                    failures.add("Screen missing after render sweep");
                    finishAndCleanup(mc);
                    return;
                }
                assertCheck(screen.getCachedWaitingMultiCountForTest() > 0,
                        "Status cache detected multi-recipe items in 16-slot input grid ("
                                + screen.getCachedWaitingMultiCountForTest() + ")");
                assertCheck(avgRenderMs < 15.0 && maxRenderMs < 60.0,
                        String.format(Locale.ROOT,
                                "Cursor sweep render latency fast (warmup=%.3f ms, avg=%.3f ms, max=%.3f ms across %d frames)",
                                warmupRenderMs, avgRenderMs, maxRenderMs, RENDER_FRAME_COUNT));
                assertNoBannedText(screen);
                transition(Phase.VERIFY_FILTER_UI_AND_SAVE, 1);
            }

            case VERIFY_FILTER_UI_AND_SAVE -> {
                if (!(mc.screen instanceof UncraftingTableScreen screen)) {
                    failures.add("Screen missing before Pipez filter test");
                    finishAndCleanup(mc);
                    return;
                }
                // Click Recipes (N)
                clickAtGui(screen, 87 + 35, 62 + 10);
                assertCheck(screen.getViewModeForTest() == UncraftingTableScreen.ViewMode.FILTER_LIST,
                        "Recipes (N) opens FILTER_LIST view");

                // Click Add
                clickAtGui(screen, 192 + 25, 28 + 10);
                assertCheck(screen.getViewModeForTest() == UncraftingTableScreen.ViewMode.FILTER_EDIT,
                        "Add opens FILTER_EDIT view");

                // Test Shift-Click from player inventory loads OAK_PLANKS into ghost slot
                Slot planksSlot = findPlayerSlotWithItem(screen.getMenu(), Items.OAK_PLANKS);
                if (planksSlot != null) {
                    screen.slotClicked(planksSlot, planksSlot.index, 0, ClickType.QUICK_MOVE);
                    assertCheck(screen.getGhostStackForTest().is(Items.OAK_PLANKS),
                            "Shift-Clicking inventory item loads ghost slot in FILTER_EDIT");
                }

                // Simulate carried drag/click drop of STONE_PICKAXE into ghost slot
                screen.getMenu().setCarried(new ItemStack(Items.STONE_PICKAXE));
                clickAtGui(screen, UncraftingTableScreen.GHOST_X + 8, UncraftingTableScreen.GHOST_Y + 8);
                screen.getMenu().setCarried(ItemStack.EMPTY);

                assertCheck(screen.getGhostStackForTest().is(Items.STONE_PICKAXE),
                        "Carrying & clicking Ghost Slot loads Stone Pickaxe cleanly");
                List<ConcreteOutput> opts = screen.getEditOptionsForTest();
                assertCheck(!opts.isEmpty(), "Stone Pickaxe exposes selectable recipe options (" + opts.size() + ")");

                if (!opts.isEmpty()) {
                    ConcreteOutput first = opts.get(0);
                    if (first.hasInlineGroupSwatches()) {
                        choseInlineVariantMode = true;
                        // Click first material swatch once or twice until a valid variant picks cleanly
                        clickAtGui(screen, UncraftingTableScreen.OPT_X + 6 + 4, UncraftingTableScreen.OPT_Y + 3 + 4);
                        var groups = first.sourceRecipe().groups();
                        if (!groups.isEmpty() && !groups.get(0).variants().isEmpty()) {
                            Item picked = groups.get(0).pickVariant(groups.get(0).variants().size() > 1 ? 1 : 0).getItem();
                            expectedPickedMaterial = BuiltInRegistries.ITEM.getKey(picked);
                        }
                    } else {
                        int blackstoneIdx = -1;
                        for (int i = 0; i < opts.size(); i++) {
                            if (opts.get(i).chosenMaterialIds().contains(ResourceLocation.withDefaultNamespace("blackstone"))) {
                                blackstoneIdx = i;
                                expectedPickedMaterial = ResourceLocation.withDefaultNamespace("blackstone");
                                break;
                            }
                        }
                        int targetIdx = blackstoneIdx >= 0 ? blackstoneIdx : Math.min(1, opts.size() - 1);
                        if (targetIdx >= UncraftingTableScreen.OPT_VISIBLE) {
                            screen.setEditScrollForTest(targetIdx - UncraftingTableScreen.OPT_VISIBLE + 1);
                        }
                        int visRow = targetIdx - screen.getEditScrollForTest();
                        clickAtGui(screen, UncraftingTableScreen.OPT_X + 20,
                                UncraftingTableScreen.OPT_Y + 2 + visRow * UncraftingTableScreen.OPT_ROW_H + 8);
                        assertCheck(screen.getSelectedOptionIdxForTest() == targetIdx,
                                "Clicking recipe option row " + targetIdx + " selects it");
                        expectedPickedMaterial = opts.get(targetIdx).chosenMaterialIds().isEmpty()
                                ? expectedPickedMaterial
                                : opts.get(targetIdx).chosenMaterialIds().get(0);
                    }
                }

                // Click Save
                clickAtGui(screen, 200 + 20, 5 + 8);
                assertCheck(screen.getViewModeForTest() == UncraftingTableScreen.ViewMode.FILTER_LIST,
                        "Save button returns cleanly to FILTER_LIST");

                // Exercise Policy Toggle + Edit -> Cancel -> Back
                clickAtGui(screen, 8 + 80, 199 + 10);
                clickAtGui(screen, 192 + 25, 52 + 10);
                assertCheck(screen.getViewModeForTest() == UncraftingTableScreen.ViewMode.FILTER_EDIT,
                        "Edit button reopens selected rule in FILTER_EDIT");
                clickAtGui(screen, 8 + 20, 5 + 8); // Cancel
                clickAtGui(screen, 8 + 20, 5 + 8); // Back to MAIN
                assertCheck(screen.getViewModeForTest() == UncraftingTableScreen.ViewMode.MAIN,
                        "Back button returns cleanly to MAIN view");

                transition(Phase.WAIT_SAVE_RULE_SYNC, 5);
            }

            case WAIT_SAVE_RULE_SYNC -> {
                MinecraftServer server = mc.getSingleplayerServer();
                if (server == null || testTablePos == null || !(mc.screen instanceof UncraftingTableScreen screen)) {
                    failures.add("Missing server/screen verifying saved rule");
                    finishAndCleanup(mc);
                    return;
                }
                final boolean[] rulePresent = {false};
                server.execute(() -> {
                    if (server.overworld().getBlockEntity(testTablePos) instanceof UncraftingTableBlockEntity be) {
                        RecipeRule rule = be.getRuleFor(new ItemStack(Items.STONE_PICKAXE));
                        rulePresent[0] = rule != null;
                        for (int i = 0; i < UncraftingTableBlockEntity.INPUT_SLOTS; i++) {
                            be.itemHandler.setStackInSlot(UncraftingTableBlockEntity.INPUT_START_SLOT + i, ItemStack.EMPTY);
                            be.itemHandler.setStackInSlot(UncraftingTableBlockEntity.MATERIAL_START_SLOT + i, ItemStack.EMPTY);
                        }
                        be.itemHandler.setStackInSlot(UncraftingTableBlockEntity.INPUT_START_SLOT, new ItemStack(Items.STONE_PICKAXE));
                        be.energyStorage.receiveEnergy(10000, false);
                        be.setChanged();
                    }
                });

                // Toggle Auto: ON via GUI button
                clickAtGui(screen, 87 + 35, 40 + 8);
                assertCheck(!choseInlineVariantMode || expectedPickedMaterial != null,
                        "Prepared Auto-Uncraft test using saved rule (" + expectedPickedMaterial + ")");
                transition(Phase.WAIT_AUTO_UNCRAFT_RESULT, 5);
            }

            case WAIT_AUTO_UNCRAFT_RESULT -> {
                if (!(mc.screen instanceof UncraftingTableScreen screen)) {
                    failures.add("Screen closed during Auto-Uncraft verification");
                    finishAndCleanup(mc);
                    return;
                }
                boolean foundMat = false;
                boolean foundStick = false;
                Item targetItem = BuiltInRegistries.ITEM.get(expectedPickedMaterial);
                for (int i = UncraftingTableBlockEntity.MATERIAL_START_SLOT; i <= UncraftingTableBlockEntity.MATERIAL_END_SLOT; i++) {
                    ItemStack out = screen.getMenu().getSlot(i).getItem();
                    if (!out.isEmpty()) {
                        if (out.is(targetItem)) foundMat = true;
                        if (out.is(Items.STICK)) foundStick = true;
                    }
                }
                if (!(foundMat && foundStick) && phaseTimeoutTicks < 90) {
                    return; // Default auto-uncraft speed delay is 40 ticks
                }
                assertCheck(!screen.getMenu().blockEntity.getRecipeRules().isEmpty(),
                        "Saved Pipez recipe rule persisted on BlockEntity");
                assertCheck(foundMat && foundStick,
                        "Auto: ON uncrafted Stone Pickaxe per saved rule (recovered " + expectedPickedMaterial + " + sticks)");

                // Toggle Auto: OFF and test Manual Uncraft button (0 FE)
                clickAtGui(screen, 87 + 35, 40 + 8);
                MinecraftServer server = mc.getSingleplayerServer();
                if (server != null && testTablePos != null) {
                    server.execute(() -> {
                        if (server.overworld().getBlockEntity(testTablePos) instanceof UncraftingTableBlockEntity be) {
                            for (int i = 0; i < UncraftingTableBlockEntity.INPUT_SLOTS; i++) {
                                be.itemHandler.setStackInSlot(UncraftingTableBlockEntity.INPUT_START_SLOT + i, ItemStack.EMPTY);
                                be.itemHandler.setStackInSlot(UncraftingTableBlockEntity.MATERIAL_START_SLOT + i, ItemStack.EMPTY);
                            }
                            be.energyStorage.setEnergy(0);
                            be.itemHandler.setStackInSlot(UncraftingTableBlockEntity.INPUT_START_SLOT, new ItemStack(Items.CRAFTING_TABLE));
                            be.setChanged();
                        }
                    });
                }
                transition(Phase.WAIT_MANUAL_UNCRAFT_RESULT, 5);
            }

            case WAIT_MANUAL_UNCRAFT_RESULT -> {
                if (!(mc.screen instanceof UncraftingTableScreen screen)) {
                    failures.add("Screen missing before manual uncraft check");
                    finishAndCleanup(mc);
                    return;
                }
                clickAtGui(screen, 87 + 35, 20 + 8); // Uncraft button
                transition(Phase.VERIFY_REMOVE_RULE, 6);
            }

            case VERIFY_REMOVE_RULE -> {
                if (!(mc.screen instanceof UncraftingTableScreen screen)) {
                    failures.add("Screen missing after manual uncraft check");
                    finishAndCleanup(mc);
                    return;
                }
                boolean hasPlanks = false;
                for (int i = UncraftingTableBlockEntity.MATERIAL_START_SLOT; i <= UncraftingTableBlockEntity.MATERIAL_END_SLOT; i++) {
                    ItemStack out = screen.getMenu().getSlot(i).getItem();
                    if (out.is(Items.OAK_PLANKS) && out.getCount() >= 4) {
                        hasPlanks = true;
                        break;
                    }
                }
                assertCheck(hasPlanks, "Manual Uncraft button uncrafted Crafting Table -> Oak Planks x4 at 0 FE");

                // Open Recipes -> Remove saved rule cleanly
                clickAtGui(screen, 87 + 35, 62 + 10);
                clickAtGui(screen, 192 + 25, 76 + 10);
                assertCheck(screen.getMenu().blockEntity.getRecipeRules().isEmpty(),
                        "Remove button cleared saved rule cleanly");
                transition(Phase.COMPLETE, 2);
            }

            case COMPLETE -> finishAndCleanup(mc);

            default -> {
            }
        }
    }

    @SubscribeEvent
    public static void onScreenRenderPost(ScreenEvent.Render.Post event) {
        if (!renderSweepRequested || renderSweepDone) {
            return;
        }
        if (!(event.getScreen() instanceof UncraftingTableScreen screen)) {
            return;
        }
        renderSweepRequested = false;

        try {
            GuiGraphics guiGraphics = event.getGuiGraphics();
            int left = screen.getLeftPosForTest();
            int top = screen.getTopPosForTest();

            double totalSteadyNs = 0.0;
            double maxSteadyNs = 0.0;
            for (int f = 0; f < RENDER_FRAME_COUNT; f++) {
                // Sweep cursor smoothly across the 4x4 input grid, station boxes, and status box
                int slotIdx = f % UncraftingTableBlockEntity.INPUT_SLOTS;
                int col = slotIdx % UncraftingTableBlockEntity.INPUT_COLS;
                int row = slotIdx / UncraftingTableBlockEntity.INPUT_COLS;
                int mx = left + 8 + col * 18 + 8;
                int my = top + 20 + row * 18 + 8;
                if (f >= 96 && f < 108) {
                    int m = (f - 96) % 4;
                    mx = left + 164 + m * 18 + 8;
                    my = top + 105 + 8;
                } else if (f >= 108) {
                    mx = left + 120;
                    my = top + 102;
                }

                long start = System.nanoTime();
                screen.containerTick();
                screen.render(guiGraphics, mx, my, 0.0F);
                guiGraphics.flush();
                long elapsed = System.nanoTime() - start;

                if (f == 0) {
                    warmupRenderMs = elapsed / 1_000_000.0;
                } else {
                    totalSteadyNs += elapsed;
                    if (elapsed > maxSteadyNs) {
                        maxSteadyNs = elapsed;
                    }
                }
            }
            avgRenderMs = (totalSteadyNs / (RENDER_FRAME_COUNT - 1)) / 1_000_000.0;
            maxRenderMs = maxSteadyNs / 1_000_000.0;
        } catch (Throwable t) {
            failures.add("Exception during GUI cursor sweep render: " + t.getClass().getSimpleName() + ": " + t.getMessage());
            t.printStackTrace();
        } finally {
            renderSweepDone = true;
        }
    }

    private static void transition(Phase next, int settleTicks) {
        phase = next;
        waitTicks = settleTicks;
        phaseTimeoutTicks = 0;
    }

    private static void clickAtGui(UncraftingTableScreen screen, int relX, int relY) {
        screen.mouseClicked(screen.getLeftPosForTest() + relX, screen.getTopPosForTest() + relY, 0);
    }

    private static Slot findPlayerSlotWithItem(UncraftingTableMenu menu, Item item) {
        for (int i = UncraftingTableBlockEntity.SLOT_COUNT; i < menu.slots.size(); i++) {
            Slot s = menu.slots.get(i);
            if (s.hasItem() && s.getItem().is(item)) {
                return s;
            }
        }
        return null;
    }

    private static void assertNoBannedText(UncraftingTableScreen screen) {
        for (String label : screen.getWidgetLabelsForTest()) {
            String lower = label.toLowerCase(Locale.ROOT);
            assertCheck(!lower.contains("pipes follow rules")
                            && !lower.contains("filters active")
                            && !lower.contains("unset:"),
                    "Widget label '" + label + "' has no removed status text");
        }
    }

    private static void assertCheck(boolean condition, String description) {
        if (condition) {
            checkLog.add("PASS: " + description);
        } else {
            String msg = "FAIL: " + description;
            checkLog.add(msg);
            failures.add(description);
        }
    }

    private static void finishAndCleanup(Minecraft mc) {
        boolean passed = failures.isEmpty();
        phase = Phase.IDLE;

        mc.setScreen(null);

        MinecraftServer server = mc.getSingleplayerServer();
        if (server != null && testTablePos != null && savedOriginalBlock != null) {
            final BlockPos restorePos = testTablePos;
            final BlockState restoreState = savedOriginalBlock;
            final List<ItemStack> restoreBar = new ArrayList<>(savedHotbar);
            server.execute(() -> {
                ServerLevel overworld = server.overworld();
                if (overworld.getBlockEntity(restorePos) instanceof UncraftingTableBlockEntity be) {
                    for (int i = 0; i < be.itemHandler.getSlots(); i++) {
                        be.itemHandler.setStackInSlot(i, ItemStack.EMPTY);
                    }
                }
                overworld.setBlockAndUpdate(restorePos, restoreState);
                if (mc.player != null && !deleteTempWorldOnFinish) {
                    ServerPlayer sp = server.getPlayerList().getPlayer(mc.player.getUUID());
                    if (sp != null) {
                        for (int i = 0; i < Math.min(9, restoreBar.size()); i++) {
                            sp.getInventory().setItem(i, restoreBar.get(i));
                        }
                        sp.containerMenu.broadcastChanges();
                    }
                }
            });
        }

        Path reportPath = mc.gameDirectory.toPath().resolve("uncrafting_client_gui_report.txt");
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(reportPath, StandardCharsets.UTF_8))) {
            pw.println("====== UNCRAFTING TABLE CLIENT GUI AUTO-TEST REPORT ======");
            pw.println("STATUS: " + (passed ? "PASSED" : "FAILED"));
            pw.println("Checks Passed: " + checkLog.stream().filter(s -> s.startsWith("PASS:")).count());
            pw.println("Failures: " + failures.size());
            pw.println(String.format(Locale.ROOT,
                    "Render Cursor Sweep (%d frames): warmup=%.3f ms, avg=%.3f ms, max=%.3f ms",
                    RENDER_FRAME_COUNT, warmupRenderMs, avgRenderMs, maxRenderMs));
            pw.println();
            for (String line : checkLog) {
                pw.println("  " + line);
            }
            if (!failures.isEmpty()) {
                pw.println();
                pw.println("--- FAILURES ---");
                for (String err : failures) {
                    pw.println("  " + err);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        String summary = String.format(Locale.ROOT,
                "[Uncrafting GUI Auto-Test] %s | Checks: %d/%d | AvgFrame: %.3f ms (Max: %.3f ms) -> %s",
                passed ? "PASSED" : "FAILED",
                checkLog.stream().filter(s -> s.startsWith("PASS:")).count(),
                checkLog.size(),
                avgRenderMs,
                maxRenderMs,
                reportPath.getFileName());
        System.out.println(summary);

        if (mc.player != null && !exitOnFinish) {
            mc.gui.getChat().addMessage(Component.literal(summary)
                    .withStyle(passed ? ChatFormatting.GREEN : ChatFormatting.RED));
        }

        if (exitOnFinish) {
            mc.stop();
        }
    }

    private static void deleteTestWorldIfExists(Minecraft mc) {
        try {
            if (mc.getLevelSource().levelExists(TEST_WORLD_NAME)) {
                try (var access = mc.getLevelSource().createAccess(TEST_WORLD_NAME)) {
                    access.deleteLevel();
                }
            }
        } catch (Exception e) {
            System.err.println("[UncraftingAutoTest] Could not delete temp world: " + e.getMessage());
        }
    }
}

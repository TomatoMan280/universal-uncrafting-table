package com.example.uncraftingtable.command;

import com.example.uncraftingtable.UncraftingTableMod;
import com.example.uncraftingtable.block.entity.UncraftingTableBlockEntity;
import com.example.uncraftingtable.recipe.UncraftingRecipeResolver;
import com.example.uncraftingtable.recipe.UncraftingRecipeResolver.ConcreteOutput;
import com.example.uncraftingtable.recipe.UncraftingRecipeResolver.ResolvedRecipe;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@EventBusSubscriber(modid = UncraftingTableMod.MOD_ID)
public class UncraftingTestCommand {

    public record SweepResult(
            int totalItems,
            long elapsedMs,
            int successCount,
            int multiRecipeCount,
            int blacklistedCount,
            int rejectedIntactCount,
            int skippedCount,
            int errorCount,
            Path reportPath
    ) {
        public String summaryText() {
            return String.format(
                    "Test Complete (%d ms)! Items: %d | Uncrafted: %d | Multi: %d | Blacklisted: %d | RejectedIntact: %d | Errors: %d",
                    elapsedMs, totalItems, successCount, multiRecipeCount, blacklistedCount, rejectedIntactCount, errorCount
            );
        }
    }

    private static volatile String pendingLoginSummary = null;
    private static volatile boolean pendingLoginFailed = false;
    private static volatile boolean pendingAutoExitFailure = false;

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("testuncrafting")
                .requires(source -> source.hasPermission(2))
                .executes(UncraftingTestCommand::execute));
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        pendingLoginSummary = null;
        pendingLoginFailed = false;
        pendingAutoExitFailure = false;

        if (!shouldAutoRunOnWorldLoad(server)) {
            return;
        }

        ServerLevel level = server.overworld();
        if (level == null) {
            return;
        }

        System.out.println("[UncraftingAutoTest] Auto-running /testuncrafting sweep for world '"
                + server.getWorldData().getLevelName() + "'...");
        try {
            SweepResult result = runFullRegistrySweep(server, level);
            pendingLoginSummary = "[Uncrafting Auto-Test] " + result.summaryText() + " -> " + result.reportPath().getFileName();
            pendingLoginFailed = result.errorCount() > 0;
            if ("true".equalsIgnoreCase(System.getProperty("uncrafting_table.exitAfterAutoSweep"))) {
                pendingAutoExitFailure = result.errorCount() > 0;
                System.out.println("[UncraftingAutoTest] Halting server after automated sweep (Errors=" + result.errorCount() + ").");
                server.halt(false);
            }
        } catch (Exception e) {
            pendingLoginSummary = "[Uncrafting Auto-Test] FAILED with exception: " + e.getMessage();
            pendingLoginFailed = true;
            e.printStackTrace();
            if ("true".equalsIgnoreCase(System.getProperty("uncrafting_table.exitAfterAutoSweep"))) {
                pendingAutoExitFailure = true;
                server.halt(false);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        String msg = pendingLoginSummary;
        if (msg != null && event.getEntity() instanceof net.minecraft.server.level.ServerPlayer sp
                && sp.hasPermissions(2)) {
            pendingLoginSummary = null;
            ChatFormatting color = pendingLoginFailed ? ChatFormatting.RED : ChatFormatting.GREEN;
            sp.sendSystemMessage(Component.literal(msg).withStyle(color));
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        if ("true".equalsIgnoreCase(System.getProperty("uncrafting_table.exitAfterAutoSweep")) && pendingAutoExitFailure) {
            System.err.println("[UncraftingAutoTest] Exiting with code 1 because Errors > 0.");
            System.exit(1);
        }
    }

    private static boolean shouldAutoRunOnWorldLoad(MinecraftServer server) {
        if (server instanceof GameTestServer) {
            return false;
        }
        String explicit = System.getProperty("uncrafting_table.autoSweepOnWorldLoad");
        if ("false".equalsIgnoreCase(explicit)) {
            return false;
        }
        if ("true".equalsIgnoreCase(explicit)) {
            return true;
        }
        if ("true".equalsIgnoreCase(System.getProperty("uncrafting_table.autoClientTest"))) {
            return false;
        }
        if (Files.exists(server.getServerDirectory().resolve("auto_uncrafting_test.flag"))
                || Files.exists(server.getWorldPath(LevelResource.ROOT).resolve("auto_uncrafting_test.flag"))) {
            return true;
        }
        String levelName = server.getWorldData().getLevelName();
        return levelName != null && levelName.toLowerCase(Locale.ROOT).contains("test");
    }

    private static int execute(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        source.sendSuccess(() -> Component.literal("Starting full uncrafting test across all registered items..."), false);
        try {
            SweepResult result = runFullRegistrySweep(source.getServer(), source.getLevel());
            source.sendSuccess(() -> Component.literal(result.summaryText()), true);
            return result.errorCount() == 0 ? 1 : 0;
        } catch (Exception e) {
            source.sendFailure(Component.literal("Test failed with exception: " + e.getMessage()));
            e.printStackTrace();
            return 0;
        }
    }

    /**
     * Sweep every registered item (plus Iron's Gems 'n Jewelry patterns when installed) through a real
     * UncraftingTableBlockEntity and write a structured verification report to uncrafting_test_report.txt.
     */
    public static SweepResult runFullRegistrySweep(MinecraftServer server, ServerLevel level) throws Exception {
        long startNs = System.nanoTime();
        UncraftingTableBlockEntity entity = new UncraftingTableBlockEntity(
                net.minecraft.core.BlockPos.ZERO,
                UncraftingTableMod.UNCRAFTING_TABLE.get().defaultBlockState()
        );
        entity.setLevel(level);
        entity.muteSounds = true;
        entity.muteParticles = true;

        int totalItems = 0;
        int successCount = 0;
        int multiRecipeCount = 0;
        int blacklistedCount = 0;
        int rejectedNoRecipeCount = 0;
        int skippedNoOutputCount = 0;
        List<String> errors = new ArrayList<>();
        Map<String, Integer> successByNamespace = new LinkedHashMap<>();
        List<String> sampleUncrafted = new ArrayList<>();

        for (Item item : BuiltInRegistries.ITEM) {
            if (item == Items.AIR) continue;
            totalItems++;
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);

            try {
                for (int i = 0; i < entity.itemHandler.getSlots(); i++) {
                    entity.itemHandler.setStackInSlot(i, ItemStack.EMPTY);
                }

                entity.enableGrindstone = false;
                entity.enableSmoker = false;
                entity.enableBlastFurnace = false;
                entity.enableBrewingStand = false;
                entity.waitForUnconfiguredChoice = false;

                entity.itemHandler.setStackInSlot(UncraftingTableBlockEntity.GRINDSTONE_SLOT, new ItemStack(Items.GRINDSTONE));
                entity.itemHandler.setStackInSlot(UncraftingTableBlockEntity.SMOKER_SLOT, new ItemStack(Items.SMOKER));
                entity.itemHandler.setStackInSlot(UncraftingTableBlockEntity.BLAST_FURNACE_SLOT, new ItemStack(Items.BLAST_FURNACE));
                entity.itemHandler.setStackInSlot(UncraftingTableBlockEntity.BREWING_STAND_SLOT, new ItemStack(Items.BREWING_STAND));
                entity.itemHandler.setStackInSlot(UncraftingTableBlockEntity.BOOK_SLOT, new ItemStack(Items.BOOK, 16));

                ItemStack inputStack = new ItemStack(item, Math.max(1, item.getDefaultMaxStackSize()));
                entity.itemHandler.setStackInSlot(UncraftingTableBlockEntity.INPUT_START_SLOT, inputStack);

                if (item == Items.POTION || item == Items.SPLASH_POTION || item == Items.LINGERING_POTION) {
                    entity.itemHandler.setStackInSlot(UncraftingTableBlockEntity.INPUT_START_SLOT + 1, new ItemStack(item, 1));
                    entity.itemHandler.setStackInSlot(UncraftingTableBlockEntity.INPUT_START_SLOT + 2, new ItemStack(item, 1));
                }

                if (inputStack.is(UncraftingTableBlockEntity.BLACKLIST_TAG)) {
                    blacklistedCount++;
                }

                List<ResolvedRecipe> resolved = UncraftingRecipeResolver.findRecipes(
                        level, inputStack, true, true);
                if (!resolved.isEmpty()) {
                    if (UncraftingRecipeResolver.hasMultipleChoices(resolved)) {
                        multiRecipeCount++;
                    }
                    List<ConcreteOutput> expanded = UncraftingRecipeResolver.expandOutputOptions(resolved);
                    if (expanded.isEmpty()) {
                        errors.add(id + ": expandOutputOptions returned empty for non-empty resolved recipes");
                    }
                }

                entity.energyStorage.receiveEnergy(1000000, false);
                boolean success = entity.tryUncraft();

                List<String> outSummary = new ArrayList<>();
                for (int i = UncraftingTableBlockEntity.MATERIAL_START_SLOT; i <= UncraftingTableBlockEntity.MATERIAL_END_SLOT; i++) {
                    ItemStack out = entity.itemHandler.getStackInSlot(i);
                    if (!out.isEmpty()) {
                        outSummary.add(BuiltInRegistries.ITEM.getKey(out.getItem()) + "x" + out.getCount());
                        if (!inputStack.is(UncraftingTableBlockEntity.BLACKLIST_TAG) && out.is(item)) {
                            if (success) {
                                errors.add(id + ": self-loop detected in output (" + outSummary + ")");
                            }
                        }
                    }
                }

                if (success && !outSummary.isEmpty()) {
                    successCount++;
                    successByNamespace.merge(id.getNamespace(), 1, Integer::sum);
                    if (sampleUncrafted.size() < 40 && !"minecraft".equals(id.getNamespace())) {
                        sampleUncrafted.add(id + " -> " + String.join(", ", outSummary));
                    }
                } else if (!success && !outSummary.isEmpty()) {
                    rejectedNoRecipeCount++;
                } else {
                    skippedNoOutputCount++;
                }
            } catch (Throwable t) {
                errors.add(id + ": EXCEPTION " + t.getClass().getSimpleName() + ": " + t.getMessage());
            }
        }

        if (net.neoforged.fml.ModList.get().isLoaded("irons_jewelry")) {
            testIronsJewelryPatterns(level, entity, errors, sampleUncrafted, successByNamespace);
        }

        long elapsedMs = (System.nanoTime() - startNs) / 1_000_000L;

        Path reportPath = server.getServerDirectory().resolve("uncrafting_test_report.txt");
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(reportPath, StandardCharsets.UTF_8))) {
            pw.println("====== UNCRAFTING TABLE FULL REGISTRY TEST REPORT ======");
            pw.println("Total registered items tested: " + totalItems);
            pw.println("Elapsed ms: " + elapsedMs);
            pw.println("Successfully uncrafted: " + successCount);
            pw.println("Multi-recipe items detected: " + multiRecipeCount);
            pw.println("Blacklisted items protected: " + blacklistedCount);
            pw.println("No-recipe items safely moved to output: " + rejectedNoRecipeCount);
            pw.println("Skipped (insufficient input/output/room): " + skippedNoOutputCount);
            pw.println("Errors / self-loops: " + errors.size());
            pw.println();
            pw.println("--- Successes by Mod Namespace (" + successByNamespace.size() + " mods) ---");
            successByNamespace.entrySet().stream()
                    .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                    .forEach(e -> pw.println("  " + e.getKey() + ": " + e.getValue()));
            if (!sampleUncrafted.isEmpty()) {
                pw.println();
                pw.println("--- Sample Modded Uncraftings ---");
                sampleUncrafted.forEach(s -> pw.println("  " + s));
            }
            if (!errors.isEmpty()) {
                pw.println();
                pw.println("--- ERRORS ---");
                errors.forEach(e -> pw.println("  " + e));
            }
        }

        System.out.println("====== UNCRAFTING TEST REPORT ======");
        System.out.println("Total items tested: " + totalItems + " in " + elapsedMs + " ms");
        System.out.println("Successful uncrafting: " + successCount + " across " + successByNamespace.size() + " namespaces");
        System.out.println("Multi-recipe items: " + multiRecipeCount + " | Blacklisted: " + blacklistedCount + " | Rejected/Intact: " + rejectedNoRecipeCount + " | Skipped: " + skippedNoOutputCount);
        System.out.println("Errors: " + errors.size());
        if (!errors.isEmpty()) {
            errors.stream().limit(25).forEach(System.err::println);
        }

        return new SweepResult(
                totalItems,
                elapsedMs,
                successCount,
                multiRecipeCount,
                blacklistedCount,
                rejectedNoRecipeCount,
                skippedNoOutputCount,
                errors.size(),
                reportPath
        );
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void testIronsJewelryPatterns(
            ServerLevel level,
            UncraftingTableBlockEntity entity,
            List<String> errors,
            List<String> sampleUncrafted,
            Map<String, Integer> successByNamespace
    ) {
        try {
            Class<?> regCls = Class.forName("io.redspace.ironsjewelry.registry.IronsJewelryRegistries");
            Class<?> jDataCls = Class.forName("io.redspace.ironsjewelry.core.data.JewelryData");
            Class<?> jTypeCls = Class.forName("io.redspace.ironsjewelry.core.data.JewelryType");

            net.minecraft.core.Registry<?> patterns = (net.minecraft.core.Registry<?>) regCls
                    .getMethod("patternRegistry", net.minecraft.core.RegistryAccess.class)
                    .invoke(null, level.registryAccess());
            net.minecraft.core.Registry<?> materials = (net.minecraft.core.Registry<?>) regCls
                    .getMethod("materialRegistry", net.minecraft.core.RegistryAccess.class)
                    .invoke(null, level.registryAccess());

            java.lang.reflect.Method setJewelry = jDataCls.getMethod("set", ItemStack.class, jDataCls);
            java.lang.reflect.Constructor<?> jCtor = jDataCls.getConstructor(net.minecraft.core.Holder.class, Map.class);
            java.lang.reflect.Method partTemplateM = Class.forName("io.redspace.ironsjewelry.core.data.PatternDefinition").getMethod("partTemplate");
            java.lang.reflect.Method jewelryTypeM = Class.forName("io.redspace.ironsjewelry.core.data.PatternDefinition").getMethod("jewelryType");
            java.lang.reflect.Method typeItemM = jTypeCls.getMethod("item");
            java.lang.reflect.Method partM = Class.forName("io.redspace.ironsjewelry.core.data.PartIngredient").getMethod("part");
            java.lang.reflect.Method canUseMaterialM = Class.forName("io.redspace.ironsjewelry.core.data.PartDefinition")
                    .getMethod("canUseMaterial", net.minecraft.core.Holder.class);
            java.lang.reflect.Method isIngEmptyM = Class.forName("io.redspace.ironsjewelry.core.data.MaterialDefinition").getMethod("isIngredientEmpty");

            List<net.minecraft.core.Holder> allMats = new ArrayList<>();
            materials.holders().forEach(allMats::add);

            int tested = 0;
            int passed = 0;
            for (var pHolder : patterns.holders().toList()) {
                Object pat = pHolder.value();
                Object jType = jewelryTypeM.invoke(pat);
                net.minecraft.core.Holder<Item> itemHolder = (net.minecraft.core.Holder<Item>) typeItemM.invoke(jType);
                Item jItem = itemHolder.value();

                Map<net.minecraft.core.Holder, net.minecraft.core.Holder> chosen = new LinkedHashMap<>();
                for (Object pi : (List<?>) partTemplateM.invoke(pat)) {
                    net.minecraft.core.Holder partHolder = (net.minecraft.core.Holder) partM.invoke(pi);
                    Object partDef = partHolder.value();
                    net.minecraft.core.Holder picked = null;
                    for (var cand : allMats) {
                        if (!(boolean) isIngEmptyM.invoke(cand.value()) && (boolean) canUseMaterialM.invoke(partDef, cand)) {
                            picked = cand;
                            break;
                        }
                    }
                    if (picked != null) chosen.put(partHolder, picked);
                }

                ItemStack stack = new ItemStack(jItem);
                Object jData = jCtor.newInstance(pHolder, chosen);
                setJewelry.invoke(null, stack, jData);

                tested++;
                for (int i = 0; i < entity.itemHandler.getSlots(); i++) {
                    entity.itemHandler.setStackInSlot(i, ItemStack.EMPTY);
                }
                entity.itemHandler.setStackInSlot(UncraftingTableBlockEntity.INPUT_START_SLOT, stack);
                if (entity.tryUncraft()) {
                    passed++;
                    successByNamespace.merge("irons_jewelry_crafted", 1, Integer::sum);
                    if (sampleUncrafted.size() < 55) {
                        List<String> outs = new ArrayList<>();
                        for (int i = UncraftingTableBlockEntity.MATERIAL_START_SLOT; i <= UncraftingTableBlockEntity.MATERIAL_END_SLOT; i++) {
                            ItemStack out = entity.itemHandler.getStackInSlot(i);
                            if (!out.isEmpty()) outs.add(BuiltInRegistries.ITEM.getKey(out.getItem()) + "x" + out.getCount());
                        }
                        sampleUncrafted.add("irons_jewelry:" + pHolder.unwrapKey().map(k -> k.location().getPath()).orElse("pattern") + " -> " + String.join(", ", outs));
                    }
                } else {
                    errors.add("irons_jewelry pattern failed to uncraft: " + pHolder.unwrapKey());
                }
            }
            System.out.println("Iron's Gems 'n Jewelry patterns tested: " + tested + ", uncrafted: " + passed);
        } catch (Throwable t) {
            errors.add("irons_jewelry sweep error: " + t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }
}

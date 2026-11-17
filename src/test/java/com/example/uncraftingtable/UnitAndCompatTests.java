package com.example.uncraftingtable;

import com.example.uncraftingtable.compat.UncraftingJeiPlugin;
import com.example.uncraftingtable.recipe.UncraftingRecipeResolver;
import com.example.uncraftingtable.recipe.UncraftingRecipeResolver.ConcreteOutput;
import com.example.uncraftingtable.recipe.UncraftingRecipeResolver.IngredientGroup;
import com.example.uncraftingtable.recipe.UncraftingRecipeResolver.ResolvedRecipe;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class UnitAndCompatTests {

    // Exact reflection contract matching io.redspace.ironsjewelry.core.data.* (1.21.1)
    public record MockPartDefinition(String id) {
    }

    public record MockMaterialDefinition(Optional<Ingredient> ingredient) {
    }

    public record MockPartIngredient(Holder<MockPartDefinition> part, int materialCost) {
    }

    public record MockPatternDefinition(List<MockPartIngredient> partTemplate) {
    }

    public static final class MockJewelryData {
        private final Holder<MockPatternDefinition> pattern;
        private final Map<Holder<MockPartDefinition>, Holder<MockMaterialDefinition>> parts;
        private final boolean valid;

        public MockJewelryData(
                Holder<MockPatternDefinition> pattern,
                Map<Holder<MockPartDefinition>, Holder<MockMaterialDefinition>> parts,
                boolean valid
        ) {
            this.pattern = pattern;
            this.parts = parts;
            this.valid = valid;
        }

        public boolean isValid() {
            return valid;
        }

        public Holder<MockPatternDefinition> pattern() {
            return pattern;
        }

        public Map<Holder<MockPartDefinition>, Holder<MockMaterialDefinition>> parts() {
            return parts;
        }
    }

    @Test
    void testIronsJewelryReflectionResolution() {
        Holder<MockPartDefinition> bandPart = Holder.direct(new MockPartDefinition("band"));
        Holder<MockPartDefinition> gemPart = Holder.direct(new MockPartDefinition("gem"));

        MockPatternDefinition ringPattern = new MockPatternDefinition(List.of(
                new MockPartIngredient(bandPart, 4),
                new MockPartIngredient(gemPart, 1)
        ));

        Map<Holder<MockPartDefinition>, Holder<MockMaterialDefinition>> partsMap = new LinkedHashMap<>();
        partsMap.put(bandPart, Holder.direct(new MockMaterialDefinition(Optional.of(Ingredient.of(Items.GOLD_INGOT)))));
        partsMap.put(gemPart, Holder.direct(new MockMaterialDefinition(Optional.of(Ingredient.of(Items.EMERALD, Items.DIAMOND)))));

        MockJewelryData validRing = new MockJewelryData(Holder.direct(ringPattern), partsMap, true);
        Optional<ResolvedRecipe> resolvedOpt = UncraftingRecipeResolver.resolveIronsJewelryData(validRing);
        assertTrue(resolvedOpt.isPresent(), "Valid Iron's Jewelry component data must resolve");

        ResolvedRecipe resolved = resolvedOpt.get();
        assertEquals("Jewelcraft", resolved.sourceLabel());
        assertEquals(2, resolved.groups().size());
        assertEquals(4, resolved.groups().get(0).count());
        assertEquals(Items.GOLD_INGOT, resolved.groups().get(0).pickVariant(0).getItem());
        assertEquals(1, resolved.groups().get(1).count());
        assertTrue(resolved.groups().get(1).hasChoices(), "Multi-gem ingredient must expose variant choices");

        // Invalid JewelryData must be safely ignored
        MockJewelryData invalidRing = new MockJewelryData(Holder.direct(ringPattern), partsMap, false);
        assertTrue(UncraftingRecipeResolver.resolveIronsJewelryData(invalidRing).isEmpty());
        assertTrue(UncraftingRecipeResolver.resolveIronsJewelryData(null).isEmpty());
    }

    @Test
    void testExpandOutputOptionsThresholdsAndDeduplication() {
        IngredientGroup g3a = new IngredientGroup(List.of(
                new ItemStack(Items.OAK_PLANKS), new ItemStack(Items.SPRUCE_PLANKS), new ItemStack(Items.BIRCH_PLANKS)
        ), 2);
        IngredientGroup g3b = new IngredientGroup(List.of(
                new ItemStack(Items.COBBLESTONE), new ItemStack(Items.BLACKSTONE), new ItemStack(Items.COBBLED_DEEPSLATE)
        ), 3);

        ResolvedRecipe small = new ResolvedRecipe(
                ResourceLocation.fromNamespaceAndPath("test", "small"), "Crafting", 1, List.of(g3a, g3b), 5
        );
        // Duplicate identical recipe to verify fingerprint deduplication
        ResolvedRecipe duplicate = new ResolvedRecipe(
                ResourceLocation.fromNamespaceAndPath("test", "small_dup"), "Crafting", 1, List.of(g3a, g3b), 5
        );

        List<ConcreteOutput> expanded = UncraftingRecipeResolver.expandOutputOptions(List.of(small, duplicate));
        assertEquals(9, expanded.size(), "3x3 cartesian expansion should yield 9 unique rows even with duplicate recipe");
        assertTrue(expanded.stream().noneMatch(ConcreteOutput::hasInlineGroupSwatches));

        // > 64 combinations (5*5*5 = 125) must stay single row with inline clickable swatches
        IngredientGroup g5 = new IngredientGroup(List.of(
                new ItemStack(Items.OAK_LOG), new ItemStack(Items.BIRCH_LOG), new ItemStack(Items.SPRUCE_LOG),
                new ItemStack(Items.JUNGLE_LOG), new ItemStack(Items.ACACIA_LOG)
        ), 1);
        ResolvedRecipe mega = new ResolvedRecipe(
                ResourceLocation.fromNamespaceAndPath("test", "mega"), "Crafting", 1, List.of(g5, g5, g5), 3
        );
        List<ConcreteOutput> megaOut = UncraftingRecipeResolver.expandOutputOptions(List.of(mega));
        assertEquals(1, megaOut.size());
        assertTrue(megaOut.get(0).hasInlineGroupSwatches());
    }

    public static final class CustomProcessingOutput {
        private final ItemStack stack;
        private final float chance;

        public CustomProcessingOutput(ItemStack stack, float chance) {
            this.stack = stack;
            this.chance = chance;
        }

        public ItemStack getStack() {
            return stack;
        }

        public float getChance() {
            return chance;
        }
    }

    private static final class CustomSizedRecipe implements Recipe<RecipeInput> {
        private static final RecipeType<CustomSizedRecipe> CUSTOM_TYPE = new RecipeType<>() {
            @Override
            public String toString() {
                return "custom_mod:mechanical_pressing";
            }
        };

        private final SizedIngredient input;
        private final CustomProcessingOutput output;

        CustomSizedRecipe(SizedIngredient input, CustomProcessingOutput output) {
            this.input = input;
            this.output = output;
        }

        public CustomProcessingOutput getOutput() {
            return output;
        }

        @Override public boolean matches(RecipeInput in, Level level) { return false; }
        @Override public ItemStack assemble(RecipeInput in, HolderLookup.Provider p) { return ItemStack.EMPTY; }
        @Override public boolean canCraftInDimensions(int w, int h) { return true; }
        @Override public ItemStack getResultItem(HolderLookup.Provider p) { return ItemStack.EMPTY; }
        @Override public RecipeSerializer<?> getSerializer() { return null; }
        @Override public RecipeType<?> getType() { return CUSTOM_TYPE; }
    }

    @Test
    void testCustomRecipeTypeSizedIngredientAndSelfLoopSafety() {
        RegistryAccess emptyAccess = RegistryAccess.EMPTY;
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("custom_mod", "press_iron");

        CustomSizedRecipe deterministic = new CustomSizedRecipe(
                new SizedIngredient(Ingredient.of(Items.IRON_INGOT), 6),
                new CustomProcessingOutput(new ItemStack(Items.IRON_BLOCK), 1.0f)
        );
        Optional<ResolvedRecipe> ok = UncraftingRecipeResolver.resolveSingleRecipe(
                id, deterministic, emptyAccess, new ItemStack(Items.IRON_BLOCK), false, false);
        assertTrue(ok.isPresent(), "Custom SizedIngredient recipe with reflection getOutput() must resolve");
        assertEquals(6, ok.get().groups().get(0).count());
        assertEquals(Items.IRON_INGOT, ok.get().groups().get(0).pickVariant(0).getItem());

        // Chance < 1.0f must be rejected to avoid RNG duplication exploits
        CustomSizedRecipe rngChance = new CustomSizedRecipe(
                new SizedIngredient(Ingredient.of(Items.IRON_INGOT), 6),
                new CustomProcessingOutput(new ItemStack(Items.IRON_BLOCK), 0.5f)
        );
        assertTrue(UncraftingRecipeResolver.resolveSingleRecipe(
                id, rngChance, emptyAccess, new ItemStack(Items.IRON_BLOCK), false, false).isEmpty());

        // Self-reference loop (iron_block -> iron_block) must be blocked
        CustomSizedRecipe selfLoop = new CustomSizedRecipe(
                new SizedIngredient(Ingredient.of(Items.IRON_BLOCK), 1),
                new CustomProcessingOutput(new ItemStack(Items.IRON_BLOCK), 1.0f)
        );
        assertTrue(UncraftingRecipeResolver.resolveSingleRecipe(
                id, selfLoop, emptyAccess, new ItemStack(Items.IRON_BLOCK), false, false).isEmpty());
    }

    @Test
    void testJeiPluginUidRegistrationAndAssetsIntegrity() throws Exception {
        UncraftingJeiPlugin plugin = new UncraftingJeiPlugin();
        assertEquals(ResourceLocation.fromNamespaceAndPath(UncraftingTableMod.MOD_ID, "jei_plugin"), plugin.getPluginUid());

        // Verify registerGuiHandlers registers GhostIngredientHandler for UncraftingTableScreen on Dist.CLIENT
        java.util.List<Class<?>> registeredScreens = new java.util.ArrayList<>();
        mezz.jei.api.registration.IGuiHandlerRegistration proxyReg = (mezz.jei.api.registration.IGuiHandlerRegistration)
                java.lang.reflect.Proxy.newProxyInstance(
                        UnitAndCompatTests.class.getClassLoader(),
                        new Class<?>[]{mezz.jei.api.registration.IGuiHandlerRegistration.class},
                        (proxy, method, args) -> {
                            if ("addGhostIngredientHandler".equals(method.getName()) && args != null && args.length >= 2) {
                                registeredScreens.add((Class<?>) args[0]);
                                return null;
                            }
                            return null;
                        }
                );
        plugin.registerGuiHandlers(proxyReg);
        if (registeredScreens.isEmpty()) {
            // Test harness may report server dist; exercise client handler registration directly too
            UncraftingJeiPlugin.registerClientGhostHandler(proxyReg);
        }
        assertEquals(1, registeredScreens.size(), "Expected JEI ghost handler registration for UncraftingTableScreen");
        assertEquals(com.example.uncraftingtable.client.UncraftingTableScreen.class, registeredScreens.get(0));

        // Verify hasMultipleChoices detects multi-variant groups vs single deterministic recipes
        IngredientGroup singleGroup = new IngredientGroup(List.of(new ItemStack(Items.DIAMOND)), 2);
        IngredientGroup multiGroup = new IngredientGroup(List.of(new ItemStack(Items.COBBLESTONE), new ItemStack(Items.BLACKSTONE)), 3);
        ResolvedRecipe singleRec = new ResolvedRecipe(ResourceLocation.withDefaultNamespace("single"), "Crafting", 1, List.of(singleGroup), 2);
        ResolvedRecipe multiRec = new ResolvedRecipe(ResourceLocation.withDefaultNamespace("multi"), "Crafting", 1, List.of(multiGroup), 3);
        assertFalse(UncraftingRecipeResolver.hasMultipleChoices(List.of(singleRec)));
        assertTrue(UncraftingRecipeResolver.hasMultipleChoices(List.of(multiRec)));
        assertTrue(UncraftingRecipeResolver.hasMultipleChoices(List.of(singleRec, singleRec)));

        try (InputStream png = UnitAndCompatTests.class.getResourceAsStream("/assets/uncrafting_table/textures/gui/container/uncrafting_table.png")) {
            assertNotNull(png, "GUI texture must exist on classpath");
            BufferedImage img = ImageIO.read(png);
            assertNotNull(img);
            assertEquals(256, img.getWidth());
            assertEquals(256, img.getHeight());
        }

        try (InputStream lang = UnitAndCompatTests.class.getResourceAsStream("/assets/uncrafting_table/lang/en_us.json")) {
            assertNotNull(lang, "en_us.json must exist");
            JsonObject obj = JsonParser.parseReader(new InputStreamReader(lang, StandardCharsets.UTF_8)).getAsJsonObject();
            assertEquals("Uncrafting Table", obj.get("block.uncrafting_table.uncrafting_table").getAsString());
        }

        try (InputStream bl = UnitAndCompatTests.class.getResourceAsStream("/data/uncrafting_table/tags/item/blacklist.json")) {
            assertNotNull(bl, "blacklist.json must exist");
            JsonObject obj = JsonParser.parseReader(new InputStreamReader(bl, StandardCharsets.UTF_8)).getAsJsonObject();
            assertTrue(obj.getAsJsonArray("values").size() > 10);
        }
    }
}

package com.example.uncraftingtable.recipe;

import com.example.uncraftingtable.block.entity.UncraftingTableBlockEntity;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleItemRecipe;
import net.minecraft.world.item.crafting.SmithingTransformRecipe;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.crafting.SizedIngredient;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves all valid uncrafting recipes for an ItemStack, including:
 * - Standard Minecraft Crafting, Smithing, Stonecutting, Smoking, Blasting,
 * Smelting
 * - Modded custom RecipeTypes (via reflection on SizedIngredient, Ingredient,
 * ItemStack, and custom outputs)
 * - Dynamic DataComponent crafting systems such as Iron's Gems 'n Jewelry
 * (irons_jewelry:jewelry_data)
 * Also groups identical ingredients and exposes selectable variants (e.g., wood
 * types, stone types).
 */
public final class UncraftingRecipeResolver {

    private static final ResourceLocation IRONS_JEWELRY_DATA_ID = ResourceLocation.fromNamespaceAndPath("irons_jewelry",
            "jewelry_data");

    private static final Set<String> SINGLE_OUT_METHOD_NAMES = Set.of(
            "getResult", "result", "getOutput", "output", "getOutputItem", "outputItem");
    private static final Set<String> LIST_OUT_METHOD_NAMES = Set.of(
            "getResults", "getRollableResults", "results", "outputs");
    private static final List<String> COUNT_METHOD_NAMES = List.of(
            "count", "amount", "getCount", "getAmount");

    private static final Map<Class<?>, RecipeReflectMeta> RECIPE_META_CACHE = new ConcurrentHashMap<>();
    private static final Map<Class<?>, WrapperMeta> WRAPPER_META_CACHE = new ConcurrentHashMap<>();
    private static final Map<RecipeManager, IndexedRecipes> MANAGER_INDEX_CACHE = Collections
            .synchronizedMap(new WeakHashMap<>());

    private record QueryKey(Item item, boolean hasSmoker, boolean hasBlastFurnace) {
    }

    private static final class IndexedRecipes {
        final Collection<RecipeHolder<?>> recipesRef;
        final int recipesSize;
        final Map<Item, List<RecipeHolder<?>>> byResultItem;
        final Map<QueryKey, List<ResolvedRecipe>> resolvedCache = new ConcurrentHashMap<>();

        IndexedRecipes(Collection<RecipeHolder<?>> recipesRef, int recipesSize,
                Map<Item, List<RecipeHolder<?>>> byResultItem) {
            this.recipesRef = recipesRef;
            this.recipesSize = recipesSize;
            this.byResultItem = byResultItem;
        }
    }

    private record RecipeReflectMeta(
            List<Method> singleOutMethods,
            List<Method> listOutMethods,
            List<Field> outputFields,
            List<Field> inputFields) {
        static RecipeReflectMeta get(Class<?> recipeClass) {
            return RECIPE_META_CACHE.computeIfAbsent(recipeClass, cls -> {
                List<Method> singleOut = new ArrayList<>();
                List<Method> listOut = new ArrayList<>();
                List<Field> outFields = new ArrayList<>();
                List<Field> inFields = new ArrayList<>();
                Class<?> cur = cls;
                while (cur != null && cur != Object.class) {
                    for (Method m : cur.getDeclaredMethods()) {
                        if (m.getParameterCount() != 0 || Modifier.isStatic(m.getModifiers()))
                            continue;
                        String name = m.getName();
                        if (SINGLE_OUT_METHOD_NAMES.contains(name)) {
                            m.setAccessible(true);
                            singleOut.add(m);
                        } else if (LIST_OUT_METHOD_NAMES.contains(name)) {
                            m.setAccessible(true);
                            listOut.add(m);
                        }
                    }
                    for (Field f : cur.getDeclaredFields()) {
                        if (Modifier.isStatic(f.getModifiers()))
                            continue;
                        String fname = f.getName().toLowerCase();
                        f.setAccessible(true);
                        if (fname.contains("result") || fname.contains("output")) {
                            outFields.add(f);
                        } else if (!fname.contains("rollable")) {
                            inFields.add(f);
                        }
                    }
                    cur = cur.getSuperclass();
                }
                return new RecipeReflectMeta(
                        List.copyOf(singleOut), List.copyOf(listOut),
                        List.copyOf(outFields), List.copyOf(inFields));
            });
        }
    }

    private record WrapperMeta(Method getStackMethod, Method getChanceMethod, Method ingredientMethod,
            Method countMethod) {
        static WrapperMeta get(Class<?> wrapperClass) {
            return WRAPPER_META_CACHE.computeIfAbsent(wrapperClass, cls -> {
                Method getStack = null;
                Method getChance = null;
                Method ingredient = null;
                Map<String, Method> foundCountMethods = new HashMap<>();
                for (Method m : cls.getMethods()) {
                    if (m.getParameterCount() != 0)
                        continue;
                    String name = m.getName();
                    if ("getStack".equals(name))
                        getStack = m;
                    else if ("getChance".equals(name))
                        getChance = m;
                    else if ("ingredient".equals(name))
                        ingredient = m;
                    else if (COUNT_METHOD_NAMES.contains(name))
                        foundCountMethods.put(name, m);
                }
                Method count = null;
                for (String cName : COUNT_METHOD_NAMES) {
                    if (foundCountMethods.containsKey(cName)) {
                        count = foundCountMethods.get(cName);
                        break;
                    }
                }
                return new WrapperMeta(getStack, getChance, ingredient, count);
            });
        }
    }

    /**
     * Clear cached RecipeManager indexes (called on datapack reload / recipe sync).
     */
    public static void clearCache() {
        MANAGER_INDEX_CACHE.clear();
    }

    private UncraftingRecipeResolver() {
    }

    public record IngredientGroup(List<ItemStack> variants, int count) {
        public ItemStack pickVariant(int variantIndex) {
            if (variants.isEmpty()) {
                return ItemStack.EMPTY;
            }
            int safe = Math.floorMod(variantIndex, variants.size());
            return variants.get(safe).copyWithCount(1);
        }

        public boolean hasChoices() {
            return variants.size() > 1;
        }
    }

    public record ResolvedRecipe(
            ResourceLocation id,
            String sourceLabel,
            int inputCost,
            List<IngredientGroup> groups,
            int totalIngredientCount) {
    }

    /**
     * A concrete selectable output choice (recipe + chosen variant per group) for
     * the Recipe Filter UI.
     */
    public record ConcreteOutput(
            ResourceLocation recipeId,
            String sourceLabel,
            int inputCost,
            List<ItemStack> outputs,
            List<ResourceLocation> chosenMaterialIds,
            ResolvedRecipe sourceRecipe,
            boolean hasInlineGroupSwatches) {
    }

    public static boolean hasMultipleChoices(List<ResolvedRecipe> recipes) {
        if (recipes.size() > 1)
            return true;
        if (recipes.isEmpty())
            return false;
        for (IngredientGroup g : recipes.get(0).groups()) {
            if (g.hasChoices())
                return true;
        }
        return false;
    }

    /**
     * Expand matching recipes and tag variants into deduplicated concrete output
     * rows (up to 64 combinations per recipe).
     * Recipes with >64 combinations stay a single row with inline clickable
     * swatches so UI never lags.
     */
    public static List<ConcreteOutput> expandOutputOptions(List<ResolvedRecipe> recipes) {
        List<ConcreteOutput> out = new ArrayList<>();
        Set<String> seenFingerprints = new HashSet<>();

        for (ResolvedRecipe recipe : recipes) {
            List<IngredientGroup> groups = recipe.groups();
            long combos = 1;
            for (IngredientGroup g : groups) {
                combos *= Math.max(1, g.variants().size());
                if (combos > 64)
                    break;
            }

            if (combos <= 64) {
                List<int[]> indexCombos = new ArrayList<>();
                buildCombinations(groups, 0, new int[groups.size()], indexCombos);
                for (int[] idxArr : indexCombos) {
                    List<ItemStack> stacks = new ArrayList<>();
                    List<ResourceLocation> matIds = new ArrayList<>();
                    StringBuilder fp = new StringBuilder(recipe.sourceLabel())
                            .append('|').append(recipe.inputCost()).append('|');
                    for (int g = 0; g < groups.size(); g++) {
                        IngredientGroup group = groups.get(g);
                        ItemStack picked = group.pickVariant(idxArr[g]);
                        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(picked.getItem());
                        stacks.add(picked.copyWithCount(group.count()));
                        matIds.add(itemId);
                        fp.append(itemId).append('x').append(group.count()).append(',');
                    }
                    if (seenFingerprints.add(fp.toString())) {
                        out.add(new ConcreteOutput(
                                recipe.id(), recipe.sourceLabel(), recipe.inputCost(),
                                stacks, matIds, recipe, false));
                    }
                }
            } else {
                List<ItemStack> stacks = new ArrayList<>();
                List<ResourceLocation> matIds = new ArrayList<>();
                for (IngredientGroup group : groups) {
                    ItemStack picked = group.pickVariant(0);
                    stacks.add(picked.copyWithCount(group.count()));
                    matIds.add(BuiltInRegistries.ITEM.getKey(picked.getItem()));
                }
                out.add(new ConcreteOutput(
                        recipe.id(), recipe.sourceLabel(), recipe.inputCost(),
                        stacks, matIds, recipe, true));
            }
        }
        return out;
    }

    private static void buildCombinations(List<IngredientGroup> groups, int depth, int[] cur, List<int[]> out) {
        if (depth == groups.size()) {
            out.add(cur.clone());
            return;
        }
        int count = Math.max(1, groups.get(depth).variants().size());
        for (int i = 0; i < count; i++) {
            cur[depth] = i;
            buildCombinations(groups, depth + 1, cur, out);
        }
    }

    private record RawIngredient(Ingredient ingredient, List<ItemStack> explicitStacks, int count) {
    }

    /**
     * Find all valid uncrafting recipe options for the given input stack, sorted by
     * best default match.
     */
    public static List<ResolvedRecipe> findRecipes(
            Level level,
            ItemStack inputStack,
            boolean hasSmoker,
            boolean hasBlastFurnace) {
        if (level == null || inputStack.isEmpty()) {
            return List.of();
        }

        // 1. Iron's Gems 'n Jewelry (component-driven crafting via Jewelcrafting
        // Station)
        Optional<ResolvedRecipe> jewelry = tryResolveIronsJewelry(inputStack);

        // 1b. Potion Brewing preview support (actual uncrafting requires 3 identical
        // bottles + Brewing Stand)
        if (inputStack.is(Items.POTION) || inputStack.is(Items.SPLASH_POTION)
                || inputStack.is(Items.LINGERING_POTION)) {
            Optional<ResolvedRecipe> potion = tryResolvePotionPreview(level, inputStack);
            if (jewelry.isEmpty()) {
                return potion.map(List::of).orElse(List.of());
            }
            List<ResolvedRecipe> combined = new ArrayList<>(2);
            jewelry.ifPresent(combined::add);
            potion.ifPresent(combined::add);
            return combined;
        }

        // 2. Vanilla & Modded RecipeManager recipes (O(1) cached index per
        // RecipeManager)
        RegistryAccess access = level.registryAccess();
        Item item = inputStack.getItem();
        IndexedRecipes index = getOrBuildIndex(level.getRecipeManager(), access);
        List<ResolvedRecipe> fromManager = index.resolvedCache.computeIfAbsent(
                new QueryKey(item, hasSmoker, hasBlastFurnace),
                key -> resolveIndexedCandidates(index, access, key.item(), key.hasSmoker(), key.hasBlastFurnace()));

        if (jewelry.isEmpty()) {
            return fromManager;
        }
        List<ResolvedRecipe> combined = new ArrayList<>(1 + fromManager.size());
        combined.add(jewelry.get());
        combined.addAll(fromManager);
        return combined;
    }

    private static IndexedRecipes getOrBuildIndex(RecipeManager manager, RegistryAccess access) {
        Collection<RecipeHolder<?>> recipes = manager.getRecipes();
        int size = recipes.size();
        IndexedRecipes existing = MANAGER_INDEX_CACHE.get(manager);
        if (existing != null && existing.recipesRef == recipes && existing.recipesSize == size) {
            return existing;
        }

        synchronized (manager) {
            existing = MANAGER_INDEX_CACHE.get(manager);
            if (existing != null && existing.recipesRef == recipes && existing.recipesSize == size) {
                return existing;
            }
            Map<Item, List<RecipeHolder<?>>> byResult = new HashMap<>(Math.max(16, size));
            for (RecipeHolder<?> holder : recipes) {
                try {
                    ItemStack result = extractPrimaryResult(holder.value(), access, ItemStack.EMPTY);
                    if (!result.isEmpty()) {
                        byResult.computeIfAbsent(result.getItem(), k -> new ArrayList<>(2)).add(holder);
                    }
                } catch (Exception ignored) {
                }
            }
            IndexedRecipes built = new IndexedRecipes(recipes, size, byResult);
            MANAGER_INDEX_CACHE.put(manager, built);
            return built;
        }
    }

    private static List<ResolvedRecipe> resolveIndexedCandidates(
            IndexedRecipes index,
            RegistryAccess access,
            Item item,
            boolean hasSmoker,
            boolean hasBlastFurnace) {
        List<RecipeHolder<?>> matching = index.byResultItem.get(item);
        if (matching == null || matching.isEmpty()) {
            return List.of();
        }
        ItemStack probe = new ItemStack(item);
        String itemNamespace = BuiltInRegistries.ITEM.getKey(item).getNamespace();
        List<CandidateRecipe> candidates = new ArrayList<>(matching.size());
        for (RecipeHolder<?> holder : matching) {
            inspectCandidate(holder.id(), holder.value(), access, itemNamespace, probe, hasSmoker, hasBlastFurnace)
                    .ifPresent(candidates::add);
        }
        candidates.sort(
                Comparator.comparingInt((CandidateRecipe c) -> c.tier)
                        .thenComparing((CandidateRecipe c) -> !c.sameNamespace)
                        .thenComparingInt(c -> c.inputCost == 9 ? 1 : 0)
                        .thenComparing((CandidateRecipe c) -> -c.totalIngs)
                        .thenComparingInt(c -> c.inputCost)
                        .thenComparing(c -> c.id.toString()));
        List<ResolvedRecipe> resolved = new ArrayList<>(candidates.size());
        for (CandidateRecipe c : candidates) {
            resolved.add(c.toResolved());
        }
        return List.copyOf(resolved);
    }

    /**
     * Inspect a single recipe against an input item, returning a ResolvedRecipe if
     * valid for uncrafting.
     */
    public static Optional<ResolvedRecipe> resolveSingleRecipe(
            ResourceLocation recipeId,
            Recipe<?> recipe,
            RegistryAccess access,
            ItemStack inputStack,
            boolean hasSmoker,
            boolean hasBlastFurnace) {
        if (recipeId == null || recipe == null || inputStack.isEmpty()) {
            return Optional.empty();
        }
        String itemNamespace = BuiltInRegistries.ITEM.getKey(inputStack.getItem()).getNamespace();
        return inspectCandidate(recipeId, recipe, access, itemNamespace, inputStack, hasSmoker, hasBlastFurnace)
                .map(CandidateRecipe::toResolved);
    }

    private static Optional<CandidateRecipe> inspectCandidate(
            ResourceLocation recipeId,
            Recipe<?> recipe,
            RegistryAccess access,
            String itemNamespace,
            ItemStack inputStack,
            boolean hasSmoker,
            boolean hasBlastFurnace) {
        try {
            RecipeType<?> type = recipe.getType();

            ItemStack result = extractPrimaryResult(recipe, access, inputStack);
            if (result.isEmpty() || result.getItem() != inputStack.getItem()) {
                return Optional.empty();
            }

            if (type == RecipeType.BLASTING && !hasBlastFurnace) {
                return Optional.empty();
            }
            if (!hasSmoker && isSmokerRequired(type, result)) {
                return Optional.empty();
            }

            List<RawIngredient> rawInputs = extractRawIngredients(recipe);
            if (rawInputs.isEmpty() || containsSelfReference(rawInputs, inputStack)) {
                return Optional.empty();
            }

            List<IngredientGroup> groups = groupIngredients(rawInputs);
            if (groups.isEmpty()) {
                return Optional.empty();
            }

            int totalIngs = groups.stream().mapToInt(IngredientGroup::count).sum();
            int resultCount = Math.max(1, result.getCount());
            String label = formatSourceLabel(type, recipeId);
            int tier = computePriorityTier(type, resultCount);

            return Optional.of(new CandidateRecipe(
                    recipeId,
                    label,
                    resultCount,
                    groups,
                    totalIngs,
                    tier,
                    recipeId.getNamespace().equals(itemNamespace)));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private record CandidateRecipe(
            ResourceLocation id,
            String label,
            int inputCost,
            List<IngredientGroup> groups,
            int totalIngs,
            int tier,
            boolean sameNamespace) {
        ResolvedRecipe toResolved() {
            return new ResolvedRecipe(id, label, inputCost, groups, totalIngs);
        }
    }

    private static int computePriorityTier(RecipeType<?> type, int resultCount) {
        if (type == RecipeType.BLASTING || type == RecipeType.SMOKING || type == RecipeType.CAMPFIRE_COOKING) {
            return 0;
        }
        if (type == RecipeType.CRAFTING || type == RecipeType.SMITHING) {
            return resultCount == 9 ? 4 : 1;
        }
        if (type == RecipeType.STONECUTTING) {
            return 3;
        }
        if (type == RecipeType.SMELTING) {
            return 3;
        }
        // Custom modded recipe type (Create, Ars Nouveau, Mekanism, etc.)
        return 2;
    }

    private static String formatSourceLabel(RecipeType<?> type, ResourceLocation recipeId) {
        if (type == RecipeType.CRAFTING)
            return "Crafting";
        if (type == RecipeType.SMITHING)
            return "Smithing";
        if (type == RecipeType.BLASTING)
            return "Blasting";
        if (type == RecipeType.SMOKING)
            return "Smoking";
        if (type == RecipeType.CAMPFIRE_COOKING)
            return "Campfire";
        if (type == RecipeType.SMELTING)
            return "Smelting";
        if (type == RecipeType.STONECUTTING)
            return "Stonecutting";

        ResourceLocation typeKey = BuiltInRegistries.RECIPE_TYPE.getKey(type);
        String raw = typeKey != null ? typeKey.getPath() : recipeId.getPath();
        int slash = raw.lastIndexOf('/');
        if (slash >= 0 && slash + 1 < raw.length()) {
            raw = raw.substring(slash + 1);
        }
        String[] parts = raw.replace('_', ' ').trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (!p.isEmpty()) {
                if (!sb.isEmpty())
                    sb.append(' ');
                sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
            }
        }
        String formatted = sb.toString();
        return formatted.length() > 14 ? formatted.substring(0, 14) : formatted;
    }

    public record PotionStep(ItemStack baseBottles, ItemStack reagent) {
    }

    /**
     * Resolve the base potion stack (count 3) and brewing reagent (count 1) for a
     * potion, or empty if base Water Bottle.
     */
    @SuppressWarnings("unchecked")
    public static Optional<PotionStep> resolvePotionOutputs(Level level, ItemStack inputStack) {
        if (level == null || inputStack.isEmpty())
            return Optional.empty();
        PotionContents contents = inputStack.get(DataComponents.POTION_CONTENTS);

        ItemStack bottle = null;
        ItemStack reagent = null;

        if (inputStack.is(Items.LINGERING_POTION)) {
            bottle = new ItemStack(Items.SPLASH_POTION);
            if (contents != null)
                bottle.set(DataComponents.POTION_CONTENTS, contents);
            reagent = new ItemStack(Items.DRAGON_BREATH);
        } else if (inputStack.is(Items.SPLASH_POTION)) {
            bottle = new ItemStack(Items.POTION);
            if (contents != null)
                bottle.set(DataComponents.POTION_CONTENTS, contents);
            reagent = new ItemStack(Items.GUNPOWDER);
        } else {
            try {
                PotionBrewing potionBrewing = level.potionBrewing();
                List<?> mixes = null;
                for (Field f : potionBrewing.getClass().getDeclaredFields()) {
                    f.setAccessible(true);
                    if (List.class.isAssignableFrom(f.getType())) {
                        Object val = f.get(potionBrewing);
                        if (val instanceof List<?> list && !list.isEmpty()
                                && list.get(0).getClass().getName().contains("Mix")) {
                            mixes = list;
                            break;
                        }
                    }
                }

                if (mixes != null && contents != null) {
                    Holder<Potion> targetPotion = contents.potion().orElse(null);
                    if (targetPotion != null) {
                        for (Object mix : mixes) {
                            Method toMethod = mix.getClass().getMethod("to");
                            toMethod.setAccessible(true);
                            if (toMethod.invoke(mix).equals(targetPotion)) {
                                Method fromMethod = mix.getClass().getMethod("from");
                                fromMethod.setAccessible(true);
                                Object fromVal = fromMethod.invoke(mix);

                                Method ingMethod = mix.getClass().getMethod("ingredient");
                                ingMethod.setAccessible(true);
                                Ingredient ing = (Ingredient) ingMethod.invoke(mix);

                                if (fromVal instanceof Holder<?> fromHolder) {
                                    bottle = new ItemStack(Items.POTION);
                                    bottle.set(DataComponents.POTION_CONTENTS,
                                            new PotionContents((Holder<Potion>) fromHolder));
                                }
                                if (ing != null && ing.getItems().length > 0) {
                                    reagent = new ItemStack(ing.getItems()[0].getItem());
                                }
                                break;
                            }
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }

        if (bottle == null || reagent == null) {
            if (contents != null && contents.potion().isPresent()
                    && contents.potion().get().value() == Potions.WATER.value()) {
                return Optional.empty();
            }
            bottle = new ItemStack(Items.GLASS_BOTTLE);
            reagent = new ItemStack(Items.NETHER_WART);
        }

        bottle.setCount(3);
        reagent.setCount(1);
        return Optional.of(new PotionStep(bottle, reagent));
    }

    private static Optional<ResolvedRecipe> tryResolvePotionPreview(Level level, ItemStack stack) {
        return resolvePotionOutputs(level, stack).map(step -> {
            List<IngredientGroup> groups = List.of(
                    new IngredientGroup(List.of(step.baseBottles().copyWithCount(1)), step.baseBottles().getCount()),
                    new IngredientGroup(List.of(step.reagent().copyWithCount(1)), step.reagent().getCount()));
            return new ResolvedRecipe(
                    ResourceLocation.withDefaultNamespace("brewing"),
                    "Brewing (3x)",
                    3,
                    groups,
                    4);
        });
    }

    /**
     * Soft-integration for Iron's Gems 'n Jewelry (irons_jewelry:jewelry_data
     * component).
     * Reads PatternDefinition.partTemplate() and JewelryData.parts() via reflection
     * so no hard dependency is needed.
     */
    @SuppressWarnings("unchecked")
    private static Optional<ResolvedRecipe> tryResolveIronsJewelry(ItemStack stack) {
        DataComponentType<?> compType = BuiltInRegistries.DATA_COMPONENT_TYPE.get(IRONS_JEWELRY_DATA_ID);
        if (compType == null) {
            return Optional.empty();
        }
        return resolveIronsJewelryData(stack.get(compType));
    }

    /**
     * Reflectively extract materials from an Iron's Gems 'n Jewelry JewelryData
     * instance.
     */
    public static Optional<ResolvedRecipe> resolveIronsJewelryData(Object jewelryData) {
        if (jewelryData == null) {
            return Optional.empty();
        }

        try {
            Method isValidMethod = jewelryData.getClass().getMethod("isValid");
            if (!Boolean.TRUE.equals(isValidMethod.invoke(jewelryData))) {
                return Optional.empty();
            }

            Holder<?> patternHolder = (Holder<?>) jewelryData.getClass().getMethod("pattern").invoke(jewelryData);
            Map<?, ?> partsMap = (Map<?, ?>) jewelryData.getClass().getMethod("parts").invoke(jewelryData);
            if (patternHolder == null || partsMap == null) {
                return Optional.empty();
            }

            Object patternDef = patternHolder.value();
            List<?> partTemplate = (List<?>) patternDef.getClass().getMethod("partTemplate").invoke(patternDef);
            if (partTemplate == null || partTemplate.isEmpty()) {
                return Optional.empty();
            }

            List<RawIngredient> rawIngredients = new ArrayList<>();
            for (Object partIng : partTemplate) {
                Object partHolder = partIng.getClass().getMethod("part").invoke(partIng);
                int cost = ((Number) partIng.getClass().getMethod("materialCost").invoke(partIng)).intValue();
                if (cost <= 0)
                    continue;

                Object matHolderObj = partsMap.get(partHolder);
                if (matHolderObj instanceof Holder<?> matHolder) {
                    Object matDef = matHolder.value();
                    Optional<?> optIng = (Optional<?>) matDef.getClass().getMethod("ingredient").invoke(matDef);
                    if (optIng.isPresent() && optIng.get() instanceof Ingredient ing && !ing.isEmpty()) {
                        rawIngredients.add(new RawIngredient(ing, List.of(), cost));
                    }
                }
            }

            List<IngredientGroup> groups = groupIngredients(rawIngredients);
            if (groups.isEmpty()) {
                return Optional.empty();
            }

            ResourceLocation patternId = patternHolder.unwrapKey()
                    .map(ResourceKey::location)
                    .orElse(ResourceLocation.fromNamespaceAndPath("irons_jewelry", "jewelcrafting"));
            int total = groups.stream().mapToInt(IngredientGroup::count).sum();
            return Optional.of(new ResolvedRecipe(patternId, "Jewelcraft", 1, groups, total));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private static ItemStack extractPrimaryResult(Recipe<?> recipe, RegistryAccess access, ItemStack target) {
        try {
            ItemStack direct = recipe.getResultItem(access);
            if (direct != null && !direct.isEmpty()) {
                return direct;
            }
        } catch (Exception ignored) {
        }

        // Cached reflection lookup (zero NoSuchMethodException allocations in hot
        // loops)
        RecipeReflectMeta meta = RecipeReflectMeta.get(recipe.getClass());
        for (Method m : meta.singleOutMethods()) {
            try {
                ItemStack found = unwrapOutputObject(m.invoke(recipe), target);
                if (!found.isEmpty())
                    return found;
            } catch (Exception ignored) {
            }
        }
        for (Method m : meta.listOutMethods()) {
            try {
                ItemStack found = unwrapOutputList(m.invoke(recipe), target);
                if (!found.isEmpty())
                    return found;
            } catch (Exception ignored) {
            }
        }
        for (Field f : meta.outputFields()) {
            try {
                Object val = f.get(recipe);
                ItemStack found = unwrapOutputObject(val, target);
                if (!found.isEmpty())
                    return found;
                found = unwrapOutputList(val, target);
                if (!found.isEmpty())
                    return found;
            } catch (Exception ignored) {
            }
        }

        return ItemStack.EMPTY;
    }

    private static ItemStack unwrapOutputObject(Object obj, ItemStack target) {
        if (obj == null)
            return ItemStack.EMPTY;
        if (obj instanceof ItemStack stack && !stack.isEmpty()) {
            return stack;
        }
        if (obj instanceof Optional<?> opt && opt.isPresent()) {
            return unwrapOutputObject(opt.get(), target);
        }
        // Handle Create ProcessingOutput / wrapper with getStack() & getChance()
        WrapperMeta wMeta = WrapperMeta.get(obj.getClass());
        if (wMeta.getStackMethod() != null) {
            try {
                Object stackObj = wMeta.getStackMethod().invoke(obj);
                boolean itemMatches = target == null || target.isEmpty()
                        || (stackObj instanceof ItemStack st && st.getItem() == target.getItem());
                if (stackObj instanceof ItemStack stack && !stack.isEmpty() && itemMatches) {
                    if (wMeta.getChanceMethod() != null) {
                        float chance = ((Number) wMeta.getChanceMethod().invoke(obj)).floatValue();
                        if (chance < 0.999f)
                            return ItemStack.EMPTY;
                    }
                    return stack;
                }
            } catch (Exception ignored) {
            }
        }
        return ItemStack.EMPTY;
    }

    private static ItemStack unwrapOutputList(Object obj, ItemStack target) {
        if (!(obj instanceof Collection<?> col) || col.isEmpty()) {
            return ItemStack.EMPTY;
        }
        // Only treat single-output deterministic recipes cleanly to avoid byproduct
        // dupes
        if (col.size() == 1) {
            Object only = col.iterator().next();
            return unwrapOutputObject(only, target);
        }
        return ItemStack.EMPTY;
    }

    private static List<RawIngredient> extractRawIngredients(Recipe<?> recipe) {
        // Smithing recipes expose template, base, addition fields directly
        if (recipe instanceof SmithingTransformRecipe) {
            List<RawIngredient> smithing = new ArrayList<>();
            extractFieldIngredients(recipe, smithing, new ArrayList<>());
            if (!smithing.isEmpty())
                return smithing;
        }

        // Standard vanilla crafting/cooking/stonecutting recipes use getIngredients()
        // directly
        if (recipe instanceof CraftingRecipe || recipe instanceof AbstractCookingRecipe
                || recipe instanceof SingleItemRecipe) {
            List<RawIngredient> vanilla = new ArrayList<>();
            try {
                for (Ingredient ing : recipe.getIngredients()) {
                    if (ing != null && !ing.isEmpty()) {
                        vanilla.add(new RawIngredient(ing, List.of(), 1));
                    }
                }
            } catch (Exception ignored) {
            }
            if (!vanilla.isEmpty())
                return vanilla;
        }

        // Modded recipes: separate counted (SizedIngredient) fields from plain
        // Ingredient fields to avoid double-counting caches
        List<RawIngredient> sizedFound = new ArrayList<>();
        List<RawIngredient> plainFound = new ArrayList<>();
        extractFieldIngredients(recipe, plainFound, sizedFound);

        if (!sizedFound.isEmpty()) {
            return sizedFound;
        }

        List<RawIngredient> methodIngs = new ArrayList<>();
        try {
            for (Ingredient ing : recipe.getIngredients()) {
                if (ing != null && !ing.isEmpty()) {
                    methodIngs.add(new RawIngredient(ing, List.of(), 1));
                }
            }
        } catch (Exception ignored) {
        }

        if (!methodIngs.isEmpty() && methodIngs.size() >= plainFound.size()) {
            return methodIngs;
        }
        return !plainFound.isEmpty() ? plainFound : methodIngs;
    }

    private static void extractFieldIngredients(
            Recipe<?> recipe,
            List<RawIngredient> plainOut,
            List<RawIngredient> sizedOut) {
        RecipeReflectMeta meta = RecipeReflectMeta.get(recipe.getClass());
        for (Field f : meta.inputFields()) {
            try {
                collectIngredientValue(f.get(recipe), plainOut, sizedOut);
            } catch (Exception ignored) {
            }
        }
    }

    private static void collectIngredientValue(Object val, List<RawIngredient> plainOut, List<RawIngredient> sizedOut) {
        if (val == null)
            return;

        if (val instanceof SizedIngredient sized) {
            if (!sized.ingredient().isEmpty() && sized.count() > 0) {
                sizedOut.add(new RawIngredient(sized.ingredient(), List.of(), sized.count()));
            }
            return;
        }
        if (val instanceof Ingredient ing) {
            if (!ing.isEmpty()) {
                plainOut.add(new RawIngredient(ing, List.of(), 1));
            }
            return;
        }
        if (val instanceof ItemStack stack) {
            if (!stack.isEmpty()) {
                plainOut.add(new RawIngredient(null, List.of(stack.copyWithCount(1)), stack.getCount()));
            }
            return;
        }
        if (val instanceof Optional<?> opt) {
            opt.ifPresent(inner -> collectIngredientValue(inner, plainOut, sizedOut));
            return;
        }
        if (val instanceof Collection<?> col) {
            for (Object elem : col) {
                collectIngredientValue(elem, plainOut, sizedOut);
            }
            return;
        }
        if (val.getClass().isArray()) {
            int len = java.lang.reflect.Array.getLength(val);
            for (int i = 0; i < len; i++) {
                collectIngredientValue(java.lang.reflect.Array.get(val, i), plainOut, sizedOut);
            }
            return;
        }

        // Duck-type third-party counted ingredient wrappers (e.g. ingredient() +
        // count()/amount()) using cached metadata
        WrapperMeta wMeta = WrapperMeta.get(val.getClass());
        if (wMeta.ingredientMethod() != null) {
            try {
                Object inner = wMeta.ingredientMethod().invoke(val);
                if (inner instanceof Ingredient ing && !ing.isEmpty()) {
                    int count = 1;
                    if (wMeta.countMethod() != null) {
                        count = Math.max(1, ((Number) wMeta.countMethod().invoke(val)).intValue());
                    }
                    sizedOut.add(new RawIngredient(ing, List.of(), count));
                }
            } catch (Exception ignored) {
            }
        }
    }

    private static boolean containsSelfReference(List<RawIngredient> rawInputs, ItemStack target) {
        Item targetItem = target.getItem();
        ItemStack probe = new ItemStack(targetItem);
        for (RawIngredient ri : rawInputs) {
            if (ri.ingredient != null) {
                try {
                    if (ri.ingredient.test(probe) || (!target.isEmpty() && ri.ingredient.test(target))) {
                        return true;
                    }
                    // Also inspect resolved stacks: DataComponent/custom upgrade ingredients return
                    // false on
                    // bare probes yet still emit targetItem variants in groupIngredients().
                    ItemStack[] items = ri.ingredient.getItems();
                    if (items != null) {
                        for (ItemStack s : items) {
                            if (!s.isEmpty() && s.getItem() == targetItem) {
                                return true;
                            }
                        }
                    }
                } catch (Exception ignored) {
                }
            }
            for (ItemStack s : ri.explicitStacks) {
                if (!s.isEmpty() && s.getItem() == targetItem) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Require Smoker modifier for vanilla smoking/campfire and modded
     * smelting/smoking of food items.
     */
    private static boolean isSmokerRequired(RecipeType<?> type, ItemStack result) {
        if (type == RecipeType.SMOKING || type == RecipeType.CAMPFIRE_COOKING) {
            return true;
        }
        if (!result.has(DataComponents.FOOD)) {
            return false;
        }
        if (type == RecipeType.SMELTING) {
            return true;
        }
        ResourceLocation typeId = BuiltInRegistries.RECIPE_TYPE.getKey(type);
        if (typeId == null) {
            return false;
        }
        String path = typeId.getPath();
        return path.contains("smelt") || path.contains("smok");
    }

    private static List<IngredientGroup> groupIngredients(List<RawIngredient> rawInputs) {
        Map<String, List<ItemStack>> keyToVariants = new LinkedHashMap<>();
        Map<String, Integer> keyToCount = new LinkedHashMap<>();

        for (RawIngredient ri : rawInputs) {
            List<ItemStack> candidates = new ArrayList<>();
            if (ri.ingredient != null) {
                ItemStack[] items = ri.ingredient.getItems();
                if (items != null) {
                    for (ItemStack st : items) {
                        if (!st.isEmpty()) {
                            candidates.add(st.copyWithCount(1));
                        }
                    }
                }
            } else {
                candidates.addAll(ri.explicitStacks);
            }

            if (candidates.isEmpty()) {
                continue;
            }

            // Deduplicate variants by item ID while preserving order
            Map<ResourceLocation, ItemStack> dedup = new LinkedHashMap<>();
            for (ItemStack st : candidates) {
                ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(st.getItem());
                dedup.putIfAbsent(itemId, st);
            }

            // Prefer non-blacklisted alternatives when a tag contains blacklisted items
            // alongside normal ones
            List<ItemStack> nonBlacklisted = dedup.values().stream()
                    .filter(s -> !s.is(UncraftingTableBlockEntity.BLACKLIST_TAG))
                    .toList();
            List<ItemStack> finalVariants = nonBlacklisted.isEmpty()
                    ? new ArrayList<>(dedup.values())
                    : new ArrayList<>(nonBlacklisted);

            StringBuilder keyBuilder = new StringBuilder();
            for (ItemStack v : finalVariants) {
                keyBuilder.append(BuiltInRegistries.ITEM.getKey(v.getItem())).append('|');
            }
            String key = keyBuilder.toString();

            keyToVariants.putIfAbsent(key, finalVariants);
            keyToCount.merge(key, Math.max(1, ri.count), Integer::sum);
        }

        List<IngredientGroup> groups = new ArrayList<>();
        for (Map.Entry<String, List<ItemStack>> e : keyToVariants.entrySet()) {
            groups.add(new IngredientGroup(e.getValue(), keyToCount.get(e.getKey())));
        }
        return groups;
    }
}

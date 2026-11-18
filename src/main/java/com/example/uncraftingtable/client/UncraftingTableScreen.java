package com.example.uncraftingtable.client;

import com.example.uncraftingtable.UncraftingTableMod;
import com.example.uncraftingtable.block.entity.UncraftingTableBlockEntity;
import com.example.uncraftingtable.block.entity.UncraftingTableBlockEntity.RecipeRule;
import com.example.uncraftingtable.menu.UncraftingTableMenu;
import com.example.uncraftingtable.network.UncraftingActionPayload;
import com.example.uncraftingtable.network.UncraftingSelectionPayload;
import com.example.uncraftingtable.network.UncraftingSettingsPayload;
import com.example.uncraftingtable.recipe.UncraftingRecipeResolver;
import com.example.uncraftingtable.recipe.UncraftingRecipeResolver.ConcreteOutput;
import com.example.uncraftingtable.recipe.UncraftingRecipeResolver.IngredientGroup;
import com.example.uncraftingtable.recipe.UncraftingRecipeResolver.ResolvedRecipe;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UncraftingTableScreen extends AbstractContainerScreen<UncraftingTableMenu> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            UncraftingTableMod.MOD_ID,
            "textures/gui/container/uncrafting_table.png"
    );

    private static final ItemStack[] MODIFIER_GHOSTS = {
            new ItemStack(Items.GRINDSTONE),
            new ItemStack(Items.SMOKER),
            new ItemStack(Items.BLAST_FURNACE),
            new ItemStack(Items.BREWING_STAND)
    };

    public enum ViewMode {
        MAIN,
        FILTER_LIST,
        FILTER_EDIT
    }

    // List & Configure Window geometry (inside 256x228 panel)
    static final int LIST_X = 8;
    static final int LIST_Y = 28;
    static final int LIST_W = 178;
    static final int LIST_ROW_H = 32;
    static final int LIST_VISIBLE = 5;

    static final int GHOST_X = 10;
    static final int GHOST_Y = 26;
    static final int OPT_X = 10;
    static final int OPT_Y = 48;
    static final int OPT_W = 236;
    static final int OPT_ROW_H = 20;
    static final int OPT_VISIBLE = 4;

    private ViewMode viewMode = ViewMode.MAIN;
    private boolean isAuto;
    private boolean muteSounds;
    private boolean muteParticles;
    private boolean showSettingsPanel = false;
    private boolean enableGrindstone;
    private boolean enableSmoker;
    private boolean enableBlastFurnace;
    private boolean enableBrewingStand;
    private boolean waitForUnconfiguredChoice;
    private int syncedRuleCount = -1;

    // Render-frame caches (avoid re-scanning RecipeManager 60+ FPS during mouse movement)
    private record RulePreviewEntry(
            RecipeRule rule,
            boolean hasSmoker,
            boolean hasBlast,
            ItemStack targetStack,
            List<ItemStack> outputs
    ) {
    }

    private final ItemStack[] lastInputSnapshots = new ItemStack[UncraftingTableBlockEntity.INPUT_SLOTS];
    private final int[] cachedInputDotColors = new int[UncraftingTableBlockEntity.INPUT_SLOTS];
    private int cachedWaitingMultiCount = 0;
    private boolean lastStatusSmoker = false;
    private boolean lastStatusBlast = false;
    private boolean lastStatusWait = false;
    private int lastStatusRuleCount = -1;
    private boolean inputStatusDirty = true;
    private final Map<ResourceLocation, RulePreviewEntry> rulePreviewCache = new HashMap<>();

    // FILTER_LIST state
    private int listScroll = 0;
    private int selectedRuleIdx = -1;
    private long lastListClickMs = 0L;
    private int lastClickedRuleIdx = -1;

    // FILTER_EDIT state
    private ItemStack ghostStack = ItemStack.EMPTY;
    private List<ConcreteOutput> editOptions = List.of();
    private int selectedOptionIdx = -1;
    private int editScroll = 0;
    private int[] inlineVariantIndices = new int[0];

    private Button editRuleBtn;
    private Button removeRuleBtn;
    private Button saveRuleBtn;

    public UncraftingTableScreen(UncraftingTableMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 256;
        this.imageHeight = 228;
        this.inventoryLabelX = 47;
        this.inventoryLabelY = 135;
        Arrays.fill(lastInputSnapshots, ItemStack.EMPTY);
        syncFieldsFromEntity();
    }

    private void syncFieldsFromEntity() {
        this.isAuto = menu.blockEntity.autoUncraft;
        this.muteSounds = menu.blockEntity.muteSounds;
        this.muteParticles = menu.blockEntity.muteParticles;
        this.enableGrindstone = menu.blockEntity.enableGrindstone;
        this.enableSmoker = menu.blockEntity.enableSmoker;
        this.enableBlastFurnace = menu.blockEntity.enableBlastFurnace;
        this.enableBrewingStand = menu.blockEntity.enableBrewingStand;
        this.waitForUnconfiguredChoice = menu.blockEntity.waitForUnconfiguredChoice;
        this.syncedRuleCount = menu.blockEntity.getRecipeRules().size();
        this.inputStatusDirty = true;
        this.rulePreviewCache.clear();
    }

    private boolean hasEntityStateChanged() {
        return this.isAuto != menu.blockEntity.autoUncraft
                || this.muteSounds != menu.blockEntity.muteSounds
                || this.muteParticles != menu.blockEntity.muteParticles
                || this.enableGrindstone != menu.blockEntity.enableGrindstone
                || this.enableSmoker != menu.blockEntity.enableSmoker
                || this.enableBlastFurnace != menu.blockEntity.enableBlastFurnace
                || this.enableBrewingStand != menu.blockEntity.enableBrewingStand
                || this.waitForUnconfiguredChoice != menu.blockEntity.waitForUnconfiguredChoice
                || this.syncedRuleCount != menu.blockEntity.getRecipeRules().size();
    }

    public boolean isFilterEditOpen() {
        return viewMode == ViewMode.FILTER_EDIT;
    }

    public Rect2i getGhostSlotBounds() {
        return new Rect2i(leftPos + GHOST_X, topPos + GHOST_Y, 18, 18);
    }

    public void setGhostItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            this.ghostStack = ItemStack.EMPTY;
            this.editOptions = List.of();
            this.selectedOptionIdx = -1;
            this.editScroll = 0;
            this.inlineVariantIndices = new int[0];
        } else {
            this.ghostStack = stack.copyWithCount(1);
            rebuildEditOptions(menu.blockEntity.getRuleFor(this.ghostStack));
        }
        updateActionButtonsState();
    }

    private void rebuildEditOptions(RecipeRule existingRule) {
        if (ghostStack.isEmpty() || minecraft == null || minecraft.level == null) {
            this.editOptions = List.of();
            this.selectedOptionIdx = -1;
            this.inlineVariantIndices = new int[0];
            return;
        }
        List<ResolvedRecipe> recipes = UncraftingRecipeResolver.findRecipes(
                minecraft.level,
                ghostStack,
                menu.blockEntity.hasSmokerModifier(),
                menu.blockEntity.hasBlastFurnaceModifier()
        );
        this.editOptions = UncraftingRecipeResolver.expandOutputOptions(recipes);
        this.editScroll = 0;
        this.selectedOptionIdx = this.editOptions.isEmpty() ? -1 : 0;
        this.inlineVariantIndices = new int[0];

        if (existingRule != null && !this.editOptions.isEmpty()) {
            for (int i = 0; i < this.editOptions.size(); i++) {
                ConcreteOutput opt = this.editOptions.get(i);
                if (!opt.recipeId().equals(existingRule.recipeId())) continue;
                if (opt.hasInlineGroupSwatches()) {
                    this.selectedOptionIdx = i;
                    initInlineVariants(opt, existingRule.chosenMaterialIds());
                    break;
                }
                if (opt.chosenMaterialIds().equals(existingRule.chosenMaterialIds())) {
                    this.selectedOptionIdx = i;
                    break;
                }
            }
        }
        if (this.selectedOptionIdx >= 0) {
            ConcreteOutput sel = this.editOptions.get(this.selectedOptionIdx);
            if (sel.hasInlineGroupSwatches() && this.inlineVariantIndices.length == 0) {
                initInlineVariants(sel, existingRule != null ? existingRule.chosenMaterialIds() : List.of());
            }
            if (this.selectedOptionIdx >= OPT_VISIBLE) {
                this.editScroll = Math.min(Math.max(0, this.editOptions.size() - OPT_VISIBLE), this.selectedOptionIdx);
            }
        }
    }

    private void initInlineVariants(ConcreteOutput opt, List<ResourceLocation> savedMats) {
        List<IngredientGroup> groups = opt.sourceRecipe().groups();
        this.inlineVariantIndices = new int[groups.size()];
        for (int g = 0; g < groups.size(); g++) {
            if (g < savedMats.size()) {
                ResourceLocation target = savedMats.get(g);
                List<ItemStack> vars = groups.get(g).variants();
                for (int v = 0; v < vars.size(); v++) {
                    if (BuiltInRegistries.ITEM.getKey(vars.get(v).getItem()).equals(target)) {
                        this.inlineVariantIndices[g] = v;
                        break;
                    }
                }
            }
        }
    }

    private void switchView(ViewMode newMode) {
        this.viewMode = newMode;
        if (newMode == ViewMode.MAIN) {
            menu.setSlotVisibility(true, true);
        } else if (newMode == ViewMode.FILTER_LIST) {
            menu.setSlotVisibility(false, false);
        } else {
            menu.setSlotVisibility(false, true);
        }
        this.clearWidgets();
        this.init();
    }

    @Override
    protected void init() {
        super.init();

        if (viewMode == ViewMode.MAIN) {
            menu.setSlotVisibility(true, true);
            initMainView();
        } else if (viewMode == ViewMode.FILTER_LIST) {
            menu.setSlotVisibility(false, false);
            initFilterListView();
        } else {
            menu.setSlotVisibility(false, true);
            initFilterEditView();
        }
        updateActionButtonsState();
    }

    private void initMainView() {
        int buttonX = leftPos + 87;

        Button uncraftButton = Button.builder(Component.literal("Uncraft"), b -> {
            PacketDistributor.sendToServer(new UncraftingActionPayload(menu.blockEntity.getBlockPos()));
        }).bounds(buttonX, topPos + 20, 70, 18)
          .tooltip(Tooltip.create(Component.literal("Uncraft the active item manually (0 FE — always free!).")))
          .build();
        addRenderableWidget(uncraftButton);

        Button autoUncraftButton = Button.builder(Component.literal(isAuto ? "Auto: ON" : "Auto: OFF"), b -> {
            isAuto = !isAuto;
            menu.blockEntity.autoUncraft = isAuto;
            b.setMessage(Component.literal(isAuto ? "Auto: ON" : "Auto: OFF"));
            sendSettings();
        }).bounds(buttonX, topPos + 40, 70, 18)
          .tooltip(Tooltip.create(Component.literal("Toggle automatic uncrafting (uses Forge Energy / FE, e.g. from Powah).")))
          .build();
        addRenderableWidget(autoUncraftButton);

        int ruleCount = menu.blockEntity.getRecipeRules().size();
        Button recipesButton = Button.builder(Component.literal("Recipes (" + ruleCount + ")"), b -> {
            switchView(ViewMode.FILTER_LIST);
        }).bounds(buttonX, topPos + 62, 70, 20)
          .tooltip(Tooltip.create(Component.literal("Configure custom output recipe filters (Pipez-style list for storage automation).")))
          .build();
        addRenderableWidget(recipesButton);

        AbstractButton cogButton = new AbstractButton(leftPos + 7, topPos + 5, 12, 12, Component.literal("⚙")) {
            @Override
            public void onPress() {
                showSettingsPanel = !showSettingsPanel;
                UncraftingTableScreen.this.clearWidgets();
                UncraftingTableScreen.this.init();
            }

            @Override
            protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
                int color = (this.isHoveredOrFocused() || showSettingsPanel) ? 0xFFD700 : 0xFFFFFF;
                guiGraphics.drawString(font, this.getMessage(), this.getX(), this.getY(), color, false);
            }

            @Override
            protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
                this.defaultButtonNarrationText(narrationElementOutput);
            }
        };
        cogButton.setTooltip(Tooltip.create(Component.literal("Audio & Particle Settings")));
        addRenderableWidget(cogButton);

        for (int i = 0; i < 4; i++) {
            final int index = i;
            AbstractButton cb = new AbstractButton(leftPos + 164 + i * 18 + 4, topPos + 123, 8, 8, Component.literal("")) {
                @Override
                public void onPress() {
                    if (index == 0) { enableGrindstone = !enableGrindstone; menu.blockEntity.enableGrindstone = enableGrindstone; }
                    else if (index == 1) { enableSmoker = !enableSmoker; menu.blockEntity.enableSmoker = enableSmoker; }
                    else if (index == 2) { enableBlastFurnace = !enableBlastFurnace; menu.blockEntity.enableBlastFurnace = enableBlastFurnace; }
                    else if (index == 3) { enableBrewingStand = !enableBrewingStand; menu.blockEntity.enableBrewingStand = enableBrewingStand; }
                    sendSettings();
                }

                @Override
                protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
                    boolean enabled = index == 0 ? enableGrindstone : index == 1 ? enableSmoker : index == 2 ? enableBlastFurnace : enableBrewingStand;
                    guiGraphics.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, 0xFF222222);
                    int innerColor = enabled ? 0xFF444444 : 0xFFCCCCCC;
                    if (this.isHoveredOrFocused()) {
                        innerColor = enabled ? 0xFF666666 : 0xFFFFFFFF;
                    }
                    guiGraphics.fill(this.getX() + 1, this.getY() + 1, this.getX() + this.width - 1, this.getY() + this.height - 1, innerColor);
                }

                @Override
                protected void updateWidgetNarration(NarrationElementOutput output) {
                    this.defaultButtonNarrationText(output);
                }
            };
            if (index == 0) cb.setTooltip(Tooltip.create(Component.literal("Toggle Grindstone Requirement")));
            else if (index == 1) cb.setTooltip(Tooltip.create(Component.literal("Toggle Smoker Requirement")));
            else if (index == 2) cb.setTooltip(Tooltip.create(Component.literal("Toggle Blast Furnace Requirement")));
            else if (index == 3) cb.setTooltip(Tooltip.create(Component.literal("Toggle Brewing Stand Requirement")));
            addRenderableWidget(cb);
        }

        if (showSettingsPanel) {
            int panelX = leftPos - 82;

            Button soundButton = Button.builder(Component.literal(muteSounds ? "Sounds: OFF" : "Sounds: ON"), b -> {
                muteSounds = !muteSounds;
                menu.blockEntity.muteSounds = muteSounds;
                b.setMessage(Component.literal(muteSounds ? "Sounds: OFF" : "Sounds: ON"));
                sendSettings();
            }).bounds(panelX + 2, topPos + 10, 78, 16)
              .tooltip(Tooltip.create(Component.literal("Toggle uncrafting sound effects.")))
              .build();
            addRenderableWidget(soundButton);

            Button particleButton = Button.builder(Component.literal(muteParticles ? "Particles: OFF" : "Particles: ON"), b -> {
                muteParticles = !muteParticles;
                menu.blockEntity.muteParticles = muteParticles;
                b.setMessage(Component.literal(muteParticles ? "Particles: OFF" : "Particles: ON"));
                sendSettings();
            }).bounds(panelX + 2, topPos + 30, 78, 16)
              .tooltip(Tooltip.create(Component.literal("Toggle uncrafting particle effects.")))
              .build();
            addRenderableWidget(particleButton);
        }
    }

    private void initFilterListView() {
        List<RecipeRule> currentRules = menu.blockEntity.getRecipeRules();
        if (!currentRules.isEmpty() && (selectedRuleIdx < 0 || selectedRuleIdx >= currentRules.size())) {
            selectedRuleIdx = 0;
        }
        Button backBtn = Button.builder(Component.literal("< Back"), b -> {
            switchView(ViewMode.MAIN);
        }).bounds(leftPos + 8, topPos + 5, 44, 16).build();
        addRenderableWidget(backBtn);

        Button addBtn = Button.builder(Component.literal("Add"), b -> {
            setGhostItem(ItemStack.EMPTY);
            switchView(ViewMode.FILTER_EDIT);
        }).bounds(leftPos + 192, topPos + 28, 56, 20)
          .tooltip(Tooltip.create(Component.literal("Add a new custom output recipe rule.")))
          .build();
        addRenderableWidget(addBtn);

        editRuleBtn = Button.builder(Component.literal("Edit"), b -> {
            openSelectedRuleForEdit();
        }).bounds(leftPos + 192, topPos + 52, 56, 20)
          .tooltip(Tooltip.create(Component.literal("Edit the selected recipe rule.")))
          .build();
        addRenderableWidget(editRuleBtn);

        removeRuleBtn = Button.builder(Component.literal("Remove"), b -> {
            List<RecipeRule> rules = menu.blockEntity.getRecipeRules();
            if (selectedRuleIdx >= 0 && selectedRuleIdx < rules.size()) {
                RecipeRule target = rules.get(selectedRuleIdx);
                menu.blockEntity.removeRecipeRule(target.itemId());
                PacketDistributor.sendToServer(new UncraftingSelectionPayload(
                        menu.blockEntity.getBlockPos(),
                        UncraftingSelectionPayload.Action.REMOVE,
                        target.itemId(),
                        target.recipeId(),
                        List.of()
                ));
                if (selectedRuleIdx >= menu.blockEntity.getRecipeRules().size()) {
                    selectedRuleIdx = menu.blockEntity.getRecipeRules().size() - 1;
                }
                listScroll = Math.max(0, Math.min(listScroll, Math.max(0, menu.blockEntity.getRecipeRules().size() - LIST_VISIBLE)));
                updateActionButtonsState();
            }
        }).bounds(leftPos + 192, topPos + 76, 56, 20)
          .tooltip(Tooltip.create(Component.literal("Remove the selected rule (reverts to default).")))
          .build();
        addRenderableWidget(removeRuleBtn);

        Button policyBtn = Button.builder(Component.literal(getPolicyLabel()), b -> {
            waitForUnconfiguredChoice = !waitForUnconfiguredChoice;
            menu.blockEntity.waitForUnconfiguredChoice = waitForUnconfiguredChoice;
            b.setMessage(Component.literal(getPolicyLabel()));
            sendSettings();
        }).bounds(leftPos + 8, topPos + 199, 240, 20)
          .tooltip(Tooltip.create(Component.literal(
                  "Default: Auto mode uncrafts unlisted multi-recipe items using the default recipe.\n" +
                  "Wait for Filter: Auto mode pauses unlisted multi-recipe items from storage until you add a rule here.")))
          .build();
        addRenderableWidget(policyBtn);
    }

    private String getPolicyLabel() {
        return waitForUnconfiguredChoice
                ? "Unset Multi-Recipe: Wait for Filter"
                : "Unset Multi-Recipe: Auto Default";
    }

    private void initFilterEditView() {
        Button cancelBtn = Button.builder(Component.literal("Cancel"), b -> {
            switchView(ViewMode.FILTER_LIST);
        }).bounds(leftPos + 8, topPos + 5, 48, 16).build();
        addRenderableWidget(cancelBtn);

        saveRuleBtn = Button.builder(Component.literal("Save"), b -> {
            saveCurrentFilterAndReturn();
        }).bounds(leftPos + 200, topPos + 5, 48, 16)
          .tooltip(Tooltip.create(Component.literal("Save this output recipe rule to the table.")))
          .build();
        addRenderableWidget(saveRuleBtn);
    }

    private void openSelectedRuleForEdit() {
        List<RecipeRule> rules = menu.blockEntity.getRecipeRules();
        if (selectedRuleIdx < 0 || selectedRuleIdx >= rules.size()) return;
        RecipeRule rule = rules.get(selectedRuleIdx);
        Item item = BuiltInRegistries.ITEM.get(rule.itemId());
        if (item != Items.AIR) {
            this.ghostStack = new ItemStack(item);
            rebuildEditOptions(rule);
            switchView(ViewMode.FILTER_EDIT);
        }
    }

    private void saveCurrentFilterAndReturn() {
        if (ghostStack.isEmpty() || selectedOptionIdx < 0 || selectedOptionIdx >= editOptions.size()) {
            return;
        }
        ConcreteOutput chosen = editOptions.get(selectedOptionIdx);
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(ghostStack.getItem());
        List<ResourceLocation> matIds;
        if (chosen.hasInlineGroupSwatches()) {
            List<IngredientGroup> groups = chosen.sourceRecipe().groups();
            matIds = new ArrayList<>(groups.size());
            for (int g = 0; g < groups.size(); g++) {
                int vIdx = g < inlineVariantIndices.length ? inlineVariantIndices[g] : 0;
                ItemStack picked = groups.get(g).pickVariant(vIdx);
                matIds.add(BuiltInRegistries.ITEM.getKey(picked.getItem()));
            }
        } else {
            matIds = chosen.chosenMaterialIds();
        }

        menu.blockEntity.saveRecipeRule(itemId, chosen.recipeId(), matIds);
        this.inputStatusDirty = true;
        this.rulePreviewCache.clear();
        PacketDistributor.sendToServer(new UncraftingSelectionPayload(
                menu.blockEntity.getBlockPos(),
                UncraftingSelectionPayload.Action.SAVE,
                itemId,
                chosen.recipeId(),
                matIds
        ));

        // Select the newly saved rule in the list
        List<RecipeRule> updated = menu.blockEntity.getRecipeRules();
        for (int i = 0; i < updated.size(); i++) {
            if (updated.get(i).itemId().equals(itemId)) {
                selectedRuleIdx = i;
                break;
            }
        }
        switchView(ViewMode.FILTER_LIST);
    }

    private void updateActionButtonsState() {
        if (viewMode == ViewMode.FILTER_LIST) {
            int count = menu.blockEntity.getRecipeRules().size();
            boolean valid = selectedRuleIdx >= 0 && selectedRuleIdx < count;
            if (editRuleBtn != null) editRuleBtn.active = valid;
            if (removeRuleBtn != null) removeRuleBtn.active = valid;
        } else if (viewMode == ViewMode.FILTER_EDIT) {
            if (saveRuleBtn != null) {
                saveRuleBtn.active = !ghostStack.isEmpty()
                        && selectedOptionIdx >= 0
                        && selectedOptionIdx < editOptions.size();
            }
        }
    }

    private void sendSettings() {
        PacketDistributor.sendToServer(new UncraftingSettingsPayload(
                menu.blockEntity.getBlockPos(),
                isAuto,
                0,
                muteSounds,
                muteParticles,
                enableGrindstone,
                enableSmoker,
                enableBlastFurnace,
                enableBrewingStand,
                waitForUnconfiguredChoice
        ));
    }

    @Override
    protected void slotClicked(Slot slot, int slotId, int mouseButton, ClickType type) {
        // In FILTER_EDIT, Shift-Clicking an item in Player Inventory loads it into the Ghost Slot safely
        if (viewMode == ViewMode.FILTER_EDIT && slot != null && type == ClickType.QUICK_MOVE && slot.hasItem()) {
            setGhostItem(slot.getItem());
            if (minecraft != null) {
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.1F));
            }
            return;
        }
        super.slotClicked(slot, slotId, mouseButton, type);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int mx = (int) mouseX;
        int my = (int) mouseY;

        if (viewMode == ViewMode.FILTER_LIST) {
            List<RecipeRule> rules = menu.blockEntity.getRecipeRules();
            if (isHovering(LIST_X, LIST_Y, LIST_W, LIST_VISIBLE * LIST_ROW_H, mx, my)) {
                int relY = my - (topPos + LIST_Y);
                int idx = listScroll + (relY / LIST_ROW_H);
                if (idx >= 0 && idx < rules.size()) {
                    long now = System.currentTimeMillis();
                    if (idx == lastClickedRuleIdx && (now - lastListClickMs) < 350L) {
                        selectedRuleIdx = idx;
                        openSelectedRuleForEdit();
                        return true;
                    }
                    selectedRuleIdx = idx;
                    lastClickedRuleIdx = idx;
                    lastListClickMs = now;
                    updateActionButtonsState();
                    if (minecraft != null) {
                        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    }
                    return true;
                }
            }
        } else if (viewMode == ViewMode.FILTER_EDIT) {
            // Ghost Item Drop Slot click
            if (isHovering(GHOST_X, GHOST_Y, 18, 18, mx, my)) {
                ItemStack carried = menu.getCarried();
                if (!carried.isEmpty()) {
                    setGhostItem(carried);
                    if (minecraft != null) {
                        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.1F));
                    }
                } else if (button == 1) {
                    setGhostItem(ItemStack.EMPTY);
                }
                return true;
            }

            // Output Recipe Option rows click
            if (!editOptions.isEmpty() && isHovering(OPT_X, OPT_Y, OPT_W, OPT_VISIBLE * OPT_ROW_H, mx, my)) {
                int relY = my - (topPos + OPT_Y);
                int rowIdx = editScroll + (relY / OPT_ROW_H);
                if (rowIdx >= 0 && rowIdx < editOptions.size()) {
                    ConcreteOutput opt = editOptions.get(rowIdx);
                    if (opt.hasInlineGroupSwatches() && rowIdx == selectedOptionIdx) {
                        // Clicking an icon swatch inside a mega-tag row cycles that material group
                        List<IngredientGroup> groups = opt.sourceRecipe().groups();
                        int maxIcons = Math.min(7, groups.size());
                        for (int g = 0; g < maxIcons; g++) {
                            int iconX = OPT_X + 6 + g * 20;
                            int iconY = OPT_Y + (rowIdx - editScroll) * OPT_ROW_H + 3;
                            if (isHovering(iconX, iconY, 16, 16, mx, my) && groups.get(g).hasChoices()) {
                                int step = button == 1 ? -1 : 1;
                                if (inlineVariantIndices.length != groups.size()) {
                                    initInlineVariants(opt, List.of());
                                }
                                inlineVariantIndices[g] = Math.floorMod(inlineVariantIndices[g] + step, groups.get(g).variants().size());
                                if (minecraft != null) {
                                    minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.15F));
                                }
                                return true;
                            }
                        }
                    }

                    selectedOptionIdx = rowIdx;
                    if (opt.hasInlineGroupSwatches()) {
                        initInlineVariants(opt, List.of());
                    } else {
                        inlineVariantIndices = new int[0];
                    }
                    updateActionButtonsState();
                    if (minecraft != null) {
                        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    }
                    return true;
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int mx = (int) mouseX;
        int my = (int) mouseY;
        int dir = scrollY > 0 ? -1 : 1;

        if (viewMode == ViewMode.FILTER_LIST) {
            int maxScroll = Math.max(0, menu.blockEntity.getRecipeRules().size() - LIST_VISIBLE);
            if (maxScroll > 0 && isHovering(LIST_X, LIST_Y, LIST_W, LIST_VISIBLE * LIST_ROW_H, mx, my)) {
                listScroll = Math.max(0, Math.min(maxScroll, listScroll + dir));
                return true;
            }
        } else if (viewMode == ViewMode.FILTER_EDIT) {
            int maxScroll = Math.max(0, editOptions.size() - OPT_VISIBLE);
            if (maxScroll > 0 && isHovering(OPT_X, OPT_Y, OPT_W, OPT_VISIBLE * OPT_ROW_H, mx, my)) {
                editScroll = Math.max(0, Math.min(maxScroll, editScroll + dir));
                return true;
            }
        }

        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void drawPanelFrame(GuiGraphics guiGraphics, int px, int py, int pw, int ph) {
        guiGraphics.fill(px, py, px + pw, py + ph, 0xFF23272A);
        guiGraphics.fill(px, py, px + pw, py + 2, 0xFF32353A);
        guiGraphics.fill(px, py, px + 2, py + ph, 0xFF32353A);
        guiGraphics.fill(px, py + ph - 2, px + pw, py + ph, 0xFF141618);
        guiGraphics.fill(px + pw - 2, py, px + pw, py + ph, 0xFF141618);
    }

    private void drawRecessedBox(GuiGraphics guiGraphics, int x, int y, int w, int h) {
        guiGraphics.fill(x, y, x + w, y + h, 0xFF18191C);
        guiGraphics.fill(x, y, x + w, y + 1, 0xFF0F1012);
        guiGraphics.fill(x, y, x + 1, y + h, 0xFF0F1012);
        guiGraphics.fill(x, y + h - 1, x + w, y + h, 0xFF2D3034);
        guiGraphics.fill(x + w - 1, y, x + w, y + h, 0xFF2D3034);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        if (viewMode == ViewMode.MAIN) {
            renderMainBg(guiGraphics, mouseX, mouseY);
        } else if (viewMode == ViewMode.FILTER_LIST) {
            renderFilterListBg(guiGraphics, mouseX, mouseY);
        } else {
            renderFilterEditBg(guiGraphics, mouseX, mouseY);
        }
    }

    private void renderMainBg(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);

        if (showSettingsPanel) {
            int px = leftPos - 84;
            int py = topPos + 5;
            int pw = 84;
            int ph = 51;

            guiGraphics.fill(px + 1, py + 1, px + pw - 1, py + ph - 1, 0xFF23272A);
            guiGraphics.fill(px + 1, py + 1, px + pw - 1, py + 2, 0xFF32353A);
            guiGraphics.fill(px + 1, py + 1, px + 2, py + ph - 1, 0xFF32353A);
            guiGraphics.fill(px + 1, py + ph - 2, px + pw - 1, py + ph - 1, 0xFF141618);
            guiGraphics.fill(px + pw - 2, py + 1, px + pw - 1, py + ph - 1, 0xFF141618);
            guiGraphics.fill(px, py, px + pw, py + 1, 0xFF000000);
            guiGraphics.fill(px, py + ph - 1, px + pw, py + ph, 0xFF000000);
            guiGraphics.fill(px, py, px + 1, py + ph, 0xFF000000);
        }

        // Station modifier boxes: brighten when occupied, show dimmed ghost when empty
        for (int i = 0; i < MODIFIER_GHOSTS.length; i++) {
            int sx = leftPos + 164 + i * 18;
            int sy = topPos + 105;
            boolean present = !menu.getSlot(UncraftingTableBlockEntity.GRINDSTONE_SLOT + i).getItem().isEmpty();
            if (present) {
                guiGraphics.fill(sx - 1, sy - 1, sx + 17, sy + 17, 0xFF4ADE80);
                guiGraphics.fill(sx, sy, sx + 16, sy + 16, 0xFF2D5E3C);
                guiGraphics.fill(sx, sy, sx + 16, sy + 1, 0xFF3E8053);
                guiGraphics.fill(sx, sy, sx + 1, sy + 16, 0xFF3E8053);
            } else {
                guiGraphics.renderFakeItem(MODIFIER_GHOSTS[i], sx, sy);
            }
        }

        // Dim overlay shade over empty station ghost icons
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0, 0, 250.0F);
        for (int i = 0; i < MODIFIER_GHOSTS.length; i++) {
            if (menu.getSlot(UncraftingTableBlockEntity.GRINDSTONE_SLOT + i).getItem().isEmpty()) {
                int sx = leftPos + 164 + i * 18;
                int sy = topPos + 105;
                guiGraphics.fill(sx, sy, sx + 16, sy + 16, 0x66000000);
            }
        }
        guiGraphics.pose().popPose();

        // FE Energy Bar
        int energy = menu.blockEntity.energyStorage.getEnergyStored();
        int maxEnergy = menu.blockEntity.energyStorage.getCapacity();
        int barX = leftPos + 240;
        int barY = topPos + 20;
        int barWidth = 6;
        int barHeight = 72;
        int fillHeight = maxEnergy > 0 ? (int) ((energy / (float) maxEnergy) * barHeight) : 0;
        int fillY = barY + barHeight - fillHeight;
        guiGraphics.fill(barX, fillY, barX + barWidth, barY + barHeight, 0xFFCC0000);

        guiGraphics.pose().pushPose();
        guiGraphics.pose().scale(0.55f, 0.55f, 1.0f);
        String energyText = energy + " / " + maxEnergy + " FE";
        int textWidth = font.width(energyText);
        float scaledX = (leftPos + 203) / 0.55f - textWidth / 2.0f;
        float scaledY = (topPos + 95) / 0.55f;
        guiGraphics.drawString(font, energyText, (int) scaledX, (int) scaledY, 0xFF4444, false);
        guiGraphics.pose().popPose();

        // Refresh cached multi-recipe corner dot colors on the 4x4 input grid
        refreshInputStatusIfNeeded();

        // Corner status indicators on 4x4 Input slots using precomputed colors
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0, 0, 260.0F);
        for (int i = 0; i < UncraftingTableBlockEntity.INPUT_SLOTS; i++) {
            int dotColor = cachedInputDotColors[i];
            if (dotColor == 0) continue;
            int col = i % UncraftingTableBlockEntity.INPUT_COLS;
            int row = i / UncraftingTableBlockEntity.INPUT_COLS;
            int dotX = leftPos + 8 + col * 18 + 13;
            int dotY = topPos + 20 + row * 18;
            guiGraphics.fill(dotX, dotY, dotX + 3, dotY + 3, dotColor);
        }
        guiGraphics.pose().popPose();
    }

    private void refreshInputStatusIfNeeded() {
        if (minecraft == null || minecraft.level == null) return;
        boolean hasSmoker = menu.blockEntity.hasSmokerModifier();
        boolean hasBlast = menu.blockEntity.hasBlastFurnaceModifier();
        int ruleCount = menu.blockEntity.getRecipeRules().size();

        boolean changed = inputStatusDirty
                || hasSmoker != lastStatusSmoker
                || hasBlast != lastStatusBlast
                || waitForUnconfiguredChoice != lastStatusWait
                || ruleCount != lastStatusRuleCount;

        if (!changed) {
            for (int i = 0; i < UncraftingTableBlockEntity.INPUT_SLOTS; i++) {
                ItemStack cur = menu.blockEntity.itemHandler.getStackInSlot(UncraftingTableBlockEntity.INPUT_START_SLOT + i);
                if (!ItemStack.isSameItemSameComponents(lastInputSnapshots[i], cur)) {
                    changed = true;
                    break;
                }
            }
        }
        if (!changed) return;

        lastStatusSmoker = hasSmoker;
        lastStatusBlast = hasBlast;
        lastStatusWait = waitForUnconfiguredChoice;
        lastStatusRuleCount = ruleCount;
        inputStatusDirty = false;

        int waiting = 0;
        for (int i = 0; i < UncraftingTableBlockEntity.INPUT_SLOTS; i++) {
            ItemStack stack = menu.blockEntity.itemHandler.getStackInSlot(UncraftingTableBlockEntity.INPUT_START_SLOT + i);
            lastInputSnapshots[i] = stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1);
            cachedInputDotColors[i] = 0;
            if (stack.isEmpty() || stack.is(UncraftingTableBlockEntity.BLACKLIST_TAG)) continue;

            if (menu.blockEntity.hasRuleFor(stack)) {
                cachedInputDotColors[i] = 0xFF4ADE80;
            } else {
                List<ResolvedRecipe> recipes = UncraftingRecipeResolver.findRecipes(minecraft.level, stack, hasSmoker, hasBlast);
                if (UncraftingRecipeResolver.hasMultipleChoices(recipes)) {
                    cachedInputDotColors[i] = waitForUnconfiguredChoice ? 0xFFFACC15 : 0xFF38BDF8;
                    waiting++;
                }
            }
        }
        cachedWaitingMultiCount = waiting;
    }

    private void renderFilterListBg(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        drawPanelFrame(guiGraphics, leftPos, topPos, imageWidth, imageHeight);
        int lx = leftPos + LIST_X;
        int ly = topPos + LIST_Y;
        int lh = LIST_VISIBLE * LIST_ROW_H + 4;
        drawRecessedBox(guiGraphics, lx, ly, LIST_W, lh);

        List<RecipeRule> rules = menu.blockEntity.getRecipeRules();
        if (rules.isEmpty()) {
            guiGraphics.drawCenteredString(font, Component.literal("No recipe filters yet").withStyle(ChatFormatting.GRAY),
                    lx + LIST_W / 2, ly + 60, 0xAAAAAA);
            guiGraphics.drawCenteredString(font, Component.literal("Click 'Add' to set an item's recipe").withStyle(ChatFormatting.DARK_GRAY),
                    lx + LIST_W / 2, ly + 74, 0x888888);
            return;
        }

        int maxScroll = Math.max(0, rules.size() - LIST_VISIBLE);
        if (listScroll > maxScroll) listScroll = maxScroll;

        for (int i = 0; i < LIST_VISIBLE && (listScroll + i) < rules.size(); i++) {
            int idx = listScroll + i;
            RecipeRule rule = rules.get(idx);
            int rx = lx + 2;
            int ry = ly + 2 + i * LIST_ROW_H;
            int rw = LIST_W - 8;

            boolean selected = idx == selectedRuleIdx;
            boolean hovered = isHovering(LIST_X + 2, LIST_Y + 2 + i * LIST_ROW_H, rw, LIST_ROW_H, mouseX, mouseY);
            int bg = selected ? 0xFF283A4A : (hovered ? 0xFF25292E : 0xFF1E2125);
            int border = selected ? 0xFF38BDF8 : 0xFF2A2E33;
            guiGraphics.fill(rx, ry, rx + rw, ry + LIST_ROW_H - 1, border);
            guiGraphics.fill(rx + 1, ry + 1, rx + rw - 1, ry + LIST_ROW_H - 2, bg);

            RulePreviewEntry preview = getCachedRulePreview(rule);
            ItemStack targetStack = preview.targetStack();
            guiGraphics.renderFakeItem(targetStack, rx + 4, ry + 7);

            guiGraphics.pose().pushPose();
            guiGraphics.pose().scale(0.75f, 0.75f, 1.0f);
            String title = font.plainSubstrByWidth(targetStack.getHoverName().getString(), 110);
            guiGraphics.drawString(font, title, (int) ((rx + 24) / 0.75f), (int) ((ry + 3) / 0.75f), 0xFFFFFFFF, false);
            guiGraphics.pose().popPose();

            // Render chosen output icons cleanly
            List<ItemStack> outPreview = preview.outputs();
            guiGraphics.drawString(font, "->", rx + 23, ry + 14, 0xFF4ADE80, false);
            int maxIcons = Math.min(5, outPreview.size());
            for (int o = 0; o < maxIcons; o++) {
                ItemStack outStack = outPreview.get(o);
                int ox = rx + 36 + o * 18;
                int oy = ry + 12;
                guiGraphics.renderFakeItem(outStack, ox, oy);
                if (outStack.getCount() > 1) {
                    guiGraphics.pose().pushPose();
                    guiGraphics.pose().translate(0, 0, 260.0F);
                    guiGraphics.pose().scale(0.65f, 0.65f, 1.0f);
                    String c = String.valueOf(outStack.getCount());
                    guiGraphics.drawString(font, c, (int) ((ox + 15) / 0.65f - font.width(c)), (int) ((oy + 9) / 0.65f), 0xFFFFFFFF, true);
                    guiGraphics.pose().popPose();
                }
            }
        }

        // Scrollbar
        if (maxScroll > 0) {
            int barX = lx + LIST_W - 5;
            int barY = ly + 2;
            int barH = LIST_VISIBLE * LIST_ROW_H;
            guiGraphics.fill(barX, barY, barX + 3, barY + barH, 0xFF121417);
            int thumbH = Math.max(16, (barH * LIST_VISIBLE) / rules.size());
            int thumbY = barY + ((barH - thumbH) * listScroll) / maxScroll;
            guiGraphics.fill(barX, thumbY, barX + 3, thumbY + thumbH, 0xFF64748B);
        }
    }

    private RulePreviewEntry getCachedRulePreview(RecipeRule rule) {
        boolean hasSmoker = menu.blockEntity.hasSmokerModifier();
        boolean hasBlast = menu.blockEntity.hasBlastFurnaceModifier();
        RulePreviewEntry cached = rulePreviewCache.get(rule.itemId());
        if (cached != null && cached.rule().equals(rule) && cached.hasSmoker() == hasSmoker && cached.hasBlast() == hasBlast) {
            return cached;
        }
        ItemStack targetStack = new ItemStack(BuiltInRegistries.ITEM.get(rule.itemId()));
        List<ItemStack> outputs = resolveRulePreviewOutputs(targetStack, rule);
        RulePreviewEntry created = new RulePreviewEntry(rule, hasSmoker, hasBlast, targetStack, List.copyOf(outputs));
        rulePreviewCache.put(rule.itemId(), created);
        return created;
    }

    private List<ItemStack> resolveRulePreviewOutputs(ItemStack targetStack, RecipeRule rule) {
        List<ItemStack> result = new ArrayList<>();
        if (minecraft != null && minecraft.level != null && !targetStack.isEmpty()) {
            List<ResolvedRecipe> recipes = UncraftingRecipeResolver.findRecipes(
                    minecraft.level,
                    targetStack,
                    menu.blockEntity.hasSmokerModifier(),
                    menu.blockEntity.hasBlastFurnaceModifier()
            );
            for (ResolvedRecipe r : recipes) {
                if (r.id().equals(rule.recipeId())) {
                    List<IngredientGroup> groups = r.groups();
                    for (int g = 0; g < groups.size(); g++) {
                        int vIdx = 0;
                        if (g < rule.chosenMaterialIds().size()) {
                            ResourceLocation matId = rule.chosenMaterialIds().get(g);
                            List<ItemStack> vars = groups.get(g).variants();
                            for (int v = 0; v < vars.size(); v++) {
                                if (BuiltInRegistries.ITEM.getKey(vars.get(v).getItem()).equals(matId)) {
                                    vIdx = v;
                                    break;
                                }
                            }
                        }
                        result.add(groups.get(g).pickVariant(vIdx).copyWithCount(groups.get(g).count()));
                    }
                    return result;
                }
            }
        }
        // Fallback: render saved material IDs directly
        for (ResourceLocation matId : rule.chosenMaterialIds()) {
            Item item = BuiltInRegistries.ITEM.get(matId);
            if (item != Items.AIR) {
                result.add(new ItemStack(item));
            }
        }
        return result;
    }

    private void renderFilterEditBg(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        drawPanelFrame(guiGraphics, leftPos, topPos, imageWidth, 136);
        // Blit exact Player Inventory + Hotbar background from container texture (y=133..228)
        guiGraphics.blit(TEXTURE, leftPos, topPos + 133, 0, 133, imageWidth, imageHeight - 133, 256, 256);

        // Ghost Item Slot (18x18 at GHOST_X, GHOST_Y)
        int gx = leftPos + GHOST_X;
        int gy = topPos + GHOST_Y;
        drawRecessedBox(guiGraphics, gx, gy, 18, 18);
        if (isHovering(GHOST_X, GHOST_Y, 18, 18, mouseX, mouseY)) {
            guiGraphics.fill(gx + 1, gy + 1, gx + 17, gy + 17, 0x3338BDF8);
        }

        if (!ghostStack.isEmpty()) {
            guiGraphics.renderFakeItem(ghostStack, gx + 1, gy + 1);
            guiGraphics.drawString(font,
                    font.plainSubstrByWidth(ghostStack.getHoverName().getString(), 185),
                    gx + 24, gy + 2, 0xFFFFFFFF, false);
            guiGraphics.pose().pushPose();
            guiGraphics.pose().scale(0.7f, 0.7f, 1.0f);
            String sub = editOptions.isEmpty()
                    ? "No uncrafting recipes found"
                    : editOptions.size() + " output option" + (editOptions.size() == 1 ? "" : "s") + " found — pick one below:";
            guiGraphics.drawString(font, sub, (int) ((gx + 24) / 0.7f), (int) ((gy + 12) / 0.7f), 0xFF86EFAC, false);
            guiGraphics.pose().popPose();
        } else {
            guiGraphics.drawString(font, "Target Item", gx + 24, gy + 1, 0xFFD1D5DB, false);
            guiGraphics.pose().pushPose();
            guiGraphics.pose().scale(0.68f, 0.68f, 1.0f);
            guiGraphics.drawString(font, "Click with item, Shift+Click inv, or drag from JEI",
                    (int) ((gx + 24) / 0.68f), (int) ((gy + 11) / 0.68f), 0xFF9CA3AF, false);
            guiGraphics.pose().popPose();
        }

        // Selectable Output Recipes Box
        int ox = leftPos + OPT_X;
        int oy = topPos + OPT_Y;
        int oh = OPT_VISIBLE * OPT_ROW_H + 4;
        drawRecessedBox(guiGraphics, ox, oy, OPT_W, oh);

        if (ghostStack.isEmpty() || editOptions.isEmpty()) {
            return;
        }

        int maxScroll = Math.max(0, editOptions.size() - OPT_VISIBLE);
        if (editScroll > maxScroll) editScroll = maxScroll;

        for (int i = 0; i < OPT_VISIBLE && (editScroll + i) < editOptions.size(); i++) {
            int idx = editScroll + i;
            ConcreteOutput opt = editOptions.get(idx);
            int rx = ox + 2;
            int ry = oy + 2 + i * OPT_ROW_H;
            int rw = OPT_W - 8;

            boolean selected = idx == selectedOptionIdx;
            boolean hovered = isHovering(OPT_X + 2, OPT_Y + 2 + i * OPT_ROW_H, rw, OPT_ROW_H, mouseX, mouseY);
            int border = selected ? 0xFF4ADE80 : (hovered ? 0xFF64748B : 0xFF282C32);
            int bg = selected ? 0xFF1D3324 : (hovered ? 0xFF252A30 : 0xFF1B1E22);
            guiGraphics.fill(rx, ry, rx + rw, ry + OPT_ROW_H - 1, border);
            guiGraphics.fill(rx + 1, ry + 1, rx + rw - 1, ry + OPT_ROW_H - 2, bg);

            // Render output stacks
            List<ItemStack> displayStacks = getDisplayStacksForOption(opt, selected);
            int maxIcons = Math.min(7, displayStacks.size());
            for (int k = 0; k < maxIcons; k++) {
                ItemStack st = displayStacks.get(k);
                int ix = rx + 4 + k * 20;
                int iy = ry + 1;
                if (opt.hasInlineGroupSwatches() && selected
                        && k < opt.sourceRecipe().groups().size()
                        && opt.sourceRecipe().groups().get(k).hasChoices()) {
                    guiGraphics.fill(ix - 1, iy, ix + 17, iy + 17, 0xFF38BDF8);
                }
                guiGraphics.renderFakeItem(st, ix, iy);
                if (st.getCount() > 1) {
                    guiGraphics.pose().pushPose();
                    guiGraphics.pose().translate(0, 0, 260.0F);
                    guiGraphics.pose().scale(0.7f, 0.7f, 1.0f);
                    String c = String.valueOf(st.getCount());
                    guiGraphics.drawString(font, c, (int) ((ix + 16) / 0.7f - font.width(c)), (int) ((iy + 9) / 0.7f), 0xFFFFFFFF, true);
                    guiGraphics.pose().popPose();
                }
            }

            // Right-aligned source tag (Crafting, Blasting, etc.)
            guiGraphics.pose().pushPose();
            guiGraphics.pose().scale(0.7f, 0.7f, 1.0f);
            String src = opt.sourceLabel() + (opt.hasInlineGroupSwatches() && selected ? " *" : "");
            int sw = font.width(src);
            guiGraphics.drawString(font, src, (int) ((rx + rw - 4) / 0.7f - sw),
                    (int) ((ry + 6) / 0.7f), selected ? 0xFF86EFAC : 0xFF9CA3AF, false);
            guiGraphics.pose().popPose();
        }

        if (maxScroll > 0) {
            int barX = ox + OPT_W - 5;
            int barY = oy + 2;
            int barH = OPT_VISIBLE * OPT_ROW_H;
            guiGraphics.fill(barX, barY, barX + 3, barY + barH, 0xFF121417);
            int thumbH = Math.max(12, (barH * OPT_VISIBLE) / editOptions.size());
            int thumbY = barY + ((barH - thumbH) * editScroll) / maxScroll;
            guiGraphics.fill(barX, thumbY, barX + 3, thumbY + thumbH, 0xFF64748B);
        }
    }

    private List<ItemStack> getDisplayStacksForOption(ConcreteOutput opt, boolean selected) {
        if (!opt.hasInlineGroupSwatches() || !selected) {
            return opt.outputs();
        }
        List<IngredientGroup> groups = opt.sourceRecipe().groups();
        List<ItemStack> list = new ArrayList<>(groups.size());
        for (int g = 0; g < groups.size(); g++) {
            int vIdx = g < inlineVariantIndices.length ? inlineVariantIndices[g] : 0;
            list.add(groups.get(g).pickVariant(vIdx).copyWithCount(groups.get(g).count()));
        }
        return list;
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (viewMode == ViewMode.MAIN) {
            Component cleanTitle = Component.literal("Uncrafting Table");
            guiGraphics.drawString(font, cleanTitle, (imageWidth - font.width(cleanTitle)) / 2, 6, 0xFFFFFF, false);
            guiGraphics.drawString(font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, 0xFFFFFF, false);
            guiGraphics.drawString(font, "Input", 44 - (font.width("Input") / 2), 11, 0xAAAAAA, false);
            guiGraphics.drawString(font, "Output", 200 - (font.width("Output") / 2), 11, 0xAAAAAA, false);
            guiGraphics.drawString(font, "Books", 24 - (font.width("Books") / 2), 95, 0xAAAAAA, false);
            guiGraphics.drawString(font, "Speed", 60 - (font.width("Speed") / 2), 95, 0xAAAAAA, false);
        } else if (viewMode == ViewMode.FILTER_LIST) {
            Component title = Component.literal("Recipe Filters");
            guiGraphics.drawString(font, title, (imageWidth - font.width(title)) / 2, 9, 0xFFFFFF, false);
        } else {
            Component title = Component.literal("Configure Recipe");
            guiGraphics.drawString(font, title, (imageWidth - font.width(title)) / 2, 9, 0xFFFFFF, false);
            guiGraphics.drawString(font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, 0xFFFFFF, false);
        }
    }

    @Override
    public void containerTick() {
        super.containerTick();
        if (hasEntityStateChanged()) {
            syncFieldsFromEntity();
            this.clearWidgets();
            this.init();
        }
        if (viewMode == ViewMode.MAIN) {
            refreshInputStatusIfNeeded();
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderTooltip(guiGraphics, mouseX, mouseY);

        if (viewMode == ViewMode.MAIN) {
            renderMainTooltips(guiGraphics, mouseX, mouseY);
        } else if (viewMode == ViewMode.FILTER_LIST) {
            renderFilterListTooltips(guiGraphics, mouseX, mouseY);
        } else {
            renderFilterEditTooltips(guiGraphics, mouseX, mouseY);
        }
    }

    private void renderMainTooltips(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (this.isHovering(16, 105, 16, 16, mouseX, mouseY)
                && this.menu.getSlot(UncraftingTableBlockEntity.BOOK_SLOT).getItem().isEmpty()) {
            guiGraphics.renderTooltip(this.font, Component.literal("Book Slot (insert Books to extract Enchantments)"), mouseX, mouseY);
        }
        if (this.isHovering(52, 105, 16, 16, mouseX, mouseY)
                && this.menu.getSlot(UncraftingTableBlockEntity.UPGRADE_SLOT).getItem().isEmpty()) {
            guiGraphics.renderTooltip(this.font, Component.literal("Speed Card Slot (faster Auto-Uncrafting)"), mouseX, mouseY);
        }

        if (this.isHovering(164, 105, 16, 16, mouseX, mouseY)
                && this.menu.getSlot(UncraftingTableBlockEntity.GRINDSTONE_SLOT).getItem().isEmpty()) {
            guiGraphics.renderTooltip(this.font, Component.literal("Grindstone (Reverse Enchanting)"), mouseX, mouseY);
        }
        if (this.isHovering(182, 105, 16, 16, mouseX, mouseY)
                && this.menu.getSlot(UncraftingTableBlockEntity.SMOKER_SLOT).getItem().isEmpty()) {
            guiGraphics.renderTooltip(this.font, Component.literal("Smoker (Reverse Cooking)"), mouseX, mouseY);
        }
        if (this.isHovering(200, 105, 16, 16, mouseX, mouseY)
                && this.menu.getSlot(UncraftingTableBlockEntity.BLAST_FURNACE_SLOT).getItem().isEmpty()) {
            guiGraphics.renderTooltip(this.font, Component.literal("Blast Furnace (Reverse Blasting)"), mouseX, mouseY);
        }
        if (this.isHovering(218, 105, 16, 16, mouseX, mouseY)
                && this.menu.getSlot(UncraftingTableBlockEntity.BREWING_STAND_SLOT).getItem().isEmpty()) {
            guiGraphics.renderTooltip(this.font, Component.literal("Brewing Stand (Reverse Brewing)"), mouseX, mouseY);
        }

        if (this.isHovering(239, 19, 8, 74, mouseX, mouseY)) {
            int stored = menu.blockEntity.energyStorage.getEnergyStored();
            int cap = menu.blockEntity.energyStorage.getCapacity();
            int cost = menu.blockEntity.getEnergyCost();
            int delay = menu.blockEntity.getSpeedDelay();
            List<Component> lines = List.of(
                    Component.literal("Forge Energy: " + stored + " / " + cap + " FE").withStyle(ChatFormatting.RED),
                    Component.literal("Auto Cost: " + cost + " FE / every " + delay + " ticks").withStyle(ChatFormatting.GRAY),
                    Component.literal("Powers Auto: ON mode (Powah, Pipez, etc.)").withStyle(ChatFormatting.AQUA),
                    Component.literal("Manual 'Uncraft' button is always FREE!").withStyle(ChatFormatting.GREEN)
            );
            guiGraphics.renderComponentTooltip(this.font, lines, mouseX, mouseY);
        }
    }

    private void renderFilterListTooltips(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        List<RecipeRule> rules = menu.blockEntity.getRecipeRules();
        for (int i = 0; i < LIST_VISIBLE && (listScroll + i) < rules.size(); i++) {
            RecipeRule rule = rules.get(listScroll + i);
            int rx = LIST_X + 2;
            int ry = LIST_Y + 2 + i * LIST_ROW_H;
            RulePreviewEntry preview = getCachedRulePreview(rule);
            if (isHovering(rx + 4, ry + 7, 16, 16, mouseX, mouseY)) {
                guiGraphics.renderTooltip(this.font, preview.targetStack(), mouseX, mouseY);
                return;
            }
            List<ItemStack> outs = preview.outputs();
            int maxIcons = Math.min(5, outs.size());
            for (int o = 0; o < maxIcons; o++) {
                if (isHovering(rx + 36 + o * 18, ry + 12, 16, 16, mouseX, mouseY)) {
                    guiGraphics.renderTooltip(this.font, outs.get(o), mouseX, mouseY);
                    return;
                }
            }
        }
    }

    private void renderFilterEditTooltips(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (!ghostStack.isEmpty() && isHovering(GHOST_X + 1, GHOST_Y + 1, 16, 16, mouseX, mouseY)) {
            guiGraphics.renderTooltip(this.font, ghostStack, mouseX, mouseY);
            return;
        }
        if (ghostStack.isEmpty() || editOptions.isEmpty()) return;

        for (int i = 0; i < OPT_VISIBLE && (editScroll + i) < editOptions.size(); i++) {
            int idx = editScroll + i;
            ConcreteOutput opt = editOptions.get(idx);
            boolean selected = idx == selectedOptionIdx;
            List<ItemStack> displayStacks = getDisplayStacksForOption(opt, selected);
            int maxIcons = Math.min(7, displayStacks.size());
            for (int k = 0; k < maxIcons; k++) {
                int ix = OPT_X + 6 + k * 20;
                int iy = OPT_Y + 3 + i * OPT_ROW_H;
                if (isHovering(ix, iy, 16, 16, mouseX, mouseY)) {
                    ItemStack st = displayStacks.get(k);
                    List<Component> lines = new ArrayList<>();
                    lines.add(Component.literal(st.getHoverName().getString() + " x" + st.getCount()));
                    lines.add(Component.literal("Recipe: " + opt.recipeId()).withStyle(ChatFormatting.DARK_GRAY));
                    if (opt.hasInlineGroupSwatches() && selected
                            && k < opt.sourceRecipe().groups().size()
                            && opt.sourceRecipe().groups().get(k).hasChoices()) {
                        lines.add(Component.literal("Click icon to cycle material variant").withStyle(ChatFormatting.AQUA));
                    }
                    guiGraphics.renderComponentTooltip(this.font, lines, mouseX, mouseY);
                    return;
                }
            }
        }
    }

    // Package-private inspection helpers for automated client UI tests
    ViewMode getViewModeForTest() {
        return viewMode;
    }

    ItemStack getGhostStackForTest() {
        return ghostStack;
    }

    List<ConcreteOutput> getEditOptionsForTest() {
        return editOptions;
    }

    int getSelectedOptionIdxForTest() {
        return selectedOptionIdx;
    }

    boolean isShowingSettingsPanelForTest() {
        return showSettingsPanel;
    }

    boolean isStationSlotOccupiedForTest(int modifierIndex) {
        if (modifierIndex < 0 || modifierIndex >= MODIFIER_GHOSTS.length) {
            return false;
        }
        return !menu.getSlot(UncraftingTableBlockEntity.GRINDSTONE_SLOT + modifierIndex).getItem().isEmpty();
    }

    int getCachedWaitingMultiCountForTest() {
        return cachedWaitingMultiCount;
    }

    int getEditScrollForTest() {
        return editScroll;
    }

    void setEditScrollForTest(int scroll) {
        this.editScroll = Math.max(0, Math.min(Math.max(0, editOptions.size() - OPT_VISIBLE), scroll));
    }

    int getLeftPosForTest() {
        return leftPos;
    }

    int getTopPosForTest() {
        return topPos;
    }

    List<String> getWidgetLabelsForTest() {
        List<String> labels = new ArrayList<>();
        for (var listener : this.children()) {
            if (listener instanceof Button btn) {
                labels.add(btn.getMessage().getString());
            }
        }
        return labels;
    }
}

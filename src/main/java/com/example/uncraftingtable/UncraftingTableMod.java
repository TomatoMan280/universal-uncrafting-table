package com.example.uncraftingtable;

import com.example.uncraftingtable.block.UncraftingTableBlock;
import com.example.uncraftingtable.block.entity.UncraftingTableBlockEntity;
import com.example.uncraftingtable.menu.UncraftingTableMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.inventory.MenuType;
import com.example.uncraftingtable.item.SpeedCardItem;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(UncraftingTableMod.MOD_ID)
public final class UncraftingTableMod {
    public static final String MOD_ID = "uncrafting_table";

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MOD_ID);
    public static final DeferredRegister<MenuType<?>> MENU_TYPES = DeferredRegister.create(Registries.MENU, MOD_ID);

    public static final DeferredBlock<UncraftingTableBlock> UNCRAFTING_TABLE = BLOCKS.register(
            "uncrafting_table",
            () -> new UncraftingTableBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(1.5F))
    );

    public static final DeferredItem<BlockItem> UNCRAFTING_TABLE_ITEM = ITEMS.register(
            "uncrafting_table",
            () -> new BlockItem(UNCRAFTING_TABLE.get(), new Item.Properties())
    );

    public static final DeferredItem<Item> BASIC_SPEED_CARD = ITEMS.register(
            "basic_speed_card",
            () -> new SpeedCardItem(new Item.Properties(), 200, 20)
    );

    public static final DeferredItem<Item> ADVANCED_SPEED_CARD = ITEMS.register(
            "advanced_speed_card",
            () -> new SpeedCardItem(new Item.Properties(), 400, 10)
    );

    public static final DeferredItem<Item> ELITE_SPEED_CARD = ITEMS.register(
            "elite_speed_card",
            () -> new SpeedCardItem(new Item.Properties(), 600, 5)
    );

    public static final DeferredItem<Item> ULTIMATE_SPEED_CARD = ITEMS.register(
            "ultimate_speed_card",
            () -> new SpeedCardItem(new Item.Properties(), 800, 1)
    );

    public static final DeferredItem<Item> CREATIVE_SPEED_CARD = ITEMS.register(
            "creative_speed_card",
            () -> new SpeedCardItem(new Item.Properties(), 0, 1)
    );

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<UncraftingTableBlockEntity>> UNCRAFTING_TABLE_BLOCK_ENTITY =
            BLOCK_ENTITY_TYPES.register("uncrafting_table",
                    () -> BlockEntityType.Builder.of(UncraftingTableBlockEntity::new, UNCRAFTING_TABLE.get()).build(null));

    public static final DeferredHolder<MenuType<?>, MenuType<UncraftingTableMenu>> UNCRAFTING_TABLE_MENU =
            MENU_TYPES.register("uncrafting_table", () -> IMenuTypeExtension.create(UncraftingTableMenu::new));

    public UncraftingTableMod(IEventBus modEventBus) {
        
        // Register the deferred registers to the mod event bus
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITY_TYPES.register(modEventBus);
        MENU_TYPES.register(modEventBus);

        modEventBus.addListener(this::addCreativeTabItems);
        modEventBus.addListener(this::registerCapabilities);

        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(com.example.uncraftingtable.command.UncraftingTestCommand::onRegisterCommands);
    }

    private void addCreativeTabItems(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(UNCRAFTING_TABLE_ITEM);
            event.accept(BASIC_SPEED_CARD);
            event.accept(ADVANCED_SPEED_CARD);
            event.accept(ELITE_SPEED_CARD);
            event.accept(ULTIMATE_SPEED_CARD);
        } else if (event.getTabKey() == CreativeModeTabs.OP_BLOCKS) {
            event.accept(CREATIVE_SPEED_CARD);
        }
    }

    private void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                UNCRAFTING_TABLE_BLOCK_ENTITY.get(),
                (blockEntity, side) -> blockEntity.getItemHandler(side)
        );
        event.registerBlockEntity(
                Capabilities.EnergyStorage.BLOCK,
                UNCRAFTING_TABLE_BLOCK_ENTITY.get(),
                (blockEntity, side) -> blockEntity.energyStorage
        );
    }
}

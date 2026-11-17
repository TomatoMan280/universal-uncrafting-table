package com.example.uncraftingtable.block;

import com.example.uncraftingtable.block.entity.UncraftingTableBlockEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.items.IItemHandler;
import javax.annotation.Nullable;
import javax.annotation.Nullable;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import com.example.uncraftingtable.block.entity.UncraftingTableBlockEntity;

public class UncraftingTableBlock extends BaseEntityBlock {
    public static final MapCodec<UncraftingTableBlock> CODEC = simpleCodec(UncraftingTableBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public UncraftingTableBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rot) {
        return state.setValue(FACING, rot.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirrorIn) {
        return state.rotate(mirrorIn.getRotation(state.getValue(FACING)));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new UncraftingTableBlockEntity(pos, state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    private static final VoxelShape BASE_SHAPE = Shapes.or(
        Block.box(0, 9, 0, 16, 12, 16), // Table top
        Block.box(1, 0, 1, 4, 9, 4),    // Leg FL
        Block.box(12, 0, 1, 15, 9, 4),  // Leg FR
        Block.box(1, 0, 12, 4, 9, 15),  // Leg BL
        Block.box(12, 0, 12, 15, 9, 15) // Leg BR
    );

    private static final VoxelShape SHAPE_NORTH = Shapes.or(BASE_SHAPE,
        Block.box(12, 12, 1, 15, 14, 5), // Vice Base
        Block.box(11, 13, 2, 12, 15, 4), // Vice Jaw
        Block.box(15, 12.5, 2.5, 17, 13.5, 3.5) // Vice Handle
    );

    private static final VoxelShape SHAPE_EAST = Shapes.or(BASE_SHAPE,
        Block.box(11, 12, 12, 15, 14, 15),
        Block.box(12, 13, 11, 14, 15, 12),
        Block.box(12.5, 12.5, 15, 13.5, 13.5, 17)
    );

    private static final VoxelShape SHAPE_SOUTH = Shapes.or(BASE_SHAPE,
        Block.box(1, 12, 11, 4, 14, 15),
        Block.box(4, 13, 12, 5, 15, 14),
        Block.box(-1, 12.5, 12.5, 1, 13.5, 13.5)
    );

    private static final VoxelShape SHAPE_WEST = Shapes.or(BASE_SHAPE,
        Block.box(1, 12, 1, 5, 14, 4),
        Block.box(2, 13, 4, 4, 15, 5),
        Block.box(2.5, 12.5, -1, 3.5, 13.5, 1)
    );

    @Override
    protected VoxelShape getShape(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case EAST -> SHAPE_EAST;
            case SOUTH -> SHAPE_SOUTH;
            case WEST -> SHAPE_WEST;
            default -> SHAPE_NORTH;
        };
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            MenuProvider menuProvider = state.getMenuProvider(level, pos);
            if (menuProvider != null) {
                serverPlayer.openMenu(menuProvider, pos);
            }
        }

        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof UncraftingTableBlockEntity uncraftingTable) {
                IItemHandler items = uncraftingTable.itemHandler;
                for (int slot = 0; slot < items.getSlots(); slot++) {
                    ItemStack stack = items.getStackInSlot(slot);
                    if (!stack.isEmpty()) {
                        popResource(level, pos, stack);
                    }
                }
            }
        }

        super.onRemove(state, level, pos, newState, movedByPiston);
    }
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return (lvl, pos, blockState, blockEntity) -> {
            if (blockEntity instanceof UncraftingTableBlockEntity uncraftingTable) {
                uncraftingTable.tick();
            }
        };
    }
}

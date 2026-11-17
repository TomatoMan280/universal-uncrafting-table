package com.example.uncraftingtable.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

public class SpeedCardItem extends Item {
    private final int energyCost;
    private final int tickDelay;

    public SpeedCardItem(Properties properties, int energyCost, int tickDelay) {
        super(properties);
        this.energyCost = energyCost;
        this.tickDelay = tickDelay;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        if (energyCost > 0) {
            tooltipComponents.add(Component.literal("Energy Cost: " + energyCost + " FE/action").withStyle(ChatFormatting.RED));
        } else {
            tooltipComponents.add(Component.literal("Energy Cost: Free!").withStyle(ChatFormatting.AQUA));
        }

        if (tickDelay > 1) {
            tooltipComponents.add(Component.literal("Speed: 1 item every " + tickDelay + " ticks").withStyle(ChatFormatting.GREEN));
        } else if (tickDelay == 1) {
            tooltipComponents.add(Component.literal("Speed: 1 item per tick!").withStyle(ChatFormatting.GOLD));
        } else {
            tooltipComponents.add(Component.literal("Speed: Instantaneous!").withStyle(ChatFormatting.GOLD));
        }
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
    }
}

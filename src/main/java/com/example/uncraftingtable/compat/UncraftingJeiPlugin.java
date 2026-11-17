package com.example.uncraftingtable.compat;

import com.example.uncraftingtable.UncraftingTableMod;
import com.example.uncraftingtable.client.UncraftingTableScreen;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

import java.util.List;
import java.util.Optional;

@JeiPlugin
public class UncraftingJeiPlugin implements IModPlugin {

    private static final ResourceLocation UID =
            ResourceLocation.fromNamespaceAndPath(UncraftingTableMod.MOD_ID, "jei_plugin");

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }
        registerClientGhostHandler(registration);
    }

    /**
     * Registers JEI ghost-slot drop support for the Filter Edit view (client-only).
     */
    public static void registerClientGhostHandler(IGuiHandlerRegistration registration) {
        ClientGhostHandler.register(registration);
    }

    private static final class ClientGhostHandler {
        private static void register(IGuiHandlerRegistration registration) {
            registration.addGhostIngredientHandler(UncraftingTableScreen.class, new IGhostIngredientHandler<>() {
                @Override
                public <I> List<Target<I>> getTargetsTyped(UncraftingTableScreen gui, ITypedIngredient<I> ingredient, boolean doStart) {
                    if (!gui.isFilterEditOpen()) {
                        return List.of();
                    }
                    Optional<ItemStack> stackOpt = ingredient.getItemStack();
                    if (stackOpt.isEmpty() || stackOpt.get().isEmpty()) {
                        return List.of();
                    }
                    Rect2i area = gui.getGhostSlotBounds();
                    return List.of(new Target<>() {
                        @Override
                        public Rect2i getArea() {
                            return area;
                        }

                        @Override
                        public void accept(I ing) {
                            gui.setGhostItem(stackOpt.get());
                        }
                    });
                }

                @Override
                public void onComplete() {
                }
            });
        }
    }
}

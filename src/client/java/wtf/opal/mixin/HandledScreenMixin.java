package wtf.opal.mixin;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import wtf.opal.client.OpalClient;
import wtf.opal.client.feature.module.impl.utility.inventory.ChestStealerModule;
import wtf.opal.event.EventDispatcher;
import wtf.opal.event.impl.game.inventory.ManualInventoryInteractionEvent;

import java.awt.*;
import java.util.Map;

import static wtf.opal.client.Constants.mc;

@Mixin(HandledScreen.class)
public abstract class HandledScreenMixin<T extends ScreenHandler> {

    @Shadow @Final protected T handler;

    @Inject(
            method = "onMouseClick(Lnet/minecraft/screen/slot/Slot;IILnet/minecraft/screen/slot/SlotActionType;)V",
            at = @At("HEAD")
    )
    private void hookManualInventoryInteraction(final Slot slot, final int slotId, final int button,
                                                final SlotActionType actionType, final CallbackInfo ci) {
        EventDispatcher.dispatch(new ManualInventoryInteractionEvent(this.handler.syncId, slotId, actionType));
    }

    @Inject(
            method = "drawSlot",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;drawStackOverlay(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/item/ItemStack;IILjava/lang/String;)V")
    )
    private void hookStackOverlay(DrawContext context, Slot slot, CallbackInfo ci) {
        if (!(mc.currentScreen instanceof GenericContainerScreen container)) return;
        if (!(this.handler instanceof GenericContainerScreenHandler containerHandler)) return;
        if (mc.player.getInventory() == null || slot.inventory == mc.player.getInventory()) return;

        final ItemStack stack = slot.getStack();

        final Inventory chestInventory = containerHandler.getInventory();

        final ChestStealerModule chestStealer = OpalClient.getInstance()
                .getModuleRepository()
                .getModule(ChestStealerModule.class);

        if (!chestStealer.isEnabled() || !chestStealer.getSmart().getValue() || !chestStealer.getHighlight().getValue()) return;

        final Map<EquipmentSlot, ItemStack> bestChestArmor = chestStealer.getBestChestArmor(chestInventory);
        final ItemStack bestChestSword = chestStealer.getBestChestSword(chestInventory);
        final ItemStack bestChestPickaxe = chestStealer.getBestChestTool(chestInventory, ItemTags.PICKAXES);
        final ItemStack bestChestAxe = chestStealer.getBestChestTool(chestInventory, ItemTags.AXES);

        final boolean take = chestStealer.shouldTake(stack, bestChestArmor, bestChestSword, bestChestPickaxe, bestChestAxe);
        if (take) {
            final int color = new Color(200, 200, 200, 50).getRGB();
            context.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, color);
        }
    }

}

package wtf.opal.client.feature.module.impl.utility;

import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import org.lwjgl.glfw.GLFW;
import wtf.opal.client.feature.module.Module;
import wtf.opal.client.feature.module.ModuleCategory;
import wtf.opal.client.feature.module.property.impl.bool.BooleanProperty;
import wtf.opal.client.feature.module.property.impl.number.NumberProperty;
import wtf.opal.event.impl.game.PreGameTickEvent;
import wtf.opal.event.subscriber.Subscribe;
import wtf.opal.utility.player.PlayerUtility;

import java.util.Set;

import static wtf.opal.client.Constants.mc;

public final class AutoChestModule extends Module {

    private static final Set<Item> RESOURCES = Set.of(Items.IRON_INGOT, Items.GOLD_INGOT, Items.DIAMOND, Items.EMERALD);

    private final NumberProperty ticks = new NumberProperty("Ticks", 1, 0, 10, 1);
    private final BooleanProperty autoDeposit = new BooleanProperty("Auto deposit", true);

    private ChestInteractionMode mode = ChestInteractionMode.NONE;
    private boolean hasSeenChest;
    private int tickCount;

    public AutoChestModule() {
        super("Auto Chest", "Dumps and retrieves resources in chests.", ModuleCategory.UTILITY);
        addProperties(ticks, autoDeposit);
    }

    @Override
    protected void onEnable() {
        this.resetState();
        super.onEnable();
    }

    @Override
    protected void onDisable() {
        this.resetState();
        super.onDisable();
    }

    @Subscribe
    public void onPreGameTick(final PreGameTickEvent event) {
        if (!(mc.currentScreen instanceof GenericContainerScreen container)) {
            this.resetState();
            return;
        }

        if (!this.hasSeenChest && this.autoDeposit.getValue()) {
            mode = ChestInteractionMode.DEPOSIT;
        }

        this.hasSeenChest = true;
        this.tickCount++;

        if (this.tickCount - 1 < this.ticks.getValue().intValue()) {
            return;
        }

        this.tickCount = 0;

        if (PlayerUtility.isKeyPressed(GLFW.GLFW_KEY_MINUS)) {
            this.mode = ChestInteractionMode.WITHDRAW;
        } else if (PlayerUtility.isKeyPressed(GLFW.GLFW_KEY_EQUAL)) {
            this.mode = ChestInteractionMode.DEPOSIT;
        }

        final GenericContainerScreenHandler screenHandler = container.getScreenHandler();

        switch (this.mode) {
            case DEPOSIT:
                if (this.handleDeposit(screenHandler)) {
                    return;
                }

                this.mode = ChestInteractionMode.NONE;
                break;

            case WITHDRAW:
                if (this.handleWithdraw(screenHandler)) {
                    return;
                }

                this.mode = ChestInteractionMode.NONE;
                break;
        }
    }

    private boolean handleDeposit(GenericContainerScreenHandler screenHandler) {
        final int chestSlotCount = screenHandler.getInventory().size();

        for (int i = chestSlotCount; i < screenHandler.slots.size(); i++) {
            Slot slot = screenHandler.slots.get(i);
            if (RESOURCES.contains(slot.getStack().getItem())) {
                mc.interactionManager.clickSlot(screenHandler.syncId, i, 0, SlotActionType.QUICK_MOVE, mc.player);
                return true;
            }
        }

        return false;
    }

    private boolean handleWithdraw(final GenericContainerScreenHandler screenHandler) {
        final Inventory chestInventory = screenHandler.getInventory();

        for (int i = 0; i < chestInventory.size(); i++) {
            if (RESOURCES.contains(chestInventory.getStack(i).getItem())) {
                mc.interactionManager.clickSlot(screenHandler.syncId, i, 0, SlotActionType.QUICK_MOVE, mc.player);
                return true;
            }
        }

        return false;
    }

    private void resetState() {
        this.mode = ChestInteractionMode.NONE;
        this.hasSeenChest = false;
        this.tickCount = 0;
    }

    private enum ChestInteractionMode {
        DEPOSIT,
        WITHDRAW,
        NONE
    }

}

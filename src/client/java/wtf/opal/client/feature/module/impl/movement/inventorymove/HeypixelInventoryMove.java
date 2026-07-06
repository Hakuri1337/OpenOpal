package wtf.opal.client.feature.module.impl.movement.inventorymove;

import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import wtf.opal.client.OpalClient;
import wtf.opal.client.feature.module.impl.movement.InventoryMoveModule;
import wtf.opal.client.feature.module.impl.utility.inventory.manager.InventoryManagerModule;
import wtf.opal.client.feature.module.property.impl.mode.ModuleMode;
import wtf.opal.client.screen.click.dropdown.DropdownClickGUI;
import wtf.opal.event.impl.game.input.MoveInputEvent;
import wtf.opal.event.subscriber.Subscribe;

import static wtf.opal.client.Constants.mc;

public final class HeypixelInventoryMove extends ModuleMode<InventoryMoveModule> {

    public HeypixelInventoryMove(final InventoryMoveModule module) {
        super(module);
    }

    @Override
    public Enum<?> getEnumValue() {
        return InventoryMoveModule.Mode.HEYPIXEL;
    }

    @Subscribe
    public void onMoveInput(final MoveInputEvent event) {
        if (!module.canProcessScreenInput() || !this.isHeypixelMovementContext()) {
            return;
        }

        module.applyMovementInput(event);
    }

    private boolean isHeypixelMovementContext() {
        if (mc.currentScreen instanceof DropdownClickGUI) {
            return true;
        }

        if (!(mc.currentScreen instanceof InventoryScreen)) {
            return false;
        }

        return OpalClient.getInstance()
                .getModuleRepository()
                .getModule(InventoryManagerModule.class)
                .isPerformingAction();
    }
}

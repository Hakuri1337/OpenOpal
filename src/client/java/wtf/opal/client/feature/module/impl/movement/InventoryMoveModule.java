package wtf.opal.client.feature.module.impl.movement;

import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.option.KeyBinding;
import wtf.opal.client.feature.module.Module;
import wtf.opal.client.feature.module.ModuleCategory;
import wtf.opal.client.feature.module.impl.movement.inventorymove.HeypixelInventoryMove;
import wtf.opal.client.feature.module.impl.movement.inventorymove.LegitInventoryMove;
import wtf.opal.client.feature.module.impl.movement.inventorymove.NormalInventoryMove;
import wtf.opal.client.feature.module.property.impl.mode.ModeProperty;
import wtf.opal.client.screen.click.dropdown.DropdownClickGUI;
import wtf.opal.event.impl.game.input.MoveInputEvent;
import wtf.opal.utility.player.PlayerUtility;

import static wtf.opal.client.Constants.mc;

public final class InventoryMoveModule extends Module {

    private final ModeProperty<Mode> mode = new ModeProperty<>("Mode", this, Mode.NORMAL);

    public InventoryMoveModule() {
        super("Inventory Move", "Allows you to move while in inventories.", ModuleCategory.MOVEMENT);
        addProperties(mode);
        addModuleModes(mode, new NormalInventoryMove(this), new HeypixelInventoryMove(this), new LegitInventoryMove(this));
    }

    @Override
    public String getSuffix() {
        return mode.getValue().toString();
    }

    public boolean canProcessScreenInput() {
        return mc.player != null
                && mc.currentScreen != null
                && mc.getOverlay() == null
                && !(mc.currentScreen instanceof ChatScreen);
    }

    public boolean isNormalScreenBlocked() {
        return mc.currentScreen instanceof DropdownClickGUI;
    }

    public void applyMovementInput(final MoveInputEvent event) {
        event.setForward(getMovementSpeed(isMovementKeyPressed(mc.options.forwardKey), isMovementKeyPressed(mc.options.backKey)));
        event.setSideways(getMovementSpeed(isMovementKeyPressed(mc.options.leftKey), isMovementKeyPressed(mc.options.rightKey)));
        event.setJump(isMovementKeyPressed(mc.options.jumpKey));
    }

    public void stopMovementInput(final MoveInputEvent event) {
        event.setForward(0.0F);
        event.setSideways(0.0F);
        event.setJump(false);
        event.setSneak(false);
    }

    private float getMovementSpeed(final boolean positive, final boolean negative) {
        if (positive == negative) {
            return 0.0F;
        }
        return positive ? 1.0F : -1.0F;
    }

    private boolean isMovementKeyPressed(final KeyBinding keyBinding) {
        return PlayerUtility.isKeyPressed(keyBinding.getDefaultKey().getCode());
    }

    public enum Mode {
        NORMAL("Normal"),
        HEYPIXEL("Heypixel"),
        LEGIT("Legit");

        private final String name;

        Mode(final String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }
}

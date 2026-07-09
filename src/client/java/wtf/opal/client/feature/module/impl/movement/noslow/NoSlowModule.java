package wtf.opal.client.feature.module.impl.movement.noslow;

import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.ItemTags;
import wtf.opal.client.feature.helper.impl.player.slot.SlotHelper;
import wtf.opal.client.feature.module.Module;
import wtf.opal.client.feature.module.ModuleCategory;
import wtf.opal.client.feature.module.impl.movement.noslow.impl.GrimFastNoSlow;
import wtf.opal.client.feature.module.impl.movement.noslow.impl.GrimJumpNoSlow;
import wtf.opal.client.feature.module.impl.movement.noslow.impl.UniversalNoSlow;
import wtf.opal.client.feature.module.impl.movement.noslow.impl.VanillaNoSlow;
import wtf.opal.client.feature.module.impl.movement.noslow.impl.WatchdogNoSlow;
import wtf.opal.client.feature.module.property.impl.bool.BooleanProperty;
import wtf.opal.client.feature.module.property.impl.mode.ModeProperty;
import wtf.opal.event.impl.game.PreGameTickEvent;
import wtf.opal.event.subscriber.Subscribe;

import static wtf.opal.client.Constants.mc;

public final class NoSlowModule extends Module {

    private final ModeProperty<Mode> mode = new ModeProperty<>("Mode", this, Mode.VANILLA);
    private final BooleanProperty allowSprinting = new BooleanProperty("Keep sprinting", true);

    private Action action = Action.NONE;

    public NoSlowModule() {
        super("No Slow", "Removes vanilla slowdowns such as item usage.", ModuleCategory.MOVEMENT);
        addModuleModes(mode, new VanillaNoSlow(this), new WatchdogNoSlow(this), new UniversalNoSlow(this), new GrimJumpNoSlow(this), new GrimFastNoSlow(this));
        addProperties(mode, allowSprinting);
    }

    @Subscribe(priority = 2)
    public void onPreGameTick(final PreGameTickEvent event) {
        if (mc.player == null || mc.currentScreen != null || mc.getOverlay() != null) {
            this.action = Action.NONE;
            return;
        }

        SlotHelper slotHelper = SlotHelper.getInstance();
        ItemStack mainHandStack = slotHelper.getSilence() == SlotHelper.Silence.FULL ? slotHelper.getMainHandStack(mc.player) : mc.player.getMainHandStack();
        switch (mainHandStack.getUseAction()) {
            case BLOCK -> action = Action.BLOCKABLE;
            case NONE -> action = mainHandStack.isIn(ItemTags.SWORDS) ? Action.BLOCKABLE : Action.NONE;
            case BOW -> action = Action.BOW;
            default -> action = Action.USEABLE;
        }
    }

    @Override
    protected void onEnable() {
        super.onEnable();
    }

    @Override
    protected void onDisable() {
        super.onDisable();
    }

    @Override
    public String getSuffix() {
        return mode.getValue().toString();
    }

    public Action getAction() {
        return action;
    }

    public enum Mode {
        VANILLA("Vanilla"),
        WATCHDOG("Watchdog"),
        UNIVERSAL("Universal"),
        GRIM_JUMP("GrimJump"),
        GRIM_FAST("GrimFast");

        private final String name;

        Mode(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    public enum Action {
        BLOCKABLE,
        USEABLE,
        BOW,
        NONE
    }

    public boolean isSprintingAllowed() {
        return allowSprinting.getValue();
    }

    public boolean applyLegacyModeValue(final Object propertyValue) {
        if (!(propertyValue instanceof String valueString)) {
            return false;
        }

        final String normalizedValue = normalize(valueString);
        if (normalizedValue.equals("grim")
                || normalizedValue.equals("grimv3")
                || normalizedValue.equals("grimfast")
                || normalizedValue.equals("noslow")
                || normalizedValue.equals("zen")
                || normalizedValue.equals("grimc0f")
                || normalizedValue.equals("noc0f")
                || normalizedValue.equals("heypixel")) {
            this.mode.setValueOrdinal(Mode.GRIM_FAST.ordinal());
            return true;
        }

        if (normalizedValue.equals("grimjump")) {
            this.mode.setValueOrdinal(Mode.GRIM_JUMP.ordinal());
            return true;
        }

        return false;
    }

    public boolean isLegacyKeepSprintingProperty(final String propertyName) {
        return normalize(propertyName).equals("keepsprinting");
    }

    public void applyLegacyKeepSprintingValue(final Object propertyValue) {
        this.allowSprinting.applyValue(propertyValue);
    }

    private static String normalize(final String value) {
        if (value == null) {
            return "";
        }

        final StringBuilder builder = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            final char character = value.charAt(i);
            if (Character.isLetterOrDigit(character)) {
                builder.append(Character.toLowerCase(character));
            }
        }
        return builder.toString();
    }

}

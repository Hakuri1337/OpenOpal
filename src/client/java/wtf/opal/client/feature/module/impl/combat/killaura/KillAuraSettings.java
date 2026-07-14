package wtf.opal.client.feature.module.impl.combat.killaura;

import wtf.opal.client.feature.helper.impl.player.rotation.RotationProperty;
import wtf.opal.client.feature.helper.impl.player.rotation.model.IRotationModel;
import wtf.opal.client.feature.helper.impl.player.rotation.model.impl.HeypixelRotationModel;
import wtf.opal.client.feature.helper.impl.player.rotation.model.impl.InstantRotationModel;
import wtf.opal.client.feature.helper.impl.player.swing.CPSProperty;
import wtf.opal.client.feature.helper.impl.target.TargetProperty;
import wtf.opal.client.feature.module.property.impl.GroupProperty;
import wtf.opal.client.feature.module.property.impl.bool.BooleanProperty;
import wtf.opal.client.feature.module.property.impl.bool.MultipleBooleanProperty;
import wtf.opal.client.feature.module.property.impl.mode.ModeProperty;
import wtf.opal.client.feature.module.property.impl.number.NumberProperty;

public final class KillAuraSettings {

    private final RotationProperty rotationProperty;
    private final ModeProperty<Mode> mode;
    private final TargetProperty targetProperty;
    private final CPSProperty cpsProperty, swingCpsProperty;

    private final NumberProperty range, rotationRange, swingRange;
    private final BooleanProperty hideFakeSwings;

    private final BooleanProperty requireAttackKey, requireWeapon, hitSelect;
    private final BooleanProperty overrideRaycast, tickLookahead, throughWalls;
    private final BooleanProperty heypixelBypass;
    private final ModeProperty<AutoblockMode> autoblockMode;
    private final BooleanProperty keepSprintFov;
    private final BooleanProperty grimKeepSprint;
    private final BooleanProperty smartWeapon;
    private final BooleanProperty attackCooldown19;
    private final NumberProperty fov;

    private final MultipleBooleanProperty visuals;

    public KillAuraSettings(final KillAuraModule module) {
        this.rotationProperty = new RotationProperty(InstantRotationModel.INSTANCE);
        this.targetProperty = new TargetProperty(true, false, false, false, false, true);
        this.cpsProperty = new CPSProperty(module, "Attack CPS", false);
        this.swingCpsProperty = new CPSProperty(module, "Swing CPS", false);

        this.range = new NumberProperty("Range", 3.D, 3.D, 6.D, 0.1D);
        this.rotationRange = new NumberProperty("Rotation range", 5.D, 3.D, 8.D, 0.1D);
        this.swingRange = new NumberProperty("Swing range", 5.D, 3.D, 8.D, 0.1D);
        this.hideFakeSwings = new BooleanProperty("Hide fake swings", true);

        this.requireAttackKey = new BooleanProperty("Require attack key", false);
        this.requireWeapon = new BooleanProperty("Require weapon", false);
        this.hitSelect = new BooleanProperty("Hit Select", false);
        this.overrideRaycast = new BooleanProperty("Override raycast", true);
        this.tickLookahead = new BooleanProperty("Tick lookahead", false).hideIf(() -> !this.isOverrideRaycast());
        this.throughWalls = new BooleanProperty("Through Walls", false);
        this.heypixelBypass = new BooleanProperty("Heypixel Bypass", false);
        this.autoblockMode = new ModeProperty<>("Autoblock", AutoblockMode.OFF);
        this.keepSprintFov = new BooleanProperty("Keep Sprint FOV", false);
        this.grimKeepSprint = new BooleanProperty("Grim Keep Sprint", false);
        this.smartWeapon = new BooleanProperty("SmartWeapon", false);
        this.attackCooldown19 = new BooleanProperty("1.9+ Attack Cooldown", false);
        this.mode = new ModeProperty<>("Mode", Mode.SWITCH);
        this.fov = new NumberProperty("FOV", 180, 1, 180, 1);

        this.visuals = new MultipleBooleanProperty("Visuals",
                new BooleanProperty("Box", false),
                new BooleanProperty("Halo", true)
        );

        module.addProperties(
                rotationProperty.get(), new GroupProperty("Requirements", requireWeapon, requireAttackKey, hitSelect),
                mode, range, rotationRange, swingRange, hideFakeSwings, targetProperty.get(),
                fov, overrideRaycast, tickLookahead, throughWalls, heypixelBypass, autoblockMode, keepSprintFov, grimKeepSprint, smartWeapon, attackCooldown19, visuals
        );
    }

    public boolean isHitSelect() {
        return hitSelect.getValue();
    }

    public boolean isFakeAutoBlock() {
        return autoblockMode.getValue() == AutoblockMode.FAKE;
    }

    public boolean isVanillaAutoBlock() {
        return autoblockMode.getValue() == AutoblockMode.VANILLA;
    }

    public AutoblockMode getAutoblockMode() {
        return autoblockMode.getValue();
    }

    public boolean isHeypixelBypass() {
        return heypixelBypass.getValue();
    }

    public double getSwingRange() {
        return this.swingRange.getValue();
    }

    public double getRange() {
        return this.range.getValue();
    }

    public boolean isThroughWalls() {
        return this.throughWalls.getValue();
    }

    public boolean isHideFakeSwings() {
        return this.hideFakeSwings.getValue();
    }

    public boolean isOverrideRaycast() {
        return this.overrideRaycast.getValue() && !this.isHeypixelBypass();
    }

    public boolean isTickLookahead() {
        return this.tickLookahead.getValue();
    }

    public double getRotationRange() {
        return this.rotationRange.getValue();
    }

    public MultipleBooleanProperty getVisuals() {
        return visuals;
    }

    public TargetProperty getTargetProperty() {
        return targetProperty;
    }

    public CPSProperty getCpsProperty() {
        return cpsProperty;
    }

    public CPSProperty getSwingCpsProperty() {
        return swingCpsProperty;
    }

    public boolean isRequireAttackKey() {
        return requireAttackKey.getValue();
    }

    public boolean isRequireWeapon() {
        return requireWeapon.getValue();
    }

    public IRotationModel createRotationModel() {
        return isHeypixelBypass() ? new HeypixelRotationModel(rotationProperty.getMaxAngle()) : rotationProperty.createModel();
    }

    public Mode getMode() {
        return mode.getValue();
    }

    public float getFov() {
        return this.fov.getValue().floatValue();
    }

    public boolean isKeepSprintFov() {
        return keepSprintFov.getValue();
    }

    public boolean isGrimKeepSprint() {
        return grimKeepSprint.getValue();
    }

    public boolean isAttackCooldown19() {
        return attackCooldown19.getValue();
    }

    public boolean isSmartWeapon() {
        return smartWeapon.getValue();
    }

    public enum Mode {
        SINGLE("Single"),
        SWITCH("Switch");

        private final String name;

        Mode(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    public enum AutoblockMode {
        OFF("Off"),
        VANILLA("Vanilla"),
        FAKE("Fake");

        private final String name;

        AutoblockMode(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

}

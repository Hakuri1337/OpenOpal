package wtf.opal.client.feature.module.impl.world.scaffold;

import wtf.opal.client.feature.helper.impl.LocalDataWatch;
import wtf.opal.client.feature.helper.impl.player.rotation.RotationProperty;
import wtf.opal.client.feature.helper.impl.player.rotation.model.EnumRotationModel;
import wtf.opal.client.feature.helper.impl.player.rotation.model.IRotationModel;
import wtf.opal.client.feature.helper.impl.player.rotation.model.impl.InstantRotationModel;
import wtf.opal.client.feature.helper.impl.player.swing.CPSProperty;
import wtf.opal.client.feature.helper.impl.server.impl.HypixelServer;
import wtf.opal.client.feature.module.impl.world.scaffold.mode.AntiGamingChairScaffold;
import wtf.opal.client.feature.module.impl.world.scaffold.mode.BloxdScaffold;
import wtf.opal.client.feature.module.impl.world.scaffold.mode.HeypixelScaffold;
import wtf.opal.client.feature.module.impl.world.scaffold.mode.HypixelScaffold;
import wtf.opal.client.feature.module.impl.world.scaffold.mode.TellyScaffold;
import wtf.opal.client.feature.module.impl.world.scaffold.mode.VanillaScaffold;
import wtf.opal.client.feature.module.impl.world.scaffold.mode.watchdog.WatchdogScaffold;
import wtf.opal.client.feature.module.property.impl.GroupProperty;
import wtf.opal.client.feature.module.property.impl.bool.BooleanProperty;
import wtf.opal.client.feature.module.property.impl.bool.MultipleBooleanProperty;
import wtf.opal.client.feature.module.property.impl.mode.ModeProperty;
import wtf.opal.client.feature.module.property.impl.number.NumberProperty;

public final class ScaffoldSettings {

    private final RotationProperty rotationProperty;
    private final BooleanProperty movementIntelligence;
    private final BooleanProperty movementSnapping, diagonalMovement;
    private final NumberProperty movementSteps;

    private final CPSProperty simulationCps;

    private final BooleanProperty tower;
    private final ModeProperty<Mode> mode;

    private final ModeProperty<SwitchMode> switchMode;
    private final ModeProperty<SwingMode> swingMode;

    private final BooleanProperty snapRotations;
    private final BooleanProperty sameY, autoJump;
    private final BooleanProperty tellyHeypixel;
    private final BooleanProperty keepFov;
    private final BooleanProperty duplicateRotPlace;

    private final BooleanProperty blockOverlay;

    private final BooleanProperty overrideRaycast;
    private final BooleanProperty interactBeforePlace;

    private final BooleanProperty uitemsTelly;
    private final BooleanProperty upTellyBypass;
    private final BooleanProperty snap;
    private final ModeProperty<SelfRescueMode> selfRescueMode;
    private final NumberProperty rotateSpeed;
    private final NumberProperty rotateBackSpeed;
    private final NumberProperty tellyTick;
    private final BooleanProperty safeWalk;

    private final MultipleBooleanProperty hypixelAddons;

    public ScaffoldSettings(final ScaffoldModule module) {
        this.movementIntelligence = new BooleanProperty("Enabled", false);
        this.diagonalMovement = new BooleanProperty("Diagonal movement", false);
        this.movementSnapping = new BooleanProperty("Snap movement", true);
        this.movementSteps = new NumberProperty("Steps", 3, 1, 3, 1).hideIf(() -> !this.isMovementSnapping());
        this.rotationProperty = new RotationProperty(InstantRotationModel.INSTANCE,
                new GroupProperty("Movement intelligence", this.movementIntelligence, this.diagonalMovement, this.movementSnapping, this.movementSteps));

        this.simulationCps = new CPSProperty(module, "Interact CPS", false);

        this.mode = new ModeProperty<>("Mode", module, Mode.WATCHDOG);
        this.tower = new BooleanProperty("Tower", false);

        this.switchMode = new ModeProperty<>("Switch mode", module, SwitchMode.HOTBAR);
        this.swingMode = new ModeProperty<>("Swing mode", SwingMode.CLIENT);

        this.snapRotations = new BooleanProperty("Snap rotations", false);
        this.sameY = new BooleanProperty("Same Y", true);
        this.autoJump = new BooleanProperty("Auto jump", true).hideIf(() -> !this.isSameYEnabled());
        this.tellyHeypixel = new BooleanProperty("Heypixel", false).hideIf(() -> !this.isTellyMode());
        this.keepFov = new BooleanProperty("Keep Sprint FOV", false);
        this.duplicateRotPlace = new BooleanProperty("Duplicate Rot Place", false);

        this.blockOverlay = new BooleanProperty("Block overlay", true);

        this.overrideRaycast = new BooleanProperty("Override raycast", true);
        this.interactBeforePlace = new BooleanProperty("Interact before place", false);

        this.uitemsTelly = new BooleanProperty("Uitems Telly", true).hideIf(() -> !this.isUitemsScaffoldMode());
        this.upTellyBypass = new BooleanProperty("UpTellyBypass", false).hideIf(() -> this.mode.getValue() != Mode.HEYPIXEL || !this.uitemsTelly.getValue());
        this.snap = new BooleanProperty("Snap", false).hideIf(() -> !this.isUitemsScaffoldMode() || this.uitemsTelly.getValue());
        this.selfRescueMode = new ModeProperty<>("Self rescue mode", SelfRescueMode.DISABLED)
                .hideIf(() -> !this.isUitemsScaffoldMode());
        this.rotateSpeed = new NumberProperty("Rotation Speed", 180.0F, 1.0F, 180.0F, 1.0F).hideIf(() -> !this.isUitemsScaffoldMode());
        this.rotateBackSpeed = new NumberProperty("Rotation Back Speed", 180.0F, 1.0F, 180.0F, 1.0F).hideIf(() -> !this.isUitemsScaffoldMode() || !this.uitemsTelly.getValue());
        this.tellyTick = new NumberProperty("Telly Ticks", 5.0F, 0.0F, 10.0F, 1.0F).hideIf(() -> !this.isUitemsScaffoldMode() || !this.uitemsTelly.getValue());
        this.safeWalk = new BooleanProperty("SafeWalk", true).hideIf(() -> !this.isUitemsScaffoldMode() || this.uitemsTelly.getValue());

        this.hypixelAddons = new MultipleBooleanProperty("Hypixel addons",
                new BooleanProperty("Boost", true)).hideIf(() -> !(LocalDataWatch.get().getKnownServerManager().getCurrentServer() instanceof HypixelServer));

        module.addModuleModes(mode, new VanillaScaffold(module), new WatchdogScaffold(module), new AntiGamingChairScaffold(module), new BloxdScaffold(module), new TellyScaffold(module), new HeypixelScaffold(module), new HypixelScaffold(module));
        module.addProperties(rotationProperty.get(), mode, switchMode, swingMode, tower, snapRotations, overrideRaycast,
                interactBeforePlace, sameY, autoJump, tellyHeypixel, keepFov, duplicateRotPlace, blockOverlay,
                uitemsTelly, upTellyBypass, snap, selfRescueMode, rotateSpeed, rotateBackSpeed, tellyTick, safeWalk,
                hypixelAddons);
    }

    private boolean isTellyMode() {
        return this.mode.getValue() == Mode.TELLY;
    }

    public boolean isTellyHeypixel() {
        return this.tellyHeypixel.getValue();
    }

    private boolean isUitemsScaffoldMode() {
        return this.mode.getValue() == Mode.HEYPIXEL || this.mode.getValue() == Mode.HYPIXEL;
    }

    public boolean isKeepFov() {
        return keepFov.getValue();
    }

    public boolean isDuplicateRotPlace() {
        return duplicateRotPlace.getValue();
    }

    public boolean isTelly() {
        return uitemsTelly.getValue();
    }

    public boolean isUpTellyBypass() {
        return upTellyBypass.getValue();
    }

    public boolean isSnap() {
        return snap.getValue();
    }

    public SelfRescueMode getSelfRescueMode() {
        return selfRescueMode.getValue();
    }

    public float getRotateSpeed() {
        return rotateSpeed.getValue().floatValue();
    }

    public float getRotateBackSpeed() {
        return rotateBackSpeed.getValue().floatValue();
    }

    public int getTellyTick() {
        return tellyTick.getValue().intValue();
    }

    public boolean isSafeWalk() {
        return safeWalk.getValue();
    }

    public CPSProperty getSimulationCps() {
        return simulationCps;
    }

    public boolean isDiagonalMovement() {
        return this.diagonalMovement.getValue();
    }

    public boolean isMovementSnapping() {
        return this.movementSnapping.getValue();
    }

    public boolean isMovementIntelligence() {
        return this.movementIntelligence.getValue();
    }

    public int getMovementIntelligenceSteps() {
        return this.movementSteps.getValue().intValue();
    }

    public boolean isAutoJump() {
        return this.autoJump.getValue();
    }

    public ModeProperty<Mode> getMode() {
        return mode;
    }

    public boolean isTowerEnabled() {
        return tower.getValue();
    }

    public boolean isBlockOverlayEnabled() {
        return blockOverlay.getValue();
    }

    public ModeProperty<SwitchMode> getSwitchMode() {
        return switchMode;
    }

    public ModeProperty<SwingMode> getSwingMode() {
        return swingMode;
    }

    public boolean isSnapRotationsEnabled() {
        return snapRotations.getValue();
    }

    public boolean isSameYEnabled() {
        return sameY.getValue();
    }

    public boolean isOverrideRaycast() {
        return overrideRaycast.getValue();
    }

    public boolean isInteractBeforePlace() {
        return interactBeforePlace.getValue();
    }

    public MultipleBooleanProperty getHypixelAddons() {
        return hypixelAddons;
    }

    public IRotationModel createRotationModel() {
        return rotationProperty.createModel();
    }

    public boolean isRotationModel(final EnumRotationModel model) {
        return rotationProperty.isModel(model);
    }

    public enum SwitchMode {
        NORMAL("Normal"),
        HOTBAR("Hotbar"),
        FULL("Full");

        private final String name;

        SwitchMode(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    public enum SwingMode {
        CLIENT("Client"),
        SERVER("Server");

        private final String name;

        SwingMode(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    public enum Mode {
        VANILLA("Vanilla"),
        ANTI_GAMING_CHAIR("Anti Gaming Chair"),
        WATCHDOG("Watchdog"),
        BLOXD("Bloxd"),
        TELLY("Telly"),
        HEYPIXEL("Heypixel"),
        HYPIXEL("Hypixel");

        private final String name;

        Mode(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    public enum SelfRescueMode {
        DISABLED("Disabled"),
        SKIP_TICK("SkipTick");

        private final String name;

        SelfRescueMode(final String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return this.name;
        }
    }

}

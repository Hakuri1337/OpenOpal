package wtf.opal.client.feature.module.impl.utility.disabler;

import wtf.opal.client.feature.module.Module;
import wtf.opal.client.feature.module.ModuleCategory;
import wtf.opal.client.feature.module.impl.utility.disabler.impl.CubecraftDisabler;
import wtf.opal.client.feature.module.impl.utility.disabler.impl.HeypixelDisabler;
import wtf.opal.client.feature.module.impl.utility.disabler.impl.MinibloxDisabler;
import wtf.opal.client.feature.module.property.impl.bool.BooleanProperty;
import wtf.opal.client.feature.module.property.impl.mode.ModeProperty;

public final class DisablerModule extends Module {
    private final ModeProperty<Mode> mode = new ModeProperty<>("Mode", this, Mode.HEYPIXEL)
            .alias("MINIBLOX_C0C", Mode.MINIBLOX)
            .alias("MINIBLOX_INPUT", Mode.MINIBLOX)
            .alias("MINIBLOX_MOVEPAYLOAD", Mode.MINIBLOX);
    private final BooleanProperty debug = new BooleanProperty("Debug", false)
            .hideIf(() -> !this.isMinibloxMode());

    public DisablerModule() {
        super("Disabler", "Lessens anti-cheat strength.", ModuleCategory.UTILITY);
        addProperties(mode, debug);
        addModuleModes(mode,
                new HeypixelDisabler(this),
                new CubecraftDisabler(this),
                new MinibloxDisabler(this));
    }

    @Override
    public String getSuffix() {
        if (this.getActiveMode() instanceof CubecraftDisabler cubecraft) {
            return cubecraft.getStatusSuffix();
        }
        return mode.getValue().toString();
    }

    public boolean isDebugEnabled() {
        return debug.getValue();
    }

    private boolean isMinibloxMode() {
        return mode.getValue() == Mode.MINIBLOX;
    }

    public enum Mode {
        HEYPIXEL("Heypixel"),
        CUBECRAFT("CubeCraft"),
        MINIBLOX("MiniBlox");

        private final String name;

        Mode(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }
}

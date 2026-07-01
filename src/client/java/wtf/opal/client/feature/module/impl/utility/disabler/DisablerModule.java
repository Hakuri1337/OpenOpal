package wtf.opal.client.feature.module.impl.utility.disabler;

import wtf.opal.client.feature.module.Module;
import wtf.opal.client.feature.module.ModuleCategory;
import wtf.opal.client.feature.module.impl.utility.disabler.impl.CubecraftDisabler;
import wtf.opal.client.feature.module.impl.utility.disabler.impl.HeypixelDisabler;
import wtf.opal.client.feature.module.property.impl.mode.ModeProperty;

public final class DisablerModule extends Module {
    private final ModeProperty<Mode> mode = new ModeProperty<>("Mode", this, Mode.HEYPIXEL);

    public DisablerModule() {
        super("Disabler", "Lessens anti-cheat strength.", ModuleCategory.UTILITY);
        addProperties(mode);
        addModuleModes(mode, new HeypixelDisabler(this), new CubecraftDisabler(this));
    }

    @Override
    public String getSuffix() {
        return mode.getValue().toString();
    }

    public enum Mode {
        HEYPIXEL("Heypixel"),
        CUBECRAFT("CubeCraft");

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

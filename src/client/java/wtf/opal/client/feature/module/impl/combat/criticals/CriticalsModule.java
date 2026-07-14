package wtf.opal.client.feature.module.impl.combat.criticals;

import wtf.opal.client.feature.module.Module;
import wtf.opal.client.feature.module.ModuleCategory;
import wtf.opal.client.feature.module.impl.combat.criticals.impl.HeypixelCriticals;
import wtf.opal.client.feature.module.impl.combat.criticals.impl.PacketCriticals;
import wtf.opal.client.feature.module.property.impl.mode.ModeProperty;

public final class CriticalsModule extends Module {

    private final ModeProperty<Mode> mode = new ModeProperty<>("Mode", this, Mode.PACKET);

    public CriticalsModule() {
        super("Criticals", "Forces every attack to be a critical hit.", ModuleCategory.COMBAT);
        addProperties(mode);
        addModuleModes(mode, new PacketCriticals(this), new HeypixelCriticals(this));
    }

    @Override
    public String getSuffix() {
        return mode.getValue().toString();
    }

    public enum Mode {
        PACKET("Packet"),
        HEYPIXEL("Heypixel");

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

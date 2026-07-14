package wtf.opal.client.feature.module.impl.movement.flight;

import wtf.opal.client.feature.module.Module;
import wtf.opal.client.feature.module.ModuleCategory;
import wtf.opal.client.feature.module.impl.movement.flight.impl.*;
import wtf.opal.client.feature.module.property.impl.mode.ModeProperty;

public final class FlightModule extends Module {

    private final ModeProperty<Mode> mode = new ModeProperty<>("Mode", this, Mode.VANILLA);

    public FlightModule() {
        super("Flight", "You grow wings in real life.", ModuleCategory.MOVEMENT);
        addProperties(mode);
        addModuleModes(mode, new VanillaFlight(this), new FireballFlight(this), new AirWalkFlight(this),
                new BloxdFlight(this), new CubeCraftAirWalkFlight(this), new CubeCraftPingSpoofFlight(this),
                new CubeCraftFastFlight(this));
    }

    @Override
    public String getSuffix() {
        return mode.getValue().toString();
    }

    public enum Mode {
        VANILLA("Vanilla"),
        FIREBALL("Fireball"),
        HYPIXEL("Hypixel"),
        AIR_WALK("Air Walk"),
        BLOXD("Bloxd"),
        CUBECRAFT_AIR_WALK("CubeCraftAirWalk"),
        CUBECRAFT_PING_SPOOF("CubeCraftPingSpoof"),
        CUBECRAFT_FAST("CubeCraftFast");

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

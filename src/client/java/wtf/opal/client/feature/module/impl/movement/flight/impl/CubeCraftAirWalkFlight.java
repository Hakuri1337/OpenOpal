package wtf.opal.client.feature.module.impl.movement.flight.impl;

import net.minecraft.util.math.Direction;
import wtf.opal.client.feature.module.impl.movement.flight.FlightModule;
import wtf.opal.client.feature.module.property.impl.mode.ModuleMode;
import wtf.opal.client.feature.module.property.impl.number.BoundedNumberProperty;
import wtf.opal.event.impl.game.input.MoveInputEvent;
import wtf.opal.event.impl.game.player.movement.PostMoveEvent;
import wtf.opal.event.subscriber.Subscribe;
import wtf.opal.utility.player.MoveUtility;

import static wtf.opal.client.Constants.mc;

public final class CubeCraftAirWalkFlight extends ModuleMode<FlightModule> {

    private final BoundedNumberProperty horizontalSpeed = new BoundedNumberProperty("Horizontal Speed", 0.33D, 0.34D, 0.1D, 1.0D, 0.01D)
            .hideIf(() -> this.module.getActiveMode() != this);

    private int ticks;

    public CubeCraftAirWalkFlight(final FlightModule module) {
        super(module);
        module.addProperties(this.horizontalSpeed);
    }

    @Subscribe
    public void onPostMove(final PostMoveEvent event) {
        if (mc.player == null || mc.player.isOnGround()) {
            return;
        }

        if (this.ticks++ % 6 != 0) {
            return;
        }

        final double motionY;
        if (mc.options.sneakKey.isPressed()) {
            motionY = -0.4D;
        } else if (mc.options.jumpKey.isPressed()) {
            motionY = 0.42D;
        } else {
            motionY = 0.2D;
        }

        mc.player.setVelocity(mc.player.getVelocity().withAxis(Direction.Axis.Y, motionY));
        if (MoveUtility.isMoving()) {
            MoveUtility.setSpeed(this.horizontalSpeed.getRandomValue());
        } else {
            MoveUtility.setSpeed(0.0D);
        }
    }

    @Subscribe
    public void onMoveInput(final MoveInputEvent event) {
        event.setSneak(false);
    }

    @Override
    public void onEnable() {
        this.ticks = 0;
        super.onEnable();
    }

    @Override
    public Enum<?> getEnumValue() {
        return FlightModule.Mode.CUBECRAFT_AIR_WALK;
    }
}

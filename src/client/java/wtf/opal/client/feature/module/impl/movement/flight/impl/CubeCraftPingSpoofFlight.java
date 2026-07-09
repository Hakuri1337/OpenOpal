package wtf.opal.client.feature.module.impl.movement.flight.impl;

import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.Direction;
import wtf.opal.client.OpalClient;
import wtf.opal.client.feature.module.impl.movement.flight.FlightModule;
import wtf.opal.client.feature.module.impl.movement.speed.SpeedModule;
import wtf.opal.client.feature.module.impl.utility.PingSpoofModule;
import wtf.opal.client.feature.module.property.impl.bool.BooleanProperty;
import wtf.opal.client.feature.module.property.impl.mode.ModuleMode;
import wtf.opal.client.feature.module.property.impl.number.NumberProperty;
import wtf.opal.duck.ClientConnectionAccess;
import wtf.opal.event.impl.game.PreGameTickEvent;
import wtf.opal.event.impl.game.player.movement.PostMoveEvent;
import wtf.opal.event.subscriber.Subscribe;
import wtf.opal.utility.player.MoveUtility;

import static wtf.opal.client.Constants.mc;

public final class CubeCraftPingSpoofFlight extends ModuleMode<FlightModule> {

    private final NumberProperty horizontalSpeed = new NumberProperty("Horizontal Speed", 3.5D, 0.1D, 10.0D, 0.1D)
            .hideIf(() -> this.module.getActiveMode() != this);
    private final BooleanProperty constantSpeed = new BooleanProperty("Constant Speed", false)
            .hideIf(() -> this.module.getActiveMode() != this);
    private final NumberProperty verticalSpeed = new NumberProperty("Vertical Speed", 0.7D, 0.1D, 1.0D, 0.1D)
            .hideIf(() -> this.module.getActiveMode() != this);
    private final NumberProperty reboostTicks = new NumberProperty("Reboost Ticks", 30.0D, 10.0D, 50.0D, 1.0D)
            .hideIf(() -> this.module.getActiveMode() != this);
    private final BooleanProperty boostOnce = new BooleanProperty("Boost Once", false)
            .hideIf(() -> this.module.getActiveMode() != this);
    private final BooleanProperty nostalgia = new BooleanProperty("Nostalgia", false)
            .hideIf(() -> this.module.getActiveMode() != this);

    private boolean hasBeenHurt;
    private boolean hasBeenTeleported;
    private int reboostTicksLeft;

    public CubeCraftPingSpoofFlight(final FlightModule module) {
        super(module);
        module.addProperties(this.horizontalSpeed, this.constantSpeed, this.verticalSpeed, this.reboostTicks, this.boostOnce, this.nostalgia);
    }

    @Subscribe
    public void onPreGameTick(final PreGameTickEvent event) {
        if (mc.player == null || mc.world == null) {
            return;
        }

        if (this.reboostTicksLeft > 0) {
            this.reboostTicksLeft--;
            return;
        }

        if (this.boostOnce.getValue()) {
            this.module.setEnabled(false);
            MoveUtility.setSpeed(0.0D);
            return;
        }

        this.boost();
        this.reboostTicksLeft = this.reboostTicks.getValue().intValue();
    }

    @Subscribe
    public void onPostMove(final PostMoveEvent event) {
        if (mc.player == null) {
            return;
        }

        if (mc.player.hurtTime > 0 && !this.hasBeenHurt) {
            this.hasBeenHurt = true;
            MoveUtility.setSpeed(this.horizontalSpeed.getValue());

            if (!this.hasBeenTeleported && this.nostalgia.getValue()) {
                this.hasBeenTeleported = true;
                mc.player.setPosition(mc.player.getX(), mc.player.getY() + 0.42D, mc.player.getZ());
            }
        }

        if (!this.hasBeenHurt) {
            return;
        }

        final double motionY;
        if (mc.options.jumpKey.isPressed()) {
            motionY = this.verticalSpeed.getValue();
        } else if (mc.options.sneakKey.isPressed()) {
            motionY = -this.verticalSpeed.getValue();
        } else {
            motionY = 0.0D;
        }

        mc.player.setVelocity(mc.player.getVelocity().withAxis(Direction.Axis.Y, motionY));
        if (this.constantSpeed.getValue() && MoveUtility.isMoving()) {
            MoveUtility.setSpeed(this.horizontalSpeed.getValue());
        }
    }

    private void boost() {
        if (mc.player == null) {
            return;
        }

        this.hasBeenHurt = false;
        this.sendPacketSilent(new PlayerMoveC2SPacket.PositionAndOnGround(
                mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                false, mc.player.horizontalCollision
        ));
        this.sendPacketSilent(new PlayerMoveC2SPacket.PositionAndOnGround(
                mc.player.getX(), mc.player.getY() + 3.25D, mc.player.getZ(),
                false, mc.player.horizontalCollision
        ));
        this.sendPacketSilent(new PlayerMoveC2SPacket.PositionAndOnGround(
                mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                false, mc.player.horizontalCollision
        ));
        this.sendPacketSilent(new PlayerMoveC2SPacket.PositionAndOnGround(
                mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                true, mc.player.horizontalCollision
        ));
    }

    private void sendPacketSilent(final Packet<?> packet) {
        if (mc.getNetworkHandler() != null && mc.getNetworkHandler().getConnection() instanceof ClientConnectionAccess access) {
            access.opal$sendPacketSilent(packet);
        }
    }

    @Override
    public void onEnable() {
        final PingSpoofModule pingSpoof = OpalClient.getInstance().getModuleRepository().getModule(PingSpoofModule.class);
        if (pingSpoof != null && !pingSpoof.isEnabled()) {
            pingSpoof.setEnabled(true);
        }

        final SpeedModule speed = OpalClient.getInstance().getModuleRepository().getModule(SpeedModule.class);
        if (speed != null && speed.isEnabled()) {
            speed.setEnabled(false);
        }

        this.hasBeenHurt = false;
        this.hasBeenTeleported = false;
        this.boost();
        this.reboostTicksLeft = this.reboostTicks.getValue().intValue();
        super.onEnable();
    }

    @Override
    public void onDisable() {
        if (mc.player != null) {
            MoveUtility.setSpeed(0.0D);
        }
        super.onDisable();
    }

    @Override
    public Enum<?> getEnumValue() {
        return FlightModule.Mode.CUBECRAFT_PING_SPOOF;
    }
}

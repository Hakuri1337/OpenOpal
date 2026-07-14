package wtf.opal.client.feature.module.impl.movement.flight.impl;

import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.common.CommonPongC2SPacket;
import net.minecraft.network.packet.c2s.common.KeepAliveC2SPacket;
import net.minecraft.network.packet.c2s.play.ChatMessageC2SPacket;
import net.minecraft.network.packet.c2s.play.CommandExecutionC2SPacket;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import wtf.opal.client.OpalClient;
import wtf.opal.client.feature.helper.impl.player.packet.blockage.block.holder.BlockHolder;
import wtf.opal.client.feature.helper.impl.player.packet.blockage.impl.OutboundNetworkBlockage;
import wtf.opal.client.feature.module.impl.movement.TargetStrafeModule;
import wtf.opal.client.feature.module.impl.movement.flight.FlightModule;
import wtf.opal.client.feature.module.impl.utility.disabler.DisablerModule;
import wtf.opal.client.feature.module.impl.utility.disabler.impl.CubecraftDisabler;
import wtf.opal.client.feature.module.property.impl.mode.ModuleMode;
import wtf.opal.client.feature.module.property.impl.number.NumberProperty;
import wtf.opal.event.impl.game.JoinWorldEvent;
import wtf.opal.event.impl.game.player.movement.PreMovementPacketEvent;
import wtf.opal.event.impl.game.server.ServerDisconnectEvent;
import wtf.opal.event.subscriber.Subscribe;
import wtf.opal.utility.player.MoveUtility;

import static wtf.opal.client.Constants.mc;

public final class CubeCraftFastFlight extends ModuleMode<FlightModule> {
    private final NumberProperty horizontalSpeed = new NumberProperty("Horizontal Speed", this, 3.5D, 0.1D, 10.0D, 0.1D)
            .hideIf(() -> this.module.getActiveMode() != this);
    private final NumberProperty verticalSpeed = new NumberProperty("Vertical Speed", this, 0.7D, 0.1D, 5.0D, 0.1D)
            .hideIf(() -> this.module.getActiveMode() != this);

    private final BlockHolder blink = new BlockHolder(OutboundNetworkBlockage.get());
    private int tick;

    public CubeCraftFastFlight(FlightModule module) {
        super(module);
    }

    @Subscribe
    public void onPreMovementPacket(PreMovementPacketEvent event) {
        if (mc.player == null) {
            return;
        }

        if (this.isDisablerWaiting()) {
            this.blink.release();
            return;
        }

        final TargetStrafeModule targetStrafe = OpalClient.getInstance().getModuleRepository().getModule(TargetStrafeModule.class);
        final boolean strafing = targetStrafe != null && targetStrafe.isActivelyStrafing();
        final Vec2f input = mc.player.input.getMovementInput();
        final boolean moving = input.x != 0.0F || input.y != 0.0F;

        double targetY = 0.0D;
        if (!strafing) {
            if (mc.options.jumpKey.isPressed()) {
                targetY = this.verticalSpeed.getValue();
            } else if (mc.options.sneakKey.isPressed()) {
                targetY = -this.verticalSpeed.getValue();
            }
        }

        if (this.tick++ % 6 == 0) {
            this.blink.block(packet -> packet, packet -> !isBlinkIgnored(packet));
            if (!strafing) {
                this.setHorizontalVelocity(0.0D, 0.0D);
            }
            if (moving) {
                this.setHorizontalSpeed(input, this.horizontalSpeed.getValue());
            }
        } else if (!moving) {
            this.setHorizontalVelocity(0.0D, 0.0D);
        } else {
            this.blink.release();
        }

        final Vec3d velocity = mc.player.getVelocity();
        mc.player.setVelocity(velocity.x, targetY, velocity.z);
    }

    @Subscribe
    public void onJoinWorld(JoinWorldEvent event) {
        this.reset(false);
    }

    @Subscribe
    public void onServerDisconnect(ServerDisconnectEvent event) {
        this.reset(false);
    }

    @Override
    public void onEnable() {
        this.reset(false);
        super.onEnable();
    }

    @Override
    public void onDisable() {
        this.reset(true);
        super.onDisable();
    }

    @Override
    public Enum<?> getEnumValue() {
        return FlightModule.Mode.CUBECRAFT_FAST;
    }

    private boolean isDisablerWaiting() {
        final DisablerModule disabler = OpalClient.getInstance().getModuleRepository().getModule(DisablerModule.class);
        return disabler != null
                && disabler.isEnabled()
                && disabler.getActiveMode() instanceof CubecraftDisabler cubecraft
                && cubecraft.isWaiting();
    }

    private void setHorizontalSpeed(Vec2f input, double speed) {
        final double direction = MoveUtility.getDirection(mc.player.getYaw(), input.y, input.x);
        MoveUtility.setSpeed(mc.player, speed, direction);
    }

    private void setHorizontalVelocity(double x, double z) {
        final Vec3d velocity = mc.player.getVelocity();
        mc.player.setVelocity(x, velocity.y, z);
    }

    private void reset(boolean stopPlayer) {
        this.blink.release();
        this.tick = 0;
        if (stopPlayer && mc.player != null) {
            mc.player.setVelocity(Vec3d.ZERO);
        }
    }

    private static boolean isBlinkIgnored(Packet<?> packet) {
        return packet instanceof KeepAliveC2SPacket
                || packet instanceof CommonPongC2SPacket
                || packet instanceof ChatMessageC2SPacket
                || packet instanceof CommandExecutionC2SPacket;
    }
}

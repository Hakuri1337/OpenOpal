package wtf.opal.client.feature.module.impl.combat.velocity.impl;

import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.s2c.play.EntityDamageS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket;
import net.minecraft.util.math.Direction;
import wtf.opal.client.feature.module.impl.combat.velocity.VelocityMode;
import wtf.opal.client.feature.module.impl.combat.velocity.VelocityModule;
import wtf.opal.client.feature.module.property.impl.bool.BooleanProperty;
import wtf.opal.duck.ClientConnectionAccess;
import wtf.opal.event.impl.game.PreGameTickEvent;
import wtf.opal.event.impl.game.packet.ReceivePacketEvent;
import wtf.opal.event.subscriber.Subscribe;

import static wtf.opal.client.Constants.mc;

public final class CubeCraftVelocity extends VelocityMode {

    private final BooleanProperty alternativeBypass = new BooleanProperty("Alternative Bypass", true)
            .hideIf(() -> this.module.getActiveMode() != this);

    private boolean canCancel;
    private int bypassTicks;

    public CubeCraftVelocity(final VelocityModule module) {
        super(module);
        module.addProperties(this.alternativeBypass);
    }

    @Subscribe
    public void onReceivePacket(final ReceivePacketEvent event) {
        if (mc.player == null || mc.world == null || this.module.isInvalid()) {
            return;
        }

        final Packet<?> packet = event.getPacket();
        if (packet instanceof EntityDamageS2CPacket damagePacket && damagePacket.entityId() == mc.player.getId()) {
            this.canCancel = true;
            return;
        }

        if (((packet instanceof EntityVelocityUpdateS2CPacket velocityPacket && velocityPacket.getEntityId() == mc.player.getId())
                || packet instanceof ExplosionS2CPacket) && this.canCancel) {
            event.setCancelled();
            this.bypassTicks = 1;
            this.canCancel = false;
        }
    }

    @Subscribe
    public void onPreGameTick(final PreGameTickEvent event) {
        if (mc.player == null || mc.world == null || this.module.isInvalid()) {
            this.reset();
            return;
        }

        if (this.bypassTicks > 0 && --this.bypassTicks == 0) {
            this.sendBypassPackets();
        }
    }

    private void sendBypassPackets() {
        final int repeats = this.alternativeBypass.getValue() ? 4 : 1;
        for (int i = 0; i < repeats; i++) {
            this.sendPacketSilent(new PlayerMoveC2SPacket.Full(
                    mc.player.getX(),
                    mc.player.getY(),
                    mc.player.getZ(),
                    mc.player.getYaw(),
                    mc.player.getPitch(),
                    mc.player.isOnGround(),
                    mc.player.horizontalCollision
            ));
        }

        final Direction facing = mc.player.getHorizontalFacing().getOpposite();
        this.sendPacketSilent(new PlayerActionC2SPacket(
                PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK,
                mc.player.getBlockPos(),
                facing
        ));
    }

    private void sendPacketSilent(final Packet<?> packet) {
        if (mc.getNetworkHandler() != null && mc.getNetworkHandler().getConnection() instanceof ClientConnectionAccess access) {
            access.opal$sendPacketSilent(packet);
        }
    }

    private void reset() {
        this.canCancel = false;
        this.bypassTicks = 0;
    }

    @Override
    public void onEnable() {
        super.onEnable();
        this.reset();
    }

    @Override
    public void onDisable() {
        this.reset();
        super.onDisable();
    }

    @Override
    public Enum<?> getEnumValue() {
        return VelocityModule.Mode.CUBECRAFT;
    }
}

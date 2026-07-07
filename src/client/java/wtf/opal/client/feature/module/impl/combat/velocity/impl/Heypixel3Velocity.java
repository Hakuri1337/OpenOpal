package wtf.opal.client.feature.module.impl.combat.velocity.impl;

import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntityDamageS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.math.Vec3d;
import wtf.opal.client.feature.module.impl.combat.velocity.VelocityMode;
import wtf.opal.client.feature.module.impl.combat.velocity.VelocityModule;
import wtf.opal.client.feature.module.property.impl.number.NumberProperty;
import wtf.opal.event.impl.game.PreGameTickEvent;
import wtf.opal.event.impl.game.packet.ReceivePacketEvent;
import wtf.opal.event.subscriber.Subscribe;

import java.util.ArrayDeque;
import java.util.Queue;

import static wtf.opal.client.Constants.mc;

public final class Heypixel3Velocity extends VelocityMode {

    private final NumberProperty maxDelayTicks = new NumberProperty("Delay SPacket Ticks", 5, 1, 60, 1)
            .hideIf(() -> this.module.getActiveMode() != this);

    private final Queue<Packet<?>> delayedPackets = new ArrayDeque<>();
    private int delayTicks;
    private int attackCount;
    private boolean shouldFlush;
    private Vec3d pendingVelocity;
    private int hurtWindowTicks;

    public Heypixel3Velocity(final VelocityModule module) {
        super(module);
        module.addProperties(this.maxDelayTicks);
    }

    @Subscribe
    public void onReceivePacket(final ReceivePacketEvent event) {
        if (mc.player == null || mc.world == null) {
            return;
        }

        final Packet<?> packet = event.getPacket();

        if (packet instanceof EntityDamageS2CPacket damagePacket && damagePacket.entityId() == mc.player.getId()) {
            this.hurtWindowTicks = 3;
            return;
        }

        if (packet instanceof EntityVelocityUpdateS2CPacket velocityPacket && velocityPacket.getEntityId() == mc.player.getId()) {
            if (this.hurtWindowTicks <= 0) {
                return;
            }

            if (this.delayTicks > 0 || this.shouldFlush) {
                if (this.delayTicks > 0 && this.canTriggerAttackCountNow()) {
                    this.attackCount = 1;
                    this.attackCount--;
                    this.delayTicks = 0;
                    this.pendingVelocity = null;
                    this.shouldFlush = true;
                }
                this.hurtWindowTicks = 0;
                event.setCancelled();
                return;
            }

            if (this.attackCount > 0 && this.canTriggerAttackCountNow()) {
                this.attackCount--;
                this.hurtWindowTicks = 0;
                event.setCancelled();
                return;
            }

            this.delayTicks = this.maxDelayTicks.getValue().intValue();
            this.shouldFlush = false;
            this.delayedPackets.clear();
            this.pendingVelocity = velocityPacket.getVelocity();
            this.hurtWindowTicks = 0;
            event.setCancelled();
            return;
        }

        if (this.delayTicks > 0) {
            if (packet instanceof PlayerPositionLookS2CPacket) {
                this.delayTicks = 0;
                this.shouldFlush = true;
                return;
            }

            this.delayedPackets.add(packet);
            event.setCancelled();
        }
    }

    @Subscribe
    public void onPreTick(final PreGameTickEvent event) {
        if (mc.player == null) {
            return;
        }

        if (this.delayTicks > 0) {
            this.delayTicks--;
            if (this.delayTicks == 0) {
                this.shouldFlush = true;
            }
        }

        if (this.hurtWindowTicks > 0) {
            this.hurtWindowTicks--;
        }

        if (this.shouldFlush) {
            this.flushDelayedPackets();
            this.shouldFlush = false;
        }
    }

    @Override
    public void onEnable() {
        super.onEnable();
        this.delayedPackets.clear();
        this.delayTicks = 0;
        this.shouldFlush = false;
        this.pendingVelocity = null;
        this.hurtWindowTicks = 0;
        this.attackCount = 1;
    }

    private void flushDelayedPackets() {
        if (mc.getNetworkHandler() == null) {
            this.delayedPackets.clear();
            this.delayTicks = 0;
            this.shouldFlush = false;
            this.pendingVelocity = null;
            this.hurtWindowTicks = 0;
            return;
        }

        if (this.pendingVelocity != null && mc.player != null) {
            mc.player.setVelocityClient(this.pendingVelocity);
            this.pendingVelocity = null;
        }

        while (!this.delayedPackets.isEmpty()) {
            final Packet<?> packet = this.delayedPackets.poll();
            if (packet != null) {
                //noinspection rawtypes,unchecked
                ((Packet) packet).apply(mc.getNetworkHandler());
            }
        }

        this.delayTicks = 0;
    }

    private boolean canTriggerAttackCountNow() {
        return mc.player != null
                && mc.player.isSprinting()
                && !mc.player.isSneaking()
                && mc.player.forwardSpeed > 0.0F
                && !mc.player.isUsingItem();
    }

    @Override
    public void onDisable() {
        this.flushDelayedPackets();
        this.shouldFlush = false;
        this.attackCount = 1;
        this.pendingVelocity = null;
        this.hurtWindowTicks = 0;
        super.onDisable();
    }

    @Override
    public Enum<?> getEnumValue() {
        return VelocityModule.Mode.BUFFER;
    }

    @Override
    public String getSuffix() {
        if (this.delayTicks > 0) {
            return "Buffer " + (this.maxDelayTicks.getValue().intValue() - this.delayTicks) + "Ticks";
        }
        return "Buffer";
    }

    @Override
    public boolean isDelaying() {
        return this.delayTicks > 0;
    }

    @Override
    public boolean hasQueuedPackets() {
        return !this.delayedPackets.isEmpty() || this.shouldFlush;
    }

    @Override
    public boolean shouldStopBacktrack() {
        return this.isDelaying() || this.hasQueuedPackets();
    }
}

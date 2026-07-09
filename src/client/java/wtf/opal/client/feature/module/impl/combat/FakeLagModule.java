package wtf.opal.client.feature.module.impl.combat;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.consume.UseAction;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.common.ResourcePackStatusC2SPacket;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSignC2SPacket;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket;
import net.minecraft.network.packet.s2c.play.HealthUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import wtf.opal.client.feature.helper.impl.LocalDataWatch;
import wtf.opal.client.feature.module.Module;
import wtf.opal.client.feature.module.ModuleCategory;
import wtf.opal.client.feature.module.impl.utility.AntiBotsModule;
import wtf.opal.client.feature.module.property.impl.bool.BooleanProperty;
import wtf.opal.client.feature.module.property.impl.bool.MultipleBooleanProperty;
import wtf.opal.client.feature.module.property.impl.mode.ModeProperty;
import wtf.opal.client.feature.module.property.impl.number.BoundedNumberProperty;
import wtf.opal.client.feature.module.property.impl.number.NumberProperty;
import wtf.opal.duck.ClientConnectionAccess;
import wtf.opal.event.impl.game.PreGameTickEvent;
import wtf.opal.event.impl.game.packet.ReceivePacketEvent;
import wtf.opal.event.impl.game.packet.SendPacketEvent;
import wtf.opal.event.subscriber.Subscribe;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import static wtf.opal.client.Constants.mc;

public final class FakeLagModule extends Module {

    private enum Mode {
        CONSTANT("Constant"),
        DYNAMIC("Dynamic");

        private final String name;

        Mode(final String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    private record QueuedPacket(Packet<?> packet, long timestamp, Vec3d position) {
    }

    private final BoundedNumberProperty range = new BoundedNumberProperty("Range", 2.0D, 5.0D, 0.0D, 10.0D, 0.1D);
    private final BoundedNumberProperty delay = new BoundedNumberProperty("Delay", "ms", 300.0D, 600.0D, 0.0D, 1000.0D, 10.0D);
    private final NumberProperty recoilTime = new NumberProperty("Recoil Time", "ms", 250.0D, 0.0D, 1000.0D, 10.0D);
    private final ModeProperty<Mode> mode = new ModeProperty<>("Mode", Mode.DYNAMIC);
    private final MultipleBooleanProperty flushOn = new MultipleBooleanProperty("Flush On",
            new BooleanProperty("Entity Interact", true),
            new BooleanProperty("Block Interact", true),
            new BooleanProperty("Action", true));

    private final Queue<QueuedPacket> packets = new ConcurrentLinkedQueue<>();
    private long lastFlush;
    private long nextDelay;
    private boolean flushing;
    private boolean enemyNearby;

    public FakeLagModule() {
        super("FakeLag", "Queues outgoing packets while enemies are nearby.", ModuleCategory.COMBAT);
        this.addProperties(this.range, this.delay, this.recoilTime, this.mode, this.flushOn);
    }

    @Subscribe
    public void onPreGameTick(final PreGameTickEvent event) {
        if (mc.player == null || mc.world == null) {
            this.packets.clear();
            return;
        }

        this.enemyNearby = this.findEnemy(this.range.getValue().second) != null;
        if (!this.packets.isEmpty() && System.currentTimeMillis() - this.packets.peek().timestamp() >= this.nextDelay) {
            this.flushQueuedPackets();
            this.nextDelay = Math.max(0L, this.delay.getRandomValue().longValue());
        }
    }

    @Subscribe
    public void onSendPacket(final SendPacketEvent event) {
        if (this.flushing || mc.player == null || mc.world == null || mc.player.isDead() || mc.player.isTouchingWater()
                || mc.currentScreen != null) {
            return;
        }

        final Packet<?> packet = event.getPacket();
        if (this.shouldFlushOn(packet) || packet instanceof ResourcePackStatusC2SPacket) {
            this.flushQueuedPackets();
            this.lastFlush = System.currentTimeMillis();
            return;
        }

        if (System.currentTimeMillis() - this.lastFlush < this.recoilTime.getValue().longValue()) {
            return;
        }

        if (mc.player.isUsingItem() && this.isConsumable(mc.player.getActiveItem())) {
            return;
        }

        if (this.mode.getValue() == Mode.DYNAMIC && !this.shouldLagDynamically()) {
            return;
        }

        event.setCancelled();
        this.packets.add(new QueuedPacket(packet, System.currentTimeMillis(), this.getMovePosition(packet)));
    }

    @Subscribe
    public void onReceivePacket(final ReceivePacketEvent event) {
        if (this.flushing || mc.player == null || mc.world == null) {
            return;
        }

        final Packet<?> packet = event.getPacket();
        boolean shouldFlush = packet instanceof PlayerPositionLookS2CPacket || packet instanceof HealthUpdateS2CPacket;

        if (packet instanceof EntityVelocityUpdateS2CPacket velocityPacket
                && velocityPacket.getEntityId() == mc.player.getId()
                && (velocityPacket.getVelocity().x != 0.0D || velocityPacket.getVelocity().y != 0.0D || velocityPacket.getVelocity().z != 0.0D)) {
            shouldFlush = true;
        }

        if (packet instanceof ExplosionS2CPacket explosionPacket) {
            shouldFlush = explosionPacket.playerKnockback()
                    .map(knockback -> knockback.x != 0.0D || knockback.y != 0.0D || knockback.z != 0.0D)
                    .orElse(false);
        }

        if (shouldFlush) {
            this.flushQueuedPackets();
            this.lastFlush = System.currentTimeMillis();
        }
    }

    private boolean shouldLagDynamically() {
        if (!this.enemyNearby || mc.player == null) {
            return false;
        }

        final Vec3d serverPosition = this.getFirstQueuedPosition();
        if (serverPosition == null) {
            return true;
        }

        final double maxRange = this.range.getValue().second;
        final Vec3d clientPosition = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        final Box playerBox = mc.player.getBoundingBox().offset(serverPosition.subtract(clientPosition));
        boolean intersects = false;
        double serverDistance = Double.MAX_VALUE;
        double clientDistance = Double.MAX_VALUE;

        for (final Entity entity : mc.world.getEntities()) {
            if (!this.isValidEnemy(entity)) {
                continue;
            }

            final Vec3d entityPosition = new Vec3d(entity.getX(), entity.getY(), entity.getZ());
            final double distanceToServer = entityPosition.distanceTo(serverPosition);
            final double distanceToClient = entityPosition.distanceTo(clientPosition);
            if (distanceToServer > maxRange) {
                continue;
            }

            intersects |= entity.getBoundingBox().intersects(playerBox);
            serverDistance = Math.min(serverDistance, distanceToServer);
            clientDistance = Math.min(clientDistance, distanceToClient);
        }

        if (serverDistance == Double.MAX_VALUE) {
            return false;
        }

        return serverDistance >= clientDistance && !intersects;
    }

    private Entity findEnemy(final double range) {
        if (mc.player == null || mc.world == null) {
            return null;
        }

        Entity nearest = null;
        double bestDistance = range;
        for (final Entity entity : mc.world.getEntities()) {
            if (!this.isValidEnemy(entity)) {
                continue;
            }

            final double distance = mc.player.distanceTo(entity);
            if (distance < bestDistance) {
                bestDistance = distance;
                nearest = entity;
            }
        }
        return nearest;
    }

    private boolean isValidEnemy(final Entity entity) {
        if (entity == mc.player || !(entity instanceof LivingEntity livingEntity) || entity.isRemoved()
                || livingEntity.isDead() || livingEntity.getHealth() <= 0.0F) {
            return false;
        }
        if (AntiBotsModule.isBot(entity) || AntiBotsModule.isBedWarsBot(entity) || TeamsModule.isTeammate(entity)) {
            return false;
        }
        return !LocalDataWatch.getFriendList().contains(entity.getName().getString().toUpperCase());
    }

    private Vec3d getFirstQueuedPosition() {
        for (final QueuedPacket queuedPacket : this.packets) {
            if (queuedPacket.position() != null) {
                return queuedPacket.position();
            }
        }
        return null;
    }

    private Vec3d getMovePosition(final Packet<?> packet) {
        if (mc.player == null || !(packet instanceof PlayerMoveC2SPacket movePacket) || !movePacket.changesPosition()) {
            return null;
        }
        return new Vec3d(
                movePacket.getX(mc.player.getX()),
                movePacket.getY(mc.player.getY()),
                movePacket.getZ(mc.player.getZ())
        );
    }

    private boolean isConsumable(final ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }

        final UseAction action = stack.getUseAction();
        return stack.contains(DataComponentTypes.FOOD) || action == UseAction.EAT || action == UseAction.DRINK;
    }

    private boolean shouldFlushOn(final Packet<?> packet) {
        if (this.flushOn.getProperty("Entity Interact").getValue()
                && (packet instanceof PlayerInteractEntityC2SPacket || packet instanceof HandSwingC2SPacket)) {
            return true;
        }
        if (this.flushOn.getProperty("Block Interact").getValue()
                && (packet instanceof PlayerInteractBlockC2SPacket || packet instanceof UpdateSignC2SPacket)) {
            return true;
        }
        return this.flushOn.getProperty("Action").getValue() && packet instanceof PlayerActionC2SPacket;
    }

    private void flushQueuedPackets() {
        if (mc.getNetworkHandler() == null || !(mc.getNetworkHandler().getConnection() instanceof ClientConnectionAccess access)) {
            this.packets.clear();
            return;
        }

        this.flushing = true;
        try {
            QueuedPacket queuedPacket;
            while ((queuedPacket = this.packets.poll()) != null) {
                access.opal$sendPacketSilent(queuedPacket.packet());
            }
        } finally {
            this.flushing = false;
        }
    }

    @Override
    protected void onEnable() {
        this.packets.clear();
        this.lastFlush = 0L;
        this.nextDelay = Math.max(0L, this.delay.getRandomValue().longValue());
        this.enemyNearby = false;
        super.onEnable();
    }

    @Override
    protected void onDisable() {
        this.flushQueuedPackets();
        this.enemyNearby = false;
        super.onDisable();
    }
}

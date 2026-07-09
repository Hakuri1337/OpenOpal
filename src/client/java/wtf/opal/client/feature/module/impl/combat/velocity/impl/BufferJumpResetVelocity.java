package wtf.opal.client.feature.module.impl.combat.velocity.impl;

import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.common.DisconnectS2CPacket;
import net.minecraft.network.packet.s2c.play.ChatMessageS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityDamageS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.GameJoinS2CPacket;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerRespawnS2CPacket;
import net.minecraft.network.packet.s2c.play.WorldTimeUpdateS2CPacket;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import wtf.opal.client.OpalClient;
import wtf.opal.client.feature.helper.impl.player.rotation.RotationHelper;
import wtf.opal.client.feature.helper.impl.player.rotation.model.impl.InstantRotationModel;
import wtf.opal.client.feature.module.impl.combat.velocity.VelocityMode;
import wtf.opal.client.feature.module.impl.combat.velocity.VelocityModule;
import wtf.opal.client.feature.module.impl.world.scaffold.ScaffoldModule;
import wtf.opal.client.feature.module.property.impl.bool.BooleanProperty;
import wtf.opal.client.feature.module.property.impl.number.NumberProperty;
import wtf.opal.event.impl.game.PreGameTickEvent;
import wtf.opal.event.impl.game.input.MoveInputEvent;
import wtf.opal.event.impl.game.packet.ReceivePacketEvent;
import wtf.opal.event.subscriber.Subscribe;
import wtf.opal.mixin.LivingEntityAccessor;
import wtf.opal.utility.misc.chat.ChatUtility;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import static wtf.opal.client.Constants.mc;

public final class BufferJumpResetVelocity extends VelocityMode {

    private enum Phase {
        IDLE,
        AIR,
        GROUND
    }

    private final BooleanProperty rotate = new BooleanProperty("Rotate", true)
            .hideIf(() -> this.module.getActiveMode() != this);
    private final BooleanProperty followDirection = new BooleanProperty("Follow Direction", false)
            .hideIf(() -> this.module.getActiveMode() != this);
    private final NumberProperty rotateTicks = new NumberProperty("Rotate Ticks", 10.0D, 3.0D, 20.0D, 1.0D)
            .hideIf(() -> this.module.getActiveMode() != this || (!this.rotate.getValue() && !this.followDirection.getValue()));
    private final NumberProperty airDelay = new NumberProperty("Air Delay", "ticks", 12.0D, 1.0D, 40.0D, 1.0D)
            .hideIf(() -> this.module.getActiveMode() != this);
    private final NumberProperty groundDelay = new NumberProperty("Ground Delay", "ticks", 6.0D, 1.0D, 30.0D, 1.0D)
            .hideIf(() -> this.module.getActiveMode() != this);
    private final BooleanProperty debug = new BooleanProperty("Debug", false)
            .hideIf(() -> this.module.getActiveMode() != this);

    private final Queue<Packet<?>> delayedPackets = new ConcurrentLinkedQueue<>();
    private Phase phase = Phase.IDLE;
    private Vec3d pendingVelocity;
    private Vec2f heldRotation;
    private Vec2f delayedGroundRotation;
    private int hurtWindowTicks;
    private int delayTicks;
    private int rotationHeldTicks;
    private int jumpTicks;
    private int attackCount;
    private boolean shouldFlush;

    public BufferJumpResetVelocity(final VelocityModule module) {
        super(module);
        module.addProperties(this.rotate, this.followDirection, this.rotateTicks, this.airDelay, this.groundDelay, this.debug);
    }

    @Subscribe(priority = 1)
    public void onReceivePacket(final ReceivePacketEvent event) {
        if (mc.player == null || mc.world == null || this.module.isInvalid()) {
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

            if (this.isBuffering()) {
                if (this.delayTicks > 0 && this.canTriggerAttackCountNow()) {
                    this.attackCount = 1;
                    this.attackCount--;
                    this.pendingVelocity = null;
                    this.shouldFlush = true;
                    this.debugLog("cancel buffered velocity by sprint trigger");
                }
                this.hurtWindowTicks = 0;
                event.setCancelled();
                return;
            }

            if (this.attackCount > 0 && this.canTriggerAttackCountNow()) {
                this.attackCount--;
                this.hurtWindowTicks = 0;
                event.setCancelled();
                this.debugLog("cancel velocity by sprint trigger");
                return;
            }

            this.startBuffer(velocityPacket.getVelocity());
            this.hurtWindowTicks = 0;
            event.setCancelled();
            return;
        }

        if (!this.isBuffering()) {
            return;
        }

        if (this.isPassthroughPacket(packet)) {
            return;
        }

        if (packet instanceof DisconnectS2CPacket || packet instanceof PlayerRespawnS2CPacket || packet instanceof GameJoinS2CPacket) {
            this.flushDelayedPackets(false);
            this.resetAll();
            return;
        }

        if (packet instanceof PlayerPositionLookS2CPacket) {
            this.flushDelayedPackets(false);
            this.resetDelayState();
            this.clearRotation();
            return;
        }

        event.setCancelled();
        this.delayedPackets.add(packet);
    }

    @Subscribe
    public void onPreTick(final PreGameTickEvent event) {
        if (mc.player == null || mc.world == null || this.module.isInvalid()) {
            if (this.isBuffering()) {
                this.flushDelayedPackets(false);
            }
            this.resetAll();
            return;
        }

        if (this.hurtWindowTicks > 0) {
            this.hurtWindowTicks--;
        }

        if (this.shouldFlush) {
            this.debugLog("flush requested");
            this.flushDelayedPackets(false);
            this.resetDelayState();
            this.clearRotation();
            this.shouldFlush = false;
        } else if (this.phase == Phase.AIR) {
            if (mc.player.isOnGround() || this.delayTicks-- <= 0) {
                this.debugLog("flush air");
                this.flushDelayedPackets(false);
                this.resetDelayState();
            }
        } else if (this.phase == Phase.GROUND) {
            if (this.delayTicks-- <= 0) {
                final Vec2f rotation = this.delayedGroundRotation;
                this.debugLog("flush ground");
                this.flushDelayedPackets(false);
                this.resetDelayState();
                this.setHeldRotation(rotation);
                this.jumpTicks = 1;
            }
        }

        this.tickHeldRotation();
    }

    @Subscribe(priority = 2)
    public void onMoveInput(final MoveInputEvent event) {
        if (mc.player == null) {
            return;
        }

        if (this.followDirection.getValue() && this.heldRotation != null) {
            event.setForward(1.0F);
            event.setSideways(0.0F);
        }

        if (this.jumpTicks > 0 && !this.isScaffoldEnabled()) {
            ((LivingEntityAccessor) mc.player).setJumpingCooldown(0);
            event.setJump(true);
            this.jumpTicks--;
            this.debugLog("jump reset");
        }
    }

    private void startBuffer(final Vec3d velocity) {
        this.delayedPackets.clear();
        this.pendingVelocity = velocity;
        this.shouldFlush = false;

        final Vec2f knockbackRotation = this.getKnockbackRotation(velocity);
        if (mc.player.isOnGround()) {
            this.phase = Phase.GROUND;
            this.delayTicks = this.groundDelay.getValue().intValue();
            this.delayedGroundRotation = knockbackRotation;
            this.debugLog("ground delay=" + this.delayTicks + ", speed=" + this.horizontalSpeed(velocity));
        } else {
            this.phase = Phase.AIR;
            this.delayTicks = this.airDelay.getValue().intValue();
            this.setHeldRotation(knockbackRotation);
            this.debugLog("air delay=" + this.delayTicks + ", speed=" + this.horizontalSpeed(velocity));
        }
    }

    private Vec2f getKnockbackRotation(final Vec3d velocity) {
        if (!this.rotate.getValue() && !this.followDirection.getValue()) {
            return null;
        }

        final float yaw = (float) Math.toDegrees(Math.atan2(velocity.x, -velocity.z));
        return new Vec2f(yaw, mc.player.getPitch());
    }

    private void tickHeldRotation() {
        if (this.heldRotation == null || mc.player == null) {
            return;
        }

        RotationHelper.getHandler().rotate(this.heldRotation, InstantRotationModel.INSTANCE);
        this.rotationHeldTicks++;
        if (mc.player.hurtTime == 0
                || this.rotationHeldTicks > this.rotateTicks.getValue().intValue()
                || (!this.rotate.getValue() && !this.followDirection.getValue())) {
            this.clearRotation();
        }
    }

    private void setHeldRotation(final Vec2f rotation) {
        if (rotation == null) {
            return;
        }

        this.heldRotation = rotation;
        this.rotationHeldTicks = 0;
        RotationHelper.getHandler().rotate(rotation, InstantRotationModel.INSTANCE);
    }

    private void clearRotation() {
        this.heldRotation = null;
        this.rotationHeldTicks = 0;
    }

    private void flushDelayedPackets(final boolean clearOnly) {
        if (clearOnly || mc.getNetworkHandler() == null) {
            this.delayedPackets.clear();
            this.pendingVelocity = null;
            return;
        }

        if (this.pendingVelocity != null && mc.player != null) {
            mc.player.setVelocityClient(this.pendingVelocity);
            this.pendingVelocity = null;
        }

        final ClientPlayNetworkHandler networkHandler = mc.getNetworkHandler();
        while (!this.delayedPackets.isEmpty()) {
            final Packet<?> packet = this.delayedPackets.poll();
            if (packet == null) {
                continue;
            }
            try {
                //noinspection rawtypes,unchecked
                ((Packet) packet).apply(networkHandler);
            } catch (Exception ignored) {
                this.delayedPackets.clear();
                return;
            }
        }
    }

    private boolean isPassthroughPacket(final Packet<?> packet) {
        return packet instanceof ChatMessageS2CPacket
                || packet instanceof GameMessageS2CPacket
                || packet instanceof WorldTimeUpdateS2CPacket;
    }

    private boolean canTriggerAttackCountNow() {
        return mc.player != null
                && mc.player.isSprinting()
                && !mc.player.isSneaking()
                && mc.player.forwardSpeed > 0.0F
                && !mc.player.isUsingItem();
    }

    private boolean isBuffering() {
        return this.phase != Phase.IDLE || this.shouldFlush;
    }

    private boolean isScaffoldEnabled() {
        final ScaffoldModule scaffold = OpalClient.getInstance().getModuleRepository().getModule(ScaffoldModule.class);
        return scaffold != null && scaffold.isEnabled();
    }

    private String horizontalSpeed(final Vec3d velocity) {
        final double speed = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
        return String.format(java.util.Locale.ROOT, "%.3f", speed);
    }

    private void resetDelayState() {
        this.phase = Phase.IDLE;
        this.delayTicks = 0;
        this.delayedGroundRotation = null;
        this.pendingVelocity = null;
        this.shouldFlush = false;
        this.delayedPackets.clear();
    }

    private void resetAll() {
        this.resetDelayState();
        this.hurtWindowTicks = 0;
        this.jumpTicks = 0;
        this.clearRotation();
    }

    private void debugLog(final String message) {
        if (!this.debug.getValue()) {
            return;
        }
        final String text = "AntiKB BufferJumpReset | " + message;
        if (mc.isOnThread()) {
            ChatUtility.print(text);
        } else {
            mc.execute(() -> ChatUtility.print(text));
        }
    }

    @Override
    public void onEnable() {
        super.onEnable();
        this.attackCount = 1;
        this.resetAll();
    }

    @Override
    public void onDisable() {
        this.flushDelayedPackets(false);
        this.attackCount = 1;
        this.resetAll();
        super.onDisable();
    }

    @Override
    public boolean isDelaying() {
        return this.isBuffering();
    }

    @Override
    public boolean hasQueuedPackets() {
        return !this.delayedPackets.isEmpty() || this.shouldFlush;
    }

    @Override
    public boolean shouldStopBacktrack() {
        return this.isDelaying() || this.hasQueuedPackets();
    }

    @Override
    public Enum<?> getEnumValue() {
        return VelocityModule.Mode.BUFFER_JUMP_RESET;
    }

    @Override
    public String getSuffix() {
        return this.phase == Phase.IDLE ? "BufferJumpReset" : "BufferJumpReset " + this.phase.name();
    }
}

package wtf.opal.client.feature.module.impl.combat.velocity.impl;

import net.minecraft.block.Blocks;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.common.CommonPingS2CPacket;
import net.minecraft.network.packet.s2c.common.DisconnectS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.GameJoinS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerRespawnS2CPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Vec3d;
import wtf.opal.client.OpalClient;
import wtf.opal.client.feature.helper.impl.LocalDataWatch;
import wtf.opal.client.feature.helper.impl.player.timer.TimerHelper;
import wtf.opal.client.feature.module.impl.combat.TeamsModule;
import wtf.opal.client.feature.module.impl.combat.killaura.KillAuraModule;
import wtf.opal.client.feature.module.impl.combat.velocity.VelocityMode;
import wtf.opal.client.feature.module.impl.combat.velocity.VelocityModule;
import wtf.opal.client.feature.module.impl.movement.StuckModule;
import wtf.opal.client.feature.module.impl.utility.AntiBotsModule;
import wtf.opal.client.feature.module.impl.world.TimerModule;
import wtf.opal.client.feature.module.impl.world.scaffold.ScaffoldModule;
import wtf.opal.client.feature.module.property.impl.bool.BooleanProperty;
import wtf.opal.client.feature.module.property.impl.number.NumberProperty;
import wtf.opal.duck.ClientConnectionAccess;
import wtf.opal.event.impl.game.PreGameTickEvent;
import wtf.opal.event.impl.game.input.MoveInputEvent;
import wtf.opal.event.impl.game.input.PostHandleInputEvent;
import wtf.opal.event.impl.game.packet.ReceivePacketEvent;
import wtf.opal.event.impl.game.player.movement.PreMovementPacketEvent;
import wtf.opal.event.impl.game.server.ServerDisconnectEvent;
import wtf.opal.event.subscriber.Subscribe;
import wtf.opal.utility.misc.chat.ChatUtility;
import wtf.opal.utility.player.PlayerUtility;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import static wtf.opal.client.Constants.mc;

/**
 * OpenZen NoXZ port. Horizontal knockback is reduced only by real attacks;
 * this mode never directly zeros or scales an incoming velocity packet.
 */
public final class NoXZVelocity extends VelocityMode {

    private static final int SUSPEND_TIMEOUT_TICKS = 12;
    private static final int MAX_TRANSACTION_PACKETS = 128;
    private static final double MAX_TARGET_RANGE = 3.7D;

    private enum Phase {
        IDLE,
        SUSPENDING,
        ATTACKING,
        INSTANT
    }

    private final NumberProperty attackAmount = new NumberProperty("Attack Amount", 5.0D, 1.0D, 5.0D, 1.0D)
            .hideIf(() -> this.module.getActiveMode() != this);
    private final BooleanProperty instantAttack = new BooleanProperty("Instant Attack", false)
            .hideIf(() -> this.module.getActiveMode() != this);
    private final BooleanProperty sprintStateCheck = new BooleanProperty("Sprint State Check", true)
            .hideIf(() -> this.module.getActiveMode() != this);
    private final BooleanProperty debug = new BooleanProperty("Debug", false)
            .hideIf(() -> this.module.getActiveMode() != this);

    private final Queue<Packet<?>> inboundPackets = new ConcurrentLinkedQueue<>();

    private Phase phase = Phase.IDLE;
    private EntityVelocityUpdateS2CPacket pendingVelocity;
    private LivingEntity attackTarget;
    private int attacksRemaining;
    private int pendingKillAuraReductions;
    private int attackWindowTicks;
    private int suspendTicks;
    private int flagCooldown;
    private int forwardPrimeTicks;
    private boolean flushInboundOnMotion;
    private boolean transactionCapReached;
    private boolean pendingKillAuraFinalization;
    private Vec3d velocityBeforeKillAuraAttack;
    private boolean flushing;

    private float instantProgress;
    private float previousTimer = 1.0F;
    private boolean ownsTimer;

    public NoXZVelocity(final VelocityModule module) {
        super(module);
        module.addProperties(this.attackAmount, this.instantAttack, this.sprintStateCheck, this.debug);
    }

    @Subscribe(priority = -1)
    public void onReceivePacket(final ReceivePacketEvent event) {
        if (this.flushing || mc.player == null || mc.world == null) {
            return;
        }

        final Packet<?> packet = event.getPacket();
        if (packet instanceof DisconnectS2CPacket || packet instanceof GameJoinS2CPacket || packet instanceof PlayerRespawnS2CPacket) {
            this.clearWithoutFlush("world reset");
            return;
        }

        if (packet instanceof PlayerPositionLookS2CPacket) {
            if (this.isSuspending()) {
                this.releaseSuspension("position correction", true);
            }
            this.clearAttackState();
            this.restoreTimer();
            this.flagCooldown = 2;
            this.debugLog("flag cooldown=2");
            return;
        }

        if (this.isSuspending()) {
            if (packet instanceof EntityVelocityUpdateS2CPacket velocityPacket
                    && velocityPacket.getEntityId() == mc.player.getId()) {
                this.releaseSuspension("second velocity", true);
                this.captureVelocity(event, velocityPacket);
                return;
            }

            if (!(packet instanceof CommonPingS2CPacket)) {
                return;
            }

            this.inboundPackets.add(packet);
            if (this.inboundPackets.size() >= MAX_TRANSACTION_PACKETS) {
                this.transactionCapReached = true;
            }
            event.setCancelled();
            return;
        }

        if (packet instanceof EntityVelocityUpdateS2CPacket velocityPacket
                && velocityPacket.getEntityId() == mc.player.getId()) {
            this.captureVelocity(event, velocityPacket);
        }
    }

    private void captureVelocity(final ReceivePacketEvent event, final EntityVelocityUpdateS2CPacket velocityPacket) {
        final Vec3d velocity = velocityPacket.getVelocity();
        if (this.flagCooldown > 0 || this.module.isInvalid() || this.shouldIgnore() || velocity.y <= 0.0D) {
            return;
        }

        if (Math.abs(velocity.x) > 0.01D || Math.abs(velocity.z) > 0.01D) {
            this.forwardPrimeTicks = 2;
        }

        final LivingEntity target = this.getAttackTarget();
        final boolean canAttack = this.isValidTarget(target) && mc.player.isSprinting();
        this.debugLog("capture h=" + this.horizontalSpeed(velocity)
                + " y=" + this.format(velocity.y)
                + " ground=" + mc.player.isOnGround()
                + " target=" + this.targetName(target));

        if (mc.player.isOnGround() && canAttack) {
            this.startAttackWindow(target, this.attackAmount.getValue().intValue(), false);
            return;
        }

        this.clearAttackState();
        this.restoreTimer();
        this.pendingVelocity = velocityPacket;
        this.phase = Phase.SUSPENDING;
        this.suspendTicks = 0;
        this.instantProgress = 0.0F;
        if (this.flushInboundOnMotion) {
            this.flushInboundOnMotion = false;
            this.transactionCapReached = this.inboundPackets.size() >= MAX_TRANSACTION_PACKETS;
        } else {
            this.inboundPackets.clear();
            this.transactionCapReached = false;
        }
        event.setCancelled();
        this.debugLog("suspend reason=" + (!mc.player.isOnGround() ? "air" : !this.isValidTarget(target) ? "no target" : "not sprinting"));
    }

    @Subscribe(priority = 2)
    public void onPreGameTick(final PreGameTickEvent event) {
        if (mc.player == null || mc.world == null) {
            this.clearWithoutFlush("invalid world");
            return;
        }

        if (this.flagCooldown > 0) {
            this.flagCooldown--;
        }
        if (this.forwardPrimeTicks > 0) {
            this.forwardPrimeTicks--;
        }

        if (this.module.isInvalid() || this.shouldIgnore()) {
            if (this.isSuspending()) {
                this.releaseSuspension("invalid state", false);
            }
            this.clearAttackState();
            this.restoreTimer();
            return;
        }

        if (this.isSuspending()) {
            this.suspendTicks++;
            this.tickInstantCharge();

            final boolean onGround = mc.player.isOnGround();
            final boolean timeout = this.suspendTicks >= SUSPEND_TIMEOUT_TICKS;
            final boolean transactionCap = this.transactionCapReached;
            if (onGround || timeout || transactionCap) {
                final LivingEntity target = this.getAttackTarget();
                if (onGround && this.isValidTarget(target) && mc.player.isSprinting()) {
                    final int attacks = this.getReleaseAttackCount();
                    final boolean useInstant = this.ownsTimer && this.instantProgress > 0.0F;
                    this.releaseSuspension(onGround ? "ground" : "timeout", false, useInstant);
                    this.startAttackWindow(target, attacks, useInstant);
                } else {
                    final String reason = transactionCap ? "transaction cap" : timeout ? "timeout" : "ground without attack";
                    this.releaseSuspension(reason, false);
                    if (onGround && mc.player.isSprinting()) {
                        mc.player.setSprinting(false);
                    }
                }
            }

            if (this.isSuspending()) {
                return;
            }
        }

        this.processAttackWindow();

        if (this.attackWindowTicks > 0 && --this.attackWindowTicks == 0 && this.hasPendingAttacks()) {
            this.debugLog("attack window expired remaining=" + this.remainingAttacks());
            this.clearAttackState();
            this.restoreTimer();
        }
    }

    @Subscribe(priority = 2)
    public void onMoveInput(final MoveInputEvent event) {
        if (this.forwardPrimeTicks > 0 && !this.shouldIgnore()) {
            event.setForward(1.0F);
        }
    }

    @Subscribe(priority = 2)
    public void onPreMovementPacket(final PreMovementPacketEvent event) {
        if (this.forwardPrimeTicks > 0 && mc.player != null && mc.player.isOnGround() && mc.player.isSprinting()) {
            event.setSprinting(true);
        }

        if (this.flushInboundOnMotion) {
            this.flushInboundOnMotion = false;
            this.flushInboundPackets();
        }
    }

    @Subscribe
    public void onServerDisconnect(final ServerDisconnectEvent event) {
        this.clearWithoutFlush("disconnect");
    }

    private void processAttackWindow() {
        if (this.attackTarget == null || this.pendingKillAuraReductions > 0 || this.pendingKillAuraFinalization) {
            return;
        }
        if (this.attacksRemaining <= 0) {
            return;
        }
        if (!this.isValidTarget(this.attackTarget)) {
            this.debugLog("abort invalid target");
            this.clearAttackState();
            this.restoreTimer();
            return;
        }
        final KillAuraModule killAura = this.getKillAura();
        if (killAura != null && killAura.isEnabled()) {
            killAura.getTargeting().update();
            if (killAura.getTargeting().getTarget() != null
                    && killAura.getTargeting().getTarget().getEntity().getId() == this.attackTarget.getId()
                    && (!this.sprintStateCheck.getValue() || mc.player.isSprinting())
                    && killAura.requestVelocityResetAttack(1, 4, mc.player.isSprinting(), 0.6D)) {
                this.pendingKillAuraReductions = 1;
                this.attacksRemaining--;
                this.attackWindowTicks = Math.max(this.attackWindowTicks, 4);
                this.debugLog("request KillAura attack target=" + this.targetName(this.attackTarget)
                        + " remaining=" + this.remainingAttacks());
                return;
            }
        }

        if (!(mc.crosshairTarget instanceof EntityHitResult hitResult) || hitResult.getEntity().getId() != this.attackTarget.getId()) {
            this.debugLog("wait direct raycast target=" + this.targetName(this.attackTarget));
            return;
        }
        if (mc.interactionManager == null || mc.player.isUsingItem()) {
            return;
        }
        if (this.sprintStateCheck.getValue() && !mc.player.isSprinting()) {
            this.debugLog("wait sprint state");
            return;
        }

        final boolean wasSprinting = mc.player.isSprinting();
        if (wasSprinting) {
            mc.player.setSprinting(false);
        }
        mc.interactionManager.attackEntity(mc.player, this.attackTarget);
        mc.player.swingHand(Hand.MAIN_HAND);
        if (wasSprinting) {
            this.reduceHorizontalVelocity("direct");
        }
        this.attacksRemaining--;
        this.attackWindowTicks = Math.max(this.attackWindowTicks, 2);
        this.debugLog("direct attack remaining=" + this.attacksRemaining);
        if (this.attacksRemaining <= 0) {
            this.clearAttackState();
            this.restoreTimer();
        }
    }

    private void startAttackWindow(final LivingEntity target, final int count, final boolean useInstant) {
        this.attackTarget = target;
        this.attacksRemaining = Math.max(1, Math.min(5, count));
        this.pendingKillAuraReductions = 0;
        this.attackWindowTicks = Math.max(6, this.attacksRemaining * 4);
        this.phase = useInstant ? Phase.INSTANT : Phase.ATTACKING;
        if (useInstant && this.ownsTimer) {
            TimerHelper.getInstance().timer = 4.0F;
        } else {
            this.restoreTimer();
        }
        this.debugLog("attack start count=" + this.attacksRemaining
                + " target=" + this.targetName(target)
                + " instant=" + useInstant);
    }

    @Override
    public void onVelocityResetAttackPerformed() {
        if (this.pendingKillAuraReductions <= 0 || this.pendingKillAuraFinalization || mc.player == null) {
            return;
        }

        this.velocityBeforeKillAuraAttack = mc.player.getVelocity();
        this.pendingKillAuraFinalization = true;
    }

    @Subscribe
    public void onPostHandleInput(final PostHandleInputEvent event) {
        if (!this.pendingKillAuraFinalization || this.pendingKillAuraReductions <= 0 || mc.player == null) {
            return;
        }

        final Vec3d before = this.velocityBeforeKillAuraAttack;
        final Vec3d afterVanilla = mc.player.getVelocity();
        final double beforeHorizontal = before == null ? 0.0D : this.horizontalMagnitude(before);
        final double afterHorizontal = this.horizontalMagnitude(afterVanilla);
        final boolean vanillaReduced = beforeHorizontal > 1.0E-4D && afterHorizontal <= beforeHorizontal * 0.75D;
        if (!vanillaReduced) {
            this.reduceHorizontalVelocity("KillAura manual");
        } else {
            this.debugLog("KillAura vanilla h=" + this.horizontalSpeed(before) + " -> " + this.horizontalSpeed(afterVanilla));
        }

        this.pendingKillAuraFinalization = false;
        this.velocityBeforeKillAuraAttack = null;
        this.pendingKillAuraReductions--;
        this.attackWindowTicks = Math.max(this.attackWindowTicks, 1);
        this.debugLog("KillAura attack remaining=" + this.remainingAttacks());
        if (this.pendingKillAuraReductions <= 0 && this.attacksRemaining <= 0) {
            this.clearAttackState();
            this.restoreTimer();
        }
    }

    private void reduceHorizontalVelocity(final String source) {
        if (mc.player == null) {
            return;
        }

        final Vec3d before = mc.player.getVelocity();
        mc.player.setVelocity(before.x * 0.6D, before.y, before.z * 0.6D);
        this.debugLog(source + " h=" + this.horizontalSpeed(before) + " -> " + this.horizontalSpeed(mc.player.getVelocity()));
    }

    private void releaseSuspension(final String reason, final boolean deferInbound) {
        this.releaseSuspension(reason, deferInbound, false);
    }

    private void releaseSuspension(final String reason, final boolean deferInbound, final boolean keepTimer) {
        if (!this.isSuspending()) {
            return;
        }

        final int inboundCount = this.inboundPackets.size();
        this.flushing = true;
        try {
            this.applyPendingVelocity();
            if (deferInbound) {
                this.flushInboundOnMotion = !this.inboundPackets.isEmpty();
            } else {
                this.flushInboundPackets();
            }
        } finally {
            this.flushing = false;
        }

        this.pendingVelocity = null;
        this.suspendTicks = 0;
        this.transactionCapReached = false;
        this.phase = Phase.IDLE;
        if (!deferInbound && !keepTimer) {
            this.restoreTimer();
        }
        this.debugLog("release reason=" + reason + " transactions=" + inboundCount);
    }

    private void applyPendingVelocity() {
        if (this.pendingVelocity == null) {
            return;
        }
        this.applyInboundPacket(this.pendingVelocity);
        this.pendingVelocity = null;
    }

    private void flushInboundPackets() {
        final boolean wasFlushing = this.flushing;
        this.flushing = true;
        try {
            Packet<?> packet;
            while ((packet = this.inboundPackets.poll()) != null) {
                this.applyInboundPacket(packet);
            }
        } finally {
            this.flushing = wasFlushing;
        }
    }

    private void applyInboundPacket(final Packet<?> packet) {
        final ClientConnection connection = this.getConnection();
        if (connection instanceof ClientConnectionAccess access) {
            access.opal$channelReadSilent(packet);
            return;
        }

        final ClientPlayNetworkHandler networkHandler = mc.getNetworkHandler();
        if (networkHandler != null) {
            try {
                //noinspection rawtypes,unchecked
                ((Packet) packet).apply(networkHandler);
            } catch (Exception ignored) {
            }
        }
    }

    private ClientConnection getConnection() {
        final ClientPlayNetworkHandler networkHandler = mc.getNetworkHandler();
        return networkHandler == null ? null : networkHandler.getConnection();
    }

    private void tickInstantCharge() {
        if (!this.instantAttack.getValue()) {
            this.restoreTimer();
            return;
        }

        final TimerModule timerModule = OpalClient.getInstance().getModuleRepository().getModule(TimerModule.class);
        if (timerModule != null && timerModule.isEnabled()) {
            this.restoreTimer();
            return;
        }

        if (this.instantProgress >= 3.0F) {
            return;
        }
        if (!this.ownsTimer && !this.acquireTimer()) {
            return;
        }

        TimerHelper.getInstance().timer = 0.5F;
        this.instantProgress = Math.min(3.0F, this.instantProgress + 0.5F);
    }

    private boolean acquireTimer() {
        final TimerHelper helper = TimerHelper.getInstance();
        final TimerModule timerModule = OpalClient.getInstance().getModuleRepository().getModule(TimerModule.class);
        if (helper == null || (timerModule != null && timerModule.isEnabled())) {
            return false;
        }

        this.previousTimer = helper.timer;
        this.ownsTimer = true;
        return true;
    }

    private void restoreTimer() {
        if (!this.ownsTimer) {
            return;
        }

        final TimerHelper helper = TimerHelper.getInstance();
        if (helper != null && (Math.abs(helper.timer - 0.5F) < 0.001F || Math.abs(helper.timer - 4.0F) < 0.001F)) {
            helper.timer = this.previousTimer;
        }
        this.ownsTimer = false;
        this.previousTimer = 1.0F;
        this.instantProgress = 0.0F;
    }

    private int getReleaseAttackCount() {
        if (this.ownsTimer && this.instantProgress > 0.0F) {
            return Math.max(1, (int) this.instantProgress);
        }
        return this.attackAmount.getValue().intValue();
    }

    private LivingEntity getAttackTarget() {
        final KillAuraModule killAura = this.getKillAura();
        if (killAura != null && killAura.isEnabled()) {
            killAura.getTargeting().update();
            if (killAura.getTargeting().getTarget() != null) {
                final LivingEntity target = killAura.getTargeting().getTarget().getEntity();
                if (this.isValidTarget(target)) {
                    return target;
                }
            }
        }

        if (mc.crosshairTarget instanceof EntityHitResult hitResult && hitResult.getEntity() instanceof LivingEntity living) {
            return this.isValidTarget(living) ? living : null;
        }
        return null;
    }

    private KillAuraModule getKillAura() {
        return OpalClient.getInstance().getModuleRepository().getModule(KillAuraModule.class);
    }

    private boolean isValidTarget(final LivingEntity target) {
        if (target == null || mc.player == null || target == mc.player || target.isRemoved() || target.isDead()
                || !target.isAlive() || !target.isAttackable() || target.getHealth() <= 0.0F) {
            return false;
        }
        if (AntiBotsModule.isBot(target) || AntiBotsModule.isBedWarsBot(target) || TeamsModule.isTeammate(target)) {
            return false;
        }
        if (LocalDataWatch.getFriendList().contains(target.getName().getString().toUpperCase())) {
            return false;
        }
        return PlayerUtility.getDistanceToEntity(target) <= MAX_TARGET_RANGE;
    }

    private boolean shouldIgnore() {
        if (mc.player == null || mc.world == null || !mc.player.isAlive() || mc.player.isDead()
                || mc.player.getHealth() <= 0.0F || mc.player.isSpectator()
                || mc.player.getAbilities().flying || mc.player.isTouchingWater() || mc.player.isInLava()
                || mc.player.isOnFire() || mc.player.isClimbing() || mc.player.isSleeping()
                || mc.player.isUsingItem() || mc.world.getBlockState(mc.player.getBlockPos()).isOf(Blocks.COBWEB)) {
            return true;
        }

        final StuckModule stuck = OpalClient.getInstance().getModuleRepository().getModule(StuckModule.class);
        final ScaffoldModule scaffold = OpalClient.getInstance().getModuleRepository().getModule(ScaffoldModule.class);
        return (stuck != null && stuck.isEnabled()) || (scaffold != null && scaffold.isEnabled());
    }

    private boolean isSuspending() {
        return this.phase == Phase.SUSPENDING;
    }

    private boolean hasPendingAttacks() {
        return this.attacksRemaining > 0 || this.pendingKillAuraReductions > 0;
    }

    private int remainingAttacks() {
        return this.attacksRemaining + this.pendingKillAuraReductions;
    }

    private void clearAttackState() {
        this.attackTarget = null;
        this.attacksRemaining = 0;
        this.pendingKillAuraReductions = 0;
        this.pendingKillAuraFinalization = false;
        this.velocityBeforeKillAuraAttack = null;
        this.attackWindowTicks = 0;
        if (!this.isSuspending()) {
            this.phase = Phase.IDLE;
        }
    }

    private void clearWithoutFlush(final String reason) {
        this.pendingVelocity = null;
        this.inboundPackets.clear();
        this.flushInboundOnMotion = false;
        this.transactionCapReached = false;
        this.suspendTicks = 0;
        this.forwardPrimeTicks = 0;
        this.clearAttackState();
        this.phase = Phase.IDLE;
        this.restoreTimer();
        this.debugLog("clear reason=" + reason);
    }

    private String targetName(final Entity entity) {
        return entity == null ? "none" : entity.getName().getString();
    }

    private String horizontalSpeed(final Vec3d velocity) {
        return this.format(this.horizontalMagnitude(velocity));
    }

    private double horizontalMagnitude(final Vec3d velocity) {
        return velocity == null ? 0.0D : Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
    }

    private String format(final double value) {
        return String.format(java.util.Locale.ROOT, "%.3f", value);
    }

    private void debugLog(final String message) {
        if (!this.debug.getValue()) {
            return;
        }

        final String text = "AntiKB NoXZ | " + message;
        if (mc.isOnThread()) {
            ChatUtility.print(text);
        } else {
            mc.execute(() -> ChatUtility.print(text));
        }
    }

    @Override
    public void onEnable() {
        super.onEnable();
        this.flagCooldown = 0;
        this.clearWithoutFlush("enable");
    }

    @Override
    public void onDisable() {
        if (this.isSuspending()) {
            this.releaseSuspension("disable", false);
        } else {
            this.flushInboundPackets();
        }
        this.clearWithoutFlush("disable");
        super.onDisable();
    }

    @Override
    public boolean isAttacking() {
        return this.hasPendingAttacks();
    }

    @Override
    public boolean isDelaying() {
        return this.isSuspending();
    }

    @Override
    public boolean hasQueuedPackets() {
        return this.pendingVelocity != null || !this.inboundPackets.isEmpty();
    }

    @Override
    public boolean shouldStopBacktrack() {
        return this.isSuspending() || this.hasQueuedPackets() || this.hasPendingAttacks();
    }

    @Override
    public Enum<?> getEnumValue() {
        return VelocityModule.Mode.NO_XZ;
    }

    @Override
    public String getSuffix() {
        return switch (this.phase) {
            case SUSPENDING -> "NoXZ Wait " + this.suspendTicks + "t";
            case ATTACKING -> "NoXZ Attack " + this.remainingAttacks();
            case INSTANT -> "NoXZ Instant " + this.remainingAttacks();
            default -> "NoXZ";
        };
    }
}

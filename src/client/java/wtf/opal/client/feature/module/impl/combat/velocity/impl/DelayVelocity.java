package wtf.opal.client.feature.module.impl.combat.velocity.impl;

import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.common.DisconnectS2CPacket;
import net.minecraft.network.packet.s2c.play.ChatMessageS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityDamageS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.GameJoinS2CPacket;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerRespawnS2CPacket;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import wtf.opal.client.OpalClient;
import wtf.opal.client.feature.helper.impl.LocalDataWatch;
import wtf.opal.client.feature.helper.impl.player.rotation.RotationHelper;
import wtf.opal.client.feature.helper.impl.player.rotation.model.impl.InstantRotationModel;
import wtf.opal.client.feature.module.impl.combat.TeamsModule;
import wtf.opal.client.feature.module.impl.combat.killaura.KillAuraModule;
import wtf.opal.client.feature.module.impl.combat.velocity.VelocityMode;
import wtf.opal.client.feature.module.impl.combat.velocity.VelocityModule;
import wtf.opal.client.feature.module.impl.utility.AntiBotsModule;
import wtf.opal.client.feature.module.property.impl.bool.BooleanProperty;
import wtf.opal.client.feature.module.property.impl.number.NumberProperty;
import wtf.opal.event.impl.game.PreGameTickEvent;
import wtf.opal.event.impl.game.input.MoveInputEvent;
import wtf.opal.event.impl.game.packet.ReceivePacketEvent;
import wtf.opal.event.subscriber.Subscribe;
import wtf.opal.utility.misc.chat.ChatUtility;
import wtf.opal.utility.player.RotationUtility;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import static wtf.opal.client.Constants.mc;

public final class DelayVelocity extends VelocityMode {

    private static final int DEFAULT_ATTACK_COUNT = 4;
    private static final boolean DEFAULT_AUTO_ATTACK_COUNT = true;
    private static final double DEFAULT_ALINK_TARGET_RANGE = 10.0D;
    private static final int DEFAULT_ALINK_MAX_DELAY = 60;

    private final BooleanProperty killAuraAttack = new BooleanProperty("KillAura Attack", true)
            .hideIf(() -> this.module.getActiveMode() != this);
    private final NumberProperty fov = new NumberProperty("FOV", 90.0D, 30.0D, 180.0D, 1.0D)
            .hideIf(() -> this.module.getActiveMode() != this);
    private final NumberProperty alinkDelay = new NumberProperty("Alink Delay", "ticks", DEFAULT_ALINK_MAX_DELAY, 5.0D, 120.0D, 1.0D)
            .hideIf(() -> this.module.getActiveMode() != this);
    private final BooleanProperty debug = new BooleanProperty("Debug", false)
            .hideIf(() -> this.module.getActiveMode() != this);

    private Entity target;
    private int attackQueue;
    private boolean receiveDamage;
    private boolean projectileDamage;
    private boolean explosionOrFireDamage;
    private int alinkTicks;
    private String releaseReason;
    private Vec3d velocity;
    private boolean attacking;
    private boolean velocityApplied;
    private final Queue<Packet<?>> packets = new ConcurrentLinkedQueue<>();
    private int hitSelectSkipAttacks;
    private int pendingKillAuraReductions;
    private int pendingKillAuraWindowTicks;

    public DelayVelocity(final VelocityModule module) {
        super(module);
        module.addProperties(this.killAuraAttack, this.fov, this.alinkDelay, this.debug);
    }

    private void findTarget() {
        this.target = null;
        if (mc.player == null || mc.world == null) {
            return;
        }

        final KillAuraModule killAura = OpalClient.getInstance().getModuleRepository().getModule(KillAuraModule.class);
        if (killAura != null && killAura.isEnabled() && killAura.getTargeting().getTarget() != null) {
            final Entity entity = killAura.getTargeting().getTarget().getEntity();
            if (this.isValidTarget(entity)) {
                this.target = entity;
                return;
            }
        }

        if (mc.crosshairTarget instanceof EntityHitResult hitResult) {
            final Entity entity = hitResult.getEntity();
            if (this.isValidTarget(entity)) {
                this.target = entity;
                return;
            }
        }

        Entity nearest = null;
        double bestDistance = DEFAULT_ALINK_TARGET_RANGE;
        for (final Entity entity : mc.world.getEntities()) {
            if (!this.isValidTarget(entity)) {
                continue;
            }

            final double distance = mc.player.distanceTo(entity);
            if (distance < bestDistance) {
                bestDistance = distance;
                nearest = entity;
            }
        }

        if (nearest != null) {
            this.target = nearest;
        }
    }

    private boolean isValidTarget(final Entity entity) {
        if (mc.player == null || mc.world == null || entity == null || entity == mc.player || !(entity instanceof LivingEntity living)) {
            return false;
        }
        if (entity.isRemoved() || living.isDead() || !living.isAttackable() || living.getHealth() <= 0.0F) {
            return false;
        }
        if (AntiBotsModule.isBot(entity) || AntiBotsModule.isBedWarsBot(entity) || TeamsModule.isTeammate(entity)) {
            return false;
        }
        if (LocalDataWatch.getFriendList().contains(entity.getName().getString().toUpperCase())) {
            return false;
        }
        return RotationUtility.isEntityInFOV(entity, this.fov.getValue().floatValue());
    }

    private boolean rotateToTarget(final Entity entity) {
        if (mc.player == null || entity == null) {
            return false;
        }

        final Vec2f rotations = RotationUtility.getRotationFromPosition(entity.getEyePos());
        RotationHelper.getHandler().rotate(rotations, InstantRotationModel.INSTANCE);
        return true;
    }

    private boolean requestKillAuraAttacks(final int count) {
        if (count <= 0 || this.target == null || mc.player == null || !this.isValidTarget(this.target)) {
            return false;
        }

        final KillAuraModule killAura = OpalClient.getInstance().getModuleRepository().getModule(KillAuraModule.class);
        if (killAura == null || !killAura.isEnabled()) {
            return false;
        }

        killAura.getTargeting().update();
        if (killAura.getTargeting().getTarget() == null) {
            return false;
        }

        final Entity auraTarget = killAura.getTargeting().getTarget().getEntity();
        if (auraTarget == null || auraTarget.getId() != this.target.getId() || !this.isValidTarget(auraTarget)) {
            return false;
        }

        return killAura.requestVelocityResetAttack(count, Math.max(4, count * 3), mc.player.isSprinting(), 0.6D);
    }

    private void handle() {
        if (mc.getNetworkHandler() == null) {
            this.packets.clear();
            return;
        }

        final ClientPlayNetworkHandler networkHandler = mc.getNetworkHandler();
        while (!this.packets.isEmpty()) {
            final Packet<?> packet = this.packets.poll();
            if (packet != null) {
                try {
                    //noinspection rawtypes,unchecked
                    ((Packet) packet).apply(networkHandler);
                } catch (Exception ignored) {
                }
            }
        }
    }

    private int getCurrentAttackCount() {
        if (!DEFAULT_AUTO_ATTACK_COUNT) {
            return DEFAULT_ATTACK_COUNT;
        }
        if (this.velocity == null) {
            return 0;
        }

        final double speed = Math.sqrt(this.velocity.x * this.velocity.x + this.velocity.z * this.velocity.z);
        if (speed < 0.1D) {
            return 0;
        }
        if (speed < 0.3D) {
            return 3;
        }
        if (speed < 1.0D) {
            return 4;
        }
        return 5;
    }

    private void applyQueuedVelocity() {
        if (!this.velocityApplied && this.velocity != null && mc.player != null) {
            mc.player.setVelocity(this.velocity.x, this.velocity.y, this.velocity.z);
            this.velocityApplied = true;
        }
    }

    private boolean isHoldingAttackReduceItem() {
        if (mc.player == null) {
            return false;
        }

        final ItemStack stack = mc.player.getMainHandStack();
        return !stack.isEmpty()
                && (stack.isIn(ItemTags.SWORDS) || stack.isIn(ItemTags.AXES) || stack.isIn(ItemTags.PICKAXES));
    }

    private void reduceHorizontalVelocityAfterAttack(final String source) {
        if (mc.player == null) {
            return;
        }

        final Vec3d before = mc.player.getVelocity();
        final double beforeHorizontal = Math.sqrt(before.x * before.x + before.z * before.z);
        mc.player.setVelocity(before.multiply(0.6D, 1.0D, 0.6D));
        final Vec3d after = mc.player.getVelocity();
        final double afterHorizontal = Math.sqrt(after.x * after.x + after.z * after.z);
        this.debugLog(source + " reduce hSpeed="
                + String.format(java.util.Locale.ROOT, "%.3f", beforeHorizontal)
                + " -> "
                + String.format(java.util.Locale.ROOT, "%.3f", afterHorizontal));
    }

    private void clearAttackState() {
        this.target = null;
        this.attackQueue = 0;
        this.hitSelectSkipAttacks = 0;
        this.velocity = null;
        this.velocityApplied = false;
        this.attacking = false;
        this.pendingKillAuraReductions = 0;
        this.pendingKillAuraWindowTicks = 0;
    }

    private String targetName(final Entity entity) {
        return entity == null ? "none" : entity.getName().getString();
    }

    private String velocityHorizontalSpeed() {
        if (this.velocity == null) {
            return "0.00";
        }
        final double speed = Math.sqrt(this.velocity.x * this.velocity.x + this.velocity.z * this.velocity.z);
        return String.format(java.util.Locale.ROOT, "%.2f", speed);
    }

    @Subscribe
    public void onReceivePacket(final ReceivePacketEvent event) {
        if (mc.player == null || mc.world == null) {
            return;
        }

        final Packet<?> packet = event.getPacket();

        if (this.alinkTicks >= 0) {
            if (packet instanceof ChatMessageS2CPacket || packet instanceof GameMessageS2CPacket) {
                return;
            }

            if (packet instanceof DisconnectS2CPacket || packet instanceof PlayerRespawnS2CPacket || packet instanceof GameJoinS2CPacket) {
                this.handle();
                this.alinkTicks = -1;
                return;
            }

            if (packet instanceof PlayerPositionLookS2CPacket) {
                this.releaseReason = "flag";
                return;
            }

            event.setCancelled();
            this.packets.add(packet);
            return;
        }

        if (packet instanceof EntityDamageS2CPacket damagePacket && damagePacket.entityId() == mc.player.getId()) {
            this.receiveDamage = true;
            this.projectileDamage = false;
            this.explosionOrFireDamage = false;

            final DamageSource source = damagePacket.createDamageSource(mc.world);
            final String name = source.getName();
            if (name != null) {
                final String lowerName = name.toLowerCase();
                if (lowerName.contains("explosion")
                        || lowerName.contains("fire")
                        || lowerName.contains("lava")
                        || lowerName.contains("hot_floor")) {
                    this.explosionOrFireDamage = true;
                }
            }

            if (damagePacket.sourceDirectId() != -1) {
                final Entity direct = mc.world.getEntityById(damagePacket.sourceDirectId());
                if (direct instanceof ProjectileEntity) {
                    this.projectileDamage = true;
                }
            }
            this.debugLog("damage source=" + (name == null ? "unknown" : name)
                    + ", projectile=" + this.projectileDamage
                    + ", special=" + this.explosionOrFireDamage);
        }

        if (packet instanceof EntityVelocityUpdateS2CPacket velocityPacket
                && velocityPacket.getEntityId() == mc.player.getId()
                && this.receiveDamage) {
            this.receiveDamage = false;

            if (mc.player.isUsingItem()) {
                this.debugLog("skip velocity: using item");
                return;
            }
            if (this.explosionOrFireDamage) {
                this.debugLog("skip velocity: explosion/fire");
                return;
            }

            this.findTarget();
            this.velocity = velocityPacket.getVelocity();

            final int count = this.getCurrentAttackCount();
            if (count == 0) {
                this.debugLog("skip velocity: low horizontal speed=" + this.velocityHorizontalSpeed());
                return;
            }

            this.debugLog("velocity speed=" + this.velocityHorizontalSpeed()
                    + ", attacks=" + count
                    + ", target=" + this.targetName(this.target)
                    + ", ka=" + this.killAuraAttack.getValue());

            if (this.projectileDamage || this.target == null || !mc.player.isSprinting()) {
                this.debugLog("start alink"
                        + (this.projectileDamage ? ": projectile" : this.target == null ? ": no fov target" : ": not sprinting"));
                this.alinkTicks = this.alinkDelay.getValue().intValue();
                event.setCancelled();
                this.packets.add(packet);
            } else {
                this.attackQueue = count;
                this.velocityApplied = false;
                this.hitSelectSkipAttacks = this.killAuraAttack.getValue() ? 0 : count;
                event.setCancelled();
            }
        }
    }

    @Subscribe
    public void onPreTick(final PreGameTickEvent event) {
        if (mc.player == null) {
            return;
        }

        this.attacking = false;

        if (this.releaseReason != null) {
            if (this.releaseReason.isEmpty() && !mc.player.isSprinting()) {
                this.releaseReason = null;
                return;
            }

            this.handle();
            this.alinkTicks = -1;

            if (this.releaseReason.isEmpty()) {
                final int count = this.getCurrentAttackCount();
                if (count > 0 && this.isValidTarget(this.target)) {
                    this.attackQueue = count;
                    this.hitSelectSkipAttacks = this.killAuraAttack.getValue() ? 0 : count;
                    this.velocityApplied = false;
                    this.debugLog("finish alink: queue attacks=" + count + ", target=" + this.targetName(this.target));
                } else {
                    this.debugLog("finish alink: no valid target");
                    this.clearAttackState();
                }
            } else {
                this.debugLog("finish alink: " + this.releaseReason);
            }
            this.releaseReason = null;
        }

        if (this.attackQueue > 0) {
            this.applyQueuedVelocity();

            if (!this.isValidTarget(this.target)) {
                this.debugLog("cancel queue: invalid/fov target");
                this.clearAttackState();
                return;
            }

            if (!this.isHoldingAttackReduceItem()) {
                this.debugLog("cancel queue: invalid held item");
                this.clearAttackState();
                return;
            }

            if (this.killAuraAttack.getValue()) {
                final int requested = this.attackQueue;
                if (this.requestKillAuraAttacks(requested)) {
                    this.debugLog("request KillAura attacks=" + requested + ", target=" + this.targetName(this.target));
                    this.pendingKillAuraReductions = requested;
                    this.pendingKillAuraWindowTicks = Math.max(4, requested * 3);
                    this.attackQueue = 0;
                    this.hitSelectSkipAttacks = 0;
                } else {
                    this.debugLog("cancel queue: KillAura request failed, target=" + this.targetName(this.target));
                    this.clearAttackState();
                }
                return;
            }

            this.attacking = true;
            if (mc.interactionManager == null || mc.player.isUsingItem() || mc.player.distanceTo(this.target) > mc.player.getEntityInteractionRange()) {
                this.debugLog("cancel direct attack: interaction unavailable");
                this.clearAttackState();
                return;
            }

            if (!this.rotateToTarget(this.target)) {
                this.debugLog("cancel direct attack: rotate failed");
                this.clearAttackState();
                return;
            }

            mc.player.setSprinting(true);
            mc.interactionManager.attackEntity(mc.player, this.target);
            mc.player.swingHand(Hand.MAIN_HAND);
            this.reduceHorizontalVelocityAfterAttack("direct attack");
            this.debugLog("direct attack: target=" + this.targetName(this.target) + ", remaining=" + (this.attackQueue - 1));
            this.attackQueue--;

            if (this.attackQueue <= 0) {
                this.clearAttackState();
            }
        }

        if (this.pendingKillAuraReductions > 0 && this.pendingKillAuraWindowTicks > 0 && --this.pendingKillAuraWindowTicks == 0) {
            this.debugLog("cancel KillAura reductions: attack window expired, remaining=" + this.pendingKillAuraReductions);
            this.clearAttackState();
        }
    }

    @Subscribe
    public void onMoveInput(final MoveInputEvent event) {
        if (mc.player == null) {
            return;
        }

        if (this.alinkTicks >= 0 && this.releaseReason == null) {
            if (this.alinkTicks > 0) {
                this.alinkTicks--;
            }
            this.findTarget();

            if (this.alinkTicks == 0) {
                this.releaseReason = "max delay";
            } else if (mc.player.getAbilities().flying) {
                this.releaseReason = "flying";
            } else if (this.target != null && mc.player.distanceTo(this.target) > DEFAULT_ALINK_TARGET_RANGE) {
                this.releaseReason = "out of range";
            } else if (this.target != null) {
                event.setForward(1.0F);
                event.setSideways(0.0F);
                this.releaseReason = "";
                this.debugLog("release alink: target=" + this.targetName(this.target));
            }
        }
    }

    private void debugLog(final String message) {
        if (!this.debug.getValue()) {
            return;
        }
        final String text = "AntiKB AttackReduce | " + message;
        if (mc.isOnThread()) {
            ChatUtility.print(text);
        } else {
            mc.execute(() -> ChatUtility.print(text));
        }
    }

    @Override
    public void onEnable() {
        super.onEnable();
        this.reset();
    }

    @Override
    public void onDisable() {
        super.onDisable();
        this.handle();
        this.reset();
    }

    private void reset() {
        this.target = null;
        this.attackQueue = 0;
        this.receiveDamage = false;
        this.projectileDamage = false;
        this.explosionOrFireDamage = false;
        this.alinkTicks = -1;
        this.releaseReason = null;
        this.velocity = null;
        this.attacking = false;
        this.velocityApplied = false;
        this.packets.clear();
        this.hitSelectSkipAttacks = 0;
        this.pendingKillAuraReductions = 0;
        this.pendingKillAuraWindowTicks = 0;
    }

    @Override
    public boolean isAttacking() {
        return this.attacking;
    }

    @Override
    public int getHitSelectSkips() {
        return this.hitSelectSkipAttacks;
    }

    @Override
    public boolean consumeHitSelectSkip() {
        if (this.hitSelectSkipAttacks > 0) {
            this.hitSelectSkipAttacks--;
            return true;
        }
        return false;
    }

    @Override
    public void onVelocityResetAttackPerformed() {
        if (this.pendingKillAuraReductions <= 0) {
            return;
        }

        this.reduceHorizontalVelocityAfterAttack("KillAura attack");
        this.pendingKillAuraReductions--;
        this.pendingKillAuraWindowTicks = Math.max(this.pendingKillAuraWindowTicks, 1);
        if (this.pendingKillAuraReductions <= 0) {
            this.clearAttackState();
        }
    }

    @Override
    public boolean isDelaying() {
        return this.alinkTicks >= 0;
    }

    @Override
    public boolean hasQueuedPackets() {
        return !this.packets.isEmpty();
    }

    @Override
    public boolean shouldStopBacktrack() {
        return this.isDelaying() || this.hasQueuedPackets() || this.attackQueue > 0 || this.attacking || this.pendingKillAuraReductions > 0;
    }

    @Override
    public Enum<?> getEnumValue() {
        return VelocityModule.Mode.ATTACK_REDUCE;
    }

    @Override
    public String getSuffix() {
        return "AttackReduce" + (this.alinkTicks >= 0 ? " " + (this.alinkDelay.getValue().intValue() - this.alinkTicks) + "Ticks" : "");
    }
}

package wtf.opal.client.feature.module.impl.combat.killaura.target;

import net.minecraft.entity.LivingEntity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import wtf.opal.client.feature.helper.impl.LocalDataWatch;
import wtf.opal.client.feature.helper.impl.target.TargetFlags;
import wtf.opal.client.feature.helper.impl.target.TargetList;
import wtf.opal.client.feature.helper.impl.target.TargetProperty;
import wtf.opal.client.feature.helper.impl.target.impl.TargetLivingEntity;
import wtf.opal.client.feature.module.impl.combat.TeamsModule;
import wtf.opal.client.feature.module.impl.combat.killaura.KillAuraSettings;
import wtf.opal.client.feature.module.impl.utility.AntiBotsModule;
import wtf.opal.utility.player.PlayerUtility;
import wtf.opal.utility.player.RaytracedRotation;
import wtf.opal.utility.player.RotationUtility;

import java.util.*;

import static wtf.opal.client.Constants.mc;

public final class KillAuraTargeting {

    private final KillAuraSettings settings;

    public KillAuraTargeting(KillAuraSettings settings) {
        this.settings = settings;
    }

    private @Nullable CurrentTarget target, rotationTarget;
    private double closestDistance = Double.MAX_VALUE;

    public void update() {
        this.closestDistance = Double.MAX_VALUE;
        this.findAttackTarget();
        this.findRotationTarget();
    }

    private void findAttackTarget() {
        final double interactionRange = this.settings.getRange();
        final List<TargetLivingEntity> targets = this.getTargets(interactionRange);
        if (targets == null || targets.isEmpty()) {
            this.target = null;
            return;
        }
        this.target = this.selectTarget(targets, interactionRange, false);
    }

    private void findRotationTarget() {
        if (this.target != null) {
            this.rotationTarget = this.target;
            return;
        }
        final double interactionRange = this.settings.getRotationRange();
        final List<TargetLivingEntity> targets = this.getTargets(interactionRange);
        if (targets == null || targets.isEmpty()) {
            this.rotationTarget = null;
            return;
        }
        this.rotationTarget = this.selectTarget(targets, interactionRange, true);
    }

    private List<TargetLivingEntity> getTargets(final double interactionRange) {
        final TargetList targetList = LocalDataWatch.getTargetList();
        if (targetList == null || mc.player == null) {
            return null;
        }

        final TargetProperty targetProperty = settings.getTargetProperty();

        final List<TargetLivingEntity> targets = targetList.collectTargets(targetProperty.getTargetFlags(), TargetLivingEntity.class);
        final Map<Integer, KillAuraTarget> updatedTargetMap = new HashMap<>();

        if (this.targetMap == null) {
            this.targetMap = new HashMap<>();
        }

        for (final Iterator<TargetLivingEntity> iterator = targets.iterator(); iterator.hasNext(); ) {
            final TargetLivingEntity target = iterator.next();
            if (target.isLocal()) {
                iterator.remove();
                continue;
            }

            final LivingEntity entity = target.getEntity();
            if (entity.isDead() || !entity.isAttackable() || !RotationUtility.isEntityInFOV(entity, this.settings.getFov())) {
                iterator.remove();
                continue;
            }

            if (AntiBotsModule.isBot(entity) || AntiBotsModule.isBedWarsBot(entity) || TeamsModule.isTeammate(entity)) {
                iterator.remove();
                continue;
            }

            if (LocalDataWatch.getFriendList().contains(entity.getName().getString().toUpperCase())) {
                iterator.remove();
                continue;
            }

            final double distance = PlayerUtility.getDistanceToEntity(entity);

            if (distance < this.closestDistance) {
                this.closestDistance = distance;
            }

            if (distance > interactionRange) {
                iterator.remove();
                continue;
            }

            updatedTargetMap.put(target.getEntityId(), this.getKillAuraTarget(target));
        }

        this.targetMap = updatedTargetMap;

        return targets;
    }

    private @Nullable Map<Integer, KillAuraTarget> targetMap;

    private CurrentTarget selectTarget(final List<TargetLivingEntity> targets, final double entityInteractionRange, final boolean distanceSorting) {
        targets.sort(Comparator.comparingDouble(t -> {
            final double score = distanceSorting ? t.getEntity().squaredDistanceTo(mc.player) : t.getFullHealth();
            if (!t.isMatchingFlags(TargetFlags.PLAYERS)) { // players are prioritized
                return score * 2.0D;
            }
            return score;
        })); // TODO compare effective damage against that player on top of health

        final List<CurrentTarget> targetList = this.convertTargets(targets, entityInteractionRange);

        if (!distanceSorting && this.settings.getMode() == KillAuraSettings.Mode.SWITCH && targetList.size() > 1) {
            targetList.sort(Comparator.comparingDouble(t -> -((t.getKillAuraTarget().getTarget().getFullHealth() * 25.0D) + (t.getKillAuraTarget().getLastAttackData() == null ? 0 : t.getKillAuraTarget().getLastAttackData().getTime()))));
        }

        if (targetList.isEmpty()) {
            return null;
        }

        return targetList.getFirst();
    }

    private @NotNull List<CurrentTarget> convertTargets(final List<TargetLivingEntity> targets, final double entityInteractionRange) {
        final Vec3d eyePos = mc.player.getEyePos();
        final List<CurrentTarget> targetList = new ArrayList<>();

        for (TargetLivingEntity target : targets) {
            final LivingEntity entity = target.getEntity();
            final Vec3d closestVector = PlayerUtility.getClosestVectorToBoundingBox(eyePos, entity);
            final RaytracedRotation tickedRotation = RotationUtility.getRotationFromRaycastedEntity(entity, closestVector, entityInteractionRange);
            if (tickedRotation != null && (this.settings.isThroughWalls() || this.hasLineOfSight(tickedRotation))) {
                targetList.add(new CurrentTarget(this.getKillAuraTarget(target), tickedRotation));
            }
        }

        return targetList;
    }

    private boolean hasLineOfSight(final RaytracedRotation targetRotation) {
        final HitResult blockHit = mc.world.raycast(new RaycastContext(
                mc.player.getEyePos(),
                targetRotation.hitResult().getPos(),
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                mc.player
        ));
        return blockHit.getType() == HitResult.Type.MISS;
    }

    private @NotNull KillAuraTarget getKillAuraTarget(final TargetLivingEntity target) {
        if (this.targetMap.containsKey(target.getEntityId())) {
            return this.targetMap.get(target.getEntityId());
        }
        final KillAuraTarget killAuraTarget = new KillAuraTarget(target);
        this.targetMap.put(target.getEntityId(), killAuraTarget);
        return killAuraTarget;
    }

    public void reset() {
        this.closestDistance = Double.MAX_VALUE;
        this.targetMap = null;
        this.target = null;
    }

    public @Nullable CurrentTarget getTarget() {
        return target;
    }

    public @Nullable CurrentTarget getRotationTarget() {
        return rotationTarget;
    }

    public double getClosestDistance() {
        return closestDistance;
    }

    public boolean isTargetSelected() {
        return getTarget() != null;
    }
}

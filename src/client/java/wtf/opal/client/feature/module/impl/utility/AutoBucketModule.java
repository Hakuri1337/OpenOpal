package wtf.opal.client.feature.module.impl.utility;

import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import wtf.opal.client.feature.helper.impl.player.rotation.RotationHelper;
import wtf.opal.client.feature.helper.impl.player.rotation.model.impl.InstantRotationModel;
import wtf.opal.client.feature.helper.impl.player.slot.SlotHelper;
import wtf.opal.client.feature.module.Module;
import wtf.opal.client.feature.module.ModuleCategory;
import wtf.opal.client.feature.module.property.impl.bool.BooleanProperty;
import wtf.opal.client.feature.module.property.impl.number.NumberProperty;
import wtf.opal.event.impl.game.PreGameTickEvent;
import wtf.opal.event.impl.game.input.MoveInputEvent;
import wtf.opal.event.subscriber.Subscribe;
import wtf.opal.utility.player.RotationUtility;

import static wtf.opal.client.Constants.mc;

public final class AutoBucketModule extends Module {

    private static final float BUCKET_ROTATION_READY_DIFFERENCE = 3.5F;
    private static final float MAX_BUCKET_PITCH = 88.0F;

    private final BooleanProperty mlg = new BooleanProperty("MLG", true);
    private final NumberProperty fallDistance = new NumberProperty("Fall Distance", 3.0D, 1.0D, 10.0D, 0.1D)
            .hideIf(() -> !this.mlg.getValue());
    private final NumberProperty predictTicks = new NumberProperty("Predict Ticks", "ticks", 2.0D, 1.0D, 5.0D, 1.0D)
            .hideIf(() -> !this.mlg.getValue());
    private final BooleanProperty solidCheck = new BooleanProperty("Solid Check", true)
            .hideIf(() -> !this.mlg.getValue());
    private final BooleanProperty recovery = new BooleanProperty("Recovery", true)
            .hideIf(() -> !this.mlg.getValue());
    private final BooleanProperty extinguish = new BooleanProperty("Extinguish", true);

    private int restoreSlot = -1;

    private float accumulatedFall;
    private double lastY;
    private boolean waterPlaced;
    private boolean readyToPlace;
    private boolean mlgRecoveryActive;
    private int mlgRecoveryDelay;
    private int mlgRecoveryTriesLeft;
    private int mlgRecoverySlot = -1;
    private BlockPos mlgPlacedWaterPos;
    private BlockHitResult pendingMlgHit;
    private Vec2f pendingMlgRotation;
    private float lastRequestedYaw = Float.NaN;
    private float lastRequestedPitch = Float.NaN;
    private int postPlaceCooldown;
    private int postActionCooldown;
    private int retryCooldown;

    private boolean helperRetrievePending;
    private int helperRetrieveTriesLeft;
    private int helperRetrieveSlot = -1;
    private BlockPos helperPlacedWaterPos;
    private int helperCooldownTicks;
    private int helperRetrieveDelay;

    public AutoBucketModule() {
        super("AutoBucket", "Handles water bucket MLG and helper recovery.", ModuleCategory.UTILITY);
        this.addProperties(this.mlg, this.fallDistance, this.predictTicks, this.solidCheck, this.recovery, this.extinguish);
    }

    @Subscribe
    public void onPreGameTick(final PreGameTickEvent event) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) {
            this.resetState();
            return;
        }

        if (mc.player.isSpectator() || mc.player.getAbilities().allowFlying || mc.player.getAbilities().flying) {
            this.resetState();
            return;
        }

        this.tickCooldowns();
        this.restoreSlotIfNeeded();

        final boolean mlgConsumedTick = this.handleMlgTick();
        if (mlgConsumedTick) {
            return;
        }

        this.handleHelperTick();
    }

    @Subscribe
    public void onMoveInput(final MoveInputEvent event) {
        if (this.postActionCooldown > 0 || this.mlgRecoveryActive) {
            event.setSneak(false);
        }
    }

    private boolean handleMlgTick() {
        if (!this.mlg.getValue()) {
            this.resetMlgState();
            return false;
        }

        this.updateFallState();

        if (mc.player.isOnGround() || this.accumulatedFall <= 0.0F) {
            this.waterPlaced = false;
            this.readyToPlace = false;
            this.clearPendingMlgPlacement();
        }

        if (this.mlgRecoveryActive) {
            this.handleMlgRecovery();
            return true;
        }

        if (this.tryFillWaterBucket()) {
            return true;
        }

        final double remainingDistance = this.distanceToGround(8.0D);

        if (this.waterPlaced) {
            if (this.mlgPlacedWaterPos == null && this.retryCooldown == 0) {
                if (remainingDistance > 0.0D && remainingDistance <= 1.35D) {
                    final int retrySlot = this.findWaterBucketSlot();
                    if (retrySlot != -1) {
                        this.placeMlgWaterBucket(retrySlot, false);
                    }
                    this.retryCooldown = 2;
                }
            }
            return true;
        }

        if (this.postPlaceCooldown > 0 || this.postActionCooldown > 0) {
            return true;
        }

        final double effectiveFallDistance = Math.max(this.accumulatedFall, mc.player.fallDistance);
        if (effectiveFallDistance < this.fallDistance.getValue().floatValue() || mc.player.getVelocity().y >= -0.08D) {
            return false;
        }

        final int waterSlot = this.findWaterBucketSlot();
        if (waterSlot == -1) {
            return true;
        }

        if (this.solidCheck.getValue() && remainingDistance == Double.POSITIVE_INFINITY) {
            return true;
        }

        if (!(remainingDistance > 0.0D && remainingDistance <= this.getMlgPlaceDistance())) {
            this.clearPendingMlgPlacement();
            return true;
        }

        this.placeMlgWaterBucket(waterSlot, true);
        return true;
    }

    private void handleHelperTick() {
        if (!this.extinguish.getValue()) {
            this.clearHelperRetrieve();
            return;
        }

        if (this.helperRetrievePending) {
            this.handleHelperRetrieve();
            return;
        }

        if (!this.shouldExtinguish() || this.helperCooldownTicks > 0) {
            return;
        }

        final int waterSlot = this.findWaterBucketSlot();
        if (waterSlot == -1) {
            return;
        }

        final Vec2f rotation = new Vec2f(mc.player.getYaw(), MAX_BUCKET_PITCH);
        this.requestRotation(rotation);
        final BlockPos placedWaterPos = this.computePlacedWaterPos(rotation);
        if (placedWaterPos == null) {
            return;
        }

        this.saveAndSwitch(waterSlot);
        this.useItem();

        this.helperRetrievePending = true;
        this.helperRetrieveTriesLeft = 8;
        this.helperRetrieveSlot = -1;
        this.helperPlacedWaterPos = placedWaterPos;
        this.helperRetrieveDelay = 3;
        this.helperCooldownTicks = 8;
    }

    private void updateFallState() {
        if (mc.player.isOnGround()
                || mc.player.isTouchingWater()
                || mc.player.isInLava()
                || mc.player.isClimbing()) {
            this.accumulatedFall = 0.0F;
        } else {
            final double deltaY = mc.player.getY() - this.lastY;
            if (deltaY < 0.0D) {
                this.accumulatedFall -= (float) deltaY;
            }
        }
        this.lastY = mc.player.getY();
    }

    private void tickCooldowns() {
        if (this.postPlaceCooldown > 0) {
            this.postPlaceCooldown--;
        }
        if (this.postActionCooldown > 0) {
            this.postActionCooldown--;
        }
        if (this.retryCooldown > 0) {
            this.retryCooldown--;
        }
        if (this.helperCooldownTicks > 0) {
            this.helperCooldownTicks--;
        }
    }

    private void restoreSlotIfNeeded() {
        if (this.restoreSlot == -1) {
            return;
        }
        SlotHelper.setCurrentItem(this.restoreSlot);
        this.restoreSlot = -1;
    }

    private boolean tryFillWaterBucket() {
        if (this.waterPlaced
                || this.mlgRecoveryActive
                || this.mlgPlacedWaterPos != null
                || this.postPlaceCooldown > 0
                || this.postActionCooldown > 0
                || this.accumulatedFall > 0.5F
                || this.findWaterBucketSlot() != -1) {
            return false;
        }

        final int emptySlot = this.findEmptyBucketSlot();
        if (emptySlot == -1) {
            return false;
        }

        final BlockPos waterPos = this.findNearestWaterSource();
        if (waterPos == null) {
            return false;
        }

        final Vec2f rotation = this.getSafeRotationTo(Vec3d.ofCenter(waterPos), this.getCurrentRotation().x, MAX_BUCKET_PITCH);
        this.requestRotation(rotation);
        if (!this.isRotationReady(rotation)) {
            return true;
        }

        final BlockHitResult hit = this.raycastFluid(this.getCurrentRotation(), 4.5D);
        if (hit.getType() == HitResult.Type.MISS || !hit.getBlockPos().equals(waterPos)) {
            return false;
        }

        this.saveAndSwitch(emptySlot);
        this.useItem();
        this.postActionCooldown = 8;
        this.postPlaceCooldown = Math.max(this.postPlaceCooldown, 1);
        return true;
    }

    private void handleMlgRecovery() {
        if (this.mlgRecoveryDelay > 0) {
            this.mlgRecoveryDelay--;
            return;
        }

        if (this.mlgRecoverySlot == -1) {
            this.mlgRecoverySlot = this.findEmptyBucketSlot();
            if (this.mlgRecoverySlot == -1) {
                this.retryMlgRecovery();
                return;
            }
        }

        final ItemStack stack = mc.player.getInventory().getStack(this.mlgRecoverySlot);
        if (stack.isOf(Items.WATER_BUCKET)) {
            this.clearMlgRecovery();
            this.postPlaceCooldown = Math.max(this.postPlaceCooldown, 1);
            return;
        }

        final BlockPos waterSource = this.findRecoverableWaterSource(this.mlgPlacedWaterPos, 1);
        if (waterSource == null) {
            this.retryMlgRecovery();
            return;
        }

        this.mlgPlacedWaterPos = waterSource;
        final Vec2f rotation = this.getSafeRotationTo(Vec3d.ofCenter(waterSource), this.getCurrentRotation().x, MAX_BUCKET_PITCH);
        this.requestRotation(rotation);
        if (!this.isRotationReady(rotation)) {
            this.retryMlgRecovery();
            return;
        }

        final BlockHitResult hit = this.raycastFluid(this.getCurrentRotation(), mc.player.getBlockInteractionRange());
        if (hit.getType() == HitResult.Type.MISS || !this.isWaterSource(hit.getBlockPos())
                || hit.getBlockPos().getSquaredDistance(this.mlgPlacedWaterPos) > 4.0D) {
            this.retryMlgRecovery();
            return;
        }

        this.mlgPlacedWaterPos = hit.getBlockPos().toImmutable();
        this.saveAndSwitch(this.mlgRecoverySlot);
        this.useItem();
        this.mlgRecoveryTriesLeft--;
        this.mlgRecoveryDelay = 2;
    }

    private void placeMlgWaterBucket(final int slot, final boolean markPlaced) {
        final Vec2f rotation = new Vec2f(mc.player.getYaw(), MAX_BUCKET_PITCH);
        this.requestRotation(rotation);

        final BlockPos placedPos = this.computePlacedWaterPos(rotation);
        if (placedPos == null) {
            return;
        }

        this.mlgPlacedWaterPos = placedPos;
        this.saveAndSwitch(slot);
        this.useItem();

        if (markPlaced) {
            this.waterPlaced = true;
        }

        this.mlgRecoveryActive = this.recovery.getValue() && this.mlgPlacedWaterPos != null;
        this.mlgRecoveryDelay = 3;
        this.mlgRecoveryTriesLeft = this.mlgRecoveryActive ? 16 : 0;
        this.mlgRecoverySlot = -1;
        this.retryCooldown = 2;
        this.clearPendingMlgPlacement();
    }

    private void handleHelperRetrieve() {
        if (mc.player == null || mc.world == null) {
            this.clearHelperRetrieve();
            return;
        }
        if (this.helperRetrieveDelay > 0) {
            this.helperRetrieveDelay--;
            return;
        }

        if (this.helperRetrieveSlot == -1) {
            this.helperRetrieveSlot = this.findEmptyBucketSlot();
            if (this.helperRetrieveSlot == -1) {
                this.retryHelperRetrieve();
                return;
            }
        }

        if (mc.player.getInventory().getStack(this.helperRetrieveSlot).isOf(Items.WATER_BUCKET)) {
            this.clearHelperRetrieve();
            return;
        }

        final BlockPos waterSource = this.findRecoverableWaterSource(this.helperPlacedWaterPos, 1);
        if (waterSource == null) {
            this.retryHelperRetrieve();
            return;
        }

        this.helperPlacedWaterPos = waterSource;
        final Vec2f rotation = this.getSafeRotationTo(Vec3d.ofCenter(waterSource), this.getCurrentRotation().x, MAX_BUCKET_PITCH);
        this.requestRotation(rotation);
        if (!this.isRotationReady(rotation)) {
            this.retryHelperRetrieve();
            return;
        }

        final BlockHitResult hit = this.raycastFluid(this.getCurrentRotation(), mc.player.getBlockInteractionRange());
        if (hit.getType() == HitResult.Type.MISS || !this.isWaterSource(hit.getBlockPos())
                || hit.getBlockPos().getSquaredDistance(this.helperPlacedWaterPos) > 4.0D) {
            this.retryHelperRetrieve();
            return;
        }

        this.helperPlacedWaterPos = hit.getBlockPos().toImmutable();
        this.saveAndSwitch(this.helperRetrieveSlot);
        this.useItem();
        this.helperRetrieveTriesLeft--;
        this.helperRetrieveDelay = 2;
    }

    private boolean shouldExtinguish() {
        if (mc.player == null) {
            return false;
        }
        if (this.mlg.getValue() && this.accumulatedFall > 0.5F) {
            return false;
        }
        if (mc.player.isTouchingWater() || mc.player.isSwimming()) {
            return false;
        }
        return mc.player.isOnFire();
    }

    private int ticksUntilGround() {
        if (mc.player.getVelocity().y >= 0.0D) {
            return 999;
        }

        final double distance = this.distanceToGround(30.0D);
        if (distance == Double.POSITIVE_INFINITY) {
            return 999;
        }

        double simulatedDrop = 0.0D;
        double simulatedVelocity = mc.player.getVelocity().y;
        for (int i = 1; i <= 20; i++) {
            simulatedDrop += simulatedVelocity;
            simulatedVelocity = (simulatedVelocity - 0.08D) * 0.98D;
            if (Math.abs(simulatedDrop) >= distance) {
                return i;
            }
        }
        return 999;
    }

    private double distanceToGround(final double maxDistance) {
        final Vec3d start = new Vec3d(mc.player.getX(), mc.player.getBoundingBox().minY, mc.player.getZ());
        final Vec3d end = start.add(0.0D, -maxDistance, 0.0D);
        final BlockHitResult hit = mc.world.raycast(new RaycastContext(
                start,
                end,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                mc.player
        ));

        if (hit.getType() == HitResult.Type.MISS) {
            return Double.POSITIVE_INFINITY;
        }
        return start.y - hit.getPos().y;
    }

    private BlockPos findNearestWaterSource() {
        final BlockPos playerPos = mc.player.getBlockPos();
        BlockPos closest = null;
        double closestDistance = Double.POSITIVE_INFINITY;

        for (int y = -1; y <= 1; y++) {
            for (int x = -4; x <= 4; x++) {
                for (int z = -4; z <= 4; z++) {
                    final BlockPos candidate = playerPos.add(x, y, z);
                    if (!this.isWaterSource(candidate)) {
                        continue;
                    }

                    final double distance = mc.player.squaredDistanceTo(candidate.getX() + 0.5D, candidate.getY() + 0.5D, candidate.getZ() + 0.5D);
                    if (distance >= closestDistance) {
                        continue;
                    }

                    final Vec2f rotation = this.getSafeRotationTo(Vec3d.ofCenter(candidate), this.getCurrentRotation().x, MAX_BUCKET_PITCH);
                    final BlockHitResult hit = this.raycastFluid(rotation, 4.5D);
                    if (hit.getType() == HitResult.Type.MISS || !hit.getBlockPos().equals(candidate)) {
                        continue;
                    }

                    closest = candidate.toImmutable();
                    closestDistance = distance;
                }
            }
        }

        return closest;
    }

    private boolean updatePendingMlgPlacement() {
        if (this.pendingMlgHit != null && this.isUsableMlgHit(this.pendingMlgHit)) {
            this.pendingMlgRotation = this.getSafeRotationTo(this.pendingMlgHit.getPos(), this.getCurrentRotation().x, MAX_BUCKET_PITCH);
            return true;
        }

        final BlockHitResult hit = this.findMlgPlacementHit();
        if (hit == null) {
            this.clearPendingMlgPlacement();
            return false;
        }

        this.pendingMlgHit = hit;
        this.pendingMlgRotation = this.getSafeRotationTo(hit.getPos(), this.getCurrentRotation().x, MAX_BUCKET_PITCH);
        return true;
    }

    private BlockHitResult findMlgPlacementHit() {
        final Vec3d eyePos = mc.player.getEyePos();
        final Vec3d playerCenter = mc.player.getEntityPos();
        final double maxDistance = Math.min(5.0D, Math.max(2.0D, this.distanceToGround(6.0D) + 1.0D));
        BlockHitResult bestHit = null;
        double bestScore = Double.POSITIVE_INFINITY;

        for (double x = -0.35D; x <= 0.35D; x += 0.35D) {
            for (double z = -0.35D; z <= 0.35D; z += 0.35D) {
                final Vec3d start = eyePos.add(x, 0.0D, z);
                final Vec3d end = start.add(0.0D, -maxDistance, 0.0D);
                final BlockHitResult hit = mc.world.raycast(new RaycastContext(
                        start,
                        end,
                        RaycastContext.ShapeType.COLLIDER,
                        RaycastContext.FluidHandling.NONE,
                        mc.player
                ));

                if (hit.getType() == HitResult.Type.MISS || !this.isUsableMlgHit(hit)) {
                    continue;
                }

                final double horizontal = hit.getPos().squaredDistanceTo(playerCenter.x, hit.getPos().y, playerCenter.z);
                final double vertical = Math.abs(mc.player.getBoundingBox().minY - hit.getPos().y);
                final double score = horizontal + vertical * 0.02D;
                if (score < bestScore) {
                    bestScore = score;
                    bestHit = hit;
                }
            }
        }

        return bestHit;
    }

    private BlockHitResult findSelfBucketPlacementHit() {
        final Vec3d eyePos = mc.player.getEyePos();
        final Vec3d playerCenter = mc.player.getEntityPos();
        BlockHitResult bestHit = null;
        double bestScore = Double.POSITIVE_INFINITY;

        for (double x = -0.35D; x <= 0.35D; x += 0.35D) {
            for (double z = -0.35D; z <= 0.35D; z += 0.35D) {
                final Vec3d start = eyePos.add(x, 0.0D, z);
                final Vec3d end = start.add(0.0D, -4.5D, 0.0D);
                final BlockHitResult hit = mc.world.raycast(new RaycastContext(
                        start,
                        end,
                        RaycastContext.ShapeType.COLLIDER,
                        RaycastContext.FluidHandling.NONE,
                        mc.player
                ));

                if (hit.getType() == HitResult.Type.MISS || !this.isSolidNonInteractive(hit.getBlockPos())) {
                    continue;
                }

                final double score = hit.getPos().squaredDistanceTo(playerCenter.x, hit.getPos().y, playerCenter.z);
                if (score < bestScore) {
                    bestScore = score;
                    bestHit = hit;
                }
            }
        }

        return bestHit;
    }

    private boolean isUsableMlgHit(final BlockHitResult hit) {
        if (hit == null || hit.getType() == HitResult.Type.MISS) {
            return false;
        }
        return this.isSolidNonInteractive(hit.getBlockPos())
                && mc.world.getFluidState(hit.getBlockPos().offset(hit.getSide())).isEmpty();
    }

    private boolean hasSolidBelow(final BlockPos pos) {
        return this.isSolidNonInteractive(pos.down()) || this.isSolidNonInteractive(pos.down(2));
    }

    private boolean isSolidNonInteractive(final BlockPos pos) {
        final BlockState state = mc.world.getBlockState(pos);
        return !state.getCollisionShape(mc.world, pos).isEmpty()
                && state.createScreenHandlerFactory(mc.world, pos) == null;
    }

    private BlockHitResult raycastSolid(final Vec2f rotation, final double range) {
        final Vec3d start = mc.player.getEyePos();
        final Vec3d direction = RotationUtility.getRotationVector(rotation.y, rotation.x);
        final Vec3d end = start.add(direction.multiply(range));
        return mc.world.raycast(new RaycastContext(
                start,
                end,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                mc.player
        ));
    }

    private BlockHitResult raycastFluid(final Vec2f rotation, final double range) {
        final Vec3d start = mc.player.getEyePos();
        final Vec3d direction = RotationUtility.getRotationVector(rotation.y, rotation.x);
        final Vec3d end = start.add(direction.multiply(range));
        return mc.world.raycast(new RaycastContext(
                start,
                end,
                RaycastContext.ShapeType.OUTLINE,
                RaycastContext.FluidHandling.ANY,
                mc.player
        ));
    }

    private boolean isWaterSource(final BlockPos pos) {
        return !mc.world.getFluidState(pos).isEmpty() && mc.world.getFluidState(pos).isStill();
    }

    private BlockPos findRecoverableWaterSource(final BlockPos expected, final int radius) {
        if (expected == null) return null;
        BlockPos best = null;
        double bestScore = Double.POSITIVE_INFINITY;
        for (int y = -1; y <= 1; y++) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    final BlockPos candidate = expected.add(x, y, z);
                    if (!this.isWaterSource(candidate)) continue;
                    final double reach = mc.player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(candidate));
                    if (reach > mc.player.getBlockInteractionRange() * mc.player.getBlockInteractionRange()) continue;
                    final double score = candidate.getSquaredDistance(expected) + reach * 0.01D;
                    if (score < bestScore) {
                        bestScore = score;
                        best = candidate.toImmutable();
                    }
                }
            }
        }
        return best;
    }

    private void retryMlgRecovery() {
        if (--this.mlgRecoveryTriesLeft <= 0) {
            this.clearMlgRecovery();
        } else {
            this.mlgRecoveryDelay = 1;
            this.mlgRecoverySlot = -1;
        }
    }

    private void retryHelperRetrieve() {
        if (--this.helperRetrieveTriesLeft <= 0) {
            this.clearHelperRetrieve();
        } else {
            this.helperRetrieveSlot = -1;
            this.helperRetrieveDelay = 1;
        }
    }

    private double getMlgPlaceDistance() {
        return MathHelper.clamp(0.75D + this.predictTicks.getValue() * 0.75D, 1.35D, 3.0D);
    }

    private BlockPos computePlacedWaterPos(final Vec2f rotation) {
        final BlockHitResult hit = this.raycastSolid(rotation, 4.5D);
        if (hit.getType() == HitResult.Type.MISS) {
            return null;
        }
        return hit.getBlockPos().offset(hit.getSide());
    }

    private Vec2f getLookDownRotationTo(final BlockPos pos) {
        final Vec2f raw = RotationUtility.getRotationFromPosition(Vec3d.ofCenter(pos));
        return new Vec2f(RotationUtility.getDuplicateWrapped(raw.x, this.getCurrentRotation().x), MAX_BUCKET_PITCH);
    }

    private Vec2f getSafeRotationTo(final Vec3d target, final float yawReference, final float maxPitch) {
        final Vec2f raw = RotationUtility.getRotationFromPosition(target);
        final float yaw = RotationUtility.getDuplicateWrapped(raw.x, yawReference);
        final float pitch = MathHelper.clamp(raw.y, -maxPitch, maxPitch);
        return new Vec2f(yaw, pitch);
    }

    private void requestRotation(final Vec2f rotation) {
        this.lastRequestedYaw = rotation.x;
        this.lastRequestedPitch = rotation.y;
        RotationHelper.getHandler().rotate(rotation, InstantRotationModel.INSTANCE);
    }

    private Vec2f getCurrentRotation() {
        return new Vec2f(
                RotationHelper.getClientHandler().getYawOr(mc.player.getYaw()),
                RotationHelper.getClientHandler().getPitchOr(mc.player.getPitch())
        );
    }

    private boolean isRotationReady(final Vec2f rotation) {
        final Vec2f current = this.getCurrentRotation();
        return RotationUtility.getRotationDifference(current, rotation) <= BUCKET_ROTATION_READY_DIFFERENCE
                && Math.abs(MathHelper.wrapDegrees(current.x - this.lastRequestedYaw)) <= BUCKET_ROTATION_READY_DIFFERENCE
                && Math.abs(current.y - this.lastRequestedPitch) <= BUCKET_ROTATION_READY_DIFFERENCE;
    }

    private BlockHitResult getVerifiedSolidHit(final Vec2f rotation, final BlockHitResult expectedHit) {
        if (!this.isRotationReady(rotation)) {
            return null;
        }

        final BlockHitResult hit = this.raycastSolid(this.getCurrentRotation(), mc.player.getBlockInteractionRange());
        if (hit.getType() == HitResult.Type.MISS || !this.isUsableMlgHit(hit)) {
            return null;
        }

        if (!hit.getBlockPos().equals(expectedHit.getBlockPos()) || hit.getSide() != expectedHit.getSide()) {
            return null;
        }

        return hit;
    }

    private void saveAndSwitch(final int targetSlot) {
        if (this.restoreSlot == -1) {
            this.restoreSlot = mc.player.getInventory().getSelectedSlot();
        }
        SlotHelper.setCurrentItem(targetSlot);
    }

    private void useItem() {
        if (mc.player == null || mc.interactionManager == null) {
            return;
        }
        mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
        mc.player.swingHand(Hand.MAIN_HAND);
    }

    private int findWaterBucketSlot() {
        if (mc.player == null) {
            return -1;
        }
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).isOf(Items.WATER_BUCKET)) {
                return i;
            }
        }
        return -1;
    }

    private int findEmptyBucketSlot() {
        if (mc.player == null) {
            return -1;
        }
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).isOf(Items.BUCKET)) {
                return i;
            }
        }
        return -1;
    }

    private void clearMlgRecovery() {
        this.mlgRecoveryActive = false;
        this.mlgRecoveryDelay = 0;
        this.mlgRecoveryTriesLeft = 0;
        this.mlgRecoverySlot = -1;
        this.mlgPlacedWaterPos = null;
        this.clearPendingMlgPlacement();
    }

    private void clearHelperRetrieve() {
        this.helperRetrievePending = false;
        this.helperRetrieveTriesLeft = 0;
        this.helperRetrieveSlot = -1;
        this.helperPlacedWaterPos = null;
        this.helperRetrieveDelay = 0;
    }

    private void clearPendingMlgPlacement() {
        this.pendingMlgHit = null;
        this.pendingMlgRotation = null;
        this.lastRequestedYaw = Float.NaN;
        this.lastRequestedPitch = Float.NaN;
    }

    private void resetMlgState() {
        this.accumulatedFall = 0.0F;
        this.lastY = mc.player == null ? 0.0D : mc.player.getY();
        this.waterPlaced = false;
        this.readyToPlace = false;
        this.clearMlgRecovery();
        this.postPlaceCooldown = 0;
        this.postActionCooldown = 0;
        this.retryCooldown = 0;
    }

    private void resetState() {
        this.restoreSlot = -1;
        this.resetMlgState();
        this.clearHelperRetrieve();
        this.helperCooldownTicks = 0;
    }

    @Override
    protected void onEnable() {
        this.resetState();
        super.onEnable();
    }

    @Override
    protected void onDisable() {
        this.resetState();
        super.onDisable();
    }
}

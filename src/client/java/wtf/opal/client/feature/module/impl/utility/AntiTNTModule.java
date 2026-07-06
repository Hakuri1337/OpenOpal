package wtf.opal.client.feature.module.impl.utility;

import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.TntEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import wtf.opal.client.feature.helper.impl.player.rotation.RotationHelper;
import wtf.opal.client.feature.helper.impl.player.rotation.model.impl.HeypixelRotationModel;
import wtf.opal.client.feature.helper.impl.player.slot.SlotHelper;
import wtf.opal.client.feature.module.Module;
import wtf.opal.client.feature.module.ModuleCategory;
import wtf.opal.event.impl.game.PreGameTickEvent;
import wtf.opal.event.subscriber.Subscribe;
import wtf.opal.utility.player.InventoryUtility;
import wtf.opal.utility.player.MoveUtility;
import wtf.opal.utility.player.RotationUtility;

import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Queue;

import static wtf.opal.client.Constants.mc;

public final class AntiTNTModule extends Module {

    private static final float ROTATION_SPEED = 45.0F;
    private static final float ROTATION_READY_DIFFERENCE = 5.0F;

    private final Queue<BlockPos> blockPositionQueue = new ArrayDeque<>();

    private TntEntity targetTnt;
    private int savedHotbarSlot = -1;
    private BlockPos lastPlacedPos;
    private int placementCooldown;
    private Vec2f targetRotation;

    public AntiTNTModule() {
        super("AntiTNT", "Places blocks around you when nearby TNT is dangerous.", ModuleCategory.UTILITY);
    }

    @Subscribe
    public void onPreGameTick(final PreGameTickEvent event) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) {
            this.resetState();
            return;
        }

        if (this.isMoving()) {
            this.blockPositionQueue.clear();
            this.restoreSlot();
            this.targetRotation = null;
            this.targetTnt = null;
            return;
        }

        if (this.placementCooldown > 0) {
            this.placementCooldown--;
        }

        if (this.blockPositionQueue.isEmpty()) {
            this.targetTnt = this.findNearestTnt();
        }

        if (!this.blockPositionQueue.isEmpty()) {
            this.placeNextBlock();
            return;
        }

        if (this.targetTnt != null) {
            this.collectBlockPositions();
        } else {
            this.targetRotation = null;
        }
    }

    private boolean isMoving() {
        return mc.options != null
                && (MoveUtility.isMoving()
                || mc.options.forwardKey.isPressed()
                || mc.options.backKey.isPressed()
                || mc.options.leftKey.isPressed()
                || mc.options.rightKey.isPressed()
                || mc.player.isSprinting());
    }

    private TntEntity findNearestTnt() {
        final Box searchBox = mc.player.getBoundingBox().expand(20.0D);
        return mc.world.getEntitiesByClass(TntEntity.class, searchBox, entity -> entity.isAlive() && entity.getFuse() > 0)
                .stream()
                .filter(entity -> this.isMovingTowardsPlayer(entity) || this.hasLineOfSight(entity))
                .min(Comparator.comparingDouble(entity -> entity.squaredDistanceTo(mc.player)))
                .orElse(null);
    }

    private boolean hasLineOfSight(final TntEntity tnt) {
        if (tnt.squaredDistanceTo(mc.player) > 64.0D) {
            return false;
        }

        final BlockHitResult hit = mc.world.raycast(new RaycastContext(
                this.getEntityPos(tnt),
                mc.player.getEyePos(),
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                mc.player
        ));
        return hit.getType() == HitResult.Type.MISS;
    }

    private boolean isMovingTowardsPlayer(final Entity entity) {
        final Vec3d toPlayer = this.getEntityPos(mc.player).subtract(this.getEntityPos(entity)).normalize();
        return entity.getVelocity().dotProduct(toPlayer) > 0.05D;
    }

    private void collectBlockPositions() {
        if (!this.blockPositionQueue.isEmpty()) {
            return;
        }

        if (mc.currentScreen != null) {
            mc.currentScreen.close();
            mc.setScreen(null);
        }

        final BlockPos playerPos = mc.player.getBlockPos();
        for (Direction direction : Direction.Type.HORIZONTAL) {
            final BlockPos sidePos = playerPos.offset(direction);
            if (this.canPlaceAt(sidePos)) {
                this.blockPositionQueue.add(sidePos.toImmutable());
            }
        }

        for (Direction direction : Direction.Type.HORIZONTAL) {
            final BlockPos sidePos = playerPos.up().offset(direction);
            if (this.canPlaceAt(sidePos)) {
                this.blockPositionQueue.add(sidePos.toImmutable());
            }
        }

        final BlockPos abovePos = playerPos.up(2);
        if (this.canPlaceAt(abovePos)) {
            this.blockPositionQueue.add(abovePos.toImmutable());
        }
    }

    private void placeNextBlock() {
        if (this.placementCooldown > 0 || this.blockPositionQueue.isEmpty()) {
            return;
        }

        final BlockPos placePos = this.blockPositionQueue.peek();
        final BlockHitResult hit = this.getPlacementHitResult(placePos);
        if (hit == null) {
            this.blockPositionQueue.poll();
            return;
        }

        final int blockSlot = this.findBlockSlot();
        if (blockSlot == -1) {
            this.blockPositionQueue.clear();
            this.restoreSlot();
            return;
        }

        if (this.savedHotbarSlot == -1) {
            this.savedHotbarSlot = mc.player.getInventory().getSelectedSlot();
        }

        final Vec2f rotation = RotationUtility.getVanillaRotation(RotationUtility.getRotationFromPosition(hit.getPos()));
        this.targetRotation = rotation;
        RotationHelper.getHandler().rotate(rotation, new HeypixelRotationModel(ROTATION_SPEED));
        if (RotationUtility.getRotationDifference(this.getCurrentRotation(), rotation) > ROTATION_READY_DIFFERENCE) {
            return;
        }

        SlotHelper.setCurrentItem(blockSlot).silence(SlotHelper.Silence.NONE);
        final ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
        if (result.isAccepted()) {
            mc.player.swingHand(Hand.MAIN_HAND);
        }

        this.lastPlacedPos = placePos;
        this.blockPositionQueue.poll();
        this.placementCooldown = 1;

        if (this.blockPositionQueue.isEmpty()) {
            this.restoreSlot();
            this.targetRotation = null;
        }
    }

    private Vec2f getCurrentRotation() {
        return new Vec2f(
                RotationHelper.getClientHandler().getYawOr(mc.player.getYaw()),
                RotationHelper.getClientHandler().getPitchOr(mc.player.getPitch())
        );
    }

    private void restoreSlot() {
        if (this.savedHotbarSlot != -1 && mc.player != null) {
            SlotHelper.setCurrentItem(this.savedHotbarSlot).silence(SlotHelper.Silence.NONE);
            this.savedHotbarSlot = -1;
        }
    }

    private boolean canPlaceAt(final BlockPos pos) {
        if (mc.world.isOutOfHeightLimit(pos.getY())) {
            return false;
        }
        return mc.world.getBlockState(pos).isReplaceable()
                && !mc.player.getBoundingBox().intersects(new Box(pos));
    }

    private int findBlockSlot() {
        for (int slot = 0; slot < 9; slot++) {
            final ItemStack stack = mc.player.getInventory().getStack(slot);
            if (stack.getItem() instanceof BlockItem blockItem && InventoryUtility.isGoodBlock(blockItem.getBlock())) {
                return slot;
            }
        }
        return -1;
    }

    private boolean isSolidBlock(final BlockPos pos) {
        final BlockState state = mc.world.getBlockState(pos);
        return !state.isAir() && !state.isReplaceable() && !state.getCollisionShape(mc.world, pos).isEmpty();
    }

    private BlockHitResult getPlacementHitResult(final BlockPos placePos) {
        final BlockPos belowPos = placePos.down();
        if (this.isSolidBlock(belowPos)) {
            return new BlockHitResult(this.getHitVec(belowPos, Direction.UP), Direction.UP, belowPos, false);
        }

        for (Direction direction : Direction.Type.HORIZONTAL) {
            final BlockPos sidePos = placePos.offset(direction);
            if (!this.isSolidBlock(sidePos)) {
                continue;
            }

            final Direction hitFace = direction.getOpposite();
            return new BlockHitResult(this.getHitVec(sidePos, hitFace), hitFace, sidePos, false);
        }

        return null;
    }

    private Vec3d getHitVec(final BlockPos pos, final Direction direction) {
        double hitX = pos.getX() + 0.5D;
        double hitY = pos.getY() + 0.5D;
        double hitZ = pos.getZ() + 0.5D;

        if (direction == Direction.UP || direction == Direction.DOWN) {
            hitX += (Math.random() - 0.5D) * 0.6D;
            hitZ += (Math.random() - 0.5D) * 0.6D;
        } else {
            hitY += (Math.random() - 0.5D) * 0.5D;
        }

        if (direction == Direction.WEST || direction == Direction.EAST) {
            hitZ += (Math.random() - 0.5D) * 0.6D;
        }
        if (direction == Direction.SOUTH || direction == Direction.NORTH) {
            hitX += (Math.random() - 0.5D) * 0.6D;
        }

        return new Vec3d(
                Math.max(pos.getX(), Math.min(pos.getX() + 1.0D, hitX)),
                Math.max(pos.getY(), Math.min(pos.getY() + 1.0D, hitY)),
                Math.max(pos.getZ(), Math.min(pos.getZ() + 1.0D, hitZ))
        );
    }

    private Vec3d getEntityPos(final Entity entity) {
        return new Vec3d(entity.getX(), entity.getY(), entity.getZ());
    }

    private void resetState() {
        this.blockPositionQueue.clear();
        this.targetTnt = null;
        this.lastPlacedPos = null;
        this.targetRotation = null;
        this.placementCooldown = 0;
        this.restoreSlot();
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

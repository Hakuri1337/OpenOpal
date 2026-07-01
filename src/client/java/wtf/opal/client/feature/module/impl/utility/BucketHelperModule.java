package wtf.opal.client.feature.module.impl.utility;

import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import wtf.opal.client.feature.helper.impl.player.rotation.RotationHelper;
import wtf.opal.client.feature.helper.impl.player.rotation.model.impl.InstantRotationModel;
import wtf.opal.client.feature.helper.impl.player.slot.SlotHelper;
import wtf.opal.client.feature.module.Module;
import wtf.opal.client.feature.module.ModuleCategory;
import wtf.opal.event.impl.game.PreGameTickEvent;
import wtf.opal.event.subscriber.Subscribe;
import wtf.opal.utility.player.RaycastUtility;
import wtf.opal.utility.player.RotationUtility;

import static wtf.opal.client.Constants.mc;

public final class BucketHelperModule extends Module {

    private int restoreSlot = -1;
    private boolean retrievePending;
    private int retrieveTriesLeft;
    private int retrieveSlot = -1;
    private BlockPos placedWaterPos;
    private int cooldownTicks;

    public BucketHelperModule() {
        super("Bucket Helper", "Water bucket helper based on WaterBucket flow.", ModuleCategory.UTILITY);
    }

    @Subscribe
    public void onPreTick(final PreGameTickEvent event) {
        if (mc.player == null || mc.world == null) {
            resetState();
            return;
        }

        if (mc.player.isSpectator() || mc.player.getAbilities().allowFlying) {
            resetState();
            return;
        }

        if (cooldownTicks > 0) {
            cooldownTicks--;
        }

        if (restoreSlot != -1) {
            SlotHelper.setCurrentItem(restoreSlot);
            restoreSlot = -1;
        }

        if (retrievePending) {
            handleRetrieve();
            return;
        }

        if (!shouldExtinguish()) {
            return;
        }

        if (cooldownTicks > 0) {
            return;
        }

        final int waterSlot = findWaterBucketSlot();
        if (waterSlot == -1) {
            return;
        }

        final Vec2f rot = new Vec2f(mc.player.getYaw(), 90.0F);
        requestRotation(rot);
        saveAndSwitch(waterSlot);
        useItem();

        retrievePending = true;
        retrieveTriesLeft = 2;
        retrieveSlot = -1;
        placedWaterPos = computePlacedWaterPos(rot);
        cooldownTicks = 8;
    }

    private void handleRetrieve() {
        if (mc.player == null || mc.world == null) {
            retrievePending = false;
            return;
        }

        if (retrieveTriesLeft-- <= 0) {
            retrievePending = false;
            retrieveSlot = -1;
            placedWaterPos = null;
            return;
        }

        if (retrieveSlot == -1) {
            retrieveSlot = findEmptyBucketSlot();
            if (retrieveSlot == -1) {
                retrievePending = false;
                return;
            }
        }

        if (mc.player.getInventory().getStack(retrieveSlot).isOf(Items.WATER_BUCKET)) {
            retrievePending = false;
            retrieveSlot = -1;
            placedWaterPos = null;
            return;
        }

        if (placedWaterPos == null || !isWaterSource(placedWaterPos)) {
            retrievePending = false;
            retrieveSlot = -1;
            placedWaterPos = null;
            return;
        }

        final Vec2f rot = getLookDownRotationTo(placedWaterPos);
        final HitResult hit = RaycastUtility.raycastBlock(4.5, 1.0F, true, rot.x, rot.y);
        if (!(hit instanceof BlockHitResult bhr) || !bhr.getBlockPos().equals(placedWaterPos)) {
            retrievePending = false;
            retrieveSlot = -1;
            placedWaterPos = null;
            return;
        }

        requestRotation(rot);
        saveAndSwitch(retrieveSlot);
        useItem();
    }

    private boolean shouldExtinguish() {
        if (mc.player == null) return false;
        if (mc.player.isTouchingWater() || mc.player.isSwimming()) return false;
        return mc.player.isOnFire();
    }

    private void requestRotation(final Vec2f rotation) {
        RotationHelper.getHandler().rotate(rotation, InstantRotationModel.INSTANCE);
    }

    private void saveAndSwitch(final int slot) {
        if (restoreSlot == -1 && mc.player != null) {
            restoreSlot = mc.player.getInventory().getSelectedSlot();
        }
        SlotHelper.setCurrentItem(slot);
    }

    private void useItem() {
        if (mc.player == null || mc.interactionManager == null) {
            return;
        }
        mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
        mc.player.swingHand(Hand.MAIN_HAND);
    }

    private BlockPos computePlacedWaterPos(final Vec2f rot) {
        final HitResult hit = RaycastUtility.raycastBlock(4.5, 1.0F, false, rot.x, rot.y);
        if (!(hit instanceof BlockHitResult bhr)) {
            return null;
        }
        return bhr.getBlockPos().offset(bhr.getSide());
    }

    private Vec2f getLookDownRotationTo(final BlockPos pos) {
        final Vec3d target = new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        final Vec2f raw = RotationUtility.getRotationFromPosition(target);
        return new Vec2f(raw.x, 90.0F);
    }

    private boolean isWaterSource(final BlockPos pos) {
        if (mc.world == null) return false;
        return mc.world.getFluidState(pos).isStill() && !mc.world.getFluidState(pos).isEmpty();
    }

    private int findWaterBucketSlot() {
        if (mc.player == null) return -1;
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).isOf(Items.WATER_BUCKET)) {
                return i;
            }
        }
        return -1;
    }

    private int findEmptyBucketSlot() {
        if (mc.player == null) return -1;
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).isOf(Items.BUCKET)) {
                return i;
            }
        }
        return -1;
    }

    private void resetState() {
        restoreSlot = -1;
        retrievePending = false;
        retrieveTriesLeft = 0;
        retrieveSlot = -1;
        placedWaterPos = null;
        cooldownTicks = 0;
    }
}

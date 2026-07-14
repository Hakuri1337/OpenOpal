package wtf.opal.client.feature.module.impl.world.scaffold.mode.silencetelly;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import wtf.opal.client.feature.helper.impl.player.rotation.RotationHelper;

import static wtf.opal.client.Constants.mc;

final class SilenceTellyFallingPlayer {
    private double x;
    private double y;
    private double z;
    private Vec3d motion;
    private Vec3d eyePos;
    private final float yaw;
    private final float strafe;
    private final float forward;
    private final float jumpMovementFactor;
    private boolean onGround;

    SilenceTellyFallingPlayer(final ClientPlayerEntity player, final float fallbackForward, final float fallbackSideways) {
        this.x = player.getX();
        this.y = player.getY();
        this.z = player.getZ();
        this.motion = player.getVelocity();
        final Vec2f clientRotation = RotationHelper.getClientHandler().getRotation();
        this.yaw = clientRotation == null ? player.getYaw() : clientRotation.x;
        final Vec2f movement = player.input.getMovementInput();
        this.strafe = Math.abs(movement.x) > 1.0E-4F || Math.abs(fallbackSideways) <= 1.0E-4F ? movement.x : fallbackSideways;
        this.forward = Math.abs(movement.y) > 1.0E-4F || Math.abs(fallbackForward) <= 1.0E-4F ? movement.y : fallbackForward;
        this.onGround = player.isOnGround();
        this.jumpMovementFactor = player.isSprinting() ? 0.026F : 0.02F;
        this.eyePos = player.getEyePos();
    }

    void calculate(final int ticks) {
        for (int i = 0; i < ticks; i++) {
            this.calculateForTick();
        }
    }

    BlockPos findCollision(final int ticks) {
        final float width = mc.player != null ? mc.player.getWidth() / 2.0F : 0.3F;
        for (int i = 0; i < ticks; i++) {
            final Vec3d start = new Vec3d(this.x, this.y, this.z);
            this.calculateForTick();
            final Vec3d end = new Vec3d(this.x, this.y, this.z);

            BlockPos raytracedBlock;
            if ((raytracedBlock = this.rayTrace(start, end)) != null) return raytracedBlock;
            if ((raytracedBlock = this.rayTrace(start.add(width, 0.0D, width), end.add(width, 0.0D, width))) != null) return raytracedBlock;
            if ((raytracedBlock = this.rayTrace(start.add(-width, 0.0D, width), end.add(-width, 0.0D, width))) != null) return raytracedBlock;
            if ((raytracedBlock = this.rayTrace(start.add(width, 0.0D, -width), end.add(width, 0.0D, -width))) != null) return raytracedBlock;
            if ((raytracedBlock = this.rayTrace(start.add(-width, 0.0D, -width), end.add(-width, 0.0D, -width))) != null) return raytracedBlock;
            if ((raytracedBlock = this.rayTrace(start.add(width, 0.0D, 0.0D), end.add(width, 0.0D, 0.0D))) != null) return raytracedBlock;
            if ((raytracedBlock = this.rayTrace(start.add(-width, 0.0D, 0.0D), end.add(-width, 0.0D, 0.0D))) != null) return raytracedBlock;
            if ((raytracedBlock = this.rayTrace(start.add(0.0D, 0.0D, width), end.add(0.0D, 0.0D, width))) != null) return raytracedBlock;
            if ((raytracedBlock = this.rayTrace(start.add(0.0D, 0.0D, -width), end.add(0.0D, 0.0D, -width))) != null) return raytracedBlock;
        }
        return null;
    }

    Vec3d getEyePos() {
        return this.eyePos;
    }

    Vec3d getPos() {
        return new Vec3d(this.x, this.y, this.z);
    }

    double getY() {
        return this.y;
    }

    private void calculateForTick() {
        this.updateVelocity(this.jumpMovementFactor, new Vec3d(this.strafe, 0.0D, this.forward));
        this.x += this.motion.x;
        this.y += this.motion.y;
        this.z += this.motion.z;
        this.updateGroundState();
        this.motion = this.motion.add(0.0D, -0.08D, 0.0D);
        this.eyePos = new Vec3d(this.x, this.y + (mc.player == null ? 1.62D : mc.player.getStandingEyeHeight()), this.z);
        this.motion = new Vec3d(this.motion.x * 0.91D, this.motion.y * 0.98D, this.motion.z * 0.91D);
    }

    private void updateVelocity(final float speed, final Vec3d input) {
        final double lengthSquared = input.lengthSquared();
        if (lengthSquared < 1.0E-7D) {
            return;
        }

        final Vec3d normalizedInput = (lengthSquared > 1.0D ? input.normalize() : input).multiply(speed);
        final float sin = MathHelper.sin(this.yaw * ((float) Math.PI / 180.0F));
        final float cos = MathHelper.cos(this.yaw * ((float) Math.PI / 180.0F));
        final double inputX = normalizedInput.x * cos - normalizedInput.z * sin;
        final double inputZ = normalizedInput.z * cos + normalizedInput.x * sin;
        this.motion = this.motion.add(inputX, 0.0D, inputZ);
    }

    private void updateGroundState() {
        final BlockHitResult result = this.rayTraceHit(new Vec3d(this.x, this.y, this.z), new Vec3d(this.x, this.y - 0.2D, this.z));
        this.onGround = result != null && result.getType() == HitResult.Type.BLOCK && result.getSide() == Direction.UP;
    }

    private BlockHitResult rayTraceHit(final Vec3d start, final Vec3d end) {
        if (mc.world == null || start.distanceTo(end) < 1.0E-7D) {
            return null;
        }
        final Vec3d diff = end.subtract(start);
        final float yaw = MathHelper.wrapDegrees((float) Math.toDegrees(Math.atan2(diff.z, diff.x)) - 90.0F);
        final float pitch = MathHelper.wrapDegrees((float) -Math.toDegrees(Math.atan2(diff.y, Math.sqrt(diff.x * diff.x + diff.z * diff.z))));
        return SilenceTellyRaycastUtility.getFacedBlock(start, new Vec2f(yaw, pitch), SilenceTellyRaycastUtility::isIgnoredBlock);
    }

    private BlockPos rayTrace(final Vec3d start, final Vec3d end) {
        final BlockHitResult result = this.rayTraceHit(start, end);
        if (result != null && result.getType() == HitResult.Type.BLOCK && result.getSide() == Direction.UP) {
            return result.getBlockPos();
        }
        return null;
    }
}

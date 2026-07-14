package wtf.opal.client.feature.module.impl.world.scaffold.mode.silencetelly;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import wtf.opal.utility.player.RotationUtility;

import static wtf.opal.client.Constants.mc;

final class SilenceTellyRotationUtility {
    private static final double[] FACE_OFFSETS = {0.0D, 0.24D, -0.24D, 0.36D, -0.36D, 0.45D, -0.45D};

    private SilenceTellyRotationUtility() {
    }

    static Vec2f getClosestToBlockFace(final BlockPos pos, final Direction face, final Vec2f reference) {
        return getClosestToBlockFace(pos, face, reference, mc.player == null ? null : mc.player.getEyePos());
    }

    static Vec2f getClosestToBlockFace(final BlockPos pos, final Direction face, final Vec2f reference, final Vec3d eyePos) {
        return getClosestToBlockFace(pos, face, reference, eyePos, false);
    }

    static Vec2f getClosestToBlockFace(final BlockPos pos, final Direction face, final Vec2f reference,
                                       final Vec3d eyePos, final boolean fixSensitivity) {
        if (mc.player == null || pos == null || face == null || eyePos == null) {
            return null;
        }

        Vec2f bestRotation = null;
        double bestDifference = Double.MAX_VALUE;
        for (final double firstOffset : FACE_OFFSETS) {
            for (final double secondOffset : FACE_OFFSETS) {
                if (Math.abs(firstOffset) == 0.45D && Math.abs(secondOffset) == 0.45D) {
                    continue;
                }
                final Vec3d hitVec = getFaceHitVec(pos, face, firstOffset, secondOffset);
                final Vec2f rawRotation = getRotationTo(eyePos, hitVec);
                final Vec2f rotation = fixSensitivity ? patchSensitivity(rawRotation, reference) : rawRotation;
                if (!SilenceTellyRaycastUtility.didHitBlockFace(eyePos, rotation, pos, face, true)) {
                    continue;
                }

                final double difference = getRotationDifference(reference, rotation);
                if (difference < bestDifference) {
                    bestDifference = difference;
                    bestRotation = rotation;
                }
            }
        }

        if (bestRotation != null) {
            return bestRotation;
        }

        final Vec2f center = getRotationTo(eyePos, getFaceHitVec(pos, face, 0.0D, 0.0D));
        final Vec2f fallback = fixSensitivity ? patchSensitivity(center, reference) : center;
        return SilenceTellyRaycastUtility.didHitBlockFace(eyePos, fallback, pos, face, true) ? fallback : null;
    }

    static Vec2f getRotationTo(final Vec3d from, final Vec3d to) {
        final double diffX = to.x - from.x;
        final double diffY = to.y - from.y;
        final double diffZ = to.z - from.z;
        final float yaw = MathHelper.wrapDegrees((float) Math.toDegrees(Math.atan2(diffZ, diffX)) - 90.0F);
        final float pitch = MathHelper.clamp(MathHelper.wrapDegrees((float) -Math.toDegrees(Math.atan2(diffY, Math.sqrt(diffX * diffX + diffZ * diffZ)))), -90.0F, 90.0F);
        return new Vec2f(yaw, pitch);
    }

    static Vec2f patchSensitivity(final Vec2f rotation, final Vec2f reference) {
        if (rotation == null || reference == null) {
            return rotation;
        }
        return RotationUtility.patchConstantRotation(rotation, reference);
    }

    static float yawDiffDirectly(final float target, final float current) {
        return MathHelper.wrapDegrees(target - current);
    }

    static float smooth(final float diff, final float max) {
        return MathHelper.clamp(diff, -Math.abs(max), Math.abs(max));
    }

    static double getRotationDifference(final Vec2f from, final Vec2f to) {
        if (from == null || to == null) {
            return 0.0D;
        }
        final float yaw = MathHelper.wrapDegrees(to.x - from.x);
        final float pitch = to.y - from.y;
        return Math.sqrt(yaw * yaw + pitch * pitch);
    }

    private static Vec3d getFaceHitVec(final BlockPos pos, final Direction face, final double firstOffset, final double secondOffset) {
        double x = pos.getX() + 0.5D + face.getOffsetX() * 0.5D;
        double y = pos.getY() + 0.5D + face.getOffsetY() * 0.5D;
        double z = pos.getZ() + 0.5D + face.getOffsetZ() * 0.5D;

        switch (face.getAxis()) {
            case X -> {
                y += firstOffset;
                z += secondOffset;
            }
            case Y -> {
                x += firstOffset;
                z += secondOffset;
            }
            case Z -> {
                x += firstOffset;
                y += secondOffset;
            }
        }

        return new Vec3d(x, y, z);
    }
}

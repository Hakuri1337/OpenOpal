package wtf.opal.utility.player;

import net.minecraft.client.util.math.Vector2f;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.util.math.MathHelper;
import org.joml.Vector2d;
import wtf.opal.client.feature.helper.impl.player.rotation.RotationHelper;
import wtf.opal.client.feature.helper.impl.player.timer.TimerHelper;

import static wtf.opal.client.Constants.mc;

public final class MoveUtility {

    private MoveUtility() {
    }

    public static double getBlocksPerSecond() {
        final double bps = (Math.hypot(mc.player.getX() - mc.player.lastX, mc.player.getZ() - mc.player.lastZ)) * 20 * TimerHelper.getInstance().timer;
        return Math.round(bps * 100.0) / 100.0;
    }

    public static double[] yawPos(float yaw, double value) {
        return new double[]{-MathHelper.sin(yaw) * value, MathHelper.cos(yaw) * value};
    }

    public static void setSpeed(final Entity entity, final double speed, final double yaw) {
        if (speed == 0.0D) {
            entity.setVelocity(0.0D, entity.getVelocity().getY(), 0.0D);
            return;
        }
        entity.setVelocity(
                -MathHelper.sin((float) yaw) * speed,
                entity.getVelocity().getY(),
                MathHelper.cos((float) yaw) * speed
        );
    }

    public static void setSpeed(final double speed) {
        final double yaw = getDirectionRadians(RotationHelper.getClientHandler().getYawOr(mc.player.getYaw()));
        setSpeed(mc.player, speed, yaw);
    }

    public static void setSpeed(final double speed, double strafePercentage) {
        strafePercentage /= 100;
        strafePercentage = Math.min(1, Math.max(0, strafePercentage));

        final double motionX = mc.player.getVelocity().getX();
        final double motionZ = mc.player.getVelocity().getZ();

        setSpeed(speed);
        mc.player.setVelocity(motionX + (mc.player.getVelocity().getX() - motionX) * strafePercentage, mc.player.getVelocity().getY(), motionZ + (mc.player.getVelocity().getZ() - motionZ) * strafePercentage);
    }

    public static void setSpeed(final double speed, final float yaw) {
        mc.player.setVelocity(
                -MathHelper.sin(yaw) * speed,
                mc.player.getVelocity().getY(),
                MathHelper.cos(yaw) * speed
        );
    }

    public static double getSwiftnessSpeed(final double speed, final double swiftnessMultiplier) {
        if (!mc.player.hasStatusEffect(StatusEffects.SPEED))
            return speed;

        return speed * (1 + swiftnessMultiplier * (mc.player.getStatusEffect(StatusEffects.SPEED).getAmplifier() + 1));
    }

    public static double getSwiftnessSpeed(final double speed) {
        return getSwiftnessSpeed(speed, 0.2D);
    }

    public static double getSpeed() {
        return Math.hypot(mc.player.getVelocity().getX(), mc.player.getVelocity().getZ());
    }

    public static float getMoveYaw(final Vector2d from, final Vector2d to) {
        final Vector2d diff = new Vector2d(to.x - from.x, to.y - from.y);
        return (float) Math.toDegrees(Math.atan2(-diff.x, diff.y));
    }

    public static float getMoveYaw() {
        final Vector2f from = new Vector2f((float) mc.player.lastX, (float) mc.player.lastZ),
                to = new Vector2f((float) mc.player.getX(), (float) mc.player.getZ()),
                diff = new Vector2f(to.getX() - from.getX(), to.getY() - from.getY());
        return (float) Math.toDegrees((Math.atan2(-diff.getX(), diff.getY()) + (MathHelper.PI * 2)) % (MathHelper.PI * 2));
    }

    public static float getDirectionDegrees() {
        return getDirectionDegrees(RotationHelper.getClientHandler().getYawOr(mc.player.getYaw()));
    }

    public static double getDirectionRadians() {
        return getDirectionRadians(RotationHelper.getClientHandler().getYawOr(mc.player.getYaw()));
    }

    public static float getDirectionDegrees(float yaw) {
        if (mc.player == null || mc.player.input == null) {
            return yaw;
        }

        final float forward = mc.player.input.getMovementInput().y;
        final float sideways = mc.player.input.getMovementInput().x;
        return (float) Math.toDegrees(getDirection(yaw, forward, sideways));
    }

    public static double getDirectionRadians(float yaw) {
        return Math.toRadians(getDirectionDegrees(yaw));
    }

    public static double getDirection(float rotationYaw, final double moveForward, final double moveStrafing) {
        if (moveForward < 0F) rotationYaw += 180F;

        float forward = 1F;

        if (moveForward < 0F) forward = -0.5F;
        else if (moveForward > 0F) forward = 0.5F;

        if (moveStrafing > 0F) rotationYaw -= 90F * forward;
        if (moveStrafing < 0F) rotationYaw += 90F * forward;

        return Math.toRadians(rotationYaw);
    }

    public static boolean isMoving() {
        if (mc.player == null) {
            return false;
        }
        return (mc.player.forwardSpeed != 0F || mc.player.sidewaysSpeed != 0F);
    }

}

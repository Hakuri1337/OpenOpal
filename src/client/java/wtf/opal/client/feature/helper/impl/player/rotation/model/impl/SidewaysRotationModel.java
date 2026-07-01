package wtf.opal.client.feature.helper.impl.player.rotation.model.impl;

import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec2f;
import wtf.opal.client.feature.helper.impl.player.rotation.model.EnumRotationModel;
import wtf.opal.client.feature.helper.impl.player.rotation.model.IRotationModel;
import wtf.opal.utility.player.RotationUtility;

public final class SidewaysRotationModel implements IRotationModel {

    private final float speed;

    public SidewaysRotationModel(final float speed) {
        this.speed = speed;
    }

    @Override
    public Vec2f tick(final Vec2f from, final Vec2f to, final float timeDelta) {
        final float targetYaw = to.x;
        final float targetPitch = to.y;
        final float lastYaw = from.x;
        final float lastPitch = from.y;

        final float deltaYaw = MathHelper.wrapDegrees(targetYaw - lastYaw);
        final float deltaPitch = targetPitch - lastPitch;

        final double distance = Math.sqrt(deltaYaw * deltaYaw + deltaPitch * deltaPitch);
        if (distance <= 1.0E-6D) {
            return from;
        }

        final double distributionYaw = Math.abs(deltaYaw / distance);
        final double distributionPitch = Math.abs(deltaPitch / distance);

        final double maxYaw = speed * distributionYaw;
        final double maxPitch = speed * distributionPitch;

        final float moveYaw = (float) Math.max(Math.min(deltaYaw, maxYaw), -maxYaw);
        final float movePitch = (float) Math.max(Math.min(deltaPitch, maxPitch), -maxPitch);

        final Vec2f rotation = new Vec2f(lastYaw + moveYaw, MathHelper.clamp(lastPitch + movePitch, -90.0F, 90.0F));
        return RotationUtility.patchConstantRotation(rotation, from);
    }

    @Override
    public EnumRotationModel getEnum() {
        return EnumRotationModel.SIDEWAYS;
    }
}

package wtf.opal.utility.misc.math;

import net.minecraft.client.render.Camera;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import static wtf.opal.client.Constants.mc;

public final class MathUtility {

    private MathUtility() {
    }

    public static Number roundAndClamp(final Number value, final Number minValue, final Number maxValue, final Number increment) {
        switch (value) {
            case Double casted -> {
                casted = Math.round(casted / increment.doubleValue()) * increment.doubleValue();
                casted = MathHelper.clamp(casted, minValue.doubleValue(), maxValue.doubleValue());
                return casted;
            }
            case Float casted -> {
                casted = Math.round(casted / increment.floatValue()) * increment.floatValue();
                casted = MathHelper.clamp(casted, minValue.floatValue(), maxValue.floatValue());
                return casted;
            }
            case Long casted -> {
                casted = Math.round((float) casted / increment.longValue()) * increment.longValue();
                casted = MathHelper.clamp(casted, minValue.longValue(), maxValue.longValue());
                return casted;
            }
            default -> {
                int casted = value.intValue();
                casted = Math.round((float) casted / increment.intValue()) * increment.intValue();
                casted = MathHelper.clamp(casted, minValue.intValue(), maxValue.intValue());
                return casted;
            }
        }
    }

    public static Vec3d interpolate(final LivingEntity entity, final float tickDelta) {
        final Camera camera = mc.gameRenderer.getCamera();
        return entity.getEntityPos()
                .add(
                        MathHelper.lerp(tickDelta, entity.lastRenderX, entity.getX()) - entity.getX(),
                        MathHelper.lerp(tickDelta, entity.lastRenderY, entity.getY()) - entity.getY(),
                        MathHelper.lerp(tickDelta, entity.lastRenderZ, entity.getZ()) - entity.getZ()
                )
                .subtract(camera.getPos());
    }

    public static double interpolate(final double a, final double b, final double v) {
        return (a + (b - a) * v);
    }

    public static float interpolate(final float a, final float b, final float v) {
        return (a + (b - a) * v);
    }

    public static long interpolate(final long a, final long b, final long v) {
        return (a + (b - a) * v);
    }

    public static int interpolate(final int a, final int b, final int v) {
        return (a + (b - a) * v);
    }
}

package wtf.opal.client.renderer.menu;

import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;

public final class ClientBootTransition {

    private static boolean initialBootActive;

    private ClientBootTransition() {
    }

    public static void beginInitialBoot() {
        initialBootActive = true;
    }

    public static boolean isInitialBootActive() {
        return initialBootActive;
    }

    public static void finishInitialBoot() {
        initialBootActive = false;
    }

    public static float getBrandProgress(final long reloadCompleteTime) {
        if (reloadCompleteTime < 0L) {
            return 0F;
        }
        final float elapsed = Util.getMeasuringTimeMs() - reloadCompleteTime;
        final float linear = MathHelper.clamp((elapsed - 300F) / 1450F, 0F, 1F);
        return 1F - (float) Math.pow(1F - linear, 3D);
    }
}

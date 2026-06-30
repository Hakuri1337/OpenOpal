package wtf.opal.client.renderer.liquidglass;

import wtf.opal.client.renderer.NVGRenderer;
import wtf.opal.utility.render.ColorUtility;

public final class LiquidGlassRenderer {

    private LiquidGlassRenderer() {
    }

    public static void drawPill(final float x, final float y, final float width, final float height, final float radius, final float progress) {
        final float inset = 1.0F;
        final float alpha = Math.max(0.0F, Math.min(1.0F, progress));
        final float innerX = x + inset;
        final float innerY = y + inset;
        final float innerWidth = Math.max(0.0F, width - inset * 2.0F);
        final float innerHeight = Math.max(0.0F, height - inset * 2.0F);
        final float innerRadius = Math.max(0.0F, radius - inset);

        NVGRenderer.roundedRect(innerX, innerY, innerWidth, innerHeight, innerRadius, NVGRenderer.BLUR_PAINT);
        NVGRenderer.roundedRect(innerX, innerY, innerWidth, innerHeight, innerRadius, ColorUtility.applyOpacity(0xff0a0c10, 0.46F * alpha));
        NVGRenderer.roundedRectGradient(innerX, innerY, innerWidth, Math.max(3.0F, innerHeight * 0.55F), innerRadius,
                ColorUtility.applyOpacity(0xffffffff, 0.18F * alpha),
                ColorUtility.applyOpacity(0xffffffff, 0.02F * alpha),
                90.0F);
        NVGRenderer.roundedRectOutline(innerX + 0.35F, innerY + 0.35F, innerWidth - 0.7F, innerHeight - 0.7F, innerRadius, 0.75F,
                ColorUtility.applyOpacity(0xffffffff, 0.28F * alpha));
        NVGRenderer.roundedRectOutline(innerX + 1.25F, innerY + 1.25F, innerWidth - 2.5F, innerHeight - 2.5F, Math.max(0.0F, innerRadius - 1.0F), 0.5F,
                ColorUtility.applyOpacity(0xff000000, 0.24F * alpha));
        NVGRenderer.roundedRectGradient(innerX + 3.0F, innerY + 2.0F, Math.max(0.0F, innerWidth - 6.0F), Math.max(1.5F, innerHeight * 0.18F), Math.max(0.0F, innerRadius - 3.0F),
                ColorUtility.applyOpacity(0xffffffff, 0.16F * alpha),
                ColorUtility.applyOpacity(0xffffffff, 0.0F),
                90.0F);
    }
}

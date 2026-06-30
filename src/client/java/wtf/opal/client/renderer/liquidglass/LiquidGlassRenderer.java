package wtf.opal.client.renderer.liquidglass;

import org.lwjgl.nanovg.NVGPaint;
import wtf.opal.client.renderer.NVGRenderer;
import wtf.opal.utility.render.ColorUtility;

public final class LiquidGlassRenderer {

    private static final NVGPaint OFFSET_BLUR_PAINT = NVGPaint.create();

    private static final LiquidGlassStyle ISLAND_STYLE = new LiquidGlassStyle(
            0xff05070a,
            0.24F,
            0.86F,
            2.0F,
            0.32F,
            0.20F,
            0.38F,
            0.12F
    );

    private LiquidGlassRenderer() {
    }

    public static void drawIsland(final float x, final float y, final float width, final float height, final float radius, final float progress) {
        if (width <= 2.0F || height <= 2.0F) {
            return;
        }

        final float alpha = clamp(progress);
        final float innerX = x + 1.0F;
        final float innerY = y + 1.0F;
        final float innerWidth = Math.max(0.0F, width - 2.0F);
        final float innerHeight = Math.max(0.0F, height - 2.0F);
        final float innerRadius = Math.min(Math.max(0.0F, radius - 1.0F), Math.min(innerWidth, innerHeight) * 0.5F);

        drawShadow(innerX, innerY, innerWidth, innerHeight, innerRadius, alpha);
        drawLensBase(innerX, innerY, innerWidth, innerHeight, innerRadius, alpha);
        drawRefractionBands(innerX, innerY, innerWidth, innerHeight, innerRadius, alpha);
        drawTint(innerX, innerY, innerWidth, innerHeight, innerRadius, alpha);
        drawFresnelAndGlare(innerX, innerY, innerWidth, innerHeight, innerRadius, alpha);
    }

    private static void drawShadow(final float x, final float y, final float width, final float height, final float radius, final float alpha) {
        final float[] expands = {4.0F, 8.0F, 13.0F};
        final float[] opacities = {0.10F, 0.055F, 0.03F};

        for (int i = expands.length - 1; i >= 0; i--) {
            final float expand = expands[i];
            NVGRenderer.roundedRect(
                    x - expand,
                    y - expand * 0.35F + 2.0F,
                    width + expand * 2.0F,
                    height + expand * 1.35F,
                    radius + expand,
                    ColorUtility.applyOpacity(0xff000000, opacities[i] * alpha)
            );
        }
    }

    private static void drawLensBase(final float x, final float y, final float width, final float height, final float radius, final float alpha) {
        drawBlur(x, y, width, height, radius, -0.55F, 0.70F, ISLAND_STYLE.blurAlpha * alpha);
        drawBlur(x + 1.0F, y + 1.0F, width - 2.0F, height - 2.0F, Math.max(0.0F, radius - 1.0F), 0.90F, -0.55F, 0.34F * alpha);
    }

    private static void drawRefractionBands(final float x, final float y, final float width, final float height, final float radius, final float alpha) {
        final float edge = Math.min(7.0F, Math.max(3.0F, height * 0.32F));
        final float side = Math.min(8.0F, Math.max(3.0F, width * 0.08F));

        NVGRenderer.scissor(x, y, width, edge, () ->
                drawBlur(x, y, width, height, radius, 0.0F, -ISLAND_STYLE.refractionThickness, 0.32F * alpha)
        );
        NVGRenderer.scissor(x, y + height - edge, width, edge, () ->
                drawBlur(x, y, width, height, radius, 0.0F, ISLAND_STYLE.refractionThickness * 0.75F, 0.22F * alpha)
        );
        NVGRenderer.scissor(x, y, side, height, () ->
                drawBlur(x, y, width, height, radius, -ISLAND_STYLE.refractionThickness, 0.0F, 0.24F * alpha)
        );
        NVGRenderer.scissor(x + width - side, y, side, height, () ->
                drawBlur(x, y, width, height, radius, ISLAND_STYLE.refractionThickness, 0.0F, 0.24F * alpha)
        );
    }

    private static void drawTint(final float x, final float y, final float width, final float height, final float radius, final float alpha) {
        NVGRenderer.roundedRect(x, y, width, height, radius, ColorUtility.applyOpacity(ISLAND_STYLE.tintColor, ISLAND_STYLE.tintAlpha * alpha));
        NVGRenderer.roundedRectGradient(
                x,
                y,
                width,
                height,
                radius,
                ColorUtility.applyOpacity(0xffffffff, 0.055F * alpha),
                ColorUtility.applyOpacity(0xff000000, 0.12F * alpha),
                90.0F
        );
    }

    private static void drawFresnelAndGlare(final float x, final float y, final float width, final float height, final float radius, final float alpha) {
        final float insetRadius = Math.max(0.0F, radius - 1.2F);

        NVGRenderer.roundedRectOutline(
                x + 0.35F,
                y + 0.35F,
                width - 0.70F,
                height - 0.70F,
                radius,
                0.75F,
                ColorUtility.applyOpacity(0xffffffff, ISLAND_STYLE.fresnelAlpha * alpha)
        );
        NVGRenderer.roundedRectOutline(
                x + 1.35F,
                y + 1.35F,
                width - 2.70F,
                height - 2.70F,
                insetRadius,
                0.60F,
                ColorUtility.applyOpacity(0xff000000, 0.22F * alpha)
        );

        final float side = Math.min(9.0F, Math.max(4.0F, width * 0.09F));
        NVGRenderer.scissor(x, y, side, height, () ->
                NVGRenderer.roundedRectOutline(x + 0.45F, y + 0.45F, width - 0.90F, height - 0.90F, radius, 0.70F,
                        ColorUtility.applyOpacity(0xff78e7ff, ISLAND_STYLE.dispersionAlpha * alpha))
        );
        NVGRenderer.scissor(x + width - side, y, side, height, () ->
                NVGRenderer.roundedRectOutline(x + 0.45F, y + 0.45F, width - 0.90F, height - 0.90F, radius, 0.70F,
                        ColorUtility.applyOpacity(0xffff7f9b, ISLAND_STYLE.dispersionAlpha * 0.85F * alpha))
        );

        final float glareHeight = Math.max(3.0F, height * 0.46F);
        NVGRenderer.roundedRectGradient(
                x + 2.0F,
                y + 1.0F,
                Math.max(0.0F, width - 4.0F),
                glareHeight,
                Math.max(0.0F, radius - 2.0F),
                ColorUtility.applyOpacity(0xffffffff, ISLAND_STYLE.glareAlpha * alpha),
                ColorUtility.applyOpacity(0xffffffff, 0.0F),
                90.0F
        );
        NVGRenderer.roundedRectGradient(
                x + 5.0F,
                y + 2.2F,
                Math.max(0.0F, width * 0.56F),
                Math.max(1.0F, height * 0.16F),
                Math.max(0.0F, radius - 4.0F),
                ColorUtility.applyOpacity(0xffffffff, 0.13F * alpha),
                ColorUtility.applyOpacity(0xffffffff, 0.0F),
                0.0F
        );
    }

    private static void drawBlur(final float x, final float y, final float width, final float height, final float radius, final float offsetX, final float offsetY, final float alpha) {
        if (width <= 0.0F || height <= 0.0F || alpha <= 0.0F) {
            return;
        }

        OFFSET_BLUR_PAINT.set(NVGRenderer.BLUR_PAINT);
        OFFSET_BLUR_PAINT.xform(4, OFFSET_BLUR_PAINT.xform(4) + offsetX);
        OFFSET_BLUR_PAINT.xform(5, OFFSET_BLUR_PAINT.xform(5) + offsetY);

        withAlpha(alpha, () -> NVGRenderer.roundedRect(x, y, width, height, radius, OFFSET_BLUR_PAINT));
    }

    private static void withAlpha(final float alpha, final Runnable content) {
        final float previousAlpha = NVGRenderer.globalAlpha;
        NVGRenderer.globalAlpha(previousAlpha * clamp(alpha));
        content.run();
        NVGRenderer.globalAlpha(previousAlpha);
    }

    private static float clamp(final float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    private record LiquidGlassStyle(
            int tintColor,
            float tintAlpha,
            float blurAlpha,
            float refractionThickness,
            float fresnelAlpha,
            float dispersionAlpha,
            float glareAlpha,
            float shadowAlpha
    ) {
    }
}

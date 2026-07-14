package wtf.opal.client.renderer.image;

import wtf.opal.client.renderer.NVGRenderer;
import wtf.opal.utility.misc.system.IOUtility;

import java.io.InputStream;
import java.nio.ByteBuffer;

import static org.lwjgl.nanovg.NanoVG.*;
import static wtf.opal.client.Constants.VG;

public final class NVGImageRenderer {

    private final ByteBuffer imageData;
    private final int imageHandle;

    public NVGImageRenderer(final InputStream inputStream, final int flags) {
        this.imageData = IOUtility.ioResourceToByteBuffer(inputStream, 512 * 1024);
        this.imageHandle = nvgCreateImageMem(VG, flags, this.imageData);
    }

    public NVGImageRenderer(final InputStream inputStream) {
        this(inputStream, 0);
    }

    public void drawImage(final float x, final float y, final float width, final float height) {
        nvgImagePattern(
                VG,
                x,
                y,
                width,
                height,
                0,
                imageHandle,
                1,
                NVGRenderer.NVG_PAINT
        );

        nvgBeginPath(VG);
        nvgRect(
                VG,
                x,
                y,
                width,
                height
        );
        nvgImagePattern(
                VG,
                x,
                y,
                width,
                height,
                0,
                imageHandle,
                1,
                NVGRenderer.NVG_PAINT
        );
        nvgFillPaint(VG, NVGRenderer.NVG_PAINT);
        nvgFill(VG);
        nvgClosePath(VG);
    }

    public void drawRoundedImage(final float x, final float y, final float width, final float height, final float radius) {
        nvgBeginPath(VG);
        nvgRoundedRect(VG, x, y, width, height, radius);
        nvgImagePattern(VG, x, y, width, height, 0, imageHandle, 1, NVGRenderer.NVG_PAINT);
        nvgFillPaint(VG, NVGRenderer.NVG_PAINT);
        nvgFill(VG);
        nvgClosePath(VG);
    }

    public void drawImage(final float x, final float y, final float width, final float height, final int colorOverlay) {
        nvgImagePattern(
                VG,
                x,
                y,
                width,
                height,
                0,
                imageHandle,
                1,
                NVGRenderer.NVG_PAINT
        );

        nvgBeginPath(VG);
        nvgRect(
                VG,
                x,
                y,
                width,
                height
        );
        nvgImagePattern(
                VG,
                x,
                y,
                width,
                height,
                0,
                imageHandle,
                1,
                NVGRenderer.NVG_PAINT
        );
        NVGRenderer.applyColor(colorOverlay, NVGRenderer.NVG_COLOR_1);
        NVGRenderer.NVG_PAINT.innerColor(NVGRenderer.NVG_COLOR_1);

        nvgFillPaint(VG, NVGRenderer.NVG_PAINT);
        nvgFill(VG);
        nvgClosePath(VG);
    }

}

package wtf.opal.client.renderer.shader;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.PostEffectProcessor;
import net.minecraft.client.gl.WindowFramebuffer;
import net.minecraft.client.gui.screen.SplashOverlay;
import net.minecraft.client.gui.screen.world.LevelLoadingScreen;
import net.minecraft.client.render.DefaultFramebufferSet;
import net.minecraft.client.render.FrameGraphBuilder;
import net.minecraft.util.Identifier;
import wtf.opal.client.OpalClient;
import wtf.opal.client.feature.module.impl.visual.PostProcessingModule;
import wtf.opal.client.renderer.NVGRenderer;
import wtf.opal.mixin.GameRendererAccessor;
import wtf.opal.utility.render.FramebufferUtility;
import org.slf4j.Logger;

import static wtf.opal.client.Constants.mc;

public final class ShaderFramebuffer {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static Framebuffer blurFramebuffer, glowFramebuffer;

    private static final Identifier BLUR_IDENTIFIER = Identifier.ofVanilla("blur");
    public static final CustomUniform CUSTOM_UNIFORM = new CustomUniform();

    private static PostEffectProcessor postEffectProcessor;

    public static void applyBlurToFullScreen() {
        if (blurFramebuffer == null) return;

        final PostProcessingModule postProcessingModule = OpalClient.getInstance().getModuleRepository().getModule(PostProcessingModule.class);

        if (!postProcessingModule.isEnabled() || !postProcessingModule.isBlur()) {
            RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(blurFramebuffer.getColorAttachment(), 0, blurFramebuffer.getDepthAttachment(), 1.0);
            return;
        }

        final Framebuffer mainBuffer = mc.getFramebuffer();

        FramebufferUtility.blit(mainBuffer, blurFramebuffer);

        renderBlurToFramebuffer(blurFramebuffer, postProcessingModule.getBlurRadius());
    }

    public static void applyGlowToNVGObjects() {
        if (glowFramebuffer == null) return;

        final PostProcessingModule postProcessingModule = OpalClient.getInstance().getModuleRepository().getModule(PostProcessingModule.class);

        if (!postProcessingModule.isEnabled() || !postProcessingModule.isBloom()) {
            RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(glowFramebuffer.getColorAttachment(), 0, glowFramebuffer.getDepthAttachment(), 1.0);
            return;
        }

        renderBlurToFramebuffer(glowFramebuffer, postProcessingModule.getBloomRadius());
    }

    public static void captureMenuBlur(final int radius) {
        if (blurFramebuffer == null || mc.getOverlay() instanceof SplashOverlay) {
            return;
        }
        FramebufferUtility.blit(mc.getFramebuffer(), blurFramebuffer);
        renderBlurToFramebuffer(blurFramebuffer, radius);
    }

    private static void renderBlurToFramebuffer(final Framebuffer framebuffer, final int radius) {
        if (mc.getOverlay() instanceof SplashOverlay) {
            // A resource reload can begin while the current frame graph still owns this processor.
            // Closing it here invalidates its MappableRingBuffer before vanilla finishes renderBlur.
            postEffectProcessor = null;
            return;
        }

        if (postEffectProcessor == null) {
            postEffectProcessor = mc.getShaderLoader().loadPostEffect(
                    BLUR_IDENTIFIER, DefaultFramebufferSet.MAIN_ONLY
            );
        } else {
            final FrameGraphBuilder frameGraphBuilder = new FrameGraphBuilder();
            final PostEffectProcessor.FramebufferSet framebufferSet = PostEffectProcessor.FramebufferSet.singleton(
                    Identifier.ofVanilla("main"), frameGraphBuilder.createObjectNode("main", framebuffer)
            );
            CUSTOM_UNIFORM.use(mc.getWindow().getFramebufferWidth(), mc.getWindow().getFramebufferHeight(), radius, () -> {
                try {
                    postEffectProcessor.render(frameGraphBuilder, framebuffer.textureWidth, framebuffer.textureHeight, framebufferSet);
                    frameGraphBuilder.run(((GameRendererAccessor) mc.gameRenderer).getPool());
                } catch (IllegalStateException exception) {
                    // Shader/resource reloads may retire a post effect between frames. Rebuild it on the next frame.
                    LOGGER.debug("Discarding stale OpenOpal post effect after GPU resource invalidation", exception);
                    postEffectProcessor = null;
                }
            });
        }
    }

    public static void onResized(final int width, final int height) {
        if (blurFramebuffer != null)
            blurFramebuffer.delete();
        if (glowFramebuffer != null)
            glowFramebuffer.delete();

        blurFramebuffer = new WindowFramebuffer(width, height);
        RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(blurFramebuffer.getColorAttachment(), 0, blurFramebuffer.getDepthAttachment(), 1.0);

        glowFramebuffer = new WindowFramebuffer(width, height);
        RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(glowFramebuffer.getColorAttachment(), 0, glowFramebuffer.getDepthAttachment(), 1.0);

        NVGRenderer.createNVGPaintFromTex(width, height, Integer.parseInt(blurFramebuffer.getColorAttachment().getLabel()), NVGRenderer.BLUR_PAINT);
        NVGRenderer.createNVGPaintFromTex(width, height, Integer.parseInt(glowFramebuffer.getColorAttachment().getLabel()), NVGRenderer.GLOW_PAINT);
    }

    public static Framebuffer getGlowFramebuffer() {
        return glowFramebuffer;
    }
}

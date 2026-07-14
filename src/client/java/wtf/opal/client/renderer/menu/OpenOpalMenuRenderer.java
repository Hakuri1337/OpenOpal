package wtf.opal.client.renderer.menu;

import com.mojang.logging.LogUtils;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.ColorHelper;
import net.minecraft.util.math.MathHelper;
import org.slf4j.Logger;
import wtf.opal.client.OpalClient;
import wtf.opal.client.feature.module.impl.visual.ClickGUIModule;

import java.io.IOException;
import java.io.InputStream;

import static wtf.opal.client.Constants.mc;

public final class OpenOpalMenuRenderer {

    private static final Logger LOGGER = LogUtils.getLogger();
    public static final Identifier LOGO = Identifier.of("opal", "dynamic/openopal_logo");
    public static final Identifier LOGO_GLOW = Identifier.of("opal", "dynamic/openopal_logo_glow");
    public static final Identifier WORDMARK = Identifier.of("opal", "dynamic/openopal_wordmark");
    public static final Identifier MENU_BUTTON = Identifier.of("opal", "dynamic/openopal_menu_button");
    public static final Identifier MENU_BUTTON_HOVER = Identifier.of("opal", "dynamic/openopal_menu_button_hover");
    public static final Identifier MENU_BUTTON_DISABLED = Identifier.of("opal", "dynamic/openopal_menu_button_disabled");
    public static final Identifier BOOT_SPINNER_DOT = Identifier.of("opal", "dynamic/openopal_boot_spinner_dot");
    private static TextureManager brandTextureManager;
    private static NativeImageBackedTexture logoTexture;
    private static NativeImageBackedTexture logoGlowTexture;
    private static NativeImageBackedTexture wordmarkTexture;
    private static NativeImageBackedTexture menuButtonTexture;
    private static NativeImageBackedTexture menuButtonHoverTexture;
    private static NativeImageBackedTexture menuButtonDisabledTexture;
    private static NativeImageBackedTexture bootSpinnerDotTexture;
    private static int logoTextureWidth;
    private static int logoTextureHeight;
    private static int logoGlowTextureWidth;
    private static int logoGlowTextureHeight;
    private static int wordmarkTextureWidth;
    private static int wordmarkTextureHeight;
    private static int bootSpinnerDotTextureWidth;
    private static int bootSpinnerDotTextureHeight;
    private static boolean brandTexturesReady;
    private static boolean brandTextureWarningLogged;

    private OpenOpalMenuRenderer() {
    }

    public static void registerBrandTextures(final TextureManager textureManager) {
        try {
            final LoadedBrandTexture newLogo = loadTexture("/assets/opal/images/logo_hd.png", "OpenOpal boot logo");
            final LoadedBrandTexture newLogoGlow = loadTexture("/assets/opal/images/logo_glow.png", "OpenOpal logo glow");
            final LoadedBrandTexture newWordmark = loadTexture("/assets/opal/images/openopal_wordmark.png", "OpenOpal wordmark");
            final LoadedBrandTexture newMenuButton = loadTexture("/assets/opal/images/menu_button.png", "OpenOpal menu button");
            final LoadedBrandTexture newMenuButtonHover = loadTexture("/assets/opal/images/menu_button_hover.png", "OpenOpal hovered menu button");
            final LoadedBrandTexture newMenuButtonDisabled = loadTexture("/assets/opal/images/menu_button_disabled.png", "OpenOpal disabled menu button");
            final LoadedBrandTexture newBootSpinnerDot = loadTexture("/assets/opal/images/boot_spinner_dot.png", "OpenOpal startup spinner dot");
            textureManager.registerTexture(LOGO, newLogo.texture);
            textureManager.registerTexture(LOGO_GLOW, newLogoGlow.texture);
            textureManager.registerTexture(WORDMARK, newWordmark.texture);
            textureManager.registerTexture(MENU_BUTTON, newMenuButton.texture);
            textureManager.registerTexture(MENU_BUTTON_HOVER, newMenuButtonHover.texture);
            textureManager.registerTexture(MENU_BUTTON_DISABLED, newMenuButtonDisabled.texture);
            textureManager.registerTexture(BOOT_SPINNER_DOT, newBootSpinnerDot.texture);
            brandTextureManager = textureManager;
            logoTexture = newLogo.texture;
            logoGlowTexture = newLogoGlow.texture;
            wordmarkTexture = newWordmark.texture;
            menuButtonTexture = newMenuButton.texture;
            menuButtonHoverTexture = newMenuButtonHover.texture;
            menuButtonDisabledTexture = newMenuButtonDisabled.texture;
            bootSpinnerDotTexture = newBootSpinnerDot.texture;
            logoTextureWidth = newLogo.width;
            logoTextureHeight = newLogo.height;
            logoGlowTextureWidth = newLogoGlow.width;
            logoGlowTextureHeight = newLogoGlow.height;
            wordmarkTextureWidth = newWordmark.width;
            wordmarkTextureHeight = newWordmark.height;
            bootSpinnerDotTextureWidth = newBootSpinnerDot.width;
            bootSpinnerDotTextureHeight = newBootSpinnerDot.height;
            brandTexturesReady = true;
            brandTextureWarningLogged = false;
        } catch (IOException | RuntimeException exception) {
            brandTexturesReady = false;
            LOGGER.error("Unable to initialize OpenOpal startup branding", exception);
        }
    }

    private static LoadedBrandTexture loadTexture(final String resourcePath, final String label) throws IOException {
        try (InputStream input = OpenOpalMenuRenderer.class.getResourceAsStream(resourcePath)) {
            if (input == null) {
                throw new IOException("Missing " + resourcePath);
            }
            final NativeImage image = NativeImage.read(input);
            final NativeImageBackedTexture texture = new NativeImageBackedTexture(() -> label, image);
            texture.setFilter(true, false);
            return new LoadedBrandTexture(texture, image.getWidth(), image.getHeight());
        }
    }

    public static boolean isEnhancedMenuEnabled() {
        try {
            final ClickGUIModule clickGUI = OpalClient.getInstance().getModuleRepository().getModule(ClickGUIModule.class);
            return clickGUI != null && clickGUI.isEnhancedMainMenu();
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    public static void renderBackground(final DrawContext context, final int width, final int height) {
        MenuVideoBackground.render(context, width, height);
        context.fill(0, 0, width, height, 0x56000000);
    }

    public static void renderBootBranding(final DrawContext context, final int width, final int height,
                                           final long reloadCompleteTime, final float loadingProgress) {
        context.createNewRootLayer();
        final float exitProgress = ClientBootTransition.getBrandProgress(reloadCompleteTime);
        final float bootAlpha = 1F - exitProgress;
        if (bootAlpha > 0.01F) {
            drawBootLockup(context, width, height, bootAlpha, loadingProgress);
        }
    }

    public static void renderTitleBranding(final DrawContext context, final int width, final int height, final float alpha) {
        drawBrandLockup(context, width, height, 1F, alpha);
    }

    public static void renderFooter(final DrawContext context, final int width, final int height) {
        context.drawTextWithShadow(mc.textRenderer, "OPENOPAL  /  stable-b2", 8, height - 22, 0xBFFFFFFF);
    }

    private static void drawBrandLockup(final DrawContext context, final int width, final int height,
                                        final float progress, final float alpha) {
        if (!areBrandTexturesDrawable()) {
            return;
        }
        final float responsiveScale = MathHelper.clamp(width / 640F, 0.72F, 1.15F);
        final int finalLogoSize = Math.round(64F * responsiveScale);
        final int wordmarkHeight = Math.round(46F * responsiveScale);
        final int wordmarkWidth = Math.round(wordmarkHeight * wordmarkTextureWidth / (float) wordmarkTextureHeight);
        final int gap = Math.round(12F * responsiveScale);
        final int groupWidth = wordmarkWidth + gap + finalLogoSize;
        final int groupX = (width - groupWidth) / 2;
        final float finalLogoCenterX = groupX + wordmarkWidth + gap + finalLogoSize / 2F;
        final float finalCenterY = 19F + Math.max(finalLogoSize, wordmarkHeight) / 2F;

        final float time = Util.getMeasuringTimeMs() / 1000F;
        final float waiting = 1F - MathHelper.clamp(progress, 0F, 1F);
        final float pulse = waiting * (0.5F + 0.5F * (float) Math.sin(time * 1.75F));
        final int startLogoSize = Math.round(Math.min(132F, Math.min(width, height) * 0.24F));
        final float logoCenterX = MathHelper.lerp(progress, width / 2F, finalLogoCenterX);
        final float logoCenterY = MathHelper.lerp(progress, height / 2F - 16F, finalCenterY);
        final int logoSize = Math.round(MathHelper.lerp(progress, startLogoSize, finalLogoSize) * (1F + pulse * 0.025F));
        final int logoColor = ColorHelper.getWhite(alpha);
        final float rotation = waiting * (float) Math.sin(time * 0.72F) * 0.035F
                + progress * (float) (Math.PI * 2D);
        final float glowAlpha = alpha * (0.17F + waiting * (0.08F + pulse * 0.08F));

        drawRotatedTexture(
                context, LOGO_GLOW, logoCenterX, logoCenterY, Math.round(logoSize * 1.56F),
                rotation * 0.14F, ColorHelper.getWhite(glowAlpha), logoGlowTextureWidth, logoGlowTextureHeight
        );
        drawRotatedTexture(
                context, LOGO, logoCenterX, logoCenterY, logoSize, rotation, logoColor,
                logoTextureWidth, logoTextureHeight
        );

        final float wordmarkProgress = MathHelper.clamp((progress - 0.34F) / 0.48F, 0F, 1F);
        if (wordmarkProgress <= 0F) {
            return;
        }
        final float easedWordmark = 1F - (float) Math.pow(1F - wordmarkProgress, 3D);
        final int wordmarkColor = ColorHelper.getWhite(alpha * easedWordmark);
        final int wordmarkX = groupX - Math.round((1F - easedWordmark) * 24F);
        final int wordmarkY = Math.round(finalCenterY - wordmarkHeight / 2F);
        context.drawTexture(
                RenderPipelines.GUI_TEXTURED,
                WORDMARK,
                wordmarkX,
                wordmarkY,
                0F,
                0F,
                wordmarkWidth,
                wordmarkHeight,
                wordmarkTextureWidth,
                wordmarkTextureHeight,
                wordmarkTextureWidth,
                wordmarkTextureHeight,
                wordmarkColor
        );
    }

    private static void drawBootLockup(final DrawContext context, final int width, final int height,
                                       final float alpha, final float loadingProgress) {
        if (!areBrandTexturesDrawable()) {
            return;
        }

        final int shortestSide = Math.min(width, height);
        final int logoSize = Math.round(MathHelper.clamp(shortestSide * 0.19F, 80F, 132F));
        final int wordmarkHeight = Math.round(MathHelper.clamp(shortestSide * 0.078F, 34F, 50F));
        final int wordmarkWidth = Math.round(wordmarkHeight * wordmarkTextureWidth / (float) wordmarkTextureHeight);
        final int logoCenterX = width / 2;
        final int logoCenterY = Math.round(height * 0.405F);
        final int wordmarkY = logoCenterY + logoSize / 2 + Math.max(12, Math.round(shortestSide * 0.022F));
        final int spinnerY = wordmarkY + wordmarkHeight + Math.max(30, Math.round(shortestSide * 0.06F));
        final float time = Util.getMeasuringTimeMs() / 1000F;
        final float breathe = 0.5F + 0.5F * (float) Math.sin(time * 1.45F);
        final float glowAlpha = alpha * (0.105F + breathe * 0.045F);

        drawRotatedTexture(
                context,
                LOGO_GLOW,
                logoCenterX,
                logoCenterY,
                Math.round(logoSize * 1.68F),
                0F,
                ColorHelper.getWhite(glowAlpha),
                logoGlowTextureWidth,
                logoGlowTextureHeight
        );
        drawRotatedTexture(
                context,
                LOGO,
                logoCenterX,
                logoCenterY,
                logoSize,
                0F,
                ColorHelper.getWhite(alpha),
                logoTextureWidth,
                logoTextureHeight
        );
        context.drawTexture(
                RenderPipelines.GUI_TEXTURED,
                WORDMARK,
                (width - wordmarkWidth) / 2,
                wordmarkY,
                0F,
                0F,
                wordmarkWidth,
                wordmarkHeight,
                wordmarkTextureWidth,
                wordmarkTextureHeight,
                wordmarkTextureWidth,
                wordmarkTextureHeight,
                ColorHelper.getWhite(alpha)
        );
        drawBootLoadingSpinner(context, logoCenterX, spinnerY, shortestSide, alpha, loadingProgress);
    }

    private static void drawRotatedTexture(final DrawContext context, final Identifier identifier,
                                           final float centerX, final float centerY, final int size,
                                           final float rotation, final int color,
                                           final int textureWidth, final int textureHeight) {
        context.getMatrices().pushMatrix();
        context.getMatrices().translate(centerX, centerY);
        context.getMatrices().rotate(rotation);
        context.drawTexture(
                RenderPipelines.GUI_TEXTURED,
                identifier,
                -size / 2,
                -size / 2,
                0F,
                0F,
                size,
                size,
                textureWidth,
                textureHeight,
                textureWidth,
                textureHeight,
                color
        );
        context.getMatrices().popMatrix();
    }

    private static boolean areBrandTexturesDrawable() {
        if (!brandTexturesReady || brandTextureManager == null || logoTexture == null
                || logoGlowTexture == null || wordmarkTexture == null || menuButtonTexture == null
                || menuButtonHoverTexture == null || menuButtonDisabledTexture == null
                || bootSpinnerDotTexture == null) {
            return false;
        }

        try {
            if (brandTextureManager.getTexture(LOGO) != logoTexture
                    || brandTextureManager.getTexture(LOGO_GLOW) != logoGlowTexture
                    || brandTextureManager.getTexture(WORDMARK) != wordmarkTexture
                    || brandTextureManager.getTexture(MENU_BUTTON) != menuButtonTexture
                    || brandTextureManager.getTexture(MENU_BUTTON_HOVER) != menuButtonHoverTexture
                    || brandTextureManager.getTexture(MENU_BUTTON_DISABLED) != menuButtonDisabledTexture
                    || brandTextureManager.getTexture(BOOT_SPINNER_DOT) != bootSpinnerDotTexture) {
                return false;
            }
            logoTexture.getGlTextureView();
            logoGlowTexture.getGlTextureView();
            wordmarkTexture.getGlTextureView();
            menuButtonTexture.getGlTextureView();
            menuButtonHoverTexture.getGlTextureView();
            menuButtonDisabledTexture.getGlTextureView();
            bootSpinnerDotTexture.getGlTextureView();
            return true;
        } catch (IllegalStateException exception) {
            if (!brandTextureWarningLogged) {
                brandTextureWarningLogged = true;
                LOGGER.warn("OpenOpal branding texture is not GPU-ready; skipping it until the next safe frame", exception);
            }
            return false;
        }
    }

    private static void drawBootLoadingSpinner(final DrawContext context, final int centerX, final int centerY,
                                               final int shortestSide, final float alpha,
                                               final float loadingProgress) {
        final int dotCount = 12;
        final int ringRadius = Math.round(MathHelper.clamp(shortestSide * 0.044F, 18F, 28F));
        final int baseDotSize = Math.round(MathHelper.clamp(shortestSide * 0.013F, 6F, 9F));
        final double phase = Util.getMeasuringTimeMs() / 1000D * 5.48D - Math.PI / 2D;

        for (int index = 0; index < dotCount; index++) {
            final float trailProgress = 1F - index / (float) dotCount;
            final float intensity = 0.045F + 0.955F * trailProgress * trailProgress;
            final double angle = phase - index * Math.PI * 2D / dotCount;
            final int dotSize = Math.max(1, Math.round(baseDotSize * (0.62F + intensity * 0.38F)));
            final int dotX = centerX + (int) Math.round(Math.cos(angle) * ringRadius) - dotSize / 2;
            final int dotY = centerY + (int) Math.round(Math.sin(angle) * ringRadius) - dotSize / 2;
            final float progressPulse = 0.96F + 0.04F * MathHelper.clamp(loadingProgress, 0F, 1F);

            context.drawTexture(
                    RenderPipelines.GUI_TEXTURED,
                    BOOT_SPINNER_DOT,
                    dotX,
                    dotY,
                    0F,
                    0F,
                    dotSize,
                    dotSize,
                    bootSpinnerDotTextureWidth,
                    bootSpinnerDotTextureHeight,
                    bootSpinnerDotTextureWidth,
                    bootSpinnerDotTextureHeight,
                    ColorHelper.getWhite(alpha * intensity * progressPulse)
            );
        }
    }

    private record LoadedBrandTexture(NativeImageBackedTexture texture, int width, int height) {
    }
}

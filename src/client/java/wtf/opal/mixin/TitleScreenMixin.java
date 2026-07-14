package wtf.opal.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.LogoDrawer;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.SplashTextRenderer;
import net.minecraft.client.font.TextRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import wtf.opal.client.renderer.menu.ClientBootTransition;
import wtf.opal.client.renderer.menu.MenuVideoBackground;
import wtf.opal.client.renderer.menu.OpenOpalMenuRenderer;
import wtf.opal.client.renderer.liquidglass.reglass.LiquidGlassUniforms;

@Mixin(TitleScreen.class)
public final class TitleScreenMixin {

    @Shadow
    private boolean doBackgroundFade;

    private TitleScreenMixin() {
    }

    @Inject(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screen/Screen;render(Lnet/minecraft/client/gui/DrawContext;IIF)V",
                    shift = At.Shift.BEFORE
            )
    )
    private void scheduleOpenOpalMenuGlass(final DrawContext context, final int mouseX, final int mouseY,
                                           final float delta, final CallbackInfo ci) {
        if (OpenOpalMenuRenderer.isEnhancedMenuEnabled()) {
            LiquidGlassUniforms.get().tryApplyBlur(context);
        }
    }

    @Inject(method = "<init>(ZLnet/minecraft/client/gui/LogoDrawer;)V", at = @At("TAIL"))
    private void synchronizeInitialFade(final boolean doBackgroundFade, final LogoDrawer logoDrawer, final CallbackInfo ci) {
        if (ClientBootTransition.isInitialBootActive()) {
            this.doBackgroundFade = false;
        }
    }

    @WrapOperation(
            method = "render",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/TitleScreen;renderPanoramaBackground(Lnet/minecraft/client/gui/DrawContext;F)V")
    )
    private void renderOpenOpalBackground(final TitleScreen instance, final DrawContext context, final float delta,
                                          final Operation<Void> original) {
        if (OpenOpalMenuRenderer.isEnhancedMenuEnabled()) {
            OpenOpalMenuRenderer.renderBackground(context, context.getScaledWindowWidth(), context.getScaledWindowHeight());
        } else {
            MenuVideoBackground.setActive(false);
            original.call(instance, context, delta);
        }
    }

    @WrapOperation(
            method = "render",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/LogoDrawer;draw(Lnet/minecraft/client/gui/DrawContext;IF)V")
    )
    private void renderOpenOpalTitle(final LogoDrawer instance, final DrawContext context, final int width,
                                     final float alpha, final Operation<Void> original) {
        if (ClientBootTransition.isInitialBootActive() && MinecraftClient.getInstance().getOverlay() != null) {
            return;
        }
        ClientBootTransition.finishInitialBoot();
        OpenOpalMenuRenderer.renderTitleBranding(context, width, context.getScaledWindowHeight(), alpha);
    }

    @WrapOperation(
            method = "render",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/SplashTextRenderer;render(Lnet/minecraft/client/gui/DrawContext;ILnet/minecraft/client/font/TextRenderer;F)V")
    )
    private void hideVanillaSplashText(final SplashTextRenderer instance, final DrawContext context, final int width,
                                       final TextRenderer textRenderer, final float alpha, final Operation<Void> original) {
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void renderOpenOpalFooter(final DrawContext context, final int mouseX, final int mouseY,
                                      final float delta, final CallbackInfo ci) {
        if (OpenOpalMenuRenderer.isEnhancedMenuEnabled()) {
            OpenOpalMenuRenderer.renderFooter(context, context.getScaledWindowWidth(), context.getScaledWindowHeight());
        }
    }

    @Inject(method = "removed", at = @At("HEAD"))
    private void stopMenuVideo(final CallbackInfo ci) {
        MenuVideoBackground.setActive(false);
    }
}

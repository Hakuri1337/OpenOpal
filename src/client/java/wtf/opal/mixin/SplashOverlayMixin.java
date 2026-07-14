package wtf.opal.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.SplashOverlay;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.resource.ResourceReload;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import wtf.opal.client.renderer.menu.ClientBootTransition;
import wtf.opal.client.renderer.menu.OpenOpalMenuRenderer;

import java.util.Optional;
import java.util.function.Consumer;

@Mixin(SplashOverlay.class)
public abstract class SplashOverlayMixin {

    @Shadow
    private float progress;

    @Shadow
    private long reloadCompleteTime;

    @Unique
    private boolean opal$initialBoot;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void detectInitialBoot(final MinecraftClient client, final ResourceReload reload,
                                   final Consumer<Optional<Throwable>> exceptionHandler, final boolean reloading,
                                   final CallbackInfo ci) {
        this.opal$initialBoot = !reloading && !client.isFinishedLoading();
        if (this.opal$initialBoot) {
            ClientBootTransition.beginInitialBoot();
        }
    }

    @Inject(method = "init", at = @At("TAIL"))
    private static void registerOpenOpalTextures(final TextureManager textureManager, final CallbackInfo ci) {
        OpenOpalMenuRenderer.registerBrandTextures(textureManager);
    }

    @ModifyExpressionValue(
            method = "render",
            at = @At(value = "INVOKE", target = "Ljava/util/function/IntSupplier;getAsInt()I")
    )
    private int useOpenOpalBootColor(final int original) {
        return this.opal$initialBoot ? 0xFF030507 : original;
    }

    @ModifyExpressionValue(
            method = "render",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/math/ColorHelper;getWhite(F)I")
    )
    private int hideMojangLogo(final int original) {
        return this.opal$initialBoot ? 0x00FFFFFF : original;
    }

    @Inject(method = "renderProgressBar", at = @At("HEAD"), cancellable = true)
    private void hideVanillaProgressBar(final DrawContext context, final int minX, final int minY,
                                        final int maxX, final int maxY, final float opacity,
                                        final CallbackInfo ci) {
        if (this.opal$initialBoot) {
            ci.cancel();
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void renderOpenOpalBootScreen(final DrawContext context, final int mouseX, final int mouseY,
                                          final float delta, final CallbackInfo ci) {
        if (!this.opal$initialBoot) {
            return;
        }
        OpenOpalMenuRenderer.renderBootBranding(
                context,
                context.getScaledWindowWidth(),
                context.getScaledWindowHeight(),
                this.reloadCompleteTime,
                this.progress
        );
    }
}

package wtf.opal.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import net.minecraft.client.render.*;
import net.minecraft.client.render.state.WorldRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import wtf.opal.client.OpalClient;
import wtf.opal.client.feature.helper.impl.render.FrustumHelper;
import wtf.opal.client.feature.module.impl.visual.AmbienceModule;

@Mixin(WorldRenderer.class)
public final class WorldRendererMixin {

    @Shadow
    @Final
    private WorldRenderState worldRenderState;

    @Redirect(
            method = "getTransparencyPostEffectProcessor",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/MinecraftClient;isFabulousGraphicsOrBetter()Z")
    )
    private boolean redirectFabulousGraphics() {
        return true;
    }

    @Inject(
            method = "renderSky",
            at = @At("HEAD")
    )
    private void redirectSkyType(FrameGraphBuilder frameGraphBuilder, Camera camera, GpuBufferSlice fogBuffer, CallbackInfo ci) {
        final AmbienceModule ambienceModule = OpalClient.getInstance().getModuleRepository().getModule(AmbienceModule.class);
        if (ambienceModule.isEnabled() && ambienceModule.isEndSky()) {
            // 10j3k check if this causes issues later on :v
            worldRenderState.skyRenderState.skyType = DimensionEffects.SkyType.END;
        }
    }

    @Inject(method = "render", at = @At(value = "INVOKE_ASSIGN", target = "Lnet/minecraft/client/render/WorldRenderer;setupFrustum(Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lnet/minecraft/util/math/Vec3d;)Lnet/minecraft/client/render/Frustum;", shift = At.Shift.AFTER))
    private void opal$hookFrustum$render(final CallbackInfo callbackInfo, final @Local Frustum frustum) {
        FrustumHelper.setFrustum(frustum);
    }
}

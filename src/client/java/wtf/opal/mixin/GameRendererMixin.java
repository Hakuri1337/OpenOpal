package wtf.opal.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.opengl.GlStateManager;
import net.minecraft.client.render.*;
import net.minecraft.client.util.memory.ObjectAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import wtf.opal.client.OpalClient;
import wtf.opal.client.feature.module.impl.combat.PiercingModule;
import wtf.opal.client.feature.module.impl.visual.NoFOVModule;
import wtf.opal.client.feature.module.impl.visual.NoHurtCameraModule;
import wtf.opal.client.renderer.shader.ShaderFramebuffer;
import wtf.opal.event.EventDispatcher;
import wtf.opal.event.impl.render.RenderWorldEvent;
import wtf.opal.utility.player.RaycastUtility;

import static wtf.opal.client.Constants.mc;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

    @Final
    @Shadow
    private BufferBuilderStorage buffers;

    @Inject(method = "onResized", at = @At("HEAD"))
    private void hookOnResized(int width, int height, CallbackInfo ci) {
        ShaderFramebuffer.onResized(width, height);
    }



    @WrapOperation(method = "renderWorld", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/WorldRenderer;render(Lnet/minecraft/client/util/memory/ObjectAllocator;Lnet/minecraft/client/render/RenderTickCounter;ZLnet/minecraft/client/render/Camera;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lorg/joml/Vector4f;Z)V"))
    private void hookRenderWorld(WorldRenderer instance, ObjectAllocator allocator, RenderTickCounter tickCounter, boolean renderBlockOutline, Camera camera, Matrix4f positionMatrix, Matrix4f matrix4f, Matrix4f projectionMatrix, GpuBufferSlice fogBuffer, Vector4f fogColor, boolean renderSky, Operation<Void> original, @Local(ordinal = 1) final Matrix4f matrix4f2) {
        original.call(instance, allocator, tickCounter, renderBlockOutline, camera, positionMatrix, matrix4f, projectionMatrix, fogBuffer, fogColor, renderSky);

        final MatrixStack stack = new MatrixStack();
        stack.multiplyPositionMatrix(positionMatrix);

        EventDispatcher.dispatch(new RenderWorldEvent(this.buffers.getEntityVertexConsumers(), stack, tickCounter.getTickProgress(false)));

        // restore state like the original world rendering code did
        GlStateManager._depthMask(true);
        GlStateManager._disableBlend();
    }

    @ModifyExpressionValue(
            method = "updateCrosshairTarget",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;getCrosshairTarget(FLnet/minecraft/entity/Entity;)Lnet/minecraft/util/hit/HitResult;")
    )
    private HitResult hookPiercingCrosshairTarget(HitResult original, float tickDelta) {
        if (!OpalClient.getInstance().getModuleRepository().getModule(PiercingModule.class).isEnabled()) {
            return original;
        }

        final Entity cameraEntity = mc.getCameraEntity();
        final HitResult hitResult = RaycastUtility.raycastEntity(mc.player.getEntityInteractionRange(), tickDelta, mc.player.getYaw(), mc.player.getPitch(), entity -> entity != cameraEntity);
        if (hitResult instanceof EntityHitResult) {
            return hitResult;
        }

        return original;
    }

    @Inject(
            method = "tiltViewWhenHurt",
            at = @At("HEAD"),
            cancellable = true
    )
    private void hookTiltViewWhenHurt(MatrixStack matrices, float tickDelta, CallbackInfo ci) {
        if (OpalClient.getInstance().getModuleRepository().getModule(NoHurtCameraModule.class).isEnabled()) {
            ci.cancel();
        }
    }

    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void hookNoFov(Camera camera, float tickDelta, boolean changingFov, CallbackInfoReturnable<Float> cir) {
        if (OpalClient.getInstance().getModuleRepository().getModule(NoFOVModule.class).isEnabled()) {
            cir.setReturnValue(mc.options.getFov().getValue().floatValue());
        }
    }
}

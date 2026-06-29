package wtf.opal.mixin;

import com.google.common.base.Predicates;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.opengl.GlStateManager;
import net.minecraft.client.render.*;
import net.minecraft.client.util.memory.ObjectAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
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

    @Unique
    private boolean passThroughBlocks;

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

    @Redirect(
            method = "findCrosshairTarget",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/hit/HitResult;getType()Lnet/minecraft/util/hit/HitResult$Type;")
    )
    private HitResult.Type redirectBlockHitResultType(HitResult instance) {
        if (passThroughBlocks) {
            passThroughBlocks = false;
            return HitResult.Type.MISS;
        }

        return instance.getType();
    }

    @Redirect(
            method = "findCrosshairTarget",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/math/Vec3d;squaredDistanceTo(Lnet/minecraft/util/math/Vec3d;)D", ordinal = 0)
    )
    private double redirectPassedThroughBlockDistance(Vec3d instance, Vec3d vec, @Local(ordinal = 1, argsOnly = true) double entityInteractionRange, @Local(argsOnly = true) float tickDelta) {
        if (OpalClient.getInstance().getModuleRepository().getModule(PiercingModule.class).isEnabled()) {
            final HitResult hitResult = RaycastUtility.raycastEntity(entityInteractionRange, tickDelta, mc.player.getYaw(), mc.player.getPitch(), Predicates.alwaysTrue());
            if (hitResult != null && hitResult.getType() == HitResult.Type.ENTITY) {
                passThroughBlocks = true;
                return Double.MAX_VALUE;
            }
        }

        return instance.squaredDistanceTo(vec);
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

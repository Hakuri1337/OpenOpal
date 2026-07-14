package wtf.opal.mixin;

import com.google.common.base.Predicates;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.render.*;
import net.minecraft.client.util.ObjectAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.effect.StatusEffects;
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
import wtf.opal.client.feature.module.impl.world.scaffold.ScaffoldModule;
import wtf.opal.client.renderer.liquidglass.reglass.LiquidGlassPipelines;
import wtf.opal.client.renderer.liquidglass.reglass.LiquidGlassPrecomputeRuntime;
import wtf.opal.client.renderer.liquidglass.reglass.LiquidGlassUniforms;
import wtf.opal.client.renderer.liquidglass.reglass.ReGlassAnim;
import wtf.opal.client.renderer.liquidglass.reglass.ReGlassConfig;
import wtf.opal.client.renderer.liquidglass.reglass.gui.QuadVertexBufferProvider;
import wtf.opal.client.renderer.shader.ShaderFramebuffer;
import wtf.opal.event.EventDispatcher;
import wtf.opal.event.impl.render.RenderWorldEvent;
import wtf.opal.utility.player.MoveUtility;
import wtf.opal.utility.player.RaycastUtility;

import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalInt;

import static wtf.opal.client.Constants.mc;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

    @Final
    @Shadow
    private BufferBuilderStorage buffers;

    @Unique
    private boolean passThroughBlocks;

    @Inject(method = "render", at = @At("HEAD"))
    private void opal$beginLiquidGlassFrame(final RenderTickCounter tickCounter, final boolean tick,
                                            final CallbackInfo ci) {
        double deltaTicks;
        try {
            deltaTicks = tickCounter.getDynamicDeltaTicks();
        } catch (Throwable ignored) {
            deltaTicks = 1.0 / 60.0 * 20.0;
        }
        final double deltaSeconds = deltaTicks / 20.0;
        LiquidGlassUniforms.get().beginFrame(deltaSeconds);
        ReGlassAnim.INSTANCE.update(ReGlassConfig.INSTANCE, deltaSeconds);
    }

    @Inject(method = "renderBlur", at = @At("HEAD"), cancellable = true)
    private void opal$renderLiquidGlass(final CallbackInfo ci) {
        final LiquidGlassUniforms uniforms = LiquidGlassUniforms.get();
        if (uniforms.getCount() == 0) {
            return;
        }

        ci.cancel();
        uniforms.logCompositeOnce();
        uniforms.uploadSharedUniforms();
        uniforms.uploadWidgetInfo();

        final List<Integer> radii = uniforms.getUsedBlurRadiiOrdered();
        final LiquidGlassPrecomputeRuntime precompute = LiquidGlassPrecomputeRuntime.get();
        precompute.setRequestedRadii(radii);
        precompute.run();

        final Framebuffer mainFramebuffer = mc.getFramebuffer();
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "opal liquid glass composite",
                mainFramebuffer.getColorAttachmentView(),
                OptionalInt.empty(),
                mainFramebuffer.useDepthAttachment ? mainFramebuffer.getDepthAttachmentView() : null,
                OptionalDouble.empty()
        )) {
            final RenderPipeline pipeline = LiquidGlassPipelines.getGuiPipeline();
            pass.setPipeline(pipeline);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("SamplerInfo", uniforms.getSamplerInfoBuffer());
            pass.setUniform("CustomUniforms", uniforms.getCustomUniformsBuffer());
            pass.setUniform("WidgetInfo", uniforms.getWidgetInfoBuffer());
            pass.setUniform("BgConfig", uniforms.getBgConfigBuffer());
            pass.bindSampler("Sampler0", precompute.getSourceView());

            final GuiRenderer guiRenderer = ((GameRendererAccessor) (Object) this).getGuiRenderer();
            final GpuBuffer quadVertexBuffer = ((QuadVertexBufferProvider) guiRenderer).getQuadVertexBuffer();
            final RenderSystem.ShapeIndexBuffer indexBufferInfo =
                    RenderSystem.getSequentialBuffer(VertexFormat.DrawMode.QUADS);
            final GpuBuffer indexBuffer = indexBufferInfo.getIndexBuffer(6);
            pass.setVertexBuffer(0, quadVertexBuffer);
            pass.setIndexBuffer(indexBuffer, indexBufferInfo.getIndexType());

            for (int index = 0; index < LiquidGlassUniforms.MAX_BLUR_LEVELS; index++) {
                final String samplerName = switch (index) {
                    case 0 -> "Sampler1";
                    case 1 -> "Sampler2";
                    case 2 -> "Sampler3";
                    case 3 -> "Sampler4";
                    default -> "Sampler5";
                };
                final int radius = radii.isEmpty() ? 0 : radii.get(Math.min(index, radii.size() - 1));
                pass.bindSampler(
                        samplerName,
                        radius <= 0
                                ? precompute.getSourceView()
                                : precompute.getBlurredViewForRadius(radius)
                );
            }

            pass.drawIndexed(0, 0, 6, 1);
        }
    }

    @Inject(method = "onResized", at = @At("HEAD"))
    private void hookOnResized(int width, int height, CallbackInfo ci) {
        ShaderFramebuffer.onResized(width, height);
    }



    @WrapOperation(method = "renderWorld", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/WorldRenderer;render(Lnet/minecraft/client/util/ObjectAllocator;Lnet/minecraft/client/render/RenderTickCounter;ZLnet/minecraft/client/render/Camera;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lorg/joml/Vector4f;Z)V"))
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
            return;
        }

        final ScaffoldModule scaffold = OpalClient.getInstance().getModuleRepository().getModule(ScaffoldModule.class);
        if (scaffold.isEnabled()
                && scaffold.isSilenceTellyMode()
                && scaffold.getSettings().isSilenceTellyKeepFov()
                && mc.player != null
                && MoveUtility.isMoving()) {
            float fov = scaffold.getSettings().getSilenceTellyFov();
            if (mc.player.hasStatusEffect(StatusEffects.SPEED)) {
                fov += (mc.player.getStatusEffect(StatusEffects.SPEED).getAmplifier() + 1) * 0.13F;
            }
            cir.setReturnValue(fov);
        }
    }
}

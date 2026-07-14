package wtf.opal.mixin.reglass;

import com.google.common.collect.ImmutableMap;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.gui.render.SpecialGuiElementRenderer;
import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.client.gui.render.state.special.SpecialGuiElementRenderState;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import org.lwjgl.system.MemoryStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import wtf.opal.client.renderer.liquidglass.reglass.gui.LiquidGlassGuiElementRenderer;
import wtf.opal.client.renderer.liquidglass.reglass.gui.QuadVertexBufferProvider;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.List;

@Mixin(GuiRenderer.class)
public final class ReGlassGuiRendererMixin implements QuadVertexBufferProvider {

    @Unique
    private GpuBuffer opal$liquidGlassQuadVertexBuffer;

    @Redirect(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/google/common/collect/ImmutableMap$Builder;buildOrThrow()Lcom/google/common/collect/ImmutableMap;"
            )
    )
    private ImmutableMap<Class<? extends SpecialGuiElementRenderState>, SpecialGuiElementRenderer<?>> opal$addLiquidGlassRenderer(
            final ImmutableMap.Builder<Class<? extends SpecialGuiElementRenderState>, SpecialGuiElementRenderer<?>> builder
    ) {
        final VertexConsumerProvider.Immediate vertexConsumers =
                ((GuiRendererAccessor) (Object) this).opal$getVertexConsumers();
        final LiquidGlassGuiElementRenderer renderer = new LiquidGlassGuiElementRenderer(vertexConsumers);
        builder.put(renderer.getElementClass(), renderer);
        return builder.buildOrThrow();
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void opal$createLiquidGlassQuad(
            final GuiRenderState state,
            final VertexConsumerProvider.Immediate vertexConsumers,
            final OrderedRenderCommandQueue queue,
            final net.minecraft.client.render.command.RenderDispatcher dispatcher,
            final List<SpecialGuiElementRenderer<?>> specialElementRenderers,
            final CallbackInfo ci
    ) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            final ByteBuffer byteBuffer = stack.malloc(4 * 3 * Float.BYTES);
            final FloatBuffer floatBuffer = byteBuffer.asFloatBuffer();
            floatBuffer.put(new float[]{
                    -1.0F, -1.0F, 0.0F,
                    1.0F, -1.0F, 0.0F,
                    1.0F, 1.0F, 0.0F,
                    -1.0F, 1.0F, 0.0F
            });
            byteBuffer.rewind();
            this.opal$liquidGlassQuadVertexBuffer = RenderSystem.getDevice().createBuffer(
                    () -> "opal_liquid_glass_quad",
                    32,
                    byteBuffer
            );
        }
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void opal$closeLiquidGlassQuad(final CallbackInfo ci) {
        if (this.opal$liquidGlassQuadVertexBuffer != null) {
            this.opal$liquidGlassQuadVertexBuffer.close();
            this.opal$liquidGlassQuadVertexBuffer = null;
        }
    }

    @Override
    public GpuBuffer getQuadVertexBuffer() {
        return this.opal$liquidGlassQuadVertexBuffer;
    }
}

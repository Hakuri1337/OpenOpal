package wtf.opal.client.renderer.liquidglass.reglass.gui;

import net.minecraft.client.gui.render.SpecialGuiElementRenderer;
import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import wtf.opal.client.renderer.liquidglass.reglass.LiquidGlassUniforms;

public class LiquidGlassGuiElementRenderer extends SpecialGuiElementRenderer<LiquidGlassGuiElementRenderState> {

    public LiquidGlassGuiElementRenderer(VertexConsumerProvider.Immediate vertexConsumers) {
        super(vertexConsumers);
    }

    @Override
    public void render(LiquidGlassGuiElementRenderState element, GuiRenderState state, int scale) {
        LiquidGlassUniforms.get().addWidget(element);
    }

    @Override
    public Class<LiquidGlassGuiElementRenderState> getElementClass() {
        return LiquidGlassGuiElementRenderState.class;
    }

    @Override
    protected void render(LiquidGlassGuiElementRenderState element, MatrixStack matrices) {
    }

    @Override
    protected String getName() {
        return "opal_liquid_glass_widget";
    }
}

package wtf.opal.mixin;

import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.fog.FogRenderer;
import net.minecraft.client.util.Pool;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(GameRenderer.class)
public interface GameRendererAccessor {
    @Accessor
    Pool getPool();

    @Invoker
    float callGetFov(final Camera camera, final float tickDelta, final boolean changingFov);

    @Invoker
    void callTiltViewWhenHurt(final MatrixStack matrices, final float tickDelta);

    @Invoker
    void callBobView(final MatrixStack matrices, final float tickDelta);

    @Accessor
    GuiRenderer getGuiRenderer();

    @Accessor
    FogRenderer getFogRenderer();
}

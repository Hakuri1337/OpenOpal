package wtf.opal.mixin;

import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.cursor.StandardCursors;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.PressableWidget;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import wtf.opal.client.renderer.liquidglass.reglass.ReGlassApi;
import wtf.opal.client.renderer.liquidglass.reglass.WidgetStyle;
import wtf.opal.client.renderer.menu.OpenOpalMenuRenderer;

import static wtf.opal.client.Constants.mc;

@Mixin(PressableWidget.class)
public abstract class PressableWidgetMixin {

    private static final WidgetStyle OPAL_MENU_BUTTON = WidgetStyle.create()
            .tint(0x51575D, 0.42F)
            .blurRadius(12)
            .smoothing(0.0025F)
            .shadow(18F, 0.24F, 0F, 2F)
            .shadowColor(0x000000, 0.9F)
            .refractionThickness(18F)
            .refractionFactor(1.35F)
            .refractionDispersion(6F)
            .fresnelRange(28F)
            .fresnelHardness(18F)
            .fresnelFactor(16F)
            .glareRange(26F)
            .glareHardness(18F)
            .glareConvergence(48F)
            .glareOppositeFactor(72F)
            .glareFactor(68F)
            .glareAngleRad((float) Math.toRadians(-45));

    private static final WidgetStyle OPAL_MENU_BUTTON_DISABLED = WidgetStyle.create()
            .tint(0x34383C, 0.56F)
            .blurRadius(12)
            .smoothing(0.0025F)
            .shadow(12F, 0.15F, 0F, 1F)
            .shadowColor(0x000000, 0.8F)
            .refractionThickness(12F)
            .refractionFactor(1.18F)
            .refractionDispersion(2F)
            .fresnelRange(20F)
            .fresnelHardness(14F)
            .fresnelFactor(8F)
            .glareRange(20F)
            .glareHardness(14F)
            .glareConvergence(40F)
            .glareOppositeFactor(60F)
            .glareFactor(36F)
            .glareAngleRad((float) Math.toRadians(-45));

    @Inject(method = "renderWidget", at = @At("HEAD"), cancellable = true)
    private void renderOpenOpalMenuButton(final DrawContext context, final int mouseX, final int mouseY,
                                          final float delta, final CallbackInfo ci) {
        if (!(mc.currentScreen instanceof TitleScreen)
                || !OpenOpalMenuRenderer.isEnhancedMenuEnabled()
                || !((Object) this instanceof ButtonWidget button)
                || button.getWidth() < 90) {
            return;
        }

        final int x = button.getX();
        final int y = button.getY();
        final int width = button.getWidth();
        final int height = button.getHeight();
        final boolean hovered = button.isHovered();
        final int textColor = button.active ? 0xFFF4FAFF : 0xFF75808A;

        ReGlassApi.create(context)
                .fromWidget(button)
                .cornerRadius(5F)
                .style(button.active ? OPAL_MENU_BUTTON : OPAL_MENU_BUTTON_DISABLED)
                .hover(button.active && hovered ? 1F : 0F)
                .focus(button.active && button.isFocused() ? 1F : 0F)
                .render();

        final Identifier background = !button.active
                ? OpenOpalMenuRenderer.MENU_BUTTON_DISABLED
                : hovered ? OpenOpalMenuRenderer.MENU_BUTTON_HOVER : OpenOpalMenuRenderer.MENU_BUTTON;
        context.drawTexture(
                RenderPipelines.GUI_TEXTURED,
                background,
                x,
                y,
                0F,
                0F,
                width,
                height,
                256,
                32,
                256,
                32
        );
        context.drawCenteredTextWithShadow(mc.textRenderer, button.getMessage(), x + width / 2, y + (height - 8) / 2, textColor);
        if (hovered) {
            context.setCursor(button.active ? StandardCursors.POINTING_HAND : StandardCursors.NOT_ALLOWED);
        }
        ci.cancel();
    }
}

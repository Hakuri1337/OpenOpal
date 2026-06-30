package wtf.opal.mixin;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Style;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import wtf.opal.utility.misc.RunnableClickEvent;

@Mixin(Screen.class)
public final class ScreenMixin {

    private ScreenMixin() {
    }

    @Inject(method = "handleTextClick", at = @At(value = "HEAD"), cancellable = true)
    private void onInvalidClickEvent(Style style, CallbackInfoReturnable<Boolean> cir) {
        if (style.getClickEvent() instanceof RunnableClickEvent runnableClickEvent) {
            runnableClickEvent.getRunnable().run();
            cir.setReturnValue(true);
        }
    }
}

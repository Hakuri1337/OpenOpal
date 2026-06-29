package wtf.opal.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.ClickEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import wtf.opal.utility.misc.RunnableClickEvent;

@Mixin(Screen.class)
public final class ScreenMixin {

    private ScreenMixin() {
    }

    @Inject(method = "handleClickEvent(Lnet/minecraft/text/ClickEvent;Lnet/minecraft/client/MinecraftClient;Lnet/minecraft/client/gui/screen/Screen;)V", at = @At(value = "HEAD"), cancellable = true)
    private static void onInvalidClickEvent(ClickEvent clickEvent, MinecraftClient client, Screen screen, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        if (clickEvent instanceof RunnableClickEvent runnableClickEvent) {
            runnableClickEvent.getRunnable().run();
            ci.cancel();
        }
    }
}

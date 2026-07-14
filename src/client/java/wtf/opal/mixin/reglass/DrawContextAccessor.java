package wtf.opal.mixin.reglass;

import net.minecraft.client.gui.DrawContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(DrawContext.class)
public interface DrawContextAccessor {

    @Accessor("scissorStack")
    DrawContext.ScissorStack opal$getScissorStack();
}

package wtf.opal.mixin;

import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Map;

@Mixin(KeyBinding.class)
public interface KeyBindingAccessor {
    @Accessor
    int getTimesPressed();

    @Accessor
    InputUtil.Key getBoundKey();

    @Accessor("KEYS_BY_ID")
    static Map<String, KeyBinding> getKeysByID() {
        throw new AssertionError();
    }

    @Invoker
    void callReset();
}

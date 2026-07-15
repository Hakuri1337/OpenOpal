package wtf.opal.mixin;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.player.SkinTextures;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import wtf.opal.client.OpalClient;
import wtf.opal.client.feature.module.impl.visual.CapeModule;

import java.util.Objects;

import static wtf.opal.client.Constants.mc;

@Mixin(PlayerListEntry.class)
public final class PlayerListEntryMixin {

    @Final
    @Shadow
    private GameProfile profile;

    @Inject(method = "getSkinTextures", at = @At("TAIL"), cancellable = true)
    private void hookSkinTextures(final CallbackInfoReturnable<SkinTextures> cir) {
        if (!this.opal$isLocalPlayerProfile()) {
            return;
        }

        final CapeModule capeModule = OpalClient.getInstance().getModuleRepository().getModule(CapeModule.class);
        if (capeModule == null || !capeModule.isEnabled()) {
            return;
        }

        final CapeModule.CapeType capeType = capeModule.getType();
        final SkinTextures oldTextures = cir.getReturnValue();
        cir.setReturnValue(new SkinTextures(
                oldTextures.body(),
                capeType.getTextureAsset(),
                oldTextures.elytra(),
                oldTextures.model(),
                oldTextures.secure()
        ));
    }

    @Unique
    private boolean opal$isLocalPlayerProfile() {
        if (mc.player != null && Objects.equals(this.profile.id(), mc.player.getGameProfile().id())) {
            return true;
        }
        return mc.getSession().getUuidOrNull() != null
                && Objects.equals(this.profile.id(), mc.getSession().getUuidOrNull());
    }

}

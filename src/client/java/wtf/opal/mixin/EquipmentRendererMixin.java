package wtf.opal.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.equipment.EquipmentRenderer;
import net.minecraft.client.render.entity.state.BipedEntityRenderState;
import net.minecraft.client.texture.Sprite;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import wtf.opal.client.OpalClient;
import wtf.opal.client.feature.module.impl.visual.AnimationsModule;
import wtf.opal.utility.render.EntityRenderStateUtility;

@Mixin(EquipmentRenderer.class)
public abstract class
EquipmentRendererMixin {
    //10j3k revisit and check if this is right
    @WrapOperation(
            method = "render(Lnet/minecraft/client/render/entity/equipment/EquipmentModel$LayerType;Lnet/minecraft/registry/RegistryKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;ILnet/minecraft/util/Identifier;II)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/RenderLayer;getArmorCutoutNoCull(Lnet/minecraft/util/Identifier;)Lnet/minecraft/client/render/RenderLayer;")
    )
    private RenderLayer opal$renderArmorTintLayer(Identifier texture, Operation<RenderLayer> original) {
        final AnimationsModule animationsModule = OpalClient.getInstance().getModuleRepository().getModule(AnimationsModule.class);
        if (animationsModule.isEnabled() && animationsModule.isOldArmorDamageTint()) {
            return RenderLayer.getEntityCutoutNoCullZOffset(texture);
        }
        return original.call(texture);
    }
//
//    @WrapOperation(
//            method = "render(Lnet/minecraft/client/render/entity/equipment/EquipmentModel$LayerType;Lnet/minecraft/registry/RegistryKey;Lnet/minecraft/client/model/Model;Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;ILnet/minecraft/util/Identifier;)V",
//            at = @At(
//                    value = "INVOKE",
//                    target = "Lnet/minecraft/client/texture/Sprite;getTextureSpecificVertexConsumer(Lnet/minecraft/client/render/VertexConsumer;)Lnet/minecraft/client/render/VertexConsumer;"
//            )
//    )
//    private VertexConsumer opal$renderArmorTrimTintLayer(Sprite instance, VertexConsumer consumer, Operation<VertexConsumer> original,
//                                                         @Local(argsOnly = true) VertexConsumerProvider vertexConsumers) {
//        final AnimationsModule animationsModule = OpalClient.getInstance().getModuleRepository().getModule(AnimationsModule.class);
//        if (animationsModule.isEnabled() && animationsModule.isOldArmorDamageTint()) {
//            return instance.getTextureSpecificVertexConsumer(vertexConsumers.getBuffer(RenderLayer.getEntityCutoutNoCullZOffset(instance.getAtlasId())));
//        }
//        return original.call(instance, consumer);
//    }

    @ModifyArg(
            method = "render(Lnet/minecraft/client/render/entity/equipment/EquipmentModel$LayerType;Lnet/minecraft/registry/RegistryKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;ILnet/minecraft/util/Identifier;II)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/command/RenderCommandQueue;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/RenderLayer;IIILnet/minecraft/client/texture/Sprite;ILnet/minecraft/client/render/command/ModelCommandRenderer$CrumblingOverlayCommand;)V",
                    ordinal = 0
            ),
            index = 4
    )
    private int opal$modifyArmorUv(int light) {
        return opal$packUv(light);
    }

    @ModifyArg(
            method = "render(Lnet/minecraft/client/render/entity/equipment/EquipmentModel$LayerType;Lnet/minecraft/registry/RegistryKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;ILnet/minecraft/util/Identifier;II)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/command/RenderCommandQueue;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/RenderLayer;IIILnet/minecraft/client/texture/Sprite;ILnet/minecraft/client/render/command/ModelCommandRenderer$CrumblingOverlayCommand;)V",
                    ordinal = 2
            ),
            index = 4
    )
    private int opal$modifyArmorTrimUv(int light) {
        return opal$packUv(light);
    }

    @Unique
    private int opal$packUv(int original) {
        final BipedEntityRenderState humanRenderState = EntityRenderStateUtility.getHumanRenderState();

        final AnimationsModule animationsModule = OpalClient.getInstance().getModuleRepository().getModule(AnimationsModule.class);
        if (animationsModule.isEnabled() && animationsModule.isOldArmorDamageTint() && humanRenderState != null) {
            return OverlayTexture.packUv(OverlayTexture.getU(0), OverlayTexture.getV(humanRenderState.hurt));
        }

        return original;
    }

}
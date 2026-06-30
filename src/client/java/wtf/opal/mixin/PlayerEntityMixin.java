package wtf.opal.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import wtf.opal.client.OpalClient;
import wtf.opal.client.feature.helper.impl.LocalDataWatch;
import wtf.opal.client.feature.helper.impl.player.slot.SlotHelper;
import wtf.opal.client.feature.module.impl.combat.ReachModule;
import wtf.opal.client.feature.module.impl.world.breaker.BreakerModule;
import wtf.opal.client.feature.module.impl.world.FastBreakModule;
import wtf.opal.duck.PlayerEntityAccess;
import wtf.opal.event.EventDispatcher;
import wtf.opal.event.impl.game.player.movement.ClipAtLedgeEvent;
import wtf.opal.event.impl.game.player.movement.KeepSprintEvent;
import wtf.opal.utility.player.PlayerUtility;

import static wtf.opal.client.Constants.mc;

@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin extends LivingEntity implements PlayerEntityAccess {

    @Unique
    private KeepSprintEvent keepSprintEvent;

    protected PlayerEntityMixin(EntityType<? extends LivingEntity> entityType, World world) {
        super(entityType, world);
    }

    @Inject(
            method = "attack",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerEntity;setVelocity(Lnet/minecraft/util/math/Vec3d;)V")
    )
    private void hookKeepSprint(final Entity target, final CallbackInfo ci) {
        keepSprintEvent = new KeepSprintEvent();

        EventDispatcher.dispatch(keepSprintEvent);
    }

    @Redirect(
            method = "canHarvest",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerInventory;getSelectedStack()Lnet/minecraft/item/ItemStack;")
    )
    private ItemStack redirectHandStack(PlayerInventory instance) {
        final BreakerModule breakerModule = OpalClient.getInstance().getModuleRepository().getModule(BreakerModule.class);
        if (breakerModule.isEnabled() && breakerModule.isBreaking() && breakerModule.getSlot() != -1) {
            return instance.getStack(breakerModule.getSlot());
        }
        return instance.getSelectedStack();
    }

    @Inject(method = "clipAtLedge", at = @At("HEAD"), cancellable = true)
    private void hookClipAtLedge(CallbackInfoReturnable<Boolean> ci) {
        if ((Object) this == mc.player) {
            final ClipAtLedgeEvent clipAtLedgeEvent = new ClipAtLedgeEvent();
            EventDispatcher.dispatch(clipAtLedgeEvent);
            if (clipAtLedgeEvent.isUpdated()) {
                ci.setReturnValue(clipAtLedgeEvent.isClip());
            }
        }
    }

    @Redirect(
            method = "getBlockBreakingSpeed",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerInventory;getSelectedStack()Lnet/minecraft/item/ItemStack;")
    )
    private ItemStack redirectHandStack2(PlayerInventory instance) {
        final BreakerModule breakerModule = OpalClient.getInstance().getModuleRepository().getModule(BreakerModule.class);
        if (breakerModule.isEnabled() && breakerModule.isBreaking() && breakerModule.getSlot() != -1) {
            return instance.getStack(breakerModule.getSlot());
        }
        return instance.getSelectedStack();
    }

    @ModifyArg(
            method = "attack",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerEntity;setSprinting(Z)V")
    )
    private boolean hookKeepSprintState(final boolean sprinting) {
        return (keepSprintEvent == null || keepSprintEvent.isCancelled()) && mc.player.isSprinting();
    }

    @ModifyArg(
            method = "attack",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerEntity;setVelocity(Lnet/minecraft/util/math/Vec3d;)V")
    )
    private Vec3d hookKeepSprintVelocity(final Vec3d velocity) {
        return (keepSprintEvent == null || keepSprintEvent.isCancelled()) && mc.player.isSprinting() ? mc.player.getVelocity() : velocity;
    }

    @Inject(
            method = "tick",
            at = @At("TAIL")
    )
    private void incrementTicks(CallbackInfo ci) {
        if ((Object) this == mc.player) {
            final LocalDataWatch ldw = LocalDataWatch.get();
            if (this.isOnGround()) {
                ldw.airTicks = 0;
                ldw.groundTicks++;
            } else {
                ldw.airTicks++;
                ldw.groundTicks = 0;
            }
        }
    }

    @Unique
    private int visualLastAttackedTicks;
    @Unique
    private ItemStack visualSelectedItem;

    @Inject(
            method = "resetLastAttackedTicks",
            at = @At("TAIL")
    )
    private void resetVisualLastAttackedTicks(CallbackInfo ci) {
        this.visualLastAttackedTicks = 0;
    }

    @Inject(
            method = "tick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerEntity;getMainHandStack()Lnet/minecraft/item/ItemStack;")
    )
    private void checkVisualItemStackEquality(CallbackInfo ci) {
        if ((Object) this == mc.player) {
            ItemStack itemStack = SlotHelper.getInstance().getMainHandStack(mc.player);
            if (this.visualSelectedItem == null) {
                this.visualSelectedItem = ItemStack.EMPTY;
            }
            if (!ItemStack.areEqual(this.visualSelectedItem, itemStack)) {
                if (!ItemStack.areItemsEqual(this.visualSelectedItem, itemStack)) {
                    this.visualLastAttackedTicks = 0;
                }

                this.visualSelectedItem = itemStack.copy();
            }
        }
    }

    @Redirect(
            method = "tick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerEntity;resetLastAttackedTicks()V")
    )
    private void onSwapLastAttackedTicksReset(PlayerEntity instance) {
        this.lastAttackedTicks = 0;
    }

    @Inject(
            method = "tick",
            at = @At(value = "FIELD", target = "Lnet/minecraft/entity/player/PlayerEntity;lastAttackedTicks:I", ordinal = 0)
    )
    private void incrementLastAttackedTicks(CallbackInfo ci) {
        this.visualLastAttackedTicks++;
    }

    @Unique
    private float getVisualAttackCooldownProgressPerTick() {
        final double attackSpeed;
        SlotHelper slotHelper = SlotHelper.getInstance();
        if (slotHelper.isActive() && slotHelper.getSilence() != SlotHelper.Silence.NONE) {
            attackSpeed = PlayerUtility.getStackAttackSpeed(slotHelper.getMainHandStack(mc.player));
        } else {
            attackSpeed = this.getAttributeValue(EntityAttributes.ATTACK_SPEED);
        }
        return (float) (1.0 / attackSpeed * 20.0);
    }

    @Override
    @Unique
    public float opal$getVisualAttackCooldownProgress(float baseTime) {
        return MathHelper.clamp(((float) this.visualLastAttackedTicks + baseTime) / this.getVisualAttackCooldownProgressPerTick(), 0.0F, 1.0F);
    }

    @Inject(
            method = "getEntityInteractionRange",
            at = @At("HEAD"),
            cancellable = true
    )
    private void hookEntityReach(CallbackInfoReturnable<Double> cir) {
        final ReachModule reachModule = OpalClient.getInstance().getModuleRepository().getModule(ReachModule.class);
        if (reachModule.isEnabled())
            cir.setReturnValue(reachModule.getEntityInteractionRange());
    }

    @Inject(
            method = "getBlockInteractionRange",
            at = @At("HEAD"),
            cancellable = true
    )
    private void hookBlockReach(CallbackInfoReturnable<Double> cir) {
        final ReachModule reachModule = OpalClient.getInstance().getModuleRepository().getModule(ReachModule.class);
        if (reachModule.isEnabled())
            cir.setReturnValue(reachModule.getBlockInteractionRange());
    }

    @ModifyReturnValue(
            method = "getBlockBreakingSpeed",
            at = @At("RETURN")
    )
    private float addBreakingSpeedMultiplier(float original) {
        final FastBreakModule fastBreakModule = OpalClient.getInstance().getModuleRepository().getModule(FastBreakModule.class);
        if (fastBreakModule.isEnabled() && fastBreakModule.isSpeedEnabled()) {
            // Limit to 99% faster because of division by 0
            final float cappedSpeedIncrease = Math.min(fastBreakModule.getSpeed(), 99.0F);
            final float multiplier = 1.0F / (1.0F - (cappedSpeedIncrease / 100.0F));

            return original * multiplier;
        }
        return original;
    }

    @ModifyExpressionValue(method = "getBlockBreakingSpeed", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerEntity;isOnGround()Z"))
    private boolean hookAirBreakSlowdown(final boolean original) {
        final FastBreakModule fastBreakModule = OpalClient.getInstance().getModuleRepository().getModule(FastBreakModule.class);
        if ((Object) this == mc.player && fastBreakModule.isEnabled() && !fastBreakModule.getBreakSlowdowns().getProperty("In air").getValue()) {
            return true;
        }
        return original;
    }

    @ModifyExpressionValue(method = "getBlockBreakingSpeed", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerEntity;isSubmergedIn(Lnet/minecraft/registry/tag/TagKey;)Z"))
    private boolean hookWaterBreakSlowdown(final boolean original) {
        final FastBreakModule fastBreakModule = OpalClient.getInstance().getModuleRepository().getModule(FastBreakModule.class);
        if ((Object) this == mc.player && fastBreakModule.isEnabled() && !fastBreakModule.getBreakSlowdowns().getProperty("In water").getValue()) {
            return false;
        }
        return original;
    }

    @ModifyExpressionValue(method = "getBlockBreakingSpeed", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerEntity;hasStatusEffect(Lnet/minecraft/registry/entry/RegistryEntry;)Z"))
    private boolean hookFatigueBreakSlowdown(final boolean original) {
        final FastBreakModule fastBreakModule = OpalClient.getInstance().getModuleRepository().getModule(FastBreakModule.class);
        if ((Object) this == mc.player && fastBreakModule.isEnabled() && !fastBreakModule.getBreakSlowdowns().getProperty("Mining fatigue").getValue()) {
            return false;
        }
        return original;
    }

}

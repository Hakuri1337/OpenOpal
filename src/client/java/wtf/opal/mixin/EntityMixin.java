package wtf.opal.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.block.BlockState;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import wtf.opal.client.OpalClient;
import wtf.opal.client.feature.helper.impl.player.rotation.RotationHelper;
import wtf.opal.client.feature.module.impl.movement.MovementFixModule;
import wtf.opal.client.feature.module.impl.movement.physics.PhysicsModule;
import wtf.opal.event.EventDispatcher;
import wtf.opal.event.impl.game.player.movement.PostMoveEvent;
import wtf.opal.event.impl.game.player.movement.PreMoveEvent;
import wtf.opal.event.impl.game.player.movement.StuckInBlockEvent;
import wtf.opal.event.impl.game.player.movement.step.StepSuccessEvent;

import static wtf.opal.client.Constants.mc;

import java.lang.reflect.Field;

@Mixin(Entity.class)
public abstract class EntityMixin {

    @Shadow
    private World world;

    private EntityMixin() {
    }

    @Inject(
            method = "setYaw",
            at = @At("HEAD")
    )
    private void setYaw(float yaw, CallbackInfo ci) {
        this.checkRotation();
    }

    @Inject(
            method = "setPitch",
            at = @At("HEAD")
    )
    private void setPitch(float pitch, CallbackInfo ci) {
        this.checkRotation();
    }

    @Redirect(
            method = "updateVelocity",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;getYaw()F")
    )
    private float redirectYaw(Entity instance) {
        final boolean isPlayer = mc.player != null && (Object) this == mc.player;
        if (isPlayer && !OpalClient.getInstance().getModuleRepository().getModule(MovementFixModule.class).isFixMovement()) {
            return RotationHelper.getClientHandler().getYawOr(instance.getYaw());
        }
        return instance.getYaw();
    }

    @Inject(
            method = "updateVelocity",
            at = @At("HEAD"),
            cancellable = true
    )
    private void hookUpdateVelocityHead(float speed, Vec3d movementInput, CallbackInfo ci) {
        if ((Object) this == mc.player) {
            PreMoveEvent event = new PreMoveEvent(speed, movementInput);
            EventDispatcher.dispatch(event);
            if (event.isCancelled()) {
                ci.cancel();
            }
        }
    }

    @Inject(
            method = "updateVelocity",
            at = @At("TAIL")
    )
    private void hookUpdateVelocityTail(float speed, Vec3d movementInput, CallbackInfo ci) {
        if ((Object) this == mc.player)
            EventDispatcher.dispatch(new PostMoveEvent(speed, movementInput));
    }

    @Inject(method = "adjustMovementForCollisions(Lnet/minecraft/util/math/Vec3d;)Lnet/minecraft/util/math/Vec3d;",
            at = @At(value = "RETURN", ordinal = 0), cancellable = true)
    private void hookStepHeight(final Vec3d movement, final CallbackInfoReturnable<Vec3d> cir) {
        if ((Object) this == mc.player) {
            final StepSuccessEvent movementCollisionsEvent = new StepSuccessEvent(movement, cir.getReturnValue());
            EventDispatcher.dispatch(movementCollisionsEvent);
            cir.setReturnValue(movementCollisionsEvent.getAdjustedVec());
        }
    }

    @Redirect(method = "move", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/Entity;groundCollision:Z", opcode = Opcodes.PUTFIELD))
    private void hookMoveTail(Entity instance, boolean value, @Local(argsOnly = true) Vec3d movement) {
        if (instance instanceof ClientPlayerEntity && OpalClient.getInstance().getModuleRepository().getModule(PhysicsModule.class).isEnabled()) {
            instance.groundCollision = value && movement.y < -0.01D;
        } else {
            instance.groundCollision = value;
        }
    }

    @Inject(
            method = "slowMovement",
            at = @At("TAIL")
    )
    private void hookSlowMovement(final BlockState state, final Vec3d motion, final CallbackInfo ci) {
        if ((Object) this != mc.player) {
            return;
        }

        final StuckInBlockEvent event = new StuckInBlockEvent(state, motion);
        EventDispatcher.dispatch(event);
        this.setStuckSpeedMultiplierReflectively(event.getMotion());
    }

//    @Inject(
//            method = "adjustMovementForCollisions(Lnet/minecraft/util/math/Vec3d;)Lnet/minecraft/util/math/Vec3d;",
//            at = @At(value = "FIELD", target = "Lnet/minecraft/util/math/Vec3d;y:D", ordinal = 4),
//            cancellable = true
//    )
//    private void redirectAdjustMovementForCollisions(Vec3d movement, CallbackInfoReturnable<Vec3d> cir,
//                                                     @Local(ordinal = 1) Vec3d vec3d, @Local(ordinal = 0) Box box, @Local(ordinal = 1) Box box2, @Local(ordinal = 1) List<VoxelShape> list2) {
//        if (HypixelOffsetHelper.getInstance().isOffset()) { // fixing floating point math error that mc has so we can offset with precise values! lol!
//            double f = vec3d.y;
//            double[] fs = collectStepHeightsDouble(box2, list2, this.getStepHeight(), f);
//
//            for (double g : fs) {
//                Vec3d vec3d2 = adjustMovementForCollisions(new Vec3d(movement.x, g, movement.z), box2, list2);
//                if (vec3d2.horizontalLengthSquared() > vec3d.horizontalLengthSquared()) {
//                    double d = box.minY - box2.minY;
//                    cir.setReturnValue(vec3d2.add(0.0, -d, 0.0));
//                    return;
//                }
//            }
//
//            cir.setReturnValue(vec3d);
//        }
//    }
//
//    @Unique
//    private static double[] collectStepHeightsDouble(Box collisionBox, List<VoxelShape> collisions, float f, double stepHeight) {
//        DoubleSet doubleSet = new DoubleArraySet(4);
//
//        for (VoxelShape voxelShape : collisions) {
//            for (double d : voxelShape.getPointPositions(Direction.Axis.Y)) {
//                double g = d - collisionBox.minY;
//                if (!(g < 0.0D) && g != stepHeight) {
//                    if (g > f) {
//                        break;
//                    }
//
//                    doubleSet.add(g);
//                }
//            }
//        }
//
//        double[] fs = doubleSet.toDoubleArray();
//        DoubleArrays.unstableSort(fs);
//        return fs;
//    }

    @Unique
    private void checkRotation() {
        if (mc.player != null && (Object) this == mc.player && this.world.isClient()) {
            RotationHelper.getClientHandler().onRotationSet();
        }
    }

    @Unique
    private void setStuckSpeedMultiplierReflectively(final Vec3d motion) {
        final String[] fieldNames = {"stuckSpeedMultiplier", "movementMultiplier"};

        for (final String fieldName : fieldNames) {
            try {
                final Field field = Entity.class.getDeclaredField(fieldName);
                field.setAccessible(true);
                field.set(this, motion);
                return;
            } catch (ReflectiveOperationException ignored) {
            }
        }
    }

    @ModifyExpressionValue(method = "move", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;isLogicalSideForUpdatingMovement()Z"))
    private boolean fixFallDistanceCalculation(boolean original) {
        if ((Object) this == mc.player) {
            return true;
        }
        return original;
    }
}

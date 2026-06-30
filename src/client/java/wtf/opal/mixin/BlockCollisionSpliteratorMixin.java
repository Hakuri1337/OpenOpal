package wtf.opal.mixin;

import com.google.common.collect.AbstractIterator;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockCollisionSpliterator;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import wtf.opal.event.EventDispatcher;
import wtf.opal.event.impl.game.world.BlockShapeEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

@Mixin(BlockCollisionSpliterator.class)
public abstract class BlockCollisionSpliteratorMixin<T> extends AbstractIterator<T> {
    @Shadow
    @Final
    private BlockPos.Mutable pos;

    @Shadow
    @Final
    private Box box;

    @Shadow
    @Final
    private BiFunction<BlockPos.Mutable, VoxelShape, T> resultFunction;

    private BlockCollisionSpliteratorMixin() {
    }

    @Unique
    private List<VoxelShape> extraVoxelShapes;

    @ModifyExpressionValue(
            method = "computeNext",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/block/ShapeContext;getCollisionShape(Lnet/minecraft/block/BlockState;Lnet/minecraft/world/CollisionView;Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/util/shape/VoxelShape;")
    )
    private VoxelShape hookComputeNextShape(VoxelShape original, @Local BlockState blockState) {
        if (this.pos != null) {
            final BlockShapeEvent event = new BlockShapeEvent(this.pos, blockState, original, this.extraVoxelShapes);
            EventDispatcher.dispatch(event);
            return event.getVoxelShape();
        }
        return original;
    }

    @Inject(
            method = "computeNext",
            at = @At("HEAD"),
            cancellable = true
    )
    private void hookComputeNextHead(CallbackInfoReturnable<T> cir) {
        this.handleExtraVoxels(cir);
    }

    @Inject(
            method = "computeNext",
            at = @At(value = "INVOKE", target = "Lcom/google/common/collect/AbstractIterator;endOfData()Ljava/lang/Object;"),
            cancellable = true
    )
    private void hookComputeNextReturn(CallbackInfoReturnable<T> cir) {
        this.handleExtraVoxels(cir);
    }

    @Unique
    private void handleExtraVoxels(CallbackInfoReturnable<T> cir) {
        if (this.extraVoxelShapes == null) {
            this.extraVoxelShapes = new ArrayList<>();
        }
        if (!this.extraVoxelShapes.isEmpty()) {
            VoxelShape voxelShape = this.extraVoxelShapes.removeFirst();
            cir.setReturnValue(this.resultFunction.apply(this.pos, voxelShape));
        }
    }
}

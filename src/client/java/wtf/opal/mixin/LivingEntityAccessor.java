package wtf.opal.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LivingEntity.class)
public interface LivingEntityAccessor {
    @Accessor("LIVING_FLAGS")
    static TrackedData<Byte> getTrackedLivingFlags() {
        throw new AssertionError();
    }

    @Invoker
    void callTravelMidAir(Vec3d movementInput);

    @Invoker
    float callGetJumpVelocity();

    @Accessor
    void setJumpingCooldown(int jumpingCooldown);
}

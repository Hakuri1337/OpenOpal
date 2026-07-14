package wtf.opal.client.feature.module.impl.movement.speed.impl;

import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import wtf.opal.client.feature.helper.impl.LocalDataWatch;
import wtf.opal.client.feature.module.impl.movement.speed.SpeedModule;
import wtf.opal.client.feature.module.property.impl.bool.BooleanProperty;
import wtf.opal.client.feature.module.property.impl.mode.ModuleMode;
import wtf.opal.event.impl.game.input.MoveInputEvent;
import wtf.opal.event.impl.game.player.movement.JumpEvent;
import wtf.opal.event.impl.game.player.movement.PostMoveEvent;
import wtf.opal.event.subscriber.Subscribe;
import wtf.opal.utility.player.MoveUtility;

import static wtf.opal.client.Constants.mc;

public final class CubeCraftLowHopSpeed extends ModuleMode<SpeedModule> {

    private static final double AIR_TICK_ONE_Y_ADD = 0.0568D;
    private static final double HURT_STRAFE_MINIMUM = 0.281D;

    public static boolean shouldStrafe;

    private final BooleanProperty glide = new BooleanProperty("Glide", this, false).hideIf(() -> this.module.getActiveMode() != this);

    public CubeCraftLowHopSpeed(final SpeedModule module) {
        super(module);
    }

    @Subscribe
    public void onMoveInput(final MoveInputEvent event) {
        if (mc.player == null || mc.player.isSneaking()) {
            return;
        }

        if (mc.player.isOnGround() && this.isInputMoving(event)) {
            event.setJump(true);
        }
    }

    @Subscribe
    public void onJump(final JumpEvent event) {
        if (mc.player == null || !MoveUtility.isMoving()) {
            return;
        }

        final double minimum = 0.247D + 0.15D * this.getSpeedAmplifier();
        MoveUtility.setSpeed(Math.max(MoveUtility.getSpeed(), minimum));
        event.setSprinting(true);
        shouldStrafe = true;
    }

    @Subscribe
    public void onPostMove(final PostMoveEvent event) {
        if (mc.player == null) {
            return;
        }

        shouldStrafe = false;

        if (!MoveUtility.isMoving()) {
            MoveUtility.setSpeed(0.0D);
            return;
        }

        if (mc.player.isOnGround()) {
            MoveUtility.setSpeed(MoveUtility.getSpeed());
            shouldStrafe = true;
            return;
        }

        final int airTicks = LocalDataWatch.get().airTicks;
        switch (airTicks) {
            case 1 -> {
                MoveUtility.setSpeed(MoveUtility.getSpeed());
                this.addVelocityY(AIR_TICK_ONE_Y_ADD);
                shouldStrafe = true;
            }
            case 3 -> this.setVelocity(
                    mc.player.getVelocity().x * 0.95D,
                    mc.player.getVelocity().y - 0.13D,
                    mc.player.getVelocity().z * 0.95D
            );
            case 4 -> this.addVelocityY(-0.2D);
            case 7 -> {
                if (this.glide.getValue() && this.isGroundExempt()) {
                    this.setVelocityY(0.0D);
                }
            }
            default -> {
            }
        }

        if (this.isGroundExempt()) {
            MoveUtility.setSpeed(MoveUtility.getSpeed());
        }

        if (mc.player.hurtTime == 9) {
            MoveUtility.setSpeed(Math.max(MoveUtility.getSpeed(), HURT_STRAFE_MINIMUM));
        }

        if (this.getSpeedAmplifier() == 2 && this.isBoostAirTick(airTicks)) {
            final Vec3d velocity = mc.player.getVelocity();
            this.setVelocity(velocity.x * 1.2D, velocity.y, velocity.z * 1.2D);
        }
    }

    @Override
    public void onDisable() {
        shouldStrafe = false;
        if (mc.player != null) {
            final double maxSpeed = MoveUtility.getSwiftnessSpeed(0.221D);
            MoveUtility.setSpeed(Math.min(MoveUtility.getSpeed(), maxSpeed));
        }
        super.onDisable();
    }

    private boolean isGroundExempt() {
        if (mc.player == null || mc.world == null || mc.player.getVelocity().y >= 0.0D) {
            return false;
        }

        for (final VoxelShape shape : mc.world.getBlockCollisions(mc.player, mc.player.getBoundingBox().offset(0.0D, -0.66D, 0.0D))) {
            if (!shape.isEmpty()) {
                return true;
            }
        }

        return false;
    }

    private boolean isBoostAirTick(final int airTicks) {
        return airTicks == 1 || airTicks == 2 || airTicks == 5 || airTicks == 6 || airTicks == 8;
    }

    private boolean isInputMoving(final MoveInputEvent event) {
        return event.getForward() != 0.0F || event.getSideways() != 0.0F;
    }

    private int getSpeedAmplifier() {
        if (mc.player == null) {
            return 0;
        }

        final StatusEffectInstance speed = mc.player.getStatusEffect(StatusEffects.SPEED);
        return speed == null ? 0 : speed.getAmplifier();
    }

    private void addVelocityY(final double y) {
        final Vec3d velocity = mc.player.getVelocity();
        this.setVelocity(velocity.x, velocity.y + y, velocity.z);
    }

    private void setVelocityY(final double y) {
        final Vec3d velocity = mc.player.getVelocity();
        this.setVelocity(velocity.x, y, velocity.z);
    }

    private void setVelocity(final double x, final double y, final double z) {
        mc.player.setVelocity(x, y, z);
    }

    @Override
    public Enum<?> getEnumValue() {
        return SpeedModule.Mode.CUBECRAFT_LOW_HOP;
    }
}

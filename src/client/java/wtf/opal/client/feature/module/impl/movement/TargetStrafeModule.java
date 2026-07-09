package wtf.opal.client.feature.module.impl.movement;

import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import wtf.opal.client.OpalClient;
import wtf.opal.client.feature.helper.impl.LocalDataWatch;
import wtf.opal.client.feature.helper.impl.player.rotation.RotationHelper;
import wtf.opal.client.feature.module.Module;
import wtf.opal.client.feature.module.ModuleCategory;
import wtf.opal.client.feature.module.impl.combat.killaura.KillAuraModule;
import wtf.opal.client.feature.module.impl.combat.killaura.target.CurrentTarget;
import wtf.opal.client.feature.module.property.impl.bool.BooleanProperty;
import wtf.opal.client.feature.module.property.impl.number.NumberProperty;
import wtf.opal.event.impl.game.PreGameTickEvent;
import wtf.opal.event.impl.game.input.MoveInputEvent;
import wtf.opal.event.subscriber.Subscribe;
import wtf.opal.utility.player.MoveUtility;
import wtf.opal.utility.player.PlayerUtility;
import wtf.opal.utility.player.RotationUtility;

import static wtf.opal.client.Constants.mc;

public final class TargetStrafeModule extends Module {

    private static final double RANGE_TOLERANCE = 0.18D;
    private static final double EDGE_LOOKAHEAD = 0.38D;
    private static final float INPUT_STABILITY_MARGIN = 8.0F;
    private static final int TELEPORT_GRACE_TICKS = 4;

    private final BooleanProperty smartStrafe = new BooleanProperty("Jump Key Only", true);
    private final NumberProperty range = new NumberProperty("Range", 0.5D, 0.1D, 2.0D, 0.1D);
    private final NumberProperty switchDelay = new NumberProperty("Switch Delay", "ms", 1000.0D, 100.0D, 5000.0D, 100.0D);

    private int strafeDirectionSign = 1;
    private LivingEntity strafeTarget;
    private long lastSwitchTime;
    private long lastCollisionSwitchTime;
    private InputChoice lastInput = InputChoice.NONE;

    public TargetStrafeModule() {
        super("TargetStrafe", "Circles the current KillAura target.", ModuleCategory.MOVEMENT);
        this.addProperties(this.smartStrafe, this.range, this.switchDelay);
    }

    @Subscribe
    public void onPreGameTick(final PreGameTickEvent event) {
        if (mc.player == null || mc.world == null) {
            this.strafeTarget = null;
            this.lastInput = InputChoice.NONE;
            return;
        }

        this.updateTarget();
        if (this.strafeTarget == null || !this.isMovementStateSafe()) {
            this.lastInput = InputChoice.NONE;
            return;
        }

        final Box box = mc.player.getBoundingBox();
        final boolean aboveVoid = PlayerUtility.isBoxEmpty(box.offset(0.0D, -1.0D, 0.0D))
                && PlayerUtility.isBoxEmpty(box.offset(0.0D, -2.0D, 0.0D))
                && PlayerUtility.isBoxEmpty(box.offset(0.0D, -3.0D, 0.0D));
        if ((aboveVoid || mc.player.horizontalCollision) && System.currentTimeMillis() - this.lastCollisionSwitchTime >= 500L) {
            this.strafeDirectionSign *= -1;
            this.lastCollisionSwitchTime = System.currentTimeMillis();
        }
    }

    @Subscribe(priority = 1)
    public void onMoveInput(final MoveInputEvent event) {
        if (mc.player == null || this.strafeTarget == null || !this.strafeTarget.isAlive() || !this.isMovementStateSafe()) {
            this.lastInput = InputChoice.NONE;
            return;
        }

        if (this.smartStrafe.getValue() && !mc.options.jumpKey.isPressed()) {
            this.lastInput = InputChoice.NONE;
            return;
        }

        if (event.isSneak() || Math.abs(mc.player.getY() - this.strafeTarget.getY()) > 2.5D) {
            this.lastInput = InputChoice.NONE;
            return;
        }

        final double distance = this.getHorizontalDistance(this.strafeTarget);
        final float targetYaw = RotationUtility.getRotationFromPosition(this.strafeTarget.getEyePos()).x;
        float desiredYaw = this.getDesiredMoveYaw(targetYaw, distance);

        if (mc.player.isOnGround() && !this.hasGroundAhead(desiredYaw)) {
            this.switchDirection();
            desiredYaw = this.getDesiredMoveYaw(targetYaw, distance);
            if (!this.hasGroundAhead(desiredYaw)) {
                this.lastInput = InputChoice.NONE;
                return;
            }
        }

        final float referenceYaw = this.getMovementReferenceYaw();
        InputChoice input = this.findClosestInput(desiredYaw, referenceYaw);
        input = this.keepStableInput(input, desiredYaw, referenceYaw);

        event.setForward(input.forward);
        event.setSideways(input.sideways);
        this.lastInput = input;
    }

    private void updateTarget() {
        final KillAuraModule killAura = OpalClient.getInstance().getModuleRepository().getModule(KillAuraModule.class);
        if (killAura == null || !killAura.isEnabled()) {
            this.strafeTarget = null;
            return;
        }

        final CurrentTarget currentTarget = killAura.getTargeting().getTarget();
        if (currentTarget == null || currentTarget.getEntity() == null || !currentTarget.getEntity().isAlive()) {
            this.strafeTarget = null;
            return;
        }

        final LivingEntity currentEntity = currentTarget.getEntity();
        if (this.strafeTarget == currentEntity) {
            return;
        }

        final long now = System.currentTimeMillis();
        if (now - this.lastSwitchTime < this.switchDelay.getValue().longValue()) {
            this.strafeTarget = null;
            return;
        }

        this.strafeTarget = currentEntity;
        this.lastSwitchTime = now;
    }

    private boolean isMovementStateSafe() {
        final LocalDataWatch dataWatch = LocalDataWatch.get();
        return mc.currentScreen == null
                && mc.getOverlay() == null
                && !mc.player.isTouchingWater()
                && !mc.player.isInLava()
                && !mc.player.isClimbing()
                && !mc.player.hasVehicle()
                && !mc.player.isUsingItem()
                && (dataWatch == null || dataWatch.ticksSinceTeleport > TELEPORT_GRACE_TICKS);
    }

    private float getDesiredMoveYaw(final float targetYaw, final double distance) {
        final float orbitYaw = targetYaw + 90.0F * this.strafeDirectionSign;
        final double desiredRange = this.range.getValue();

        if (distance > desiredRange + RANGE_TOLERANCE) {
            return MathHelper.lerpAngleDegrees(0.45F, orbitYaw, targetYaw);
        }

        if (distance < Math.max(0.05D, desiredRange - RANGE_TOLERANCE)) {
            return MathHelper.lerpAngleDegrees(0.55F, orbitYaw, targetYaw + 180.0F);
        }

        return orbitYaw;
    }

    private InputChoice findClosestInput(final float desiredYaw, final float referenceYaw) {
        InputChoice best = InputChoice.NONE;
        float bestScore = Float.MAX_VALUE;

        for (int forward = -1; forward <= 1; forward++) {
            for (int sideways = -1; sideways <= 1; sideways++) {
                if (forward == 0 && sideways == 0) {
                    continue;
                }

                final float inputYaw = getInputYaw(referenceYaw, forward, sideways);
                float score = MathHelper.angleBetween(desiredYaw, inputYaw);
                if (forward < 0) {
                    score += 3.0F;
                }

                if (score < bestScore) {
                    bestScore = score;
                    best = new InputChoice(forward, sideways);
                }
            }
        }

        return best;
    }

    private InputChoice keepStableInput(final InputChoice next, final float desiredYaw, final float referenceYaw) {
        if (this.lastInput == InputChoice.NONE || this.lastInput.equals(next)) {
            return next;
        }

        final float previousYaw = getInputYaw(referenceYaw, this.lastInput.forward, this.lastInput.sideways);
        final float nextYaw = getInputYaw(referenceYaw, next.forward, next.sideways);
        final float previousDifference = MathHelper.angleBetween(desiredYaw, previousYaw);
        final float nextDifference = MathHelper.angleBetween(desiredYaw, nextYaw);

        if (previousDifference <= nextDifference + INPUT_STABILITY_MARGIN && this.hasGroundAhead(previousYaw)) {
            return this.lastInput;
        }

        return next;
    }

    private float getMovementReferenceYaw() {
        final MovementFixModule movementFix = OpalClient.getInstance().getModuleRepository().getModule(MovementFixModule.class);
        if (movementFix != null && movementFix.isFixMovement()) {
            return RotationHelper.getClientHandler().getYawOr(mc.player.getYaw());
        }
        return mc.player.getYaw();
    }

    private boolean hasGroundAhead(final float yaw) {
        final double[] offset = MoveUtility.yawPos((float) Math.toRadians(yaw), EDGE_LOOKAHEAD);
        final Box checkBox = mc.player.getBoundingBox().expand(-0.05D, 0.0D, -0.05D).offset(offset[0], -0.55D, offset[1]);
        return !PlayerUtility.isBoxEmpty(checkBox);
    }

    private double getHorizontalDistance(final LivingEntity target) {
        final double distance = Math.hypot(mc.player.getX() - target.getX(), mc.player.getZ() - target.getZ());
        return Math.max(0.0D, distance - mc.player.getWidth() * 0.5D - target.getWidth() * 0.5D);
    }

    private static float getInputYaw(final float referenceYaw, final int forward, final int sideways) {
        return (float) Math.toDegrees(MoveUtility.getDirection(referenceYaw, forward, sideways));
    }

    private void switchDirection() {
        if (System.currentTimeMillis() - this.lastCollisionSwitchTime < 250L) {
            return;
        }

        this.strafeDirectionSign *= -1;
        this.lastCollisionSwitchTime = System.currentTimeMillis();
    }

    @Override
    protected void onEnable() {
        this.strafeDirectionSign = 1;
        this.strafeTarget = null;
        this.lastSwitchTime = 0L;
        this.lastCollisionSwitchTime = 0L;
        this.lastInput = InputChoice.NONE;
        super.onEnable();
    }

    @Override
    protected void onDisable() {
        this.strafeTarget = null;
        this.lastInput = InputChoice.NONE;
        super.onDisable();
    }

    private static final class InputChoice {
        private static final InputChoice NONE = new InputChoice(0, 0);

        private final int forward;
        private final int sideways;

        private InputChoice(final int forward, final int sideways) {
            this.forward = forward;
            this.sideways = sideways;
        }

        @Override
        public boolean equals(final Object object) {
            if (this == object) {
                return true;
            }
            if (!(object instanceof InputChoice choice)) {
                return false;
            }
            return this.forward == choice.forward && this.sideways == choice.sideways;
        }

        @Override
        public int hashCode() {
            return 31 * this.forward + this.sideways;
        }
    }
}

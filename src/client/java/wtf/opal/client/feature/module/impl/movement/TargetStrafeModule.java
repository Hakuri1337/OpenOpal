package wtf.opal.client.feature.module.impl.movement;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import wtf.opal.client.OpalClient;
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
import wtf.opal.utility.player.PlayerUtility;
import wtf.opal.utility.player.RotationUtility;

import static wtf.opal.client.Constants.mc;

public final class TargetStrafeModule extends Module {

    private final BooleanProperty smartStrafe = new BooleanProperty("Jump Key Only", true);
    private final NumberProperty range = new NumberProperty("Range", 0.5D, 0.1D, 2.0D, 0.1D);
    private final NumberProperty switchDelay = new NumberProperty("Switch Delay", "ms", 1000.0D, 100.0D, 5000.0D, 100.0D);

    private int strafeDirectionSign = 1;
    private LivingEntity strafeTarget;
    private long lastSwitchTime;
    private long lastCollisionSwitchTime;

    public TargetStrafeModule() {
        super("TargetStrafe", "Circles the current KillAura target.", ModuleCategory.MOVEMENT);
        this.addProperties(this.smartStrafe, this.range, this.switchDelay);
    }

    @Subscribe
    public void onPreGameTick(final PreGameTickEvent event) {
        if (mc.player == null || mc.world == null) {
            this.strafeTarget = null;
            return;
        }

        this.updateTarget();

        final Box box = mc.player.getBoundingBox();
        final boolean aboveVoid = PlayerUtility.isBoxEmpty(box.offset(0.0D, -1.0D, 0.0D))
                && PlayerUtility.isBoxEmpty(box.offset(0.0D, -2.0D, 0.0D))
                && PlayerUtility.isBoxEmpty(box.offset(0.0D, -3.0D, 0.0D));
        if ((aboveVoid || mc.player.horizontalCollision) && System.currentTimeMillis() - this.lastCollisionSwitchTime >= 500L) {
            this.strafeDirectionSign *= -1;
            this.lastCollisionSwitchTime = System.currentTimeMillis();
        }
    }

    @Subscribe
    public void onMoveInput(final MoveInputEvent event) {
        if (mc.player == null || this.strafeTarget == null || !this.strafeTarget.isAlive()) {
            return;
        }

        if (this.smartStrafe.getValue() && !mc.options.jumpKey.isPressed()) {
            return;
        }

        final double distance = mc.player.distanceTo(this.strafeTarget);
        final float targetYaw = RotationUtility.getRotationFromPosition(this.strafeTarget.getEyePos()).x;
        final float orbitYaw = targetYaw + 90.0F * this.strafeDirectionSign;
        final float currentYaw = RotationHelper.getClientHandler().getYawOr(mc.player.getYaw());
        final float delta = MathHelper.wrapDegrees(orbitYaw - currentYaw);

        event.setSideways(delta > 0.0F ? 1.0F : -1.0F);
        event.setForward(distance > this.range.getValue() + 0.15D ? 1.0F : 0.0F);
        event.setSneak(false);
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

        if (this.strafeTarget == null || System.currentTimeMillis() - this.lastSwitchTime >= this.switchDelay.getValue().longValue()) {
            this.strafeTarget = currentTarget.getEntity();
            this.lastSwitchTime = System.currentTimeMillis();
        }
    }

    @Override
    protected void onEnable() {
        this.strafeDirectionSign = 1;
        this.strafeTarget = null;
        this.lastSwitchTime = 0L;
        this.lastCollisionSwitchTime = 0L;
        super.onEnable();
    }

    @Override
    protected void onDisable() {
        this.strafeTarget = null;
        super.onDisable();
    }
}

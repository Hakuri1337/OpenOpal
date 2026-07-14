package wtf.opal.client.feature.module.impl.combat.criticals.impl;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import wtf.opal.client.OpalClient;
import wtf.opal.client.feature.module.impl.combat.criticals.CriticalsModule;
import wtf.opal.client.feature.module.impl.combat.killaura.KillAuraModule;
import wtf.opal.client.feature.module.property.impl.bool.BooleanProperty;
import wtf.opal.client.feature.module.property.impl.mode.ModuleMode;
import wtf.opal.client.feature.module.property.impl.number.NumberProperty;
import wtf.opal.event.impl.game.input.MoveInputEvent;
import wtf.opal.event.impl.game.player.interaction.AttackEvent;
import wtf.opal.event.subscriber.Subscribe;
import wtf.opal.utility.player.PlayerUtility;
import wtf.opal.utility.player.SkipTickUtility;

import static wtf.opal.client.Constants.mc;

public final class HeypixelCriticals extends ModuleMode<CriticalsModule> {

    private final NumberProperty range = new NumberProperty("Critical Range", this, 3.0D, 1.0D, 3.2D, 0.1D);
    private final BooleanProperty autoJump = new BooleanProperty("Auto Jump", this, true);
    private final BooleanProperty skipTicks = new BooleanProperty("SkipTicks", this, true);

    private boolean armedSkipTick;

    public HeypixelCriticals(final CriticalsModule module) {
        super(module);
    }

    @Subscribe
    public void onMoveInput(final MoveInputEvent event) {
        if (!autoJump.getValue() || mc.player == null || mc.world == null) {
            return;
        }

        if (hasHeadBlock() || mc.options.jumpKey.isPressed()) {
            return;
        }

        final KillAuraModule aura = OpalClient.getInstance().getModuleRepository().getModule(KillAuraModule.class);
        if (!aura.isEnabled()) {
            return;
        }

        final var target = aura.getTargeting().getTarget();
        if (target == null) {
            return;
        }

        if (mc.player.isOnGround() && mc.player.distanceTo(target.getEntity()) <= 3.0F) {
            event.setJump(true);
        }
    }

    @Subscribe
    public void onAttack(final AttackEvent event) {
        if (mc.player == null || mc.world == null) {
            return;
        }

        if (!(event.getTarget() instanceof LivingEntity living)) {
            return;
        }

        if (cantCrit(living)) {
            resetState();
            return;
        }

        final KillAuraModule aura = OpalClient.getInstance().getModuleRepository().getModule(KillAuraModule.class);
        if (!aura.isEnabled()) {
            resetState();
            return;
        }

        if (mc.player.getVelocity().y < 0.0D && !mc.player.isOnGround() && mc.player.distanceTo(living) <= range.getValue()) {
            if (skipTicks.getValue()) {
                if (!armedSkipTick) {
                    SkipTickUtility.addSkipTicks(1);
                    armedSkipTick = true;
                }
            }
            cancelSprint();
        } else {
            resetState();
        }
    }

    @Override
    public void onDisable() {
        super.onDisable();
        resetState();
        SkipTickUtility.reset();
    }

    private void resetState() {
        this.armedSkipTick = false;
    }

    private boolean cancelSprint() {
        if (mc.player == null) {
            return false;
        }

        if (mc.player.isSprinting() || mc.options.sprintKey.isPressed()) {
            mc.options.sprintKey.setPressed(false);
            mc.player.setSprinting(false);
            return true;
        }
        return false;
    }

    private boolean cantCrit(final Entity target) {
        if (!(target instanceof LivingEntity living)) {
            return true;
        }

        return !PlayerUtility.isCriticalHitAvailable()
                || mc.player.isClimbing()
                || mc.player.isTouchingWater()
                || mc.player.isInLava()
                || mc.player.hasVehicle()
                || hasHeadBlock()
                || living.hurtTime > 10
                || living.getHealth() <= 0.0F;
    }

    private boolean hasHeadBlock() {
        if (mc.player == null || mc.world == null) {
            return false;
        }

        final Box box = mc.player.getBoundingBox().offset(0.0D, 0.5D, 0.0D);
        return mc.world.getBlockCollisions(mc.player, box).iterator().hasNext();
    }

    @Override
    public Enum<?> getEnumValue() {
        return CriticalsModule.Mode.HEYPIXEL;
    }
}

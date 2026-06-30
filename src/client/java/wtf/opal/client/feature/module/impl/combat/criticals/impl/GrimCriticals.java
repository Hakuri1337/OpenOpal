package wtf.opal.client.feature.module.impl.combat.criticals.impl;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import wtf.opal.client.feature.module.impl.combat.criticals.CriticalsModule;
import wtf.opal.client.feature.module.property.impl.mode.ModuleMode;
import wtf.opal.event.impl.game.world.EntityRemoveEvent;
import wtf.opal.event.subscriber.Subscribe;

import static wtf.opal.client.Constants.mc;

public final class GrimCriticals extends ModuleMode<CriticalsModule> {

    public GrimCriticals(final CriticalsModule module) {
        super(module);
    }

    @Subscribe
    public void onEntityRemove(final EntityRemoveEvent event) {
        if (mc.player == null || !(event.getEntity() instanceof LivingEntity target)) {
            return;
        }

        if (!this.canCrit()) {
            return;
        }

        final boolean wasSprinting = mc.player.isSprinting();
        if (!event.isDead()) {
            mc.player.resetLastAttackedTicks();
            this.module.debugDamage("Grim", target, true, "reset attack ticks, sprint=" + wasSprinting);
        } else if (wasSprinting) {
            mc.options.sprintKey.setPressed(false);
            this.module.debugDamage("Grim", target, true, "target dead, released sprint key");
        }
    }

    private boolean canCrit() {
        return mc.player.fallDistance > 0.0F
                && !mc.player.isOnGround()
                && !mc.player.isClimbing()
                && !mc.player.isTouchingWater()
                && !mc.player.hasStatusEffect(StatusEffects.BLINDNESS)
                && !mc.player.hasVehicle();
    }

    @Override
    public Enum<?> getEnumValue() {
        return CriticalsModule.Mode.GRIM;
    }
}

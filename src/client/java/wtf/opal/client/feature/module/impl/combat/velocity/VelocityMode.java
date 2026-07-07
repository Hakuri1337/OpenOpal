package wtf.opal.client.feature.module.impl.combat.velocity;

import wtf.opal.client.feature.module.property.impl.mode.ModuleMode;

public abstract class VelocityMode extends ModuleMode<VelocityModule> {
    protected VelocityMode(VelocityModule module) {
        super(module);
    }

    public String getSuffix() {
        return this.getEnumValue().toString();
    }

    public boolean shouldStopBacktrack() {
        return false;
    }

    public boolean shouldPauseKillAuraClicks() {
        return false;
    }

    public boolean isDelaying() {
        return false;
    }

    public boolean hasQueuedPackets() {
        return false;
    }

    public boolean isAttacking() {
        return false;
    }

    public int getHitSelectSkips() {
        return 0;
    }

    public boolean consumeHitSelectSkip() {
        return false;
    }

    public void onVelocityResetAttackPerformed() {
    }
}

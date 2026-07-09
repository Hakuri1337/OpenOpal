package wtf.opal.client.feature.module.impl.combat.velocity;

import wtf.opal.client.OpalClient;
import wtf.opal.client.feature.module.Module;
import wtf.opal.client.feature.module.ModuleCategory;
import wtf.opal.client.feature.module.impl.combat.velocity.impl.BufferJumpResetVelocity;
import wtf.opal.client.feature.module.impl.combat.velocity.impl.CubeCraftVelocity;
import wtf.opal.client.feature.module.impl.combat.velocity.impl.Heypixel3Velocity;
import wtf.opal.client.feature.module.impl.combat.velocity.impl.JumpResetVelocity;
import wtf.opal.client.feature.module.impl.combat.velocity.impl.NormalVelocity;
import wtf.opal.client.feature.module.impl.movement.flight.FlightModule;
import wtf.opal.client.feature.module.impl.movement.longjump.LongJumpModule;
import wtf.opal.client.feature.module.property.impl.mode.ModeProperty;
import wtf.opal.client.feature.module.repository.ModuleRepository;

import static wtf.opal.client.Constants.mc;

public final class VelocityModule extends Module {
    private final ModeProperty<Mode> mode = new ModeProperty<>("Mode", this, Mode.NORMAL);

    public VelocityModule() {
        super("AntiKB", "Reduces or nullifies your players velocity when being hit.", ModuleCategory.COMBAT);
        this.addProperties(this.mode);
        addModuleModes(mode,
                new NormalVelocity(this),
                new Heypixel3Velocity(this),
                new BufferJumpResetVelocity(this),
                new CubeCraftVelocity(this),
                new JumpResetVelocity(this)
        );
    }

    @Override
    public String getSuffix() {
        return this.getActiveMode() instanceof VelocityMode velocityMode
                ? velocityMode.getSuffix()
                : this.mode.getValue().toString();
    }

    public boolean isInvalid() {
        if (mc.player == null) {
            return true;
        }

        final ModuleRepository moduleRepository = OpalClient.getInstance().getModuleRepository();
        if (moduleRepository.getModule(LongJumpModule.class).isEnabled()
                || moduleRepository.getModule(FlightModule.class).isEnabled()) {
            return true;
        }

        return false;
    }

    public boolean isPaused() {
        return false;
    }

    public boolean shouldStopBacktrack() {
        return this.isEnabled()
                && !this.isInvalid()
                && this.getActiveMode() instanceof VelocityMode velocityMode
                && velocityMode.shouldStopBacktrack();
    }

    public boolean shouldPauseKillAuraClicks() {
        return this.isEnabled()
                && !this.isInvalid()
                && this.getActiveMode() instanceof VelocityMode velocityMode
                && velocityMode.shouldPauseKillAuraClicks();
    }

    public enum Mode {
        NORMAL("Normal"),
        BUFFER("Buffer"),
        BUFFER_JUMP_RESET("BufferJumpReset"),
        CUBECRAFT("CubeCraft"),
        ATTACK_REDUCE("AttackReduce (Disabled)"),
        JUMP_RESET("JumpReset");

        private final String name;

        Mode(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }
}

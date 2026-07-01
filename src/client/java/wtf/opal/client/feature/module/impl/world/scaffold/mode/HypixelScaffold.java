package wtf.opal.client.feature.module.impl.world.scaffold.mode;

import wtf.opal.client.feature.helper.impl.player.rotation.RotationHelper;
import wtf.opal.client.feature.helper.impl.player.rotation.model.EnumRotationModel;
import wtf.opal.client.feature.module.impl.world.scaffold.ScaffoldModule;
import wtf.opal.client.feature.module.impl.world.scaffold.ScaffoldSettings;
import wtf.opal.utility.player.MoveUtility;

import static wtf.opal.client.Constants.mc;

public final class HypixelScaffold extends HeypixelScaffold {

    public HypixelScaffold(final ScaffoldModule module) {
        super(module);
    }

    @Override
    public Enum<?> getEnumValue() {
        return ScaffoldSettings.Mode.HYPIXEL;
    }

    @Override
    public boolean isHandlingEvents() {
        return module.isEnabled() && module.getEffectiveMode() == ScaffoldSettings.Mode.HYPIXEL;
    }

    @Override
    protected float resolveBaseYaw() {
        if (!module.getSettings().isRotationModel(EnumRotationModel.SIDEWAYS)) {
            return super.resolveBaseYaw();
        }

        return resolveSideYaw();
    }

    @Override
    protected boolean shouldTrackMovementYawDuringTelly() {
        return module.getSettings().isRotationModel(EnumRotationModel.SIDEWAYS);
    }

    @Override
    protected boolean isTellyEnabled() {
        return true;
    }

    @Override
    protected int getTellyTick() {
        return 5;
    }

    @Override
    protected float getRotateSpeed() {
        return 180.0F;
    }

    @Override
    protected float getRotateBackSpeed() {
        return 180.0F;
    }

    private float resolveSideYaw() {
        return MoveUtility.getDirectionDegrees(RotationHelper.getClientHandler().getYawOr(mc.player.getYaw()));
    }
}

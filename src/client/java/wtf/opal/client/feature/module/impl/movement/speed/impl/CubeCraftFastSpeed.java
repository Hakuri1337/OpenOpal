package wtf.opal.client.feature.module.impl.movement.speed.impl;

import net.minecraft.util.math.Vec2f;
import wtf.opal.client.feature.module.impl.movement.speed.SpeedModule;
import wtf.opal.client.feature.module.property.impl.bool.BooleanProperty;
import wtf.opal.client.feature.module.property.impl.mode.ModuleMode;
import wtf.opal.client.feature.module.property.impl.number.NumberProperty;
import wtf.opal.event.impl.game.JoinWorldEvent;
import wtf.opal.event.impl.game.player.movement.PreMovementPacketEvent;
import wtf.opal.event.impl.game.player.teleport.PreTeleportEvent;
import wtf.opal.event.subscriber.Subscribe;
import wtf.opal.utility.misc.chat.ChatUtility;
import wtf.opal.utility.player.MoveUtility;

import static wtf.opal.client.Constants.mc;

public final class CubeCraftFastSpeed extends ModuleMode<SpeedModule> {
    private final NumberProperty speed = new NumberProperty("Speed", this, 1.0D, 1.0D, 5.0D, 0.1D)
            .hideIf(() -> this.module.getActiveMode() != this);
    private final BooleanProperty lagBackCheck = new BooleanProperty("LagBack Check", this, true)
            .hideIf(() -> this.module.getActiveMode() != this);
    private boolean ignoreInitialTeleport;

    public CubeCraftFastSpeed(SpeedModule module) {
        super(module);
    }

    @Subscribe
    public void onPreMovementPacket(PreMovementPacketEvent event) {
        if (mc.player == null || !mc.player.isOnGround()) {
            return;
        }

        final Vec2f input = mc.player.input.getMovementInput();
        if (input.x == 0.0F && input.y == 0.0F) {
            return;
        }

        final double direction = MoveUtility.getDirection(mc.player.getYaw(), input.y, input.x);
        MoveUtility.setSpeed(mc.player, this.speed.getValue() / 4.0D, direction);
    }

    @Subscribe
    public void onPreTeleport(PreTeleportEvent event) {
        if (this.ignoreInitialTeleport) {
            this.ignoreInitialTeleport = false;
            return;
        }

        if (!this.lagBackCheck.getValue()) {
            return;
        }

        ChatUtility.print("CubeCraftFast Speed | lag detected, disabling Speed.");
        this.module.setEnabled(false);
    }

    @Subscribe
    public void onJoinWorld(JoinWorldEvent event) {
        this.ignoreInitialTeleport = true;
    }

    @Override
    public void onEnable() {
        this.ignoreInitialTeleport = mc.player == null || mc.world == null;
        super.onEnable();
    }

    @Override
    public Enum<?> getEnumValue() {
        return SpeedModule.Mode.CUBECRAFT_FAST;
    }
}

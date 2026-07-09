package wtf.opal.client.feature.module.impl.utility.disabler.impl;

import net.minecraft.network.packet.s2c.common.CommonPingS2CPacket;
import net.minecraft.network.packet.s2c.common.KeepAliveS2CPacket;
import wtf.opal.client.OpalClient;
import wtf.opal.client.feature.helper.impl.player.packet.blockage.block.holder.BlockHolder;
import wtf.opal.client.feature.helper.impl.player.packet.blockage.impl.InboundNetworkBlockage;
import wtf.opal.client.feature.module.Module;
import wtf.opal.client.feature.module.impl.movement.FastWebModule;
import wtf.opal.client.feature.module.impl.movement.PhaseModule;
import wtf.opal.client.feature.module.impl.movement.SpiderModule;
import wtf.opal.client.feature.module.impl.movement.clipper.ClipperModule;
import wtf.opal.client.feature.module.impl.movement.flight.FlightModule;
import wtf.opal.client.feature.module.impl.movement.longjump.LongJumpModule;
import wtf.opal.client.feature.module.impl.movement.speed.SpeedModule;
import wtf.opal.client.feature.module.impl.utility.disabler.DisablerModule;
import wtf.opal.client.feature.module.property.impl.mode.ModuleMode;
import wtf.opal.client.feature.module.repository.ModuleRepository;
import wtf.opal.event.impl.game.JoinWorldEvent;
import wtf.opal.event.impl.game.packet.InstantaneousReceivePacketEvent;
import wtf.opal.event.impl.game.player.movement.PreMovementPacketEvent;
import wtf.opal.event.impl.game.player.teleport.PreTeleportEvent;
import wtf.opal.event.subscriber.Subscribe;
import wtf.opal.mixin.CommonPingS2CPacketAccessor;
import wtf.opal.utility.misc.time.Stopwatch;

public final class CubecraftDisabler extends ModuleMode<DisablerModule> {
    public CubecraftDisabler(final DisablerModule module) {
        super(module);
    }

    private final BlockHolder blockHolder = new BlockHolder(InboundNetworkBlockage.get());
    private final Stopwatch flagStopwatch = new Stopwatch();

    private boolean cancel;

    @Subscribe
    public void onPreMovementPacket(final PreMovementPacketEvent event) {
        if (!this.shouldOperate()) {
            return;
        }

        if (this.flagStopwatch.hasTimeElapsed(200L)) {
            this.blockHolder.block(p -> p, p -> p instanceof CommonPingS2CPacket);
        } else {
            this.blockHolder.release();
        }

        if (this.flagStopwatch.hasTimeElapsed(3000L)) {
            event.setY(event.getY() + 11);
        }
    }

    @Subscribe
    public void onInstantaneousReceivePacket(final InstantaneousReceivePacketEvent event) {
        if (!this.shouldOperate()) {
            return;
        }

        if (event.getPacket() instanceof KeepAliveS2CPacket) {
            event.setCancelled();
        } else if (event.getPacket() instanceof CommonPingS2CPacket ping) {
            final CommonPingS2CPacketAccessor accessor = (CommonPingS2CPacketAccessor) ping;
        }
    }

    @Subscribe
    public void onPreTeleport(final PreTeleportEvent event) {
        this.blockHolder.release();
        this.flagStopwatch.reset();
        this.cancel = true;
    }

    @Subscribe
    public void onJoinWorld(final JoinWorldEvent event) {
        this.blockHolder.release();
        this.cancel = true;
    }

    @Override
    public void onDisable() {
        this.blockHolder.release();
        super.onDisable();
    }

    @Override
    public Enum<?> getEnumValue() {
        return DisablerModule.Mode.CUBECRAFT;
    }

    private boolean shouldOperate() {
        if (this.isHighRiskMovementEnabled()) {
            return true;
        }

        this.blockHolder.release();
        this.flagStopwatch.reset();
        this.cancel = false;
        return false;
    }

    private boolean isHighRiskMovementEnabled() {
        final ModuleRepository repository = OpalClient.getInstance().getModuleRepository();
        return isEnabled(repository, FlightModule.class)
                || isEnabled(repository, SpeedModule.class)
                || isEnabled(repository, LongJumpModule.class)
                || isEnabled(repository, PhaseModule.class)
                || isEnabled(repository, ClipperModule.class)
                || isEnabled(repository, SpiderModule.class)
                || isEnabled(repository, FastWebModule.class);
    }

    private static <T extends Module> boolean isEnabled(final ModuleRepository repository, final Class<T> moduleClass) {
        final T module = repository.getModule(moduleClass);
        return module != null && module.isEnabled();
    }
}

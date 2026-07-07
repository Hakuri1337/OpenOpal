package wtf.opal.client.feature.module.impl.utility;

import net.minecraft.network.packet.s2c.common.CommonPingS2CPacket;
import wtf.opal.client.feature.helper.impl.player.packet.blockage.block.holder.BlockHolder;
import wtf.opal.client.feature.helper.impl.player.packet.blockage.impl.InboundNetworkBlockage;
import wtf.opal.client.feature.helper.impl.player.packet.blockage.impl.OutboundNetworkBlockage;
import wtf.opal.client.feature.module.Module;
import wtf.opal.client.feature.module.ModuleCategory;
import wtf.opal.client.feature.module.property.impl.bool.BooleanProperty;
import wtf.opal.client.feature.module.property.impl.bool.MultipleBooleanProperty;
import wtf.opal.client.feature.module.property.impl.mode.ModeProperty;
import wtf.opal.client.feature.module.property.impl.number.BoundedNumberProperty;
import wtf.opal.client.feature.module.property.impl.number.NumberProperty;
import wtf.opal.event.impl.game.PreGameTickEvent;
import wtf.opal.event.subscriber.Subscribe;
import wtf.opal.utility.misc.math.RandomUtility;
import wtf.opal.utility.misc.time.Stopwatch;

public final class BlinkModule extends Module {

    private enum WhileMoreMode {
        RELEASE_PREVIOUS("Release previous packets"),
        RELEASE_ALL("Release all");

        private final String name;

        WhileMoreMode(final String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    private final MultipleBooleanProperty blinkDirections = new MultipleBooleanProperty("Direction",
            new BooleanProperty("Inbound", true),
            new BooleanProperty("Outbound", true));

    private final NumberProperty maxBlinkTicks = new NumberProperty("Max Blink Ticks", 20, 1, 200, 1);
    private final ModeProperty<WhileMoreMode> whileMore = new ModeProperty<>("While More", WhileMoreMode.RELEASE_PREVIOUS);
    private final BooleanProperty pulse = new BooleanProperty("Pulse", false);
    private final BoundedNumberProperty pulseDelay = new BoundedNumberProperty("Pulse delay", "ms", 1000, 2000, 50, 10000, 1)
            .hideIf(() -> !pulse.getValue());

    private final Stopwatch oPulseTimer = new Stopwatch();
    private final Stopwatch iPulseTimer = new Stopwatch();
    private int blinkTicks;

    public BlinkModule() {
        super("Blink", "Blocks your network connection.", ModuleCategory.UTILITY);
        addProperties(blinkDirections, maxBlinkTicks, whileMore, pulse, pulseDelay);
    }

    private final BlockHolder iBlockHolder = new BlockHolder(InboundNetworkBlockage.get());
    private final BlockHolder oBlockHolder = new BlockHolder(OutboundNetworkBlockage.get());

    @Subscribe
    public void onPreGameTick(final PreGameTickEvent event) {
        this.blinkTicks++;

        if (blinkDirections.getProperty("Inbound").getValue()) {
            this.iBlockHolder.block(p -> p, p -> !(p instanceof CommonPingS2CPacket));
            this.iBlockHolder.tickBlockedPackets();

            if (pulse.getValue() && iPulseTimer.hasTimeElapsed(RandomUtility.getRandomInt((int) pulseDelay.getMinValue(), (int) pulseDelay.getMaxValue()), true)) {
                this.iBlockHolder.release();
                this.blinkTicks = 0;
            }
        } else {
            this.iBlockHolder.release();
        }

        if (blinkDirections.getProperty("Outbound").getValue()) {
            this.oBlockHolder.block();
            this.oBlockHolder.tickBlockedPackets();

            if (pulse.getValue() && oPulseTimer.hasTimeElapsed(RandomUtility.getRandomInt((int) pulseDelay.getMinValue(), (int) pulseDelay.getMaxValue()), true)) {
                this.oBlockHolder.release();
                this.blinkTicks = 0;
            }
        } else {
            this.oBlockHolder.release();
        }

        if (this.blinkTicks > this.maxBlinkTicks.getValue().intValue()) {
            if (this.whileMore.getValue() == WhileMoreMode.RELEASE_ALL) {
                this.setEnabled(false);
                return;
            }

            this.iBlockHolder.releasePacketsOlderThan(this.maxBlinkTicks.getValue().intValue());
            this.oBlockHolder.releasePacketsOlderThan(this.maxBlinkTicks.getValue().intValue());
        }
    }

    @Override
    protected void onEnable() {
        this.blinkTicks = 0;
        super.onEnable();
    }

    @Override
    protected void onDisable() {
        this.iBlockHolder.release();
        this.oBlockHolder.release();
        this.blinkTicks = 0;
    }
}

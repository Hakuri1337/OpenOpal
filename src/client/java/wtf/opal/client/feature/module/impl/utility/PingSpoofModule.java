package wtf.opal.client.feature.module.impl.utility;

import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.common.CommonPingS2CPacket;
import net.minecraft.network.packet.s2c.common.KeepAliveS2CPacket;
import wtf.opal.client.feature.module.Module;
import wtf.opal.client.feature.module.ModuleCategory;
import wtf.opal.client.feature.module.property.impl.number.NumberProperty;
import wtf.opal.event.impl.game.PreGameTickEvent;
import wtf.opal.event.impl.game.packet.ReceivePacketEvent;
import wtf.opal.event.subscriber.Subscribe;
import wtf.opal.utility.misc.chat.ChatUtility;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import static wtf.opal.client.Constants.mc;

public final class PingSpoofModule extends Module {

    private record QueuedPacket(Packet<?> packet, long timestamp) {
    }

    private final NumberProperty delay = new NumberProperty("Delay", "ms", 500.0D, 0.0D, 25000.0D, 50.0D);
    private final Queue<QueuedPacket> packets = new ConcurrentLinkedQueue<>();
    private boolean flushing;

    public PingSpoofModule() {
        super("PingSpoof", "Delays ping and keep-alive responses.", ModuleCategory.UTILITY);
        this.addProperties(this.delay);
    }

    @Subscribe
    public void onReceivePacket(final ReceivePacketEvent event) {
        if (this.flushing || mc.player == null || mc.world == null) {
            return;
        }

        final Packet<?> packet = event.getPacket();
        if (packet instanceof KeepAliveS2CPacket || packet instanceof CommonPingS2CPacket) {
            event.setCancelled();
            this.packets.add(new QueuedPacket(packet, System.currentTimeMillis()));
        }
    }

    @Subscribe
    public void onPreGameTick(final PreGameTickEvent event) {
        if (mc.isInSingleplayer()) {
            this.packets.clear();
            this.setEnabled(false);
            return;
        }

        if (mc.getNetworkHandler() == null) {
            this.packets.clear();
            return;
        }

        final long now = System.currentTimeMillis();
        final long delayMs = this.delay.getValue().longValue();
        while (!this.packets.isEmpty()) {
            final QueuedPacket queuedPacket = this.packets.peek();
            if (queuedPacket == null || now - queuedPacket.timestamp() < delayMs) {
                return;
            }
            this.applyPacket(this.packets.poll().packet());
        }
    }

    private void applyPacket(final Packet<?> packet) {
        final ClientPlayNetworkHandler networkHandler = mc.getNetworkHandler();
        if (networkHandler == null || packet == null) {
            return;
        }

        this.flushing = true;
        try {
            //noinspection rawtypes,unchecked
            ((Packet) packet).apply(networkHandler);
        } catch (Exception ignored) {
            this.packets.clear();
        } finally {
            this.flushing = false;
        }
    }

    @Override
    protected void onEnable() {
        if (mc.isInSingleplayer()) {
            ChatUtility.print("PingSpoof cannot be enabled in singleplayer.");
            this.setEnabled(false);
            return;
        }
        super.onEnable();
    }

    @Override
    protected void onDisable() {
        while (!this.packets.isEmpty()) {
            this.applyPacket(this.packets.poll().packet());
        }
        super.onDisable();
    }
}

package wtf.opal.client.feature.module.impl.utility.disabler.impl;

import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.common.CommonPongC2SPacket;
import net.minecraft.network.packet.c2s.common.KeepAliveC2SPacket;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import wtf.opal.client.feature.helper.impl.player.packet.blockage.impl.OutboundNetworkBlockage;
import wtf.opal.client.feature.module.impl.utility.disabler.DisablerModule;
import wtf.opal.client.feature.module.property.impl.mode.ModuleMode;
import wtf.opal.event.impl.game.JoinWorldEvent;
import wtf.opal.event.impl.game.PreGameTickEvent;
import wtf.opal.event.impl.game.packet.SendPacketEvent;
import wtf.opal.event.impl.game.player.teleport.PreTeleportEvent;
import wtf.opal.event.impl.game.server.ServerDisconnectEvent;
import wtf.opal.event.subscriber.Subscribe;
import wtf.opal.utility.misc.chat.ChatUtility;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import static wtf.opal.client.Constants.mc;

public final class CubecraftDisabler extends ModuleMode<DisablerModule> {
    private static final long PACKET_DELAY_MS = 2_000L;
    private static final long WAIT_AFTER_LAG_MS = 5_000L;

    private record PacketEntry(Packet<?> packet, long queuedAt) {
    }

    private final Queue<PacketEntry> packetQueue = new ConcurrentLinkedQueue<>();
    private long waitStartedAt;
    private long disabledStartedAt;
    private boolean lagDetected;
    private boolean waitingLastTick;
    private boolean ignoreInitialTeleport;

    public CubecraftDisabler(DisablerModule module) {
        super(module);
    }

    @Subscribe
    public void onSendPacket(SendPacketEvent event) {
        if (mc.player == null || this.isWaiting()) {
            return;
        }

        final Packet<?> packet = event.getPacket();
        if (packet instanceof ClientCommandC2SPacket command
                && command.getMode() == ClientCommandC2SPacket.Mode.START_SPRINTING) {
            event.setCancelled();
            return;
        }

        if (packet instanceof KeepAliveC2SPacket || packet instanceof CommonPongC2SPacket) {
            this.packetQueue.add(new PacketEntry(packet, System.currentTimeMillis()));
            event.setCancelled();
        }
    }

    @Subscribe
    public void onPreTeleport(PreTeleportEvent event) {
        if (this.ignoreInitialTeleport) {
            this.ignoreInitialTeleport = false;
            return;
        }

        this.handleLagCorrection();
    }

    private void handleLagCorrection() {
        if (!this.isHandlingEvents()) {
            return;
        }

        this.waitStartedAt = System.currentTimeMillis();
        this.lagDetected = true;
        this.flushQueue();
        ChatUtility.print("CubeCraft Disabler | lag detected, waiting 5 seconds.");
    }

    @Subscribe
    public void onPreGameTick(PreGameTickEvent event) {
        final boolean waiting = this.isWaiting();
        if (this.waitingLastTick && !waiting) {
            this.disabledStartedAt = System.currentTimeMillis();
        }
        this.waitingLastTick = waiting;

        if (waiting) {
            return;
        }

        final long now = System.currentTimeMillis();
        PacketEntry entry;
        while ((entry = this.packetQueue.peek()) != null && now - entry.queuedAt() >= PACKET_DELAY_MS) {
            this.packetQueue.poll();
            sendDirect(entry.packet());
        }
    }

    @Subscribe
    public void onJoinWorld(JoinWorldEvent event) {
        this.clearState(true);
    }

    @Subscribe
    public void onServerDisconnect(ServerDisconnectEvent event) {
        this.clearState(true);
    }

    @Override
    public void onEnable() {
        this.clearState(mc.player == null || mc.world == null);
        super.onEnable();
    }

    @Override
    public void onDisable() {
        this.flushQueue();
        this.lagDetected = false;
        this.waitingLastTick = false;
        super.onDisable();
    }

    @Override
    public Enum<?> getEnumValue() {
        return DisablerModule.Mode.CUBECRAFT;
    }

    public boolean isWaiting() {
        return this.lagDetected && System.currentTimeMillis() - this.waitStartedAt < WAIT_AFTER_LAG_MS;
    }

    public double getDisabledDuration() {
        if (this.isWaiting()) {
            return 0.0D;
        }
        return (System.currentTimeMillis() - this.disabledStartedAt) / 1_000.0D;
    }

    public int getQueuedPacketCount() {
        return this.packetQueue.size();
    }

    public String getStatusSuffix() {
        return this.isWaiting() ? "Waiting" : "Sentinel";
    }

    private void flushQueue() {
        PacketEntry entry;
        while ((entry = this.packetQueue.poll()) != null) {
            sendDirect(entry.packet());
        }
    }

    private void clearState(boolean expectInitialTeleport) {
        this.packetQueue.clear();
        this.lagDetected = false;
        this.waitingLastTick = false;
        this.ignoreInitialTeleport = expectInitialTeleport;
        this.waitStartedAt = 0L;
        this.disabledStartedAt = System.currentTimeMillis();
    }

    private static void sendDirect(Packet<?> packet) {
        if (mc.getNetworkHandler() != null) {
            OutboundNetworkBlockage.sendPacketDirect(packet);
        }
    }
}

package wtf.opal.client.feature.helper.impl.player.packet.blockage.impl;

import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.Packet;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import wtf.opal.client.feature.helper.impl.player.packet.blockage.DirectionalNetworkBlockage;
import wtf.opal.duck.ClientConnectionAccess;

import static wtf.opal.client.Constants.mc;

public final class OutboundNetworkBlockage extends DirectionalNetworkBlockage<ServerPlayNetworkHandler> {
    public static OutboundNetworkBlockage get() {
        return instance;
    }

    private static final OutboundNetworkBlockage instance;

    static {
        instance = new OutboundNetworkBlockage();
    }

    /**
     * Sends a packet that completely bypasses blockages
     */
    public static void sendPacketDirect(Packet<?> packet) {
        mc.getNetworkHandler().getConnection().send(packet, null, true);
    }

    @Override
    protected void flushPacket(ClientConnection connection, Packet<?> packet) {
        ((ClientConnectionAccess) connection).opal$sendPacketSilent(packet);
    }
}

package wtf.opal.client.feature.helper.impl.player.packet.blockage.block.holder;

import wtf.opal.client.feature.helper.impl.player.packet.blockage.DirectionalNetworkBlockage;
import wtf.opal.client.feature.helper.impl.player.packet.blockage.block.NetworkBlock;
import wtf.opal.client.feature.helper.impl.player.packet.blockage.block.PacketTransformer;
import wtf.opal.client.feature.helper.impl.player.packet.blockage.block.PacketValidator;

public final class BlockHolder {
    private final DirectionalNetworkBlockage<?> networkBlockage;
    private final boolean priority;

    public BlockHolder(DirectionalNetworkBlockage<?> networkBlockage, boolean priority) {
        this.networkBlockage = networkBlockage;
        this.priority = priority;
    }

    public BlockHolder(DirectionalNetworkBlockage<?> networkBlockage) {
        this(networkBlockage, false);
    }

    private NetworkBlock networkBlock;

    /**
     * Blocks the connection in the utilized direction
     */
    public void block(PacketTransformer packetTransformer, PacketValidator packetValidator) {
        if (this.networkBlock == null) {
            this.networkBlock = this.networkBlockage.newBlockage(packetTransformer, packetValidator, this.priority);
        }
    }

    public void block(PacketTransformer packetTransformer) {
        this.block(packetTransformer, null);
    }

    public void block() {
        this.block(null);
    }

    public void setPacketTransformer(PacketTransformer packetTransformer) {
        if (this.networkBlock != null) {
            this.networkBlock.setPacketTransformer(packetTransformer);
        }
    }

    /**
     * Entirely releases the block, it must be blocked again for packets to be queued
     */
    public void release() {
        if (this.networkBlock != null) {
            this.networkBlockage.releaseBlockage(this.networkBlock);
            this.networkBlock = null;
        }
    }

    /**
     * Flushes queued packets and maintains the network block
     */
    public void flush() {
        this.release();
        this.block();
    }

    public void tickBlockedPackets() {
        if (this.networkBlock != null) {
            this.networkBlockage.tickBlockedPackets(this.networkBlock);
        }
    }

    public void releasePacketsOlderThan(int maxAgeTicks) {
        if (this.networkBlock != null) {
            this.networkBlockage.releasePacketsOlderThan(this.networkBlock, maxAgeTicks, this.networkBlock.getPacketTransformer());
        }
    }

    public boolean isBlocking() {
        return this.networkBlock != null;
    }
}

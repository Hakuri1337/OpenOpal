package wtf.opal.client.feature.helper.impl.player.packet.blockage;

import net.minecraft.network.packet.Packet;

public final class BlockedPacket {
    private final Packet<?> packet;
    private final long id;
    private final long blockageId;
    private int ageTicks;

    public BlockedPacket(Packet<?> packet, long id) {
        this(packet, id, Long.MIN_VALUE);
    }

    public BlockedPacket(Packet<?> packet, long id, long blockageId) {
        this.packet = packet;
        this.id = id;
        this.blockageId = blockageId;
    }

    public Packet<?> getPacket() {
        return packet;
    }

    public long getId() {
        return id;
    }

    public long getBlockageId() {
        return blockageId;
    }

    public int getAgeTicks() {
        return ageTicks;
    }

    public void tickAge() {
        this.ageTicks++;
    }
}

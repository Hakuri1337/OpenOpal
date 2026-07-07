package wtf.opal.client.feature.helper.impl.player.packet.blockage;

import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.listener.PacketListener;
import net.minecraft.network.packet.Packet;
import org.jetbrains.annotations.Nullable;
import wtf.opal.client.feature.helper.impl.player.packet.blockage.block.NetworkBlock;
import wtf.opal.client.feature.helper.impl.player.packet.blockage.block.PacketTransformer;
import wtf.opal.client.feature.helper.impl.player.packet.blockage.block.PacketValidator;

import java.util.*;

import static wtf.opal.client.Constants.mc;

public abstract class DirectionalNetworkBlockage<T extends PacketListener> {

    private final List<NetworkBlock> blockageList = new ArrayList<>();
    private final List<BlockedPacket> packetList = new ArrayList<>();
    private long id;

    public NetworkBlock newBlockage() {
        return newBlockage(null, null);
    }

    protected final Object lock = new Object();

    public NetworkBlock newBlockage(PacketTransformer packetTransformer, PacketValidator packetValidator) {
        return newBlockage(packetTransformer, packetValidator, false);
    }

    public NetworkBlock newBlockage(PacketTransformer packetTransformer, PacketValidator packetValidator, boolean priority) {
        synchronized (this.lock) {
            NetworkBlock blockage = new NetworkBlock(packetTransformer, packetValidator, priority, this.getBlockageId());
            this.blockageList.add(blockage);
            return blockage;
        }
    }

    private long getBlockageId() {
        long id = this.id;
        for (NetworkBlock block : this.blockageList) {
            if (block.isPriority() && id >= block.getId()) {
                id = block.getId();
            }
        }
        return id;
    }

    public void releaseBlockage(NetworkBlock networkBlock) {
        synchronized (this.lock) {
            if (this.blockageList.contains(networkBlock)) {
                this.blockageList.remove(networkBlock);
                this.sort();
                this.flush(this.blockageList.isEmpty() ? null : this.blockageList.getFirst().getId(), networkBlock.getPacketTransformer());
            }
        }
    }

    private void flush(@Nullable Long id, @Nullable PacketTransformer packetTransformer) {
        ClientPlayNetworkHandler networkHandler = mc.getNetworkHandler();
        ClientConnection connection;
        if (networkHandler == null) {
            connection = null;
        } else {
            connection = networkHandler.getConnection();
        }
        List<Packet<?>> packetsToFlush = new ArrayList<>();
        for (Iterator<BlockedPacket> iterator = this.packetList.iterator(); iterator.hasNext(); ) {
            BlockedPacket blockedPacket = iterator.next();
            if (id == null || blockedPacket.getId() < id) {
                if (connection != null) {
                    Packet<?> packet = blockedPacket.getPacket();
                    if (packetTransformer != null) {
                        packet = packetTransformer.transform(packet);
                    }
                    if (packet != null) {
                        packetsToFlush.add(packet);
                    }
                }
                iterator.remove();
            }
        }
        for (Packet<?> packet : packetsToFlush) {
            this.flushPacket(connection, packet);
        }
    }

    public void tickBlockedPackets(NetworkBlock networkBlock) {
        synchronized (this.lock) {
            for (BlockedPacket blockedPacket : this.packetList) {
                if (blockedPacket.getBlockageId() == networkBlock.getId()) {
                    blockedPacket.tickAge();
                }
            }
        }
    }

    public void releasePacketsOlderThan(NetworkBlock networkBlock, int maxAgeTicks, @Nullable PacketTransformer packetTransformer) {
        ClientPlayNetworkHandler networkHandler = mc.getNetworkHandler();
        ClientConnection connection = networkHandler == null ? null : networkHandler.getConnection();
        List<Packet<?>> packetsToFlush = new ArrayList<>();
        synchronized (this.lock) {
            for (Iterator<BlockedPacket> iterator = this.packetList.iterator(); iterator.hasNext(); ) {
                BlockedPacket blockedPacket = iterator.next();
                if (blockedPacket.getBlockageId() != networkBlock.getId() || blockedPacket.getAgeTicks() < maxAgeTicks) {
                    continue;
                }

                if (connection != null) {
                    Packet<?> packet = blockedPacket.getPacket();
                    if (packetTransformer != null) {
                        packet = packetTransformer.transform(packet);
                    }
                    if (packet != null) {
                        packetsToFlush.add(packet);
                    }
                }
                iterator.remove();
            }
        }
        for (Packet<?> packet : packetsToFlush) {
            this.flushPacket(connection, packet);
        }
    }

    protected abstract void flushPacket(ClientConnection connection, Packet<?> packet);

    public boolean isBlocked(Packet<?> packet) {
        synchronized (this.lock) {
            if (!this.blockageList.isEmpty()) {
                this.sort();
                NetworkBlock validBlock = null;
                for (final NetworkBlock block : this.blockageList) {
                    final PacketValidator blockValidator = block.getPacketValidator();
                    if (blockValidator == null || blockValidator.isValid(packet)) {
                        validBlock = block;
                        break;
                    }
                }
                if (validBlock != null) {
                    this.packetList.add(new BlockedPacket(packet, this.id, validBlock.getId()));
                    this.id++;
                    return true;
                }
            }
            return false;
        }
    }

    private void sort() {
        synchronized (this.lock) {
            this.blockageList.sort(Comparator.comparingLong(NetworkBlock::getId));
            this.packetList.sort(Comparator.comparingLong(BlockedPacket::getId));
        }
    }

    public void reset() {
        synchronized (this.lock) {
            this.blockageList.clear();
            this.packetList.clear();
            this.id = 0;
        }
    }

    public boolean isAnyBlockages() {
        synchronized (this.lock) {
            return !this.blockageList.isEmpty();
        }
    }
}

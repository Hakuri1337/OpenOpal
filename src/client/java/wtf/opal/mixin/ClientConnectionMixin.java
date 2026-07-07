package wtf.opal.mixin;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.OffThreadException;
import net.minecraft.network.listener.PacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BundleS2CPacket;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import wtf.opal.client.feature.helper.impl.player.packet.blockage.impl.InboundNetworkBlockage;
import wtf.opal.client.feature.helper.impl.player.packet.blockage.impl.OutboundNetworkBlockage;
import wtf.opal.duck.ClientConnectionAccess;
import wtf.opal.event.EventDispatcher;
import wtf.opal.event.impl.game.packet.InstantaneousReceivePacketEvent;
import wtf.opal.event.impl.game.packet.InstantaneousSendPacketEvent;
import wtf.opal.event.impl.game.packet.ReceivePacketEvent;
import wtf.opal.event.impl.game.packet.SendPacketEvent;

import java.util.concurrent.RejectedExecutionException;

@Mixin(ClientConnection.class)
public abstract class ClientConnectionMixin implements ClientConnectionAccess {

    @Shadow
    private static <T extends PacketListener> void handlePacket(Packet<T> packet, PacketListener listener) {
    }

    @Shadow private volatile @Nullable PacketListener packetListener;

    @Shadow public abstract void disconnect(Text disconnectReason);

    @Shadow @Final private static Logger LOGGER;

    @Shadow private int packetsReceivedCounter;

    @Shadow private Channel channel;

    @Shadow protected abstract void channelRead0(ChannelHandlerContext channelHandlerContext, Packet<?> packet);

    @Shadow public abstract NetworkSide getSide();

    private ClientConnectionMixin() {
    }

    @Inject(
            method = "<init>",
            at = @At("TAIL")
    )
    private void init(NetworkSide side, CallbackInfo ci) {
        InboundNetworkBlockage.get().reset();
        OutboundNetworkBlockage.get().reset();
    }

    @Inject(
            method = "send(Lnet/minecraft/network/packet/Packet;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void hookSendPacket(Packet<?> packet, CallbackInfo ci) {
        InstantaneousSendPacketEvent event = new InstantaneousSendPacketEvent(packet);
        EventDispatcher.dispatch(event);
        if (event.isCancelled() || OutboundNetworkBlockage.get().isBlocked(packet)) {
            ci.cancel();
        }
    }

    @Inject(
            method = "send(Lnet/minecraft/network/packet/Packet;Lio/netty/channel/ChannelFutureListener;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void hookSendPacket(Packet<?> packet, @Nullable ChannelFutureListener channelFutureListener, CallbackInfo ci) {
        final SendPacketEvent event = new SendPacketEvent(packet);
        EventDispatcher.dispatch(event);
        if (event.isCancelled() || OutboundNetworkBlockage.get().isBlocked(packet)) {
            ci.cancel();
        }
    }

    @Inject(method = "handlePacket", at = @At("HEAD"), cancellable = true, require = 1)
    private static void hookReceivePacket(Packet<?> packet, PacketListener listener, CallbackInfo ci) {
        if (packet instanceof BundleS2CPacket bundleS2CPacket) {
            ci.cancel();

            for (Packet<?> packetInBundle : bundleS2CPacket.getPackets()) {
                try {
                    handlePacket(packetInBundle, listener);
                } catch (OffThreadException ignored) {}
            }
            return;
        }

        final ReceivePacketEvent event = new ReceivePacketEvent(packet);
        EventDispatcher.dispatch(event);

        if (event.isCancelled()) {
            ci.cancel();
        }
    }

    @Inject(
            method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/packet/Packet;)V",
            at = @At(value = "INVOKE", target = "Lio/netty/channel/Channel;isOpen()Z"),
            cancellable = true
    )
    private void hookChannelRead(ChannelHandlerContext channelHandlerContext, Packet<?> packet, CallbackInfo ci) {
        if (this.getSide() == NetworkSide.CLIENTBOUND) {
            if (packet instanceof BundleS2CPacket bundleS2CPacket) {
                ci.cancel();

                for (Packet<?> packetInBundle : bundleS2CPacket.getPackets()) {
                    try {
                        channelRead0(channelHandlerContext, packetInBundle);
                    } catch (OffThreadException ignored) {}
                }
                return;
            }
            InstantaneousReceivePacketEvent event = new InstantaneousReceivePacketEvent(packet);
            EventDispatcher.dispatch(event);
            if (event.isCancelled() || InboundNetworkBlockage.get().isBlocked(packet)) {
                ci.cancel();
            }
        }
    }

    @Unique
    @Override
    public void opal$sendPacketSilent(Packet<?> packet) {
        if (this.channel != null && this.channel.isOpen()) {
            this.channel.writeAndFlush(packet);
        }
    }

    @Unique
    @Override
    public void opal$channelReadSilent(Packet<?> packet) {
        if (this.channel.isOpen()) {
            PacketListener packetListener = this.packetListener;
            if (packetListener == null) {
                throw new IllegalStateException("Received a packet before the packet listener was initialized");
            } else {
                if (packetListener.accepts(packet)) {
                    try {
                        handlePacket(packet, packetListener);
                    } catch (OffThreadException var5) {
                    } catch (RejectedExecutionException var6) {
                        this.disconnect(Text.translatable("multiplayer.disconnect.server_shutdown"));
                    } catch (ClassCastException var7) {
                        LOGGER.error("Received {} that couldn't be processed", packet.getClass(), var7);
                        this.disconnect(Text.translatable("multiplayer.disconnect.invalid_packet"));
                    }

                    this.packetsReceivedCounter++;
                }
            }
        }
    }
}

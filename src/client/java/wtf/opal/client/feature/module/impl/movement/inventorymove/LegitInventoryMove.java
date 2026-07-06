package wtf.opal.client.feature.module.impl.movement.inventorymove;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.ButtonClickC2SPacket;
import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.network.packet.c2s.play.CreativeInventoryActionC2SPacket;
import net.minecraft.network.packet.c2s.play.SlotChangedStateC2SPacket;
import wtf.opal.client.feature.module.impl.movement.InventoryMoveModule;
import wtf.opal.client.feature.module.property.impl.mode.ModuleMode;
import wtf.opal.duck.ClientConnectionAccess;
import wtf.opal.event.impl.game.PreGameTickEvent;
import wtf.opal.event.impl.game.input.MoveInputEvent;
import wtf.opal.event.impl.game.packet.SendPacketEvent;
import wtf.opal.event.subscriber.Subscribe;
import wtf.opal.utility.player.MoveUtility;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import static wtf.opal.client.Constants.mc;

public final class LegitInventoryMove extends ModuleMode<InventoryMoveModule> {

    private final Queue<Packet<?>> delayedContainerPackets = new ConcurrentLinkedQueue<>();
    private int stopInputTicks;
    private boolean releasingPackets;

    public LegitInventoryMove(final InventoryMoveModule module) {
        super(module);
    }

    @Override
    public Enum<?> getEnumValue() {
        return InventoryMoveModule.Mode.LEGIT;
    }

    @Override
    public void onDisable() {
        this.releaseDelayedPackets();
        this.stopInputTicks = 0;
        super.onDisable();
    }

    @Subscribe
    public void onMoveInput(final MoveInputEvent event) {
        if (!module.canProcessScreenInput()) {
            return;
        }

        if (this.stopInputTicks > 0) {
            module.stopMovementInput(event);
            return;
        }

        module.applyMovementInput(event);
    }

    @Subscribe
    public void onPreGameTick(final PreGameTickEvent event) {
        if (this.stopInputTicks > 0) {
            this.stopInputTicks--;
        }

        if (this.stopInputTicks == 0) {
            this.releaseDelayedPackets();
        }
    }

    @Subscribe
    public void onSendPacket(final SendPacketEvent event) {
        if (this.releasingPackets || mc.player == null || !(mc.currentScreen instanceof HandledScreen<?>)) {
            return;
        }

        final Packet<?> packet = event.getPacket();
        if (!this.isContainerPacket(packet) || !this.isMovingInInventory()) {
            return;
        }

        event.setCancelled();
        this.delayedContainerPackets.add(packet);
        this.stopInputTicks = Math.max(this.stopInputTicks, 1);
    }

    private boolean isMovingInInventory() {
        return MoveUtility.isMoving()
                || mc.options.forwardKey.isPressed()
                || mc.options.backKey.isPressed()
                || mc.options.leftKey.isPressed()
                || mc.options.rightKey.isPressed()
                || mc.options.jumpKey.isPressed();
    }

    private boolean isContainerPacket(final Packet<?> packet) {
        return packet instanceof ClickSlotC2SPacket
                || packet instanceof ButtonClickC2SPacket
                || packet instanceof CreativeInventoryActionC2SPacket
                || packet instanceof SlotChangedStateC2SPacket
                || packet instanceof CloseHandledScreenC2SPacket;
    }

    private void releaseDelayedPackets() {
        if (mc.getNetworkHandler() == null || !(mc.getNetworkHandler().getConnection() instanceof ClientConnectionAccess access)) {
            this.delayedContainerPackets.clear();
            return;
        }

        this.releasingPackets = true;
        try {
            Packet<?> packet;
            while ((packet = this.delayedContainerPackets.poll()) != null) {
                access.opal$sendPacketSilent(packet);
            }
        } finally {
            this.releasingPackets = false;
        }
    }
}

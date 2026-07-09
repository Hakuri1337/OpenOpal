package wtf.opal.client.feature.module.impl.movement.noslow.impl;

import net.minecraft.client.util.InputUtil;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.PotionItem;
import net.minecraft.item.consume.UseAction;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.common.CommonPongC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.s2c.play.EntityEquipmentUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.InventoryS2CPacket;
import net.minecraft.network.packet.s2c.play.ScreenHandlerSlotUpdateS2CPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import wtf.opal.client.feature.helper.impl.player.packet.blockage.impl.OutboundNetworkBlockage;
import wtf.opal.client.feature.module.impl.movement.noslow.NoSlowModule;
import wtf.opal.client.feature.module.property.impl.mode.ModuleMode;
import wtf.opal.event.impl.game.PreGameTickEvent;
import wtf.opal.event.impl.game.packet.ReceivePacketEvent;
import wtf.opal.event.impl.game.packet.SendPacketEvent;
import wtf.opal.event.impl.game.player.movement.SlowdownEvent;
import wtf.opal.event.subscriber.Subscribe;
import wtf.opal.mixin.KeyBindingAccessor;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.Queue;

import static wtf.opal.client.Constants.mc;

public final class GrimFastNoSlow extends ModuleMode<NoSlowModule> {

    private static final int SWAP_FALLBACK_TICKS = 2;
    private static final int WAIT_FALLBACK_TICKS = 4;
    private static final int IDLE_RESET_TICKS = 5;

    private final Queue<CommonPongC2SPacket> pongQueue = new ArrayDeque<>();

    private UseState useState = UseState.IDLE;
    private boolean didSwapOffhand;
    private int waitTicks;
    private int swapTicks;
    private int idleTicks;

    public GrimFastNoSlow(final NoSlowModule module) {
        super(module);
    }

    @Subscribe
    public void onSlowdown(final SlowdownEvent event) {
        if (mc.player == null || !mc.player.isUsingItem()) {
            return;
        }

        final ItemStack activeStack = mc.player.getActiveItem();
        if (!isFoodOrPotion(activeStack) || mc.player.getItemUseTimeLeft() <= 0) {
            this.resetOffhandState();
            return;
        }

        final Hand otherHand = mc.player.getActiveHand() == Hand.MAIN_HAND ? Hand.OFF_HAND : Hand.MAIN_HAND;
        if (isUseAnimation(getStackInHand(otherHand).getUseAction())) {
            this.resetOffhandState();
            return;
        }

        if (this.useState != UseState.USING) {
            mc.options.useKey.setPressed(false);
        }

        if (this.useState == UseState.IDLE) {
            this.useState = UseState.WAITING;
            this.waitTicks = 0;
            this.swapTicks = 0;
            return;
        }

        if (this.useState == UseState.USING) {
            event.setCancelled();
            if (this.module.isSprintingAllowed()) {
                mc.player.setSprinting(true);
            }
        }
    }

    @Subscribe
    public void onPreGameTick(final PreGameTickEvent event) {
        if (mc.player == null || mc.currentScreen != null || mc.getOverlay() != null) {
            this.resetOffhandState();
            return;
        }

        if (this.useState == UseState.WAITING && ++this.waitTicks >= WAIT_FALLBACK_TICKS) {
            this.startSwap();
        }

        if (this.useState == UseState.SWAPPING && ++this.swapTicks >= SWAP_FALLBACK_TICKS) {
            this.beginUsing();
        }

        if (this.useState == UseState.USING) {
            if (mc.player.isUsingItem()) {
                this.idleTicks = 0;
            } else if (++this.idleTicks >= IDLE_RESET_TICKS) {
                this.resetOffhandState();
            }
        } else {
            this.idleTicks = 0;
        }
    }

    @Subscribe
    public void onSendPacket(final SendPacketEvent event) {
        final Packet<?> packet = event.getPacket();
        if (packet instanceof CommonPongC2SPacket pongPacket) {
            if (this.useState != UseState.IDLE) {
                event.setCancelled();
                this.pongQueue.add(pongPacket);
                if (this.useState == UseState.WAITING) {
                    this.startSwap();
                }
            }
            return;
        }

        if (packet instanceof PlayerActionC2SPacket actionPacket) {
            final PlayerActionC2SPacket.Action action = actionPacket.getAction();
            if (action == PlayerActionC2SPacket.Action.RELEASE_USE_ITEM && this.useState == UseState.USING) {
                this.resetOffhandState();
            } else if (action == PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND && this.useState != UseState.IDLE) {
                this.resetOffhandState();
            }
        }
    }

    @Subscribe
    public void onReceivePacket(final ReceivePacketEvent event) {
        if (mc.player == null) {
            this.resetOffhandState();
            return;
        }

        final Packet<?> packet = event.getPacket();
        if (this.useState == UseState.SWAPPING && this.isEquipmentChangePacket(packet)) {
            this.beginUsing();
            return;
        }

        if (packet instanceof EntityVelocityUpdateS2CPacket velocityPacket
                && velocityPacket.getEntityId() == mc.player.getId()
                && this.useState == UseState.USING) {
            mc.options.useKey.setPressed(false);
        }
    }

    @Override
    public void onDisable() {
        this.resetOffhandState();
        super.onDisable();
    }

    @Override
    public Enum<?> getEnumValue() {
        return NoSlowModule.Mode.GRIM_FAST;
    }

    private void beginUsing() {
        mc.options.useKey.setPressed(true);
        this.useState = UseState.USING;
        this.waitTicks = 0;
        this.swapTicks = 0;
        this.idleTicks = 0;
    }

    private void startSwap() {
        this.useState = UseState.SWAPPING;
        this.didSwapOffhand = true;
        this.waitTicks = 0;
        this.swapTicks = 0;
        this.sendSwapOffhand();
    }

    private void resetOffhandState() {
        if (this.useState == UseState.IDLE && this.pongQueue.isEmpty() && !this.didSwapOffhand) {
            this.clearOffhandState();
            return;
        }

        while (!this.pongQueue.isEmpty()) {
            OutboundNetworkBlockage.sendPacketDirect(this.pongQueue.poll());
        }

        if (this.didSwapOffhand) {
            this.sendSwapOffhand();
        }

        this.clearOffhandState();
        this.restoreUseKeyState();
    }

    private void clearOffhandState() {
        this.pongQueue.clear();
        this.useState = UseState.IDLE;
        this.didSwapOffhand = false;
        this.waitTicks = 0;
        this.swapTicks = 0;
        this.idleTicks = 0;
    }

    private void sendSwapOffhand() {
        OutboundNetworkBlockage.sendPacketDirect(new PlayerActionC2SPacket(
                PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND,
                BlockPos.ORIGIN,
                Direction.DOWN
        ));
    }

    private boolean isEquipmentChangePacket(final Packet<?> packet) {
        return packet instanceof ScreenHandlerSlotUpdateS2CPacket
                || packet instanceof InventoryS2CPacket
                || packet instanceof EntityEquipmentUpdateS2CPacket;
    }

    private ItemStack getStackInHand(final Hand hand) {
        return hand == Hand.MAIN_HAND ? mc.player.getMainHandStack() : mc.player.getOffHandStack();
    }

    private boolean isFoodOrPotion(final ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }

        final UseAction action = stack.getUseAction();
        return (stack.contains(DataComponentTypes.FOOD) && action == UseAction.EAT)
                || action == UseAction.DRINK
                || stack.getItem() instanceof PotionItem;
    }

    private boolean isUseAnimation(final UseAction action) {
        return action == UseAction.EAT
                || action == UseAction.DRINK
                || action == UseAction.BOW
                || action == UseAction.SPEAR
                || action == UseAction.CROSSBOW;
    }

    private void restoreUseKeyState() {
        if (mc.getWindow() == null) {
            return;
        }

        final InputUtil.Key key = ((KeyBindingAccessor) mc.options.useKey).getBoundKey();
        final boolean pressed = key.getCategory() == InputUtil.Type.MOUSE
                ? GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), key.getCode()) == GLFW.GLFW_PRESS
                : InputUtil.isKeyPressed(mc.getWindow(), key.getCode());
        mc.options.useKey.setPressed(pressed);
    }

    private enum UseState {
        IDLE,
        WAITING,
        SWAPPING,
        USING
    }

}

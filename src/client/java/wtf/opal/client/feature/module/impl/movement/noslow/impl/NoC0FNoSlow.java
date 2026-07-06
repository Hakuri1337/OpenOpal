package wtf.opal.client.feature.module.impl.movement.noslow.impl;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.PotionItem;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.common.CommonPongC2SPacket;
import net.minecraft.network.packet.c2s.play.*;
import net.minecraft.network.packet.s2c.play.InventoryS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.network.packet.s2c.play.ScreenHandlerSlotUpdateS2CPacket;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import wtf.opal.client.OpalClient;
import wtf.opal.client.feature.module.impl.combat.velocity.VelocityMode;
import wtf.opal.client.feature.module.impl.combat.velocity.VelocityModule;
import wtf.opal.client.feature.module.impl.movement.noslow.NoSlowModule;
import wtf.opal.client.feature.module.impl.utility.inventory.manager.InventoryManagerModule;
import wtf.opal.client.feature.module.property.impl.mode.ModuleMode;
import wtf.opal.client.notification.NotificationType;
import wtf.opal.duck.ClientConnectionAccess;
import wtf.opal.utility.player.PlayerUtility;
import wtf.opal.event.impl.game.PreGameTickEvent;
import wtf.opal.event.impl.game.input.MoveInputEvent;
import wtf.opal.event.impl.game.packet.ReceivePacketEvent;
import wtf.opal.event.impl.game.packet.SendPacketEvent;
import wtf.opal.event.impl.game.player.movement.SlowdownEvent;
import wtf.opal.event.subscriber.Subscribe;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import static wtf.opal.client.Constants.mc;

public final class NoC0FNoSlow extends ModuleMode<NoSlowModule> {

    private enum Step {
        NONE,
        CANCEL_PONG,
        SWAP_HANDS,
        USING
    }

    private Step step = Step.NONE;
    private int noUsingItemTicks = 0;
    private int swapWaitTicks = 0;
    private final Queue<CommonPongC2SPacket> queuedPongs = new ConcurrentLinkedQueue<>();
    private boolean swapSent = false;
    private boolean forcedUseKeyDown = false;
    private Hand targetHand = Hand.MAIN_HAND;
    private boolean lockUseUntilRelease = false;
    private boolean wasUsingSlowingItem = false;
    private boolean wantForceLeftMainHand = false;
    private boolean startUseSet = false;
    private ItemStack startUseStack = ItemStack.EMPTY;
    private int startUseCount = 0;
    private boolean mainHandForced = false;
    private Arm mainHandOld = null;
    private Field optionsMainHandField = null;
    private Method optionsMainHandGet = null;
    private Method optionsMainHandSet = null;
    private static volatile Class<?> OPTION_INSTANCE_CLASS = null;
    private int prepareTicks = 0;

    public NoC0FNoSlow(final NoSlowModule module) {
        super(module);
    }

    private boolean isSlowingUse(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        return stack.contains(DataComponentTypes.FOOD) || stack.getItem() instanceof PotionItem;
    }

    private boolean isVelocityDelaying() {
        VelocityModule velocityModule = OpalClient.getInstance().getModuleRepository().getModule(VelocityModule.class);
        if (velocityModule != null && velocityModule.isEnabled()
                && velocityModule.getActiveMode() instanceof VelocityMode velocityMode) {
            return velocityMode.isDelaying();
        }
        return false;
    }

    private boolean isVelocityQueueing() {
        VelocityModule velocityModule = OpalClient.getInstance().getModuleRepository().getModule(VelocityModule.class);
        if (velocityModule != null && velocityModule.isEnabled()
                && velocityModule.getActiveMode() instanceof VelocityMode velocityMode) {
            return velocityMode.hasQueuedPackets();
        }
        return false;
    }

    private boolean isBlocked() {
        if (mc.player == null) return false;
        
        // Both hands holding slowing items
        if (isSlowingUse(mc.player.getMainHandStack()) && isSlowingUse(mc.player.getOffHandStack())) {
            return true;
        }

        // Velocity module check
        if (isVelocityDelaying() || isVelocityQueueing()) {
            return true;
        }

        // Inventory Manager check
        InventoryManagerModule invManager = OpalClient.getInstance().getModuleRepository().getModule(InventoryManagerModule.class);
        if (invManager != null && invManager.isEnabled()) {
            // Check if it's currently sorting/cleaning by seeing if it recently acted
            if (!invManager.canMove(50)) {
                return true;
            }
        }

        return false;
    }

    private boolean canStartNoC0F() {
        if (mc.player == null || mc.options == null) {
            return false;
        }
        if (isBlocked()) {
            return false;
        }
        if (mc.player.hurtTime > 0) {
            return false;
        }
        if (mc.currentScreen != null) {
            return false;
        }
        return mc.player.currentScreenHandler == mc.player.playerScreenHandler;
    }

    private boolean isSingleUseConsumable(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        net.minecraft.item.consume.UseAction action = stack.getUseAction();
        return action == net.minecraft.item.consume.UseAction.EAT || action == net.minecraft.item.consume.UseAction.DRINK;
    }

    private boolean hasConsumedOrChanged() {
        if (mc.player == null || !this.startUseSet) {
            return false;
        }
        ItemStack current = mc.player.getStackInHand(this.targetHand);
        if (current.isEmpty()) {
            return true;
        }
        if (!ItemStack.areItemsEqual(current, this.startUseStack)) {
            return true;
        }
        return current.getCount() < this.startUseCount;
    }

    private boolean isUsingSlowingItem() {
        if (mc.player == null) {
            return false;
        }
        if (!mc.player.isUsingItem()) {
            return false;
        }
        if (mc.player.getItemUseTimeLeft() <= 0) {
            return false;
        }
        return isSlowingUse(mc.player.getActiveItem());
    }

    private void ensureMainHandLeft() {
        if (this.mainHandForced || mc.options == null) {
            return;
        }
        try {
            if (this.optionsMainHandField == null) {
                resolveOptionsMainHandAccessor();
            }
            if (this.optionsMainHandField == null) {
                return;
            }

            if (this.optionsMainHandGet == null || this.optionsMainHandSet == null) {
                Object value = this.optionsMainHandField.get(mc.options);
                if (value instanceof Arm arm) {
                    this.mainHandOld = arm;
                    this.optionsMainHandField.set(mc.options, arm == Arm.LEFT ? Arm.RIGHT : Arm.LEFT);
                    this.mainHandForced = true;
                    saveOptions();
                }
                return;
            }

            Object optionObj = this.optionsMainHandField.get(mc.options);
            if (optionObj == null) {
                return;
            }
            Object current = this.optionsMainHandGet.invoke(optionObj);
            if (current instanceof Arm arm) {
                this.mainHandOld = arm;
                this.optionsMainHandSet.invoke(optionObj, arm == Arm.LEFT ? Arm.RIGHT : Arm.LEFT);
                this.mainHandForced = true;
                saveOptions();
            }
        } catch (Throwable ignored) {
        }
    }

    private void restoreMainHand() {
        if (!this.mainHandForced || mc.options == null || this.mainHandOld == null) {
            this.mainHandForced = false;
            this.mainHandOld = null;
            return;
        }
        try {
            if (this.optionsMainHandField == null) {
                return;
            }
            if (this.optionsMainHandGet == null || this.optionsMainHandSet == null) {
                this.optionsMainHandField.set(mc.options, this.mainHandOld);
                saveOptions();
                return;
            }
            Object optionObj = this.optionsMainHandField.get(mc.options);
            if (optionObj != null) {
                this.optionsMainHandSet.invoke(optionObj, this.mainHandOld);
                saveOptions();
            }
        } catch (Throwable ignored) {
        } finally {
            this.mainHandForced = false;
            this.mainHandOld = null;
        }
    }

    private void saveOptions() {
        if (mc.options == null) return;
        try {
            Method write = mc.options.getClass().getMethod("write");
            write.invoke(mc.options);
        } catch (Throwable e) {
            try {
                Method write = mc.options.getClass().getMethod("write", Path.class);
                File optionsFile = new File(mc.runDirectory, "options.txt");
                write.invoke(mc.options, optionsFile.toPath());
            } catch (Throwable ignored) {}
        }
    }

    private boolean isOptionInstanceType(Class<?> type) {
        if (type == null) {
            return false;
        }
        Class<?> cached = OPTION_INSTANCE_CLASS;
        if (cached == null) {
            try {
                cached = Class.forName("net.minecraft.client.option.SimpleOption");
                OPTION_INSTANCE_CLASS = cached;
            } catch (Throwable ignored) {
                OPTION_INSTANCE_CLASS = Object.class;
                cached = Object.class;
            }
        }
        if (cached != Object.class && cached.isAssignableFrom(type)) {
            return true;
        }
        String name = type.getName();
        return name.equals("net.minecraft.client.option.SimpleOption") || name.endsWith(".SimpleOption");
    }

    private void resolveOptionsMainHandAccessor() {
        if (mc.options == null) {
            return;
        }
        Object options = mc.options;
        Class<?> optionsClass = options.getClass();

        try {
            Field f = optionsClass.getDeclaredField("mainArm");
            f.setAccessible(true);
            if (f.getType() == Arm.class) {
                this.optionsMainHandField = f;
                this.optionsMainHandGet = null;
                this.optionsMainHandSet = null;
                return;
            }
            if (isOptionInstanceType(f.getType())) {
                Object optionObj = f.get(options);
                Method getter = findArmGetter(optionObj);
                Method setter = findArmSetter(optionObj, getter);
                if (getter != null && setter != null) {
                    this.optionsMainHandField = f;
                    this.optionsMainHandGet = getter;
                    this.optionsMainHandSet = setter;
                    return;
                }
            }
        } catch (Throwable ignored) {
        }

        for (Field f : optionsClass.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers())) {
                continue;
            }
            if (f.getType() != Arm.class) {
                continue;
            }
            try {
                f.setAccessible(true);
                this.optionsMainHandField = f;
                this.optionsMainHandGet = null;
                this.optionsMainHandSet = null;
                return;
            } catch (Throwable ignored) {
            }
        }

        for (Field f : optionsClass.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers())) {
                continue;
            }
            Object value;
            try {
                f.setAccessible(true);
                value = f.get(options);
            } catch (Throwable ignored) {
                continue;
            }
            if (value == null) {
                continue;
            }
            if (!isOptionInstanceType(value.getClass())) {
                continue;
            }
            Method getter = findArmGetter(value);
            Method setter = findArmSetter(value, getter);
            if (getter == null || setter == null) {
                continue;
            }
            this.optionsMainHandField = f;
            this.optionsMainHandGet = getter;
            this.optionsMainHandSet = setter;
            return;
        }
    }

    private Method findArmGetter(Object optionObj) {
        if (optionObj == null) {
            return null;
        }
        Class<?> cls = optionObj.getClass();
        try {
            for (Method m : cls.getMethods()) {
                if (m.getParameterCount() != 0) {
                    continue;
                }
                Object value;
                try {
                    value = m.invoke(optionObj);
                } catch (Throwable ignored) {
                    continue;
                }
                if (value instanceof Arm) {
                    m.setAccessible(true);
                    return m;
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private Method findArmSetter(Object optionObj, Method getter) {
        if (optionObj == null) {
            return null;
        }
        Arm safeValue = null;
        if (getter != null) {
            try {
                Object current = getter.invoke(optionObj);
                if (current instanceof Arm arm) {
                    safeValue = arm;
                }
            } catch (Throwable ignored) {
            }
        }
        return findArmSetter(optionObj, safeValue);
    }

    private Method findArmSetter(Object optionObj, Arm safeValue) {
        if (optionObj == null) {
            return null;
        }
        Method bestByName = null;
        Class<?> cls = optionObj.getClass();
        try {
            for (Method m : cls.getMethods()) {
                if (m.getParameterCount() != 1) {
                    continue;
                }
                if (m.getReturnType() != void.class) {
                    continue;
                }
                Class<?> param = m.getParameterTypes()[0];
                if (param != Arm.class && param != Object.class) {
                    continue;
                }
                String name = m.getName();
                if (bestByName == null && (name.equals("setValue") || name.equals("set") || name.toLowerCase().contains("set"))) {
                    bestByName = m;
                }
                if (safeValue == null) {
                    continue;
                }
                try {
                    m.invoke(optionObj, safeValue);
                } catch (Throwable ignored) {
                    continue;
                }
                m.setAccessible(true);
                return m;
            }
        } catch (Throwable ignored) {
        }
        if (bestByName != null) {
            bestByName.setAccessible(true);
            return bestByName;
        }
        return null;
    }

    @Subscribe
    public void onSlowdown(SlowdownEvent event) {
        if (isBlocked()) {
            return;
        }
        if (this.step != Step.USING) {
            return;
        }
        if (!isUsingSlowingItem()) {
            return;
        }
        event.setCancelled();
    }

    private void resetState() {
        restoreMainHand();
        this.step = Step.NONE;
        this.noUsingItemTicks = 0;
        this.swapWaitTicks = 0;
        this.queuedPongs.clear();
        this.swapSent = false;
        this.targetHand = Hand.MAIN_HAND;
        if (this.forcedUseKeyDown && mc.options != null) {
            mc.options.useKey.setPressed(PlayerUtility.isKeyPressed(mc.options.useKey));
        }
        this.forcedUseKeyDown = false;
        this.wasUsingSlowingItem = false;
        this.wantForceLeftMainHand = false;
        this.startUseSet = false;
        this.startUseStack = ItemStack.EMPTY;
        this.startUseCount = 0;
        this.prepareTicks = 0;
    }

    private void abort(boolean revertSwap) {
        CommonPongC2SPacket pong;
        while ((pong = queuedPongs.poll()) != null) {
            sendPacketSilent(pong);
        }

        if (revertSwap && this.swapSent) {
            sendPacketSilent(new PlayerActionC2SPacket(
                    PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND,
                    BlockPos.ORIGIN,
                    Direction.DOWN
            ));
        }

        resetState();
    }

    @Override
    public void onDisable() {
        this.lockUseUntilRelease = false;
        abort(true);
        super.onDisable();
    }

    @Subscribe
    public void onTick(PreGameTickEvent event) {
        if (mc.player == null) {
            return;
        }

        if (this.step != Step.NONE && (isVelocityDelaying() || isVelocityQueueing())) {
            this.lockUseUntilRelease = false;
            abort(false);
            return;
        }

        if (isBlocked()) {
            if (this.step != Step.NONE) {
                abort(false);
            }
            return;
        }

        if (this.step != Step.NONE && mc.player.hurtTime > 0) {
            this.lockUseUntilRelease = false;
            abort(false);
            return;
        }

        if (this.lockUseUntilRelease && mc.options != null) {
            if (PlayerUtility.isKeyPressed(mc.options.useKey)) {
                mc.options.useKey.setPressed(false);
                return;
            }
            this.lockUseUntilRelease = false;
        }

        boolean usingSlowingItem = isUsingSlowingItem();
        if (usingSlowingItem) {
            if (this.step == Step.NONE) {
                if (!canStartNoC0F()) {
                    return;
                }
                Hand usedHand = mc.player.getActiveHand();
                this.targetHand = usedHand == Hand.MAIN_HAND ? Hand.OFF_HAND : Hand.MAIN_HAND;
                this.step = Step.CANCEL_PONG;
                this.swapWaitTicks = 0;
                this.prepareTicks = 0;
                this.wantForceLeftMainHand = isSingleUseConsumable(mc.player.getActiveItem());
            }
            if (mc.options != null && this.step != Step.USING) {
                mc.options.useKey.setPressed(false);
                this.forcedUseKeyDown = true;
            }
        }

        if (this.step == Step.CANCEL_PONG) {
            this.prepareTicks++;
            if (this.prepareTicks >= 6) {
                abort(false);
                return;
            }
        }

        if (this.step != Step.NONE && this.swapSent && this.wantForceLeftMainHand) {
            ensureMainHandLeft();
        } else {
            restoreMainHand();
        }

        if (this.step == Step.SWAP_HANDS) {
            this.swapWaitTicks++;
            if (this.swapWaitTicks >= 20) {
                abort(false);
            }
            return;
        }

        if (this.step != Step.USING) {
            this.noUsingItemTicks = 0;
            this.wasUsingSlowingItem = false;
            return;
        }

        if (this.wasUsingSlowingItem && !usingSlowingItem) {
            if (hasConsumedOrChanged()) {
                abort(true);
                this.lockUseUntilRelease = true;
                return;
            }
        }

        if (usingSlowingItem) {
            this.noUsingItemTicks = 0;
            this.wasUsingSlowingItem = true;
            return;
        }

        this.noUsingItemTicks++;
        if (this.noUsingItemTicks >= 10) {
            abort(true);
        }
        this.wasUsingSlowingItem = false;
    }

    @Subscribe
    public void onMoveInput(MoveInputEvent event) {
        if (mc.player == null || mc.options == null) {
            return;
        }

        if (isBlocked()) {
            return;
        }

        if (this.step != Step.USING) {
            return;
        }
        if (!isUsingSlowingItem()) {
            return;
        }

        if (!event.isSneak()) {
            mc.player.setSprinting(true);
        }
    }

    @Subscribe
    public void onSendPacket(SendPacketEvent event) {
        if (mc.player == null) {
            return;
        }

        if (isBlocked()) {
            if (this.step != Step.NONE) {
                abort(false);
            }
            return;
        }

        Packet<?> packet = event.getPacket();

        if (packet instanceof PlayerActionC2SPacket actionPacket
                && actionPacket.getAction() == PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND
                && this.step != Step.NONE) {
            abort(false);
            return;
        }

        if (packet instanceof PlayerInteractItemC2SPacket interactPacket
                && this.step == Step.USING
                && this.swapSent
                && interactPacket.getHand() != this.targetHand) {
            event.setCancelled();
            Hand hand = this.targetHand;
            sendPacketSilent(new PlayerInteractItemC2SPacket(hand, interactPacket.getSequence(), mc.player.getYaw(), mc.player.getPitch()));
            return;
        }

        if (packet instanceof CommonPongC2SPacket pong && this.step != Step.NONE) {
            event.setCancelled();
            this.queuedPongs.add(pong);

            if (this.step == Step.CANCEL_PONG) {
                this.step = Step.SWAP_HANDS;
                this.swapWaitTicks = 0;
                this.swapSent = true;

                // Simple Swap logic from NoSlow.java
                sendPacketSilent(new PlayerActionC2SPacket(
                        PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND,
                        BlockPos.ORIGIN,
                        Direction.DOWN
                ));
            }
            return;
        }

        if (packet instanceof PlayerActionC2SPacket actionPacket
                && actionPacket.getAction() == PlayerActionC2SPacket.Action.RELEASE_USE_ITEM
                && this.step == Step.USING) {
            abort(this.swapSent);
            return;
        }
    }

    @Subscribe
    public void onReceivePacket(ReceivePacketEvent event) {
        if (mc.player == null) {
            return;
        }

        Packet<?> packet = event.getPacket();

        if (packet instanceof PlayerPositionLookS2CPacket) {
            OpalClient.getInstance().getNotificationManager()
                    .builder(NotificationType.WARN)
                    .title("NoSlow")
                    .description("Detected pullback (S08)")
                    .duration(2000)
                    .buildAndPublish();
        }

        if (this.step == Step.SWAP_HANDS) {
            if (packet instanceof ScreenHandlerSlotUpdateS2CPacket || packet instanceof InventoryS2CPacket) {
                if (mc.options != null) {
                    mc.options.useKey.setPressed(true);
                    this.forcedUseKeyDown = true;
                }
                this.step = Step.USING;
                this.noUsingItemTicks = 0;
                this.swapWaitTicks = 0;
                ItemStack current = mc.player.getStackInHand(this.targetHand);
                if (isSingleUseConsumable(current)) {
                    this.startUseStack = current.copy();
                    this.startUseCount = current.getCount();
                    this.startUseSet = true;
                } else {
                    this.startUseSet = false;
                    this.startUseStack = ItemStack.EMPTY;
                    this.startUseCount = 0;
                }
            }
        }
    }

    private void sendPacketSilent(Packet<?> packet) {
        if (mc.getNetworkHandler() != null && mc.getNetworkHandler().getConnection() instanceof ClientConnectionAccess access) {
            access.opal$sendPacketSilent(packet);
        }
    }

    @Override
    public Enum<?> getEnumValue() {
        return NoSlowModule.Mode.NOC0F;
    }
}

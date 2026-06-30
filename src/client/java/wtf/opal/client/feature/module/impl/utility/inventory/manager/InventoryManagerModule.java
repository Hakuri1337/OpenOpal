package wtf.opal.client.feature.module.impl.utility.inventory.manager;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.EquippableComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.*;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.network.packet.s2c.play.ScreenHandlerSlotUpdateS2CPacket;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.slot.Slot;
import wtf.opal.client.OpalClient;
import wtf.opal.client.feature.helper.impl.player.packet.blockage.impl.OutboundNetworkBlockage;
import wtf.opal.client.feature.module.Module;
import wtf.opal.client.feature.module.ModuleCategory;
import wtf.opal.client.feature.module.impl.combat.killaura.KillAuraModule;
import wtf.opal.client.feature.module.impl.movement.InventoryMoveModule;
import wtf.opal.client.feature.module.impl.utility.inventory.AutoArmorModule;
import wtf.opal.client.feature.module.impl.utility.inventory.ChestStealerModule;
import wtf.opal.client.feature.module.impl.world.scaffold.ScaffoldModule;
import wtf.opal.client.feature.module.repository.ModuleRepository;
import wtf.opal.event.impl.game.PreGameTickEvent;
import wtf.opal.event.impl.game.packet.ReceivePacketEvent;
import wtf.opal.event.impl.game.packet.SendPacketEvent;
import wtf.opal.event.impl.game.player.movement.PostMovementPacketEvent;
import wtf.opal.event.impl.game.player.movement.PreMovementPacketEvent;
import wtf.opal.event.impl.game.player.movement.SprintEvent;
import wtf.opal.event.subscriber.Subscribe;
import wtf.opal.utility.misc.chat.ChatUtility;
import wtf.opal.utility.misc.time.Stopwatch;
import wtf.opal.utility.player.InventoryUtility;
import wtf.opal.utility.player.MoveUtility;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import static wtf.opal.client.Constants.mc;

public final class InventoryManagerModule extends Module {

    private final InventoryManagerSettings settings = new InventoryManagerSettings(this);

    public final Stopwatch stopwatch = new Stopwatch();

    private boolean pendingOffhandPlace;
    private int inventoryOpenTicks;
    private int idleTicks;
    private int sprintReleaseTicks;
    private boolean warnedAboutDuplicateSlots;
    private boolean wasSprinting;
    private boolean justClosedInventory;
    private boolean performingAction;
    private boolean releasingPendingPackets;
    private boolean pendingSyntheticClose;
    private final Queue<Packet<?>> pendingPackets = new ConcurrentLinkedQueue<>();
    private final Stopwatch bufferedPacketStopwatch = new Stopwatch(0L);

    public InventoryManagerModule() {
        super("Inventory Manager", "Manages your inventory.", ModuleCategory.UTILITY);
    }

    @Override
    protected void onDisable() {
        this.pendingOffhandPlace = false;
        this.inventoryOpenTicks = 0;
        this.idleTicks = 0;
        this.sprintReleaseTicks = 0;
        this.warnedAboutDuplicateSlots = false;
        this.wasSprinting = false;
        this.justClosedInventory = false;
        this.performingAction = false;
        this.releasingPendingPackets = false;
        this.pendingSyntheticClose = false;
        this.pendingPackets.clear();
        this.bufferedPacketStopwatch.setTime(0L);
        super.onDisable();
    }

    @Subscribe
    public void onPreGameTickEvent(final PreGameTickEvent event) {
        runInventoryManager(false, settings.getActionDelay().longValue());
    }

    public void runLegacyAutoArmorOnly(final long actionDelay) {
        runInventoryManager(true, actionDelay);
    }

    @Subscribe
    public void onReceivePacket(final ReceivePacketEvent event) {
        if (event.getPacket() instanceof ScreenHandlerSlotUpdateS2CPacket slotUpdate
                && slotUpdate.getStack().getItem() != Items.AIR
                && mc.player != null
                && slotUpdate.getSyncId() == mc.player.playerScreenHandler.syncId) {
            stopwatch.reset();
        }
    }

    @Subscribe
    public void onSendPacket(final SendPacketEvent event) {
        if (mc.player == null || mc.getNetworkHandler() == null) {
            return;
        }

        if (event.getPacket() instanceof ClientCommandC2SPacket command) {
            if (command.getMode() == ClientCommandC2SPacket.Mode.START_SPRINTING) {
                wasSprinting = true;
            } else if (command.getMode() == ClientCommandC2SPacket.Mode.STOP_SPRINTING) {
                wasSprinting = false;
            }
        }

        if (!shouldCacheInventoryPackets() || releasingPendingPackets) {
            return;
        }

        if (event.getPacket() instanceof ClickSlotC2SPacket clickSlot
                && clickSlot.syncId() == mc.player.playerScreenHandler.syncId) {
            event.setCancelled();
            pendingPackets.add(clickSlot);
            return;
        }

        if (event.getPacket() instanceof CloseHandledScreenC2SPacket closeScreen
                && closeScreen.getSyncId() == mc.player.playerScreenHandler.syncId) {
            event.setCancelled();
            pendingSyntheticClose = false;
            pendingPackets.add(closeScreen);
        }
    }

    @Subscribe
    public void onSprint(final SprintEvent event) {
        if (!shouldSuppressSprintForBufferedInventory()) {
            return;
        }

        mc.player.setSprinting(false);
        event.setCanStartSprinting(false);
    }

    @Subscribe
    public void onPreMovementPacket(final PreMovementPacketEvent event) {
        if (shouldSuppressSprintForBufferedInventory()) {
            event.setSprinting(false);
        }
    }

    @Subscribe
    public void onPostMovementPacket(final PostMovementPacketEvent event) {
        if (mc.player == null || mc.getNetworkHandler() == null) {
            return;
        }

        if (pendingPackets.isEmpty() && !pendingSyntheticClose) {
            if (justClosedInventory) {
                justClosedInventory = false;
            }
            sprintReleaseTicks = 0;
            return;
        }

        if (mc.currentScreen instanceof HandledScreen<?> && !(mc.currentScreen instanceof InventoryScreen)) {
            return;
        }

        if (wasSprinting || mc.player.isSprinting() || event.isSprinting()) {
            sprintReleaseTicks = Math.max(sprintReleaseTicks, settings.getOpenDelayTicks() + 2);
            return;
        }

        if (sprintReleaseTicks > 0) {
            sprintReleaseTicks--;
            return;
        }

        releasePendingPackets();
    }

    public boolean canMove(final long delay) {
        return stopwatch.hasTimeElapsed(InventoryUtility.withAcaQuickMoveDelay(delay));
    }

    private boolean canPickupMove(final long delay) {
        return stopwatch.hasTimeElapsed(InventoryUtility.withAcaPickupDelay(delay));
    }

    private void runInventoryManager(final boolean autoArmorOnly, final long actionDelay) {
        if (mc.player == null || mc.world == null) {
            this.performingAction = false;
            return;
        }

        final ModuleRepository moduleRepository = OpalClient.getInstance().getModuleRepository();
        final PlayerScreenHandler playerHandler = getPlayerScreenHandler();
        if (playerHandler == null) {
            this.performingAction = false;
            return;
        }

        updateIdleState();

        if (!validateSlotConfig()) {
            resetStateForBlockedContext();
            this.performingAction = false;
            return;
        }

        if (!canManageInventory(moduleRepository)) {
            this.performingAction = false;
            return;
        }

        if (isAutoArmorEnabled(moduleRepository) && tryAutoArmorAction(playerHandler, actionDelay)) {
            this.performingAction = true;
            return;
        }

        if (autoArmorOnly) {
            this.performingAction = false;
            return;
        }

        if (pendingOffhandPlace && tryCompletePendingOffhandPlace(playerHandler, actionDelay)) {
            this.performingAction = true;
            return;
        }

        if (tryOffhandAction(playerHandler, actionDelay)) {
            this.performingAction = true;
            return;
        }

        if (tryHotbarAction(playerHandler, actionDelay)) {
            this.performingAction = true;
            return;
        }

        if (tryOverflowAction(playerHandler)) {
            this.performingAction = true;
            return;
        }

        this.performingAction = tryCleanupAction(playerHandler);
    }

    private boolean canManageInventory(final ModuleRepository moduleRepository) {
        if (InventoryUtility.hasServerItem()) {
            resetStateForBlockedContext();
            return false;
        }

        if (mc.currentScreen instanceof HandledScreen<?> && !(mc.currentScreen instanceof InventoryScreen)) {
            resetStateForBlockedContext();
            return false;
        }

        final KillAuraModule killAuraModule = moduleRepository.getModule(KillAuraModule.class);
        final ScaffoldModule scaffoldModule = moduleRepository.getModule(ScaffoldModule.class);
        if ((killAuraModule.isEnabled() && killAuraModule.getTargeting().isTargetSelected()) || scaffoldModule.isEnabled()) {
            resetStateForBlockedContext();
            return false;
        }

        final ChestStealerModule chestStealerModule = moduleRepository.getModule(ChestStealerModule.class);
        if (chestStealerModule.isConflictActive()) {
            resetStateForBlockedContext();
            return false;
        }

        final boolean inventoryScreenOpen = mc.currentScreen instanceof InventoryScreen;
        final InventoryMoveModule inventoryMoveModule = moduleRepository.getModule(InventoryMoveModule.class);

        if (inventoryScreenOpen) {
            inventoryOpenTicks++;
        } else {
            inventoryOpenTicks = 0;
        }

        if (settings.isInventoryOnlyEnabled()) {
            if (!inventoryScreenOpen) {
                resetStateForBlockedContext();
                return false;
            }
            if (inventoryOpenTicks < settings.getOpenDelayTicks()) {
                return false;
            }
        } else {
            if (!inventoryScreenOpen && !inventoryMoveModule.isEnabled() && idleTicks <= 1) {
                return false;
            }
            if (inventoryScreenOpen && inventoryOpenTicks < settings.getOpenDelayTicks()) {
                return false;
            }
        }

        if (!inventoryScreenOpen && inventoryMoveModule.isEnabled() && hasBufferedInventoryPackets()) {
            return false;
        }

        if (!inventoryScreenOpen && inventoryMoveModule.isEnabled() && justClosedInventory) {
            justClosedInventory = false;
            return false;
        }

        return true;
    }

    private void updateIdleState() {
        if (MoveUtility.isMoving()) {
            idleTicks = 0;
        } else {
            idleTicks++;
        }
    }

    private void resetStateForBlockedContext() {
        this.pendingOffhandPlace = false;
        this.inventoryOpenTicks = 0;
        this.performingAction = false;
    }

    private boolean shouldCacheInventoryPackets() {
        if (mc.player == null || mc.getNetworkHandler() == null || settings.isInventoryOnlyEnabled()) {
            return false;
        }

        if (mc.currentScreen instanceof InventoryScreen) {
            return false;
        }

        if (mc.currentScreen instanceof HandledScreen<?>) {
            return false;
        }

        return OpalClient.getInstance()
                .getModuleRepository()
                .getModule(InventoryMoveModule.class)
                .isEnabled();
    }

    private boolean shouldSuppressSprintForBufferedInventory() {
        return mc.player != null
                && (shouldCacheInventoryPackets() || hasBufferedInventoryPackets())
                && (hasBufferedInventoryPackets() || sprintReleaseTicks > 0 || justClosedInventory);
    }

    private boolean hasBufferedInventoryPackets() {
        return !pendingPackets.isEmpty() || pendingSyntheticClose;
    }

    public boolean isPerformingAction() {
        return this.isEnabled() && performingAction;
    }

    private void releasePendingPackets() {
        if (mc.player == null || mc.getNetworkHandler() == null || (!pendingSyntheticClose && pendingPackets.isEmpty())) {
            return;
        }

        final Packet<?> nextPacket = pendingPackets.peek();
        final long releaseDelay = nextPacket instanceof ClickSlotC2SPacket
                ? InventoryUtility.ACA_MULTIINTERACTION_PICKUP_DELAY_MS
                : InventoryUtility.ACA_INVENTORY_CLOSE_DELAY_MS;
        if (!bufferedPacketStopwatch.hasTimeElapsed(releaseDelay)) {
            return;
        }

        releasingPendingPackets = true;
        try {
            final Packet<?> packet = pendingPackets.poll();
            if (packet != null) {
                OutboundNetworkBlockage.sendPacketDirect(packet);
                if (packet instanceof ClickSlotC2SPacket && pendingPackets.isEmpty()) {
                    pendingSyntheticClose = true;
                } else if (packet instanceof CloseHandledScreenC2SPacket) {
                    pendingSyntheticClose = false;
                    justClosedInventory = true;
                    sprintReleaseTicks = 0;
                }
            } else if (pendingSyntheticClose) {
                OutboundNetworkBlockage.sendPacketDirect(new CloseHandledScreenC2SPacket(mc.player.playerScreenHandler.syncId));
                pendingSyntheticClose = false;
                justClosedInventory = true;
                sprintReleaseTicks = 0;
            }
            bufferedPacketStopwatch.reset();
        } finally {
            releasingPendingPackets = false;
        }
    }

    private boolean validateSlotConfig() {
        final List<Integer> configuredSlots = new ArrayList<>();

        addConfiguredSlot(configuredSlots, settings.getSwordSlot());
        addConfiguredSlot(configuredSlots, settings.getAxeSlot());
        addConfiguredSlot(configuredSlots, settings.getPickaxeSlot());
        addConfiguredSlot(configuredSlots, settings.getBowSlot());
        addConfiguredSlot(configuredSlots, settings.getWaterBucketSlot());
        addConfiguredSlot(configuredSlots, settings.getPearlSlot());
        addConfiguredSlot(configuredSlots, settings.getSlimeBallSlot());
        addConfiguredSlot(configuredSlots, settings.getCrystalSlot());

        if (settings.getOffhandMode() != InventoryManagerSettings.OffhandMode.GOLDEN_APPLE) {
            addConfiguredSlot(configuredSlots, settings.getGoldenAppleSlot());
        }

        if (settings.getOffhandMode() != InventoryManagerSettings.OffhandMode.PROJECTILE) {
            addConfiguredSlot(configuredSlots, settings.getEggsSnowballsSlot());
        }

        if (settings.getOffhandMode() != InventoryManagerSettings.OffhandMode.BLOCK) {
            addConfiguredSlot(configuredSlots, settings.getBlockSlot());
        }

        for (Integer configuredSlot : configuredSlots) {
            if (Collections.frequency(configuredSlots, configuredSlot) > 1) {
                if (!warnedAboutDuplicateSlots) {
                    ChatUtility.print("Inventory Manager has duplicate slot assignments.");
                    warnedAboutDuplicateSlots = true;
                }
                return false;
            }
        }

        warnedAboutDuplicateSlots = false;
        return true;
    }

    private void addConfiguredSlot(final List<Integer> configuredSlots, final int slot) {
        if (slot > 0) {
            configuredSlots.add(slot - 1);
        }
    }

    private boolean isAutoArmorEnabled(final ModuleRepository moduleRepository) {
        return settings.isAutoArmorEnabled() || moduleRepository.getModule(AutoArmorModule.class).isEnabled();
    }

    private PlayerScreenHandler getPlayerScreenHandler() {
        return mc.player.currentScreenHandler instanceof PlayerScreenHandler playerHandler ? playerHandler : null;
    }

    private boolean tryAutoArmorAction(final PlayerScreenHandler playerHandler, final long actionDelay) {
        for (EquipmentSlot equipmentSlot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            final Slot bestArmorSlot = InventoryUtility.getBestArmorSlot(playerHandler, equipmentSlot);
            if (bestArmorSlot == null) {
                continue;
            }

            final double bestScore = InventoryUtility.getArmorValue(bestArmorSlot.getStack());
            final double equippedScore = InventoryUtility.getArmorValue(mc.player.getEquippedStack(equipmentSlot));
            final int armorScreenSlot = InventoryUtility.getArmorScreenSlot(equipmentSlot);

            if (bestArmorSlot.id != armorScreenSlot && bestScore > equippedScore && !mc.player.getEquippedStack(equipmentSlot).isEmpty()) {
                if (!canMove(actionDelay)) {
                    return false;
                }

                InventoryUtility.drop(playerHandler, armorScreenSlot);
                stopwatch.reset();
                return true;
            }
        }

        for (EquipmentSlot equipmentSlot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            final Slot bestArmorSlot = InventoryUtility.getBestArmorSlot(playerHandler, equipmentSlot);
            if (bestArmorSlot == null) {
                continue;
            }

            final double bestScore = InventoryUtility.getArmorValue(bestArmorSlot.getStack());
            final double equippedScore = InventoryUtility.getArmorValue(mc.player.getEquippedStack(equipmentSlot));
            final int armorScreenSlot = InventoryUtility.getArmorScreenSlot(equipmentSlot);

            if (bestArmorSlot.id != armorScreenSlot && bestScore > equippedScore) {
                if (!canMove(actionDelay)) {
                    return false;
                }

                InventoryUtility.shiftClick(playerHandler, bestArmorSlot.id, 0);
                stopwatch.reset();
                return true;
            }
        }

        return false;
    }

    private boolean tryCompletePendingOffhandPlace(final PlayerScreenHandler playerHandler, final long actionDelay) {
        if (!canPickupMove(actionDelay)) {
            return false;
        }

        InventoryUtility.pickup(playerHandler, InventoryUtility.OFFHAND_SCREEN_SLOT);
        pendingOffhandPlace = false;
        stopwatch.reset();
        return true;
    }

    private boolean tryOffhandAction(final PlayerScreenHandler playerHandler, final long actionDelay) {
        if (!canMove(actionDelay)) {
            return false;
        }

        final ItemStack offhandStack = mc.player.getOffHandStack();
        switch (settings.getOffhandMode()) {
            case GOLDEN_APPLE -> {
                final ItemStack bestGoldenApple = InventoryUtility.getAllItems().stream()
                        .filter(stack -> !stack.isEmpty() && InventoryUtility.isGoldenApple(stack) && InventoryUtility.isUsable(stack))
                        .max(java.util.Comparator.comparingInt(ItemStack::getCount))
                        .orElse(null);
                if (bestGoldenApple == null) {
                    return false;
                }

                final int slot = InventoryUtility.getSlot(bestGoldenApple);
                if (slot == -1) {
                    return false;
                }

                if (!InventoryUtility.isGoldenApple(offhandStack)) {
                    InventoryUtility.moveToOffhand(playerHandler, slot);
                    stopwatch.reset();
                    return true;
                }

                if (offhandStack.getCount() + bestGoldenApple.getCount() <= offhandStack.getMaxCount()) {
                    if (!canPickupMove(actionDelay)) {
                        return false;
                    }

                    InventoryUtility.pickup(playerHandler, InventoryUtility.getScreenSlot(slot));
                    pendingOffhandPlace = true;
                    stopwatch.reset();
                    return true;
                }
            }
            case PROJECTILE -> {
                final ItemStack bestProjectile = InventoryUtility.getBestProjectile();
                if (bestProjectile == null) {
                    return false;
                }

                final int slot = InventoryUtility.getSlot(bestProjectile);
                if (slot == -1) {
                    return false;
                }

                final boolean shouldSwap = !InventoryUtility.isProjectile(offhandStack) || offhandStack.getCount() < bestProjectile.getCount();
                if (shouldSwap) {
                    InventoryUtility.moveToOffhand(playerHandler, slot);
                    stopwatch.reset();
                    return true;
                }
            }
            case FISHING_ROD -> {
                final ItemStack fishingRod = InventoryUtility.getFishingRodStack();
                if (fishingRod == null || offhandStack.getItem() instanceof FishingRodItem) {
                    return false;
                }

                final int slot = InventoryUtility.getSlot(fishingRod);
                if (slot != -1) {
                    InventoryUtility.moveToOffhand(playerHandler, slot);
                    stopwatch.reset();
                    return true;
                }
            }
            case BLOCK -> {
                final ItemStack bestBlock = InventoryUtility.getBestBlock();
                if (bestBlock == null) {
                    return false;
                }

                final int slot = InventoryUtility.getSlot(bestBlock);
                if (slot == -1) {
                    return false;
                }

                final boolean shouldSwap = !InventoryUtility.isPlaceableBlock(offhandStack) || offhandStack.getCount() < bestBlock.getCount();
                if (shouldSwap) {
                    InventoryUtility.moveToOffhand(playerHandler, slot);
                    stopwatch.reset();
                    return true;
                }
            }
            case NONE -> {
                return false;
            }
        }

        return false;
    }

    private boolean tryHotbarAction(final PlayerScreenHandler playerHandler, final long actionDelay) {
        if (settings.getOffhandMode() != InventoryManagerSettings.OffhandMode.GOLDEN_APPLE
                && settings.getGoldenAppleSlot() != 0
                && swapItemToHotbar(playerHandler, settings.getGoldenAppleSlot() - 1, InventoryUtility.getAllItems().stream()
                .filter(stack -> !stack.isEmpty() && InventoryUtility.isGoldenApple(stack) && InventoryUtility.isUsable(stack))
                .max(java.util.Comparator.comparingInt(ItemStack::getCount))
                .orElse(null), actionDelay)) {
            return true;
        }

        if (settings.getBlockSlot() != 0 && settings.getOffhandMode() != InventoryManagerSettings.OffhandMode.BLOCK) {
            final int targetSlot = settings.getBlockSlot() - 1;
            final ItemStack current = mc.player.getInventory().getStack(targetSlot);
            final ItemStack bestBlock = InventoryUtility.getBestBlock();
            if (bestBlock != null
                    && (!InventoryUtility.isPlaceableBlock(current) || bestBlock.getCount() > current.getCount())
                    && swapItemToHotbar(playerHandler, targetSlot, bestBlock, actionDelay)) {
                return true;
            }
        }

        if (settings.getSwordSlot() != 0) {
            final int targetSlot = settings.getSwordSlot() - 1;
            final ItemStack current = mc.player.getInventory().getStack(targetSlot);
            final ItemStack preferredWeapon = getPreferredWeapon();
            if (preferredWeapon != null
                    && getWeaponDamage(preferredWeapon) > getWeaponDamage(current)
                    && swapItemToHotbar(playerHandler, targetSlot, preferredWeapon, actionDelay)) {
                return true;
            }
        }

        if (settings.getPickaxeSlot() != 0) {
            final int targetSlot = settings.getPickaxeSlot() - 1;
            final ItemStack current = mc.player.getInventory().getStack(targetSlot);
            final ItemStack bestPickaxe = InventoryUtility.getBestPickaxe();
            if (bestPickaxe != null
                    && (!current.isIn(ItemTags.PICKAXES) || InventoryUtility.getDigSpeed(bestPickaxe) > InventoryUtility.getDigSpeed(current))
                    && swapItemToHotbar(playerHandler, targetSlot, bestPickaxe, actionDelay)) {
                return true;
            }
        }

        if (settings.getBowSlot() != 0) {
            final int targetSlot = settings.getBowSlot() - 1;
            final ItemStack current = mc.player.getInventory().getStack(targetSlot);
            final ItemStack preferredRanged = getPreferredRanged();
            if (preferredRanged != null
                    && getRangedScore(preferredRanged) > getRangedScore(current)
                    && swapItemToHotbar(playerHandler, targetSlot, preferredRanged, actionDelay)) {
                return true;
            }
        }

        if (settings.getAxeSlot() != 0) {
            final int targetSlot = settings.getAxeSlot() - 1;
            final ItemStack current = mc.player.getInventory().getStack(targetSlot);
            final ItemStack bestAxe = InventoryUtility.getBestAxe();
            if (bestAxe != null
                    && (!(current.getItem() instanceof AxeItem) || InventoryUtility.getDigSpeed(bestAxe) > InventoryUtility.getDigSpeed(current))
                    && swapItemToHotbar(playerHandler, targetSlot, bestAxe, actionDelay)) {
                return true;
            }
        }

        if (settings.getEggsSnowballsSlot() != 0
                && settings.getOffhandMode() != InventoryManagerSettings.OffhandMode.PROJECTILE
                && swapUtilityStackToHotbar(playerHandler, settings.getEggsSnowballsSlot() - 1, InventoryUtility.getBestProjectile(), actionDelay)) {
            return true;
        }

        if (settings.getPearlSlot() != 0
                && swapItemToHotbar(playerHandler, settings.getPearlSlot() - 1, InventoryUtility.getLargestStack(Items.ENDER_PEARL), actionDelay)) {
            return true;
        }

        if (settings.getWaterBucketSlot() != 0
                && swapItemToHotbar(playerHandler, settings.getWaterBucketSlot() - 1, InventoryUtility.getLargestStack(Items.WATER_BUCKET), actionDelay)) {
            return true;
        }

        if (settings.getSlimeBallSlot() != 0
                && swapItemToHotbar(playerHandler, settings.getSlimeBallSlot() - 1, InventoryUtility.getLargestStack(Items.SLIME_BALL), actionDelay)) {
            return true;
        }

        if (settings.getCrystalSlot() != 0
                && swapItemToHotbar(playerHandler, settings.getCrystalSlot() - 1, InventoryUtility.getLargestStack(Items.END_CRYSTAL), actionDelay)) {
            return true;
        }

        return false;
    }

    private boolean tryOverflowAction(final PlayerScreenHandler playerHandler) {
        if (InventoryUtility.countItem(InventoryUtility::isPlaceableBlock) > settings.getMaxBlockSize()
                && throwItem(playerHandler, InventoryUtility.getWorstBlock())) {
            return true;
        }

        if (InventoryUtility.countItem(InventoryUtility::isFoodItem) > settings.getMaxFoodSize()
                && throwItem(playerHandler, InventoryUtility.getBestFoodStack())) {
            return true;
        }

        if (InventoryUtility.countItem(stack -> stack.getItem() instanceof FishingRodItem) > settings.getMaxRodSize()
                && throwItem(playerHandler, InventoryUtility.getFishingRodStack())) {
            return true;
        }

        if (InventoryUtility.countItem(InventoryUtility::isProjectile) > settings.getMaxEggsSnowballsSize()
                && throwItem(playerHandler, InventoryUtility.getWorstProjectile())) {
            return true;
        }

        if (InventoryUtility.countItem(Items.ARROW) > 256
                && throwItem(playerHandler, InventoryUtility.getArrowStack())) {
            return true;
        }

        if (InventoryUtility.countItem(Items.WATER_BUCKET) > 1
                && throwItem(playerHandler, InventoryUtility.getSmallestStack(stack -> stack.getItem() == Items.WATER_BUCKET))) {
            return true;
        }

        return InventoryUtility.countItem(Items.LAVA_BUCKET) > 1
                && throwItem(playerHandler, InventoryUtility.getSmallestStack(stack -> stack.getItem() == Items.LAVA_BUCKET));
    }

    private boolean tryCleanupAction(final PlayerScreenHandler playerHandler) {
        final List<Integer> order = new ArrayList<>();
        for (int i = 0; i < InventoryUtility.MAIN_INVENTORY_SIZE; i++) {
            order.add(i);
        }
        Collections.shuffle(order);

        for (final int slotIndex : order) {
            final ItemStack stack = mc.player.getInventory().getStack(slotIndex);
            if (!stack.isEmpty() && !isUsefulItem(stack, playerHandler)) {
                return throwItem(playerHandler, stack);
            }
        }

        return false;
    }

    private boolean swapUtilityStackToHotbar(final PlayerScreenHandler playerHandler, final int targetSlot, final ItemStack candidate, final long actionDelay) {
        if (candidate == null) {
            return false;
        }

        final ItemStack current = mc.player.getInventory().getStack(targetSlot);
        return (!InventoryUtility.isProjectile(current) || candidate.getCount() > current.getCount())
                && swapItemToHotbar(playerHandler, targetSlot, candidate, actionDelay);
    }

    private boolean swapItemToHotbar(final PlayerScreenHandler playerHandler, final int targetSlot, final ItemStack candidate, final long actionDelay) {
        if (candidate == null || targetSlot < 0 || targetSlot >= InventoryUtility.HOTBAR_SIZE) {
            return false;
        }

        final int sourceSlot = InventoryUtility.getSlot(candidate);
        if (sourceSlot == -1 || sourceSlot == targetSlot || !canMove(actionDelay)) {
            return false;
        }

        final ItemStack current = mc.player.getInventory().getStack(targetSlot);
        if (!InventoryUtility.isUsable(current)) {
            return false;
        }

        InventoryUtility.swapInventorySlotToHotbar(playerHandler, sourceSlot, targetSlot);
        stopwatch.reset();
        return true;
    }

    private boolean throwItem(final PlayerScreenHandler playerHandler, final ItemStack stack) {
        if (stack == null || stack.isEmpty() || !settings.isThrowItemsEnabled() || !InventoryUtility.isUsable(stack)) {
            return false;
        }

        if (!settings.isFastThrowEnabled() && !canMove(settings.getDropDelay().longValue())) {
            return false;
        }

        final int slot = InventoryUtility.getSlot(stack);
        if (slot == -1) {
            return false;
        }

        InventoryUtility.drop(playerHandler, InventoryUtility.getScreenSlot(slot));
        stopwatch.reset();
        return true;
    }

    private ItemStack getPreferredWeapon() {
        ItemStack bestWeapon = InventoryUtility.getBestSword();
        final ItemStack bestSharpAxe = InventoryUtility.getBestSharpAxe();
        if (bestSharpAxe != null && InventoryUtility.getAxeDamage(bestSharpAxe) > InventoryUtility.getSwordDamage(bestWeapon)) {
            bestWeapon = bestSharpAxe;
        }
        return bestWeapon;
    }

    private double getWeaponDamage(final ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return 0.0;
        }

        if (stack.isIn(ItemTags.SWORDS)) {
            return InventoryUtility.getSwordDamage(stack);
        }

        if (stack.getItem() instanceof AxeItem) {
            return InventoryUtility.getAxeDamage(stack);
        }

        return 0.0;
    }

    private ItemStack getPreferredRanged() {
        if (settings.getBowPriority() == InventoryManagerSettings.BowPriority.CROSSBOW) {
            final ItemStack crossbow = InventoryUtility.getBestCrossbow();
            if (crossbow != null) {
                return crossbow;
            }

            final ItemStack bowAlt = InventoryUtility.getBestBowAlt();
            if (bowAlt != null) {
                return bowAlt;
            }

            return InventoryUtility.getBestBow();
        }

        final ItemStack punchBow = InventoryUtility.getBestBow();
        if (punchBow != null) {
            return punchBow;
        }

        final ItemStack crossbow = InventoryUtility.getBestCrossbow();
        if (crossbow != null) {
            return crossbow;
        }

        return InventoryUtility.getBestBowAlt();
    }

    private double getRangedScore(final ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return 0.0;
        }

        if (stack.getItem() instanceof CrossbowItem) {
            return InventoryUtility.getCrossbowScore(stack);
        }

        if (stack.getItem() instanceof BowItem) {
            return settings.getBowPriority() == InventoryManagerSettings.BowPriority.PUNCH_BOW
                    ? Math.max(InventoryUtility.getBowScore(stack), InventoryUtility.getBowScoreAlt(stack))
                    : Math.max(InventoryUtility.getBowScoreAlt(stack), InventoryUtility.getBowScore(stack));
        }

        return 0.0;
    }

    private boolean isUsefulItem(final ItemStack stack, final PlayerScreenHandler playerHandler) {
        if (stack.isEmpty()) {
            return false;
        }

        if (InventoryUtility.hasCustomName(stack) || InventoryUtility.isServerMenuItem(stack)) {
            return true;
        }

        if (!InventoryUtility.isUsable(stack)) {
            return false;
        }

        final Item item = stack.getItem();

        if (item == Items.COBWEB) {
            return true;
        }

        if (InventoryUtility.isArmor(stack)) {
            final EquippableComponent equippable = stack.get(DataComponentTypes.EQUIPPABLE);
            if (equippable == null) {
                return false;
            }

            final double score = InventoryUtility.getArmorValue(stack);
            final double equippedScore = InventoryUtility.getArmorValue(mc.player.getEquippedStack(equippable.slot()));
            final Slot bestArmorSlot = InventoryUtility.getBestArmorSlot(playerHandler, equippable.slot());
            final double bestScore = bestArmorSlot != null ? InventoryUtility.getArmorValue(bestArmorSlot.getStack()) : 0.0;

            if (equippedScore >= score) {
                return false;
            }

            return score >= bestScore;
        }

        if (stack.isIn(ItemTags.SWORDS)) {
            return stack == InventoryUtility.getBestSword() || stack == getPreferredWeapon();
        }

        if (stack.isIn(ItemTags.PICKAXES)) {
            return stack == InventoryUtility.getBestPickaxe();
        }

        if (item instanceof AxeItem) {
            if (InventoryUtility.isGodAxe(stack)) {
                return true;
            }
            if (InventoryUtility.isLegitAxe(stack)) {
                return stack == InventoryUtility.getBestSharpAxe() || stack == getPreferredWeapon();
            }
            return stack == InventoryUtility.getBestAxe();
        }

        if (item instanceof ShovelItem) {
            return stack == InventoryUtility.getBestShovel();
        }

        if (item instanceof CrossbowItem) {
            return stack == InventoryUtility.getBestCrossbow();
        }

        if (item instanceof BowItem) {
            return stack == InventoryUtility.getBestBow() || stack == InventoryUtility.getBestBowAlt();
        }

        if (item == Items.WATER_BUCKET && InventoryUtility.countItem(Items.WATER_BUCKET) > 1) {
            return stack != InventoryUtility.getSmallestStack(candidate -> candidate.getItem() == Items.WATER_BUCKET);
        }

        if (item == Items.LAVA_BUCKET && InventoryUtility.countItem(Items.LAVA_BUCKET) > 1) {
            return stack != InventoryUtility.getSmallestStack(candidate -> candidate.getItem() == Items.LAVA_BUCKET);
        }

        if (item instanceof FishingRodItem && InventoryUtility.countItem(candidate -> candidate.getItem() instanceof FishingRodItem) > settings.getMaxRodSize()) {
            return stack != InventoryUtility.getFishingRodStack();
        }

        if (InventoryUtility.isProjectile(stack) && InventoryUtility.countItem(InventoryUtility::isProjectile) > settings.getMaxEggsSnowballsSize()) {
            return stack != InventoryUtility.getWorstProjectile();
        }

        if (InventoryUtility.isPlaceableBlock(stack) && InventoryUtility.countItem(InventoryUtility::isPlaceableBlock) > settings.getMaxBlockSize()) {
            return stack != InventoryUtility.getWorstBlock();
        }

        if (InventoryUtility.isFoodItem(stack) && InventoryUtility.countItem(InventoryUtility::isFoodItem) > settings.getMaxFoodSize()) {
            return stack != InventoryUtility.getBestFoodStack();
        }

        if (item == Items.ARROW && InventoryUtility.countItem(Items.ARROW) > 256) {
            return stack != InventoryUtility.getArrowStack();
        }

        return InventoryUtility.isOpenZenUsefulItem(stack);
    }
}

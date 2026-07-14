package wtf.opal.client.feature.module.impl.utility.inventory.manager;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.*;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.slot.Slot;
import wtf.opal.client.OpalClient;
import wtf.opal.client.feature.module.Module;
import wtf.opal.client.feature.module.ModuleCategory;
import wtf.opal.client.feature.module.impl.combat.killaura.KillAuraModule;
import wtf.opal.client.feature.module.impl.movement.InventoryMoveModule;
import wtf.opal.client.feature.module.impl.utility.inventory.AutoArmorModule;
import wtf.opal.client.feature.module.impl.utility.inventory.AcaInventoryActionScheduler;
import wtf.opal.client.feature.module.impl.utility.inventory.ChestStealerModule;
import wtf.opal.client.feature.module.impl.world.scaffold.ScaffoldModule;
import wtf.opal.client.feature.module.repository.ModuleRepository;
import wtf.opal.event.impl.game.PostGameTickEvent;
import wtf.opal.event.impl.game.inventory.ManualInventoryInteractionEvent;
import wtf.opal.event.subscriber.Subscribe;
import wtf.opal.utility.misc.chat.ChatUtility;
import wtf.opal.utility.player.InventoryUtility;
import wtf.opal.utility.player.MinibloxArmorUtility;
import wtf.opal.utility.player.MoveUtility;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static wtf.opal.client.Constants.mc;

public final class InventoryManagerModule extends Module {
    private static final int MAX_INSTANT_ACTIONS_PER_TICK = 64;

    private final InventoryManagerSettings settings = new InventoryManagerSettings(this);

    private final AcaInventoryActionScheduler actionScheduler = AcaInventoryActionScheduler.getInstance();

    private int idleTicks;
    private boolean warnedAboutDuplicateSlots;
    private boolean performingAction;
    private boolean managementSessionActive;
    private boolean wasInventoryScreenOpen;

    public InventoryManagerModule() {
        super("Inventory Manager", "Manages your inventory.", ModuleCategory.UTILITY);
    }

    @Override
    protected void onDisable() {
        this.idleTicks = 0;
        this.warnedAboutDuplicateSlots = false;
        this.performingAction = false;
        this.managementSessionActive = false;
        this.wasInventoryScreenOpen = false;
        this.actionScheduler.endSession(AcaInventoryActionScheduler.Owner.INVENTORY_MANAGER);
        super.onDisable();
    }

    @Subscribe
    public void onPostGameTickEvent(final PostGameTickEvent event) {
        this.runInventoryManager(false, this.settings.getTimingMode());
    }

    public void runAutoArmorOnly(final AcaInventoryActionScheduler.TimingMode timingMode) {
        this.runInventoryManager(true, timingMode);
    }

    public void stopAutoArmorOnlySession() {
        if (this.isEnabled()) {
            return;
        }
        this.performingAction = false;
        this.managementSessionActive = false;
        this.actionScheduler.endSession(AcaInventoryActionScheduler.Owner.INVENTORY_MANAGER);
    }

    @Subscribe
    public void onManualInventoryInteraction(final ManualInventoryInteractionEvent event) {
        if (mc.player == null || !(mc.currentScreen instanceof InventoryScreen)
                || event.syncId() != mc.player.playerScreenHandler.syncId) {
            return;
        }
        this.performingAction = false;
        this.actionScheduler.pauseForManualInput(AcaInventoryActionScheduler.Owner.INVENTORY_MANAGER, mc.player.age);
    }

    private void runInventoryManager(final boolean autoArmorOnly,
                                     final AcaInventoryActionScheduler.TimingMode timingMode) {
        if (mc.player == null || mc.world == null) {
            this.resetStateForBlockedContext();
            this.wasInventoryScreenOpen = false;
            return;
        }

        final ModuleRepository moduleRepository = OpalClient.getInstance().getModuleRepository();
        final PlayerScreenHandler playerHandler = getPlayerScreenHandler();
        if (playerHandler == null) {
            this.resetStateForBlockedContext();
            this.wasInventoryScreenOpen = false;
            return;
        }

        final boolean inventoryScreenOpen = mc.currentScreen instanceof InventoryScreen;
        if (inventoryScreenOpen != this.wasInventoryScreenOpen) {
            this.resetStateForBlockedContext();
            this.wasInventoryScreenOpen = inventoryScreenOpen;
        }

        updateIdleState();

        if (!validateSlotConfig()) {
            resetStateForBlockedContext();
            this.performingAction = false;
            return;
        }

        if (!canManageInventory(moduleRepository, timingMode)) {
            this.performingAction = false;
            return;
        }
        this.actionScheduler.beginSession(
                AcaInventoryActionScheduler.Owner.INVENTORY_MANAGER,
                timingMode,
                mc.player.age
        );
        if (!this.managementSessionActive) {
            this.performingAction = false;
        }
        this.managementSessionActive = true;

        boolean acted = false;
        if (timingMode == AcaInventoryActionScheduler.TimingMode.INSTANT) {
            for (int action = 0; action < MAX_INSTANT_ACTIONS_PER_TICK; action++) {
                if (!this.tryNextAction(moduleRepository, playerHandler, autoArmorOnly, timingMode, true)) {
                    break;
                }
                acted = true;
            }
        } else if (settings.isFastThrowEnabled() && !autoArmorOnly) {
            acted = this.tryNextAction(moduleRepository, playerHandler, false, timingMode, false);
            if (!acted) {
                acted = this.tryFastThrowActions(playerHandler, timingMode);
            }
        } else {
            acted = this.tryNextAction(moduleRepository, playerHandler, autoArmorOnly, timingMode, true);
        }
        this.performingAction = acted;
    }

    private boolean tryNextAction(final ModuleRepository moduleRepository,
                                  final PlayerScreenHandler playerHandler,
                                  final boolean autoArmorOnly,
                                  final AcaInventoryActionScheduler.TimingMode timingMode,
                                  final boolean includeThrows) {
        if (isAutoArmorEnabled(moduleRepository) && tryAutoArmorAction(playerHandler, timingMode)) {
            return true;
        }
        if (autoArmorOnly) {
            return false;
        }
        if (tryOffhandAction(playerHandler, timingMode)) {
            return true;
        }
        if (tryHotbarAction(playerHandler, timingMode)) {
            return true;
        }
        if (!includeThrows) {
            return false;
        }
        if (tryOverflowAction(playerHandler, timingMode)) {
            return true;
        }
        return tryCleanupAction(playerHandler, timingMode);
    }

    private boolean canManageInventory(final ModuleRepository moduleRepository,
                                       final AcaInventoryActionScheduler.TimingMode timingMode) {
        if (InventoryUtility.hasServerItem()) {
            resetStateForBlockedContext();
            return false;
        }

        if (!mc.player.playerScreenHandler.getCursorStack().isEmpty()) {
            this.actionScheduler.pauseForManualInput(AcaInventoryActionScheduler.Owner.INVENTORY_MANAGER, mc.player.age);
            return false;
        }

        if (timingMode == AcaInventoryActionScheduler.TimingMode.ACA
                && (mc.player.isSprinting() || mc.player.isSneaking())) {
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

        if (settings.isInventoryOnlyEnabled()) {
            if (!inventoryScreenOpen) {
                resetStateForBlockedContext();
                return false;
            }
        } else {
            if (!inventoryScreenOpen && !inventoryMoveModule.isEnabled() && idleTicks <= 1) {
                return false;
            }
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
        this.performingAction = false;
        if (this.managementSessionActive) {
            this.actionScheduler.endSession(AcaInventoryActionScheduler.Owner.INVENTORY_MANAGER);
            this.managementSessionActive = false;
        }
    }

    public boolean isPerformingAction() {
        return this.isEnabled() && performingAction;
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

    private boolean performInventoryAction(final AcaInventoryActionScheduler.TimingMode timingMode,
                                           final AcaInventoryActionScheduler.Action action,
                                           final int rawSlot,
                                           final boolean fastThrow,
                                           final Runnable operation) {
        return this.actionScheduler.executeAction(
                AcaInventoryActionScheduler.Owner.INVENTORY_MANAGER,
                timingMode,
                mc.player.age,
                action,
                rawSlot,
                fastThrow,
                operation
        );
    }

    private boolean tryAutoArmorAction(final PlayerScreenHandler playerHandler,
                                       final AcaInventoryActionScheduler.TimingMode timingMode) {
        for (EquipmentSlot equipmentSlot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            final Slot bestArmorSlot = getBestArmorSlot(playerHandler, equipmentSlot);
            if (bestArmorSlot == null) {
                continue;
            }

            final double bestScore = getArmorScore(bestArmorSlot.getStack(), equipmentSlot);
            final double equippedScore = getArmorScore(mc.player.getEquippedStack(equipmentSlot), equipmentSlot);
            final int armorScreenSlot = InventoryUtility.getArmorScreenSlot(equipmentSlot);

            if (bestArmorSlot.id != armorScreenSlot
                    && bestScore > equippedScore
                    && !mc.player.getEquippedStack(equipmentSlot).isEmpty()
                    && this.hasEmptyInventorySlot()) {
                return this.performInventoryAction(
                        timingMode,
                        AcaInventoryActionScheduler.Action.QUICK_MOVE,
                        armorScreenSlot,
                        false,
                        () -> InventoryUtility.shiftClick(playerHandler, armorScreenSlot, 0)
                );
            }
        }

        for (EquipmentSlot equipmentSlot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            final Slot bestArmorSlot = getBestArmorSlot(playerHandler, equipmentSlot);
            if (bestArmorSlot == null) {
                continue;
            }

            final double bestScore = getArmorScore(bestArmorSlot.getStack(), equipmentSlot);
            final double equippedScore = getArmorScore(mc.player.getEquippedStack(equipmentSlot), equipmentSlot);
            final int armorScreenSlot = InventoryUtility.getArmorScreenSlot(equipmentSlot);

            if (bestArmorSlot.id != armorScreenSlot
                    && bestScore > equippedScore
                    && mc.player.getEquippedStack(equipmentSlot).isEmpty()) {
                return this.performInventoryAction(
                        timingMode,
                        AcaInventoryActionScheduler.Action.QUICK_MOVE,
                        bestArmorSlot.id,
                        false,
                        () -> InventoryUtility.shiftClick(playerHandler, bestArmorSlot.id, 0)
                );
            }
        }

        return false;
    }

    private Slot getBestArmorSlot(final PlayerScreenHandler playerHandler, final EquipmentSlot equipmentSlot) {
        return settings.isMinibloxMode()
                ? MinibloxArmorUtility.getBestArmorSlot(playerHandler, equipmentSlot)
                : InventoryUtility.getBestArmorSlot(playerHandler, equipmentSlot);
    }

    private double getArmorScore(final ItemStack stack, final EquipmentSlot equipmentSlot) {
        return settings.isMinibloxMode()
                ? MinibloxArmorUtility.getTier(stack, equipmentSlot)
                : InventoryUtility.getArmorValue(stack);
    }

    private boolean hasEmptyInventorySlot() {
        for (int slot = 0; slot < InventoryUtility.MAIN_INVENTORY_SIZE; slot++) {
            if (mc.player.getInventory().getStack(slot).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private boolean tryOffhandAction(final PlayerScreenHandler playerHandler,
                                     final AcaInventoryActionScheduler.TimingMode timingMode) {
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
                    final int screenSlot = InventoryUtility.getScreenSlot(slot);
                    return screenSlot != -1 && this.performInventoryAction(
                            timingMode,
                            AcaInventoryActionScheduler.Action.SWAP,
                            screenSlot,
                            false,
                            () -> InventoryUtility.moveToOffhand(playerHandler, slot)
                    );
                }

                if (bestGoldenApple != offhandStack && bestGoldenApple.getCount() > offhandStack.getCount()) {
                    final int screenSlot = InventoryUtility.getScreenSlot(slot);
                    return screenSlot != -1 && this.performInventoryAction(
                            timingMode,
                            AcaInventoryActionScheduler.Action.SWAP,
                            screenSlot,
                            false,
                            () -> InventoryUtility.moveToOffhand(playerHandler, slot)
                    );
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
                    final int screenSlot = InventoryUtility.getScreenSlot(slot);
                    return screenSlot != -1 && this.performInventoryAction(
                            timingMode,
                            AcaInventoryActionScheduler.Action.SWAP,
                            screenSlot,
                            false,
                            () -> InventoryUtility.moveToOffhand(playerHandler, slot)
                    );
                }
            }
            case FISHING_ROD -> {
                final ItemStack fishingRod = InventoryUtility.getFishingRodStack();
                if (fishingRod == null || offhandStack.getItem() instanceof FishingRodItem) {
                    return false;
                }

                final int slot = InventoryUtility.getSlot(fishingRod);
                if (slot != -1) {
                    final int screenSlot = InventoryUtility.getScreenSlot(slot);
                    return screenSlot != -1 && this.performInventoryAction(
                            timingMode,
                            AcaInventoryActionScheduler.Action.SWAP,
                            screenSlot,
                            false,
                            () -> InventoryUtility.moveToOffhand(playerHandler, slot)
                    );
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
                    final int screenSlot = InventoryUtility.getScreenSlot(slot);
                    return screenSlot != -1 && this.performInventoryAction(
                            timingMode,
                            AcaInventoryActionScheduler.Action.SWAP,
                            screenSlot,
                            false,
                            () -> InventoryUtility.moveToOffhand(playerHandler, slot)
                    );
                }
            }
            case NONE -> {
                return false;
            }
        }

        return false;
    }

    private boolean tryHotbarAction(final PlayerScreenHandler playerHandler,
                                    final AcaInventoryActionScheduler.TimingMode timingMode) {
        if (settings.getOffhandMode() != InventoryManagerSettings.OffhandMode.GOLDEN_APPLE
                && settings.getGoldenAppleSlot() != 0
                && swapItemToHotbar(playerHandler, settings.getGoldenAppleSlot() - 1, InventoryUtility.getAllItems().stream()
                .filter(stack -> !stack.isEmpty() && InventoryUtility.isGoldenApple(stack) && InventoryUtility.isUsable(stack))
                .max(java.util.Comparator.comparingInt(ItemStack::getCount))
                .orElse(null), timingMode)) {
            return true;
        }

        if (settings.getBlockSlot() != 0 && settings.getOffhandMode() != InventoryManagerSettings.OffhandMode.BLOCK) {
            final int targetSlot = settings.getBlockSlot() - 1;
            final ItemStack current = mc.player.getInventory().getStack(targetSlot);
            final ItemStack bestBlock = InventoryUtility.getBestBlock();
            if (bestBlock != null
                    && (!InventoryUtility.isPlaceableBlock(current) || bestBlock.getCount() > current.getCount())
                    && swapItemToHotbar(playerHandler, targetSlot, bestBlock, timingMode)) {
                return true;
            }
        }

        if (settings.getSwordSlot() != 0) {
            final int targetSlot = settings.getSwordSlot() - 1;
            final ItemStack current = mc.player.getInventory().getStack(targetSlot);
            final ItemStack preferredWeapon = getPreferredWeapon();
            if (preferredWeapon != null
                    && getWeaponDamage(preferredWeapon) > getWeaponDamage(current)
                    && swapItemToHotbar(playerHandler, targetSlot, preferredWeapon, timingMode)) {
                return true;
            }
        }

        if (settings.getPickaxeSlot() != 0) {
            final int targetSlot = settings.getPickaxeSlot() - 1;
            final ItemStack current = mc.player.getInventory().getStack(targetSlot);
            final ItemStack bestPickaxe = InventoryUtility.getBestPickaxe();
            if (bestPickaxe != null
                    && (!current.isIn(ItemTags.PICKAXES) || InventoryUtility.getDigSpeed(bestPickaxe) > InventoryUtility.getDigSpeed(current))
                    && swapItemToHotbar(playerHandler, targetSlot, bestPickaxe, timingMode)) {
                return true;
            }
        }

        if (settings.getBowSlot() != 0) {
            final int targetSlot = settings.getBowSlot() - 1;
            final ItemStack current = mc.player.getInventory().getStack(targetSlot);
            final ItemStack preferredRanged = getPreferredRanged();
            if (preferredRanged != null
                    && getRangedScore(preferredRanged) > getRangedScore(current)
                    && swapItemToHotbar(playerHandler, targetSlot, preferredRanged, timingMode)) {
                return true;
            }
        }

        if (settings.getAxeSlot() != 0) {
            final int targetSlot = settings.getAxeSlot() - 1;
            final ItemStack current = mc.player.getInventory().getStack(targetSlot);
            final ItemStack bestAxe = InventoryUtility.getBestAxe();
            if (bestAxe != null
                    && (!(current.getItem() instanceof AxeItem) || InventoryUtility.getDigSpeed(bestAxe) > InventoryUtility.getDigSpeed(current))
                    && swapItemToHotbar(playerHandler, targetSlot, bestAxe, timingMode)) {
                return true;
            }
        }

        if (settings.getEggsSnowballsSlot() != 0
                && settings.getOffhandMode() != InventoryManagerSettings.OffhandMode.PROJECTILE
                && swapUtilityStackToHotbar(playerHandler, settings.getEggsSnowballsSlot() - 1, InventoryUtility.getBestProjectile(), timingMode)) {
            return true;
        }

        if (settings.getPearlSlot() != 0
                && swapItemToHotbar(playerHandler, settings.getPearlSlot() - 1, InventoryUtility.getLargestStack(Items.ENDER_PEARL), timingMode)) {
            return true;
        }

        if (settings.getWaterBucketSlot() != 0
                && swapItemToHotbar(playerHandler, settings.getWaterBucketSlot() - 1, InventoryUtility.getLargestStack(Items.WATER_BUCKET), timingMode)) {
            return true;
        }

        if (settings.getSlimeBallSlot() != 0
                && swapItemToHotbar(playerHandler, settings.getSlimeBallSlot() - 1, InventoryUtility.getLargestStack(Items.SLIME_BALL), timingMode)) {
            return true;
        }

        if (settings.getCrystalSlot() != 0
                && swapItemToHotbar(playerHandler, settings.getCrystalSlot() - 1, InventoryUtility.getLargestStack(Items.END_CRYSTAL), timingMode)) {
            return true;
        }

        return false;
    }

    private boolean tryFastThrowActions(final PlayerScreenHandler playerHandler,
                                        final AcaInventoryActionScheduler.TimingMode timingMode) {
        boolean acted = false;
        for (int action = 0; action < MAX_INSTANT_ACTIONS_PER_TICK; action++) {
            if (this.tryOverflowAction(playerHandler, timingMode)) {
                acted = true;
                continue;
            }
            if (this.tryCleanupAction(playerHandler, timingMode)) {
                acted = true;
                continue;
            }
            break;
        }
        return acted;
    }

    private boolean tryOverflowAction(final PlayerScreenHandler playerHandler,
                                      final AcaInventoryActionScheduler.TimingMode timingMode) {
        if (InventoryUtility.countItem(InventoryUtility::isPlaceableBlock) > settings.getMaxBlockSize()
                && throwItem(playerHandler, InventoryUtility.getWorstBlock(), timingMode)) {
            return true;
        }

        if (InventoryUtility.countItem(InventoryUtility::isFoodItem) > settings.getMaxFoodSize()
                && throwItem(playerHandler, InventoryUtility.getBestFoodStack(), timingMode)) {
            return true;
        }

        if (InventoryUtility.countItem(stack -> stack.getItem() instanceof FishingRodItem) > settings.getMaxRodSize()
                && throwItem(playerHandler, InventoryUtility.getFishingRodStack(), timingMode)) {
            return true;
        }

        if (InventoryUtility.countItem(InventoryUtility::isProjectile) > settings.getMaxEggsSnowballsSize()
                && throwItem(playerHandler, InventoryUtility.getWorstProjectile(), timingMode)) {
            return true;
        }

        if (InventoryUtility.countItem(Items.ARROW) > 256
                && throwItem(playerHandler, InventoryUtility.getArrowStack(), timingMode)) {
            return true;
        }

        if (InventoryUtility.countItem(Items.WATER_BUCKET) > 1
                && throwItem(playerHandler, InventoryUtility.getSmallestStack(stack -> stack.getItem() == Items.WATER_BUCKET), timingMode)) {
            return true;
        }

        return InventoryUtility.countItem(Items.LAVA_BUCKET) > 1
                && throwItem(playerHandler, InventoryUtility.getSmallestStack(stack -> stack.getItem() == Items.LAVA_BUCKET), timingMode);
    }

    private boolean tryCleanupAction(final PlayerScreenHandler playerHandler,
                                     final AcaInventoryActionScheduler.TimingMode timingMode) {
        final List<Integer> order = new ArrayList<>();
        for (int i = 0; i < InventoryUtility.MAIN_INVENTORY_SIZE; i++) {
            order.add(i);
        }
        Collections.shuffle(order);

        for (final int slotIndex : order) {
            final ItemStack stack = mc.player.getInventory().getStack(slotIndex);
            if (!stack.isEmpty() && !isUsefulItem(stack, playerHandler)) {
                return throwItem(playerHandler, stack, timingMode);
            }
        }

        return false;
    }

    private boolean swapUtilityStackToHotbar(final PlayerScreenHandler playerHandler, final int targetSlot,
                                             final ItemStack candidate,
                                             final AcaInventoryActionScheduler.TimingMode timingMode) {
        if (candidate == null) {
            return false;
        }

        final ItemStack current = mc.player.getInventory().getStack(targetSlot);
        return (!InventoryUtility.isProjectile(current) || candidate.getCount() > current.getCount())
                && swapItemToHotbar(playerHandler, targetSlot, candidate, timingMode);
    }

    private boolean swapItemToHotbar(final PlayerScreenHandler playerHandler, final int targetSlot,
                                     final ItemStack candidate,
                                     final AcaInventoryActionScheduler.TimingMode timingMode) {
        if (candidate == null || targetSlot < 0 || targetSlot >= InventoryUtility.HOTBAR_SIZE) {
            return false;
        }

        final int sourceSlot = InventoryUtility.getSlot(candidate);
        if (sourceSlot == -1 || sourceSlot == targetSlot) {
            return false;
        }

        final ItemStack current = mc.player.getInventory().getStack(targetSlot);
        if (!InventoryUtility.isUsable(current)) {
            return false;
        }

        final int screenSlot = InventoryUtility.getScreenSlot(sourceSlot);
        return screenSlot != -1 && this.performInventoryAction(
                timingMode,
                AcaInventoryActionScheduler.Action.SWAP,
                screenSlot,
                false,
                () -> InventoryUtility.swapInventorySlotToHotbar(playerHandler, sourceSlot, targetSlot)
        );
    }

    private boolean throwItem(final PlayerScreenHandler playerHandler, final ItemStack stack,
                              final AcaInventoryActionScheduler.TimingMode timingMode) {
        if (stack == null || stack.isEmpty() || !settings.isThrowItemsEnabled() || !InventoryUtility.isUsable(stack)) {
            return false;
        }

        final int slot = InventoryUtility.getSlot(stack);
        if (slot == -1) {
            return false;
        }

        final int screenSlot = InventoryUtility.getScreenSlot(slot);
        return screenSlot != -1 && this.performInventoryAction(
                timingMode,
                AcaInventoryActionScheduler.Action.THROW,
                screenSlot,
                settings.isFastThrowEnabled(),
                () -> InventoryUtility.drop(playerHandler, screenSlot)
        );
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

        if (settings.isMinibloxMode()) {
            final MinibloxArmorUtility.ArmorInfo armor = MinibloxArmorUtility.identify(stack);
            if (armor != null) {
                final Slot bestArmorSlot = MinibloxArmorUtility.getBestArmorSlot(playerHandler, armor.slot());
                return bestArmorSlot != null && bestArmorSlot.getStack() == stack;
            }
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
            final EquipmentSlot equipmentSlot = InventoryUtility.getArmorEquipmentSlot(stack);
            if (equipmentSlot == null) {
                return false;
            }

            final double score = InventoryUtility.getArmorValue(stack);
            final double equippedScore = InventoryUtility.getArmorValue(mc.player.getEquippedStack(equipmentSlot));
            final Slot bestArmorSlot = InventoryUtility.getBestArmorSlot(playerHandler, equipmentSlot);
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

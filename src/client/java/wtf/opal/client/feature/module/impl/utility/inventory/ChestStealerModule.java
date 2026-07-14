package wtf.opal.client.feature.module.impl.utility.inventory;

import net.minecraft.block.ChestBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.EnderChestBlockEntity;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.block.enums.ChestType;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.EquippableComponent;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.*;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;
import wtf.opal.client.feature.module.Module;
import wtf.opal.client.feature.module.ModuleCategory;
import wtf.opal.client.feature.helper.impl.player.mouse.MouseHelper;
import wtf.opal.client.feature.module.property.impl.bool.BooleanProperty;
import wtf.opal.client.feature.module.property.impl.mode.ModeProperty;
import wtf.opal.event.impl.game.PostGameTickEvent;
import wtf.opal.event.impl.game.input.MouseHandleInputEvent;
import wtf.opal.event.impl.game.inventory.ManualInventoryInteractionEvent;
import wtf.opal.event.impl.game.player.interaction.ItemUseEvent;
import wtf.opal.event.subscriber.Subscribe;
import wtf.opal.mixin.KeyBindingAccessor;
import wtf.opal.utility.misc.chat.ChatUtility;
import wtf.opal.utility.player.InventoryUtility;
import wtf.opal.utility.player.PlayerUtility;

import java.util.*;
import java.util.stream.IntStream;

import static wtf.opal.client.Constants.mc;

public final class
ChestStealerModule extends Module {

    private final AcaInventoryActionScheduler actionScheduler = AcaInventoryActionScheduler.getInstance();

    private final BooleanProperty smart = new BooleanProperty("Smart", true);
    private final BooleanProperty highlight = new BooleanProperty("Highlight items", true).hideIf(() -> !smart.getValue());
    private final ModeProperty<AcaInventoryActionScheduler.TimingMode> timingMode =
            new ModeProperty<>("Timing", AcaInventoryActionScheduler.TimingMode.ACA);
    private final BooleanProperty ghostHand = new BooleanProperty("Ghost Hand", false);
    private final BooleanProperty ghostDebug = new BooleanProperty("Ghost Debug", false).hideIf(() -> !ghostHand.getValue());

    private long ghostLastInteractTime;
    private boolean ghostSessionActive;
    private boolean ghostWaitingRelease;
    private boolean ghostHadScreen;
    private int ghostSessionTimeout;
    private int lastContainerSyncId = -1;

    public ChestStealerModule() {
        super("Chest Stealer", "Steals only useful or upgraded items from chests.", ModuleCategory.UTILITY);
        addProperties(smart, highlight, timingMode, ghostHand, ghostDebug);
    }

    @Override
    protected void onDisable() {
        this.actionScheduler.endSession(AcaInventoryActionScheduler.Owner.CHEST_STEALER);
        this.lastContainerSyncId = -1;
        this.releaseGhostUseKey();
        this.resetGhostSession();
        super.onDisable();
    }

    @Subscribe
    public void onMouseHandleInput(final MouseHandleInputEvent event) {
        if (!this.ghostHand.getValue() || mc.currentScreen != null || mc.player == null || mc.player.isUsingItem()) {
            return;
        }

        final BlockHitResult targetHit = this.findGhostTargetHit();
        if (targetHit == null) {
            return;
        }

        if (System.currentTimeMillis() - this.ghostLastInteractTime < 200L) {
            return;
        }

        if (!MouseHelper.getRightButton().wasPressed()) {
            return;
        }

        final var result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, targetHit);
        if (result.isAccepted()) {
            mc.player.swingHand(Hand.MAIN_HAND);
            this.ghostLastInteractTime = System.currentTimeMillis();
            this.ghostSessionActive = true;
            this.ghostWaitingRelease = false;
            this.ghostHadScreen = false;
            this.ghostSessionTimeout = 40;
            event.setCancelled();
        }
    }

    @Subscribe
    public void onItemUse(final ItemUseEvent event) {
        if (this.ghostSessionActive) {
            event.setCancelled();
            this.releaseGhostUseKey();
            MouseHelper.getRightButton().setDisabled();
        }
    }

    @Subscribe
    public void onPostGameTickEvent(final PostGameTickEvent event) {
        this.updateGhostHandSession();

        if (!(mc.currentScreen instanceof GenericContainerScreen container)) {
            if (this.lastContainerSyncId != -1) {
                this.actionScheduler.endSession(AcaInventoryActionScheduler.Owner.CHEST_STEALER);
            }
            this.lastContainerSyncId = -1;
            return;
        }

        final GenericContainerScreenHandler screenHandler = container.getScreenHandler();
        final Inventory chestInventory = screenHandler.getInventory();

        if (this.lastContainerSyncId != screenHandler.syncId) {
            this.lastContainerSyncId = screenHandler.syncId;
        }
        this.actionScheduler.beginSession(
                AcaInventoryActionScheduler.Owner.CHEST_STEALER,
                this.timingMode.getValue(),
                mc.player.age
        );

        if (!screenHandler.getCursorStack().isEmpty()) {
            this.actionScheduler.pauseForManualInput(AcaInventoryActionScheduler.Owner.CHEST_STEALER, mc.player.age);
            return;
        }

        if (chestInventory.isEmpty() || !this.canStoreAnyChestItem(chestInventory)) {
            closeContainerWhenSafe(container);
            return;
        }

        final Map<EquipmentSlot, ItemStack> bestChestArmor = getBestChestArmor(chestInventory);
        final ItemStack bestChestSword = getBestChestSword(chestInventory);
        final ItemStack bestChestPickaxe = getBestChestTool(chestInventory, ItemTags.PICKAXES);
        final ItemStack bestChestAxe = getBestChestTool(chestInventory, ItemTags.AXES);

        final List<Integer> candidates = new ArrayList<>();
        for (int i = 0; i < chestInventory.size(); i++) {
            final ItemStack stack = chestInventory.getStack(i);
            if (stack.isEmpty()) continue;
            if (shouldTake(stack, bestChestArmor, bestChestSword, bestChestPickaxe, bestChestAxe) || !smart.getValue()) {
                candidates.add(i);
            }
        }

        boolean tookItem = false;
        if (this.timingMode.getValue() == AcaInventoryActionScheduler.TimingMode.INSTANT) {
            for (final int slot : candidates) {
                if (slot < 0 || slot >= chestInventory.size() || chestInventory.getStack(slot).isEmpty()) {
                    continue;
                }
                tookItem |= this.moveChestSlot(screenHandler, slot);
            }
        } else if (!candidates.isEmpty()) {
            final int slot = this.selectNextSlot(candidates);
            tookItem = this.moveChestSlot(screenHandler, slot);
        }

        if (this.timingMode.getValue() == AcaInventoryActionScheduler.TimingMode.INSTANT && tookItem) {
            final boolean candidateRemains = candidates.stream().anyMatch(slot ->
                    slot >= 0 && slot < chestInventory.size() && !chestInventory.getStack(slot).isEmpty());
            if (!candidateRemains) {
                closeContainerWhenSafe(container);
                return;
            }
        }

        if (smart.getValue() && !tookItem) {
            boolean hasValuableLeft = false;
            for (int i = 0; i < chestInventory.size(); i++) {
                final ItemStack stack = chestInventory.getStack(i);
                if (stack.isEmpty()) continue;

                if (shouldTake(stack, bestChestArmor, bestChestSword, bestChestPickaxe, bestChestAxe)) {
                    hasValuableLeft = true;
                    break;
                }
            }

            if (!hasValuableLeft) {
                closeContainerWhenSafe(container);
            }
        }
    }

    public BooleanProperty getHighlight() {
        return highlight;
    }

    public BooleanProperty getSmart() {
        return smart;
    }

    public boolean isRateLimited() {
        return mc.player != null && this.actionScheduler.isCoolingDown(
                AcaInventoryActionScheduler.Owner.CHEST_STEALER,
                this.timingMode.getValue(),
                mc.player.age
        );
    }

    public boolean isConflictActive() {
        if (!isEnabled() || mc.player == null) {
            return false;
        }

        return mc.currentScreen instanceof GenericContainerScreen
                || mc.player.currentScreenHandler instanceof GenericContainerScreenHandler
                || isRateLimited();
    }

    public boolean shouldTake(ItemStack stack,
                              Map<EquipmentSlot, ItemStack> bestChestArmor,
                              ItemStack bestChestSword,
                              ItemStack bestChestPickaxe,
                              ItemStack bestChestAxe) {
        if (InventoryUtility.isGoodItem(stack)) {
            return true;
        }

        if (stack.isIn(ItemTags.SWORDS)) {
            final double value = InventoryUtility.getSwordValue(stack);
            final double current = InventoryUtility.getSwordValue(getBestInventorySword());

            return stack == bestChestSword && value > current;
        }

        if (stack.isIn(ItemTags.PICKAXES)) {
            final double value = InventoryUtility.getToolValue(stack);
            final double current = InventoryUtility.getToolValue(getBestInventoryTool(ItemTags.PICKAXES));

            return stack == bestChestPickaxe && value > current;
        }

        if (stack.isIn(ItemTags.AXES)) {
            final double value = InventoryUtility.getToolValue(stack);
            final double current = InventoryUtility.getToolValue(getBestInventoryAxe());

            return stack == bestChestAxe && value > current;
        }

        if (!InventoryUtility.isArmor(stack)) return false;

        final EquippableComponent equip = stack.getComponents().get(DataComponentTypes.EQUIPPABLE);
        if (equip == null) return false;


        final EquipmentSlot slot = equip.slot();
        final ItemStack currentEquipped = mc.player.getEquippedStack(slot);
        final ItemStack bestInChest = bestChestArmor.getOrDefault(slot, ItemStack.EMPTY);

        if (stack != bestInChest) return false;


        final double stackValue = InventoryUtility.getArmorValue(stack);
        final double equippedValue = InventoryUtility.getArmorValue(currentEquipped);

        return stackValue > equippedValue;

    }

    public Map<EquipmentSlot, ItemStack> getBestChestArmor(Inventory chest) {
        return IntStream.range(0, chest.size())
                .mapToObj(chest::getStack)
                .filter(InventoryUtility::isArmor)
                .map(stack -> {
                    final EquippableComponent equip = stack.getComponents().get(DataComponentTypes.EQUIPPABLE);
                    return equip != null ? Map.entry(equip.slot(), stack) : null;
                })
                .filter(Objects::nonNull)
                .collect(HashMap::new, (map, entry) -> {
                    map.merge(entry.getKey(), entry.getValue(), (existing, replacement) ->
                            InventoryUtility.getArmorValue(replacement) > InventoryUtility.getArmorValue(existing)
                                    ? replacement : existing);
                }, HashMap::putAll);
    }

    public ItemStack getBestChestSword(Inventory chest) {
        return IntStream.range(0, chest.size())
                .mapToObj(chest::getStack)
                .filter(stack -> stack.isIn(ItemTags.SWORDS))
                .max(Comparator.comparingDouble(InventoryUtility::getSwordValue))
                .orElse(ItemStack.EMPTY);
    }

    public ItemStack getBestChestTool(Inventory chest, TagKey<Item> tag) {
        return IntStream.range(0, chest.size())
                .mapToObj(chest::getStack)
                .filter(stack -> stack.isIn(tag))
                .max(Comparator.comparingDouble(InventoryUtility::getToolValue))
                .orElse(ItemStack.EMPTY);
    }

    private ItemStack getBestInventorySword() {
        return IntStream.range(0, InventoryUtility.MAIN_INVENTORY_SIZE)
                .mapToObj(i -> mc.player.getInventory().getStack(i))
                .filter(stack -> stack.isIn(ItemTags.SWORDS))
                .max(Comparator.comparingDouble(InventoryUtility::getSwordValue))
                .orElse(ItemStack.EMPTY);
    }

    private ItemStack getBestInventoryTool(TagKey<Item> tag) {
        return IntStream.range(0, InventoryUtility.MAIN_INVENTORY_SIZE)
                .mapToObj(i -> mc.player.getInventory().getStack(i))
                .filter(stack -> stack.isIn(tag))
                .max(Comparator.comparingDouble(InventoryUtility::getToolValue))
                .orElse(ItemStack.EMPTY);
    }

    private ItemStack getBestInventoryAxe() {
        return IntStream.range(0, InventoryUtility.MAIN_INVENTORY_SIZE)
                .mapToObj(i -> mc.player.getInventory().getStack(i))
                .filter(stack -> stack.getItem() instanceof AxeItem)
                .max(Comparator.comparingDouble(InventoryUtility::getToolValue))
                .orElse(ItemStack.EMPTY);
    }

    private boolean moveChestSlot(final GenericContainerScreenHandler screenHandler, final int slot) {
        if (mc.player != null
                && this.timingMode.getValue() == AcaInventoryActionScheduler.TimingMode.ACA
                && (mc.player.isSprinting() || mc.player.isSneaking())) {
            return false;
        }
        return mc.player != null && this.actionScheduler.executeAction(
                AcaInventoryActionScheduler.Owner.CHEST_STEALER,
                this.timingMode.getValue(),
                mc.player.age,
                AcaInventoryActionScheduler.Action.QUICK_MOVE,
                slot,
                false,
                () -> InventoryUtility.shiftClick(screenHandler, slot, 0)
        );
    }

    private boolean canStoreAnyChestItem(final Inventory chestInventory) {
        if (mc.player == null) {
            return false;
        }

        if (!InventoryUtility.isInventoryFull()) {
            return true;
        }

        for (int chestSlot = 0; chestSlot < chestInventory.size(); chestSlot++) {
            final ItemStack chestStack = chestInventory.getStack(chestSlot);
            if (chestStack.isEmpty()) {
                continue;
            }

            for (int playerSlot = 0; playerSlot < InventoryUtility.MAIN_INVENTORY_SIZE; playerSlot++) {
                final ItemStack playerStack = mc.player.getInventory().getStack(playerSlot);
                if (ItemStack.areItemsAndComponentsEqual(playerStack, chestStack)
                        && playerStack.getCount() < Math.min(playerStack.getMaxCount(), mc.player.getInventory().getMaxCount(playerStack))) {
                    return true;
                }
            }
        }

        return false;
    }

    private void closeContainerWhenSafe(final GenericContainerScreen container) {
        this.actionScheduler.scheduleClose(
                AcaInventoryActionScheduler.Owner.CHEST_STEALER,
                this.timingMode.getValue(),
                mc.player.age
        );
        if (this.actionScheduler.canClose(
                AcaInventoryActionScheduler.Owner.CHEST_STEALER,
                this.timingMode.getValue(),
                mc.player.age
        )) {
            container.close();
            this.actionScheduler.recordClose(AcaInventoryActionScheduler.Owner.CHEST_STEALER);
        }
    }

    private int selectNextSlot(final List<Integer> candidates) {
        final int lastSlot = this.actionScheduler.getLastRawSlot(AcaInventoryActionScheduler.Owner.CHEST_STEALER);
        if (lastSlot < 0) {
            Collections.shuffle(candidates);
            return candidates.getFirst();
        }

        candidates.sort(Comparator.comparingInt(slot -> this.slotDistance(lastSlot, slot)));
        return candidates.getFirst();
    }

    private int slotDistance(final int first, final int second) {
        return Math.abs(first / 9 - second / 9) + Math.abs(first % 9 - second % 9);
    }

    @Subscribe
    public void onManualInventoryInteraction(final ManualInventoryInteractionEvent event) {
        if (mc.player == null || !(mc.currentScreen instanceof GenericContainerScreen container)
                || event.syncId() != container.getScreenHandler().syncId) {
            return;
        }
        this.actionScheduler.pauseForManualInput(AcaInventoryActionScheduler.Owner.CHEST_STEALER, mc.player.age);
    }

    private void updateGhostHandSession() {
        if (!this.ghostSessionActive) {
            return;
        }

        if (this.ghostSessionTimeout > 0) {
            this.ghostSessionTimeout--;
        } else {
            this.resetGhostSession();
            return;
        }

        if (mc.currentScreen != null) {
            this.ghostHadScreen = true;
        } else if (this.ghostHadScreen) {
            this.ghostWaitingRelease = true;
        }

        if (this.ghostWaitingRelease && !PlayerUtility.isMouseButtonPressed(GLFW.GLFW_MOUSE_BUTTON_RIGHT)) {
            this.releaseGhostUseKey();
            MouseHelper.getRightButton().setDisabled();
            this.resetGhostSession();
        }
    }

    private void resetGhostSession() {
        this.ghostSessionActive = false;
        this.ghostWaitingRelease = false;
        this.ghostHadScreen = false;
    }

    private void releaseGhostUseKey() {
        if (mc.options != null && mc.options.useKey != null) {
            mc.options.useKey.setPressed(false);
            ((KeyBindingAccessor) mc.options.useKey).callReset();
        }
    }

    private BlockHitResult findGhostTargetHit() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) {
            return null;
        }

        if (mc.crosshairTarget instanceof BlockHitResult blockHit) {
            final BlockEntity blockEntity = mc.world.getBlockEntity(blockHit.getBlockPos());
            if (blockEntity instanceof ChestBlockEntity || blockEntity instanceof EnderChestBlockEntity || blockEntity instanceof ShulkerBoxBlockEntity) {
                return null;
            }
        }

        final Vec3d eyePos = mc.player.getEyePos();
        final Vec3d lookVec = mc.player.getRotationVec(1.0F);
        final Vec3d reachEnd = eyePos.add(lookVec.multiply(4.5D));

        BlockHitResult fakeHit = null;
        double closestDistance = Double.MAX_VALUE;

        final List<BlockEntity> blockEntities = new ArrayList<>();
        final int radius = 2;
        final int playerChunkX = mc.player.getBlockX() >> 4;
        final int playerChunkZ = mc.player.getBlockZ() >> 4;

        for (int x = playerChunkX - radius; x <= playerChunkX + radius; x++) {
            for (int z = playerChunkZ - radius; z <= playerChunkZ + radius; z++) {
                final var chunk = mc.world.getChunk(x, z);
                if (chunk != null) {
                    blockEntities.addAll(chunk.getBlockEntities().values());
                }
            }
        }

        for (final BlockEntity blockEntity : blockEntities) {
            if (!(blockEntity instanceof ChestBlockEntity || blockEntity instanceof EnderChestBlockEntity || blockEntity instanceof ShulkerBoxBlockEntity)) {
                continue;
            }

            final Box box = this.getContainerBox(blockEntity);
            if (box == null) {
                continue;
            }

            final Optional<Vec3d> hit = box.raycast(eyePos, reachEnd);
            if (hit.isPresent()) {
                final double distance = hit.get().distanceTo(eyePos);
                if (distance < closestDistance) {
                    closestDistance = distance;
                    fakeHit = new BlockHitResult(hit.get(), Direction.UP, blockEntity.getPos(), false);
                }
            }
        }

        if (fakeHit != null && this.ghostDebug.getValue()) {
            final BlockEntity blockEntity = mc.world.getBlockEntity(fakeHit.getBlockPos());
            if (blockEntity != null) {
                ChatUtility.print("GhostHand: Interacting with "
                        + blockEntity.getCachedState().getBlock().getName().getString()
                        + " at " + blockEntity.getPos().toShortString());
            }
        }

        return fakeHit;
    }

    private Box getContainerBox(final BlockEntity blockEntity) {
        final BlockPos pos = blockEntity.getPos();
        final Box baseBox = new Box(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1);

        if (blockEntity instanceof ChestBlockEntity) {
            final var state = blockEntity.getCachedState();
            if (!state.contains(ChestBlock.CHEST_TYPE)) {
                return baseBox;
            }

            final ChestType type = state.get(ChestBlock.CHEST_TYPE);
            if (type == ChestType.SINGLE) {
                return baseBox;
            }

            if (type == ChestType.LEFT) {
                return null;
            }

            final Direction facing = state.get(ChestBlock.FACING);
            final Direction side = facing.rotateYClockwise();
            final BlockPos otherPos = pos.offset(side);

            return baseBox.union(new Box(otherPos.getX(), otherPos.getY(), otherPos.getZ(), otherPos.getX() + 1, otherPos.getY() + 1, otherPos.getZ() + 1));
        }

        return baseBox;
    }
}

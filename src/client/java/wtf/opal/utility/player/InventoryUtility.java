package wtf.opal.utility.player;

import net.minecraft.block.*;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.EquippableComponent;
import net.minecraft.component.type.ToolComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.*;
import net.minecraft.registry.*;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.EmptyBlockView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static wtf.opal.client.Constants.mc;

public final class InventoryUtility {

    public static final int HOTBAR_SIZE = 9;
    public static final int MAIN_INVENTORY_SIZE = 36;
    public static final int HOTBAR_SCREEN_OFFSET = 36;
    public static final int OFFHAND_SWAP_BUTTON = 40;
    public static final int OFFHAND_SCREEN_SLOT = 45;
    public static final long ACA_MULTIINTERACTION_QUICK_MOVE_DELAY_MS = 135L;
    public static final long ACA_MULTIINTERACTION_PICKUP_DELAY_MS = 285L;
    public static final long ACA_INVENTORY_CLOSE_DELAY_MS = 90L;

    private InventoryUtility() {
    }

    public static int findItemInHotbar(final Item item) {
        if (mc.player == null) {
            return -1;
        }

        return IntStream.range(0, HOTBAR_SIZE)
                .filter(i -> {
                    final ItemStack itemStack = mc.player.getInventory().getMainStacks().get(i);
                    return itemStack.getItem() == item && itemStack.getCount() > 0;
                })
                .findFirst()
                .orElse(-1);
    }

    public static boolean isInventoryFull() {
        return mc.player != null && mc.player.getInventory().getMainStacks().stream().noneMatch(ItemStack::isEmpty);
    }

    public static List<ItemStack> getAllItems() {
        if (mc.player == null) {
            return List.of();
        }

        final List<ItemStack> items = new ArrayList<>(MAIN_INVENTORY_SIZE + 5);
        items.addAll(mc.player.getInventory().getMainStacks());
        items.add(mc.player.getEquippedStack(EquipmentSlot.HEAD));
        items.add(mc.player.getEquippedStack(EquipmentSlot.CHEST));
        items.add(mc.player.getEquippedStack(EquipmentSlot.LEGS));
        items.add(mc.player.getEquippedStack(EquipmentSlot.FEET));
        items.add(mc.player.getOffHandStack());
        return items;
    }

    public static boolean hasServerItem() {
        return getAllItems().stream().anyMatch(InventoryUtility::isServerMenuItem);
    }

    public static boolean isServerMenuItem(final ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) {
            return false;
        }

        final String displayName = itemStack.getName().getString();
        return displayName.contains("长按点击")
                || displayName.contains("点击使用")
                || displayName.contains("离开游戏")
                || displayName.contains("选择一个队伍")
                || displayName.contains("再来一局")
                || displayName.contains("Click")
                || displayName.contains("Right")
                || displayName.contains("Teleport")
                || displayName.contains("使用")
                || displayName.contains("传送")
                || displayName.contains("再来");
    }

    public static boolean hasCustomName(final ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) {
            return false;
        }

        final String stackName = itemStack.getName().getString();
        final String defaultName = itemStack.getItem().getName().getString();
        return !Objects.equals(stackName, defaultName);
    }

    public static boolean isArmor(final ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) {
            return false;
        }

        if (itemStack.getItem() == Items.PLAYER_HEAD || itemStack.getItem() == Items.PUMPKIN || itemStack.getItem() == Items.CARVED_PUMPKIN) {
            return false;
        }

        return itemStack.getComponents().get(DataComponentTypes.EQUIPPABLE) != null;
    }

    public static boolean isFoodItem(final ItemStack itemStack) {
        return itemStack != null
                && !itemStack.isEmpty()
                && itemStack.getItem().getComponents().contains(DataComponentTypes.FOOD)
                && !isGoldenApple(itemStack);
    }

    public static boolean isGoldenApple(final ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) {
            return false;
        }

        return itemStack.getItem() == Items.GOLDEN_APPLE || itemStack.getItem() == Items.ENCHANTED_GOLDEN_APPLE;
    }

    public static boolean isProjectile(final ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) {
            return false;
        }

        return itemStack.getItem() == Items.EGG || itemStack.getItem() == Items.SNOWBALL;
    }

    public static boolean isPlaceableBlock(final ItemStack itemStack) {
        return itemStack != null
                && !itemStack.isEmpty()
                && itemStack.getItem() instanceof BlockItem blockItem
                && isGoodBlock(blockItem.getBlock())
                && isUsable(itemStack);
    }

    public static boolean isUsable(final ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) {
            return true;
        }

        if (isServerMenuItem(itemStack)) {
            return false;
        }

        final Item item = itemStack.getItem();
        if (item == Items.BOOK
                || item == Items.ENCHANTED_BOOK
                || item == Items.EXPERIENCE_BOTTLE
                || item == Items.WHEAT_SEEDS
                || item == Items.BEETROOT_SEEDS
                || item == Items.MELON_SEEDS
                || item == Items.PUMPKIN_SEEDS
                || item == Items.FLINT_AND_STEEL) {
            return false;
        }

        if (item instanceof BlockItem blockItem) {
            final Block block = blockItem.getBlock();
            if ((block instanceof SkullBlock && item != Items.PLAYER_HEAD) || block == Blocks.ENCHANTING_TABLE) {
                return false;
            }
        }

        return true;
    }

    public static boolean isWeaponSpecialItem(final ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) {
            return false;
        }

        final Item item = itemStack.getItem();
        if (item == Items.TOTEM_OF_UNDYING || item == Items.END_CRYSTAL) {
            return true;
        }

        if (item == Items.SLIME_BALL || item == Items.STICK) {
            return calculateEnchantmentLevel(itemStack, Enchantments.KNOCKBACK) > 1;
        }

        return item instanceof AxeItem
                && item == Items.GOLDEN_AXE
                && calculateEnchantmentLevel(itemStack, Enchantments.SHARPNESS) > 100;
    }

    public static boolean isOtherCheatItem(final ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) {
            return false;
        }

        final String displayName = itemStack.getName().getString();
        return displayName.contains("一刀");
    }

    public static boolean isSpecialKeepItem(final ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty() || !isUsable(itemStack)) {
            return false;
        }

        final Item item = itemStack.getItem();
        return isWeaponSpecialItem(itemStack)
                || isOtherCheatItem(itemStack)
                || item == Items.COMPASS
                || item == Items.PLAYER_HEAD
                || item == Items.ENCHANTED_GOLDEN_APPLE
                || item == Items.TOTEM_OF_UNDYING
                || item == Items.MACE
                || item == Items.WIND_CHARGE
                || item == Items.ELYTRA
                || item == Items.FIREWORK_ROCKET
                || isGodAxe(itemStack);
    }

    public static boolean isOpenZenUsefulItem(final ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty() || !isUsable(itemStack)) {
            return false;
        }

        if (isSpecialKeepItem(itemStack)) {
            return true;
        }

        final Item item = itemStack.getItem();
        if (item == Items.COBWEB) {
            return true;
        }

        if (item instanceof BlockItem) {
            return isPlaceableBlock(itemStack);
        }

        return item instanceof EnderPearlItem
                || item instanceof PotionItem
                || item instanceof ShieldItem
                || item instanceof FireChargeItem
                || item instanceof FishingRodItem
                || item instanceof ArrowItem
                || item instanceof BucketItem
                || item instanceof BowItem
                || item instanceof CrossbowItem
                || isProjectile(itemStack)
                || isGoldenApple(itemStack)
                || isFoodItem(itemStack)
                || item == Items.SLIME_BALL
                || item == Items.END_CRYSTAL
                || item == Items.TOTEM_OF_UNDYING;
    }

    public static boolean isGoodItem(final ItemStack itemStack) {
        return isOpenZenUsefulItem(itemStack);
    }

    public static double getSwordDamage(final ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty() || !itemStack.isIn(ItemTags.SWORDS)) {
            return 0.0;
        }

        return PlayerUtility.getStackAttackDamage(itemStack)
                + calculateEnchantmentLevel(itemStack, Enchantments.SHARPNESS) * 1.25
                + calculateEnchantmentLevel(itemStack, Enchantments.FIRE_ASPECT) * 0.1
                - getDurabilityPenalty(itemStack);
    }

    public static double getAxeDamage(final ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty() || !(itemStack.getItem() instanceof AxeItem)) {
            return 0.0;
        }

        return PlayerUtility.getStackAttackDamage(itemStack)
                + calculateEnchantmentLevel(itemStack, Enchantments.SHARPNESS) * 1.25
                - getDurabilityPenalty(itemStack);
    }

    public static double getSwordValue(final ItemStack itemStack) {
        return getSwordDamage(itemStack);
    }

    public static double getArmorValue(final ItemStack itemStack) {
        if (!isArmor(itemStack)) {
            return 0.0;
        }

        double score = PlayerUtility.getArmorProtection(itemStack) * 100.0;
        score += calculateEnchantmentLevel(itemStack, Enchantments.PROTECTION) * 10.0;
        score += calculateEnchantmentLevel(itemStack, Enchantments.BLAST_PROTECTION) * 2.0;
        score += calculateEnchantmentLevel(itemStack, Enchantments.FIRE_PROTECTION) * 2.0;
        score += calculateEnchantmentLevel(itemStack, Enchantments.PROJECTILE_PROTECTION) * 2.0;
        score += calculateEnchantmentLevel(itemStack, Enchantments.THORNS);
        score += calculateEnchantmentLevel(itemStack, Enchantments.UNBREAKING) * 0.5;
        score -= getDurabilityPenalty(itemStack);
        return score;
    }

    public static double getToolValue(final ItemStack itemStack) {
        final ToolComponent toolComponent = itemStack != null ? itemStack.get(DataComponentTypes.TOOL) : null;
        if (toolComponent == null) {
            return 0.0;
        }

        return getDigSpeed(itemStack);
    }

    public static double getDigSpeed(final ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) {
            return 0.0;
        }

        double score = 0.0;
        if (itemStack.isIn(ItemTags.PICKAXES)) {
            score += itemStack.getMiningSpeedMultiplier(Blocks.STONE.getDefaultState());
        } else if (itemStack.getItem() instanceof AxeItem) {
            score += itemStack.getMiningSpeedMultiplier(Blocks.OAK_LOG.getDefaultState());
        } else if (itemStack.getItem() instanceof ShovelItem) {
            score += itemStack.getMiningSpeedMultiplier(Blocks.DIRT.getDefaultState());
        } else {
            return 0.0;
        }

        score += calculateEnchantmentLevel(itemStack, Enchantments.EFFICIENCY) * 0.75;
        score += calculateEnchantmentLevel(itemStack, Enchantments.UNBREAKING) * 0.1;
        score -= getDurabilityPenalty(itemStack);
        return score;
    }

    public static double getBowScore(final ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty() || !(itemStack.getItem() instanceof BowItem)) {
            return 0.0;
        }

        double score = 10.0;
        score += calculateEnchantmentLevel(itemStack, Enchantments.PUNCH) * 1.0;
        score += calculateEnchantmentLevel(itemStack, Enchantments.INFINITY) * 1.0;
        score += calculateEnchantmentLevel(itemStack, Enchantments.FLAME) * 1.0;
        score += calculateEnchantmentLevel(itemStack, Enchantments.POWER) * 0.1;
        score -= getDurabilityPenalty(itemStack);
        return score;
    }

    public static double getBowScoreAlt(final ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty() || !(itemStack.getItem() instanceof BowItem)) {
            return 0.0;
        }

        double score = 10.0;
        score += calculateEnchantmentLevel(itemStack, Enchantments.PUNCH) * 0.1;
        score += calculateEnchantmentLevel(itemStack, Enchantments.INFINITY) * 1.0;
        score += calculateEnchantmentLevel(itemStack, Enchantments.FLAME) * 1.0;
        score += calculateEnchantmentLevel(itemStack, Enchantments.POWER) * 1.0;
        score -= getDurabilityPenalty(itemStack);
        return score;
    }

    public static double getCrossbowScore(final ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty() || !(itemStack.getItem() instanceof CrossbowItem)) {
            return 0.0;
        }

        double score = 0.0;
        score += calculateEnchantmentLevel(itemStack, Enchantments.QUICK_CHARGE);
        score += calculateEnchantmentLevel(itemStack, Enchantments.MULTISHOT);
        score += calculateEnchantmentLevel(itemStack, Enchantments.PIERCING);
        score -= getDurabilityPenalty(itemStack);
        return score;
    }

    public static boolean isLegitAxe(final ItemStack itemStack) {
        return itemStack != null
                && !itemStack.isEmpty()
                && itemStack.getItem() instanceof AxeItem
                && itemStack.getItem() != Items.NETHERITE_AXE
                && calculateEnchantmentLevel(itemStack, Enchantments.SHARPNESS) >= 8
                && calculateEnchantmentLevel(itemStack, Enchantments.SHARPNESS) <= 10;
    }

    public static boolean isGodAxe(final ItemStack itemStack) {
        return itemStack != null
                && !itemStack.isEmpty()
                && itemStack.getItem() instanceof AxeItem
                && calculateEnchantmentLevel(itemStack, Enchantments.SHARPNESS) > 10;
    }

    public static int countItem(final Item item) {
        return countItem(stack -> stack.getItem() == item);
    }

    public static int countItem(final Predicate<ItemStack> predicate) {
        return getAllItems().stream()
                .filter(stack -> !stack.isEmpty())
                .filter(InventoryUtility::isUsable)
                .filter(predicate)
                .mapToInt(ItemStack::getCount)
                .sum();
    }

    public static ItemStack getLargestStack(final Item item) {
        return getAllItems().stream()
                .filter(stack -> !stack.isEmpty() && stack.getItem() == item && isUsable(stack))
                .max(Comparator.comparingInt(ItemStack::getCount))
                .orElse(null);
    }

    public static ItemStack getSmallestStack(final Predicate<ItemStack> predicate) {
        return getAllItems().stream()
                .filter(stack -> !stack.isEmpty())
                .filter(InventoryUtility::isUsable)
                .filter(predicate)
                .min(Comparator.comparingInt(ItemStack::getCount))
                .orElse(null);
    }

    public static ItemStack getBestSword() {
        return getAllItems().stream()
                .filter(stack -> !stack.isEmpty() && stack.isIn(ItemTags.SWORDS) && isUsable(stack))
                .max(Comparator.comparingDouble(InventoryUtility::getSwordDamage))
                .orElse(null);
    }

    public static ItemStack getBestSharpAxe() {
        return getAllItems().stream()
                .filter(stack -> !stack.isEmpty() && stack.getItem() instanceof AxeItem && isLegitAxe(stack) && isUsable(stack))
                .max(Comparator.comparingDouble(InventoryUtility::getAxeDamage))
                .orElse(null);
    }

    public static ItemStack getBestPickaxe() {
        return getAllItems().stream()
                .filter(stack -> !stack.isEmpty() && stack.isIn(ItemTags.PICKAXES) && isUsable(stack))
                .max(Comparator.comparingDouble(InventoryUtility::getDigSpeed))
                .orElse(null);
    }

    public static ItemStack getBestAxe() {
        return getAllItems().stream()
                .filter(stack -> !stack.isEmpty()
                        && stack.getItem() instanceof AxeItem
                        && !isLegitAxe(stack)
                        && !isGodAxe(stack)
                        && isUsable(stack))
                .max(Comparator.comparingDouble(InventoryUtility::getDigSpeed))
                .orElse(null);
    }

    public static ItemStack getBestShovel() {
        return getAllItems().stream()
                .filter(stack -> !stack.isEmpty() && stack.getItem() instanceof ShovelItem && isUsable(stack))
                .max(Comparator.comparingDouble(InventoryUtility::getDigSpeed))
                .orElse(null);
    }

    public static ItemStack getBestBow() {
        return getAllItems().stream()
                .filter(stack -> !stack.isEmpty() && stack.getItem() instanceof BowItem && isUsable(stack))
                .max(Comparator.comparingDouble(InventoryUtility::getBowScore))
                .orElse(null);
    }

    public static ItemStack getBestBowAlt() {
        return getAllItems().stream()
                .filter(stack -> !stack.isEmpty() && stack.getItem() instanceof BowItem && isUsable(stack))
                .max(Comparator.comparingDouble(InventoryUtility::getBowScoreAlt))
                .orElse(null);
    }

    public static ItemStack getBestCrossbow() {
        return getAllItems().stream()
                .filter(stack -> !stack.isEmpty() && stack.getItem() instanceof CrossbowItem && isUsable(stack))
                .max(Comparator.comparingDouble(InventoryUtility::getCrossbowScore))
                .orElse(null);
    }

    public static ItemStack getBestProjectile() {
        return getAllItems().stream()
                .filter(stack -> !stack.isEmpty() && isProjectile(stack) && isUsable(stack))
                .max(Comparator.comparingInt(ItemStack::getCount))
                .orElse(null);
    }

    public static ItemStack getWorstProjectile() {
        return getAllItems().stream()
                .filter(stack -> !stack.isEmpty() && isProjectile(stack) && isUsable(stack))
                .min(Comparator.comparingInt(ItemStack::getCount))
                .orElse(null);
    }

    public static ItemStack getBestBlock() {
        return getAllItems().stream()
                .filter(InventoryUtility::isPlaceableBlock)
                .max(Comparator.comparingInt(ItemStack::getCount))
                .orElse(null);
    }

    public static ItemStack getWorstBlock() {
        return getAllItems().stream()
                .filter(InventoryUtility::isPlaceableBlock)
                .min(Comparator.comparingInt(ItemStack::getCount))
                .orElse(null);
    }

    public static ItemStack getBestFoodStack() {
        return getAllItems().stream()
                .filter(InventoryUtility::isFoodItem)
                .filter(InventoryUtility::isUsable)
                .min(Comparator.comparingInt(ItemStack::getCount))
                .orElse(null);
    }

    public static ItemStack getFishingRodStack() {
        return getAllItems().stream()
                .filter(stack -> !stack.isEmpty() && stack.getItem() instanceof FishingRodItem && isUsable(stack))
                .findFirst()
                .orElse(null);
    }

    public static ItemStack getArrowStack() {
        return getAllItems().stream()
                .filter(stack -> !stack.isEmpty() && stack.getItem() instanceof ArrowItem && isUsable(stack))
                .min(Comparator.comparingInt(ItemStack::getCount))
                .orElse(null);
    }

    public static int getSlot(final ItemStack stack) {
        if (mc.player == null || stack == null || stack.isEmpty()) {
            return -1;
        }

        for (int i = 0; i < MAIN_INVENTORY_SIZE; i++) {
            if (mc.player.getInventory().getStack(i) == stack) {
                return i;
            }
        }

        return -1;
    }

    public static int getSlot(final Item item) {
        if (mc.player == null) {
            return -1;
        }

        for (int i = 0; i < MAIN_INVENTORY_SIZE; i++) {
            final ItemStack itemStack = mc.player.getInventory().getStack(i);
            if (!itemStack.isEmpty() && itemStack.getItem() == item) {
                return i;
            }
        }

        return -1;
    }

    public static int getScreenSlot(final int inventorySlot) {
        if (inventorySlot < 0 || inventorySlot >= MAIN_INVENTORY_SIZE) {
            return -1;
        }

        return inventorySlot < HOTBAR_SIZE ? HOTBAR_SCREEN_OFFSET + inventorySlot : inventorySlot;
    }

    public static int getArmorScreenSlot(final EquipmentSlot equipmentSlot) {
        return switch (equipmentSlot) {
            case HEAD -> 5;
            case CHEST -> 6;
            case LEGS -> 7;
            case FEET -> 8;
            default -> -1;
        };
    }

    public static Slot getBestArmorSlot(final ScreenHandler screenHandler, final EquipmentSlot equipmentSlot) {
        return filterSlots(screenHandler, slot -> {
            if (slot.getStack().isEmpty() || !isArmor(slot.getStack())) {
                return false;
            }

            final EquippableComponent equippable = slot.getStack().getComponents().get(DataComponentTypes.EQUIPPABLE);
            return equippable != null && equippable.slot() == equipmentSlot;
        }, false).stream().max(Comparator.comparingDouble(slot -> getArmorValue(slot.getStack()))).orElse(null);
    }

    public static List<Slot> filterSlots(final ScreenHandler screenHandler, final Predicate<Slot> filterCondition, final boolean shuffle) {
        final List<Slot> filteredSlots = screenHandler.slots.stream().filter(filterCondition).collect(Collectors.toList());

        if (shuffle) {
            Collections.shuffle(filteredSlots);
        }

        return filteredSlots;
    }

    public static void drop(final ScreenHandler screenHandler, final int slot) {
        mc.interactionManager.clickSlot(screenHandler.syncId, slot, 1, SlotActionType.THROW, mc.player);
    }

    public static void shiftClick(final ScreenHandler screenHandler, final int slot, final int mouseButton) {
        mc.interactionManager.clickSlot(screenHandler.syncId, slot, mouseButton, SlotActionType.QUICK_MOVE, mc.player);
    }

    public static void pickup(final ScreenHandler screenHandler, final int slot) {
        mc.interactionManager.clickSlot(screenHandler.syncId, slot, 0, SlotActionType.PICKUP, mc.player);
    }

    public static void swap(final ScreenHandler screenHandler, final int originalSlot, final int newSlot) {
        mc.interactionManager.clickSlot(screenHandler.syncId, originalSlot, newSlot, SlotActionType.SWAP, mc.player);
    }

    public static void swapInventorySlotToHotbar(final ScreenHandler screenHandler, final int inventorySlot, final int hotbarSlot) {
        final int screenSlot = getScreenSlot(inventorySlot);
        if (screenSlot != -1) {
            swap(screenHandler, screenSlot, hotbarSlot);
        }
    }

    public static void moveToOffhand(final ScreenHandler screenHandler, final int inventorySlot) {
        final int screenSlot = getScreenSlot(inventorySlot);
        if (screenSlot != -1) {
            swap(screenHandler, screenSlot, OFFHAND_SWAP_BUTTON);
        }
    }

    public static long withAcaQuickMoveDelay(final long delay) {
        return Math.max(delay, ACA_MULTIINTERACTION_QUICK_MOVE_DELAY_MS);
    }

    public static long withAcaPickupDelay(final long delay) {
        return Math.max(delay, ACA_MULTIINTERACTION_PICKUP_DELAY_MS);
    }

    public static int calculateEnchantmentLevel(final ItemStack itemStack, final RegistryKey<Enchantment> enchantment) {
        if (mc.world == null || itemStack == null || itemStack.isEmpty()) {
            return 0;
        }

        final DynamicRegistryManager drm = mc.world.getRegistryManager();
        final RegistryWrapper.Impl<Enchantment> registryWrapper = drm.getOrThrow(RegistryKeys.ENCHANTMENT);
        return EnchantmentHelper.getLevel(registryWrapper.getOrThrow(enchantment), itemStack);
    }

    public static boolean isGoodBlock(final Block block) {
        return block != null
                && mc.player != null
                && !isBlockInteractable(block)
                && block.getDefaultState().getOutlineShape(EmptyBlockView.INSTANCE, mc.player.getBlockPos(), ShapeContext.of(mc.player)) == VoxelShapes.fullCube()
                && !(block instanceof TntBlock)
                && !(block instanceof FallingBlock)
                && !(block instanceof CobwebBlock)
                && block != Blocks.ENCHANTING_TABLE;
    }

    public static boolean isBlockInteractable(final Block block) {
        return block != null && INTERACTABLE_BLOCKS.contains(block);
    }

    private static double getDurabilityPenalty(final ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty() || itemStack.getMaxDamage() <= 0) {
            return 0.0;
        }

        return (itemStack.getDamage() / (double) itemStack.getMaxDamage()) * 0.1;
    }

    private static final List<Block> INTERACTABLE_BLOCKS = Registries.BLOCK.stream()
            .filter(block ->
                    block instanceof TrapdoorBlock
                            || block instanceof SweetBerryBushBlock
                            || block instanceof AbstractFurnaceBlock
                            || block instanceof AbstractSignBlock
                            || block instanceof AnvilBlock
                            || block instanceof BarrelBlock
                            || block instanceof BeaconBlock
                            || block instanceof BedBlock
                            || block instanceof BellBlock
                            || block instanceof BrewingStandBlock
                            || block instanceof ButtonBlock
                            || block instanceof CakeBlock
                            || block instanceof CandleCakeBlock
                            || block instanceof CartographyTableBlock
                            || block instanceof CaveVinesBodyBlock
                            || block instanceof CaveVinesHeadBlock
                            || block instanceof ChestBlock
                            || block instanceof ChiseledBookshelfBlock
                            || block instanceof CommandBlock
                            || block instanceof ComparatorBlock
                            || block instanceof ComposterBlock
                            || block instanceof CraftingTableBlock
                            || block instanceof DaylightDetectorBlock
                            || block instanceof DecoratedPotBlock
                            || block instanceof DispenserBlock
                            || block instanceof DoorBlock
                            || block instanceof DragonEggBlock
                            || block instanceof EnchantingTableBlock
                            || block instanceof EnderChestBlock
                            || block instanceof FenceBlock
                            || block instanceof FenceGateBlock
                            || block instanceof FlowerPotBlock
                            || block instanceof GrindstoneBlock
                            || block instanceof HopperBlock
                            || block instanceof JigsawBlock
                            || block instanceof JukeboxBlock
                            || block instanceof LecternBlock
                            || block instanceof LeverBlock
                            || block instanceof LightBlock
                            || block instanceof LoomBlock
                            || block instanceof NoteBlock
                            || block instanceof PistonExtensionBlock
                            || block instanceof RedstoneWireBlock
                            || block instanceof RepeaterBlock
                            || block instanceof RespawnAnchorBlock
                            || block instanceof ShulkerBoxBlock
                            || block instanceof SmithingTableBlock
                            || block instanceof StonecutterBlock
                            || block instanceof FlowerBlock
                            || block instanceof StructureBlock
                            || block instanceof SlimeBlock
                            || block instanceof CobwebBlock)
            .toList();

}

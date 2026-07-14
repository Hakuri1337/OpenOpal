package wtf.opal.utility.player;

import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Identifies MiniBlox armor translated into ordinary client items by its display name.
 */
public final class MinibloxArmorUtility {
    private MinibloxArmorUtility() {
    }

    public record ArmorInfo(EquipmentSlot slot, int tier) {
    }

    public static ArmorInfo identify(final ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }

        final String name = normalize(stack.getName().getString());
        final EquipmentSlot slot = findSlot(name);
        final int tier = findTier(name);
        return slot != null && tier > 0 ? new ArmorInfo(slot, tier) : null;
    }

    public static int getTier(final ItemStack stack, final EquipmentSlot expectedSlot) {
        final ArmorInfo armor = identify(stack);
        return armor != null && armor.slot() == expectedSlot ? armor.tier() : 0;
    }

    public static Slot getBestArmorSlot(final ScreenHandler screenHandler, final EquipmentSlot equipmentSlot) {
        if (screenHandler == null) {
            return null;
        }

        Slot bestSlot = null;
        int bestTier = 0;
        for (final Slot slot : screenHandler.slots) {
            final int tier = getTier(slot.getStack(), equipmentSlot);
            if (tier > bestTier) {
                bestTier = tier;
                bestSlot = slot;
            }
        }
        return bestSlot;
    }

    private static EquipmentSlot findSlot(final String name) {
        if (containsAny(name, "helmet", "head", "cap", "头盔", "头甲")) {
            return EquipmentSlot.HEAD;
        }
        if (containsAny(name, "chestplate", "chestarmor", "chest", "胸甲", "护胸", "胸部")) {
            return EquipmentSlot.CHEST;
        }
        if (containsAny(name, "leggings", "legarmor", "legs", "护腿", "腿甲")) {
            return EquipmentSlot.LEGS;
        }
        if (containsAny(name, "boots", "shoes", "feet", "靴子", "鞋子")) {
            return EquipmentSlot.FEET;
        }
        return null;
    }

    private static int findTier(final String name) {
        if (containsAny(name, "diamond", "钻石")) {
            return 3;
        }
        if (containsAny(name, "iron", "铁")) {
            return 2;
        }
        if (containsAny(name, "golden", "gold", "黄金", "金")) {
            return 1;
        }
        return 0;
    }

    private static boolean containsAny(final String value, final String... terms) {
        for (final String term : terms) {
            if (value.contains(term)) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(final String value) {
        if (value == null || value.isBlank()) {
            return "";
        }

        final String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        final StringBuilder result = new StringBuilder(normalized.length());
        for (int index = 0; index < normalized.length(); index++) {
            final char character = normalized.charAt(index);
            if (character == '\u00A7') {
                index++;
                continue;
            }
            if (Character.isLetterOrDigit(character)) {
                result.append(character);
            }
        }
        return result.toString();
    }
}

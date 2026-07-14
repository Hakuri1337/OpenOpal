package wtf.opal.client.feature.module.impl.utility.inventory.manager;

import wtf.opal.client.feature.module.impl.utility.inventory.AcaInventoryActionScheduler;
import wtf.opal.client.feature.module.property.impl.GroupProperty;
import wtf.opal.client.feature.module.property.impl.bool.BooleanProperty;
import wtf.opal.client.feature.module.property.impl.mode.ModeProperty;
import wtf.opal.client.feature.module.property.impl.number.NumberProperty;

public final class InventoryManagerSettings {

    public enum InventoryRules {
        VANILLA("Vanilla"),
        MINIBLOX("MiniBlox");

        private final String name;

        InventoryRules(final String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    public enum OffhandMode {
        GOLDEN_APPLE,
        PROJECTILE,
        FISHING_ROD,
        BLOCK,
        NONE
    }

    public enum BowPriority {
        CROSSBOW,
        PUNCH_BOW
    }

    private final BooleanProperty autoArmor;
    private final BooleanProperty throwItems;
    private final BooleanProperty inventoryOnly;
    private final BooleanProperty fastThrow;

    private final ModeProperty<AcaInventoryActionScheduler.TimingMode> timingMode;
    private final ModeProperty<InventoryRules> inventoryRules;
    private final ModeProperty<OffhandMode> offhandMode;
    private final ModeProperty<BowPriority> bowPriority;

    private final NumberProperty maxEggsSnowballsSize;
    private final NumberProperty maxBlockSize;
    private final NumberProperty maxFoodSize;
    private final NumberProperty maxRodSize;

    private final NumberProperty swordSlot;
    private final NumberProperty blockSlot;
    private final NumberProperty axeSlot;
    private final NumberProperty pickaxeSlot;
    private final NumberProperty bowSlot;
    private final NumberProperty waterBucketSlot;
    private final NumberProperty pearlSlot;
    private final NumberProperty goldenAppleSlot;
    private final NumberProperty eggsSnowballsSlot;
    private final NumberProperty slimeBallSlot;
    private final NumberProperty crystalSlot;

    public InventoryManagerSettings(final InventoryManagerModule module) {
        this.autoArmor = new BooleanProperty("Auto Armor", true);
        this.throwItems = new BooleanProperty("Throw Items", true);
        this.inventoryOnly = new BooleanProperty("Inventory Only", true);
        this.fastThrow = new BooleanProperty("Fast Throw", false).hideIf(() -> !throwItems.getValue());

        this.timingMode = new ModeProperty<>("Timing", AcaInventoryActionScheduler.TimingMode.ACA);
        this.inventoryRules = new ModeProperty<>("Inventory Rules", InventoryRules.VANILLA);
        this.offhandMode = new ModeProperty<>("Offhand Items", OffhandMode.PROJECTILE);
        this.bowPriority = new ModeProperty<>("Bow Priority", BowPriority.CROSSBOW);

        this.maxEggsSnowballsSize = new NumberProperty("Max Eggs & Snowballs Size", 64, 16, 256, 16);
        this.maxBlockSize = new NumberProperty("Max Block Size", 256, 64, 512, 64);
        this.maxFoodSize = new NumberProperty("Max Food Size", 128, 32, 256, 32);
        this.maxRodSize = new NumberProperty("Max Rod Size", 1, 1, 16, 1);

        this.swordSlot = new NumberProperty("Sword Slot", 1, 0, 9, 1);
        this.blockSlot = new NumberProperty("Block Slot", 4, 0, 9, 1);
        this.axeSlot = new NumberProperty("Axe Slot", 3, 0, 9, 1);
        this.pickaxeSlot = new NumberProperty("Pickaxe Slot", 2, 0, 9, 1);
        this.bowSlot = new NumberProperty("Bow Slot", 0, 0, 9, 1);
        this.waterBucketSlot = new NumberProperty("Water Bucket Slot", 0, 0, 9, 1);
        this.pearlSlot = new NumberProperty("Ender Pearl Slot", 0, 0, 9, 1);
        this.goldenAppleSlot = new NumberProperty("Golden Apple Slot", 0, 0, 9, 1).hideIf(() -> offhandMode.is(OffhandMode.GOLDEN_APPLE));
        this.eggsSnowballsSlot = new NumberProperty("Eggs & Snowballs Slot", 0, 0, 9, 1).hideIf(() -> offhandMode.is(OffhandMode.PROJECTILE));
        this.slimeBallSlot = new NumberProperty("Slime Ball Slot", 0, 0, 9, 1);
        this.crystalSlot = new NumberProperty("Crystal Slot", 0, 0, 9, 1);

        module.addProperties(
                new GroupProperty("Timing", timingMode),
                new GroupProperty("General", inventoryRules, autoArmor, throwItems, inventoryOnly, fastThrow, offhandMode, bowPriority),
                new GroupProperty("Limits", maxEggsSnowballsSize, maxBlockSize, maxFoodSize, maxRodSize),
                new GroupProperty("Slots",
                        swordSlot,
                        blockSlot,
                        axeSlot,
                        pickaxeSlot,
                        bowSlot,
                        waterBucketSlot,
                        pearlSlot,
                        goldenAppleSlot,
                        eggsSnowballsSlot,
                        slimeBallSlot,
                        crystalSlot
                )
        );
    }

    public AcaInventoryActionScheduler.TimingMode getTimingMode() {
        return this.timingMode.getValue();
    }

    public boolean isAutoArmorEnabled() {
        return autoArmor.getValue();
    }

    public boolean isThrowItemsEnabled() {
        return throwItems.getValue();
    }

    public boolean isInventoryOnlyEnabled() {
        return inventoryOnly.getValue();
    }

    public boolean isFastThrowEnabled() {
        return fastThrow.getValue();
    }

    public boolean isMinibloxMode() {
        return inventoryRules.getValue() == InventoryRules.MINIBLOX;
    }

    public OffhandMode getOffhandMode() {
        return offhandMode.getValue();
    }

    public BowPriority getBowPriority() {
        return bowPriority.getValue();
    }

    public int getMaxEggsSnowballsSize() {
        return maxEggsSnowballsSize.getValue().intValue();
    }

    public int getMaxBlockSize() {
        return maxBlockSize.getValue().intValue();
    }

    public int getMaxFoodSize() {
        return maxFoodSize.getValue().intValue();
    }

    public int getMaxRodSize() {
        return maxRodSize.getValue().intValue();
    }

    public int getSwordSlot() {
        return swordSlot.getValue().intValue();
    }

    public int getBlockSlot() {
        return blockSlot.getValue().intValue();
    }

    public int getAxeSlot() {
        return axeSlot.getValue().intValue();
    }

    public int getPickaxeSlot() {
        return pickaxeSlot.getValue().intValue();
    }

    public int getBowSlot() {
        return bowSlot.getValue().intValue();
    }

    public int getWaterBucketSlot() {
        return waterBucketSlot.getValue().intValue();
    }

    public int getPearlSlot() {
        return pearlSlot.getValue().intValue();
    }

    public int getGoldenAppleSlot() {
        return goldenAppleSlot.getValue().intValue();
    }

    public int getEggsSnowballsSlot() {
        return eggsSnowballsSlot.getValue().intValue();
    }

    public int getSlimeBallSlot() {
        return slimeBallSlot.getValue().intValue();
    }

    public int getCrystalSlot() {
        return crystalSlot.getValue().intValue();
    }
}

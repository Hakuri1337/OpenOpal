package wtf.opal.client.feature.module.impl.utility.inventory;

import wtf.opal.client.OpalClient;
import wtf.opal.client.feature.module.Module;
import wtf.opal.client.feature.module.ModuleCategory;
import wtf.opal.client.feature.module.impl.utility.inventory.manager.InventoryManagerModule;
import wtf.opal.client.feature.module.property.impl.mode.ModeProperty;
import wtf.opal.event.impl.game.PostGameTickEvent;
import wtf.opal.event.impl.game.inventory.ManualInventoryInteractionEvent;
import wtf.opal.event.subscriber.Subscribe;

import static wtf.opal.client.Constants.mc;

public final class AutoArmorModule extends Module {

    private final ModeProperty<AcaInventoryActionScheduler.TimingMode> timingMode =
            new ModeProperty<>("Timing", AcaInventoryActionScheduler.TimingMode.ACA);

    public AutoArmorModule() {
        super("Auto Armor", "Delegates armor handling to Inventory Manager.", ModuleCategory.UTILITY);
        addProperties(timingMode);
    }

    @Subscribe
    public void onPostGameTickEvent(final PostGameTickEvent event) {
        if (mc.player == null) {
            return;
        }

        final InventoryManagerModule inventoryManagerModule = OpalClient.getInstance()
                .getModuleRepository()
                .getModule(InventoryManagerModule.class);

        if (inventoryManagerModule.isEnabled()) {
            return;
        }

        inventoryManagerModule.runAutoArmorOnly(this.timingMode.getValue());
    }

    @Subscribe
    public void onManualInventoryInteraction(final ManualInventoryInteractionEvent event) {
        if (mc.player == null || event.syncId() != mc.player.playerScreenHandler.syncId) {
            return;
        }
        AcaInventoryActionScheduler.getInstance().pauseForManualInput(
                AcaInventoryActionScheduler.Owner.INVENTORY_MANAGER,
                mc.player.age
        );
    }

    @Override
    protected void onDisable() {
        final InventoryManagerModule inventoryManagerModule = OpalClient.getInstance()
                .getModuleRepository()
                .getModule(InventoryManagerModule.class);
        inventoryManagerModule.stopAutoArmorOnlySession();
        super.onDisable();
    }
}

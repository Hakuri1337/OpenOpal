package wtf.opal.client.feature.module.impl.world;

import wtf.opal.client.OpalClient;
import wtf.opal.client.feature.module.Module;
import wtf.opal.client.feature.module.ModuleCategory;
import wtf.opal.client.feature.module.impl.world.scaffold.ScaffoldModule;
import wtf.opal.client.feature.module.impl.world.scaffold.ScaffoldSettings;
import wtf.opal.event.impl.game.PreGameTickEvent;
import wtf.opal.event.subscriber.Subscribe;

public final class SilenceTellyModule extends Module {
    private boolean previousScaffoldEnabled;
    private boolean previousScaffoldVisible;
    private int previousScaffoldModeOrdinal;
    private boolean suppressRestore;

    public SilenceTellyModule() {
        super("SilenceTelly", "Runs Scaffold's SilenceTelly mode as a standalone module.", ModuleCategory.WORLD);
    }

    @Override
    protected void onEnable() {
        final ScaffoldModule scaffold = this.getScaffold();
        this.previousScaffoldEnabled = scaffold.isEnabled();
        this.previousScaffoldVisible = scaffold.isVisible();
        this.previousScaffoldModeOrdinal = scaffold.getSettings().getMode().getValue().ordinal();

        scaffold.setVisible(false);
        scaffold.getSettings().getMode().setValueOrdinal(ScaffoldSettings.Mode.SILENCE_TELLY.ordinal());
        if (!scaffold.isEnabled()) {
            scaffold.setEnabled(true);
        }
        super.onEnable();
    }

    @Override
    protected void onDisable() {
        final ScaffoldModule scaffold = this.getScaffold();
        scaffold.setVisible(this.previousScaffoldVisible);

        if (!this.suppressRestore) {
            if (this.previousScaffoldModeOrdinal >= 0 && this.previousScaffoldModeOrdinal < ScaffoldSettings.Mode.values().length) {
                scaffold.getSettings().getMode().setValueOrdinal(this.previousScaffoldModeOrdinal);
            }
            if (scaffold.isEnabled() != this.previousScaffoldEnabled) {
                scaffold.setEnabled(this.previousScaffoldEnabled);
            }
        }

        super.onDisable();
    }

    @Subscribe
    public void onPreGameTick(final PreGameTickEvent event) {
        final ScaffoldModule scaffold = this.getScaffold();
        if (!scaffold.isEnabled() || !scaffold.isSilenceTellyMode()) {
            this.suppressRestore = true;
            this.setEnabled(false);
            this.suppressRestore = false;
        }
    }

    @Override
    public String getSuffix() {
        return "SSNG";
    }

    private ScaffoldModule getScaffold() {
        return OpalClient.getInstance().getModuleRepository().getModule(ScaffoldModule.class);
    }
}

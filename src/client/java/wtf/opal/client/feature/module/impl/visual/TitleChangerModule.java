package wtf.opal.client.feature.module.impl.visual;

import wtf.opal.client.feature.module.Module;
import wtf.opal.client.feature.module.ModuleCategory;
import wtf.opal.client.feature.module.property.impl.StringProperty;
import wtf.opal.event.impl.game.PreGameTickEvent;
import wtf.opal.event.subscriber.Subscribe;

import static wtf.opal.client.Constants.mc;

public final class TitleChangerModule extends Module {
    private final StringProperty title = new StringProperty("Title", "OpenOpal");

    public TitleChangerModule() {
        super("TitleChanger", "Changes the Minecraft window title.", ModuleCategory.VISUAL);
        this.addProperties(title);
    }

    @Subscribe
    public void onPreGameTick(final PreGameTickEvent event) {
        this.applyTitle();
    }

    @Override
    protected void onEnable() {
        this.applyTitle();
        super.onEnable();
    }

    @Override
    protected void onDisable() {
        mc.updateWindowTitle();
        super.onDisable();
    }

    private void applyTitle() {
        final String value = title.getValue() == null ? "" : title.getValue().trim();
        mc.getWindow().setTitle(value.isEmpty() ? "OpenOpal" : value);
    }
}

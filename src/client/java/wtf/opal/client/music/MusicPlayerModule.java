package wtf.opal.client.music;

import wtf.opal.client.OpalClient;
import wtf.opal.client.feature.module.Module;
import wtf.opal.client.feature.module.ModuleCategory;
import wtf.opal.client.feature.module.property.impl.bool.BooleanProperty;
import wtf.opal.client.music.screen.MusicScreen;

import static wtf.opal.client.Constants.mc;

public final class MusicPlayerModule extends Module {
    private final MusicScreen screen;
    private final BooleanProperty islandLyrics = new BooleanProperty("Island Lyrics", true);

    public MusicPlayerModule() {
        super("Music Player", "Opens the built-in NetEase music player.", ModuleCategory.UTILITY);
        this.screen = new MusicScreen(OpalClient.getInstance().getMusicService());
        this.addProperties(islandLyrics);
    }

    @Override
    protected void onEnable() {
        mc.setScreen(screen);
    }

    @Override
    protected void onDisable() {
        if (mc.currentScreen == screen) mc.setScreen(null);
    }

    public boolean isIslandLyricsEnabled() {
        return islandLyrics.getValue();
    }
}

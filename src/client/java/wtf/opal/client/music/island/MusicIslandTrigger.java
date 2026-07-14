package wtf.opal.client.music.island;

import com.ibm.icu.impl.Pair;
import net.minecraft.client.gui.DrawContext;
import wtf.opal.client.music.MusicService;
import wtf.opal.client.music.MusicPlayerModule;
import wtf.opal.client.OpalClient;
import wtf.opal.client.music.model.LyricTimeline;
import wtf.opal.client.music.playback.PlaybackSnapshot;
import wtf.opal.client.music.playback.PlaybackState;
import wtf.opal.client.feature.module.impl.visual.overlay.impl.dynamicisland.IslandTrigger;
import wtf.opal.client.renderer.NVGRenderer;
import wtf.opal.client.renderer.image.NVGImageRenderer;
import wtf.opal.client.renderer.repository.FontRepository;
import wtf.opal.client.renderer.repository.ImageRepository;
import wtf.opal.client.renderer.text.NVGTextRenderer;
import wtf.opal.utility.render.ColorUtility;

public final class MusicIslandTrigger implements IslandTrigger {
    private static final float MIN_WIDTH = 205;
    private static final float MAX_WIDTH = 260;

    private final MusicService service;

    public MusicIslandTrigger(final MusicService service) {
        this.service = service;
    }

    @Override
    public void renderIsland(
            final DrawContext context,
            final float posX,
            final float posY,
            final float width,
            final float height,
            final float progress
    ) {
        final PlaybackSnapshot snapshot = service.getSnapshot();
        if (snapshot.song() == null) return;

        final NVGTextRenderer titleFont = FontRepository.getFont("productsans-semibold");
        final NVGTextRenderer textFont = FontRepository.getFont("productsans-regular");
        final NVGTextRenderer iconFont = FontRepository.getFont("materialicons-regular");
        final Pair<Integer, Integer> colors = ColorUtility.getClientTheme();

        final float coverX = posX + 7;
        final float coverY = posY + 7;
        final float coverSize = 24;
        final NVGImageRenderer artwork = service.getCurrentArtworkPath() == null
                ? null : ImageRepository.getImage(service.getCurrentArtworkPath(), 0);
        if (artwork != null) {
            artwork.drawRoundedImage(coverX, coverY, coverSize, coverSize, 4);
        } else {
            NVGRenderer.roundedRectGradient(coverX, coverY, coverSize, coverSize, 4, colors.first, colors.second, 45);
            iconFont.drawString(stateIcon(snapshot.state()), coverX + 5.5F, coverY + 17.5F, 13, 0xFFFFFFFF);
        }

        final float textX = coverX + coverSize + 7;
        final float textWidth = Math.max(20, width - (textX - posX) - 51);
        titleFont.drawString(ellipsize(titleFont, snapshot.song().name(), textWidth, 8.5F), textX, posY + 14.5F, 8.5F, 0xFFFFFFFF);
        final LyricTimeline.Line lyric = service.getCurrentLyricLine(snapshot.positionMillis());
        final MusicPlayerModule module = OpalClient.getInstance().getModuleRepository().getModule(MusicPlayerModule.class);
        final String secondary = module != null && module.isIslandLyricsEnabled() && lyric != null && !lyric.text().isBlank()
                ? lyric.text() : snapshot.song().singer();
        textFont.drawString(ellipsize(textFont, secondary, textWidth, 6.7F), textX, posY + 24.5F, 6.7F, 0xFFAAAAAA);

        final String time = formatTime(snapshot.positionMillis()) + "/" + formatTime(snapshot.durationMillis());
        textFont.drawString(time, posX + width - 43, posY + 15F, 6.3F, 0xFFD0D0D0);

        final float trackX = textX;
        final float trackY = posY + height - 6;
        final float trackWidth = width - (trackX - posX) - 10;
        NVGRenderer.roundedRect(trackX, trackY, trackWidth, 1.5F, 0.75F, 0x334F4F4F);
        final float ratio = snapshot.durationMillis() <= 0 ? 0
                : Math.clamp((float) snapshot.positionMillis() / snapshot.durationMillis(), 0, 1);
        if (ratio > 0) {
            NVGRenderer.roundedRectGradient(trackX, trackY, trackWidth * ratio, 1.5F, 0.75F, colors.first, colors.second, 0);
        }
    }

    @Override
    public float getIslandWidth() {
        final PlaybackSnapshot snapshot = service.getSnapshot();
        if (snapshot.song() == null) return MIN_WIDTH;
        final NVGTextRenderer font = FontRepository.getFont("productsans-semibold");
        final LyricTimeline.Line lyric = service.getCurrentLyricLine(snapshot.positionMillis());
        final float contentWidth = Math.max(font.getStringWidth(snapshot.song().name(), 8.5F),
                lyric == null ? 0 : font.getStringWidth(lyric.text(), 6.7F));
        return Math.clamp(105 + contentWidth, MIN_WIDTH, MAX_WIDTH);
    }

    @Override
    public float getIslandHeight() {
        return 38;
    }

    @Override
    public int getIslandPriority() {
        return 0;
    }

    private static String stateIcon(final PlaybackState state) {
        return switch (state) {
            case PLAYING -> "\ue037";
            case PAUSED -> "\ue034";
            case ERROR -> "\ue000";
            default -> "\ue627";
        };
    }

    private static String ellipsize(final NVGTextRenderer font, final String text, final float width, final float size) {
        if (font.getStringWidth(text, size) <= width) return text;
        final String suffix = "...";
        return font.trimStringToWidth(text, Math.max(0, width - font.getStringWidth(suffix, size)), size) + suffix;
    }

    private static String formatTime(final long millis) {
        final long seconds = Math.max(0, millis / 1000);
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }
}

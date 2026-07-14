package wtf.opal.client.music.screen;

import com.ibm.icu.impl.Pair;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import wtf.opal.client.OpalClient;
import wtf.opal.client.music.MusicPlayerModule;
import wtf.opal.client.music.MusicService;
import wtf.opal.client.music.library.MusicLibraryRepository;
import wtf.opal.client.music.model.Song;
import wtf.opal.client.music.playback.PlaybackSnapshot;
import wtf.opal.client.music.playback.PlaybackState;
import wtf.opal.client.renderer.image.NVGImageRenderer;
import wtf.opal.client.renderer.NVGRenderer;
import wtf.opal.client.renderer.liquidglass.LiquidGlassRenderer;
import wtf.opal.client.renderer.repository.FontRepository;
import wtf.opal.client.renderer.repository.ImageRepository;
import wtf.opal.client.renderer.text.NVGTextRenderer;
import wtf.opal.utility.render.ColorUtility;

import java.util.ArrayList;
import java.util.List;

import static wtf.opal.client.Constants.mc;

public final class MusicScreen extends Screen {
    private static final float SIDEBAR_WIDTH = 112;
    private static final float PLAYER_HEIGHT = 58;
    private static final float ROW_HEIGHT = 28;

    private final MusicService service;
    private TextFieldWidget searchField;
    private View view = View.SEARCH;
    private float panelX;
    private float panelY;
    private float panelWidth;
    private float panelHeight;
    private float scroll;
    private int selectedIndex = -1;
    private boolean seeking;

    public MusicScreen(final MusicService service) {
        super(Text.literal("OpenOpal Music"));
        this.service = service;
    }

    @Override
    protected void init() {
        updateLayout();
        searchField = new TextFieldWidget(
                mc.textRenderer,
                (int) (panelX + SIDEBAR_WIDTH + 23),
                (int) panelY + 17,
                (int) panelWidth - (int) SIDEBAR_WIDTH - 74,
                18,
                Text.literal("Search")
        );
        searchField.setMaxLength(120);
        searchField.setDrawsBackground(false);
        searchField.setPlaceholder(Text.literal("Search songs or enter a song ID"));
        searchField.setEditableColor(0xFFFFFFFF);
        addDrawableChild(searchField);
    }

    @Override
    public void renderBackground(final DrawContext context, final int mouseX, final int mouseY, final float deltaTicks) {
    }

    @Override
    public void render(final DrawContext context, final int mouseX, final int mouseY, final float delta) {
        updateLayout();
        positionSearchField();

        final boolean frameStarted = NVGRenderer.beginFrame();
        final Pair<Integer, Integer> colors = ColorUtility.getClientTheme();
        NVGRenderer.rect(0, 0, width, height, 0x8A050607);
        LiquidGlassRenderer.drawIsland(panelX, panelY, panelWidth, panelHeight, 8, 1);
        NVGRenderer.roundedRect(panelX + 1, panelY + 1, panelWidth - 2, panelHeight - 2, 8, 0xA80D1012);

        renderSidebar(mouseX, mouseY, colors);
        renderHeader(colors);
        renderRows(mouseX, mouseY, colors);
        renderPlayerBar(mouseX, mouseY, colors);

        if (frameStarted) NVGRenderer.endFrameAndReset(true);
        super.render(context, mouseX, mouseY, delta);
    }

    private void renderSidebar(final int mouseX, final int mouseY, final Pair<Integer, Integer> colors) {
        final NVGTextRenderer brand = FontRepository.getFont("borel-regular");
        final NVGTextRenderer font = FontRepository.getFont("productsans-medium");
        final NVGTextRenderer icon = FontRepository.getFont("materialicons-regular");

        NVGRenderer.roundedRectVarying(panelX, panelY, SIDEBAR_WIDTH, panelHeight - PLAYER_HEIGHT, 8, 0, 0, 0, 0x6A050708);
        brand.drawGradientString("OpenOpal", panelX + 13, panelY + 29, 13, colors.second, colors.first);

        int index = 0;
        for (final View item : View.values()) {
            final float y = panelY + 48 + index * 30;
            final boolean hovered = inside(mouseX, mouseY, panelX + 8, y, SIDEBAR_WIDTH - 16, 25);
            if (item == view || hovered) {
                final int color = item == view ? ColorUtility.applyOpacity(colors.first, 0.35F) : 0x251F2427;
                NVGRenderer.roundedRect(panelX + 8, y, SIDEBAR_WIDTH - 16, 25, 5, color);
            }
            icon.drawString(item.icon, panelX + 17, y + 17.5F, 12, item == view ? 0xFFFFFFFF : 0xFF9B9FA2);
            font.drawString(item.label, panelX + 37, y + 16.5F, 8, item == view ? 0xFFFFFFFF : 0xFFB0B4B6);
            index++;
        }
    }

    private void renderHeader(final Pair<Integer, Integer> colors) {
        final float contentX = panelX + SIDEBAR_WIDTH;
        NVGRenderer.rect(contentX, panelY + 44, panelWidth - SIDEBAR_WIDTH, 1, 0x243D4246);
        NVGRenderer.roundedRect(contentX + 14, panelY + 13, panelWidth - SIDEBAR_WIDTH - 28, 24, 5, 0x90202528);
        NVGRenderer.roundedRectGradient(panelX + panelWidth - 42, panelY + 15, 20, 20, 4, colors.first, colors.second, 45);
        FontRepository.getFont("materialicons-regular").drawString(view == View.PLAYLISTS ? "\ue2c4" : "\ue8b6",
                panelX + panelWidth - 38, panelY + 29.5F, 12, 0xFFFFFFFF);

        final NVGTextRenderer font = FontRepository.getFont("productsans-regular");
        if (!service.getSearchError().isBlank() && view == View.SEARCH) {
            font.drawString(ellipsize(font, service.getSearchError(), panelWidth - SIDEBAR_WIDTH - 45, 6.5F),
                    contentX + 15, panelY + 52, 6.5F, 0xFFFF7777);
        } else if (!service.getPlaylistStatus().isBlank() && view == View.PLAYLISTS) {
            font.drawString(ellipsize(font, service.getPlaylistStatus(), panelWidth - SIDEBAR_WIDTH - 45, 6.5F),
                    contentX + 15, panelY + 52, 6.5F, 0xFFB7BCBF);
        }
    }

    private void renderRows(final int mouseX, final int mouseY, final Pair<Integer, Integer> colors) {
        final List<Song> songs = visibleSongs();
        final float contentX = panelX + SIDEBAR_WIDTH + 14;
        final float contentWidth = panelWidth - SIDEBAR_WIDTH - 28;
        final float listTop = panelY + 59;
        final float listBottom = panelY + panelHeight - PLAYER_HEIGHT - 8;
        final int visibleRows = Math.max(1, (int) ((listBottom - listTop) / ROW_HEIGHT));
        final int first = Math.clamp((int) scroll, 0, Math.max(0, songs.size() - visibleRows));

        final NVGTextRenderer regular = FontRepository.getFont("productsans-regular");
        final NVGTextRenderer medium = FontRepository.getFont("productsans-medium");
        final NVGTextRenderer icon = FontRepository.getFont("materialicons-regular");

        if (songs.isEmpty()) {
            final String empty = switch (view) {
                case SEARCH -> "Search for a song to begin";
                case FAVORITES -> "No local favorites yet";
                case HISTORY -> "No playback history yet";
                case QUEUE -> "The play queue is empty";
                case PLAYLISTS -> "Enter a NetEase playlist ID or URL above";
            };
            regular.drawString(empty, contentX + 8, listTop + 24, 8, 0xFF8D9295);
            return;
        }

        for (int row = 0; row < visibleRows && first + row < songs.size(); row++) {
            final int songIndex = first + row;
            final Song song = songs.get(songIndex);
            final float y = listTop + row * ROW_HEIGHT;
            final boolean hovered = inside(mouseX, mouseY, contentX, y, contentWidth, ROW_HEIGHT - 2);
            if (hovered || selectedIndex == songIndex) {
                NVGRenderer.roundedRect(contentX, y, contentWidth, ROW_HEIGHT - 2, 4,
                        selectedIndex == songIndex ? ColorUtility.applyOpacity(colors.first, 0.22F) : 0x241F2427);
            }

            final String number = String.format("%02d", songIndex + 1);
            regular.drawString(number, contentX + 7, y + 17, 7, 0xFF747A7E);
            medium.drawString(ellipsize(medium, song.name(), contentWidth * 0.43F, 8), contentX + 31, y + 13, 8, 0xFFF3F3F3);
            regular.drawString(ellipsize(regular, song.singer(), contentWidth * 0.34F, 6.5F), contentX + 31, y + 21.5F, 6.5F, 0xFF969B9E);
            regular.drawString(formatTime(song.durationMillis()), contentX + contentWidth - 88, y + 17, 6.5F, 0xFF969B9E);

            final boolean favorite = service.getLibrary().isFavorite(song.id());
            icon.drawString(favorite ? "\ue87d" : "\ue87e", contentX + contentWidth - 57, y + 18, 10,
                    favorite ? colors.first : 0xFF969B9E);
            icon.drawString("\ue03b", contentX + contentWidth - 27, y + 18, 10, 0xFFB8BCBE);
        }
    }

    private void renderPlayerBar(final int mouseX, final int mouseY, final Pair<Integer, Integer> colors) {
        final float y = panelY + panelHeight - PLAYER_HEIGHT;
        NVGRenderer.rect(panelX, y, panelWidth, 1, 0x353D4246);
        NVGRenderer.roundedRectVarying(panelX, y + 1, panelWidth, PLAYER_HEIGHT - 1, 0, 0, 8, 8, 0x76101517);

        final PlaybackSnapshot snapshot = service.getSnapshot();
        final NVGTextRenderer medium = FontRepository.getFont("productsans-medium");
        final NVGTextRenderer regular = FontRepository.getFont("productsans-regular");
        final NVGTextRenderer icon = FontRepository.getFont("materialicons-regular");

        if (snapshot.song() != null) {
            final NVGImageRenderer artwork = service.getCurrentArtworkPath() == null
                    ? null : ImageRepository.getImage(service.getCurrentArtworkPath(), 0);
            if (artwork != null) {
                artwork.drawRoundedImage(panelX + 12, y + 10, 36, 36, 5);
            } else {
                NVGRenderer.roundedRectGradient(panelX + 12, y + 10, 36, 36, 5, colors.first, colors.second, 45);
                icon.drawString("\ue405", panelX + 21, y + 34, 17, 0xFFFFFFFF);
            }
            medium.drawString(ellipsize(medium, snapshot.song().name(), 125, 8), panelX + 58, y + 23, 8, 0xFFFFFFFF);
            regular.drawString(ellipsize(regular, snapshot.song().singer(), 125, 6.5F), panelX + 58, y + 35, 6.5F, 0xFF989DA0);
        } else {
            regular.drawString("Nothing playing", panelX + 15, y + 31, 8, 0xFF888D90);
        }

        final float center = panelX + panelWidth * 0.52F;
        icon.drawString("\ue045", center - 38, y + 29, 14, 0xFFE4E6E7);
        NVGRenderer.roundedRectGradient(center - 11, y + 12, 25, 25, 12.5F, colors.first, colors.second, 45);
        icon.drawString(snapshot.state() == PlaybackState.PLAYING ? "\ue034" : "\ue037", center - 5, y + 30, 14, 0xFFFFFFFF);
        icon.drawString("\ue044", center + 30, y + 29, 14, 0xFFE4E6E7);

        final float progressX = center - 108;
        final float progressY = y + 45;
        final float progressWidth = 218;
        NVGRenderer.roundedRect(progressX, progressY, progressWidth, 2, 1, 0xFF343A3E);
        final float ratio = snapshot.durationMillis() <= 0 ? 0 : Math.clamp((float) snapshot.positionMillis() / snapshot.durationMillis(), 0, 1);
        if (ratio > 0) NVGRenderer.roundedRectGradient(progressX, progressY, progressWidth * ratio, 2, 1, colors.first, colors.second, 0);
        regular.drawString(formatTime(snapshot.positionMillis()), progressX - 26, progressY + 3, 5.8F, 0xFF8F9497);
        regular.drawString(formatTime(snapshot.durationMillis()), progressX + progressWidth + 6, progressY + 3, 5.8F, 0xFF8F9497);

        final float actionsX = panelX + panelWidth - 154;
        icon.drawString(snapshot.shuffle() ? "\ue043" : "\ue043", actionsX, y + 29, 11,
                snapshot.shuffle() ? colors.first : 0xFF92979A);
        icon.drawString(snapshot.repeatMode() == wtf.opal.client.music.playback.RepeatMode.ONE ? "\ue041" : "\ue040",
                actionsX + 27, y + 29, 11, snapshot.repeatMode() == wtf.opal.client.music.playback.RepeatMode.OFF ? 0xFF92979A : colors.first);
        icon.drawString("\ue050", actionsX + 58, y + 29, 11, 0xFFC5C8CA);
        NVGRenderer.roundedRect(actionsX + 78, y + 23, 63, 2, 1, 0xFF343A3E);
        NVGRenderer.roundedRectGradient(actionsX + 78, y + 23, 63 * snapshot.volume(), 2, 1, colors.first, colors.second, 0);
    }

    @Override
    public boolean mouseClicked(final Click click, final boolean doubled) {
        final double mouseX = click.x();
        final double mouseY = click.y();
        if (click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            int index = 0;
            for (final View item : View.values()) {
                final float y = panelY + 48 + index * 30;
                if (inside(mouseX, mouseY, panelX + 8, y, SIDEBAR_WIDTH - 16, 25)) {
                    view = item;
                    selectedIndex = -1;
                    scroll = 0;
                    return true;
                }
                index++;
            }

            if (inside(mouseX, mouseY, panelX + panelWidth - 42, panelY + 15, 20, 20)) {
                submitSearch();
                return true;
            }

            if (handleRowClick(mouseX, mouseY, doubled)) return true;
            if (handlePlayerClick(mouseX, mouseY)) return true;
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseReleased(final Click click) {
        if (seeking && click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            seeking = false;
            seekFromMouse(click.x());
            return true;
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseDragged(final Click click, final double offsetX, final double offsetY) {
        if (seeking) return true;
        return super.mouseDragged(click, offsetX, offsetY);
    }

    @Override
    public boolean mouseScrolled(final double mouseX, final double mouseY, final double horizontalAmount, final double verticalAmount) {
        if (inside(mouseX, mouseY, panelX + SIDEBAR_WIDTH, panelY + 45,
                panelWidth - SIDEBAR_WIDTH, panelHeight - PLAYER_HEIGHT - 45)) {
            scroll = Math.max(0, scroll - (float) Math.signum(verticalAmount) * 2);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(final KeyInput input) {
        if ((input.key() == GLFW.GLFW_KEY_ENTER || input.key() == GLFW.GLFW_KEY_KP_ENTER) && searchField.isFocused()) {
            submitSearch();
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public void close() {
        super.close();
        mc.execute(() -> {
            final MusicPlayerModule module = OpalClient.getInstance().getModuleRepository().getModule(MusicPlayerModule.class);
            if (module.isEnabled()) module.setEnabled(false);
        });
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private boolean handleRowClick(final double mouseX, final double mouseY, final boolean doubled) {
        final List<Song> songs = visibleSongs();
        final float contentX = panelX + SIDEBAR_WIDTH + 14;
        final float contentWidth = panelWidth - SIDEBAR_WIDTH - 28;
        final float listTop = panelY + 59;
        final float listBottom = panelY + panelHeight - PLAYER_HEIGHT - 8;
        if (!inside(mouseX, mouseY, contentX, listTop, contentWidth, listBottom - listTop)) return false;
        final int visibleRows = Math.max(1, (int) ((listBottom - listTop) / ROW_HEIGHT));
        final int first = Math.clamp((int) scroll, 0, Math.max(0, songs.size() - visibleRows));
        final int row = (int) ((mouseY - listTop) / ROW_HEIGHT);
        final int index = first + row;
        if (row < 0 || row >= visibleRows || index >= songs.size()) return false;
        final Song song = songs.get(index);

        if (mouseX >= contentX + contentWidth - 70 && mouseX < contentX + contentWidth - 38) {
            service.toggleFavorite(song);
        } else if (mouseX >= contentX + contentWidth - 38) {
            service.addToQueue(song);
        } else if (doubled) {
            service.playQueue(songs, index);
        } else {
            selectedIndex = index;
        }
        return true;
    }

    private boolean handlePlayerClick(final double mouseX, final double mouseY) {
        final float y = panelY + panelHeight - PLAYER_HEIGHT;
        final float center = panelX + panelWidth * 0.52F;
        if (inside(mouseX, mouseY, center - 46, y + 8, 25, 28)) {
            service.previous();
            return true;
        }
        if (inside(mouseX, mouseY, center - 14, y + 8, 31, 31)) {
            service.togglePause();
            return true;
        }
        if (inside(mouseX, mouseY, center + 23, y + 8, 28, 28)) {
            service.next();
            return true;
        }
        final float progressX = center - 108;
        if (inside(mouseX, mouseY, progressX, y + 39, 218, 14)) {
            seeking = true;
            return true;
        }
        final float actionsX = panelX + panelWidth - 154;
        if (inside(mouseX, mouseY, actionsX - 4, y + 13, 22, 25)) {
            service.toggleShuffle();
            return true;
        }
        if (inside(mouseX, mouseY, actionsX + 22, y + 13, 22, 25)) {
            service.cycleRepeatMode();
            return true;
        }
        if (inside(mouseX, mouseY, actionsX + 74, y + 15, 72, 20)) {
            service.setVolume((float) Math.clamp((mouseX - actionsX - 78) / 63, 0, 1));
            return true;
        }
        return false;
    }

    private void seekFromMouse(final double mouseX) {
        final float center = panelX + panelWidth * 0.52F;
        final float progressX = center - 108;
        final PlaybackSnapshot snapshot = service.getSnapshot();
        if (snapshot.durationMillis() <= 0) return;
        final double ratio = Math.clamp((mouseX - progressX) / 218.0, 0, 1);
        service.seek((long) (snapshot.durationMillis() * ratio));
    }

    private void submitSearch() {
        final String query = searchField.getText().trim();
        if (query.isEmpty()) return;
        if (view == View.PLAYLISTS) {
            selectedIndex = -1;
            scroll = 0;
            service.importPlaylist(query);
            return;
        }
        view = View.SEARCH;
        selectedIndex = -1;
        scroll = 0;
        try {
            final long songId = Long.parseLong(query);
            service.playById(songId);
        } catch (final NumberFormatException ignored) {
            service.search(query);
        }
    }

    private List<Song> visibleSongs() {
        return switch (view) {
            case SEARCH -> service.getSearchResults();
            case FAVORITES -> service.getLibrary().getFavorites();
            case HISTORY -> {
                final List<Song> songs = new ArrayList<>();
                for (final MusicLibraryRepository.HistoryEntry entry : service.getLibrary().getHistory()) songs.add(entry.song());
                yield songs;
            }
            case QUEUE -> service.getQueue();
            case PLAYLISTS -> service.getImportedPlaylistSongs();
        };
    }

    private void updateLayout() {
        panelWidth = Math.min(900, Math.max(540, width - 70));
        panelHeight = Math.min(560, Math.max(340, height - 54));
        panelX = (width - panelWidth) / 2.0F;
        panelY = (height - panelHeight) / 2.0F;
    }

    private void positionSearchField() {
        if (searchField == null) return;
        searchField.setX((int) (panelX + SIDEBAR_WIDTH + 23));
        searchField.setY((int) panelY + 17);
        searchField.setWidth((int) panelWidth - (int) SIDEBAR_WIDTH - 74);
        searchField.setPlaceholder(Text.literal(view == View.PLAYLISTS
                ? "Enter playlist ID or NetEase playlist URL"
                : "Search songs or enter a song ID"));
    }

    private static boolean inside(final double mouseX, final double mouseY, final float x, final float y, final float width, final float height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
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

    private enum View {
        SEARCH("Search", "\ue8b6"),
        FAVORITES("Favorites", "\ue87d"),
        HISTORY("History", "\ue889"),
        QUEUE("Queue", "\ue03b"),
        PLAYLISTS("Playlists", "\ue03d");

        private final String label;
        private final String icon;

        View(final String label, final String icon) {
            this.label = label;
            this.icon = icon;
        }
    }
}

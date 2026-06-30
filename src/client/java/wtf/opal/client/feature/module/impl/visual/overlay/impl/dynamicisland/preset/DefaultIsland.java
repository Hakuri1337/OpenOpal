package wtf.opal.client.feature.module.impl.visual.overlay.impl.dynamicisland.preset;

import com.ibm.icu.impl.Pair;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.network.ServerInfo;
import wtf.opal.client.Constants;
import wtf.opal.client.OpalClient;
import wtf.opal.client.ReleaseInfo;
import wtf.opal.client.feature.helper.impl.LocalDataWatch;
import wtf.opal.client.feature.helper.impl.server.KnownServer;
import wtf.opal.client.feature.module.impl.visual.overlay.OverlayModule;
import wtf.opal.client.feature.module.impl.visual.overlay.impl.dynamicisland.IslandTrigger;
import wtf.opal.client.renderer.NVGRenderer;
import wtf.opal.client.renderer.image.NVGImageRenderer;
import wtf.opal.client.renderer.repository.FontRepository;
import wtf.opal.client.renderer.repository.ImageRepository;
import wtf.opal.client.renderer.text.NVGTextRenderer;
import wtf.opal.utility.render.ClientTheme;
import wtf.opal.utility.render.ColorUtility;

import static wtf.opal.client.Constants.mc;

public class DefaultIsland implements IslandTrigger {
    private float width;

    @Override
    public void renderIsland(DrawContext context, float posX, float posY, float width, float height, float progress) {
        final NVGTextRenderer brandFont = FontRepository.getFont("borel-regular");
        final NVGTextRenderer titleFont = FontRepository.getFont("productsans-bold");
        final NVGTextRenderer footerFont = FontRepository.getFont("productsans-medium");

        final String openOpalText = "OpenOpal";
        final String releaseType = ReleaseInfo.CHANNEL.toString();
        final String releaseVersion = "v" + ReleaseInfo.VERSION;

        String serverAddress = "singleplayer";
        String serverPing = "0 ms";

        if (mc.getNetworkHandler() != null) {
            final ServerInfo serverInfo = mc.getNetworkHandler().getServerInfo();
            if (serverInfo != null) {
                final KnownServer currentKnownServer = LocalDataWatch.get().getKnownServerManager().getCurrentServer();

                serverAddress = currentKnownServer != null && currentKnownServer.getProxyServer() != null
                        ? currentKnownServer.getProxyServer().getName().toLowerCase()
                        : serverInfo.address.toLowerCase();

                serverAddress = serverAddress.length() > 20
                        ? serverAddress.substring(0, 20 - 3) + "..."
                        : serverAddress;

                long latency = 0;

                final PlayerListEntry playerListEntry = mc.getNetworkHandler().getPlayerListEntry(mc.getSession().getUuidOrNull());
                if (playerListEntry != null) {
                    latency = playerListEntry.getLatency();
                }

                if (latency < 2) {
                    latency = serverInfo.ping;
                }

                serverPing = latency + " ms";
            }
        }

        final float titleTextSize = 11.5f;
        final float secondaryTextSize = 7;
        final float footerTextSize = 6;

        final float releaseInfoWidth = Math.max(
                titleFont.getStringWidth(releaseType, secondaryTextSize),
                footerFont.getStringWidth(releaseVersion, footerTextSize)
        );

        this.width = 14 + brandFont.getStringWidth(openOpalText, titleTextSize) + releaseInfoWidth + titleFont.getStringWidth(serverAddress, secondaryTextSize) + 35;

        final ClientTheme theme = OpalClient.getInstance().getModuleRepository().getModule(OverlayModule.class).getThemeMode().getValue();
        final Pair<Integer, Integer> colors = theme.getColors();

        final boolean grayscale = theme != ClientTheme.OPAL;
        final NVGImageRenderer iconRenderer = this.getAppropriateImage(mc.getWindow().getScaleFactor(), grayscale);

//        final String[] variable = ClientSocket.getInstance().getVariableCache().getString("Release Version").split("_");
//        if (grayscale) {
//            final int interpolatedColor = ColorUtility.interpolateColorsBackAndForth(Integer.parseInt(variable[4]), 1, colors.second, colors.first);
//            iconRenderer.drawImage(posX + Integer.parseInt(variable[1]), posY + Integer.parseInt(variable[0]), 16, 16, ColorUtility.brighter(interpolatedColor, 0.2F));
//        } else {
//            iconRenderer.drawImage(posX + Integer.parseInt(variable[1]), posY + Integer.parseInt(variable[0]), ClientSocket.getInstance().getVariableCache().getInt("PostGres Error"), 16);
//        }

        final int xOffset = 10;
        final int yOffset = 5;
        final String version = ReleaseInfo.VERSION;

        final int baseYOffset = 5;
        final int baseXOffset = 10;
        final float dividerHeight = 17.5f;
        final float textSpacing = 1.5f;
        final int colorIndex = 5;

        if (grayscale) {
            final int interpolatedColor = ColorUtility.interpolateColorsBackAndForth(colorIndex, 1, colors.second, colors.first);
            iconRenderer.drawImage(posX + baseXOffset - 4, posY + baseYOffset + 1.5F, 16, 16, ColorUtility.brighter(interpolatedColor, 0.2F));
        } else {
            iconRenderer.drawImage(posX + baseXOffset - 4, posY + baseYOffset + 1.5F, 16, 16);
        }

        final float textStart = posX + 26.5f - 2;
        brandFont.drawGradientString(openOpalText, textStart, posY + baseYOffset + 2.5F + baseXOffset, titleTextSize, colors.second, colors.first);

        final float releaseTypeStart = textStart + brandFont.getStringWidth(openOpalText, titleTextSize) + 3.3f + 1;
        NVGRenderer.rect(releaseTypeStart, posY + dividerHeight / 1.5f - 2F, 0.75F, 10, ColorUtility.MUTED_COLOR);

        titleFont.drawString(releaseType, releaseTypeStart + textSpacing + 2, posY + dividerHeight / 1.3f + 1.0F, secondaryTextSize, -1);
        footerFont.drawString(releaseVersion, releaseTypeStart + textSpacing + 2, posY + 19.5f + 1.0F, footerTextSize, ColorUtility.MUTED_COLOR);

        final float serverIPStart = releaseTypeStart + textSpacing + releaseInfoWidth + 3.3f;
        NVGRenderer.rect(serverIPStart + 1, posY + dividerHeight / 1.5f - 2F, 0.75F, 10, ColorUtility.MUTED_COLOR);

        titleFont.drawString(serverAddress, serverIPStart + textSpacing + 3, posY + dividerHeight / 1.3f + 1F, secondaryTextSize, -1);
        footerFont.drawString(serverPing, serverIPStart + textSpacing + 3, posY + 19.5f + 1F, footerTextSize, ColorUtility.MUTED_COLOR);
    }

//    private NVGImageRenderer getAppropriateImage(double scaleFactor, boolean grayscale) {
//        final int size = scaleFactor > 2 ? 128 : 32;
//        final String suffix = grayscale ? "Gray Scaled Suffix" : "";
//
//        return ImageRepository.getImage(String.format("window-icons/icon_%dx%d%s.png", size, size, suffix));
//    }

    private NVGImageRenderer getAppropriateImage(double scaleFactor, boolean grayscale) {
        final int size = scaleFactor > 2 ? 128 : 32;

        return ImageRepository.getImage(String.format("window-icons/icon_%dx%d.png", size, size));
    }

    @Override
    public float getIslandWidth() {
        return width;
    }

    @Override
    public float getIslandHeight() {
        return 28;
    }

    @Override
    public int getIslandPriority() {
        return -5;
    }
}

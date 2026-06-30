package wtf.opal.mixin;

import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import wtf.opal.client.OpalClient;
import wtf.opal.client.feature.module.impl.visual.StreamerModeModule;

import java.util.ArrayList;
import java.util.List;

import static wtf.opal.client.Constants.mc;

@Mixin(ChatHud.class)
public final class ChatHudMixin {

    @Unique
    private static Style GRAY_STYLE;

    @Redirect(
            method = "method_71991",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/hud/ChatHudLine$Visible;content()Lnet/minecraft/text/OrderedText;")
    )
    private OrderedText appendSocketUsernames(final ChatHudLine.Visible instance) {
        if (GRAY_STYLE == null) {
            GRAY_STYLE = Style.EMPTY.withColor(Formatting.GRAY);
        }

        final List<OrderedText> styledSegments = new ArrayList<>();
        final StringBuilder builder = new StringBuilder();

        instance.content().accept((index, style, codePoint) -> {
            styledSegments.add(OrderedText.styledForwardsVisitedString(String.valueOf((char) codePoint), style));
            builder.append((char) codePoint);
            return true;
        });

        final String text = builder.toString();
        int insertOffset = 0;

        for (final AbstractClientPlayerEntity player : mc.world.getPlayers()) {
            String playerName = null;

            if (player == mc.player) {
                final StreamerModeModule streamerModeModule = OpalClient.getInstance().getModuleRepository().getModule(StreamerModeModule.class);
                if (streamerModeModule.isEnabled()) {
                    final String customUsername = streamerModeModule.getCustomUsername();
                    if (!customUsername.isEmpty()) {
                        playerName = customUsername;
                    }
                }
            }

            if (playerName == null) {
                playerName = player.getName().getString();
            }

            int startingSearchIndex = 0;

            while (true) {
                final int nameIndex = text.indexOf(playerName, startingSearchIndex);
                if (nameIndex == -1) {
                    break;
                }

                if (nameIndex > 0) {
                    char beforeChar = text.charAt(nameIndex - 1);
                    if (beforeChar != ' ' && beforeChar != '<') {
                        startingSearchIndex = nameIndex + playerName.length();
                        continue;
                    }
                }

                final String user = OpalClient.getInstance().getUser();
                if (user == null) {
                    break;
                }

                final int nameEndIndex = nameIndex + playerName.length();

                styledSegments.add(
                        nameEndIndex + insertOffset,
                        OrderedText.concat(
                                OrderedText.styledForwardsVisitedString(" (", GRAY_STYLE),
//                                OrderedText.styledForwardsVisitedString(user, Style.EMPTY.withColor(user.getRole().getColor())),
                                OrderedText.styledForwardsVisitedString(user, Style.EMPTY.withColor(-1)),
                                OrderedText.styledForwardsVisitedString(")", GRAY_STYLE)
                        )
                );

                startingSearchIndex = nameEndIndex;
                insertOffset++;
            }
        }

        return OrderedText.concat(styledSegments);
    }
}
package wtf.opal.client.command.impl.config;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.command.CommandSource;
import wtf.opal.client.command.Command;
import wtf.opal.client.command.arguments.ConfigArgumentType;
import wtf.opal.utility.data.SaveUtility;
import wtf.opal.utility.misc.chat.ChatUtility;

import java.util.List;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;

public final class ConfigCommand extends Command {

    public ConfigCommand() {
        super("config", "Interacts with configs.", "c");
    }

    @Override
    protected void onCommand(final LiteralArgumentBuilder<CommandSource> builder) {
        builder.executes(context -> {
            printConfigs();
            return SINGLE_SUCCESS;
        });

        builder.then(literal("save").then(argument("config_name", ConfigArgumentType.create()).executes(context -> {
            final String configName = getConfigName(context.getArgument("config_name", String.class));

            if (SaveUtility.saveConfig(configName)) {
                ChatUtility.success("Config \u00a7l" + configName + "\u00a77 saved!");
            } else {
                ChatUtility.error("Failed to save config \u00a7l" + configName + "\u00a77.");
            }

            return SINGLE_SUCCESS;
        })));

        builder.then(literal("list").executes(context -> {
            printConfigs();
            return SINGLE_SUCCESS;
        }));

        builder.then(literal("load").then(argument("config_name", ConfigArgumentType.create()).executes(context -> {
            final String configName = getConfigName(context.getArgument("config_name", String.class));

            if (SaveUtility.loadConfigFile(configName)) {
                ChatUtility.success("Config \u00a7l" + configName + "\u00a77 loaded!");
            } else {
                ChatUtility.error("Failed to load config \u00a7l" + configName + "\u00a77.");
            }

            return SINGLE_SUCCESS;
        })));

        builder.then(literal("delete").then(argument("config_name", ConfigArgumentType.create()).executes(context -> {
            final String configName = getConfigName(context.getArgument("config_name", String.class));

            if (SaveUtility.deleteConfig(configName)) {
                ChatUtility.success("Config \u00a7l" + configName + "\u00a77 deleted!");
            } else {
                ChatUtility.error("Failed to delete config \u00a7l" + configName + "\u00a77.");
            }

            return SINGLE_SUCCESS;
        })));
    }

    private static String getConfigName(final String configName) {
        return configName == null ? "" : configName.trim().toLowerCase();
    }

    private static void printConfigs() {
        final List<String> configs = SaveUtility.listConfigs();
        if (configs.isEmpty()) {
            ChatUtility.print("No configs found.");
            return;
        }

        ChatUtility.print("Configs: \u00a7l" + String.join("\u00a77, \u00a7l", configs) + "\u00a77");
    }
}

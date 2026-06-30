package wtf.opal.client.command.arguments;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.command.CommandSource;
import wtf.opal.utility.data.SaveUtility;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class ConfigArgumentType implements ArgumentType<String> {

    private static final ConfigArgumentType INSTANCE = new ConfigArgumentType();

    public static ConfigArgumentType create() {
        return INSTANCE;
    }

    public static String get(final CommandContext<?> context) {
        return context.getArgument("config_name", String.class);
    }

    private ConfigArgumentType() {
    }

    @Override
    public String parse(final StringReader reader) throws CommandSyntaxException {
        return reader.readString();
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(final CommandContext<S> context, final SuggestionsBuilder builder) {
        return CommandSource.suggestMatching(SaveUtility.listConfigs(), builder);
    }

    @Override
    public Collection<String> getExamples() {
        return List.of("default");
    }
}

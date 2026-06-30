package wtf.opal.utility.data;

import wtf.opal.client.binding.IBindable;
import wtf.opal.utility.misc.chat.ChatUtility;

import java.util.Date;
import java.util.Locale;
import java.util.Objects;

public final class Config implements IBindable {

    private final String name;

    private String description;
    private boolean pinned;
    private Date updatedAt;

    public Config(final String name) {
        this.name = name;
    }

    public Config(final String name, final String description, final boolean pinned, final Date updatedAt) {
        this.name = name;
        this.description = description;
        this.pinned = pinned;
        this.updatedAt = updatedAt;
    }

    @Override
    public void onBindingInteraction() {
        if (SaveUtility.loadConfigFile(this.name)) {
            ChatUtility.success("Config \u00a7l" + this.name + "\u00a77 loaded!");
        } else {
            ChatUtility.error("Failed to load config \u00a7l" + this.name + "\u00a77.");
        }
    }

    public Date getUpdatedAt() {
        return updatedAt;
    }

    public boolean isPinned() {
        return pinned;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public boolean equals(final Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof Config config)) {
            return false;
        }
        return normalizedName(this.name).equals(normalizedName(config.name));
    }

    @Override
    public int hashCode() {
        return Objects.hash(normalizedName(this.name));
    }

    private static String normalizedName(final String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }
}

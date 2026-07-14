package wtf.opal.client.feature.module.property.impl.mode;

import wtf.opal.client.feature.module.Module;
import wtf.opal.client.feature.module.property.Property;
import wtf.opal.client.screen.click.dropdown.panel.property.PropertyPanel;
import wtf.opal.client.screen.click.dropdown.panel.property.impl.ModePropertyComponent;

import java.util.HashMap;
import java.util.Map;

public final class ModeProperty<T extends Enum<T>> extends Property<T> {

    private final T[] values;
    private final Map<String, T> aliases = new HashMap<>();
    private Module module;

    private boolean theme;

    public ModeProperty(final String name, final T value) {
        super(name);
        setValue(value);
        this.values = getEnumConstants();
    }

    public ModeProperty(final String name, final T value, final T[] values) {
        super(name);
        setValue(value);
        this.values = values;
    }

    public ModeProperty(final String name, final T value, final boolean theme) {
        super(name);
        setValue(value);
        this.values = getEnumConstants();

        this.theme = theme;
    }

    public ModeProperty(final String name, final ModuleMode<?> parent, final T value) {
        super(name, parent);
        setValue(value);
        this.values = getEnumConstants();
    }

    public ModeProperty(final String name, final Module module, final T value) {
        super(name);
        setValue(value);
        this.values = getEnumConstants();
        this.module = module;
        module.setModeProperty(this);
    }

    @SuppressWarnings("unchecked")
    private T[] getEnumConstants() {
        return (T[]) getValue().getClass().getEnumConstants();
    }

    public T[] getValues() {
        return values;
    }

    public ModeProperty<T> alias(final String legacyValue, final T value) {
        aliases.put(normalize(legacyValue), value);
        return this;
    }

    public void setValueOrdinal(final int value) {
        if (module != null && module.isEnabled()) {
            module.getModuleModes().forEach(ModuleMode::onDisable);
        }
        setValue(values[value]);
        if (module != null) {
            for (final ModuleMode<?> mode : module.getModuleModes()) {
                if (mode.getEnumValue().ordinal() == value && module.isEnabled()) {
                    mode.onEnable();
                    break;
                }
            }
        }
    }

    public void cycle(final boolean forwards) {
        final int currentIndex = getValue().ordinal();
        final int nextIndex = (currentIndex + (forwards ? 1 : values.length - 1)) % values.length;
        setValueOrdinal(nextIndex);
    }

    public boolean isTheme() {
        return theme;
    }

    public boolean is(final T value) {
        return getValue() == value;
    }

    @Override
    public PropertyPanel<?> createClickGUIComponent() {
        return new ModePropertyComponent(this);
    }

    @Override
    public void applyValue(Object propertyValue) {
        if (propertyValue instanceof Number numberValue) {
            final int ordinal = numberValue.intValue();
            if (ordinal >= 0 && ordinal < values.length) {
                setValueOrdinal(ordinal);
            }
            return;
        }

        if (propertyValue instanceof String valueString) {
            final String normalizedValue = normalize(valueString);
            for (T possibleValue : values) {
                if (normalize(possibleValue.name()).equals(normalizedValue)
                        || normalize(possibleValue.toString()).equals(normalizedValue)) {
                    setValueOrdinal(possibleValue.ordinal());
                    return;
                }
            }

            final T aliasedValue = aliases.get(normalizedValue);
            if (aliasedValue != null) {
                setValueOrdinal(aliasedValue.ordinal());
            }
        }
    }

    private static String normalize(final String value) {
        if (value == null) {
            return "";
        }

        final StringBuilder builder = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            final char character = value.charAt(i);
            if (Character.isLetterOrDigit(character)) {
                builder.append(Character.toLowerCase(character));
            }
        }
        return builder.toString();
    }
}

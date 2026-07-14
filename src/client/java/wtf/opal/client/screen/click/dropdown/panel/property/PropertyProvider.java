package wtf.opal.client.screen.click.dropdown.panel.property;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.input.KeyInput;
import wtf.opal.client.feature.module.property.IPropertyListProvider;
import wtf.opal.client.feature.module.property.Property;
import wtf.opal.client.feature.module.property.impl.ScreenPositionProperty;
import wtf.opal.client.screen.click.OpalPanelComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

public final class PropertyProvider extends OpalPanelComponent {

    private final List<PropertyPanel<?>> propertyPanelList = new ArrayList<>();
    private final IPropertyListProvider propertyListProvider;
    private final BooleanSupplier expanded, lastPropertyListProvider;

    public PropertyProvider(final IPropertyListProvider propertyListProvider, final BooleanSupplier expanded, final BooleanSupplier lastPropertyListProvider) {
        this.propertyListProvider = propertyListProvider;
        this.expanded = expanded;
        this.initProperties();
        this.updateHasProperties();
        this.lastPropertyListProvider = lastPropertyListProvider;
    }

    private void initProperties() {
        for (final Property<?> property : this.propertyListProvider.getPropertyList()) {
            final PropertyPanel<?> clickGUIComponent = property.createClickGUIComponent();
            if (clickGUIComponent != null) {
                this.propertyPanelList.add(clickGUIComponent);
            } else if (!(property instanceof ScreenPositionProperty)) {
                System.err.println("Unimplemented property: " + property.getClass().getSimpleName());
            }
        }
    }

    public boolean isHasProperties() {
        if (!this.updated) {
            this.updateHasProperties();
            this.updated = true;
        }
        return hasProperties;
    }

    private boolean hasProperties, updated;

    private void updateHasProperties() {
        this.hasProperties = this.propertyPanelList.stream().anyMatch(propertyPanel -> !propertyPanel.isHidden());
    }

    private boolean isClosed() {
        return !this.expanded.getAsBoolean();
    }

    private float extraHeight;

    @Override
    public void init() {
        if (this.isClosed()) return;
        propertyPanelList.forEach(PropertyPanel::init);
    }

    @Override
    public void close() {
        if (this.isClosed()) return;
        propertyPanelList.forEach(PropertyPanel::close);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (this.isClosed()) return;

        float extraHeight = 0;

        int lastVisibleIndex = -1;
        for (int i = propertyPanelList.size() - 1; i >= 0; i--) {
            if (!propertyPanelList.get(i).isHidden()) {
                lastVisibleIndex = i;
                break;
            }
        }

        for (int i = 0; i < propertyPanelList.size(); i++) {
            final PropertyPanel<?> propertyPanel = propertyPanelList.get(i);

            if (propertyPanel.isHidden()) continue;
            propertyPanel.setX(x);
            propertyPanel.setY(y + extraHeight);
            propertyPanel.setWidth(width);

            propertyPanel.lastProperty = lastPropertyListProvider != null && lastPropertyListProvider.getAsBoolean() && i == lastVisibleIndex;

            propertyPanel.render(context, mouseX, mouseY, delta);

            extraHeight += propertyPanel.getHeight();
        }

        this.extraHeight = extraHeight;
    }

    @Override
    public void keyPressed(KeyInput keyInput) {
        if (this.isClosed()) return;
        propertyPanelList.forEach(propertyPanel -> propertyPanel.keyPressed(keyInput));
    }

    @Override
    public void charTyped(char chr, int modifiers) {
        if (this.isClosed()) return;
        propertyPanelList.forEach(propertyPanel -> propertyPanel.charTyped(chr, modifiers));
    }

    public float getExtraHeight() {
        return extraHeight;
    }

    @Override
    public void mouseClicked(double mouseX, double mouseY, int button) {
        if (this.isClosed()) return;

        for (final PropertyPanel<?> propertyPanel : this.propertyPanelList) {
            if (propertyPanel.isHidden()) continue;
            propertyPanel.mouseClicked(mouseX, mouseY, button);
        }
    }

    @Override
    public void mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (this.isClosed()) return;

        for (final PropertyPanel<?> propertyPanel : this.propertyPanelList) {
            if (propertyPanel.isHidden()) continue;
            propertyPanel.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }
    }

    @Override
    public void mouseReleased(double mouseX, double mouseY, int button) {
        if (this.isClosed()) return;

        for (final PropertyPanel<?> propertyPanel : this.propertyPanelList) {
            if (propertyPanel.isHidden()) continue;
            propertyPanel.mouseReleased(mouseX, mouseY, button);
        }
    }
}

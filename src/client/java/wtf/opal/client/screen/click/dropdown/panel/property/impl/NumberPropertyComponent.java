package wtf.opal.client.screen.click.dropdown.panel.property.impl;

import net.minecraft.client.gui.DrawContext;
import wtf.opal.client.feature.module.property.impl.number.NumberProperty;
import wtf.opal.client.renderer.NVGRenderer;
import wtf.opal.client.renderer.repository.FontRepository;
import wtf.opal.client.renderer.text.NVGTextRenderer;
import wtf.opal.client.screen.click.dropdown.panel.property.PropertyPanel;
import wtf.opal.utility.misc.HoverUtility;
import wtf.opal.utility.misc.math.MathUtility;
import wtf.opal.utility.render.ColorUtility;
import wtf.opal.utility.render.animation.Animation;
import wtf.opal.utility.render.animation.Easing;

public final class NumberPropertyComponent extends PropertyPanel<NumberProperty> {

    private boolean dragging;

    private Animation dragAnimation;

    public NumberPropertyComponent(final NumberProperty property) {
        super(property);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        setHeight(26);

        super.render(context, mouseX, mouseY, delta);

        final NumberProperty property = getProperty();
        final NVGTextRenderer font = FontRepository.getFont("productsans-medium");

        font.drawString(property.getName(), x + 5, y + 8.5F, 7, -1);

        final float sliderWidth = width - 12;
        final float sliderHeight = 2.5F;

        final float sliderX = x + 6;
        final float sliderY = y + 13F;

        if (dragging && mouseX != -1) {
            final float percent = Math.min(1, Math.max(0, (mouseX - (sliderX)) / sliderWidth));
            property.setValue(MathUtility.interpolate(property.getMinValue(), property.getMaxValue(), percent));
        }

        final double widthPercent = ((property.getValue()) - property.getMinValue()) / (property.getMaxValue() - property.getMinValue());

        final double destination = sliderWidth * widthPercent;
        if (this.dragAnimation == null) {
            this.dragAnimation = new Animation(Easing.LINEAR, 50);
            this.dragAnimation.setValue((float) destination);
        } else {
            this.dragAnimation.run((float) destination);
        }

        NVGRenderer.roundedRect(sliderX, sliderY, sliderWidth, sliderHeight, sliderHeight / 2f, 0xff373737);

        final float dragAnim = dragAnimation.getValue();
        if (dragAnim > 1) {
            final int color = ColorUtility.getClientTheme().first;
            NVGRenderer.roundedRectGradient(sliderX, sliderY, dragAnim, sliderHeight, sliderHeight / 2f, color, ColorUtility.darker(color, 0.5F), 90);
        }

        NVGRenderer.roundedRectGradient(sliderX + dragAnim - 1, sliderY - 1.3f, 2, 5, 1, -1, ColorUtility.darker(-1, 0.1F), 90);

        final Number value = getProperty().getValue();

        String valueString;
        if (value.doubleValue() == value.intValue()) {
            valueString = String.valueOf(value.intValue());
        } else {
            valueString = String.format("%.3f", value.doubleValue()).replaceAll("0+$", "").replaceAll("\\.$", "");
        }

        if (getProperty().getSuffix() != null) {
            valueString += getProperty().getSuffix();
        }

        final float valueWidth = font.getStringWidth(valueString, 5.5F);
        final float valueX = Math.max(x + 4, Math.min(x + width - valueWidth - 4, sliderX + dragAnim - (valueWidth / 2)));
        font.drawString(valueString, valueX, y + 22f, 5.5F, ColorUtility.applyOpacity(-1, 0.8F));
    }

    @Override
    public void mouseClicked(double mouseX, double mouseY, int button) {
        if (HoverUtility.isHovering(x, y, width, height, mouseX, mouseY) && button == 0)
            dragging = true;
    }

    @Override
    public void mouseReleased(double mouseX, double mouseY, int button) {
        if (dragging && button == 0)
            dragging = false;
    }
}

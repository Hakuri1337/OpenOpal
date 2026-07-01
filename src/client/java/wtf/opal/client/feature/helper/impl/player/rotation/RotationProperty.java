package wtf.opal.client.feature.helper.impl.player.rotation;

import wtf.opal.client.feature.helper.impl.player.rotation.model.EnumRotationModel;
import wtf.opal.client.feature.helper.impl.player.rotation.model.IRotationModel;
import wtf.opal.client.feature.module.property.Property;
import wtf.opal.client.feature.module.property.impl.GroupProperty;
import wtf.opal.client.feature.module.property.impl.mode.ModeProperty;
import wtf.opal.client.feature.module.property.impl.number.NumberProperty;

import java.util.Arrays;
import java.util.stream.Stream;

public final class RotationProperty {

    private final ModeProperty<EnumRotationModel> modelProperty;

    private final NumberProperty maxAngle,
            driftIntensity, jitterIntensity;

    private final GroupProperty groupProperty;

    public RotationProperty(final IRotationModel defaultModel, final Property<?>... customProperties) {
        this.modelProperty = new ModeProperty<>("Model", defaultModel.getEnum());

        this.maxAngle = new NumberProperty("Max angle", 90, 5, 360, 5)
                .hideIf(() -> this.modelProperty.getValue() == EnumRotationModel.INSTANT);

        this.driftIntensity = new NumberProperty("Drift intensity", 1.2, 0.5, 2, 0.1)
                .hideIf(() -> this.modelProperty.getValue() != EnumRotationModel.ORGANIC);
        this.jitterIntensity = new NumberProperty("Jitter intensity", 0.12, 0, 0.3, 0.01)
                .hideIf(() -> this.modelProperty.getValue() != EnumRotationModel.ORGANIC);

        final Property<?>[] properties = Stream.concat(
                Stream.of(this.modelProperty, this.maxAngle, this.driftIntensity, this.jitterIntensity),
                Arrays.stream(customProperties)
        ).toArray(Property<?>[]::new);

        this.groupProperty = new GroupProperty("Rotation", properties);
    }

    public GroupProperty get() {
        return this.groupProperty;
    }

    public int getMaxAngle() {
        return this.maxAngle.getValue().intValue();
    }

    public double getDriftIntensity() {
        return this.driftIntensity.getValue();
    }

    public double getJitterIntensity() {
        return this.jitterIntensity.getValue();
    }

    public IRotationModel createModel() {
        return this.modelProperty.getValue().supply(this);
    }

    public boolean isModel(final EnumRotationModel model) {
        return this.modelProperty.getValue() == model;
    }

}

package wtf.opal.client.feature.module.impl.visual.overlay;

import net.minecraft.util.Colors;
import wtf.opal.client.OpalClient;
import wtf.opal.client.feature.helper.impl.render.ScaleProperty;
import wtf.opal.client.feature.module.Module;
import wtf.opal.client.feature.module.ModuleCategory;
import wtf.opal.client.feature.module.impl.visual.overlay.impl.client.ClientElements;
import wtf.opal.client.feature.module.impl.visual.overlay.impl.dynamicisland.DynamicIslandElement;
import wtf.opal.client.feature.module.impl.visual.overlay.impl.modulelist.ToggledModulesElement;
import wtf.opal.client.feature.module.impl.visual.overlay.impl.notifications.NotificationsElement;
import wtf.opal.client.feature.module.impl.visual.overlay.impl.targetinfo.TargetInfoElement;
import wtf.opal.client.feature.module.property.impl.ColorProperty;
import wtf.opal.client.feature.module.property.impl.GroupProperty;
import wtf.opal.client.feature.module.property.impl.bool.BooleanProperty;
import wtf.opal.client.feature.module.property.impl.mode.ModeProperty;
import wtf.opal.event.impl.client.PostClientInitializationEvent;
import wtf.opal.event.impl.client.PropertyUpdateEvent;
import wtf.opal.event.impl.game.PostGameTickEvent;
import wtf.opal.event.impl.render.RenderBloomEvent;
import wtf.opal.event.impl.render.RenderScreenEvent;
import wtf.opal.event.impl.render.ResolutionChangeEvent;
import wtf.opal.event.subscriber.Subscribe;
import wtf.opal.utility.render.ClientTheme;

import java.util.ArrayList;
import java.util.List;

public final class
OverlayModule extends Module {

    // Theme
    private final ModeProperty<ClientTheme> themeMode = new ModeProperty<>("Theme", ClientTheme.OPAL, true);
    public static final ColorProperty primaryColorProperty = new ColorProperty("Primary color", Colors.BLACK);
    public static final ColorProperty secondaryColorProperty = new ColorProperty("Secondary color", Colors.BLACK);

    // Minecraft elements
    private final BooleanProperty statusEffectOverlayEnabled = new BooleanProperty("Enabled", false);
    private final BooleanProperty scoreboardEnabled = new BooleanProperty("Enabled", true);
    private final BooleanProperty scoreboardTextShadow = new BooleanProperty("Text shadow", true).hideIf(() -> !scoreboardEnabled.getValue());
    private final ScaleProperty scoreboardScale = ScaleProperty.newMinecraftElement();
    private final BooleanProperty bossbarEnabled = new BooleanProperty("Enabled", false);

    private final BooleanProperty dynamicIslandLeftAligned = new BooleanProperty("Left-aligned", false);
    private final BooleanProperty dynamicIslandLiquidGlass = new BooleanProperty("Liquid glass", true);

    private final List<IOverlayElement> elements = new ArrayList<>();

    private final TargetInfoElement targetInfo;
    private final ToggledModulesElement toggledModules;
    private final NotificationsElement notifications;

    public OverlayModule() {
        super("Overlay", "Renders the clients display.", ModuleCategory.VISUAL);

        primaryColorProperty.hideIf(() -> !themeMode.is(ClientTheme.CUSTOM));
        secondaryColorProperty.hideIf(() -> !themeMode.is(ClientTheme.CUSTOM));

        this.setEnabled(true);
        this.addProperties(
                themeMode, primaryColorProperty, secondaryColorProperty,
                new GroupProperty("Minecraft elements",
                        new GroupProperty(
                                "Status effect overlay",
                                statusEffectOverlayEnabled
                        ),
                        new GroupProperty(
                                "Scoreboard",
                                scoreboardScale.get(), scoreboardEnabled, scoreboardTextShadow
                        ),
                        new GroupProperty(
                                "Bossbar",
                                bossbarEnabled
                        )
                )
        );

        this.targetInfo = this.register(new TargetInfoElement(this));
        this.toggledModules = this.register(new ToggledModulesElement(this));
        this.register(new ClientElements(this));
        this.addProperties(new GroupProperty("Dynamic island", dynamicIslandLeftAligned, dynamicIslandLiquidGlass));
        this.notifications = this.register(new NotificationsElement(this));

        this.register(new DynamicIslandElement(this));
    }

    private <T extends IOverlayElement> T register(T element) {
        this.elements.add(element);
        return element;
    }

    @Override
    protected void onDisable() {
        this.elements.forEach(IOverlayElement::onDisable);
    }

    @Override
    protected void onEnable() {
        if (OpalClient.getInstance().isPostInitialization()) {
            this.toggledModules.initialize();
            this.targetInfo.initialize();
        }
    }

    @Subscribe
    public void onPostClientInitialization(PostClientInitializationEvent event) {
        this.toggledModules.initialize();
        this.targetInfo.initialize();
    }

    @Subscribe
    public void onPropertyUpdate(PropertyUpdateEvent event) {
        if (this.toggledModules != null) {
            this.toggledModules.markSortingDirty();
        }
    }

    @Subscribe(priority = -20)
    public void onRenderScreen(RenderScreenEvent event) {
        for (IOverlayElement element : this.elements) {
            if (element.isActive()) {
                element.render(event.drawContext(), event.tickDelta(), false);
            }
        }
    }

    @Subscribe(priority = -20)
    public void onBloomRender(RenderBloomEvent event) {
        for (IOverlayElement element : this.elements) {
            if (element.isActive() && element.isBloom()) {
                element.render(event.drawContext(), event.tickDelta(), true);
            }
        }
    }

    @Subscribe
    public void onResize(ResolutionChangeEvent event) {
        this.elements.forEach(IOverlayElement::onResize);
    }

    @Subscribe
    public void onPostTick(PostGameTickEvent event) {
        for (IOverlayElement element : this.elements) {
            if (element.isActive()) {
                element.tick();
            }
        }
    }

    public ModeProperty<ClientTheme> getThemeMode() {
        return themeMode;
    }

    public ToggledModulesElement getToggledModules() {
        return toggledModules;
    }

    public NotificationsElement getNotifications() {
        return notifications;
    }

    public boolean isDynamicIslandLeftAligned() {
        return dynamicIslandLeftAligned.getValue();
    }

    public boolean isDynamicIslandLiquidGlass() {
        return dynamicIslandLiquidGlass.getValue();
    }

    public boolean isScoreboardTextShadow() {
        return scoreboardEnabled.getValue() && scoreboardTextShadow.getValue();
    }

    public float getScoreboardScale() {
        return scoreboardEnabled.getValue() ? scoreboardScale.getScale() : 1;
    }

    public boolean isBossbarEnabled() {
        return bossbarEnabled.getValue();
    }

    public boolean isStatusEffectOverlayEnabled() {
        return statusEffectOverlayEnabled.getValue();
    }

}

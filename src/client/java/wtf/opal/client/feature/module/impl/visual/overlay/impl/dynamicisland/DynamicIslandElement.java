package wtf.opal.client.feature.module.impl.visual.overlay.impl.dynamicisland;

import com.google.common.collect.Lists;
import net.minecraft.client.gui.DrawContext;
import wtf.opal.client.feature.module.impl.visual.overlay.IOverlayElement;
import wtf.opal.client.feature.module.impl.visual.overlay.OverlayModule;
import wtf.opal.client.feature.module.impl.visual.overlay.impl.dynamicisland.preset.DefaultIsland;
import wtf.opal.client.renderer.NVGRenderer;
import wtf.opal.event.EventDispatcher;
import wtf.opal.event.impl.client.ModuleToggleEvent;
import wtf.opal.event.subscriber.IEventSubscriber;
import wtf.opal.event.subscriber.Subscribe;
import wtf.opal.utility.render.animation.Animation;
import wtf.opal.utility.render.animation.Easing;

import java.util.Collections;
import java.util.List;

import static wtf.opal.client.Constants.mc;

public final class DynamicIslandElement implements IOverlayElement, IEventSubscriber {

    private static final List<IslandTrigger> ACTIVE_TRIGGERS = Lists.newArrayList(new DefaultIsland());
    private final OverlayModule module;

    public DynamicIslandElement(OverlayModule module) {
        this.module = module;
        EventDispatcher.subscribe(this);
    }

    private boolean positioned;

    @Override
    public void render(DrawContext context, float delta, boolean isBloom) {
        if (SORTING_DIRTY) {
            this.sort();
        }

        final IslandTrigger trigger = this.getDecidingTrigger();
        final boolean custom;
        float width = trigger.getIslandWidth(), height = trigger.getIslandHeight();
        float x, y;
        if (trigger instanceof CustomIslandTrigger customTrigger) {
            x = customTrigger.getIslandX();
            y = customTrigger.getIslandY();
            custom = true;
        } else {
            x = this.module.isDynamicIslandLeftAligned() ? 4 : (mc.getWindow().getScaledWidth() - width) / 2.0F;
            y = this.module.isDynamicIslandLeftAligned() ? 6 : 10;
            custom = false;
        }

        this.updateAnimations(x, y, width, height);

        final float animatedX = this.xAnimation.getValue(), animatedY = this.yAnimation.getValue();
        final float animatedWidth = this.widthAnimation.getValue(), animatedHeight = this.heightAnimation.getValue();

        final float progress = Math.min(1, this.heightAnimation.getProgress());

        final Runnable render = () -> trigger.renderIsland(context, animatedX, animatedY, animatedWidth, animatedHeight, progress);

        if (custom) {
            render.run();
        } else {
            this.renderIslandBackground(animatedX, animatedY, animatedWidth, animatedHeight);

            if (!(trigger instanceof DefaultIsland)) {
                NVGRenderer.globalAlpha(progress);
            }
            NVGRenderer.scissor(animatedX, animatedY, animatedWidth, animatedHeight, render);
            NVGRenderer.globalAlpha(1);
        }
    }

    @Override
    public void onResize() {
        this.positioned = false;
    }

    @Subscribe
    public void onModuleToggle(ModuleToggleEvent event) {
        if (event.getModule() instanceof IslandTrigger trigger) {
            if (event.isEnabled()) {
                addTrigger(trigger);
            } else {
                removeTrigger(trigger);
            }
        }
    }

    public static void addTrigger(IslandTrigger trigger) {
        if (!ACTIVE_TRIGGERS.contains(trigger)) {
            ACTIVE_TRIGGERS.add(trigger);
            SORTING_DIRTY = true;
        }
    }

    public static void removeTrigger(IslandTrigger trigger) {
        if (ACTIVE_TRIGGERS.remove(trigger)) {
            SORTING_DIRTY = true;
        }
    }

    private static boolean SORTING_DIRTY;

    private void sort() {
        Collections.sort(ACTIVE_TRIGGERS);
        SORTING_DIRTY = false;
    }

    private void updateAnimations(float x, float y, float width, float height) {
        if (!this.positioned) {
            this.xAnimation.setValue(x);
            this.yAnimation.setValue(y);

            this.widthAnimation.setValue(width);
            this.heightAnimation.setValue(height);

            this.positioned = true;
        } else {
            this.xAnimation.run(x);
            this.yAnimation.run(y);

            this.widthAnimation.run(width);
            this.heightAnimation.run(height);
        }
    }

    public void renderIslandBackground(float x, float y, float width, float height) {
        NVGRenderer.roundedRect(x + 1, y + 1, width - 2, height - 2, 13, NVGRenderer.BLUR_PAINT);
        NVGRenderer.roundedRect(x + 1, y + 1, width - 2, height - 2, 13, 0x80090909);
    }

    private final Animation xAnimation = new Animation(Easing.DYNAMIC_ISLAND, 250);
    private final Animation yAnimation = new Animation(Easing.DYNAMIC_ISLAND, 250);

    private final Animation widthAnimation = new Animation(Easing.DYNAMIC_ISLAND, 250);
    private final Animation heightAnimation = new Animation(Easing.DYNAMIC_ISLAND, 250);

    public boolean isAnimationFinished() {
        return this.xAnimation.isFinished();
    }

    public float getAnimatedX() {
        return this.xAnimation.getValue();
    }

    public float getAnimatedY() {
        return this.yAnimation.getValue();
    }

    public float getAnimatedWidth() {
        return this.widthAnimation.getValue();
    }

    public float getAnimatedHeight() {
        return this.heightAnimation.getValue();
    }

    @Override
    public boolean isActive() {
        return !(this.getDecidingTrigger() instanceof CustomIslandTrigger);
    }

    private IslandTrigger getDecidingTrigger() {
        return ACTIVE_TRIGGERS.getFirst();
    }

    @Override
    public boolean isBloom() {
        return true;
    }
}
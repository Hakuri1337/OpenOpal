package wtf.opal.client.feature.module.impl.visual.esp;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.render.Frustum;
import net.minecraft.component.type.AttributeModifierSlot;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec2f;
import org.joml.Vector4d;
import wtf.opal.client.feature.helper.impl.LocalDataWatch;
import wtf.opal.client.feature.helper.impl.player.slot.SlotHelper;
import wtf.opal.client.feature.helper.impl.render.FrustumHelper;
import wtf.opal.client.feature.helper.impl.target.TargetList;
import wtf.opal.client.feature.helper.impl.target.TargetProperty;
import wtf.opal.client.feature.helper.impl.target.impl.TargetLivingEntity;
import wtf.opal.client.feature.module.Module;
import wtf.opal.client.feature.module.ModuleCategory;
import wtf.opal.client.feature.module.property.impl.bool.MultipleBooleanProperty;
import wtf.opal.client.renderer.MinecraftRenderer;
import wtf.opal.client.renderer.NVGRenderer;
import wtf.opal.client.renderer.repository.FontRepository;
import wtf.opal.client.renderer.text.NVGTextRenderer;
import wtf.opal.event.impl.render.RenderBloomEvent;
import wtf.opal.event.impl.render.RenderScreenEvent;
import wtf.opal.event.subscriber.Subscribe;
import wtf.opal.mixin.WorldRendererAccessor;
import wtf.opal.utility.player.BlockUtility;
import wtf.opal.utility.player.PlayerUtility;
import wtf.opal.utility.render.ColorUtility;
import wtf.opal.utility.render.ESPUtility;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.lwjgl.nanovg.NanoVG.nvgShapeAntiAlias;
import static wtf.opal.client.Constants.VG;
import static wtf.opal.client.Constants.mc;

public final class ESPModule extends Module {

    private final ESPSettings settings = new ESPSettings(this);

    private static final NVGTextRenderer NAMETAG_FONT = FontRepository.getFont("productsans-bold");
    private static final NVGTextRenderer ICON_FONT = FontRepository.getFont("materialicons-regular");
    private static final DecimalFormat HEALTH_DF = new DecimalFormat("0.#");
    private static final float NAMETAG_FONT_SIZE = 5;

    public ESPModule() {
        super("ESP", "Extra sensory perception.", ModuleCategory.VISUAL);
    }

    @Subscribe
    public void onRenderScreen(final RenderScreenEvent event) {
        this.render(event.drawContext(), event.tickDelta());
    }

    @Subscribe
    public void onBloomRender(final RenderBloomEvent event) {
        if (settings.getBloom())
            this.render(event.drawContext(), event.tickDelta());
    }

    private void render(final DrawContext drawContext, final float tickDelta) {
        final Frustum frustum = FrustumHelper.get();

        if(frustum == null) {
            return;
        }

        final TargetList targetList = LocalDataWatch.getTargetList();
        if (targetList == null || mc.player == null) {
            return;
        }

        final TargetProperty targetProperty = settings.getTargetProperty();

        final List<TargetLivingEntity> targets = targetList.collectTargets(targetProperty.getTargetFlags(), TargetLivingEntity.class);
        for (final TargetLivingEntity target : targets) {
            if (target.isLocal() && (!targetProperty.isLocalPlayer() || mc.options.getPerspective().isFirstPerson())) {
                continue;
            }

            final LivingEntity entity = target.getEntity();

            if (frustum.isVisible(entity.getBoundingBox())) {
                renderBoxIn2D(drawContext, entity, tickDelta);
            }
        }
    }

    private void renderBoxIn2D(final DrawContext drawContext, final LivingEntity entity, final float tickDelta) {
        final Vector4d projection = ESPUtility.getEntityPositionsOn2D(entity, tickDelta);

        final float x = (float) projection.x;
        final float y = (float) projection.y;
        final float w = (float) projection.z;
        final float h = (float) projection.w;

        final float thickness = 0.5F;

        nvgShapeAntiAlias(VG, false);

        if (settings.getBox()) {
            renderFullBox(x, y, w, h, thickness, ColorUtility.applyOpacity(entity.getTeamColorValue(), 1F));
        }

        if (settings.getHealthBar()) {
            renderHealthBar(x, y, w, h, thickness, entity.getHealth() / entity.getMaxHealth());
        }

        nvgShapeAntiAlias(VG, true);

        if (settings.areNameTagsEnabled()) {
            renderNameTag(drawContext, entity, x, y, w);
        }
    }

    private void renderHealthBar(final float x, final float y, final float w, final float h, final float thickness, final float healthValue) {
        if (settings.getHealthBarStroke()) {
            NVGRenderer.rectStroke(
                    x - (thickness * 2) - 0.5F - (settings.getBox() && settings.getBoxStroke() ? 0.5F : 0),
                    y + (h - (h * healthValue)),
                    thickness,
                    h * healthValue,
                    thickness,
                    0xff00ff00,
                    0xff000000
            );
        } else {
            NVGRenderer.rect(
                    x - thickness - 0.5F - (settings.getBox() && settings.getBoxStroke() ? 0.5F : 0),
                    y + (h - (h * healthValue)),
                    thickness,
                    h * healthValue,
                    0xff00ff00
            );
        }
    }

    private void renderFullBox(final float x, final float y, final float w, final float h, final float thickness, final int color) {
        if (!settings.getBoxStroke()) {
            NVGRenderer.rectOutline(
                    x,
                    y,
                    w,
                    h,
                    thickness,
                    color
            );
        } else {
            NVGRenderer.rectOutlineStroke(
                    x,
                    y,
                    w,
                    h,
                    thickness,
                    thickness * 3,
                    color,
                    0xff000000
            );
        }
    }

    private void renderNameTag(final DrawContext drawContext, final LivingEntity entity, final float x, final float y, final float w) {
        final MultipleBooleanProperty indicators = settings.getNameTagIndicators();
        final MultipleBooleanProperty elements = settings.getNameTagElements();

        final List<NameTagElement> elementList = new ArrayList<>();

        if (indicators.getProperty("Strength").getValue() && LocalDataWatch.get().getStrengthedPlayerList().contains(entity.getName().getString())) {
            elementList.add(new NameTagElement(new NameTagIcon("\uefe4", 0.25F), 0xFFFF0000));
        }

        if (indicators.getProperty("Sneaking").getValue() && entity.isInSneakingPose()) {
            elementList.add(new NameTagElement(new NameTagIcon("\uf19f"), 0xFFFF5555));
        }

        if (indicators.getProperty("Invisible").getValue() && entity.isInvisible()) {
            elementList.add(new NameTagElement(new NameTagIcon("\ue8f5", 0.3F), 0xFFAAAAAA));
        }

        if (indicators.getProperty("Blocking").getValue() && entity instanceof PlayerEntity player && (BlockUtility.isBlockUseState(player) || BlockUtility.isForceBlockUseState(player) || (player == mc.player && (BlockUtility.isNoSlowBlockingState() || BlockUtility.isFakeABBlockingState(player))))) {
            elementList.add(new NameTagElement(new NameTagIcon("\ue1d5", 0.15F), 0xFF41AF7D));
        }

        if (elements.getProperty("Distance").getValue() && entity != mc.player) {
            final NameTagIcon distanceIcon = new NameTagIcon("\ue55c", NameTagIconPosition.RIGHT);
            elementList.add(new NameTagElement(distanceIcon, String.valueOf((int) Math.floor(entity.distanceTo(mc.player))), 0xFFAAAAAA));
        }

        if (elements.getProperty("Name").getValue()) {
            int color = -1;
            String name = Formatting.WHITE + PlayerUtility.getFormattedEntityName(entity);

            elementList.add(new NameTagElement(name, color));
        }

        if (elements.getProperty("Health").getValue()) {
            final NameTagIcon redHeartIcon = new NameTagIcon(Formatting.RED + "\uE87D", NameTagIconPosition.RIGHT);
            elementList.add(new NameTagElement(redHeartIcon, HEALTH_DF.format(entity.getHealth()), -1));
            if (entity.getAbsorptionAmount() > 0) {
                final NameTagIcon normalHeartIcon = new NameTagIcon("\uE87D", NameTagIconPosition.RIGHT);
                elementList.add(new NameTagElement(normalHeartIcon, HEALTH_DF.format(entity.getAbsorptionAmount()), 0xFFFFC247));
            }
        }

        renderNameTagElements(elementList, calculateStartingPosition(elementList, x, y, w));

        if (elements.getProperty("Equipment").getValue()) {
            renderEquipment(drawContext, entity, x, y, w, !elementList.isEmpty());
        }
    }

    private void renderEquipment(final DrawContext drawContext, final LivingEntity entity, final float x, final float y, final float w, final boolean hasNametagElements) {
        final List<ItemStack> equipment = new ArrayList<>();

        for (final EquipmentSlot equipmentSlot : AttributeModifierSlot.ARMOR) {
            if (equipmentSlot.getType() != EquipmentSlot.Type.HUMANOID_ARMOR) {
                continue;
            }
            final ItemStack stack = entity.getEquippedStack(equipmentSlot);
            if (stack.isEmpty()) {
                continue;
            }
            equipment.add(stack);
        }

        final ItemStack mainHandStack = entity == mc.player && BlockUtility.isNoSlowBlockingState()
                ? SlotHelper.getInstance().getMainHandStack(mc.player)
                : entity.getMainHandStack();

        if (!mainHandStack.isEmpty()) {
            equipment.add(mainHandStack);
        }

        final float scale = 0.65F;
        final float stackTextScale = 0.6F;

        MinecraftRenderer.addToQueue(() -> {
            for (int i = 0; i < equipment.size(); i++) {
                final ItemStack stack = equipment.get(i);
                final float stackX = x + w / 2 - (equipment.size() * scale * 8) + ((equipment.size() - i - 1) * scale * 16);

                drawContext.getMatrices().pushMatrix();
                drawContext.getMatrices().translate(stackX, y - (hasNametagElements ? 23.5F : 14));
                drawContext.getMatrices().scale(scale, scale);

                drawContext.getMatrices().scale(stackTextScale, stackTextScale);
                drawContext.getMatrices().translate(6, 12);

                drawContext.getMatrices().pushMatrix();

                drawContext.getMatrices().translate(-6, -12);
                drawContext.getMatrices().scale(1 / stackTextScale, 1 / stackTextScale);

                drawContext.drawItem(stack, 0, 0);
                drawContext.getMatrices().popMatrix();

                final AtomicInteger enchantmentCount = new AtomicInteger();
                EnchantmentHelper
                        .getEnchantments(stack)
                        .getEnchantmentEntries()
                        .forEach((entry) -> entry.getKey().getKey().ifPresent(key -> {
                            final String shortName = ESPUtility.ENCHANTMENT_NAMES.get(key);
                            if (shortName == null) {
                                return;
                            }
                            drawContext.drawText(
                                    mc.textRenderer,
                                    Text.of(shortName + entry.getIntValue()).asOrderedText(), 2, 7 + (-8 * enchantmentCount.getAndIncrement()), -1,  true);
                        }));
                drawContext.getMatrices().popMatrix();
            }
        });
    }

    private Vec2f calculateStartingPosition(final List<NameTagElement> elements, final float x, final float y, final float w) {
        float totalWidth = 0;

        for (int i = 0; i < elements.size(); i++) {
            final NameTagElement element = elements.get(i);

            if (element.text() != null) {
                totalWidth += NAMETAG_FONT.getStringWidth(element.text(), NAMETAG_FONT_SIZE);
            }

            if (element.icon() != null) {
                totalWidth += ICON_FONT.getStringWidth(element.icon().unicode(), NAMETAG_FONT_SIZE);
            }

            if (i < elements.size() - 1) {
                totalWidth += 5;
            }
        }

        final float middleX = x + w / 2;
        final float startX = middleX - totalWidth / 2;

        return new Vec2f(startX, y - 4.5F);
    }

    private void renderNameTagElements(final List<NameTagElement> elements, final Vec2f position) {
        float currentX = position.x;

        for (final NameTagElement element : elements) {
            final boolean hasText = element.text() != null;
            final boolean hasIcon = element.icon() != null;

            final float elementWidth = hasText ? NAMETAG_FONT.getStringWidth(element.text(), NAMETAG_FONT_SIZE) : 0;
            final NameTagIcon icon = element.icon();

            final float iconWidth = hasIcon
                    ? ICON_FONT.getStringWidth(icon.unicode(), NAMETAG_FONT_SIZE)
                    : 0;

            // draw bg
            final float bgPadding = 2;
            final float bgRadius = 2;
            NVGRenderer.roundedRect(
                    currentX - bgPadding,
                    position.y - bgPadding - 4.5F,
                    elementWidth + iconWidth + bgPadding * 2,
                    NAMETAG_FONT_SIZE + bgPadding * 2,
                    bgRadius,
                    NVGRenderer.BLUR_PAINT
            );
            NVGRenderer.roundedRect(
                    currentX - bgPadding,
                    position.y - bgPadding - 4.5F,
                    elementWidth + iconWidth + bgPadding * 2,
                    NAMETAG_FONT_SIZE + bgPadding * 2,
                    bgRadius,
                    ColorUtility.applyOpacity(0xff000000, 0.5F)
            );

            float textX = currentX;

            // draw left icon
            if (hasIcon && icon.position() == NameTagIconPosition.LEFT) {
                ICON_FONT.drawString(icon.unicode(), currentX + icon.horizontalOffset(), position.y + 1, NAMETAG_FONT_SIZE, element.color());
                textX += iconWidth;
            }

            // draw text
            if (hasText) {
                NAMETAG_FONT.drawString(element.text(), textX, position.y, NAMETAG_FONT_SIZE, element.color());
            }

            // draw right icon
            if (hasIcon && icon.position() == NameTagIconPosition.RIGHT) {
                ICON_FONT.drawString(icon.unicode(), textX + elementWidth + icon.horizontalOffset(), position.y + 1, NAMETAG_FONT_SIZE, element.color());
            }

            currentX += elementWidth + iconWidth + 5;
        }
    }

    public ESPSettings getSettings() {
        return settings;
    }

}

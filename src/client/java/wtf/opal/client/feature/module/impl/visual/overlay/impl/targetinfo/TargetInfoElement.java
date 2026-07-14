package wtf.opal.client.feature.module.impl.visual.overlay.impl.targetinfo;

import com.ibm.icu.impl.Pair;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.component.type.AttributeModifierSlot;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.mob.PiglinEntity;
import net.minecraft.entity.mob.SkeletonEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Colors;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.joml.Vector3f;
import wtf.opal.client.Constants;
import wtf.opal.client.OpalClient;
import wtf.opal.client.feature.helper.impl.LocalDataWatch;
import wtf.opal.client.feature.helper.impl.server.impl.HypixelServer;
import wtf.opal.client.feature.module.impl.combat.killaura.KillAuraModule;
import wtf.opal.client.feature.module.impl.combat.killaura.target.CurrentTarget;
import wtf.opal.client.feature.module.impl.visual.overlay.IOverlayElement;
import wtf.opal.client.feature.module.impl.visual.overlay.OverlayModule;
import wtf.opal.client.feature.module.property.impl.ScreenPositionProperty;
import wtf.opal.client.renderer.MinecraftRenderer;
import wtf.opal.client.renderer.NVGRenderer;
import wtf.opal.client.renderer.repository.FontRepository;
import wtf.opal.client.renderer.text.NVGTextRenderer;
import wtf.opal.utility.render.ColorUtility;
import wtf.opal.utility.render.ESPUtility;
import wtf.opal.utility.render.OrderedTextVisitor;
import wtf.opal.utility.render.animation.Animation;
import wtf.opal.utility.render.animation.Easing;

import java.awt.*;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.lwjgl.nanovg.NanoVG.*;
import static org.lwjgl.nanovg.NanoVGGL3.NVG_IMAGE_NODELETE;
import static org.lwjgl.nanovg.NanoVGGL3.nvglCreateImageFromHandle;
import static wtf.opal.client.Constants.VG;
import static wtf.opal.client.Constants.mc;

public final class TargetInfoElement implements IOverlayElement {

    private static final NVGTextRenderer BOLD_FONT = FontRepository.getFont("productsans-bold");
    private static final NVGTextRenderer MEDIUM_FONT = FontRepository.getFont("productsans-medium");
    private static final NVGTextRenderer ICON_FONT = FontRepository.getFont("materialicons-regular");
    private static final DecimalFormat HEALTH_DF = new DecimalFormat("0.#");

    private final Animation targetAnimation, healthAnimation;
    private final TargetInfoSettings settings;
    private Target currentTarget, lastTarget;

    public TargetInfoElement(final OverlayModule module) {
        this.settings = new TargetInfoSettings(module);

        this.targetAnimation = new Animation(Easing.EASE_OUT_EXPO, 200);
        this.targetAnimation.setValue(1);

        this.healthAnimation = new Animation(Easing.EASE_OUT_EXPO, 1000);
    }

    public void initialize() {
    }

    @Override
    public void render(DrawContext context, float delta, boolean isBloom) {
        final Target target = this.getTarget();
        if (target == null) {
            return;
        }

        final float scale = this.settings.getScale();

        final float targetNameSize = 6;
        final float hpSize = 5;

        final String targetName = Formatting.WHITE + target.getFormattedName();
        final int targetNameColor = -1;

        final int skinTextureGlId = isBloom ? -1 : this.getSkinTextureGlId(target.entity);

        final float padding = 3;
        final float headOffset = 22.5F;
        final float equipmentWidth = 55;

        final ScreenPositionProperty screenPosition = this.settings.getScreenPosition();

        final float width = (padding * 2) + Math.max(50, Math.max(equipmentWidth, BOLD_FONT.getStringWidth(targetName, targetNameSize))) + headOffset + 1;
        final float height = (padding * 2) + 25.5F;

        final float x = screenPosition.getScaledX();
        final float y = screenPosition.getScaledY();

        screenPosition.setWidth(width * scale);
        screenPosition.setHeight(height * scale);

        final float targetAnimationProgress = this.targetAnimation.getValue();
        final float healthAnimationProgress = this.healthAnimation.getValue();

        final Pair<Integer, Integer> theme = ColorUtility.getClientTheme();

        final float trueHealthPercent = MathHelper.clamp(
                (target.entity.getHealth() + target.entity.getAbsorptionAmount()) / (target.entity.getMaxHealth() + target.entity.getAbsorptionAmount()),
                0, 1
        );

        this.healthAnimation.run(trueHealthPercent);


        final String finalTargetName = targetName;
        NVGRenderer.scale(scale, x, y, 0, 0, () -> {
            NVGRenderer.globalAlpha(targetAnimationProgress);

            // background
            NVGRenderer.roundedRect(x, y, width, height, 4, NVGRenderer.BLUR_PAINT);
            NVGRenderer.roundedRect(x, y, width, height, 4, 0x80090909);

            // name
            BOLD_FONT.drawString(finalTargetName, x + padding + headOffset, y + 9, targetNameSize, targetNameColor);

            // health
            final float absorption = target.entity.getAbsorptionAmount();
            final float heartWidth = ICON_FONT.getStringWidth("\uE87D", hpSize);
            final String hp = HEALTH_DF.format(target.entity.getHealth() + absorption);

            ICON_FONT.drawString((absorption > 0 ? "" : Formatting.RED) + "\uE87D", x + width - (padding * 2.5F), y + 29, hpSize, 0xFFFFC247);
            MEDIUM_FONT.drawString(hp, x + width - padding - MEDIUM_FONT.getStringWidth(hp, hpSize) - heartWidth - 0.25F, y + 28.5F, hpSize, -1);

            // health bar
            {
                final float healthBarWidth = width - (padding * 2.75F) - MEDIUM_FONT.getStringWidth(hp.length() > 2 ? hp : "88.", hpSize) - heartWidth;

                // full width bg
                NVGRenderer.roundedRect(
                        x + padding - 0.125F, y + 24.75F,
                        healthBarWidth, 4, 5 / 3F,
                        ColorUtility.applyOpacity(ColorUtility.darker(theme.second, 0.8F), 0.6F)
                );

                // animated health bg
                if (healthAnimationProgress > 0.01) {
                    NVGRenderer.roundedRectGradient(
                            x + padding - 0.125F, y + 24.75F,
                            healthAnimationProgress * healthBarWidth, 4, 5 / 3F,
                            ColorUtility.darker(theme.first, 0.6F), ColorUtility.darker(theme.second, 0.6F), 0
                    );
                }

                // true health
                if (trueHealthPercent > 0.01) {
                    NVGRenderer.roundedRectGradient(
                            x + padding - 0.125F, y + 24.75F,
                            trueHealthPercent * healthBarWidth, 4, 5 / 3F,
                            theme.first, theme.second, 0
                    );

                    NVGRenderer.roundedRectGradient(
                            x + padding - 0.125F, y + 24.75F,
                            trueHealthPercent * healthBarWidth, 4, 5 / 3F,
                         Color.TRANSLUCENT, ColorUtility.applyOpacity(0xFF000000, 0.6F), 90
                    );
                }
            }

            // head
            renderHead:
            {
                if (skinTextureGlId == -1) {
                    break renderHead;
                }

                nvgBeginPath(VG);

                final float headX = x + padding + 0.25F;
                final float headY = y + padding;
                final float headScale = 8 / 3F;
                final float size = 19.5F;

                final int skinTextureHandle = target.getSkinTextureHandle(skinTextureGlId);
                nvgImagePattern(VG, headX - ((64 - 4.8F) / headScale), headY - ((64 - 3) / headScale),
                        64 * headScale, 64 * headScale, 0, skinTextureHandle, 1, NVGRenderer.NVG_PAINT);
//                    nvgShapeAntiAlias(VG, false);

                if (target.entity.hurtTime > 0) {
                    final float damageFactor = target.entity.hurtTime / (float) target.entity.maxHurtTime;
                    final float reductionFactor = 0.6F;
                    final float r = Math.min(1, 1 + ((1 - reductionFactor) * damageFactor));
                    final float g = 1 - (damageFactor * reductionFactor);
                    final float b = 1 - (damageFactor * reductionFactor);
                    NVGRenderer.applyColor(new Color(r, g, b).getRGB(), NVGRenderer.NVG_COLOR_1);
                    NVGRenderer.NVG_PAINT.innerColor(NVGRenderer.NVG_COLOR_1);
                }

                nvgFillPaint(VG, NVGRenderer.NVG_PAINT);
                nvgRoundedRect(VG, headX, headY, size, size, 2);
                nvgFill(VG);
                nvgClosePath(VG);
//                    nvgShapeAntiAlias(VG, true);
            }

            // render equipment
            {
                final List<ItemStack> equipment = new ArrayList<>();

                for (final EquipmentSlot equipmentSlot : AttributeModifierSlot.ARMOR) {
                    if (equipmentSlot.getType() != EquipmentSlot.Type.HUMANOID_ARMOR) {
                        continue;
                    }
                    equipment.add(target.entity.getEquippedStack(equipmentSlot));
                }

                equipment.add(target.entity.getMainHandStack());
                Collections.reverse(equipment);

                final float stackScale = 0.625F * scale;
                final float stackTextScale = 0.6F;

                final int equipmentCount = equipment.size();

                // slot backgrounds
                for (int i = 0; i < equipmentCount; i++) {
                    final float boxX = x + (i * 11.5F) + padding + headOffset - 0.5F;
                    final float boxY = y + padding + 8.5F;
                    NVGRenderer.roundedRect(boxX, boxY, 10.5F, 10.5F, 1, ColorUtility.applyOpacity(Colors.BLACK, 0.2F));
                }

                // reset alpha
                NVGRenderer.globalAlpha(1);

                MinecraftRenderer.addToQueue(() -> {
                    // draw now so alpha doesn't affect previously queued items
                    context.createNewRootLayer();

                    GlStateManager._enableBlend();
//                    RenderSystem.setShaderColor(1, 1, 1, targetAnimationProgress);
//                    DiffuseLighting.disableGuiDepthLighting();

                    for (int i = 0; i < equipmentCount; i++) {
                        final float offsetX = (i * 11.6F) + padding + headOffset - 0.5F / scale;
                        final float offsetY = padding + 8.5F;
                        final float stackX = x + offsetX * scale;
                        final float stackY = y + offsetY * scale;

                        context.getMatrices().pushMatrix();
                        context.getMatrices().translate(stackX, stackY);
                        context.getMatrices().scale(stackScale, stackScale);

                        context.getMatrices().scale(stackTextScale, stackTextScale);
//                        context.getMatrices().translate(6, 8);

                        final ItemStack stack = equipment.get(i);
                        context.getMatrices().pushMatrix();

                        context.getMatrices().transform(new Vector3f(-6, -12, -200));
                        context.getMatrices().scale(1 / stackTextScale, 1 / stackTextScale);

                        if (stack.getItem() instanceof BlockItem) {
                            if (targetAnimationProgress >= 0.5F) {
                                context.drawItem(stack, 0, 0, -200);
                            }
                        } else {
                            context.drawItem(stack, 0, 0, -200);
                        }
                        context.getMatrices().popMatrix();

                        EnchantmentHelper
                                .getEnchantments(stack)
                                .getEnchantmentEntries()
                                .forEach((entry) -> entry.getKey().getKey().ifPresent(key -> {
                                    final String shortName = ESPUtility.ENCHANTMENT_NAMES.get(key);
                                    if (shortName == null) {
                                        return;
                                    }
                                    context.drawText(
                                            mc.textRenderer,
                                            Text.of(shortName + entry.getIntValue()).asOrderedText(), 2, 7, -1, true);
                                }));

                        context.getMatrices().popMatrix();
                    }
                    GlStateManager._disableBlend();
                });
            }
        });

        if (currentTarget != null) {
            lastTarget = currentTarget;
        }
    }

    @Override
    public boolean isActive() {
        return this.settings.isEnabled();
    }

    private Target getTarget() {
        LivingEntity target = LocalDataWatch.get().lastEntityAttack.getRight();
        if (target != null && !LocalDataWatch.getTargetList().hasTarget(target.getId())) {
            target = null;
        }

        if (target == null) {
            final KillAuraModule killAuraModule = OpalClient.getInstance().getModuleRepository().getModule(KillAuraModule.class);
            if (killAuraModule.isEnabled()) {
                final CurrentTarget killAuraTarget = killAuraModule.getTargeting().getTarget();
                if (killAuraTarget != null) {
                    target = killAuraTarget.getEntity();
                }
            }
        }

        final Target preCurrentTarget = this.currentTarget;
        final Target preLastTarget = this.lastTarget;

        if (target != null) {
            if (this.currentTarget == null || this.currentTarget.entity.getId() != target.getId()) {
                this.currentTarget = new Target(target);
            }
        } else {
            if (mc.currentScreen instanceof ChatScreen) {
                if (this.currentTarget == null || this.currentTarget.entity.getId() != mc.player.getId()) {
                    this.currentTarget = new Target(mc.player);
                }
            } else {
                this.currentTarget = null;
            }
        }

        Target activeTarget = this.currentTarget;
        if (activeTarget == null) {
            if (this.targetAnimation.isFinished()) {
                this.lastTarget = null;
            } else {
                activeTarget = this.lastTarget;
                this.targetAnimation.run(0);
            }
        } else {
            this.targetAnimation.setValue(1);
            this.targetAnimation.reset();
        }

        if (activeTarget != null) {
            activeTarget.updateFormattedName();
        }

        if (preCurrentTarget != null && preCurrentTarget.skinTextureHandle != -1 && this.lastTarget == preCurrentTarget && preCurrentTarget != this.currentTarget && preCurrentTarget != activeTarget) {
            // target switched (no animation)
            nvgDeleteImage(VG, preCurrentTarget.skinTextureHandle);
        } else if (this.currentTarget == null && this.lastTarget != null && this.lastTarget.skinTextureHandle != -1 && this.targetAnimation.getValue() == 0) {
            // target animated out
            nvgDeleteImage(VG, preLastTarget.skinTextureHandle);
            this.lastTarget = null;
        }

        return activeTarget;
    }

    private int getSkinTextureGlId(final LivingEntity entity) {
        final Identifier identifier = switch (entity) {
            case AbstractClientPlayerEntity player -> player.getSkin().body().texturePath();
            case SkeletonEntity ignored -> Identifier.ofVanilla("textures/entity/skeleton/skeleton.png");
            case ZombieEntity ignored -> Identifier.ofVanilla("textures/entity/zombie/zombie.png");
            case CreeperEntity ignored -> Identifier.ofVanilla("textures/entity/creeper/creeper.png");
            case PiglinEntity ignored -> Identifier.ofVanilla("textures/entity/piglin/piglin.png");
            default -> null;
        };
        if (identifier == null) {
            return -1;
        }
        return Integer.parseInt(mc.getTextureManager().getTexture(identifier).getGlTexture().getLabel());
    }

    private static final class Target {

        private final LivingEntity entity;
        private String formattedName;

        private int skinTextureHandle = -1;

        private Target(final LivingEntity entity) {
            this.entity = entity;
        }

        private String getFormattedName() {
            if (this.formattedName != null) {
                return this.formattedName;
            }
            return this.entity.getName().getString();
        }

        private void updateFormattedName() {
            if (this.entity.getDisplayName() == null) {
                return;
            }

            if (this.formattedName != null
                    && LocalDataWatch.get().getKnownServerManager().getCurrentServer() instanceof HypixelServer
                    && this.entity.getDisplayName().getStyle().getColor() == TextColor.fromFormatting(Formatting.GRAY)) {
                return;
            }

            final OrderedTextVisitor visitor = new OrderedTextVisitor();
            this.entity.getDisplayName().asOrderedText().accept(visitor);
            this.formattedName = visitor.getFormattedString();
        }

        private int getSkinTextureHandle(final int skinTextureGlId) {
            if (this.skinTextureHandle != -1) {
                return this.skinTextureHandle;
            }
            return this.skinTextureHandle = nvglCreateImageFromHandle(VG, skinTextureGlId, 64, 64, NVG_IMAGE_NODELETE);
        }

    }

    @Override
    public boolean isBloom() {
        return true;
    }
}

package wtf.opal.client.feature.module.impl.combat.killaura;

import com.google.common.base.Predicates;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.PotionItem;
import net.minecraft.item.ShieldItem;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import wtf.opal.client.OpalClient;
import wtf.opal.client.feature.helper.impl.player.mouse.MouseButton;
import wtf.opal.client.feature.helper.impl.player.mouse.MouseHelper;
import wtf.opal.client.feature.helper.impl.player.rotation.RotationHelper;
import wtf.opal.client.feature.helper.impl.player.slot.SlotHelper;
import wtf.opal.client.feature.helper.impl.player.swing.SwingDelay;
import wtf.opal.client.feature.module.Module;
import wtf.opal.client.feature.module.ModuleCategory;
import wtf.opal.client.feature.module.impl.combat.killaura.target.CurrentTarget;
import wtf.opal.client.feature.module.impl.combat.killaura.target.KillAuraTargeting;
import wtf.opal.client.feature.module.impl.combat.velocity.VelocityMode;
import wtf.opal.client.feature.module.impl.combat.velocity.VelocityModule;
import wtf.opal.client.feature.module.impl.visual.AnimationsModule;
import wtf.opal.client.feature.module.impl.world.scaffold.ScaffoldModule;
import wtf.opal.client.renderer.world.WorldRenderer;
import wtf.opal.event.impl.game.PreGameTickEvent;
import wtf.opal.event.impl.game.input.MouseHandleInputEvent;
import wtf.opal.event.impl.game.packet.ReceivePacketEvent;
import wtf.opal.event.impl.game.player.movement.KeepSprintEvent;
import wtf.opal.event.impl.game.player.movement.PostMovementPacketEvent;
import wtf.opal.event.impl.game.player.movement.PreMovementPacketEvent;
import wtf.opal.event.impl.game.player.movement.knockback.VelocityUpdateEvent;
import wtf.opal.event.impl.render.RenderWorldEvent;
import wtf.opal.event.subscriber.Subscribe;
import wtf.opal.utility.misc.math.MathUtility;
import wtf.opal.utility.misc.math.RandomUtility;
import wtf.opal.utility.player.PlayerUtility;
import wtf.opal.utility.player.RaycastUtility;
import wtf.opal.utility.render.ColorUtility;
import wtf.opal.utility.render.CustomRenderLayers;

import java.util.function.Predicate;

import static wtf.opal.client.Constants.mc;

public final class KillAuraModule extends Module {

    private final KillAuraSettings settings = new KillAuraSettings(this);
    private final KillAuraTargeting targeting = new KillAuraTargeting(this.settings);

    public KillAuraModule() {
        super(
                "KillAura",
                "Finds and attacks the most relevant nearby entities.",
                ModuleCategory.COMBAT
        );
    }

    public KillAuraSettings getSettings() {
        return settings;
    }

    @Override
    public String getSuffix() {
        return this.settings.getMode().toString();
    }

    public KillAuraTargeting getTargeting() {
        return targeting;
    }

    public boolean isTargeting() {
        return this.shouldRun() && this.targeting.getTarget() != null;
    }

    public boolean isFakeBlocking() {
        return this.settings.isFakeAutoBlock() && this.targeting.getTarget() != null && this.shouldRun();
    }

    public boolean requestVelocityResetAttack(final int clicks, final int windowTicks, final boolean wasSprinting, final Double sprintSlowdown) {
        if (!this.shouldRun() || this.targeting.getTarget() == null) {
            return false;
        }

        this.velocityResetAttacks = Math.max(this.velocityResetAttacks, Math.max(1, clicks));
        this.velocityResetWindowTicks = Math.max(1, windowTicks);
        return true;
    }

    @Subscribe
    public void onHandleInput(final MouseHandleInputEvent event) {
        if (isConsumingFoodOrPotion()) {
            return;
        }

        final CurrentTarget target = this.targeting.getTarget();
        if (target == null || mc.crosshairTarget == null || mc.crosshairTarget.getType() == HitResult.Type.MISS) {
            final double closestDistance = this.targeting.getClosestDistance();
            if (closestDistance <= this.settings.getSwingRange() && SwingDelay.isSwingAvailable(this.settings.getSwingCpsProperty()) && PlayerUtility.getBlockOver() == null) {
                final MouseButton leftButton = MouseHelper.getLeftButton();
                leftButton.setPressed(true, RandomUtility.getRandomInt(2));
                if (this.settings.isHideFakeSwings() && (mc.crosshairTarget == null || mc.crosshairTarget.getType() != HitResult.Type.ENTITY)) {
                    leftButton.setShowSwings(false);
                }
                this.settings.getSwingCpsProperty().resetClick();
            }
            return;
        }

        final AnimationsModule animationsModule = OpalClient.getInstance().getModuleRepository().getModule(AnimationsModule.class);
        final boolean allowSwingWhenUsing = animationsModule.isEnabled() && animationsModule.isSwingWhileUsing();
        if (mc.player.isUsingItem() && !allowSwingWhenUsing) {
            return;
        }

        if (this.settings.isOverrideRaycast()) {
            if (this.settings.isTickLookahead() && (this.hitResult == null || this.hitResult.getEntity() != target.getEntity())) {
                return;
            }
            mc.crosshairTarget = target.getRotations().hitResult();
        }

        if (mc.crosshairTarget.getType() == HitResult.Type.ENTITY) {
            final VelocityModule velocityModule = OpalClient.getInstance().getModuleRepository().getModule(VelocityModule.class);
            if (this.settings.isHitSelect() && this.velocityResetAttacks <= 0 && velocityModule != null && velocityModule.isEnabled()) {
                if (velocityModule.getActiveMode() instanceof VelocityMode velocityMode) {
                    if (velocityMode.getHitSelectSkips() > 0) {
                        velocityMode.consumeHitSelectSkip();
                        return;
                    }
                    if (velocityMode.isAttacking()) {
                        return;
                    }
                }
            }

            if (this.isAttackSwingAvailable(target)) {
                final EntityHitResult hitResult = (EntityHitResult) mc.crosshairTarget;
                if (hitResult.getEntity() == target.getEntity()) {
                    final int smartWeaponSlot = getSmartWeaponSlot(target.getEntity());
                    if (smartWeaponSlot != -1) {
                        SlotHelper.setCurrentItem(smartWeaponSlot).silence(SlotHelper.Silence.NONE);
                    }

                    MouseHelper.getLeftButton().setPressed();
                    target.getKillAuraTarget().onAttack(this.attacks == 0);
                    if (this.velocityResetAttacks > 0) {
                        this.velocityResetAttacks--;
                        if (this.velocityResetAttacks == 0) {
                            this.velocityResetWindowTicks = 0;
                        }
                    }

                    if (this.settings.isGrimKeepSprint() && mc.player.isSprinting()) {
                        grimAttackKeepTicks = 2;
                        final Vec3d velocity = mc.player.getVelocity();
                        mc.player.setVelocity(velocity.x * 0.6D, velocity.y, velocity.z * 0.6D);
                        mc.player.setSprinting(true);
                    }

                    this.settings.getCpsProperty().resetClick();
                    SwingDelay.reset();
                    if (this.settings.isHeypixelBypass()) {
                        this.lastAttackTime = System.currentTimeMillis();
                    }
                    if (this.attacks > 0) {
                        this.attacks--;
                    } else {
                        this.attacks = 2;
                    }
                }
            } else {
                this.attacks = 0;
            }
        }
    }

    private boolean isAttackSwingAvailable(final CurrentTarget target) {
        final boolean smartWeaponAttack = getSmartWeaponSlot(target.getEntity()) != -1;
        if (this.settings.isAttackCooldown19() && mc.player != null && !smartWeaponAttack) {
            return mc.player.getAttackCooldownProgress(0.5F) >= 1.0F;
        }

        if (this.velocityResetAttacks > 0) {
            return true;
        }

        if (settings.isHeypixelBypass()) {
            final long time = System.currentTimeMillis();
            final double baseDelay = 1000.0 / settings.getCpsProperty().getCPS();
            final long delay = (long) (baseDelay + (Math.random() - 0.5) * baseDelay * 0.4);
            return time - lastAttackTime >= delay;
        }

        if (target.getKillAuraTarget().isAttackAvailable() || this.attacks > 0) {
            return true;
        }
        return SwingDelay.isSwingAvailable(this.settings.getCpsProperty(), false);
    }

    private int getSmartWeaponSlot(final LivingEntity target) {
        if (mc.player == null || !this.settings.isSmartWeapon() || target == null) {
            return -1;
        }

        if (!isShielding(target)) {
            return -1;
        }

        for (int i = 0; i < 9; i++) {
            final ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() instanceof AxeItem) {
                return i;
            }
        }

        return -1;
    }

    private boolean isShielding(final LivingEntity target) {
        if (!target.isUsingItem()) {
            return false;
        }

        final ItemStack activeItem = target.getActiveItem();
        return !activeItem.isEmpty() && activeItem.getItem() instanceof ShieldItem;
    }

    private int attacks;
    public long lastAttackTime;

    private int grimAttackKeepTicks;
    private int grimDamageKeepTicks;
    private int grimDamageWindowTicks;
    private int velocityResetAttacks;
    private int velocityResetWindowTicks;
    private BufferAllocator worldAllocator;

    @Subscribe
    public void onKeepSprint(final KeepSprintEvent event) {
        if (this.settings.isGrimKeepSprint() && (grimAttackKeepTicks > 0 || grimDamageKeepTicks > 0)) {
            event.setCancelled();
        }
    }

    @Subscribe
    public void onReceivePacket(final ReceivePacketEvent event) {
        if (!this.settings.isGrimKeepSprint() || mc.player == null) {
            return;
        }

        if (event.getPacket() instanceof net.minecraft.network.packet.s2c.play.EntityDamageS2CPacket damagePacket
                && damagePacket.entityId() == mc.player.getId()) {
            grimDamageWindowTicks = 4;
        }
    }

    @Subscribe
    public void onVelocityUpdate(final VelocityUpdateEvent event) {
        if (!this.settings.isGrimKeepSprint()) {
            return;
        }

        if (event.isExplosion()) {
            return;
        }

        if (grimDamageWindowTicks > 0) {
            grimDamageKeepTicks = Math.max(grimDamageKeepTicks, 6);
            grimDamageWindowTicks = 0;
        }
    }

    @Subscribe
    public void onRenderWorld(final RenderWorldEvent event) {
        if (!targeting.isTargetSelected() || targeting.getTarget() == null) {
            return;
        }

        final LivingEntity target = targeting.getTarget().getEntity();

        if (worldAllocator == null) {
            worldAllocator = new BufferAllocator(8192);
        }
        VertexConsumerProvider.Immediate vcp = VertexConsumerProvider.immediate(worldAllocator);
        WorldRenderer rc = new WorldRenderer(vcp);

        if (settings.getVisuals().getProperty("Box").getValue()) {
            final Vec3d position = MathUtility.interpolate(target, event.tickDelta()).add(mc.gameRenderer.getCamera().getPos()).subtract(0.25, 0, 0.25);
            final Vec3d dimensions = new Vec3d(target.getWidth(), target.getHeight(), target.getWidth());
            rc.drawFilledCube(
                    event.matrixStack(),
                    CustomRenderLayers.getPositionColorQuads(true),
                    position, dimensions,
                    ColorUtility.applyOpacity(ColorUtility.getClientTheme().first, 0.25F)
            );
        }

        if (settings.getVisuals().getProperty("Halo").getValue()) {
            final Vec3d pos = MathUtility.interpolate(target, event.tickDelta()).add(mc.gameRenderer.getCamera().getPos());
            final double now = System.currentTimeMillis() / 1000.0;
            final double t = now * 4.0;
            final double baseY = pos.y + target.getHeight() * 0.5;
            final double s = Math.sin(t);
            final double offset = s * 0.45;
            final double ringY = baseY + offset + 0.22;

            final com.ibm.icu.impl.Pair<Integer, Integer> theme = ColorUtility.getClientTheme();
            final int segCount = 64;
            final double radius = Math.max(0.52, target.getWidth() * 0.85);
            final float baseWidth = 3.8F;
            final int glowLayers = 5;
            for (int layer = 0; layer < glowLayers; layer++) {
                final float lw = baseWidth + layer * 2.2F;
                final float alpha = 0.85F * (float) Math.pow(0.72, layer);
                final float brighten = Math.min(0.4F, layer * 0.08F);
                for (int i = 0; i < segCount; i++) {
                    final double a1 = net.minecraft.util.math.MathHelper.TAU * ((double) i / segCount);
                    final double a2 = net.minecraft.util.math.MathHelper.TAU * ((double) (i + 1) / segCount);
                    final double x1 = pos.x + Math.sin(a1) * radius;
                    final double z1 = pos.z + Math.cos(a1) * radius;
                    final double x2 = pos.x + Math.sin(a2) * radius;
                    final double z2 = pos.z + Math.cos(a2) * radius;
                    final float raw = (float) (((now * 0.75) + ((double) i / segCount)) % 1.0);
                    final float pingpong = raw < 0.5F ? raw * 2F : (1F - (raw - 0.5F) * 2F);
                    final float eased = 0.5F - 0.5F * (float) Math.cos(pingpong * Math.PI);
                    final int grad = ColorUtility.interpolateColorsHSB(theme.first, theme.second, eased);
                    final int brighterGrad = ColorUtility.brighter(grad, brighten);
                    final int color = ColorUtility.applyOpacity(brighterGrad, alpha);
                    rc.drawLine(event.matrixStack(), CustomRenderLayers.getLines(lw, true), new Vec3d(x1, ringY, z1), new Vec3d(x2, ringY, z2), color);
                }
            }
        }

        vcp.draw();
    }

    private EntityHitResult hitResult;

    @Subscribe(priority = 2)
    public void onPreGameTick(final PreGameTickEvent event) {
        if (grimAttackKeepTicks > 0) {
            grimAttackKeepTicks--;
        }
        if (grimDamageKeepTicks > 0) {
            grimDamageKeepTicks--;
        }
        if (grimDamageWindowTicks > 0) {
            grimDamageWindowTicks--;
        }

        if (!shouldRun()) {
            this.targeting.reset();
            this.clearVelocityResetAttack();
            return;
        }

        if (this.velocityResetWindowTicks > 0 && --this.velocityResetWindowTicks == 0) {
            this.clearVelocityResetAttack();
        }

        this.targeting.update();

        final CurrentTarget target = this.targeting.getRotationTarget();
        if (target == null) {
            updateAutoblock();
            return;
        }

        RotationHelper.getHandler().rotate(
                target.getRotations().rotation(),
                settings.createRotationModel()
        );

        updateAutoblock();
    }

    @Subscribe
    public void onPreMovementPacket(final PreMovementPacketEvent event) {
        if (!this.settings.isTickLookahead() || this.targeting.getRotationTarget() == null || !shouldRun()) {
            return;
        }

        this.targeting.update();

        final CurrentTarget target = this.targeting.getRotationTarget();
        if (target == null) {
            return;
        }

        event.setYaw(mc.player.getYaw());
        event.setPitch(mc.player.getPitch());
    }

    @Subscribe
    public void onPostMovementPacket(final PostMovementPacketEvent event) {
        if (!this.settings.isTickLookahead()) {
            return;
        }
        final CurrentTarget target = this.targeting.getTarget();
        Predicate<Entity> entityPredicate = target == null ? Predicates.alwaysTrue() : e -> e == target.getEntity();
        this.hitResult = RaycastUtility.raycastEntity(mc.player.getEntityInteractionRange(), 1.0F, mc.player.getYaw(), mc.player.getPitch(), entityPredicate);
    }

    private void updateAutoblock() {
        if (mc.player == null || mc.currentScreen != null || mc.getOverlay() != null) {
            releaseAutoblock();
            return;
        }

        if (isConsumingFoodOrPotion()) {
            releaseAutoblock();
            return;
        }

        final KillAuraSettings.AutoblockMode autoblockMode = this.settings.getAutoblockMode();
        if (this.settings.isFakeAutoBlock()) {
            releaseAutoblock();
            return;
        }

        if (autoblockMode == KillAuraSettings.AutoblockMode.OFF) {
            releaseAutoblock();
            return;
        }

        if (OpalClient.getInstance().getModuleRepository().getModule(ScaffoldModule.class).isEnabled()) {
            releaseAutoblock();
            return;
        }

        final CurrentTarget target = this.targeting.getTarget();
        if (target == null) {
            releaseAutoblock();
            return;
        }

        if (!mc.player.getMainHandStack().isIn(ItemTags.SWORDS)) {
            releaseAutoblock();
            return;
        }

        final MouseButton rightButton = MouseHelper.getRightButton();
        rightButton.setPressed(true, 2);
        rightButton.setShowSwings(false);
    }

    private void releaseAutoblock() {
        final MouseButton rightButton = MouseHelper.getRightButton();
        rightButton.setPressed(false, 0);
        rightButton.setShowSwings(true);
    }

    private boolean shouldRun() {
        if (mc.player == null) {
            return false;
        }

        if (isConsumingFoodOrPotion()) {
            return false;
        }

        if (settings.isRequireAttackKey() && !mc.options.attackKey.isPressed()) {
            return false;
        }

        final ItemStack heldItem = SlotHelper.getInstance().getMainHandStack(mc.player);
        if (settings.isRequireWeapon() &&
                !(heldItem.isIn(ItemTags.SWORDS) || heldItem.isIn(ItemTags.AXES) || heldItem.isIn(ItemTags.PICKAXES))) {
            return false;
        }

        if (OpalClient.getInstance().getModuleRepository().getModule(ScaffoldModule.class).isEnabled()) {
            return false;
        }

        return true;
    }

    private boolean isConsumingFoodOrPotion() {
        if (mc.player == null || !mc.player.isUsingItem()) {
            return false;
        }

        final ItemStack stack = mc.player.getActiveItem();
        return !stack.isEmpty() && (stack.contains(DataComponentTypes.FOOD) || stack.getItem() instanceof PotionItem);
    }

    @Override
    protected void onDisable() {
        this.targeting.reset();
        this.hitResult = null;
        this.attacks = 0;
        this.grimAttackKeepTicks = 0;
        this.grimDamageKeepTicks = 0;
        this.grimDamageWindowTicks = 0;
        this.haloTrailHeights.clear();
        this.clearVelocityResetAttack();
        releaseAutoblock();
        super.onDisable();
    }

    private void clearVelocityResetAttack() {
        this.velocityResetAttacks = 0;
        this.velocityResetWindowTicks = 0;
    }

    private final java.util.ArrayDeque<Double> haloTrailHeights = new java.util.ArrayDeque<>();
}

package wtf.opal.client.feature.module.impl.world.scaffold;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.s2c.play.ItemPickupAnimationS2CPacket;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.util.shape.VoxelShape;
import wtf.opal.client.OpalClient;
import wtf.opal.duck.ClientPlayerEntityAccess;
import wtf.opal.event.EventDispatcher;
import wtf.opal.client.feature.helper.impl.LocalDataWatch;
import wtf.opal.client.feature.helper.impl.player.mouse.MouseButton;
import wtf.opal.client.feature.helper.impl.player.mouse.MouseHelper;
import wtf.opal.client.feature.helper.impl.player.rotation.RotationHelper;
import wtf.opal.client.feature.helper.impl.player.rotation.model.IRotationModel;
import wtf.opal.client.feature.helper.impl.player.rotation.model.impl.HypixelRotationModel;
import wtf.opal.client.feature.helper.impl.player.slot.SlotHelper;
import wtf.opal.client.feature.helper.impl.player.swing.SwingDelay;
import wtf.opal.client.feature.helper.impl.render.FadingBlockHelper;
import wtf.opal.client.feature.helper.impl.server.impl.HypixelServer;
import wtf.opal.client.feature.module.Module;
import wtf.opal.client.feature.module.ModuleCategory;
import wtf.opal.client.feature.module.impl.movement.flight.FlightModule;
import wtf.opal.client.feature.module.impl.movement.longjump.LongJumpModule;
import wtf.opal.client.feature.module.impl.visual.overlay.impl.dynamicisland.IslandTrigger;
import wtf.opal.client.feature.module.repository.ModuleRepository;
import wtf.opal.client.feature.simulation.PlayerSimulation;
import wtf.opal.client.renderer.world.WorldRenderer;
import wtf.opal.event.impl.game.PreGameTickEvent;
import wtf.opal.event.impl.game.input.MouseHandleInputEvent;
import wtf.opal.event.impl.game.input.MoveInputEvent;
import wtf.opal.event.impl.game.packet.ReceivePacketEvent;
import wtf.opal.event.impl.game.player.interaction.block.BlockPlacedEvent;
import wtf.opal.event.impl.render.RenderWorldEvent;
import wtf.opal.event.subscriber.Subscribe;
import wtf.opal.mixin.ClientPlayerInteractionManagerAccessor;
import wtf.opal.mixin.LivingEntityAccessor;
import wtf.opal.utility.misc.chat.ChatUtility;
import wtf.opal.utility.player.*;
import wtf.opal.utility.render.ColorUtility;
import wtf.opal.utility.render.CustomRenderLayers;

import java.awt.*;
import java.util.*;
import java.util.List;

import static wtf.opal.client.Constants.mc;

public final class ScaffoldModule extends Module implements IslandTrigger {
    private static final double MAX_PLACEMENT_DISTANCE_SQUARED = 20.25D;
    private static final int NORMAL_SEARCH_DEPTH = 4;
    private static final int TELLY_SEARCH_DEPTH = 3;
    private static final int NORMAL_VISIT_BUDGET = 160;
    private static final int TELLY_VISIT_BUDGET = 96;
    private static final int NORMAL_RAYTRACE_BUDGET = 48;
    private static final int TELLY_RAYTRACE_BUDGET = 24;
    private static final int TELLY_TARGET_CANDIDATE_BUDGET = 48;
    private static final int RECENT_SUPPORT_QUARANTINE_TICKS = 1;
    private static final int RECENT_PLACED_BLOCK_TTL = 6;
    private static final int MAX_LAST_PLACED_BLOCKS = 4;
    private static final double[] FOOT_OFFSETS = {0.0D, 0.301D, -0.301D};
    private static final double[] LOOKAHEAD_STEPS = {0.0D, 0.35D, 0.7D, 1.05D, 1.4D};
    private static final double[] HIT_OFFSETS = {0.0D, 0.24D, -0.24D};

    private final ScaffoldIsland dynamicIsland = new ScaffoldIsland(this);
    private final ScaffoldSettings settings = new ScaffoldSettings(this);

    public BlockData blockCache;
    private int sameYPos;

    private Vec3d preExpandPos;
    private RaytracedRotation rotation;

    private Map<Integer, Integer> realStackSizeMap;
    private int autoJumpTicks;
    private int autoJumpGroundTicks;
    private int autoJumpAirTicks;
    private int lastAutoJumpAge;
    private int lastAutoJumpStateAge;
    private final Map<BlockPos, Integer> recentPlacedBlocks = new HashMap<>();
    private final ArrayDeque<BlockPos> lastPlacedBlocks = new ArrayDeque<>();
    private BlockPos lastSearchOrigin;
    private BlockData lastSearchData;
    private int lastSearchAge = -1;

    public ScaffoldModule() {
        super("Scaffold", "Automatically places blocks under you.", ModuleCategory.WORLD);
    }

    @Override
    protected void onDisable() {
        this.dynamicIsland.onDisable();
        this.realStackSizeMap = null;
        this.intelligentRotation = null;
        this.placeTick = 0;
        this.recentPlacedBlocks.clear();
        this.lastPlacedBlocks.clear();
        this.lastSearchOrigin = null;
        this.lastSearchData = null;
        this.lastSearchAge = -1;
        this.resetAutoJumpState();

        super.onDisable();
    }

    @Override
    protected void onEnable() {
        super.onEnable();

        blockCache = null;
        rotation = null;
        this.lastPlacedBlocks.clear();
        this.lastSearchOrigin = null;
        this.lastSearchData = null;
        this.lastSearchAge = -1;
        this.resetAutoJumpState();

        this.realStackSizeMap = new HashMap<>();

        if (mc.player == null) return;
        sameYPos = MathHelper.floor(mc.player.getY());
    }

    @Subscribe
    public void onBlockPlaced(BlockPlacedEvent event) {
        if (!mc.interactionManager.getCurrentGameMode().isCreative()) {
            int selectedSlot = mc.player.getInventory().getSelectedSlot();
            this.realStackSizeMap.put(selectedSlot, this.realStackSizeMap.getOrDefault(selectedSlot, mc.player.getMainHandStack().getCount() + 1) - 1);
        }

        if (this.isTellyMode()) {
            final BlockHitResult hitResult = event.getBlockHitResult();
            final BlockPos placed = hitResult.getBlockPos().offset(hitResult.getSide()).toImmutable();
            this.recentPlacedBlocks.put(placed, mc.player.age);
            this.trackPlacedBlock(placed);
        } else {
            this.trackPlacedBlock(event.getBlockHitResult().getBlockPos().offset(event.getBlockHitResult().getSide()).toImmutable());
        }
    }

    @Subscribe
    public void onRenderWorld(final RenderWorldEvent event) {
        if (mc.crosshairTarget instanceof BlockHitResult blockHitResult
                && blockHitResult.getType() == HitResult.Type.BLOCK
                && rotation != null
                && settings.isBlockOverlayEnabled()
                && !mc.world.getBlockState(blockHitResult.getBlockPos()).isAir()) {
            final Vec3d startVec = new Vec3d(blockHitResult.getBlockPos().getX(), blockHitResult.getBlockPos().getY(), blockHitResult.getBlockPos().getZ());
            final Vec3d dimensions = new Vec3d(1, 1, 1);

            VertexConsumerProvider.Immediate vcp = VertexConsumerProvider.immediate(new BufferAllocator(1024));
            WorldRenderer rc = new WorldRenderer(vcp);

            rc.drawFilledCube(event.matrixStack(), CustomRenderLayers.getPositionColorQuads(true), startVec, dimensions, ColorUtility.applyOpacity(ColorUtility.getClientTheme().first, 0.25F));

            vcp.draw();
        }
    }

    @Subscribe(priority = 1)
    public void onMoveInput(final MoveInputEvent event) {
        if (this.settings.getMode().is(ScaffoldSettings.Mode.TELLY)) {
            return;
        }
        this.updateAutoJumpTicks();
        if (this.shouldAutoJump(event)) {
            ((LivingEntityAccessor) mc.player).setJumpingCooldown(0);
            event.setJump(true);
            this.lastAutoJumpAge = mc.player.age;
        }
    }

    @Subscribe(priority = 1)
    public void onHandleInput(final MouseHandleInputEvent event) {
        final MouseButton rightButton = MouseHelper.getRightButton();
        final boolean isBlock = this.hasHeldPlaceableBlock();
        if (this.blockCache != null) {
            final BlockHitResult tellyHitResult = this.isTellyMode() ? this.getCurrentTellyHitResult() : null;
            if (rotation != null && settings.isOverrideRaycast()) {
                if (this.isTellyMode()) {
                    if (tellyHitResult != null) {
                        mc.crosshairTarget = tellyHitResult;
                    }
                } else {
                    mc.crosshairTarget = this.rotation.hitResult();
                }
            }

            final Block blockOver = PlayerUtility.getBlockOver();
            final BlockHitResult hitResult = this.isTellyMode()
                    ? tellyHitResult
                    : mc.crosshairTarget instanceof BlockHitResult currentHit ? currentHit : null;
            final boolean canPlaceCache = this.isTellyMode()
                    ? hitResult != null && this.isValidPlacementHit(hitResult, true)
                    : hitResult != null && this.isLookingAtBlockCache() && this.canPlaceCurrentCache();

            if (this.shouldDelayAutoJumpPlacement()) {
                rightButton.setDisabled();
            } else if (!InventoryUtility.isBlockInteractable(blockOver) && isBlock && canPlaceCache) {
                if (this.placeBlock(hitResult)) {
                    this.placeTick = mc.player.age;

                    if (settings.isBlockOverlayEnabled()) {
                        FadingBlockHelper.getInstance().addFadingBlock(
                                new FadingBlockHelper.FadingBlock(
                                        blockCache.blockWithDirection.blockPos,
                                        Color.BITMASK,
                                        ColorUtility.applyOpacity(ColorUtility.getClientTheme().first, 0.25F),
                                        300
                                )
                        );
                    }
                }
                rightButton.setDisabled();
            } else {
                rightButton.setDisabled();
            }
        } else {
            if (!isBlock || !this.simulateClick()) {
                rightButton.setDisabled();
            } else {
                rightButton.setDisabled();
            }
        }

        if (preExpandPos != null) {
            mc.player.setPos(preExpandPos.x, preExpandPos.y, preExpandPos.z);
            preExpandPos = null;
        }
    }

    private boolean simulateClick() {
        if (!SwingDelay.isSwingAvailable(this.settings.getSimulationCps(), false)) {
            return false;
        }
        if (mc.crosshairTarget != null) {
            if (mc.crosshairTarget instanceof BlockHitResult blockHitResult) {
//                if(!this.settings.isOverrideRaycast() && (this.blockCache.blockWithDirection.blockPos() != blockHitResult.getBlockPos() || this.blockCache.blockWithDirection.direction() != blockHitResult.getSide())) {
//                    return false;
//                }
                if (!this.isValidPlacementHit(blockHitResult, false)) {
                    return false;
                }

                if (!this.placeBlock(blockHitResult)) {
                    return false;
                }
                this.settings.getSimulationCps().resetClick();
                SwingDelay.reset();
                return true;
            }
        }
        return false;
    }

    private Vec2f intelligentRotation;

    @Subscribe(priority = 1)
    public void onPreGameTick(final PreGameTickEvent event) {
        if (mc.player == null) {
            blockCache = null;
            return;
        }
        this.pruneRecentPlacedBlocks();
        final ScaffoldSettings.Mode effectiveMode = this.settings.getMode().getValue();
        if (this.isTellyMode()) {
            this.updateHeldBlockSlot();
            return;
        }
        this.updateAutoJumpTicks();
            // Expand
        Vec3d expandOffset = null;
//        if (LocalDataWatch.get().getKnownServerManager().getCurrentServer() instanceof HypixelServer) {
//            expandOffset = mc.player.getVelocity().withAxis(Direction.Axis.Y, 0.0D);
//        }

        if (expandOffset != null) {
            preExpandPos = mc.player.getEntityPos();

            mc.player.setPos(mc.player.getX() + expandOffset.getX(), mc.player.getY() + expandOffset.getY(), mc.player.getZ() + expandOffset.getZ());
        }

        if (!this.updateHeldBlockSlot()) {
            return;
        }

        final ModuleRepository moduleRepository = OpalClient.getInstance().getModuleRepository();
        final boolean autoJumpActive = this.isSameYAutoJumpActive();
        final boolean manualJump = PlayerUtility.isKeyPressed(mc.options.jumpKey);
        final boolean updateY = !settings.isSameYEnabled()
              //  || mc.options.useKey.isPressed()
                || (this.settings.isAutoJump() && manualJump && !this.isRecentAutoJump())
                || (mc.player.isOnGround() && !autoJumpActive)
                || Math.abs(Math.floor(mc.player.getY() - sameYPos)) > 3
                || moduleRepository.getModule(LongJumpModule.class).isEnabled()
                || moduleRepository.getModule(FlightModule.class).isEnabled();

        if (updateY) {
            sameYPos = MathHelper.floor(mc.player.getY());
        }

        this.intelligentRotation = null;

        final boolean watchdog = effectiveMode == ScaffoldSettings.Mode.WATCHDOG;
        if (autoJumpActive || !watchdog || !mc.player.input.playerInput.jump() ||
                !mc.player.isOnGround() && (mc.player.getVelocity().getY() >= 0.0D || PlayerUtility.isBoxEmpty(mc.player.getBoundingBox().offset(0.0D, mc.player.getVelocity().getY(), 0.0D)))) {
            this.updateMovementIntelligence();
            updateData();
            this.updateMovementIntelligence();
            // TODO: when for sneak
//            if ((mc.player.input.playerInput.sneak()) &&
//                    (int) (mc.player.getY() + mc.player.getVelocity().getY()) == (int) mc.player.getY() && (mc.player.getVelocity().getY() >= 0.2D || !updateY)) { // telly check bypass, doesn't run jumping else you fall off lol
//                this.blockCache = null;
//            }
        } else {
            this.blockCache = null;
            this.rotation = null;
        }

        if (rotation != null) {
            if (!settings.isSnapRotationsEnabled() || blockCache != null) {
                final IRotationModel model = (LocalDataWatch.get().getKnownServerManager().getCurrentServer() instanceof HypixelServer && effectiveMode == ScaffoldSettings.Mode.WATCHDOG) ? new HypixelRotationModel() : this.settings.createRotationModel();
                final Vec2f targetRotation = autoJumpActive ? this.getAutoJumpRotation(rotation.rotation()) : rotation.rotation();
                RotationHelper.getHandler().rotate(
                        targetRotation,
                        model
                );
            }
        }
    }

    private boolean isYawDiagonal() {
        final float direction = Math.abs(MoveUtility.getDirectionDegrees() % 90);
        final int range = 30;
        return direction > 45 - range && direction < 45 + range;
    }

    private int placeTick;

    private void resetAutoJumpState() {
        this.autoJumpTicks = 0;
        this.autoJumpGroundTicks = 0;
        this.autoJumpAirTicks = 0;
        this.lastAutoJumpAge = -20;
        this.lastAutoJumpStateAge = -1;
    }

    private void updateAutoJumpTicks() {
        if (mc.player == null || this.lastAutoJumpStateAge == mc.player.age) {
            return;
        }
        this.lastAutoJumpStateAge = mc.player.age;
        if (!this.isSameYAutoJumpActive()) {
            this.autoJumpTicks = 0;
            this.autoJumpGroundTicks = 0;
            this.autoJumpAirTicks = 0;
            return;
        }
        this.autoJumpTicks++;
        if (mc.player.isOnGround()) {
            this.autoJumpGroundTicks++;
            this.autoJumpAirTicks = 0;
        } else {
            this.autoJumpAirTicks++;
            this.autoJumpGroundTicks = 0;
        }
    }

    private boolean shouldAutoJump(final MoveInputEvent event) {
        if (!this.settings.isSameYEnabled() || !this.settings.isAutoJump() || mc.player == null || mc.world == null) {
            return false;
        }
        if (!mc.player.isOnGround() || mc.player.isSneaking() || event.isSneak() || mc.player.hasStatusEffect(StatusEffects.JUMP_BOOST)) {
            return false;
        }
        if (mc.options.useKey.isPressed()) {
            return false;
        }
        if (this.getPlaceableBlock() == -1 && !(mc.player.getOffHandStack().getItem() instanceof BlockItem blockItem && InventoryUtility.isGoodBlock(blockItem.getBlock()))) {
            return false;
        }
        return Math.abs(event.getForward()) > 1.0E-4F || Math.abs(event.getSideways()) > 1.0E-4F || MoveUtility.isMoving();
    }

    private boolean hasHeldPlaceableBlock() {
        if (mc.player == null) {
            return false;
        }
        if (this.isTellyMode()) {
            return this.hasMainHandPlaceableBlock();
        }
        return this.hasMainHandPlaceableBlock() || this.isPlaceableBlockItem(mc.player.getOffHandStack());
    }

    public boolean hasMainHandPlaceableBlock() {
        return mc.player != null && this.isPlaceableBlockItem(mc.player.getMainHandStack());
    }

    public boolean hasHotbarPlaceableBlock() {
        return mc.player != null && this.getPlaceableBlock() != -1;
    }

    private boolean isPlaceableBlockItem(final ItemStack stack) {
        return stack.getItem() instanceof BlockItem blockItem && InventoryUtility.isGoodBlock(blockItem.getBlock());
    }

    private boolean isSameYAutoJumpActive() {
        return this.settings.isSameYEnabled()
                && this.settings.isAutoJump()
                && (MoveUtility.isMoving() || this.isRecentAutoJump());
    }

    private boolean isRecentAutoJump() {
        return mc.player != null && mc.player.age - this.lastAutoJumpAge <= 4;
    }

    private boolean shouldDelayAutoJumpPlacement() {
        return this.isSameYAutoJumpActive()
                && mc.player != null
                && mc.player.isOnGround()
                && this.autoJumpGroundTicks <= 2
                && MoveUtility.isMoving();
    }

    private boolean isLookingAtBlockCache() {
        if (this.blockCache == null || !(mc.crosshairTarget instanceof BlockHitResult hitResult)) {
            return false;
        }
        final BlockWithDirection block = this.blockCache.blockWithDirection;
        return hitResult.getType() == HitResult.Type.BLOCK
                && hitResult.getBlockPos().equals(block.blockPos)
                && hitResult.getSide() == block.direction;
    }

    private boolean canPlaceCurrentCache() {
        if (!this.isTellyMode()) {
            return mc.crosshairTarget instanceof BlockHitResult hitResult && this.isValidPlacementHit(hitResult, false);
        }
        if (this.rotation == null || this.blockCache == null || mc.player == null || mc.player.isOnGround()) {
            return false;
        }
        final BlockHitResult hitResult = this.getCurrentTellyHitResult();
        return hitResult != null && this.isValidPlacementHit(hitResult, true);
    }

    private BlockHitResult getCurrentTellyHitResult() {
        if (!this.isTellyMode() || this.rotation == null || this.blockCache == null || mc.player == null) {
            return null;
        }
        final Vec2f currentRotation = RotationUtility.getRotation();
        final HitResult result = RaycastUtility.raycastBlock(mc.player.getBlockInteractionRange(), 1.0F, false, currentRotation.x, currentRotation.y);
        if (!(result instanceof BlockHitResult hitResult)) {
            return null;
        }
        final BlockWithDirection block = this.blockCache.blockWithDirection;
        return hitResult.getType() == HitResult.Type.BLOCK
                && hitResult.getBlockPos().equals(block.blockPos)
                && hitResult.getSide() == block.direction
                && this.isValidPlacementSupport(hitResult) ? hitResult : null;
    }

    private boolean isValidPlacementHit(final BlockHitResult blockHitResult, final boolean checkPredictedCollision) {
        final PlacementHand placementHand = this.getPlacementHand(!this.isTellyMode());
        return placementHand != null && this.isValidPlacementHit(blockHitResult, checkPredictedCollision, placementHand);
    }

    private boolean isValidPlacementHit(final BlockHitResult blockHitResult, final boolean checkPredictedCollision, final PlacementHand placementHand) {
        if (mc.player == null || mc.world == null) {
            return false;
        }
        if (!this.isValidPlacementSupport(blockHitResult)) {
            return false;
        }

        final Block block = mc.world.getBlockState(blockHitResult.getBlockPos()).getBlock();
        if (InventoryUtility.isBlockInteractable(block)) {
            return false;
        }

        final ItemUsageContext itemUsageContext = new ItemUsageContext(mc.player, placementHand.hand, blockHitResult);
        final ItemPlacementContext placementContext = placementHand.blockItem.getPlacementContext(new ItemPlacementContext(itemUsageContext));
        if (placementContext == null) {
            return false;
        }

        final BlockPos offsetPos = blockHitResult.getBlockPos().offset(blockHitResult.getSide());
        if (mc.world.isOutOfHeightLimit(offsetPos.getY())) {
            return false;
        }
        final BlockState placementState = placementHand.blockItem.getBlock().getPlacementState(placementContext);
        if (placementState == null || !mc.world.getBlockState(offsetPos).isReplaceable() || !placementState.canPlaceAt(mc.world, offsetPos)) {
            return false;
        }

        final VoxelShape collisionShape = placementState.getCollisionShape(mc.world, offsetPos);
        if (!collisionShape.isEmpty()) {
            final Box blockBox = collisionShape.getBoundingBox().offset(offsetPos);
            if (mc.player.getBoundingBox().intersects(blockBox)) {
                return false;
            }
            if (checkPredictedCollision
                    && mc.player.getBoundingBox().offset(mc.player.getVelocity()).intersects(blockBox)
                    && !this.isLandingPlacementCollisionAllowed(blockBox)) {
                return false;
            }
        }

        return true;
    }

    private boolean isLandingPlacementCollisionAllowed(final Box blockBox) {
        if (mc.player == null) {
            return false;
        }
        final Box currentBox = mc.player.getBoundingBox();
        return mc.player.getVelocity().y <= 0.0D
                && currentBox.minY >= blockBox.maxY - 1.0E-3D;
    }

    public boolean placeBlock(final BlockHitResult hitResult) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null || hitResult == null) {
            return false;
        }
        if (this.placeTick == mc.player.age) {
            return false;
        }

        final PlacementHand placementHand = this.getPlacementHand(!this.isTellyMode());
        if (placementHand == null || !this.isValidPlacementHit(hitResult, this.isTellyMode(), placementHand)) {
            return false;
        }

        if (this.settings.isInteractBeforePlace()) {
            this.sendInteractBlockBeforePlace(placementHand.hand, hitResult);
        }

        final ActionResult result = mc.interactionManager.interactBlock(mc.player, placementHand.hand, hitResult);
        if (!result.isAccepted()) {
            return false;
        }

        if (this.settings.getSwingMode().getValue() == ScaffoldSettings.SwingMode.SERVER) {
            ((ClientPlayerEntityAccess) mc.player).opal$swingHandServerside(placementHand.hand);
        } else {
            mc.player.swingHand(placementHand.hand);
        }
        this.placeTick = mc.player.age;
        EventDispatcher.dispatch(new BlockPlacedEvent(hitResult));
        return true;
    }

    public boolean canPlaceAtHit(final BlockHitResult hitResult, final boolean checkPredictedCollision) {
        return this.isValidPlacementHit(hitResult, checkPredictedCollision);
    }

    private void sendInteractBlockBeforePlace(final Hand hand, final BlockHitResult hitResult) {
        if (mc.interactionManager == null || mc.world == null || hand == null || !this.isValidPlacementSupport(hitResult)) {
            return;
        }

        final ClientPlayerInteractionManagerAccessor accessor = (ClientPlayerInteractionManagerAccessor) mc.interactionManager;
        accessor.callSyncSelectedSlot();
        accessor.callSendSequencedPacket(mc.world, sequence -> new PlayerInteractBlockC2SPacket(hand, hitResult, sequence));
    }

    private PlacementHand getPlacementHand(final boolean allowOffhand) {
        if (mc.player == null) {
            return null;
        }
        if (this.isPlaceableBlockItem(mc.player.getMainHandStack())) {
            return new PlacementHand(Hand.MAIN_HAND, (BlockItem) mc.player.getMainHandStack().getItem());
        }
        if (allowOffhand && this.isPlaceableBlockItem(mc.player.getOffHandStack())) {
            return new PlacementHand(Hand.OFF_HAND, (BlockItem) mc.player.getOffHandStack().getItem());
        }
        return null;
    }

    private Vec2f getAutoJumpRotation(final Vec2f blockRotation) {
        if (mc.player == null) {
            return blockRotation;
        }
        final int groundTicks = Math.max(this.autoJumpGroundTicks, LocalDataWatch.get().groundTicks);
        final int airTicks = Math.max(this.autoJumpAirTicks, LocalDataWatch.get().airTicks);
        if (mc.player.isOnGround() && groundTicks <= 2) {
            return new Vec2f(MoveUtility.getDirectionDegrees(mc.player.getYaw()), 75.5F);
        }
        if (!mc.player.isOnGround() && airTicks == 1) {
            final float yawDelta = MathHelper.wrapDegrees(blockRotation.x - mc.player.getYaw());
            return new Vec2f(mc.player.getYaw() + MathHelper.clamp(yawDelta, -90.0F, 90.0F), blockRotation.y);
        }
        return blockRotation;
    }

    private void updateMovementIntelligence() {
        if (this.settings.isMovementIntelligence()) {
            if (!this.settings.getMode().is(ScaffoldSettings.Mode.WATCHDOG) || mc.player.isOnGround() ||
                    !PlayerUtility.isBoxEmpty(mc.player.getBoundingBox().offset(0.0D, mc.player.getVelocity().getY(), 0.0D))) {
                final Vec2f currentRotation = rotation != null ? rotation.rotation() : RotationUtility.getRotation();
                this.intelligentRotation = RotationUtility.getPriorityAngle(currentRotation, this.settings.getMovementIntelligenceSteps(), this.settings.isMovementSnapping(), this.settings.isDiagonalMovement());
            }
        }
    }

    @Subscribe
    public void onReceivePacket(final ReceivePacketEvent event) {
        if (event.getPacket() instanceof ItemPickupAnimationS2CPacket pickup
                && mc.player != null
                && pickup.getCollectorEntityId() == mc.player.getId()) {
            int selectedSlot = mc.player.getInventory().getSelectedSlot();
            this.realStackSizeMap.put(
                    selectedSlot,
                    this.realStackSizeMap.getOrDefault(selectedSlot, mc.player.getMainHandStack().getCount() - pickup.getStackAmount()) + pickup.getStackAmount()
            );
        }
    }

    public int getPlaceableBlock() {
        for (int i = 0; i < 9; i++) {
            final ItemStack itemStack = mc.player.getInventory().getMainStacks().get(i);
            final int stackCount = this.realStackSizeMap == null ? itemStack.getCount() : this.realStackSizeMap.getOrDefault(i, itemStack.getCount());
            if (itemStack.getItem() instanceof BlockItem blockItem
                    && stackCount > 0 &&
                    InventoryUtility.isGoodBlock(blockItem.getBlock())) {
                return i;
            }
        }
        return -1;
    }

    public boolean updateHeldBlockSlot() {
        final int slot = getPlaceableBlock();
        if (slot == -1) {
            return !this.isTellyMode() && mc.player.getOffHandStack().getItem() instanceof BlockItem blockItem && InventoryUtility.isGoodBlock(blockItem.getBlock());
        }
        final SlotHelper.Silence silence;
        if (this.isTellyMode()) {
            silence = SlotHelper.Silence.NONE;
        } else {
            switch (settings.getSwitchMode().getValue()) {
                case NORMAL -> silence = SlotHelper.Silence.NONE;
                case FULL -> silence = SlotHelper.Silence.FULL;
                default -> silence = SlotHelper.Silence.DEFAULT;
            }
        }
        SlotHelper.setCurrentItem(slot).silence(silence);
        return true;
    }

    private boolean updateData() {
        if (this.blockCache != null && this.canReuseCachedBlockData(this.blockCache)) {
            this.rotation = this.blockCache.rotation();
            return true;
        }

        blockCache = getBlockData();

        if (blockCache != null) {
            this.rotation = blockCache.rotation;
            return true;
        }

        final PlayerSimulation simulation = new PlayerSimulation(mc.player);
        final OtherClientPlayerEntity entity = simulation.getSimulatedEntity();
        for (int i = 0; i < 10; i++) {
            simulation.simulateTick();
            final BlockData simulatedData = getBlockData(entity.getBlockPos().down(), entity);
            if (simulatedData != null) {
                blockCache = simulatedData;
                rotation = simulatedData.rotation;
                break;
            }
        }

        return blockCache != null;
    }

    private boolean canReuseCachedBlockData(final BlockData cachedData) {
        if (cachedData == null || cachedData.rotation() == null || cachedData.blockWithDirection() == null || mc.player == null) {
            return false;
        }

        return this.isCachedBlockDataReachable(
                cachedData.rotation().rotation(),
                cachedData.blockWithDirection().blockPos(),
                cachedData.blockWithDirection().direction()
        );
    }

    private boolean isCachedBlockDataReachable(final Vec2f rotation, final BlockPos pos, final Direction face) {
        final HitResult hitResult = RaycastUtility.raycastBlock(mc.player.getBlockInteractionRange(), 1.0F, false, rotation.x, rotation.y);
        if (hitResult instanceof BlockHitResult blockHitResult) {
            return blockHitResult.getBlockPos().equals(pos)
                    && blockHitResult.getSide() == face
                    && this.isValidPlacementSupport(blockHitResult);
        }
        return false;
    }

    private RaytracedRotation getRotation(BlockWithDirection data, Vec3d start) {
        final Vec2f sortingAngle = this.getSortingAngle();
        final RaytracedRotation fastRotation = this.getFastRotation(data, sortingAngle, start);
        return fastRotation != null ? fastRotation : RotationUtility.getRotationFromRaycastedBlock(data.blockPos, data.direction, sortingAngle, start);
    }

    private Vec2f getSortingAngle() {
        final Vec2f sortingAngle;
        if (this.intelligentRotation != null) {
            sortingAngle = this.intelligentRotation;
        } else {
            sortingAngle = rotation != null ? rotation.rotation() : RotationUtility.getRotation();
        }
        return sortingAngle;
    }

    private RaytracedRotation getFastRotation(final BlockWithDirection data, final Vec2f sortingAngle, final Vec3d start) {
        RaytracedRotation best = null;
        double bestDifference = Double.MAX_VALUE;

        for (final double firstOffset : HIT_OFFSETS) {
            for (final double secondOffset : HIT_OFFSETS) {
                final Vec3d hitVec = this.getFaceHitVec(data, firstOffset, secondOffset);
                final Vec2f rotation = RotationUtility.getVanillaRotation(RotationUtility.getRotationFromPosition(start, hitVec));
                final HitResult result = RaycastUtility.raycastBlock(mc.player.getBlockInteractionRange(), false, rotation.x, rotation.y, start);
                if (!(result instanceof BlockHitResult blockHitResult)
                        || blockHitResult.getType() != HitResult.Type.BLOCK
                        || !blockHitResult.getBlockPos().equals(data.blockPos)
                        || blockHitResult.getSide() != data.direction
                        || !this.isValidPlacementSupport(blockHitResult)) {
                    continue;
                }

                final double difference = RotationUtility.getRotationDifference(rotation, sortingAngle);
                if (difference < bestDifference) {
                    bestDifference = difference;
                    best = new RaytracedRotation(rotation, blockHitResult);
                }
            }
        }

        return best;
    }

    private Vec3d getFaceHitVec(final BlockWithDirection data, final double firstOffset, final double secondOffset) {
        final Direction direction = data.direction;
        double x = data.blockPos.getX() + 0.5D + direction.getOffsetX() * 0.5D;
        double y = data.blockPos.getY() + 0.5D + direction.getOffsetY() * 0.5D;
        double z = data.blockPos.getZ() + 0.5D + direction.getOffsetZ() * 0.5D;

        switch (direction.getAxis()) {
            case X -> {
                y += firstOffset;
                z += secondOffset;
            }
            case Y -> {
                x += firstOffset;
                z += secondOffset;
            }
            case Z -> {
                x += firstOffset;
                y += secondOffset;
            }
        }

        return new Vec3d(x, y, z);
    }

    private BlockData getBlockData() {
        final BlockPos base = mc.player.getBlockPos().withY(sameYPos).down();
        for (final BlockPos target : this.getTargetBlockCandidates(base, mc.player)) {
            final BlockData data = this.getBlockData(target, mc.player);
            if (data != null) {
                return data;
            }
        }
        return null;
    }

    private List<BlockPos> getTargetBlockCandidates(final BlockPos base, final PlayerEntity entity) {
        final LinkedHashSet<BlockPos> candidates = new LinkedHashSet<>();
        candidates.add(base.toImmutable());
        this.addLastPlacedCandidate(candidates, base);

        final Vec3d velocity = entity.getVelocity();
        for (final double step : LOOKAHEAD_STEPS) {
            this.addFootCandidates(candidates, entity.getEntityPos().add(velocity.x * step, 0.0D, velocity.z * step), base.getY());
        }

        if (MoveUtility.isMoving()) {
            final float direction = MoveUtility.getDirectionDegrees();
            final double radians = Math.toRadians(direction);
            final double dirX = -MathHelper.sin((float) radians);
            final double dirZ = MathHelper.cos((float) radians);
            this.addFootCandidates(candidates, entity.getEntityPos().add(dirX * 0.35D, 0.0D, dirZ * 0.35D), base.getY());
            this.addFootCandidates(candidates, entity.getEntityPos().add(dirX * 0.65D, 0.0D, dirZ * 0.65D), base.getY());
            this.addFootCandidates(candidates, entity.getEntityPos().add(dirX * 1.05D, 0.0D, dirZ * 1.05D), base.getY());
            this.addFootCandidates(candidates, entity.getEntityPos().add(dirX * 1.45D, 0.0D, dirZ * 1.45D), base.getY());

            final double sideX = MathHelper.cos((float) radians);
            final double sideZ = MathHelper.sin((float) radians);
            this.addFootCandidates(candidates, entity.getEntityPos().add(dirX * 0.85D + sideX * 0.32D, 0.0D, dirZ * 0.85D + sideZ * 0.32D), base.getY());
            this.addFootCandidates(candidates, entity.getEntityPos().add(dirX * 0.85D - sideX * 0.32D, 0.0D, dirZ * 0.85D - sideZ * 0.32D), base.getY());
        }

        return new ArrayList<>(candidates);
    }

    private void addLastPlacedCandidate(final Set<BlockPos> candidates, final BlockPos base) {
        if (this.lastPlacedBlocks.size() < 2) {
            return;
        }

        final Iterator<BlockPos> iterator = this.lastPlacedBlocks.descendingIterator();
        final BlockPos last = iterator.next();
        final BlockPos previous = iterator.next();
        final int dx = Integer.compare(last.getX() - previous.getX(), 0);
        final int dz = Integer.compare(last.getZ() - previous.getZ(), 0);
        if (dx != 0 || dz != 0) {
            candidates.add(new BlockPos(last.getX() + dx, base.getY(), last.getZ() + dz));
        }
    }

    private void addFootCandidates(final Set<BlockPos> candidates, final Vec3d pos, final int y) {
        for (final double xOffset : FOOT_OFFSETS) {
            for (final double zOffset : FOOT_OFFSETS) {
                candidates.add(BlockPos.ofFloored(pos.x + xOffset, y, pos.z + zOffset));
            }
        }
    }

    public BlockData findBlockDataFor(final int x, final int y, final int z) {
        return this.getBlockData(new BlockPos(x, y, z), mc.player);
    }

    public BlockData findBestTellyBlockData(final int y) {
        if (mc.player == null || mc.world == null) {
            return null;
        }

        final BlockPos base = mc.player.getBlockPos().withY(y);
        final Vec3d eyePos = mc.player.getEntityPos().add(0.0D, mc.player.getStandingEyeHeight(), 0.0D);
        int checked = 0;
        for (final BlockPos target : this.getTargetBlockCandidates(base, mc.player)) {
            if (checked++ >= TELLY_TARGET_CANDIDATE_BUDGET) {
                break;
            }

            final BlockData data = this.getDirectBlockData(target, eyePos);
            if (data != null) {
                return data;
            }
        }
        return this.getBlockData(base, mc.player);
    }

    private BlockData getDirectBlockData(final BlockPos targetBlockPos, final Vec3d eyePos) {
        if (!this.isReplaceable(targetBlockPos)) {
            return null;
        }

        int raytraceCount = 0;
        final Vec2f sortingAngle = this.getSortingAngle();
        for (final BlockWithDirection block : this.getSortedSupports(targetBlockPos, targetBlockPos)) {
            if (raytraceCount++ >= TELLY_RAYTRACE_BUDGET) {
                break;
            }

            final RaytracedRotation rotation = this.getFastRotation(block, sortingAngle, eyePos);
            if (rotation != null) {
                return new BlockData(block, rotation);
            }
        }
        return null;
    }

    public void setRotation(final RaytracedRotation rotation) {
        this.rotation = rotation;
    }

    public boolean isTellyMode() {
        return this.settings.getMode().is(ScaffoldSettings.Mode.TELLY);
    }

    private boolean isReplaceable(final BlockPos pos) {
        return !mc.world.isOutOfHeightLimit(pos.getY()) && mc.world.getBlockState(pos).isReplaceable();
    }

    private boolean isValidSupportBlock(final BlockPos pos) {
        if (mc.world == null || mc.world.isOutOfHeightLimit(pos.getY())) {
            return false;
        }
        final BlockState state = mc.world.getBlockState(pos);
        return !state.isAir()
                && !state.isReplaceable()
                && state.getFluidState().isEmpty()
                && !InventoryUtility.isBlockInteractable(state.getBlock())
                && !state.getCollisionShape(mc.world, pos).isEmpty()
                && !this.isRecentlyPlacedTellySupport(pos);
    }

    public boolean isSolidAndNonInteractive(final BlockState state, final BlockPos pos) {
        return mc.world != null
                && !state.getCollisionShape(mc.world, pos).isEmpty()
                && state.createScreenHandlerFactory(mc.world, pos) == null;
    }

    private boolean isValidPlacementSupport(final BlockHitResult hitResult) {
        return hitResult != null
                && hitResult.getType() == HitResult.Type.BLOCK
                && hitResult.getSide() != null
                && this.isValidSupportBlock(hitResult.getBlockPos());
    }

    private Direction[] getPlacementDirections() {
        return new Direction[]{
                Direction.DOWN,
                Direction.EAST,
                Direction.WEST,
                Direction.NORTH,
                Direction.SOUTH,
                Direction.UP
        };
    }

    private int getDirectionPriority(final Direction direction) {
        return switch (direction) {
            case UP -> 0;
            case WEST, EAST, NORTH, SOUTH -> 1;
            case DOWN -> 2;
        };
    }

    private BlockData getBlockData(final BlockPos targetBlockPos, final PlayerEntity entity) {
        if (!mc.world.getBlockState(targetBlockPos).isReplaceable()) {
            return null;
        }

        final boolean canUseCache = entity == mc.player;
        if (canUseCache
                && this.lastSearchAge == entity.age
                && targetBlockPos.equals(this.lastSearchOrigin)
                && this.isCachedBlockDataUsable(this.lastSearchData, targetBlockPos)) {
            return this.lastSearchData;
        }

        final Vec3d eyePos = entity.getEntityPos().add(0.0D, entity.getStandingEyeHeight(), 0.0D);
        final BlockData data = this.searchPlacementTarget(targetBlockPos, eyePos);
        if (canUseCache) {
            this.lastSearchOrigin = targetBlockPos.toImmutable();
            this.lastSearchData = data;
            this.lastSearchAge = entity.age;
        }
        return data;
    }

    private boolean isCachedBlockDataUsable(final BlockData data, final BlockPos targetBlockPos) {
        if (data == null || mc.world == null) {
            return false;
        }
        final BlockWithDirection block = data.blockWithDirection;
        return block.blockPos.offset(block.direction).equals(targetBlockPos)
                && this.isReplaceable(targetBlockPos)
                && this.isValidSupportBlock(block.blockPos);
    }

    private BlockData searchPlacementTarget(final BlockPos origin, final Vec3d eyePos) {
        final Queue<PlacementCandidate> queue = new PriorityQueue<>(Comparator
                .comparingDouble((PlacementCandidate candidate) -> candidate.pos.getSquaredDistance(origin))
                .thenComparingInt(candidate -> candidate.depth));
        final Set<BlockPos> visited = new HashSet<>();
        final Set<BlockWithDirection> checkedTargets = new HashSet<>();
        final int maxDepth = this.isTellyMode() ? TELLY_SEARCH_DEPTH : NORMAL_SEARCH_DEPTH;
        final int visitBudget = this.isTellyMode() ? TELLY_VISIT_BUDGET : NORMAL_VISIT_BUDGET;
        final int raytraceBudget = this.isTellyMode() ? TELLY_RAYTRACE_BUDGET : NORMAL_RAYTRACE_BUDGET;
        int visitedCount = 0;
        int raytraceCount = 0;

        queue.add(new PlacementCandidate(origin, 0));
        visited.add(origin);

        while (!queue.isEmpty() && visitedCount++ < visitBudget) {
            final PlacementCandidate candidate = queue.poll();
            if (candidate.depth > maxDepth || candidate.pos.getSquaredDistance(origin) > MAX_PLACEMENT_DISTANCE_SQUARED || !isReplaceable(candidate.pos)) {
                continue;
            }

            final List<BlockWithDirection> supports = this.getSortedSupports(candidate.pos, origin);
            for (final BlockWithDirection block : supports) {
                if (!checkedTargets.add(block)) {
                    continue;
                }
                if (raytraceCount++ >= raytraceBudget) {
                    return null;
                }

                final RaytracedRotation rotation = this.getRotation(block, eyePos);
                if (rotation != null) {
                    return new BlockData(block, rotation);
                }
            }

            if (candidate.depth >= maxDepth) {
                continue;
            }

            for (Direction direction : getPlacementDirections()) {
                final BlockPos next = candidate.pos.offset(direction);
                if (visited.size() >= visitBudget || next.getSquaredDistance(origin) > MAX_PLACEMENT_DISTANCE_SQUARED || !visited.add(next) || !this.isReplaceable(next)) {
                    continue;
                }
                queue.add(new PlacementCandidate(next, candidate.depth + 1));
            }
        }

        return null;
    }

    private List<BlockWithDirection> getSortedSupports(final BlockPos targetPos, final BlockPos origin) {
        final List<BlockWithDirection> targets = new ArrayList<>();
        for (Direction direction : getPlacementDirections()) {
            final BlockPos support = targetPos.offset(direction);
            if (isValidSupportBlock(support)) {
                targets.add(new BlockWithDirection(support, direction.getOpposite()));
            }
        }
        targets.sort(Comparator
                .comparingDouble((BlockWithDirection data) -> data.blockPos.offset(data.direction).getSquaredDistance(origin))
                .thenComparingInt(data -> getDirectionPriority(data.direction)));
        return targets;
    }

    private boolean isRecentlyPlacedTellySupport(final BlockPos pos) {
        if (!this.isTellyMode() || mc.player == null) {
            return false;
        }
        final Integer placedAge = this.recentPlacedBlocks.get(pos);
        return placedAge != null && mc.player.age - placedAge <= RECENT_SUPPORT_QUARANTINE_TICKS;
    }

    private void pruneRecentPlacedBlocks() {
        if (mc.player == null || mc.world == null || this.recentPlacedBlocks.isEmpty()) {
            return;
        }
        this.recentPlacedBlocks.entrySet().removeIf(entry ->
                mc.player.age - entry.getValue() > RECENT_PLACED_BLOCK_TTL
                        || mc.world.getBlockState(entry.getKey()).isReplaceable());
    }

    private void trackPlacedBlock(final BlockPos pos) {
        if (pos == null) {
            return;
        }
        this.lastPlacedBlocks.remove(pos);
        this.lastPlacedBlocks.addLast(pos.toImmutable());
        while (this.lastPlacedBlocks.size() > MAX_LAST_PLACED_BLOCKS) {
            this.lastPlacedBlocks.removeFirst();
        }
        this.lastSearchOrigin = null;
        this.lastSearchData = null;
    }

    public record BlockWithDirection(BlockPos blockPos, Direction direction) {
    }

    public record BlockData(BlockWithDirection blockWithDirection, RaytracedRotation rotation) {
    }

    private record PlacementCandidate(BlockPos pos, int depth) {
    }

    private record PlacementHand(Hand hand, BlockItem blockItem) {
    }

    @Override
    public void renderIsland(DrawContext context, float posX, float posY, float width, float height, float progress) {
        this.dynamicIsland.render(context, posX, posY);
    }

    public ScaffoldSettings getSettings() {
        return settings;
    }

    @Override
    public float getIslandWidth() {
        return this.dynamicIsland.getWidth();
    }

    @Override
    public float getIslandHeight() {
        return this.dynamicIsland.getHeight();
    }

    @Override
    public int getIslandPriority() {
        return 1;
    }

}

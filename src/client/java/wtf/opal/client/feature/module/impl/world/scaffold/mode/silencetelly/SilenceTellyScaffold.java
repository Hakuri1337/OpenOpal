package wtf.opal.client.feature.module.impl.world.scaffold.mode.silencetelly;

import net.minecraft.block.*;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.util.shape.VoxelShape;
import wtf.opal.client.feature.helper.impl.LocalDataWatch;
import wtf.opal.client.feature.helper.impl.player.mouse.MouseHelper;
import wtf.opal.client.feature.helper.impl.player.packet.blockage.impl.OutboundNetworkBlockage;
import wtf.opal.client.feature.helper.impl.player.rotation.RotationHelper;
import wtf.opal.client.feature.helper.impl.player.rotation.model.impl.InstantRotationModel;
import wtf.opal.client.feature.helper.impl.player.slot.SlotHelper;
import wtf.opal.client.feature.module.impl.world.scaffold.ScaffoldModule;
import wtf.opal.client.feature.module.impl.world.scaffold.ScaffoldSettings;
import wtf.opal.client.feature.module.property.impl.mode.ModuleMode;
import wtf.opal.client.renderer.world.WorldRenderer;
import wtf.opal.duck.ClientPlayerEntityAccess;
import wtf.opal.event.EventDispatcher;
import wtf.opal.event.impl.game.PostGameTickEvent;
import wtf.opal.event.impl.game.PreGameTickEvent;
import wtf.opal.event.impl.game.input.MouseHandleInputEvent;
import wtf.opal.event.impl.game.input.MoveInputEvent;
import wtf.opal.event.impl.game.packet.InstantaneousSendPacketEvent;
import wtf.opal.event.impl.game.player.interaction.block.BlockPlacedEvent;
import wtf.opal.event.impl.game.player.movement.PostMovementPacketEvent;
import wtf.opal.event.impl.game.player.movement.PreMovementPacketEvent;
import wtf.opal.event.impl.render.RenderWorldEvent;
import wtf.opal.event.subscriber.Subscribe;
import wtf.opal.mixin.LivingEntityAccessor;
import wtf.opal.mixin.PlayerMoveC2SPacketAccessor;
import wtf.opal.utility.misc.chat.ChatUtility;
import wtf.opal.utility.player.InventoryUtility;
import wtf.opal.utility.player.MoveUtility;
import wtf.opal.utility.render.ColorUtility;
import wtf.opal.utility.render.CustomRenderLayers;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static wtf.opal.client.Constants.mc;

public final class SilenceTellyScaffold extends ModuleMode<ScaffoldModule> {
    private static final int SEARCH_RADIUS = 5;
    private static final int MAX_RESCUE_ROTATE_TICKS = 8;

    private SlotData blockSlot;
    private BlockData blockData;
    private BlockData lastBlockData;
    private Vec2f rotation;
    private Vec2f lastRotation;
    private BlockPos lastPlacePosition;
    private int posY;
    private boolean canPlace;
    private int rotateCount;
    private int placeCount;
    private int tellyJumpTicks;
    private int ups;
    private int movementCancelTicks;
    private int lastPlaceAge = -1;
    private boolean waitingForEagleSneak;
    private boolean modulePressedSneak;
    private boolean blockFlyDesyncing;
    private int blockFlyTicks;
    private float lastForward;
    private float lastSideways;
    private float lastPlacedYawDelta = Float.NaN;
    private int lastSearchAge = -1;
    private BlockPos lastSearchOrigin;
    private BlockData lastSearchData;
    private String lastDebugMessage;
    private int lastDebugAge = -1;
    private boolean placementRequested;
    private int placementRequestAge = -1;
    private BlockData placementRequestData;
    private SlotData placementRequestSlot;
    private Vec2f placementRequestRotation;
    private Vec2f packetRotation;
    private Vec3d packetEyePos;
    private boolean placementArmed;
    private int placementArmedAge = -1;
    private BlockData placementArmedData;
    private SlotData placementArmedSlot;
    private Vec2f placementArmedRotation;
    private float placementArmedYawDelta = Float.NaN;
    private Vec2f serverRotation;
    private Vec2f observedMovementRotation;
    private int observedMovementAge = -1;

    public SilenceTellyScaffold(final ScaffoldModule module) {
        super(module);
    }

    @Override
    public void onEnable() {
        this.resetState();
        if (mc.player != null) {
            this.posY = MathHelper.floor(mc.player.getY() - 1.0D);
            this.lastRotation = new Vec2f(mc.player.getYaw(), mc.player.getPitch());
            this.serverRotation = this.lastRotation;
        }
        super.onEnable();
    }

    @Override
    public void onDisable() {
        this.releaseModuleSneak();
        SlotHelper.getInstance().stop();
        this.resetBlockFly();
        this.resetState();
        super.onDisable();
    }

    @Subscribe(priority = 4)
    public void onMoveInput(final MoveInputEvent event) {
        if (!this.ready()) {
            return;
        }

        this.lastForward = event.getForward();
        this.lastSideways = event.getSideways();

        if (this.movementCancelTicks > 0) {
            event.setForward(0.0F);
            event.setSideways(0.0F);
            this.movementCancelTicks--;
            return;
        }

        this.updateEagleSneak(event);

        if (this.settings().getSilenceTellyMode() != ScaffoldSettings.SilenceTellyMode.TELLY || this.blockSlot == null || !this.isSlotStillValid(this.blockSlot)) {
            return;
        }

        if (this.shouldJump(event)) {
            ((LivingEntityAccessor) mc.player).setJumpingCooldown(0);
            event.setJump(true);
            if (this.settings().isSilenceTellyEagle()) {
                this.waitingForEagleSneak = true;
                this.tellyJumpTicks = 0;
            }
        }
    }

    @Subscribe(priority = 2)
    public void onPreGameTick(final PreGameTickEvent event) {
        if (!this.ready()) {
            this.clearTarget();
            return;
        }

        if (this.placementRequestAge != mc.player.age) {
            this.clearPlacementRequest();
        }
        if (this.placementArmed && this.placementArmedAge != mc.player.age) {
            this.clearPlacementArmed();
        }
        if (this.placementArmed) {
            if (this.isSlotStillValid(this.placementArmedSlot)) {
                this.applySlot(this.placementArmedSlot);
                return;
            }
            this.clearPlacementArmed();
        }

        this.preparePlacementTarget();
    }

    private boolean preparePlacementTarget() {
        this.blockSlot = this.selectBlockSlot();
        if (this.blockSlot == null || !this.isSlotStillValid(this.blockSlot)) {
            this.clearTarget();
            SlotHelper.getInstance().stop();
            return false;
        }

        this.updatePosY();
        this.updateBlockTarget();
        this.updateCanPlace();
        this.applySlot(this.blockSlot);
        final boolean forceRotation = this.updateRescueState();
        this.rotation = this.getBlockRotation(forceRotation);
        this.rotation = this.applyDuplicateRotation(this.rotation);
        this.rotation = this.applyFixedSensitivity(this.rotation);

        if (this.rotation != null) {
            RotationHelper.getHandler().rotate(this.rotation, InstantRotationModel.INSTANCE);
        }
        return this.blockData != null && this.rotation != null;
    }

    @Subscribe(priority = 3)
    public void onMouseHandleInput(final MouseHandleInputEvent event) {
        if (!this.ready()) {
            return;
        }

        MouseHelper.getRightButton().setDisabled();
        final boolean hadArmedPlacement = this.placementArmed;
        final boolean placed = this.tryPlaceArmed();
        if (hadArmedPlacement) {
            if (placed) {
                this.invalidateSearchCache();
            }
            this.preparePlacementTarget();
        }
        this.requestCurrentPlacement();
    }

    private void requestCurrentPlacement() {
        if (this.blockData == null || this.blockSlot == null) {
            return;
        }
        if (!this.canPlace || this.isOutgoingPlacementBlocked()) {
            return;
        }

        final Vec2f appliedRotation = this.getAppliedRotation();
        final Vec3d eyePos = mc.player.getEyePos();
        final BlockHitResult hitResult = SilenceTellyRaycastUtility.getFacedBlock(eyePos, appliedRotation, SilenceTellyRaycastUtility::isIgnoredBlock);
        if (!SilenceTellyRaycastUtility.didHitBlockFace(eyePos, appliedRotation, this.blockData.pos(), this.blockData.facing(), true)) {
            this.debug("hit fail expected=" + this.blockData.pos().toShortString() + "/" + this.blockData.facing()
                    + " actual=" + (hitResult == null ? "null" : hitResult.getBlockPos().toShortString() + "/" + hitResult.getSide()));
            return;
        }

        if (!this.isSlotStillValid(this.blockSlot) || !this.canPlaceAt(this.blockData, hitResult, this.blockSlot)) {
            this.debug("place validation failed");
            return;
        }

        this.placementRequested = true;
        this.placementRequestAge = mc.player.age;
        this.placementRequestData = this.blockData;
        this.placementRequestSlot = this.blockSlot;
        this.placementRequestRotation = appliedRotation;
    }

    private boolean tryPlaceArmed() {
        if (!this.placementArmed) {
            return false;
        }
        if (this.placementArmedAge != mc.player.age || this.lastPlaceAge == mc.player.age) {
            this.debug("discard stale armed placement age=" + this.placementArmedAge);
            this.clearPlacementArmed();
            return false;
        }
        if (this.isOutgoingPlacementBlocked()) {
            this.debug("discard armed placement while packets are blocked");
            this.clearPlacementArmed();
            return false;
        }

        final BlockData armedData = this.placementArmedData;
        final SlotData armedSlot = this.placementArmedSlot;
        final Vec2f armedRotation = this.placementArmedRotation;
        final float armedYawDelta = this.placementArmedYawDelta;
        final Vec3d eyePos = mc.player.getEyePos();
        final BlockHitResult hitResult = SilenceTellyRaycastUtility.getFacedBlock(
                eyePos,
                armedRotation,
                SilenceTellyRaycastUtility::isIgnoredBlock
        );
        if (!SilenceTellyRaycastUtility.didHitBlockFace(
                eyePos,
                armedRotation,
                armedData.pos(),
                armedData.facing(),
                true
        )) {
            this.debug("armed hit mismatch");
            this.clearPlacementArmed();
            return false;
        }
        if (!this.isSlotStillValid(armedSlot) || !this.canPlaceAt(armedData, hitResult, armedSlot)) {
            this.debug("armed place validation failed");
            this.clearPlacementArmed();
            return false;
        }

        this.blockData = armedData;
        this.blockSlot = armedSlot;
        this.applySlot(armedSlot);
        if (this.settings().isSilenceTellyAbuseRotation()) {
            this.abuseRotation(30.0F, armedRotation.x);
        }
        if (this.settings().isSilenceTellyBlockFly()) {
            this.updateBlockFlyBeforePlace();
        }
        if (this.settings().isSilenceTellyInteractItemBeforePlace()) {
            this.interactItemBeforePlace();
        }

        this.clearPlacementArmed();
        final ActionResult result = mc.interactionManager.interactBlock(mc.player, armedSlot.hand(), hitResult);
        if (!result.isAccepted()) {
            this.debug("interact rejected result=" + result);
            return false;
        }

        if (this.settings().isSilenceTellyNoSwing()) {
            ((ClientPlayerEntityAccess) mc.player).opal$swingHandServerside(armedSlot.hand());
        } else {
            mc.player.swingHand(armedSlot.hand());
        }

        this.lastPlaceAge = mc.player.age;
        if (!Float.isNaN(armedYawDelta)) {
            this.lastPlacedYawDelta = armedYawDelta;
        }
        this.placeCount++;
        this.lastPlacePosition = armedData.placePos();
        EventDispatcher.dispatch(new BlockPlacedEvent(hitResult));
        this.debug("place ok hand=" + armedSlot.hand() + " slot=" + armedSlot.slot() + " face=" + armedData.facing());
        return true;
    }

    // Placement rotation must be the final movement-event writer; the event bus runs higher priorities first.
    @Subscribe(priority = -100)
    public void onPreMovementPacket(final PreMovementPacketEvent event) {
        if (!this.hasCurrentPlacementRequest() || this.isOutgoingPlacementBlocked()) {
            this.packetRotation = null;
            this.packetEyePos = null;
            return;
        }

        final Vec3d eyePos = this.getPacketEyePos(event.getX(), event.getY(), event.getZ());
        Vec2f target = this.placementRequestRotation;
        if (!SilenceTellyRaycastUtility.didHitBlockFace(
                eyePos,
                target,
                this.placementRequestData.pos(),
                this.placementRequestData.facing(),
                true
        )) {
            final Vec2f stableTarget = SilenceTellyRotationUtility.getClosestToBlockFace(
                    this.placementRequestData.pos(),
                    this.placementRequestData.facing(),
                    new Vec2f(event.getYaw(), event.getPitch()),
                    eyePos,
                    this.settings().isSilenceTellyFixRotation()
            );
            target = this.applyDuplicateRotation(stableTarget);
            if (!SilenceTellyRaycastUtility.didHitBlockFace(
                    eyePos,
                    target,
                    this.placementRequestData.pos(),
                    this.placementRequestData.facing(),
                    true
            )) {
                target = stableTarget;
            }
        }

        target = this.avoidDuplicateYawDelta(target, eyePos, this.placementRequestData);

        if (!SilenceTellyRaycastUtility.didHitBlockFace(
                eyePos,
                target,
                this.placementRequestData.pos(),
                this.placementRequestData.facing(),
                true
        )) {
            this.debug("packet rotation cannot hit target");
            this.packetRotation = null;
            this.packetEyePos = null;
            return;
        }

        event.setYaw(target.x);
        event.setPitch(target.y);
        this.packetRotation = target;
        this.packetEyePos = eyePos;
    }

    @Subscribe(priority = -100)
    public void onInstantaneousSendPacket(final InstantaneousSendPacketEvent event) {
        if (mc.player == null || !(event.getPacket() instanceof PlayerMoveC2SPacket movePacket) || !movePacket.changesLook()) {
            return;
        }

        Vec2f outboundRotation = new Vec2f(
                movePacket.getYaw(mc.player.getYaw()),
                movePacket.getPitch(mc.player.getPitch())
        );
        final boolean placementPacket = this.hasCurrentPlacementRequest()
                && this.packetRotation != null
                && this.packetEyePos != null;
        if (placementPacket && !SilenceTellyRaycastUtility.didHitBlockFace(
                this.packetEyePos,
                outboundRotation,
                this.placementRequestData.pos(),
                this.placementRequestData.facing(),
                true
        )) {
            final PlayerMoveC2SPacketAccessor accessor = (PlayerMoveC2SPacketAccessor) movePacket;
            accessor.setYaw(this.packetRotation.x);
            accessor.setPitch(this.packetRotation.y);
            outboundRotation = this.packetRotation;
        }
        if (placementPacket) {
            this.packetRotation = outboundRotation;
        }
        this.observedMovementRotation = outboundRotation;
        this.observedMovementAge = mc.player.age;
    }

    @Subscribe
    public void onPostMovementPacket(final PostMovementPacketEvent event) {
        if (!this.ready()) {
            this.clearPlacementRequest();
            this.clearPlacementArmed();
            return;
        }

        final Vec2f previousServerRotation = this.serverRotation;
        if (this.observedMovementRotation != null && this.observedMovementAge == mc.player.age) {
            this.serverRotation = this.observedMovementRotation;
        } else if (this.serverRotation == null) {
            this.serverRotation = new Vec2f(event.getYaw(), event.getPitch());
        }
        this.observedMovementRotation = null;
        this.observedMovementAge = -1;
        if (!this.hasCurrentPlacementRequest() || this.packetRotation == null || this.isOutgoingPlacementBlocked()) {
            this.clearPlacementRequest();
            return;
        }

        final BlockData requestedData = this.placementRequestData;
        final SlotData requestedSlot = this.placementRequestSlot;
        final Vec2f appliedRotation = this.serverRotation;
        this.clearPlacementRequest();

        final Vec3d eyePos = this.getPacketEyePos(event.getX(), event.getY(), event.getZ());
        final BlockHitResult hitResult = SilenceTellyRaycastUtility.getFacedBlock(
                eyePos,
                appliedRotation,
                SilenceTellyRaycastUtility::isIgnoredBlock
        );
        if (!SilenceTellyRaycastUtility.didHitBlockFace(
                eyePos,
                appliedRotation,
                requestedData.pos(),
                requestedData.facing(),
                true
        )) {
            this.debug("post-packet hit mismatch");
            return;
        }

        if (!this.isSlotStillValid(requestedSlot) || !this.canPlaceAt(requestedData, hitResult, requestedSlot)) {
            this.debug("post-packet place validation failed");
            return;
        }

        // Modern clients place before flying; carry the validated target into the next input phase.
        this.placementArmed = true;
        this.placementArmedAge = mc.player.age;
        this.placementArmedData = requestedData;
        this.placementArmedSlot = requestedSlot;
        this.placementArmedRotation = appliedRotation;
        if (previousServerRotation != null) {
            this.placementArmedYawDelta = Math.abs(MathHelper.wrapDegrees(appliedRotation.x - previousServerRotation.x));
        } else {
            this.placementArmedYawDelta = Float.NaN;
        }
        this.debug("placement armed face=" + requestedData.facing());
    }

    @Subscribe
    public void onPostGameTick(final PostGameTickEvent event) {
        if (this.blockFlyDesyncing && ++this.blockFlyTicks > 16) {
            this.resetBlockFly();
        }
    }

    @Subscribe
    public void onRenderWorld(final RenderWorldEvent event) {
        if (!this.settings().isSilenceTellyMark() || this.lastPlacePosition == null) {
            return;
        }

        final Vec3d startVec = new Vec3d(this.lastPlacePosition.getX(), this.lastPlacePosition.getY(), this.lastPlacePosition.getZ());
        final VertexConsumerProvider.Immediate vcp = VertexConsumerProvider.immediate(new BufferAllocator(1024));
        final WorldRenderer renderer = new WorldRenderer(vcp);
        renderer.drawFilledCube(event.matrixStack(), CustomRenderLayers.getPositionColorQuads(true), startVec, new Vec3d(1.0D, 1.0D, 1.0D), ColorUtility.applyOpacity(ColorUtility.getClientTheme().first, 0.22F));
        vcp.draw();
    }

    @Override
    public Enum<?> getEnumValue() {
        return ScaffoldSettings.Mode.SILENCE_TELLY;
    }

    private void updateBlockTarget() {
        BlockData possible = null;
        final BlockPos playerBlock = BlockPos.ofFloored(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        if (SilenceTellyRaycastUtility.isIgnoredBlock(mc.world.getBlockState(playerBlock))) {
            possible = this.getBlockData(new BlockPos(MathHelper.floor(mc.player.getX()), this.posY, MathHelper.floor(mc.player.getZ())));
        }
        if (possible != null) {
            this.blockData = possible;
        }
        this.lastBlockData = possible;
        if (!this.isBlockDataUsable(this.blockData)) {
            this.blockData = possible;
        }
    }

    private boolean updateRescueState() {
        boolean reachable = true;
        boolean forceRotation = false;
        final SilenceTellyFallingPlayer fallingPlayer = new SilenceTellyFallingPlayer(mc.player, this.lastForward, this.lastSideways);
        fallingPlayer.calculate(1);
        final Vec3d nextEyePos = fallingPlayer.getEyePos();
        fallingPlayer.calculate(1);

        final BlockData placement = this.getBlockData(new BlockPos(MathHelper.floor(mc.player.getX()), mc.player.getBlockY() - 1, MathHelper.floor(mc.player.getZ())));
        if (placement != null) {
            if (this.settings().isSilenceTellySafeMode() && this.settings().isSilenceTellyTestOnGround()
                    && LocalDataWatch.get().groundTicks == 1 && mc.options.jumpKey.isPressed()) {
                forceRotation = true;
            }
            final double distance = nextEyePos.distanceTo(placement.pos().toCenterPos());
            if (distance >= this.settings().getSilenceTellySafeDistance() || placement.pos().getY() > fallingPlayer.getY()) {
                this.canPlace = true;
                reachable = false;
                this.blockData = this.lastBlockData = placement;
                this.debug("rescue dist=" + String.format("%.2f", distance) + " predY=" + String.format("%.2f", fallingPlayer.getY()));
            }
        }

        if (this.blockData != null) {
            final Box blockBox = new Box(
                    this.blockData.pos().getX(), this.blockData.pos().getY() - 1.0D, this.blockData.pos().getZ(),
                    this.blockData.pos().getX() + 1.0D, this.blockData.pos().getY() + 1.0D, this.blockData.pos().getZ() + 1.0D
            );
            if (this.blockData.pos().getY() > fallingPlayer.getY() && !blockBox.contains(mc.player.getEntityPos())) {
                this.canPlace = true;
                reachable = false;
                this.posY = mc.player.getBlockY() - 1;
                this.blockData = this.lastBlockData = this.getBlockData(new BlockPos(MathHelper.floor(mc.player.getX()), this.posY, MathHelper.floor(mc.player.getZ())));
            }
        }

        if (!reachable && this.rotateCount < MAX_RESCUE_ROTATE_TICKS) {
            this.movementCancelTicks = Math.max(this.movementCancelTicks, 1);
            this.rotateCount++;
        } else if (reachable) {
            this.rotateCount = 0;
        }
        return forceRotation;
    }

    private Vec2f getBlockRotation(final boolean forceRotation) {
        if (this.blockData == null) {
            return null;
        }

        final Vec2f reference = this.getReferenceRotation();
        Vec2f target = SilenceTellyRotationUtility.getClosestToBlockFace(this.blockData.pos(), this.blockData.facing(), reference);
        if (target == null) {
            final float plus = mc.player.getYaw() + 100.0F;
            final float minus = mc.player.getYaw() - 100.0F;
            target = Math.abs(MathHelper.wrapDegrees(plus - reference.x)) < Math.abs(MathHelper.wrapDegrees(minus - reference.x))
                    ? new Vec2f(plus, reference.y)
                    : new Vec2f(minus, reference.y);
        }

        if (this.movementCancelTicks > 0) {
            return SilenceTellyRotationUtility.getClosestToBlockFace(this.blockData.pos(), this.blockData.facing(), reference);
        }

        final ScaffoldSettings settings = this.settings();
        final int airTicks = LocalDataWatch.get().airTicks;
        final int groundTicks = LocalDataWatch.get().groundTicks;
        final float diff = SilenceTellyRotationUtility.yawDiffDirectly(target.x, reference.x);

        if (settings.getSilenceTellyMode() == ScaffoldSettings.SilenceTellyMode.TELLY) {
            if (mc.options.jumpKey.isPressed() && settings.isSilenceTellyNoUptelly()) {
                return this.rememberUsableRotation(target);
            }
            if (mc.options.jumpKey.isPressed() && settings.isSilenceTellySlowUpTelly()) {
                this.ups++;
                if (this.ups % 2 == 0) {
                    return this.rememberUsableRotation(target);
                }
            }

            if (settings.isSilenceTellyHeypixelUpTelly() && (airTicks < settings.getSilenceTellyRotationTick() || settings.isSilenceTellySafeMode())) {
                if (groundTicks > 0) {
                    if (settings.isSilenceTellySafeMode() && (!settings.isSilenceTellyTestOnGround() || mc.options.jumpKey.isPressed())) {
                        if (groundTicks == 1) {
                            if (!forceRotation) {
                                target = new Vec2f(reference.x + SilenceTellyRotationUtility.smooth(diff, diff / 2.0F), 75.5F);
                            } else {
                                target = SilenceTellyRotationUtility.getClosestToBlockFace(this.blockData.pos(), this.blockData.facing(), new Vec2f(mc.player.getYaw(), reference.y));
                            }
                            ((LivingEntityAccessor) mc.player).setJumpingCooldown(2);
                        } else if (groundTicks == 2) {
                            return new Vec2f(mc.player.getYaw(), 75.5F);
                        }
                    } else {
                        return new Vec2f(mc.player.getYaw(), 75.5F);
                    }
                } else {
                    float smooth = airTicks == 1 ? 80.0F : 50.0F;
                    smooth -= this.randomFloat(0.001F, 0.005F);
                    target = new Vec2f(reference.x + SilenceTellyRotationUtility.smooth(diff, smooth), target.y);
                }
            } else if (airTicks < settings.getSilenceTellyRotationTick()) {
                if (!settings.isSilenceTellySnap() || mc.options.jumpKey.isPressed()) {
                    return new Vec2f(mc.player.getYaw(), 85.0F + ThreadLocalRandom.current().nextFloat());
                }
                if (this.lastBlockData == null) {
                    return new Vec2f(mc.player.getYaw(), 85.0F + ThreadLocalRandom.current().nextFloat());
                }
            }
        } else if (settings.getSilenceTellyMode() == ScaffoldSettings.SilenceTellyMode.NORMAL && settings.isSilenceTellyGodBridge()) {
            target = new Vec2f(MoveUtility.getDirectionDegrees(), 75.5F);
        }

        return this.rememberUsableRotation(target);
    }

    private Vec2f rememberUsableRotation(final Vec2f target) {
        if (target == null || this.blockData == null) {
            return target;
        }
        final Vec3d eyePos = mc.player.getEyePos();
        if (this.lastRotation != null && SilenceTellyRaycastUtility.didHitBlockFace(eyePos, this.lastRotation, this.blockData.pos(), this.blockData.facing(), true)) {
            return this.lastRotation;
        }
        if (!this.settings().isSilenceTellyAlwaysUpdateRotation()
                && LocalDataWatch.get().airTicks >= this.settings().getSilenceTellyRotationTick()
                && !SilenceTellyRaycastUtility.didHitBlockFace(eyePos, target, this.blockData.pos(), this.blockData.facing(), true)
                && this.lastRotation != null) {
            this.lastRotation = new Vec2f(this.lastRotation.x + ThreadLocalRandom.current().nextFloat(), this.lastRotation.y);
            return this.lastRotation;
        }
        this.lastRotation = target;
        return target;
    }

    private Vec2f applyDuplicateRotation(final Vec2f target) {
        if (target == null || !this.settings().isSilenceTellyDuplicateRotPlace()) {
            return target;
        }
        float yaw = target.x - this.randomFloat(0.0001F, 0.0003F);
        float pitch = target.y - this.randomFloat(0.001F, 0.003F);
        while (pitch > 90.0F) {
            pitch -= this.randomFloat(0.001F, 0.003F);
        }
        pitch = MathHelper.clamp(pitch, -90.0F, 90.0F);
        return new Vec2f(yaw, pitch);
    }

    private Vec2f avoidDuplicateYawDelta(final Vec2f target, final Vec3d eyePos, final BlockData data) {
        if (target == null || eyePos == null || data == null || this.serverRotation == null
                || !this.settings().isSilenceTellyDuplicateRotPlace() || Float.isNaN(this.lastPlacedYawDelta)) {
            return target;
        }

        final float yawDelta = Math.abs(MathHelper.wrapDegrees(target.x - this.serverRotation.x));
        if (yawDelta <= 2.0F || Math.abs(yawDelta - this.lastPlacedYawDelta) >= 1.0E-4F) {
            return target;
        }

        final float[] offsets = {0.15F, -0.15F, 0.3F, -0.3F, 0.6F, -0.6F, 1.0F, -1.0F};
        for (final float offset : offsets) {
            Vec2f candidate = new Vec2f(
                    MathHelper.wrapDegrees(target.x + offset),
                    target.y
            );
            if (this.settings().isSilenceTellyFixRotation()) {
                candidate = SilenceTellyRotationUtility.patchSensitivity(candidate, this.serverRotation);
            }

            final float candidateDelta = Math.abs(MathHelper.wrapDegrees(candidate.x - this.serverRotation.x));
            if (Math.abs(candidateDelta - this.lastPlacedYawDelta) < 1.0E-4F) {
                continue;
            }
            if (SilenceTellyRaycastUtility.didHitBlockFace(eyePos, candidate, data.pos(), data.facing(), true)) {
                return candidate;
            }
        }
        return target;
    }

    private Vec2f applyFixedSensitivity(final Vec2f target) {
        return this.applyFixedSensitivity(target, this.getReferenceRotation());
    }

    private Vec2f applyFixedSensitivity(final Vec2f target, final Vec2f reference) {
        if (target == null || !this.settings().isSilenceTellyFixRotation()) {
            return target;
        }
        return SilenceTellyRotationUtility.patchSensitivity(target, reference);
    }

    private void updateCanPlace() {
        switch (this.settings().getSilenceTellyMode()) {
            case NORMAL -> this.canPlace = true;
            case SNAP -> this.canPlace = this.doesNotContainBlock(1);
            case TELLY -> {
                this.canPlace = LocalDataWatch.get().airTicks >= this.settings().getSilenceTellyPlaceTick();
                if (this.settings().isSilenceTellySafeMode() && this.settings().isSilenceTellyTestOnGround()
                        && !this.canPlace && mc.options.jumpKey.isPressed()) {
                    this.canPlace = LocalDataWatch.get().groundTicks == 1;
                }
            }
        }
    }

    private boolean shouldJump(final MoveInputEvent event) {
        if (!mc.player.isOnGround() || mc.player.isSneaking() || event.isSneak()) {
            return false;
        }
        if (!MoveUtility.isMoving() && Math.abs(event.getForward()) < 1.0E-4F && Math.abs(event.getSideways()) < 1.0E-4F) {
            return false;
        }
        if (mc.options.jumpKey.isPressed() || mc.options.useKey.isPressed()) {
            return false;
        }
        final int groundThreshold = this.settings().isSilenceTellyHeypixelUpTelly()
                && this.settings().isSilenceTellySafeMode()
                && !this.settings().isSilenceTellyTestOnGround() ? 1 : 0;
        if (LocalDataWatch.get().groundTicks <= groundThreshold) {
            return false;
        }

        return switch (this.settings().getSilenceTellyJumpMode()) {
            case NONE -> false;
            case NORMAL -> true;
            case PARKOUR -> this.shouldParkourJump();
        };
    }

    private boolean shouldParkourJump() {
        final double yaw = Math.toRadians(mc.player.getYaw());
        final double forwardX = -Math.sin(yaw);
        final double forwardZ = Math.cos(yaw);
        final BlockPos frontOne = BlockPos.ofFloored(mc.player.getX() + forwardX, mc.player.getY() - 0.1D, mc.player.getZ() + forwardZ);
        final BlockPos frontTwo = BlockPos.ofFloored(mc.player.getX() + forwardX * 2.0D, mc.player.getY() - 0.1D, mc.player.getZ() + forwardZ * 2.0D);
        return mc.world.getBlockState(frontOne).isAir() || mc.world.getBlockState(frontTwo).isAir();
    }

    private void updateEagleSneak(final MoveInputEvent event) {
        if (!this.waitingForEagleSneak) {
            return;
        }
        this.tellyJumpTicks++;
        final int start = this.settings().getSilenceTellyEagleTick();
        final int end = start + this.settings().getSilenceTellyKeepEagleTick();
        if (this.tellyJumpTicks == start && !mc.options.sneakKey.isPressed()) {
            mc.options.sneakKey.setPressed(true);
            event.setSneak(true);
            this.modulePressedSneak = true;
        }
        if (this.tellyJumpTicks >= end) {
            this.releaseModuleSneak();
            this.waitingForEagleSneak = false;
            this.tellyJumpTicks = 0;
        }
    }

    private BlockData getBlockData(final BlockPos pos) {
        if (pos == null || mc.player == null || mc.world == null) {
            return null;
        }
        if (this.lastSearchAge == mc.player.age && pos.equals(this.lastSearchOrigin)) {
            return this.lastSearchData;
        }

        BlockData data = this.getPos(pos);
        if (data == null) {
            final BlockPos blockPos = this.getBlockPos();
            if (blockPos == null) {
                return this.cacheSearch(pos, null);
            }
            final Direction direction = this.getPlaceSide(blockPos);
            if (direction == null) {
                return this.cacheSearch(pos, null);
            }
            data = new BlockData(blockPos, direction);
        }

        if (this.isReplaceableForPlacement(data.placePos())) {
            return this.cacheSearch(pos, data);
        }
        return this.cacheSearch(pos, null);
    }

    private BlockData cacheSearch(final BlockPos origin, final BlockData data) {
        this.lastSearchAge = mc.player == null ? -1 : mc.player.age;
        this.lastSearchOrigin = origin == null ? null : origin.toImmutable();
        this.lastSearchData = data;
        return data;
    }

    private BlockData getPos(final BlockPos pos) {
        if (this.isPosSolid(pos.add(-1, 0, 0))) {
            return new BlockData(pos.add(-1, 0, 0), Direction.EAST);
        } else if (this.isPosSolid(pos.add(1, 0, 0))) {
            return new BlockData(pos.add(1, 0, 0), Direction.WEST);
        } else if (this.isPosSolid(pos.add(0, 0, 1))) {
            return new BlockData(pos.add(0, 0, 1), Direction.NORTH);
        } else if (this.isPosSolid(pos.add(0, 0, -1))) {
            return new BlockData(pos.add(0, 0, -1), Direction.SOUTH);
        } else if (this.isPosSolid(pos.add(0, -1, 0))) {
            return new BlockData(pos.add(0, -1, 0), Direction.UP);
        }
        return null;
    }

    private BlockPos getBlockPos() {
        final BlockPos playerPos = BlockPos.ofFloored(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        final List<BlockPos> positions = new ArrayList<>();
        for (int x = SEARCH_RADIUS; x >= -SEARCH_RADIUS + 1; x--) {
            for (int y = SEARCH_RADIUS; y >= -SEARCH_RADIUS + 1; y--) {
                for (int z = SEARCH_RADIUS; z >= -SEARCH_RADIUS + 1; z--) {
                    final BlockPos blockPos = playerPos.add(x, y, z);
                    if (this.isPosSolid(blockPos) && blockPos.getY() < playerPos.getY()) {
                        positions.add(blockPos);
                    }
                }
            }
        }
        if (positions.isEmpty()) {
            return null;
        }
        positions.sort(Comparator.comparingDouble(pos -> pos.getSquaredDistance(playerPos)));
        return positions.getFirst();
    }

    private Direction getPlaceSide(final BlockPos blockPos) {
        final BlockPos playerBlock = BlockPos.ofFloored(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        final List<BlockData> candidates = new ArrayList<>();
        this.addPlaceSide(candidates, blockPos, Direction.EAST, playerBlock);
        this.addPlaceSide(candidates, blockPos, Direction.NORTH, playerBlock);
        this.addPlaceSide(candidates, blockPos, Direction.SOUTH, playerBlock);
        this.addPlaceSide(candidates, blockPos, Direction.WEST, playerBlock);
        if (candidates.isEmpty()) {
            return null;
        }
        candidates.sort(Comparator.comparingDouble(data -> data.placePos().getSquaredDistance(playerBlock)));
        return candidates.getFirst().facing();
    }

    private void addPlaceSide(final List<BlockData> candidates, final BlockPos support, final Direction face, final BlockPos playerBlock) {
        final BlockPos placePos = support.offset(face);
        if (!placePos.equals(playerBlock) && this.isReplaceableForPlacement(placePos)) {
            candidates.add(new BlockData(support, face));
        }
    }

    private boolean isPosSolid(final BlockPos pos) {
        if (mc.world == null || mc.world.isOutOfHeightLimit(pos.getY())) {
            return false;
        }
        final BlockState state = mc.world.getBlockState(pos);
        final Block block = state.getBlock();
        return !state.isAir()
                && !state.isReplaceable()
                && state.getFluidState().isEmpty()
                && !state.getCollisionShape(mc.world, pos).isEmpty()
                && !InventoryUtility.isBlockInteractable(block)
                && !SilenceTellyRaycastUtility.isIgnoredBlock(state)
                && !(block instanceof TrapdoorBlock)
                && !(block instanceof DoorBlock)
                && !(block instanceof FenceGateBlock)
                && block != Blocks.COBWEB
                && block != Blocks.FIRE;
    }

    private boolean isReplaceableForPlacement(final BlockPos pos) {
        return mc.world != null
                && !mc.world.isOutOfHeightLimit(pos.getY())
                && (mc.world.getBlockState(pos).isReplaceable() || SilenceTellyRaycastUtility.isIgnoredBlock(mc.world.getBlockState(pos)));
    }

    private boolean canPlaceAt(final BlockData data, final BlockHitResult hitResult, final SlotData slot) {
        if (data == null || hitResult == null || slot == null || !this.isPosSolid(data.pos()) || !this.isReplaceableForPlacement(data.placePos())) {
            return false;
        }
        final ItemStack stack = this.getStack(slot);
        if (!(stack.getItem() instanceof BlockItem blockItem)) {
            return false;
        }
        final ItemUsageContext usageContext = new ItemUsageContext(mc.player, slot.hand(), hitResult);
        final ItemPlacementContext placementContext = blockItem.getPlacementContext(new ItemPlacementContext(usageContext));
        if (placementContext == null) {
            return false;
        }
        final BlockState placementState = blockItem.getBlock().getPlacementState(placementContext);
        if (placementState == null || !placementState.canPlaceAt(mc.world, data.placePos())) {
            return false;
        }
        final VoxelShape collisionShape = placementState.getCollisionShape(mc.world, data.placePos());
        if (!collisionShape.isEmpty()) {
            final Box blockBox = collisionShape.getBoundingBox().offset(data.placePos());
            if (mc.player.getBoundingBox().intersects(blockBox) && !this.isLandingPlacementCollisionAllowed(blockBox)) {
                return false;
            }
        }
        return true;
    }

    private boolean isLandingPlacementCollisionAllowed(final Box blockBox) {
        return mc.player.getVelocity().y <= 0.0D && mc.player.getBoundingBox().minY >= blockBox.maxY - 1.0E-3D;
    }

    private SlotData selectBlockSlot() {
        if (this.isValidBlockStack(mc.player.getOffHandStack())) {
            return new SlotData(-1, Hand.OFF_HAND);
        }
        final int selectedSlot = mc.player.getInventory().getSelectedSlot();
        if (this.settings().getSilenceTellyBlockSlotMode() != ScaffoldSettings.SilenceTellyBlockSlotMode.MOST_BLOCKS
                && this.isValidBlockStack(mc.player.getMainHandStack())) {
            return new SlotData(selectedSlot, Hand.MAIN_HAND);
        }

        int bestSlot = -1;
        int bestCount = -1;
        if (this.settings().getSilenceTellyBlockSlotMode() == ScaffoldSettings.SilenceTellyBlockSlotMode.MOST_BLOCKS) {
            for (int slot = 0; slot < 9; slot++) {
                final ItemStack stack = mc.player.getInventory().getStack(slot);
                if (this.isValidBlockStack(stack) && stack.getCount() > bestCount) {
                    bestSlot = slot;
                    bestCount = stack.getCount();
                }
            }
        } else {
            for (int slot = 0; slot < 9; slot++) {
                if (this.isValidBlockStack(mc.player.getInventory().getStack(slot))) {
                    bestSlot = slot;
                }
            }
        }
        return bestSlot == -1 ? null : new SlotData(bestSlot, Hand.MAIN_HAND);
    }

    private void applySlot(final SlotData slot) {
        if (slot.hand() == Hand.MAIN_HAND) {
            SlotHelper.setCurrentItem(slot.slot()).silence(this.settings().isSilenceTellySpoofItem() ? SlotHelper.Silence.DEFAULT : SlotHelper.Silence.NONE);
        }
    }

    private boolean isSlotStillValid(final SlotData slot) {
        return slot != null && this.isValidBlockStack(this.getStack(slot));
    }

    private ItemStack getStack(final SlotData slot) {
        if (slot.hand() == Hand.OFF_HAND) {
            return mc.player.getOffHandStack();
        }
        return mc.player.getInventory().getStack(slot.slot());
    }

    private boolean isValidBlockStack(final ItemStack stack) {
        return stack != null
                && !stack.isEmpty()
                && stack.getItem() instanceof BlockItem blockItem
                && InventoryUtility.isGoodBlock(blockItem.getBlock());
    }

    private boolean isBlockDataUsable(final BlockData data) {
        return data != null && this.isPosSolid(data.pos()) && this.isReplaceableForPlacement(data.placePos());
    }

    private boolean doesNotContainBlock(final int down) {
        final BlockPos pos = BlockPos.ofFloored(mc.player.getX(), mc.player.getY() - down, mc.player.getZ());
        final BlockState state = mc.world.getBlockState(pos);
        return state.isAir() || state.isReplaceable() || state.getCollisionShape(mc.world, pos).isEmpty();
    }

    private void updatePosY() {
        if (mc.player.isOnGround()) {
            this.posY = MathHelper.floor(mc.player.getY() - 1.0D);
        }
        if (mc.options.jumpKey.isPressed()) {
            this.posY = mc.player.getBlockY() - 1;
        }
    }

    private Vec2f getReferenceRotation() {
        if (this.serverRotation != null) {
            return this.serverRotation;
        }
        final Vec2f client = RotationHelper.getClientHandler().getRotation();
        return client == null ? new Vec2f(mc.player.getYaw(), mc.player.getPitch()) : client;
    }

    private Vec2f getAppliedRotation() {
        if (this.rotation != null) {
            return this.rotation;
        }
        final Vec2f client = RotationHelper.getClientHandler().getRotation();
        return client == null ? new Vec2f(mc.player.getYaw(), mc.player.getPitch()) : client;
    }

    private Vec3d getPacketEyePos(final double x, final double y, final double z) {
        return new Vec3d(x, y + mc.player.getEyeHeight(mc.player.getPose()), z);
    }

    private boolean hasCurrentPlacementRequest() {
        final int requestAgeDelta = mc.player.age - this.placementRequestAge;
        return this.placementRequested
                && requestAgeDelta >= 0
                && requestAgeDelta <= 1
                && this.placementRequestData != null
                && this.placementRequestSlot != null
                && this.placementRequestRotation != null
                && this.isBlockDataUsable(this.placementRequestData)
                && this.isSlotStillValid(this.placementRequestSlot);
    }

    private void clearPlacementRequest() {
        this.placementRequested = false;
        this.placementRequestAge = -1;
        this.placementRequestData = null;
        this.placementRequestSlot = null;
        this.placementRequestRotation = null;
        this.packetRotation = null;
        this.packetEyePos = null;
    }

    private void clearPlacementArmed() {
        this.placementArmed = false;
        this.placementArmedAge = -1;
        this.placementArmedData = null;
        this.placementArmedSlot = null;
        this.placementArmedRotation = null;
        this.placementArmedYawDelta = Float.NaN;
    }

    private void invalidateSearchCache() {
        this.lastSearchAge = -1;
        this.lastSearchOrigin = null;
        this.lastSearchData = null;
    }

    private boolean isOutgoingPlacementBlocked() {
        return OutboundNetworkBlockage.get().isAnyBlockages();
    }

    private void interactItemBeforePlace() {
        if (this.blockSlot.hand() != Hand.MAIN_HAND || !this.isValidBlockStack(SlotHelper.getInstance().getMainHandStack(mc.player))) {
            return;
        }
        mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
    }

    private void abuseRotation(final float step, final float targetYaw) {
        if (this.lastRotation == null || !this.isValidBlockStack(SlotHelper.getInstance().getMainHandStack(mc.player))) {
            return;
        }
        final float change = SilenceTellyRotationUtility.yawDiffDirectly(targetYaw, this.lastRotation.x);
        final int times = (int) (Math.abs(change) / step);
        for (int i = 0; i < times; i++) {
            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
        }
    }

    private void updateBlockFlyBeforePlace() {
        if (this.blockFlyDesyncing || mc.getNetworkHandler() == null) {
            return;
        }
        mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
                PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND,
                BlockPos.ORIGIN,
                Direction.DOWN
        ));
        this.blockFlyDesyncing = true;
        this.blockFlyTicks = 0;
    }

    private void resetBlockFly() {
        this.blockFlyDesyncing = false;
        this.blockFlyTicks = 0;
    }

    private boolean ready() {
        return mc.player != null && mc.world != null && mc.interactionManager != null && module.isEnabled();
    }

    private ScaffoldSettings settings() {
        return module.getSettings();
    }

    private void clearTarget() {
        this.clearPlacementRequest();
        this.clearPlacementArmed();
        this.blockData = null;
        this.rotation = null;
        this.canPlace = false;
        this.rotateCount = 0;
        this.movementCancelTicks = 0;
    }

    private void resetState() {
        this.blockSlot = null;
        this.blockData = null;
        this.lastBlockData = null;
        this.rotation = null;
        this.lastRotation = null;
        this.lastPlacePosition = null;
        this.canPlace = false;
        this.rotateCount = 0;
        this.placeCount = 0;
        this.tellyJumpTicks = 0;
        this.ups = 0;
        this.movementCancelTicks = 0;
        this.lastPlaceAge = -1;
        this.waitingForEagleSneak = false;
        this.modulePressedSneak = false;
        this.lastForward = 0.0F;
        this.lastSideways = 0.0F;
        this.lastPlacedYawDelta = Float.NaN;
        this.lastSearchAge = -1;
        this.lastSearchOrigin = null;
        this.lastSearchData = null;
        this.lastDebugMessage = null;
        this.lastDebugAge = -1;
        this.clearPlacementRequest();
        this.clearPlacementArmed();
        this.serverRotation = null;
        this.observedMovementRotation = null;
        this.observedMovementAge = -1;
    }

    private void releaseModuleSneak() {
        if (this.modulePressedSneak) {
            mc.options.sneakKey.setPressed(false);
            this.modulePressedSneak = false;
        }
    }

    private void debug(final String message) {
        if (!this.settings().isSilenceTellyDebug() || mc.player == null) {
            return;
        }
        if (message.equals(this.lastDebugMessage) && mc.player.age - this.lastDebugAge < 8) {
            return;
        }
        this.lastDebugMessage = message;
        this.lastDebugAge = mc.player.age;
        ChatUtility.print("SilenceTelly | " + message);
    }

    private float randomFloat(final float min, final float max) {
        return min + ThreadLocalRandom.current().nextFloat() * (max - min);
    }

    private record BlockData(BlockPos pos, Direction facing) {
        private BlockPos placePos() {
            return this.pos.offset(this.facing);
        }
    }

    private record SlotData(int slot, Hand hand) {
    }
}

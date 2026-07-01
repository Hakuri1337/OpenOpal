package wtf.opal.client.feature.module.impl.world.scaffold.mode;

import net.minecraft.block.*;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import wtf.opal.client.feature.helper.impl.player.rotation.RotationHelper;
import wtf.opal.client.feature.helper.impl.player.rotation.model.impl.HeypixelRotationModel;
import wtf.opal.client.feature.helper.impl.player.rotation.model.impl.InstantRotationModel;
import wtf.opal.client.feature.helper.impl.player.slot.SlotHelper;
import wtf.opal.client.feature.module.impl.world.scaffold.ScaffoldModule;
import wtf.opal.client.feature.module.impl.world.scaffold.ScaffoldSettings;
import wtf.opal.client.feature.module.property.impl.mode.ModuleMode;
import wtf.opal.duck.ClientConnectionAccess;
import wtf.opal.event.impl.game.PreGameTickEvent;
import wtf.opal.event.impl.game.input.MoveInputEvent;
import wtf.opal.event.impl.game.player.movement.PreMovementPacketEvent;
import wtf.opal.event.subscriber.Subscribe;
import wtf.opal.utility.player.MoveUtility;
import wtf.opal.utility.player.RaycastUtility;
import wtf.opal.utility.player.RotationUtility;
import wtf.opal.utility.player.RaytracedRotation;
import wtf.opal.utility.player.SkipTickUtility;

import java.util.Arrays;
import java.util.List;

import static wtf.opal.client.Constants.mc;

public class HeypixelScaffold extends ModuleMode<ScaffoldModule> {

    private static final Direction[] DIRECTIONS = Direction.values();

    public HeypixelScaffold(ScaffoldModule module) {
        super(module);
    }

    private int airTick;
    private int yLevel;
    private BlockPos blockPos;
    private Direction enumFacing;
    private int oldSlot = -1;
    private float baseYaw;
    private float forwardYaw;
    private Vec2f lastTargetRotation;
    private Vec2f lastValidPlaceRotation;
    private BlockHitResult lastHitResult;
    private int rotateCount;
    private boolean checkedBlock;
    private int stuckTicks;
    private int skipRecoveryAttempts;
    private int skipRecoveryActiveTicks;
    private int skipRecoverySkipTicks;
    private int skipRecoveryNoPlaceTicks;
    private Vec2f packetRotation;
    private int packetRotationTicks;
    private int searchYTop;
    private int duplicateRotNonce;
    private int upTellyRotateTick;
    private int upTellyJumpTick;

    private static final int MAX_RESCUE_ATTEMPTS = 8;

    private static final List<Block> BLACKLISTED_BLOCKS = Arrays.asList(
            Blocks.AIR, Blocks.WATER, Blocks.LAVA, Blocks.ENCHANTING_TABLE, Blocks.GLASS_PANE,
            Blocks.IRON_BARS, Blocks.SNOW, Blocks.COAL_ORE, Blocks.DIAMOND_ORE, Blocks.EMERALD_ORE,
            Blocks.CHEST, Blocks.TRAPPED_CHEST, Blocks.TORCH, Blocks.ANVIL, Blocks.NOTE_BLOCK,
            Blocks.JUKEBOX, Blocks.TNT, Blocks.GOLD_ORE, Blocks.IRON_ORE, Blocks.LAPIS_ORE,
            Blocks.STONE_PRESSURE_PLATE, Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE, Blocks.HEAVY_WEIGHTED_PRESSURE_PLATE,
            Blocks.STONE_BUTTON, Blocks.LEVER, Blocks.TALL_GRASS, Blocks.TRIPWIRE, Blocks.TRIPWIRE_HOOK,
            Blocks.RAIL, Blocks.CORNFLOWER, Blocks.RED_MUSHROOM, Blocks.BROWN_MUSHROOM, Blocks.VINE,
            Blocks.SUNFLOWER, Blocks.LADDER, Blocks.FURNACE, Blocks.SAND, Blocks.CACTUS, Blocks.DISPENSER,
            Blocks.DROPPER, Blocks.CRAFTING_TABLE, Blocks.COBWEB, Blocks.PUMPKIN, Blocks.COBBLESTONE_WALL,
            Blocks.OAK_FENCE, Blocks.REDSTONE_TORCH, Blocks.FLOWER_POT
    );

    @Override
    public Enum<?> getEnumValue() {
        return ScaffoldSettings.Mode.HEYPIXEL;
    }

    @Override
    public boolean isHandlingEvents() {
        return module.isEnabled() && module.getEffectiveMode() == ScaffoldSettings.Mode.HEYPIXEL;
    }

    @Override
    public void onEnable() {
        if (mc.player != null) {
            oldSlot = mc.player.getInventory().getSelectedSlot();
            baseYaw = mc.player.getYaw();
            forwardYaw = resolveBaseYaw();
            stuckTicks = 0;
            skipRecoveryAttempts = 0;
            skipRecoveryActiveTicks = 0;
            skipRecoverySkipTicks = 0;
            skipRecoveryNoPlaceTicks = 0;
            lastValidPlaceRotation = null;
            packetRotation = null;
            packetRotationTicks = 0;
            duplicateRotNonce = 0;
            upTellyRotateTick = 0;
            upTellyJumpTick = 0;
            rotateCount = 0;
            checkedBlock = false;
            lastHitResult = null;
        }
    }

    @Override
    public void onDisable() {
        SlotHelper.getInstance().stop();
        if (mc.player != null && oldSlot != -1) {
            mc.player.getInventory().setSelectedSlot(oldSlot);
        }
        final var handler = RotationHelper.getHandler();
        if (mc.player != null) {
            final float clientYaw = RotationHelper.getClientHandler().getYawOr(mc.player.getYaw());
            final float clientPitch = RotationHelper.getClientHandler().getPitchOr(mc.player.getPitch());
            handler.rotate(new Vec2f(clientYaw, clientPitch), new HeypixelRotationModel(this.getRotateBackSpeed()));
            handler.reverse();
        } else {
            handler.reset();
        }
        rotateCount = 0;
        checkedBlock = false;
        lastHitResult = null;
        upTellyRotateTick = 0;
        upTellyJumpTick = 0;
        skipRecoveryActiveTicks = 0;
        skipRecoverySkipTicks = 0;
        skipRecoveryNoPlaceTicks = 0;
    }

    protected float resolveBaseYaw() {
        return RotationHelper.getClientHandler().getYawOr(mc.player.getYaw());
    }

    protected boolean shouldTrackMovementYawDuringTelly() {
        return false;
    }

    protected boolean isTellyEnabled() {
        return module.getSettings().isTelly();
    }

    protected boolean isSafeWalkEnabled() {
        return module.getSettings().isSafeWalk();
    }

    protected boolean isSnapEnabled() {
        return module.getSettings().isSnap();
    }

    protected boolean useInteractBeforePlace() {
        return module.getSettings().isInteractBeforePlace();
    }

    protected int getTellyTick() {
        return module.getSettings().getTellyTick();
    }

    protected float getRotateSpeed() {
        return module.getSettings().getRotateSpeed();
    }

    protected float getRotateBackSpeed() {
        return module.getSettings().getRotateBackSpeed();
    }

    @Subscribe
    public void onPreTick(PreGameTickEvent event) {
        if (mc.player == null || mc.world == null) return;

        final wtf.opal.client.feature.module.impl.movement.StuckModule stuckModule = wtf.opal.client.OpalClient.getInstance().getModuleRepository().getModule(wtf.opal.client.feature.module.impl.movement.StuckModule.class);
        final boolean skipTickRecovery = module.isSkipTickRecoveryActive();
        if (stuckModule.isEnabled() || skipTickRecovery) {
            stuckTicks++;
            boolean hasBlock = false;
            for (int i = 0; i < 9; i++) {
                if (isValidStack(mc.player.getInventory().getStack(i))) {
                    hasBlock = true;
                    break;
                }
            }

            if (!hasBlock || (stuckTicks > 10 && blockPos == null)) {
                if (stuckModule.isEnabled()) {
                    stuckModule.setEnabled(false);
                }
                module.setSkipTickRecoveryActive(false);
                stuckTicks = 0;
                skipRecoveryAttempts = 0;
                rotateCount = 0;
                skipRecoveryActiveTicks = 0;
                skipRecoverySkipTicks = 0;
                skipRecoveryNoPlaceTicks = 0;
            }
        } else {
            stuckTicks = 0;
            skipRecoveryAttempts = 0;
            rotateCount = 0;
            skipRecoveryActiveTicks = 0;
            skipRecoverySkipTicks = 0;
            skipRecoveryNoPlaceTicks = 0;
        }

        int slotID = -1;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (isValidStack(stack)) {
                slotID = i;
                break;
            }
        }
        if (slotID != -1) {
            final SlotHelper.Silence silence = switch (module.getSettings().getSwitchMode().getValue()) {
                case NORMAL -> SlotHelper.Silence.NONE;
                case FULL -> SlotHelper.Silence.FULL;
                case HOTBAR -> SlotHelper.Silence.DEFAULT;
            };
            SlotHelper.setCurrentItem(slotID).silence(silence);
        } else {
            SlotHelper.getInstance().stop();
        }

        if (mc.player.isOnGround()) yLevel = (int) Math.floor(mc.player.getY()) - 1;
        
        getBlockInfo();
        
        ScaffoldSettings settings = module.getSettings();

        if (handleSkipTickRecovery()) {
            return;
        }

        if (this.isSafeWalkEnabled() && !this.isTellyEnabled()) {
            boolean edge = mc.player.isOnGround() && isOnBlockEdge(0.3F);
            mc.options.sneakKey.setPressed(edge);
        }

        if (this.isTellyEnabled()) {
            if (mc.player.isOnGround()) {
                airTick = 0;
                blockPos = null;
                enumFacing = null;

                float clientYaw = resolveBaseYaw();
                float clientPitch = RotationHelper.getClientHandler().getPitchOr(mc.player.getPitch());
                
                forwardYaw = clientYaw;
                final Vec2f target = new Vec2f(clientYaw, clientPitch);
                this.lastTargetRotation = target;
                RotationHelper.getHandler().rotate(target, new HeypixelRotationModel(this.getRotateBackSpeed()));
            } else {
                if (shouldTrackMovementYawDuringTelly()) {
                    forwardYaw = resolveBaseYaw();
                }

                final int baseTellyTick = this.getTellyTick();
                int dynamicTellyTick = baseTellyTick;
                if (shouldAllowUpTelly()) {
                    dynamicTellyTick = Math.max(1, baseTellyTick - (isDiagonalYaw(forwardYaw) ? 3 : 2));
                }

                if (airTick < dynamicTellyTick) {
                    final Vec2f forward = new Vec2f(forwardYaw, mc.player.getPitch());
                    if (shouldAllowUpTelly()) {
                        upTellyRotateTick++;
                        if (upTellyRotateTick % 2 == 0) {
                            this.lastTargetRotation = forward;
                            RotationHelper.getHandler().rotate(forward, new HeypixelRotationModel(this.getRotateBackSpeed()));
                        }
                    } else {
                        upTellyRotateTick = 0;
                        this.lastTargetRotation = forward;
                        RotationHelper.getHandler().rotate(forward, new HeypixelRotationModel(this.getRotateBackSpeed()));
                    }

                    if (shouldAllowUpTelly() && isDiagonalYaw(forwardYaw) && airTick >= 1 && blockPos != null && enumFacing != null) {
                        Vec2f earlyRotation = getRotation(blockPos, enumFacing);
                        if (earlyRotation != null) {
                            this.lastTargetRotation = earlyRotation;
                            RotationHelper.getHandler().rotate(earlyRotation, InstantRotationModel.INSTANCE);
                            place();
                        }
                    }
                } else {
                    Vec2f rotation = getRotation(blockPos, enumFacing);
                    if (rotation != null) {
                        this.lastTargetRotation = rotation;
                        RotationHelper.getHandler().rotate(rotation, InstantRotationModel.INSTANCE);
                        place();
                    }
                }
                airTick++;
            }
        } else {
            if (blockPos == null) {
                final Vec2f target = new Vec2f(MathHelper.wrapDegrees(baseYaw - 180), 89.64F);
                this.lastTargetRotation = target;
                this.packetRotation = target;
                this.packetRotationTicks = 2;
                RotationHelper.getHandler().rotate(target, new HeypixelRotationModel(this.getRotateSpeed()));
            }
            if (onAir() || !this.isSnapEnabled()) {
                Vec2f rotation = getRotation(blockPos, enumFacing);
                if (rotation != null) {
                    this.lastTargetRotation = rotation;
                    this.packetRotation = rotation;
                    this.packetRotationTicks = 2;
                    RotationHelper.getHandler().rotate(rotation, new HeypixelRotationModel(this.getRotateSpeed()));
                }
            }
            place();
        }

    }

    private boolean handleSkipTickRecovery() {
        if (!module.isSkipTickRecoveryActive()) {
            return false;
        }

        skipRecoveryActiveTicks++;
        if (skipRecoveryActiveTicks > 26) {
            module.markSkipTickRecoveryFailed();
            skipRecoveryAttempts = 0;
            skipRecoverySkipTicks = 0;
            skipRecoveryNoPlaceTicks = 0;
            return true;
        }

        if (skipRecoverySkipTicks > 0 && skipRecoveryNoPlaceTicks > 6) {
            module.markSkipTickRecoveryFailed();
            skipRecoveryAttempts = 0;
            skipRecoverySkipTicks = 0;
            skipRecoveryNoPlaceTicks = 0;
            return true;
        }

        if (!onAir()) {
            module.setSkipTickRecoveryActive(false);
            skipRecoveryAttempts = 0;
            rotateCount = 0;
            skipRecoveryActiveTicks = 0;
            skipRecoverySkipTicks = 0;
            skipRecoveryNoPlaceTicks = 0;
            return true;
        }

        final BlockPos recoveryBlockPos = module.getSkipTickRecoveryBlockPos();
        final Direction recoveryFace = module.getSkipTickRecoveryFace();
        if (recoveryBlockPos != null && recoveryFace != null) {
            this.blockPos = recoveryBlockPos;
            this.enumFacing = recoveryFace;
        }

        final boolean hasTarget = blockPos != null && enumFacing != null;
        if (!hasTarget) {
            skipRecoveryAttempts++;
            skipRecoveryNoPlaceTicks++;
            if (skipRecoveryAttempts > 10) {
                module.markSkipTickRecoveryFailed();
                skipRecoveryAttempts = 0;
                skipRecoverySkipTicks = 0;
                skipRecoveryNoPlaceTicks = 0;
            }
            return true;
        }

        if (skipRecoveryAttempts > 12) {
            module.markSkipTickRecoveryFailed();
            skipRecoveryAttempts = 0;
            rotateCount = 0;
            skipRecoverySkipTicks = 0;
            skipRecoveryNoPlaceTicks = 0;
            return true;
        }

        final Vec2f rotation = getRotation(blockPos, enumFacing);
        if (rotation == null) {
            skipRecoveryAttempts++;
            skipRecoveryNoPlaceTicks++;
            return true;
        }

        applyPlacementRotation(rotation, this.getRotateSpeed());
        final boolean placed = place(false);
        if (placed) {
            skipRecoveryNoPlaceTicks = 0;
            skipRecoveryAttempts = 0;
            module.setSkipTickRecoveryActive(false);
            skipRecoveryActiveTicks = 0;
            skipRecoverySkipTicks = 0;
        } else {
            SkipTickUtility.addSkipTicks(1);
            skipRecoverySkipTicks++;
            skipRecoveryNoPlaceTicks++;
        }
        skipRecoveryAttempts++;

        return true;
    }

    private void applyPlacementRotation(Vec2f rotation, float rotateSpeed) {
        if (rotation == null) {
            return;
        }
        this.lastTargetRotation = rotation;
        this.packetRotation = rotation;
        this.packetRotationTicks = 2;
        RotationHelper.getHandler().rotate(rotation, new HeypixelRotationModel(rotateSpeed));
    }

    private void runSkidRescueRecursive(boolean tellyMode, float rotateSpeed, int depth) {
        if (depth >= MAX_RESCUE_ATTEMPTS || mc.player == null || mc.world == null) {
            return;
        }

        getBlockInfo();
        if (blockPos == null || enumFacing == null) {
            return;
        }

        Vec2f rotation = getRotation(blockPos, enumFacing);
        if (rotation == null) {
            return;
        }

        boolean reachable = computeReachable(tellyMode ? true : blockPos != null);
        if (checkedBlock && !reachable && rotateCount < MAX_RESCUE_ATTEMPTS) {
            rotateCount++;
            skipRecoveryAttempts++;
            SkipTickUtility.addSkipTicks(1);
            sendRescueRotationPacket(rotation);
            place(false);
            runSkidRescueRecursive(tellyMode, rotateSpeed, depth + 1);
        } else {
            applyPlacementRotation(rotation, rotateSpeed);
            place();
            rotateCount = Math.max(0, rotateCount - 1);
        }
    }

    private boolean computeReachable(boolean defaultReachable) {
        boolean reachable = defaultReachable;
        if (mc.player != null && blockPos != null && mc.player.getVelocity().y < -0.1D) {
            if (blockPos.getY() > predictYAfterTicks(2)) {
                reachable = false;
            }
        }
        return reachable;
    }

    private double predictYAfterTicks(int ticks) {
        double y = mc.player.getY();
        double motionY = mc.player.getVelocity().y;
        for (int i = 0; i < ticks; i++) {
            motionY = (motionY - 0.08D) * 0.98D;
            y += motionY;
        }
        return y;
    }

    private void sendRescueRotationPacket(Vec2f rotation) {
        if (rotation == null || mc.player == null) {
            return;
        }
        this.packetRotation = rotation;
        this.packetRotationTicks = 2;
        sendPacketSilent(new PlayerMoveC2SPacket.LookAndOnGround(rotation.x, rotation.y, mc.player.isOnGround(), false));
    }

    private void sendPacketSilent(Packet<?> packet) {
        if (mc.getNetworkHandler() != null && mc.getNetworkHandler().getConnection() instanceof ClientConnectionAccess access) {
            access.opal$sendPacketSilent(packet);
        }
    }

    @Subscribe
    public void onMoveInput(MoveInputEvent event) {
        if (!this.isTellyEnabled() || !MoveUtility.isMoving()) {
            upTellyJumpTick = 0;
            return;
        }

        if (!mc.player.isOnGround()) {
            return;
        }

        if (mc.options.jumpKey.isPressed()) {
            upTellyJumpTick++;
            event.setJump(upTellyJumpTick % 2 == 0);
            return;
        }

        upTellyJumpTick = 0;
        event.setJump(true);
    }

    private boolean place() {
        return place(true);
    }

    private boolean place(boolean checkRotation) {
        if (!onAir()) return false;
        if (blockPos == null || enumFacing == null) return false;

        if (!checkRotation) {
            return performPlace(new BlockHitResult(getVec3(blockPos, enumFacing), enumFacing, blockPos, false));
        }

        Vec2f currentRot = RotationHelper.getHandler().isActive() ?
            RotationHelper.getClientHandler().getRotation() :
            null;
        if (currentRot == null) {
            currentRot = this.lastValidPlaceRotation != null ? this.lastValidPlaceRotation : new Vec2f(mc.player.getYaw(), mc.player.getPitch());
        }

        final net.minecraft.util.hit.HitResult hitResult = RaycastUtility.raycastBlock(4.5, 1.0F, false, currentRot.x, currentRot.y);
        
        if (hitResult instanceof BlockHitResult blockHitResult && blockHitResult.getBlockPos().equals(blockPos) && blockHitResult.getSide() == enumFacing) {
            this.lastValidPlaceRotation = currentRot;
            return performPlace(blockHitResult);
        } else {
            if (this.isTellyEnabled() || module.getSettings().isOverrideRaycast()) {
                if (lastHitResult != null && lastHitResult.getBlockPos().equals(blockPos) && lastHitResult.getSide() == enumFacing) {
                    this.lastValidPlaceRotation = this.lastTargetRotation;
                    return performPlace(lastHitResult);
                }
            }
        }

        return false;
    }

    private boolean performPlace(BlockHitResult hitResult) {
        ItemStack stack = mc.player.getMainHandStack();
        if (!(stack.getItem() instanceof BlockItem)) return false;

        if (this.useInteractBeforePlace()) {
            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
        }

        mc.player.swingHand(Hand.MAIN_HAND);
        final boolean success = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hitResult).isAccepted();
        if (!success) {
            return false;
        }
        stuckTicks = 0;
        skipRecoveryAttempts = 0;
        return true;
    }

    private boolean overBlock(BlockPos pos, Direction facing) {
        final net.minecraft.util.hit.HitResult hitResult = RaycastUtility.raycastBlock(4.5, 1.0F, false, mc.player.getYaw(), mc.player.getPitch());
        if (hitResult instanceof BlockHitResult blockHitResult) {
            return blockHitResult.getBlockPos().equals(pos) && blockHitResult.getSide() == facing;
        }
        return false;
    }

    private int getYLevel() {
        if (!mc.options.jumpKey.isPressed()
                && MoveUtility.isMoving()
                && mc.player.fallDistance <= 0.25
                && this.isTellyEnabled()
                && mc.player.getVelocity().y <= 0.02) {
            return yLevel;
        } else {
            return (int) Math.floor(mc.player.getY()) - 1;
        }
    }

    private boolean shouldAllowUpTelly() {
        if (mc.player.horizontalCollision) {
            return false;
        }

        return this.isTellyEnabled()
                && MoveUtility.isMoving()
                && !mc.player.isOnGround()
                && (mc.options.jumpKey.isPressed() || mc.player.getVelocity().y > -0.08);
    }

    public boolean shouldSuppressSkipTickRecoveryTrigger() {
        if (mc.player == null || mc.world == null) {
            return false;
        }

        if (!this.isTellyEnabled() || mc.player.isOnGround() || !MoveUtility.isMoving()) {
            return false;
        }

        final int baseTellyTick = this.getTellyTick();
        int dynamicTellyTick = baseTellyTick;
        if (shouldAllowUpTelly()) {
            dynamicTellyTick = Math.max(1, baseTellyTick - (isDiagonalYaw(forwardYaw) ? 3 : 2));
        }

        if (airTick > dynamicTellyTick + 1) {
            return false;
        }

        return mc.player.getVelocity().y > -0.16D && mc.player.fallDistance < 1.6F;
    }

    private boolean isDiagonalYaw(final float yaw) {
        final double radians = Math.toRadians(MathHelper.wrapDegrees(yaw));
        final double diagonalStrength = Math.abs(Math.sin(radians * 2.0));
        return diagonalStrength > 0.58;
    }

    private void getBlockInfo() {
        Vec3d baseVec = mc.player.getEyePos();
        int baseY = getYLevel();
        if (shouldAllowUpTelly()) {
            baseY += 1;
        }
        this.searchYTop = baseY;

        BlockPos base = BlockPos.ofFloored(baseVec.x, baseY, baseVec.z);
        int baseX = base.getX();
        int baseZ = base.getZ();
        
        if (isSolidAndNonInteractive(mc.world.getBlockState(base), base)) {
            checkedBlock = false;
            return;
        }
        
        if (checkBlock(baseVec, base)) {
            return;
        }
        
        for (int d = 1; d <= 6; d++) {
            if (checkBlock(baseVec, new BlockPos(baseX, baseY - d, baseZ))) {
                return;
            }
            for (int x = 0; x <= d; x++) {
                for (int z = 0; z <= d - x; z++) {
                    int y = d - x - z;
                    for (int rev1 = 0; rev1 <= 1; rev1++) {
                        for (int rev2 = 0; rev2 <= 1; rev2++) {
                            if (checkBlock(baseVec, new BlockPos(baseX + (rev1 == 0 ? x : -x), baseY - y, baseZ + (rev2 == 0 ? z : -z))))
                                return;
                        }
                    }
                }
            }
        }

        checkedBlock = false;
    }

    private boolean isSolidAndNonInteractive(BlockState state, BlockPos pos) {
        boolean hasCollision = !state.getCollisionShape(mc.world, pos).isEmpty();
        boolean hasNoMenu = state.createScreenHandlerFactory(mc.world, pos) == null;
        return hasCollision && hasNoMenu;
    }

    private boolean checkBlock(Vec3d baseVec, BlockPos pos) {
        if (!(mc.world.getBlockState(pos).getBlock() instanceof AirBlock) && !(mc.world.getBlockState(pos).getBlock() instanceof LilyPadBlock)) {
            checkedBlock = false;
            return false;
        }
        
        if (pos.getY() > this.searchYTop) {
            checkedBlock = false;
            return false;
        }

        Vec3d center = new Vec3d(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        for (Direction dir : DIRECTIONS) {
            Vec3d hit = center.add(new Vec3d(dir.getOffsetX(), dir.getOffsetY(), dir.getOffsetZ()).multiply(0.5));
            BlockPos baseBlockPos = pos.offset(dir);

            if (!isSolidAndNonInteractive(mc.world.getBlockState(baseBlockPos), baseBlockPos)) continue;

            Vec3d relevant = hit.subtract(baseVec);
            if (relevant.lengthSquared() <= 4.5 * 4.5 && relevant.dotProduct(new Vec3d(dir.getOffsetX(), dir.getOffsetY(), dir.getOffsetZ())) >= 0) {
                if (dir.getOpposite() == Direction.UP && MoveUtility.isMoving() && !mc.options.jumpKey.isPressed())
                    continue;
                blockPos = baseBlockPos;
                enumFacing = dir.getOpposite();
                checkedBlock = true;
                return true;
            }
        }

        checkedBlock = false;
        return false;
    }

    private boolean isValidStack(ItemStack stack) {
        if (stack == null || !(stack.getItem() instanceof BlockItem) || stack.getCount() <= 0) {
            return false;
        }
        Block block = ((BlockItem) stack.getItem()).getBlock();
        if (block instanceof FlowerBlock || block instanceof MushroomPlantBlock || block instanceof CropBlock || block instanceof SlabBlock) {
            return false;
        }
        return !BLACKLISTED_BLOCKS.contains(block);
    }

    private Vec3d getVec3(BlockPos pos, Direction face) {
        double x = (double) pos.getX() + 0.5;
        double y = (double) pos.getY() + 0.5;
        double z = (double) pos.getZ() + 0.5;
        if (face != Direction.UP && face != Direction.DOWN) {
            y += 0.08;
        } else {
            x += (Math.random() - 0.5) * 0.4;
            z += (Math.random() - 0.5) * 0.4;
        }

        if (face == Direction.WEST || face == Direction.EAST) {
            z += (Math.random() - 0.5) * 0.4;
        }

        if (face == Direction.SOUTH || face == Direction.NORTH) {
            x += (Math.random() - 0.5) * 0.4;
        }

        return new Vec3d(x, y, z);
    }
    
    private Vec2f getRotation(BlockPos pos, Direction face) {
        if (pos == null || face == null) return null;
        lastHitResult = null;
        final Vec2f directRot = RotationUtility.getVanillaRotation(RotationUtility.getRotationFromBlock(pos, face));
        final Vec2f reverseYawRot = new Vec2f(MathHelper.wrapDegrees(mc.player.getYaw() - 180F), directRot.y);

        if (onAir()) {
            BlockHitResult reverse = raycastBlockWithRotation(reverseYawRot, pos, face);
            if (reverse != null) {
                lastHitResult = reverse;
                return withDuplicateRotJitter(reverseYawRot, pos, face);
            }
        }

        BlockHitResult direct = raycastBlockWithRotation(directRot, pos, face);
        if (direct != null) {
            lastHitResult = direct;
            return withDuplicateRotJitter(directRot, pos, face);
        }

        for (float yawOffset : new float[]{-2F, 2F, -4F, 4F, -7F, 7F, -12F, 12F}) {
            for (float pitchOffset : new float[]{0F, -1F, 1F, -2F, 2F, -4F, 4F}) {
                final Vec2f trial = new Vec2f(
                        MathHelper.wrapDegrees(directRot.x + yawOffset),
                        MathHelper.clamp(directRot.y + pitchOffset, -90F, 90F)
                );
                BlockHitResult result = raycastBlockWithRotation(trial, pos, face);
                if (result != null) {
                    lastHitResult = result;
                    return withDuplicateRotJitter(trial, pos, face);
                }
            }
        }

        RaytracedRotation raytraced = RotationUtility.getRotationFromRaycastedBlock(pos, face, directRot, mc.player.getEyePos());
        if (raytraced != null) {
            if (raytraced.hitResult() instanceof BlockHitResult bhr) {
                lastHitResult = bhr;
            }
            return withDuplicateRotJitter(raytraced.rotation(), pos, face);
        }

        return withDuplicateRotJitter(directRot, pos, face);
    }

    private Vec2f withDuplicateRotJitter(final Vec2f base, final BlockPos pos, final Direction face) {
        if (!module.getSettings().isDuplicateRotPlace() || base == null) {
            return base;
        }

        duplicateRotNonce++;
        final int idx = duplicateRotNonce % 6;
        final float yawJitter = switch (idx) {
            case 0 -> 0.032F;
            case 1 -> -0.037F;
            case 2 -> 0.021F;
            case 3 -> -0.026F;
            case 4 -> 0.014F;
            default -> -0.018F;
        };
        final float pitchJitter = (idx % 2 == 0) ? 0.012F : -0.009F;

        final Vec2f jittered = new Vec2f(
                MathHelper.wrapDegrees(base.x + yawJitter),
                MathHelper.clamp(base.y + pitchJitter, -89.9F, 89.9F)
        );

        final BlockHitResult jitterHit = raycastBlockWithRotation(jittered, pos, face);
        return jitterHit != null ? jittered : base;
    }

    @Subscribe(priority = 3)
    public void onPreMovementPacket(final PreMovementPacketEvent event) {
        if (mc.player == null || this.packetRotation == null || this.packetRotationTicks <= 0) {
            return;
        }

        event.setYaw(this.packetRotation.x);
        event.setPitch(this.packetRotation.y);
        this.packetRotationTicks--;
    }

    private BlockHitResult raycastBlockWithRotation(Vec2f rotation, BlockPos pos, Direction face) {
        final net.minecraft.util.hit.HitResult hitResult = RaycastUtility.raycastBlock(4.5, 1.0F, false, rotation.x, rotation.y);
        BlockHitResult fallback = null;
        if (hitResult instanceof BlockHitResult blockHitResult) {
            if (blockHitResult.getBlockPos().equals(pos)) {
                if (blockHitResult.getSide() == face) {
                    return blockHitResult;
                }
                fallback = blockHitResult;
            }
        }
        return fallback;
    }

    private boolean canOverBlockWithRotation(Vec2f rotation, BlockPos pos, Direction face) {
        return raycastBlockWithRotation(rotation, pos, face) != null;
    }
    
    private boolean onAir() {
        Vec3d baseVec = mc.player.getEyePos();
        BlockPos base = BlockPos.ofFloored(baseVec.x, getYLevel(), baseVec.z);
        return mc.world.getBlockState(base).getBlock() instanceof AirBlock || mc.world.getBlockState(base).getBlock() instanceof LilyPadBlock;
    }
    
    private boolean isOnBlockEdge(float sensitivity) {
         Box box = mc.player.getBoundingBox().offset(0.0, -0.5, 0.0).expand(-sensitivity, 0.0, -sensitivity);
         return mc.world.getBlockCollisions(mc.player, box).iterator().hasNext() == false;
    }
}

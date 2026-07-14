package wtf.opal.utility.player;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttributeModifierSlot;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectUtil;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.util.function.BooleanBiFunction;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import org.lwjgl.glfw.GLFW;
import wtf.opal.client.feature.helper.impl.LocalDataWatch;
import wtf.opal.client.feature.helper.impl.server.impl.HypixelServer;
import wtf.opal.utility.render.OrderedTextVisitor;

import static wtf.opal.client.Constants.mc;

public final class PlayerUtility {

    private PlayerUtility() {
    }

    public static boolean isCriticalHitAvailable() {
        return !mc.player.isTouchingWater() && !mc.player.isClimbing() &&
                !mc.player.hasStatusEffect(StatusEffects.BLINDNESS) && !mc.player.hasVehicle();
    }

    public static boolean isNoAirBelow() {
        return isNoAirBelow(0.0D, 0.0D);
    }

    public static boolean isNoAirBelow(double offsetX, double offsetZ) {
        final Box box = mc.player.getBoundingBox().offset(offsetX, -(mc.player.getBoundingBox().getLengthY() + 1.0D), offsetZ);
        return BlockPos.stream(box).noneMatch(pos -> {
            final BlockState blockState = mc.world.getBlockState(pos);
            final VoxelShape voxelShape = blockState.getCollisionShape(mc.world, pos).offset(pos.getX(), pos.getY(), pos.getZ());
            return pos.getY() == Math.floor(mc.player.getY() - 1.0D) &&
                    (voxelShape.isEmpty() || voxelShape.getMax(Direction.Axis.Y) != mc.player.getY() - (mc.player.getY() % 0.0625D));
        });
    }

    public static String getFormattedEntityName(final LivingEntity entity) {
        if (entity.getDisplayName() != null) {
            final OrderedTextVisitor visitor = new OrderedTextVisitor();
            entity.getDisplayName().asOrderedText().accept(visitor);
            return visitor.getFormattedString();
        }
        return entity.getName().getString();
    }

    public static boolean isServerBrand(String brandString) {
        if (mc.player == null || mc.isInSingleplayer()) return false;

        final ServerInfo serverInfo = mc.getCurrentServerEntry();
        if (serverInfo == null) return false;

        final String brand = mc.player.networkHandler.getBrand();
        return brand != null && brand.toLowerCase().contains(brandString.toLowerCase());
    }

    public static int getHandSwingDuration() {
        if (StatusEffectUtil.hasHaste(mc.player)) {
            return 6 - (1 + StatusEffectUtil.getHasteAmplifier(mc.player));
        }
        if (mc.player.hasStatusEffect(StatusEffects.MINING_FATIGUE)) {
            return 6 + (1 + mc.player.getStatusEffect(StatusEffects.MINING_FATIGUE).getAmplifier()) * 2;
        }
        return 6;
    }

    public static Vec3d getClosestVectorToBox(Vec3d from, Box box) {
        final double closestX = Math.max(box.minX, Math.min(from.getX(), box.maxX));
        final double closestY = Math.max(box.minY, Math.min(from.getY(), box.maxY));
        final double closestZ = Math.max(box.minZ, Math.min(from.getZ(), box.maxZ));
        return new Vec3d(closestX, closestY, closestZ);
    }

    public static Vec3d getClosestVectorToBoundingBox(Vec3d from, LivingEntity entity) {
        return getClosestVectorToBox(from, entity.getBoundingBox().expand(entity.getTargetingMargin()));
    }

    public static double getDistanceToEntity(LivingEntity entity) {
        final Vec3d eyePos = mc.player.getEyePos();

        return eyePos.distanceTo(getClosestVectorToBox(eyePos, entity.getBoundingBox().expand(entity.getTargetingMargin())));
    }

    public static Box getBlockBox(final BlockPos blockPos) {
        final VoxelShape shape = mc.world.getBlockState(blockPos).getOutlineShape(mc.world, blockPos, ShapeContext.of(mc.player));

        if (shape.isEmpty()) {
            return new Box(blockPos);
        }

        final Box bb = shape.getBoundingBox();

        return new Box(
                blockPos.getX() + bb.minX,
                blockPos.getY() + bb.minY,
                blockPos.getZ() + bb.minZ,
                blockPos.getX() + bb.maxX,
                blockPos.getY() + bb.maxY,
                blockPos.getZ() + bb.maxZ
        );
    }

    public static double getDistanceToBlock(final BlockPos blockPos) {
        final Vec3d eyePos = mc.player.getEyePos();

        final Box blockBox = getBlockBox(blockPos);
        final Vec3d closestVector = getClosestVectorToBox(eyePos, blockBox);

        return eyePos.distanceTo(closestVector);
    }

    public static boolean isAirUntil(double posY, Box playerBox) {
        Box box = new Box(playerBox.minX, posY, playerBox.minZ, playerBox.maxX, playerBox.maxY, playerBox.maxZ);
        return isBoxEmpty(box);
    }

    public static boolean isBoxEmpty(Box box) {
        return BlockPos.stream(box).noneMatch(pos -> {
            final BlockState blockState = mc.world.getBlockState(pos);
            final VoxelShape voxelShape = blockState.getCollisionShape(mc.world, pos);
            return !voxelShape.isEmpty() && VoxelShapes.matchesAnywhere(
                    blockState.getCollisionShape(mc.world, pos).offset(pos.getX(), pos.getY(), pos.getZ()),
                    VoxelShapes.cuboid(box),
                    BooleanBiFunction.AND
            );
        });
    }

    public static boolean isOverVoid(Box playerBox) {
        return isAirUntil(0, playerBox.expand(-0.005, 0, -0.005));
    }

    public static boolean isOverVoid() {
        return isOverVoid(mc.player.getBoundingBox());
    }

    public static Block getBlockOver() {
        if (mc.crosshairTarget != null && mc.crosshairTarget.getType() == HitResult.Type.BLOCK) {
            final BlockHitResult blockHitResult = (BlockHitResult) mc.crosshairTarget;
            final BlockPos blockPos = blockHitResult.getBlockPos();

            return mc.world.getBlockState(blockPos).getBlock();
        }
        return null;
    }

    public static BlockPos getBlockPosOver() {
        if (mc.crosshairTarget != null && mc.crosshairTarget.getType() == HitResult.Type.BLOCK) {
            final BlockHitResult blockHitResult = (BlockHitResult) mc.crosshairTarget;

            return blockHitResult.getBlockPos();
        }
        return null;
    }

    public static float getMaxFallDistance() {
        float distance = 3;

        if (mc.player.hasStatusEffect(StatusEffects.JUMP_BOOST)) {
            distance += (float) (mc.player.getStatusEffect(StatusEffects.JUMP_BOOST).getAmplifier() + 1);
        }

        return distance;
    }

    public static boolean isKeyPressed(final int keyCode) {
        return InputUtil.isKeyPressed(mc.getWindow(), keyCode);
    }

    public static boolean isKeyPressed(final KeyBinding keyBinding) {
        return isKeyPressed(keyBinding.getDefaultKey().getCode());
    }

    public static boolean isMouseButtonPressed(final int button) {
        return GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), button) == GLFW.GLFW_PRESS;
    }

    public static boolean isCollisionImminent(double offsetX, double offsetY, double offsetZ) {
        return !isBoxEmpty(mc.player.getBoundingBox().offset(offsetX, offsetY, offsetZ));
    }

    public static boolean isInsideBlock() {
        return !isBoxEmpty(mc.player.getBoundingBox());
    }

    public static boolean areOnSameTeam(final LivingEntity entity, final LivingEntity entity1) {
        if (entity.getDisplayName() == null || entity1.getDisplayName() == null) {
            return false;
        }

        final int entityColor = entity.getTeamColorValue();
        final int entity1Color = entity1.getTeamColorValue();

        return entityColor == entity1Color;
    }

    public static double getStackAttackSpeed(ItemStack stack) {
        final double base = 4.D;
        double attackSpeed = base;
        final AttributeModifiersComponent attributeModifiersComponent = stack.getOrDefault(DataComponentTypes.ATTRIBUTE_MODIFIERS, AttributeModifiersComponent.DEFAULT);
        for (AttributeModifiersComponent.Entry entry : attributeModifiersComponent.modifiers()) {
            if (entry.attribute() != EntityAttributes.ATTACK_SPEED || entry.slot() != AttributeModifierSlot.MAINHAND) {
                continue;
            }
            EntityAttributeModifier modifier = entry.modifier();
            attackSpeed += switch (modifier.operation()) {
                case ADD_VALUE -> modifier.value();
                case ADD_MULTIPLIED_BASE -> modifier.value() * base;
                case ADD_MULTIPLIED_TOTAL -> modifier.value() * attackSpeed;
            };
        }
        return attackSpeed;
    }

    public static double getStackAttackDamage(ItemStack stack) {
        double attackDamage = 0;
        final AttributeModifiersComponent attributeModifiersComponent = stack.getOrDefault(DataComponentTypes.ATTRIBUTE_MODIFIERS, AttributeModifiersComponent.DEFAULT);
        for (AttributeModifiersComponent.Entry entry : attributeModifiersComponent.modifiers()) {
            if (entry.attribute() != EntityAttributes.ATTACK_DAMAGE || entry.slot() != AttributeModifierSlot.MAINHAND) {
                continue;
            }
            EntityAttributeModifier modifier = entry.modifier();
            attackDamage += modifier.value();
        }
        return attackDamage;
    }

    public static double getArmorProtection(ItemStack stack) {
        double protection = 0;
        final AttributeModifiersComponent attributeModifiersComponent = stack.getOrDefault(DataComponentTypes.ATTRIBUTE_MODIFIERS, AttributeModifiersComponent.DEFAULT);
        for (AttributeModifiersComponent.Entry entry : attributeModifiersComponent.modifiers()) {
            if (!entry.attribute().equals(EntityAttributes.ARMOR)) {
                continue;
            }
            EntityAttributeModifier modifier = entry.modifier();
            protection += modifier.value();
        }
        return protection;
    }
}

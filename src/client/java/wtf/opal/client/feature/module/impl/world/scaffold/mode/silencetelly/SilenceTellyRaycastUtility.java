package wtf.opal.client.feature.module.impl.world.scaffold.mode.silencetelly;

import net.minecraft.block.AirBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.FluidBlock;
import net.minecraft.block.PlantBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.ShortPlantBlock;
import net.minecraft.block.SnowBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;

import java.util.Optional;
import java.util.function.Predicate;

final class SilenceTellyRaycastUtility {
    private static final double EPSILON = 1.0E-7D;

    private SilenceTellyRaycastUtility() {
    }

    static boolean didHitBlockFace(final Vec3d eyePos, final Vec2f rotation, final BlockPos targetPos, final Direction expectedFace, final boolean strict) {
        if (eyePos == null || rotation == null || targetPos == null || expectedFace == null) {
            return false;
        }

        final BlockHitResult result = getFacedBlock(eyePos, rotation, SilenceTellyRaycastUtility::isIgnoredBlock);
        return result != null
                && targetPos.equals(result.getBlockPos())
                && (!strict || expectedFace == result.getSide());
    }

    static BlockHitResult getFacedBlock(final Vec3d eyePos, final Vec2f rotation, final Predicate<BlockState> ignorePredicate) {
        final MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null || eyePos == null || rotation == null) {
            return null;
        }

        final double reachDistance = client.player.getBlockInteractionRange();
        Vec3d direction = Vec3d.fromPolar(rotation.y, rotation.x);
        final Vec3d endPos = eyePos.add(direction.multiply(reachDistance));
        if (direction.x == 0.0D) {
            direction = new Vec3d(EPSILON, direction.y, direction.z);
        }
        if (direction.y == 0.0D) {
            direction = new Vec3d(direction.x, EPSILON, direction.z);
        }
        if (direction.z == 0.0D) {
            direction = new Vec3d(direction.x, direction.y, EPSILON);
        }

        BlockPos currentPos = BlockPos.ofFloored(eyePos);
        final int stepX = (int) Math.signum(direction.x);
        final int stepY = (int) Math.signum(direction.y);
        final int stepZ = (int) Math.signum(direction.z);

        final double nextBoundaryX = stepX > 0 ? currentPos.getX() + 1.0D : currentPos.getX();
        final double nextBoundaryY = stepY > 0 ? currentPos.getY() + 1.0D : currentPos.getY();
        final double nextBoundaryZ = stepZ > 0 ? currentPos.getZ() + 1.0D : currentPos.getZ();

        double tMaxX = (nextBoundaryX - eyePos.x) / direction.x;
        double tMaxY = (nextBoundaryY - eyePos.y) / direction.y;
        double tMaxZ = (nextBoundaryZ - eyePos.z) / direction.z;

        final double tDeltaX = stepX / direction.x;
        final double tDeltaY = stepY / direction.y;
        final double tDeltaZ = stepZ / direction.z;
        final int maxSteps = Math.max(16, (int) Math.ceil(reachDistance * 6.0D));

        for (int steps = 0; steps < maxSteps && eyePos.distanceTo(currentPos.toCenterPos()) <= reachDistance + 1.0D; steps++) {
            final BlockState state = client.world.getBlockState(currentPos);
            if (!ignorePredicate.test(state)) {
                final VoxelShape shape = getShape(client, state, currentPos);
                if (!shape.isEmpty()) {
                    for (final Box localBox : shape.getBoundingBoxes()) {
                        final Box box = localBox.offset(currentPos);
                        final Optional<Vec3d> intercept = box.raycast(eyePos, endPos);
                        if (intercept.isPresent()) {
                            final Vec3d hitVec = intercept.get();
                            final Direction side = getHitFaceFromBox(hitVec, box);
                            return new BlockHitResult(hitVec, side, currentPos, box.contains(eyePos));
                        }
                    }
                }
            }

            if (tMaxX < tMaxY) {
                if (tMaxX < tMaxZ) {
                    currentPos = currentPos.add(stepX, 0, 0);
                    tMaxX += tDeltaX;
                } else {
                    currentPos = currentPos.add(0, 0, stepZ);
                    tMaxZ += tDeltaZ;
                }
            } else if (tMaxY < tMaxZ) {
                currentPos = currentPos.add(0, stepY, 0);
                tMaxY += tDeltaY;
            } else {
                currentPos = currentPos.add(0, 0, stepZ);
                tMaxZ += tDeltaZ;
            }
        }

        return null;
    }

    static boolean isIgnoredBlock(final BlockState state) {
        final Block block = state.getBlock();
        return state.isAir()
                || block instanceof PlantBlock
                || block instanceof SnowBlock
                || block instanceof AirBlock
                || block instanceof ShortPlantBlock
                || block instanceof FluidBlock;
    }

    private static VoxelShape getShape(final MinecraftClient client, final BlockState state, final BlockPos pos) {
        final FluidState fluidState = client.world.getFluidState(pos);
        if (fluidState != null && fluidState.isStill() && fluidState.getFluid() == Fluids.WATER) {
            return VoxelShapes.fullCube();
        }
        return state.getCollisionShape(client.world, pos, ShapeContext.of(client.player));
    }

    private static Direction getHitFaceFromBox(final Vec3d hit, final Box box) {
        if (Math.abs(hit.x - box.minX) <= EPSILON) return Direction.WEST;
        if (Math.abs(hit.x - box.maxX) <= EPSILON) return Direction.EAST;
        if (Math.abs(hit.y - box.minY) <= EPSILON) return Direction.DOWN;
        if (Math.abs(hit.y - box.maxY) <= EPSILON) return Direction.UP;
        if (Math.abs(hit.z - box.minZ) <= EPSILON) return Direction.NORTH;
        if (Math.abs(hit.z - box.maxZ) <= EPSILON) return Direction.SOUTH;

        double min = Math.abs(hit.x - box.minX);
        Direction side = Direction.WEST;
        final double east = Math.abs(hit.x - box.maxX);
        if (east < min) {
            min = east;
            side = Direction.EAST;
        }
        final double down = Math.abs(hit.y - box.minY);
        if (down < min) {
            min = down;
            side = Direction.DOWN;
        }
        final double up = Math.abs(hit.y - box.maxY);
        if (up < min) {
            min = up;
            side = Direction.UP;
        }
        final double north = Math.abs(hit.z - box.minZ);
        if (north < min) {
            min = north;
            side = Direction.NORTH;
        }
        final double south = Math.abs(hit.z - box.maxZ);
        if (south < min) {
            side = Direction.SOUTH;
        }
        return side;
    }
}

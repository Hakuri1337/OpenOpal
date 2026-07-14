package wtf.opal.client.feature.module.impl.movement;

import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import wtf.opal.client.feature.module.Module;
import wtf.opal.client.feature.module.ModuleCategory;
import wtf.opal.event.impl.game.PreGameTickEvent;
import wtf.opal.event.impl.game.player.movement.PreMoveEvent;
import wtf.opal.event.impl.game.player.movement.StuckInBlockEvent;
import wtf.opal.event.subscriber.Subscribe;

import static wtf.opal.client.Constants.mc;

public final class FastWebModule extends Module {

    private int lastWebTick;
    private int webCount;
    private int lastAcceleratedWebTick;
    private boolean exitRecoveryPending;

    public FastWebModule() {
        super("FastWeb", "Reduces cobweb slowdown using the OpenZen FastWeb flow.", ModuleCategory.MOVEMENT);
    }

    @Override
    protected void onEnable() {
        this.resetWebState();
        super.onEnable();
    }

    @Override
    protected void onDisable() {
        this.resetWebState();
        super.onDisable();
    }

    @Subscribe
    public void onPreGameTick(final PreGameTickEvent event) {
        if (mc.player == null) {
            this.resetWebState();
            return;
        }

    }

    @Subscribe
    public void onPreMove(final PreMoveEvent event) {
        if (mc.player == null) {
            return;
        }
        if (this.lastWebTick < mc.player.age && !this.isTouchingCobweb()) {
            if (this.lastAcceleratedWebTick >= 0 && this.lastAcceleratedWebTick == mc.player.age - 1) {
                this.exitRecoveryPending = true;
            }
            this.webCount = 0;
        }
        if (this.exitRecoveryPending) {
            final Vec3d velocity = mc.player.getVelocity();
            final double transition = 0.25D / 0.88D;
            mc.player.setVelocity(velocity.x * transition, velocity.y, velocity.z * transition);
            mc.player.setSprinting(false);
            this.exitRecoveryPending = false;
            this.lastAcceleratedWebTick = -1;
        } else if (this.webCount > 1) {
            mc.player.setSprinting(false);
        }
    }

    @Subscribe
    public void onStuckInBlock(final StuckInBlockEvent event) {
        if (mc.player == null || event.getBlockState().getBlock() != Blocks.COBWEB) {
            return;
        }

        this.lastWebTick = mc.player.age;
        this.webCount++;

        if (this.shouldUseVanillaWebMotion()) {
            return;
        }

        if (this.webCount > 5) {
            event.setMotion(new Vec3d(0.88D, event.getMotion().y, 0.88D));
            this.lastAcceleratedWebTick = mc.player.age;
        }
    }

    private boolean shouldUseVanillaWebMotion() {
        return mc.options.jumpKey.isPressed()
                || mc.options.sneakKey.isPressed()
                || mc.player.input.playerInput.jump()
                || mc.player.input.playerInput.sneak();
    }

    private boolean isTouchingCobweb() {
        if (mc.world == null) return false;
        final Box box = mc.player.getBoundingBox().contract(1.0E-4D);
        for (int x = MathHelper.floor(box.minX); x <= MathHelper.floor(box.maxX); x++) {
            for (int y = MathHelper.floor(box.minY); y <= MathHelper.floor(box.maxY); y++) {
                for (int z = MathHelper.floor(box.minZ); z <= MathHelper.floor(box.maxZ); z++) {
                    if (mc.world.getBlockState(new BlockPos(x, y, z)).isOf(Blocks.COBWEB)) return true;
                }
            }
        }
        return false;
    }

    private void resetWebState() {
        this.lastWebTick = 0;
        this.webCount = 0;
        this.lastAcceleratedWebTick = -1;
        this.exitRecoveryPending = false;
    }
}

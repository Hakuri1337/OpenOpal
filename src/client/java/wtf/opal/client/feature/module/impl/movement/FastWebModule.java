package wtf.opal.client.feature.module.impl.movement;

import net.minecraft.block.Blocks;
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

        if (this.lastWebTick < mc.player.age) {
            this.webCount = 0;
        }
    }

    @Subscribe
    public void onPreMove(final PreMoveEvent event) {
        if (mc.player != null && this.webCount > 1) {
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

        if (this.webCount > 5) {
            event.setMotion(new Vec3d(0.88D, 1.88D, 0.88D));
        }
    }

    private void resetWebState() {
        this.lastWebTick = 0;
        this.webCount = 0;
    }
}

package wtf.opal.client.feature.module.impl.utility;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.math.Vec3d;
import wtf.opal.client.OpalClient;
import wtf.opal.client.feature.helper.impl.LocalDataWatch;
import wtf.opal.client.feature.helper.impl.player.packet.blockage.block.holder.BlockHolder;
import wtf.opal.client.feature.helper.impl.player.packet.blockage.impl.OutboundNetworkBlockage;
import wtf.opal.client.feature.module.Module;
import wtf.opal.client.feature.module.ModuleCategory;
import wtf.opal.client.feature.module.impl.movement.flight.FlightModule;
import wtf.opal.client.feature.module.impl.movement.longjump.LongJumpModule;
import wtf.opal.client.feature.module.impl.utility.nofall.NoFallModule;
import wtf.opal.client.feature.module.impl.world.scaffold.ScaffoldModule;
import wtf.opal.client.feature.module.repository.ModuleRepository;
import wtf.opal.event.impl.game.packet.ReceivePacketEvent;
import wtf.opal.event.impl.game.player.movement.PreMovementPacketEvent;
import wtf.opal.event.subscriber.Subscribe;
import wtf.opal.mixin.ClientPlayerEntityAccessor;
import wtf.opal.utility.player.PlayerUtility;

import static wtf.opal.client.Constants.mc;

public final class
AntiVoidModule extends Module {

    private final BlockHolder blockHolder = new BlockHolder(OutboundNetworkBlockage.get());
    private GroundStates overGroundStates;

    private boolean blinked, failed;
    private double startingY;

    public AntiVoidModule() {
        super("Anti Void", "Makes it impossible to fall into the void.", ModuleCategory.UTILITY);
    }

    @Subscribe
    public void onPreMovementPacket(final PreMovementPacketEvent event) {
        if (mc.player == null) return;

        final ModuleRepository moduleRepository = OpalClient.getInstance().getModuleRepository();

        final LongJumpModule longJumpModule = moduleRepository.getModule(LongJumpModule.class);
        final boolean shouldRun = !longJumpModule.isEnabled()
                && !moduleRepository.getModule(FlightModule.class).isEnabled()
                && !moduleRepository.getModule(ScaffoldModule.class).isEnabled()
                && !mc.player.getAbilities().allowFlying
                && !mc.player.getAbilities().flying;

        if (!shouldRun) {
            this.blockHolder.release();
            overGroundStates = null;
            failed = true;
            return;
        }

        if (PlayerUtility.isOverVoid()) {
            if (!failed) {
                if (mc.player.getY() - startingY <= -6 && overGroundStates != null) {
                    overGroundStates.restoreStates(mc.player);

                    final NoFallModule noFallModule = OpalClient.getInstance().getModuleRepository().getModule(NoFallModule.class);
                    if (noFallModule.isEnabled()) {
                        noFallModule.syncFallDifference();
                    }

                    this.blockHolder.setPacketTransformer(p -> {
                        if (p instanceof PlayerMoveC2SPacket) {
                            return null;
                        }
                        return p;
                    });

                    startingY = mc.player.getY();

                    this.blockHolder.release();
                } else {
                    this.blockHolder.block();
                }
            }

            blinked = true;
        } else {
            final ClientPlayerEntityAccessor accessor = (ClientPlayerEntityAccessor) mc.player;
            overGroundStates = new GroundStates(
                    mc.player.getEntityPos(),
                    new Vec3d(mc.player.lastX, mc.player.lastY, mc.player.lastZ),
                    mc.player.getVelocity(),
                    accessor.getLastXClient(),
                    accessor.getLastYClient(),
                    accessor.getLastZClient(),
                    accessor.getLastYawClient(),
                    accessor.getLastPitchClient(),
                    accessor.isLastOnGround(),
                    accessor.getTicksSinceLastPositionPacketSent(),
                    LocalDataWatch.get().airTicks,
                    LocalDataWatch.get().groundTicks
            );
            startingY = mc.player.getY();
            failed = false;

            if (blinked) {
                this.blockHolder.release();

                blinked = false;
            }
        }
    }

    @Subscribe
    public void onReceivePacket(final ReceivePacketEvent event) {
        if (event.getPacket() instanceof PlayerPositionLookS2CPacket && overGroundStates != null) {
            this.blockHolder.release();
            overGroundStates = null;
            failed = true;
        }
    }

    @Override
    protected void onDisable() {
        this.blockHolder.release();
    }

    private record GroundStates(Vec3d position, Vec3d lastPosition, Vec3d velocity, double lastX, double lastBaseY, double lastZ,
                                float lastYaw, float lastPitch, boolean lastOnGround, int ticksSinceLastPositionPacketSent, int airTicks, int groundTicks) {

        public void restoreStates(final ClientPlayerEntity localPlayer) {
            final ClientPlayerEntityAccessor accessor = (ClientPlayerEntityAccessor) localPlayer;
            accessor.setLastXClient(lastX);
            accessor.setLastYClient(lastBaseY);
            accessor.setLastZClient(lastZ);
            accessor.setLastYawClient(lastYaw);
            accessor.setLastPitchClient(lastPitch);
            accessor.setLastOnGround(lastOnGround);
            accessor.setTicksSinceLastPositionPacketSent(ticksSinceLastPositionPacketSent);
            localPlayer.setPosition(position);
            localPlayer.lastX = lastPosition.getX();
            localPlayer.lastY = lastPosition.getY();
            localPlayer.lastZ = lastPosition.getZ();
            localPlayer.setVelocity(0, velocity.getY(), 0);
            LocalDataWatch.get().airTicks = airTicks;
            LocalDataWatch.get().groundTicks = groundTicks;
        }

    }

}

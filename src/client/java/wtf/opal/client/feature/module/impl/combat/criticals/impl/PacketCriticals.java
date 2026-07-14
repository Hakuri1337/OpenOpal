package wtf.opal.client.feature.module.impl.combat.criticals.impl;

import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import wtf.opal.client.feature.module.impl.combat.criticals.CriticalsModule;
import wtf.opal.client.feature.module.property.impl.bool.BooleanProperty;
import wtf.opal.client.feature.module.property.impl.mode.ModuleMode;
import wtf.opal.event.impl.game.player.interaction.AttackEvent;
import wtf.opal.event.subscriber.Subscribe;
import wtf.opal.mixin.ClientPlayerEntityAccessor;
import wtf.opal.utility.player.PlayerUtility;

import static wtf.opal.client.Constants.mc;

public final class PacketCriticals extends ModuleMode<CriticalsModule> {
    public PacketCriticals(CriticalsModule module) {
        super(module);
    }

    private final BooleanProperty groundOnly = new BooleanProperty("Ground only", this, false).hideIf(() -> this.module.getActiveMode() != this);

    @Subscribe
    public void onAttack(AttackEvent event) {
        if (event.getTarget() instanceof LivingEntity) {
            if (!PlayerUtility.isCriticalHitAvailable() || (this.groundOnly.getValue() && !mc.player.isOnGround())) {
                return;
            }

            final Box box = mc.player.getBoundingBox().offset(0.0D, 0.0625D, 0.0D);
            if (!PlayerUtility.isBoxEmpty(box)) {
                return;
            }

            final Vec3d pos = mc.player.getEntityPos();
            final boolean ground = mc.player.isOnGround();
            final ClientPlayerEntityAccessor accessor = (ClientPlayerEntityAccessor) mc.player;

            mc.player.setPosition(pos.add(0.0D, 0.0625D, 0.0D));
            mc.player.setOnGround(false);
            accessor.callSendMovementPackets();

            mc.player.setPosition(pos.add(0.0D, 0.00125D, 0.0D));
            mc.player.setOnGround(false);
            accessor.callSendMovementPackets();

            mc.player.setPosition(pos);
            mc.player.setOnGround(ground);
        }
    }

    @Override
    public Enum<?> getEnumValue() {
        return CriticalsModule.Mode.PACKET;
    }
}

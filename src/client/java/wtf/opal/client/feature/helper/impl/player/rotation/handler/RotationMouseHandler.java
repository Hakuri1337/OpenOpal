package wtf.opal.client.feature.helper.impl.player.rotation.handler;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec2f;
import wtf.opal.client.feature.helper.impl.player.rotation.RotationHelper;
import wtf.opal.client.feature.helper.impl.player.rotation.model.IRotationModel;
import wtf.opal.event.EventDispatcher;
import wtf.opal.event.impl.game.PreGameTickEvent;
import wtf.opal.event.impl.game.input.MouseUpdateEvent;
import wtf.opal.event.subscriber.IEventSubscriber;
import wtf.opal.event.subscriber.Subscribe;
import wtf.opal.utility.player.RotationUtility;

import static wtf.opal.client.Constants.mc;

public final class RotationMouseHandler implements IEventSubscriber {

    public RotationMouseHandler() {
        EventDispatcher.subscribe(this);
    }

    private IRotationModel rotationModel;
    private Vec2f targetRotation;
    private boolean active, forward;
    private Vec2f originalRotation;

    @Subscribe
    public void onMouseUpdate(MouseUpdateEvent event) {
        if (this.tickRotation == null || this.targetRotation == null || mc.player == null || !this.active) {
            this.ticked = false;
            return;
        }

        final double sensitivityMultiplier = event.getSensitivityMultiplier();

        if (this.originalRotation != null) {
            final double deltaX = event.getDeltaX() * sensitivityMultiplier;
            double deltaY = event.getDeltaY() * sensitivityMultiplier;

            final double m = 0.15D;

            if (mc.options.getInvertMouseY().getValue()) {
                deltaY *= -1.0;
            }
            this.originalRotation = new Vec2f(
                    this.originalRotation.x + (float) (deltaX * m),
                    MathHelper.clamp(this.originalRotation.y + (float) (deltaY * m), -90.0F, 90.0F)
            );
        }

        if (!this.forward) {
            this.resetToClient();
            if (this.targetRotation == null) {
                this.ticked = false;
                return;
            }
        }

        float tickDelta;
        if (this.ticked) { // Fixes rotation tick interpolation since tickDelta otherwise never reaches 1
            tickDelta = 1.F;
            this.ticked = false;
        } else {
            tickDelta = MinecraftClient.getInstance().getRenderTickCounter().getTickProgress(false);
        }
        final Vec2f tickedRotation = this.rotationModel.tick(this.tickRotation, this.targetRotation, tickDelta);

        final double deltaYaw = tickedRotation.x - mc.player.getYaw();
        final double cursorDeltaX = RotationUtility.getCursorDelta(deltaYaw, sensitivityMultiplier);
        final double deltaPitch = tickedRotation.y - mc.player.getPitch();
        double cursorDeltaY = RotationUtility.getCursorDelta(deltaPitch, sensitivityMultiplier);
        if (mc.options.getInvertMouseY().getValue()) {
            cursorDeltaY *= -1.D;
        }

        event.setDeltaX(cursorDeltaX);
        event.setDeltaY(cursorDeltaY);
        event.setHandled();

        if (!this.forward && RotationUtility.getRotationDifference(tickedRotation, this.targetRotation) == 0.D) {
            this.rotationModel = null;
            this.targetRotation = null;
            this.active = false;
        }
    }

    private Vec2f tickRotation;
    private boolean ticked, unlockCursor;

    @Subscribe(priority = 8)
    public void onPreTick(PreGameTickEvent event) {
        if (mc.player == null) {
            return;
        }

        this.forceTick();
        this.reverse();

        this.setTickRotation(RotationUtility.getRotation());
        this.unlockCursor = false;
    }

    public boolean isUnlockCursor() {
        return this.unlockCursor && this.ticked;
    }

    public void setTickRotation(Vec2f tickRotation) {
        this.tickRotation = tickRotation;
    }

    public void reverse() {
        if (this.forward) {
            this.resetToClient();
            this.forward = false;
        }
    }

    private void forceTick() {
        if (this.active) {
            this.ticked = true;
            mc.mouse.tick();
            this.ticked = false;
        }
    }

    private void resetToClient() {
        final ClientRotationHandler clientHandler = RotationHelper.getClientHandler();
        this.targetRotation = clientHandler.getRotation();
    }

    public void reset() {
        if (this.active && this.originalRotation != null && mc.player != null) {
            mc.player.setYaw(this.originalRotation.x);
            mc.player.setPitch(this.originalRotation.y);
            mc.player.setBodyYaw(this.originalRotation.x);
            mc.player.setHeadYaw(this.originalRotation.x);
        }
        this.active = false;
        this.forward = false;
        this.targetRotation = null;
        this.rotationModel = null;
        this.unlockCursor = false;
        this.originalRotation = null;
    }

    public void rotate(Vec2f targetRotation, IRotationModel rotationModel) {
        if (!this.active && mc.player != null) {
            this.originalRotation = new Vec2f(mc.player.getYaw(), mc.player.getPitch());
        }
        this.targetRotation = targetRotation;
        this.rotationModel = rotationModel;
        this.forward = true;
        this.active = true;
        this.forceTick();
    }

    public void unlockCursor() {
        this.unlockCursor = true;
    }

    public Vec2f getTargetRotation() {
        return targetRotation;
    }

    public IRotationModel getRotationModel() {
        return rotationModel;
    }

    public boolean isActive() {
        return active;
    }

    public boolean isForward() {
        return forward;
    }
}

package wtf.opal.client.feature.module.impl.utility.disabler.impl;

import wtf.opal.client.OpalClient;
import wtf.opal.client.feature.helper.impl.player.packet.blockage.impl.OutboundNetworkBlockage;
import wtf.opal.client.feature.module.impl.combat.FakeLagModule;
import wtf.opal.client.feature.module.impl.combat.velocity.VelocityModule;
import wtf.opal.client.feature.module.impl.movement.StuckModule;
import wtf.opal.client.feature.module.impl.utility.BlinkModule;
import wtf.opal.client.feature.module.impl.utility.disabler.DisablerModule;
import wtf.opal.client.feature.module.property.impl.mode.ModuleMode;
import wtf.opal.event.impl.game.server.ServerDisconnectEvent;
import wtf.opal.event.subscriber.Subscribe;
import wtf.opal.utility.misc.chat.ChatUtility;
import wtf.opal.utility.player.protocol.ViaFabricPlusSupport;

import static wtf.opal.client.Constants.mc;

import java.util.ArrayList;
import java.util.List;

public abstract class AbstractMinibloxDisabler extends ModuleMode<DisablerModule> {
    private static final long DEBUG_INTERVAL_MS = 2_000L;

    private long totalPackets;
    private long windowStartedAt;
    private int windowPackets;
    private boolean stopping;

    protected AbstractMinibloxDisabler(DisablerModule module) {
        super(module);
    }

    @Override
    public void onEnable() {
        this.resetStatistics();
        super.onEnable();

        if (this.isHandlingEvents() && this.validateEnvironment()) {
            this.debug("enabled | protocol=" + ViaFabricPlusSupport.getTargetVersionName());
        }
    }

    @Override
    public void onDisable() {
        if (this.module.isDebugEnabled() && this.totalPackets > 0L) {
            this.print("disabled | total=" + this.totalPackets);
        }
        this.resetStatistics();
        super.onDisable();
    }

    @Subscribe
    public void onServerDisconnect(ServerDisconnectEvent event) {
        this.resetStatistics();
    }

    protected final boolean canSend() {
        if (mc.player == null || mc.world == null || mc.getNetworkHandler() == null) {
            return false;
        }
        return this.validateEnvironment();
    }

    protected final void packetSent() {
        this.totalPackets++;
        this.windowPackets++;
    }

    protected final int takeDebugWindowPackets() {
        final int packets = this.windowPackets;
        this.windowPackets = 0;
        this.windowStartedAt = System.currentTimeMillis();
        return packets;
    }

    protected final boolean isDebugWindowElapsed() {
        return this.module.isDebugEnabled()
                && System.currentTimeMillis() - this.windowStartedAt >= DEBUG_INTERVAL_MS;
    }

    protected final void debug(String message) {
        if (this.module.isDebugEnabled()) {
            this.print(message);
        }
    }

    protected final void stopWithError(String message) {
        if (this.stopping) {
            return;
        }

        this.stopping = true;
        ChatUtility.error(this.label() + " | " + message);
        this.module.setEnabled(false);
        this.stopping = false;
    }

    protected abstract String label();

    private boolean validateEnvironment() {
        if (!ViaFabricPlusSupport.isLoaded()) {
            this.stopWithError("requires ViaFabricPlus");
            return false;
        }

        if (!ViaFabricPlusSupport.isTargeting1_8()) {
            this.stopWithError("requires protocol 1.8, current=" + ViaFabricPlusSupport.getTargetVersionName());
            return false;
        }

        if (this.resolvePacketBufferingConflict()) {
            return false;
        }
        return true;
    }

    /**
     * Sidecar movement payloads must never outlive their matching movement packet.
     * Disable known movement buffers through their normal lifecycle, then wait out an unknown transient block.
     */
    private boolean resolvePacketBufferingConflict() {
        final var repository = OpalClient.getInstance().getModuleRepository();
        if (repository == null) {
            return OutboundNetworkBlockage.get().isAnyBlockages();
        }

        final List<String> disabledModules = new ArrayList<>(4);
        disableIfEnabled(repository.getModule(BlinkModule.class), "Blink", disabledModules);
        disableIfEnabled(repository.getModule(FakeLagModule.class), "FakeLag", disabledModules);
        disableIfEnabled(repository.getModule(StuckModule.class), "Stuck", disabledModules);

        final VelocityModule velocity = repository.getModule(VelocityModule.class);
        if (velocity != null
                && velocity.isEnabled()
                && (velocity.getActiveMode() != null
                && (velocity.getActiveMode().getEnumValue() == VelocityModule.Mode.BUFFER
                || velocity.getActiveMode().getEnumValue() == VelocityModule.Mode.BUFFER_JUMP_RESET))) {
            velocity.setEnabled(false);
            disabledModules.add("AntiKB Buffer");
        }

        if (!disabledModules.isEmpty()) {
            ChatUtility.print(this.label() + " | disabled " + String.join(", ", disabledModules)
                    + " to preserve movement packet pairing.");
        }

        return OutboundNetworkBlockage.get().isAnyBlockages();
    }

    private static void disableIfEnabled(final wtf.opal.client.feature.module.Module module,
                                         final String label,
                                         final List<String> disabledModules) {
        if (module != null && module.isEnabled()) {
            module.setEnabled(false);
            disabledModules.add(label);
        }
    }

    private void resetStatistics() {
        this.totalPackets = 0L;
        this.windowPackets = 0;
        this.windowStartedAt = System.currentTimeMillis();
        this.stopping = false;
    }

    private void print(String message) {
        ChatUtility.print(this.label() + " | " + message);
    }
}

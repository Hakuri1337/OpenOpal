package wtf.opal.client.feature.module.impl.utility.inventory;

import java.util.EnumMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Serializes automated inventory work shared by ChestStealer and InventoryManager.
 * ACA timing is intentionally fixed so configuration cannot create an unsafe packet rate.
 */
public final class AcaInventoryActionScheduler {
    public static final int OPEN_DELAY_TICKS = 2;
    public static final int CLOSE_DELAY_TICKS = 2;
    public static final int MANUAL_PAUSE_TICKS = 2;
    public static final long ACTION_DELAY_MIN_MS = 125L;
    public static final long ACTION_DELAY_MAX_MS = 130L;

    public enum TimingMode {
        INSTANT("Instant"),
        ACA("ACA");

        private final String name;

        TimingMode(final String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return this.name;
        }
    }

    public enum Owner {
        CHEST_STEALER,
        INVENTORY_MANAGER
    }

    public enum Action {
        QUICK_MOVE,
        SWAP,
        THROW
    }

    private static final AcaInventoryActionScheduler INSTANCE = new AcaInventoryActionScheduler();

    private final EnumMap<Owner, Session> sessions = new EnumMap<>(Owner.class);
    private long nextActionAtMs;
    private int lastActionTick = Integer.MIN_VALUE;
    private Owner lastActionOwner;
    private boolean actionInProgress;

    private AcaInventoryActionScheduler() {
        for (final Owner owner : Owner.values()) {
            this.sessions.put(owner, new Session());
        }
    }

    public static AcaInventoryActionScheduler getInstance() {
        return INSTANCE;
    }

    public synchronized void beginSession(final Owner owner, final TimingMode mode, final int currentTick) {
        final Session session = this.sessions.get(owner);
        if (session.active && session.mode == mode) {
            return;
        }

        final int manualPauseUntilTick = session.manualPauseUntilTick;
        session.active = true;
        session.mode = mode;
        session.openReadyTick = mode == TimingMode.ACA ? currentTick + OPEN_DELAY_TICKS : currentTick;
        session.manualPauseUntilTick = manualPauseUntilTick;
        session.closeReadyTick = -1;
        session.lastRawSlot = -1;
    }

    public synchronized void endSession(final Owner owner) {
        this.sessions.get(owner).reset();
    }

    public synchronized boolean canAct(final Owner owner, final TimingMode mode, final int currentTick,
                                       final Action action, final boolean fastThrow) {
        final Session session = this.sessions.get(owner);
        if (this.actionInProgress || !session.active || session.mode != mode || currentTick < session.manualPauseUntilTick) {
            return false;
        }
        if (mode == TimingMode.ACA && currentTick < session.openReadyTick) {
            return false;
        }

        if (action == Action.THROW && fastThrow) {
            return true;
        }
        if (mode == TimingMode.INSTANT) {
            return true;
        }

        final long now = System.currentTimeMillis();
        return now >= this.nextActionAtMs && this.lastActionTick != currentTick;
    }

    public synchronized boolean executeAction(final Owner owner, final TimingMode mode, final int currentTick,
                                              final Action action, final int rawSlot, final boolean fastThrow,
                                              final Runnable operation) {
        if (operation == null || !this.canAct(owner, mode, currentTick, action, fastThrow)) {
            return false;
        }

        this.actionInProgress = true;
        try {
            operation.run();
            this.recordAction(owner, mode, currentTick, action, rawSlot, fastThrow);
            return true;
        } finally {
            this.actionInProgress = false;
        }
    }

    private void recordAction(final Owner owner, final TimingMode mode, final int currentTick,
                              final Action action, final int rawSlot, final boolean fastThrow) {
        final Session session = this.sessions.get(owner);
        session.lastRawSlot = rawSlot;
        session.closeReadyTick = -1;

        if (mode == TimingMode.ACA && !(action == Action.THROW && fastThrow)) {
            this.nextActionAtMs = System.currentTimeMillis() + randomActionDelay();
            this.lastActionTick = currentTick;
            this.lastActionOwner = owner;
        }
    }

    public synchronized void pauseForManualInput(final Owner owner, final int currentTick) {
        final Session session = this.sessions.get(owner);
        session.manualPauseUntilTick = Math.max(session.manualPauseUntilTick, currentTick + MANUAL_PAUSE_TICKS);
        session.closeReadyTick = -1;
        this.nextActionAtMs = Math.max(this.nextActionAtMs, System.currentTimeMillis() + ACTION_DELAY_MAX_MS);
        this.lastActionTick = currentTick;
        this.lastActionOwner = owner;
    }

    public synchronized void scheduleClose(final Owner owner, final TimingMode mode, final int currentTick) {
        final Session session = this.sessions.get(owner);
        if (!session.active || session.mode != mode || session.closeReadyTick >= 0) {
            return;
        }
        session.closeReadyTick = mode == TimingMode.ACA ? currentTick + CLOSE_DELAY_TICKS : currentTick;
    }

    public synchronized boolean canClose(final Owner owner, final TimingMode mode, final int currentTick) {
        final Session session = this.sessions.get(owner);
        if (this.actionInProgress || !session.active || session.mode != mode || session.closeReadyTick < 0
                || currentTick < session.manualPauseUntilTick) {
            return false;
        }
        if (currentTick < session.closeReadyTick) {
            return false;
        }
        return mode == TimingMode.INSTANT || System.currentTimeMillis() >= this.nextActionAtMs;
    }

    public synchronized void recordClose(final Owner owner) {
        this.sessions.get(owner).reset();
    }

    public synchronized boolean isCoolingDown(final Owner owner, final TimingMode mode, final int currentTick) {
        final Session session = this.sessions.get(owner);
        if (!session.active || session.mode != mode) {
            return false;
        }
        if (currentTick < session.manualPauseUntilTick || currentTick < session.openReadyTick) {
            return true;
        }
        if (session.closeReadyTick >= 0 && currentTick < session.closeReadyTick) {
            return true;
        }
        return mode == TimingMode.ACA
                && this.lastActionOwner == owner
                && System.currentTimeMillis() < this.nextActionAtMs;
    }

    public synchronized int getLastRawSlot(final Owner owner) {
        return this.sessions.get(owner).lastRawSlot;
    }

    public synchronized long remainingDelayMs(final Owner owner, final TimingMode mode, final int currentTick) {
        if (!this.isCoolingDown(owner, mode, currentTick)) {
            return 0L;
        }
        return Math.max(0L, this.nextActionAtMs - System.currentTimeMillis());
    }

    private static long randomActionDelay() {
        return ThreadLocalRandom.current().nextLong(ACTION_DELAY_MIN_MS, ACTION_DELAY_MAX_MS + 1L);
    }

    private static final class Session {
        private boolean active;
        private TimingMode mode;
        private int openReadyTick;
        private int manualPauseUntilTick = Integer.MIN_VALUE;
        private int closeReadyTick = -1;
        private int lastRawSlot = -1;

        private void reset() {
            this.active = false;
            this.mode = null;
            this.openReadyTick = 0;
            this.manualPauseUntilTick = Integer.MIN_VALUE;
            this.closeReadyTick = -1;
            this.lastRawSlot = -1;
        }
    }
}

package wtf.opal.client.feature.module.impl.utility.inventory;

import net.minecraft.screen.slot.SlotActionType;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Shared timing gate for automated inventory actions. Delays are sampled once
 * after an action so repeated tick checks cannot move the deadline around.
 */
public final class AcaInventoryActionScheduler {
    public enum Owner {
        CHEST_STEALER,
        INVENTORY_MANAGER
    }

    public enum Action {
        // ACA evaluates Bukkit inventory events on the server tick. The extra
        // tick of headroom prevents two correctly spaced client packets from
        // being compressed below the check's wall-clock threshold.
        QUICK_MOVE(225L),
        PICKUP(375L),
        SWAP(175L),
        THROW(105L);

        private final long acaFloorMs;

        Action(final long acaFloorMs) {
            this.acaFloorMs = acaFloorMs;
        }
    }

    private static final AcaInventoryActionScheduler INSTANCE = new AcaInventoryActionScheduler();

    private long nextActionAt;
    private long closeAt;
    private long lastActionAt;
    private int lastRawSlot = -1;
    private Owner owner;

    private AcaInventoryActionScheduler() {
    }

    public static AcaInventoryActionScheduler getInstance() {
        return INSTANCE;
    }

    public synchronized void beginSession(final Owner owner) {
        final long now = System.currentTimeMillis();
        this.owner = owner;
        this.nextActionAt = Math.max(this.nextActionAt, now + humanDelay(180L, 360L, false));
        this.closeAt = 0L;
        this.lastRawSlot = -1;
    }

    public synchronized boolean canAct(final Action action) {
        if (action == null) {
            return false;
        }
        final long now = System.currentTimeMillis();
        return now >= this.nextActionAt
                && (this.lastActionAt == 0L || now - this.lastActionAt >= action.acaFloorMs);
    }

    public synchronized void record(final Owner owner, final Action action, final int rawSlot,
                                    final long preferredMin, final long preferredMax) {
        if (action == null) {
            return;
        }

        final long now = System.currentTimeMillis();
        this.owner = owner;
        final long minimum = Math.max(action.acaFloorMs, Math.max(0L, preferredMin));
        final long maximum = Math.max(minimum + 110L, Math.max(minimum, preferredMax));
        this.lastActionAt = now;
        this.lastRawSlot = rawSlot;
        this.nextActionAt = now + humanDelay(minimum, maximum, true);
        this.closeAt = 0L;
    }

    public synchronized void scheduleClose() {
        if (this.closeAt != 0L) {
            return;
        }
        final long now = System.currentTimeMillis();
        this.closeAt = Math.max(this.nextActionAt, now + humanDelay(150L, 280L, false));
    }

    public synchronized boolean canClose() {
        return this.closeAt != 0L && System.currentTimeMillis() >= this.closeAt;
    }

    public synchronized void recordClose(final Owner owner) {
        final long now = System.currentTimeMillis();
        this.owner = owner;
        this.nextActionAt = Math.max(this.nextActionAt, now + humanDelay(90L, 160L, false));
        this.closeAt = 0L;
        this.lastRawSlot = -1;
    }

    public synchronized boolean isCoolingDown(final Owner owner) {
        return this.owner == owner && System.currentTimeMillis() < this.nextActionAt;
    }

    public synchronized long remainingDelayMs() {
        return Math.max(0L, this.nextActionAt - System.currentTimeMillis());
    }

    public synchronized int getLastRawSlot() {
        return this.lastRawSlot;
    }

    public synchronized long getLastActionAt() {
        return this.lastActionAt;
    }

    public static Action from(final SlotActionType actionType) {
        if (actionType == null) {
            return null;
        }
        return switch (actionType) {
            case QUICK_MOVE -> Action.QUICK_MOVE;
            case PICKUP, PICKUP_ALL, CLONE -> Action.PICKUP;
            case SWAP -> Action.SWAP;
            case THROW -> Action.THROW;
            default -> null;
        };
    }

    public static long minimumDelay(final Action action) {
        return action == null ? 0L : action.acaFloorMs;
    }

    private static long randomBetween(final long minimum, final long maximum) {
        if (maximum <= minimum) {
            return minimum;
        }
        return ThreadLocalRandom.current().nextLong(minimum, maximum + 1L);
    }

    private static long humanDelay(final long minimum, final long maximum, final boolean allowHesitation) {
        if (maximum <= minimum) {
            return minimum;
        }

        final ThreadLocalRandom random = ThreadLocalRandom.current();
        final double triangular = (random.nextDouble() + random.nextDouble()) * 0.5D;
        long delay = minimum + Math.round((maximum - minimum) * triangular);
        if (allowHesitation && random.nextInt(12) == 0) {
            delay += randomBetween(90L, 260L);
        }
        return delay;
    }
}

package com.mikasrevs.newphone.phone.animation;

/** Frame-rate independent, reversible lift/lower curve, with no overshoot. */
public final class PhoneCallMotion {
    private float from, target;
    private long started;
    private long duration = 550_000_000L;
    private long lastRaised;
    private boolean seenRaised;

    public float updateRemote(boolean raised,long now,boolean animate) {
        if(raised){lastRaised=now;seenRaised=true;}
        return update(raised||(seenRaised&&now-lastRaised<150_000_000L),now,animate);
    }

    public float update(boolean raised, long now, boolean animate) {
        float next = raised ? 1 : 0;
        float current = value(now);
        if (next != target) {
            from = current;
            target = next;
            started = now;
            duration = raised ? 550_000_000L : 400_000_000L;
        }
        if (!animate) { from = target; started = now; }
        return value(now);
    }

    public float value(long now) {
        float t = Math.max(0, Math.min(1, (now - started) / (float) duration));
        float eased = t * t * t * (t * (t * 6 - 15) + 10);
        return from + (target - from) * eased;
    }

    public static boolean shouldRaise(String status, boolean video) {
        return !video && ("OUTGOING".equals(status) || "ACTIVE".equals(status));
    }
}

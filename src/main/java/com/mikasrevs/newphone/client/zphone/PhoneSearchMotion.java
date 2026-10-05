package com.mikasrevs.newphone.client.zphone;

/** Reversible, frame-rate independent expansion from the center of the home search pill. */
public final class PhoneSearchMotion {
    private static final long DURATION_NS = 280_000_000L;
    private float from, target;
    private long startedAt;

    public float sample(boolean expanded, boolean animate, long now) {
        float next = expanded ? 1F : 0F;
        if (!animate) { from = target = next; startedAt = now; return next; }
        float value = value(now);
        if (next != target) { from = value; target = next; startedAt = now; }
        return value;
    }

    private float value(long now) {
        float t = Math.max(0F, Math.min(1F, (now - startedAt) / (float) DURATION_NS));
        float eased = 1F - (1F - t) * (1F - t) * (1F - t);
        return from + (target - from) * eased;
    }

    public static int left(float progress) { return Math.round(100 - 82 * progress); }
    public static int right(float progress) { return 300 - left(progress); }
}

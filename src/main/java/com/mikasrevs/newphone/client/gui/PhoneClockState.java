package com.mikasrevs.newphone.client.gui;

public final class PhoneClockState {
    private static long stopwatchStartedAt;
    private static long stopwatchAccumulated;
    private static boolean stopwatchRunning;

    private static long timerEndAt;
    private static long timerDurationMs;

    private PhoneClockState() {
    }

    public static boolean stopwatchRunning() {
        return stopwatchRunning;
    }

    public static void toggleStopwatch() {
        long now = System.currentTimeMillis();
        if (stopwatchRunning) {
            stopwatchAccumulated += Math.max(0L, now - stopwatchStartedAt);
            stopwatchRunning = false;
        } else {
            stopwatchStartedAt = now;
            stopwatchRunning = true;
        }
    }

    public static void resetStopwatch() {
        stopwatchAccumulated = 0L;
        stopwatchStartedAt = System.currentTimeMillis();
        stopwatchRunning = false;
    }

    public static long stopwatchElapsedMs() {
        if (!stopwatchRunning) {
            return stopwatchAccumulated;
        }
        return stopwatchAccumulated + Math.max(0L, System.currentTimeMillis() - stopwatchStartedAt);
    }

    public static void startTimerMinutes(int minutes) {
        timerDurationMs = Math.max(1, minutes) * 60_000L;
        timerEndAt = System.currentTimeMillis() + timerDurationMs;
    }

    public static void cancelTimer() {
        timerEndAt = 0L;
        timerDurationMs = 0L;
    }

    public static boolean timerActive() {
        return timerEndAt > System.currentTimeMillis();
    }

    public static boolean timerFinished() {
        return timerEndAt > 0L && timerEndAt <= System.currentTimeMillis();
    }

    public static long timerRemainingMs() {
        return timerEndAt == 0L ? 0L : Math.max(0L, timerEndAt - System.currentTimeMillis());
    }

    public static float timerProgress() {
        if (timerDurationMs <= 0L) {
            return 0.0F;
        }
        return Math.max(0.0F, Math.min(1.0F, timerRemainingMs() / (float) timerDurationMs));
    }
}

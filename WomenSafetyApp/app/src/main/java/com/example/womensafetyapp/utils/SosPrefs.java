    package com.example.womensafetyapp.utils;

    import android.content.Context;
    import android.content.SharedPreferences;

    public class SosPrefs {
        private static final String PREFS = "sos_prefs";
        private static final String K_SHAKE = "pref_sos_shake";
        private static final String K_VOICE = "pref_sos_voice";
        private static final String K_PANIC = "panic_mode_enabled";
        private static SharedPreferences sp(Context c) {
            return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        }

        public static void setShakeEnabled(Context c, boolean on) {
            sp(c).edit().putBoolean(K_SHAKE, on).apply();
        }

        public static void setVoiceEnabled(Context c, boolean on) {
            sp(c).edit().putBoolean(K_VOICE, on).apply();
        }

        public static void setAutoCallEnabled(Context ctx, boolean enabled) {
            SharedPreferences prefs = ctx.getSharedPreferences("sos_prefs", Context.MODE_PRIVATE);
            prefs.edit().putBoolean("auto_call_enabled", enabled).apply();
        }

        public static boolean isShakeEnabled(Context c) {
            return sp(c).getBoolean(K_SHAKE, false);
        }

        public static boolean isVoiceEnabled(Context c) {
            return sp(c).getBoolean(K_VOICE, false);
        }
        public static boolean isAutoCallEnabled(Context ctx) {
            SharedPreferences prefs = ctx.getSharedPreferences("sos_prefs", Context.MODE_PRIVATE);
            return prefs.getBoolean("auto_call_enabled", false);
        }
        public static void setAutoTrackingEnabled(Context ctx, boolean enabled) {
            SharedPreferences prefs = ctx.getSharedPreferences("sos_prefs", Context.MODE_PRIVATE);
            prefs.edit().putBoolean("auto_tracking_enabled", enabled).apply();
        }

        public static boolean isAutoTrackingEnabled(Context ctx) {
            SharedPreferences prefs = ctx.getSharedPreferences("sos_prefs", Context.MODE_PRIVATE);
            return prefs.getBoolean("auto_tracking_enabled", true); // mặc định bật
        }
        // === PANIC MODE ===
        public static void setPanicModeEnabled(Context ctx, boolean enabled) {
            sp(ctx).edit().putBoolean(K_PANIC, enabled).apply();
        }

        public static boolean isPanicModeEnabled(Context ctx) {
            return sp(ctx).getBoolean(K_PANIC, false);
        }

    }

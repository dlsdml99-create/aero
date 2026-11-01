package com.dlsdml99.aero;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

// 출발 알림: 앱 화면이 정한 시각들(전날 밤, 나갈 시각 30분 전, 체크인 마감 30분 전)을 예약
public class Alerts {
    static final String PREF = "aero", CHANNEL = "trip", ACTION = "com.dlsdml99.aero.ALERT";

    static void save(Context c, String json) {
        c.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString("alerts", json).apply();
    }

    private static PendingIntent intent(Context c, int code, JSONObject a) {
        Intent i = new Intent(c, AlertReceiver.class).setAction(ACTION);
        if (a != null) i.putExtra("title", a.optString("title")).putExtra("body", a.optString("body")).putExtra("code", code);
        return PendingIntent.getBroadcast(c, code, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    // 예전 예약은 모두 지우고, 앞으로 울릴 것만 다시 예약
    static void schedule(Context c) {
        SharedPreferences p = c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        try {
            JSONArray old = new JSONArray(p.getString("codes", "[]"));
            for (int k = 0; k < old.length(); k++) am.cancel(intent(c, old.getInt(k), null));
        } catch (Exception ignored) {}
        JSONArray codes = new JSONArray();
        long now = System.currentTimeMillis();
        try {
            JSONArray list = new JSONArray(p.getString("alerts", "[]"));
            for (int k = 0; k < list.length() && k < 60; k++) {
                JSONObject a = list.getJSONObject(k);
                long at = a.optLong("at");
                if (at <= now) continue;
                int code = 1000 + (a.optString("id").hashCode() & 0x3fffffff) % 100000;
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent(c, code, a));
                codes.put(code);
            }
        } catch (Exception ignored) {}
        p.edit().putString("codes", codes.toString()).apply();
    }

    static void show(Context c, String title, String body, int code) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(new NotificationChannel(CHANNEL, "출발 알림", NotificationManager.IMPORTANCE_HIGH));
        Intent open = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(c, 5, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(c, CHANNEL) : new Notification.Builder(c);
        b.setSmallIcon(R.drawable.ic_notify).setContentTitle(title).setContentText(body)
            .setStyle(new Notification.BigTextStyle().bigText(body)).setAutoCancel(true).setContentIntent(pi);
        if (Build.VERSION.SDK_INT < 26) b.setPriority(Notification.PRIORITY_HIGH);
        try { nm.notify(code, b.build()); } catch (SecurityException ignored) {}
    }
}

package com.dlsdml99.aero;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import org.json.JSONObject;

// 홈 화면 위젯: 앱을 열지 않아도 공항으로 나갈 시각이 보인다
public class AeroWidget extends AppWidgetProvider {
    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) { draw(c, m, ids); }

    static void updateAll(Context c) {
        try {
            AppWidgetManager m = AppWidgetManager.getInstance(c);
            int[] ids = m.getAppWidgetIds(new ComponentName(c, AeroWidget.class));
            if (ids != null && ids.length > 0) draw(c, m, ids);
        } catch (Exception ignored) {}
    }

    private static String until(long at) {
        long min = Math.round((at - System.currentTimeMillis()) / 60000.0);
        if (min < -60) return "지난 출발이에요";
        if (min <= 0) return "지금 출발하세요";
        if (min < 60) return min + "분 후";
        long d = min / 1440, h = (min % 1440) / 60;
        return d > 0 ? d + "일 " + h + "시간 후" : h + "시간 " + (min % 60) + "분 후";
    }

    private static void draw(Context c, AppWidgetManager m, int[] ids) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget);
        String time = "--:--", date = "공항 출발", line = "항공권을 찍으면 여기에 나와요", left = "";
        try {
            JSONObject o = new JSONObject(c.getSharedPreferences(Alerts.PREF, Context.MODE_PRIVATE).getString("widget", "{}"));
            long at = o.optLong("at", 0);
            if (o.has("time") && at > System.currentTimeMillis() - 3600000L) {
                time = o.optString("time");
                date = "공항 출발 · " + o.optString("date", "");
                line = o.optString("line", "");
                left = until(at);
            }
        } catch (Exception ignored) {}
        v.setTextViewText(R.id.w_date, date);
        v.setTextViewText(R.id.w_time, time);
        v.setTextViewText(R.id.w_line, line);
        v.setTextViewText(R.id.w_left, left);
        Intent open = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        v.setOnClickPendingIntent(R.id.w_root, PendingIntent.getActivity(c, 3, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        m.updateAppWidget(ids, v);
    }
}

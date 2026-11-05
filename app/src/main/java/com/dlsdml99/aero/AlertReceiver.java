package com.dlsdml99.aero;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

// 알림 시각이 되면 알림을 띄움. 폰을 껐다 켜거나 앱을 업데이트하면 예약을 다시 걸어 둠.
public class AlertReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        String a = i == null ? null : i.getAction();
        if (Alerts.ACTION.equals(a)) {
            // 이 앱이 예약한 알림만 띄움 (다른 앱이 보낸 가짜 알림 신호는 무시)
            if (!Alerts.token(c).equals(i.getStringExtra("tok"))) return;
            Alerts.show(c, i.getStringExtra("title"), i.getStringExtra("body"), i.getIntExtra("code", 7));
        } else {
            Alerts.schedule(c);
        }
        AeroWidget.updateAll(c);
    }
}

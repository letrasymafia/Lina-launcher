package com.lina.launcher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Al encender la pantalla, arranca el servicio y abre Lina. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        WatchService.start(context);
        try {
            Intent i = new Intent(context, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(i);
        } catch (Exception ignored) { }
    }
}

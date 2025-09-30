package com.example.doorlock;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.RemoteViews;

public class LockControlWidget extends AppWidgetProvider {
    private static final String TAG = "LockControlWidget";
    public static final String ACTION_UNLOCK = "com.example.ACTION_UNLOCK";
    public static final String ACTION_UPDATE = "com.example.ACTION_UPDATE";

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId, false);
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        Log.d(TAG, "Received action: " + intent.getAction());

        if (intent.getPackage() != null && !intent.getPackage().equals(context.getPackageName())) {
            Log.d(TAG, "忽略外部广播");
            return;
        }

        if (ACTION_UNLOCK.equals(intent.getAction())) {
            Log.d(TAG, "处理开锁指令");

            Intent activityIntent = new Intent(context, ServiceLauncherActivity.class);
            activityIntent.setAction(ACTION_UNLOCK);
            activityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(activityIntent);
            Log.d(TAG, "透明Activity已启动");

            AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
            ComponentName thisWidget = new ComponentName(context, LockControlWidget.class);
            int[] appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget);
            for (int appWidgetId : appWidgetIds) {
                updateAppWidget(context, appWidgetManager, appWidgetId, true);
            }
        } else if (ACTION_UPDATE.equals(intent.getAction())) {
            Log.d(TAG, "处理更新指令");
            boolean isUnlocked = intent.getBooleanExtra("isUnlocked", false);
            Log.d(TAG, "更新状态: " + (isUnlocked ? "门已开" : "门已锁"));

            AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
            ComponentName thisWidget = new ComponentName(context, LockControlWidget.class);
            int[] appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget);
            for (int appWidgetId : appWidgetIds) {
                updateAppWidget(context, appWidgetManager, appWidgetId, isUnlocked);
            }
        }
    }

    static void updateAppWidget(Context context, AppWidgetManager appWidgetManager,
                                int appWidgetId, boolean isUnlocked) {
        Log.d(TAG, "更新小组件[" + appWidgetId + "]状态: " + (isUnlocked ? "门已开" : "门已锁"));

        Intent intent = new Intent(context, LockControlWidget.class);
        intent.setAction(ACTION_UNLOCK);

        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, intent, flags);

        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_lock_control);

        if (isUnlocked) {
            views.setImageViewResource(R.id.widget_lock_icon, R.drawable.unlock);
        } else {
            views.setImageViewResource(R.id.widget_lock_icon, R.drawable.lock);
        }

        views.setOnClickPendingIntent(R.id.widget_lock_icon, pendingIntent);
        appWidgetManager.updateAppWidget(appWidgetId, views);
    }
}
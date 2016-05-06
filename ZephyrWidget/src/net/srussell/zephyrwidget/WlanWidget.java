package net.srussell.zephyrwidget;

import java.util.Timer;
import java.util.TimerTask;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.net.wifi.WifiManager;
import android.widget.RemoteViews;

public class WlanWidget extends AppWidgetProvider
{

	RemoteViews remoteViews;
	AppWidgetManager appWidgetManager;
	ComponentName thisWidget;
	WifiManager wifiManager;

	public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds)
	{
		Timer timer = new Timer();

		timer.scheduleAtFixedRate(new WlanTimer(context, appWidgetManager), 1, 10000);
	}

	private class WlanTimer extends TimerTask
	{
		RemoteViews remoteViews;
		AppWidgetManager appWidgetManager;
		ComponentName thisWidget;

		public WlanTimer(Context context, AppWidgetManager appWidgetManager)
		{
			this.appWidgetManager = appWidgetManager;
			// remoteViews = new RemoteViews(context.getPackageName(),
			// R.layout.widget);
			thisWidget = new ComponentName(context, WlanWidget.class);
			wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
		}

		@Override
		public void run()
		{
			// remoteViews.setTextViewText(R.id.widget_textview,
			// wifiManager.getConnectionInfo().getSSID());
			appWidgetManager.updateAppWidget(thisWidget, remoteViews);
		}

	}
}
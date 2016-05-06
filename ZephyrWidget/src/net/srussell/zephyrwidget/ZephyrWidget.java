package net.srussell.zephyrwidget;

/**
 * http://www.srussell.net
 * 
 * (c) Copyright Scott M. Russell. 2013, All rights reserved.
 */

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import net.srussell.zephyrwidget.ZephyrHxM.ListenerData;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.util.Log;
import android.widget.RemoteViews;

/**
 * 
 * This app is to display ZephyrHxM data as a widget
 * 
 * @author Scott Russell
 */
public class ZephyrWidget extends AppWidgetProvider implements PropertyChangeListener
{
	private static Context myContext = null;
	private static AppWidgetManager myWidgetMgr = null;

	private static ZephyrHxmMgr mgr = null;

	@Override
	public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds)
	{
		Log.d("onUpdate", "entering...");

		myContext = context;
		myWidgetMgr = appWidgetManager;

		try
		{
			if (mgr == null)
			{
				Log.d("onUpdate", "no ZephyrHxmMgr yet...instantiating");
				mgr = new ZephyrHxmMgr(this, context);

				Log.d("onUpdate", "Mgr instantiated...issuing start");
				mgr.start();

				Log.d("onUpdate", "Mgr started!");

			}
		}
		catch (Throwable t)
		{
			Log.d("onUpdate", "ZephyrMgr flamed! err[" + (t == null ? "n/a" : t.getLocalizedMessage()) + "]");
			StringBuffer msg = new StringBuffer();

			StackTraceElement[] stackElements = t.getStackTrace();

			int i;
			for (i = (stackElements.length - 1); i > 0; i--)
			{
				if (stackElements[i].toString().contains("Caused by:"))
					break;
			}
			for (; i < stackElements.length; i++)
			{
				msg.append(stackElements[i].toString());
				msg.append("\n");
			}

			Log.d("onUpdate", msg.toString());
		}

		Log.d("onUpdate", "...exiting");
	}

	@Override
	public void onDeleted(Context context, int[] appWidgetIds)
	{
		super.onDeleted(context, appWidgetIds);

		Log.d("onDeleted", "entering...");

		if (mgrValid())
		{
			mgr.quiesce();
			mgr = null;
		}
	}

	@Override
	public void onDisabled(Context context)
	{
		super.onDisabled(context);

		Log.d("onDisabled", "entering...");

		if (mgrValid())
			mgr.disable();
	}

	@Override
	public void onEnabled(Context context)
	{
		super.onEnabled(context);

		Log.d("onEnabled", "entering...");

		if (mgrValid())
			mgr.enable();
	}

	private boolean mgrValid()
	{
		if (mgr == null)
			return false;

		return true;
	}

	@Override
	public void propertyChange(PropertyChangeEvent event)
	{
		RemoteViews remoteViews;
		ComponentName zephyrWidget;

		switch (ListenerData.get(event.getPropertyName()))
		{
			case HEART_RATE_CHANGE:
				Integer newHeartRate = (Integer) event.getNewValue();
				int heartRate = newHeartRate.intValue();

				remoteViews = new RemoteViews(myContext.getPackageName(), R.layout.activity_zephyr_widget);
				zephyrWidget = new ComponentName(myContext, ZephyrWidget.class);
				remoteViews.setTextViewText(R.id.widgetText, "HR: " + (heartRate != 0 ? heartRate : "---"));
				Log.d("propertyChange", "updating heart rate[" + heartRate + "]");

				myWidgetMgr.updateAppWidget(zephyrWidget, remoteViews);
				break;

			case STATUS_CHANGE:

				Boolean active = (Boolean) event.getNewValue();

				remoteViews = new RemoteViews(myContext.getPackageName(), R.layout.activity_zephyr_widget);
				zephyrWidget = new ComponentName(myContext, ZephyrWidget.class);
				if (active)
				{
					Log.d("propertyChange", "we're connected..updating background to normal");
					remoteViews.setInt(R.id.widgetLayout, "setBackgroundResource", R.drawable.zephyr_hxm);
				}
				else
				{
					Log.d("propertyChange", "NOT connected..updating background to disabled");
					remoteViews.setInt(R.id.widgetLayout, "setBackgroundResource", R.drawable.zephyr_hxm_disabled);
				}

				myWidgetMgr.updateAppWidget(zephyrWidget, remoteViews);

				break;

			default:
				Log.e("propertyChange", "now wasn't expecting THAT! [" + event.getPropertyName() + "]");
				break;
		}

	}

}

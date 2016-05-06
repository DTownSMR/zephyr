package net.srussell.zephyrwidget;

/**
 * http://www.srussell.net
 * 
 * (c) Copyright Scott M. Russell. 2013, All rights reserved.
 */

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Date;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

import zephyr.android.HxMBT.BTClient;
import zephyr.android.HxMBT.ZephyrProtocol;
import android.appwidget.AppWidgetManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

public class ZephyrHxmMgr extends TimerTask implements ZephyrHxM
{
	private static final String DUMMY_MAC_ID = "00:11:22:33:44:55";
	/* adapter lock */
	private static final Object adapterLock = new Object();
	private BluetoothAdapter adapter = null;
	private BTClient _bt;
	private ZephyrProtocol _protocol;
	private NewConnectedListener _NConnListener;

	private Context myContext = null;
	private AppWidgetManager myWidgetMgr = null;

	private Date lastDataDate = null;
	private static final long STALE_TIME = 4 * 1000; // stale is considered no data in 4 seconds

	// zephyr property change updater
	private PropertyChangeSupport changes = new PropertyChangeSupport(this);

	private static boolean enabled = true;
	private static boolean dirty = false;

	private static Timer myTimer = null;
	private static int TIMER_DELAY = 5000; // update self check every 5 seconds

	/**
	 * data collected from Zephyr
	 */
	private static int zHeartRate = -1;
	private static double zInstantSpeed = 0l;
	private static int zBatteryCharge = 0;

	public ZephyrHxmMgr(PropertyChangeListener listener, Context context)
	{
		super();

		setMyContext(context);
		changes.addPropertyChangeListener(listener);
	}

	public void start()
	{
		Log.d("start", "entering...");
		try
		{
			initZephyr(getMyContext());

			setTimers();
		}
		catch (Throwable t)
		{
			Log.d("start", t.toString());
		}

		Log.d("start", "exiting...");
	}

	private void setTimers()
	{
		Log.d("setTimers", "entering...");
		if (myTimer != null)
		{
			synchronized (myTimer)
			{
				myTimer.cancel();
			}
		}
		myTimer = new Timer();
		myTimer.scheduleAtFixedRate(this, TIMER_DELAY, TIMER_DELAY);
		Log.d("setTimers", "Manager timer started [" + myTimer.toString() + "]");
	}

	/**
	 * this run method is used to ensure that after a connection error we keep
	 * trying to connect
	 */
	@Override
	public void run()
	{
		Log.d("run", "entering...");
		if (!isEnabled())
			return;

		Log.d("run", "we're enabled...init the Zephyr");
		initZephyr(getMyContext());
	}

	public void quiesce()
	{
		Log.d("quiesce", "entering...");
		if (myTimer != null)
		{
			synchronized (myTimer)
			{
				if (myTimer != null)
				{
					try
					{
						Log.d("quiesce", "canceling manager timer[" + myTimer.toString() + "]");
						myTimer.cancel();
					}
					catch (Throwable t)
					{
						Log.d("quiesce", "Timer cancel flamed!");
					}
					myTimer = null;
				}
			}
		}

		doClose();
	}

	public boolean isConnected()
	{
		if (_bt != null)
		{
			return _bt.IsConnected();
		}

		return false;
	}

	protected boolean validConnection()
	{
		Log.d("validConnection", "entering...");

		synchronized (adapterLock)
		{

			if (adapter == null)
			{
				Log.d("validConnection", "no adapter...fails");
				return false;
			}

			if (adapter.getState() == BluetoothAdapter.STATE_OFF || adapter.getState() == BluetoothAdapter.STATE_TURNING_OFF)
			{
				Log.d("validConnection", "bad state...fails");
				return false;
			}

			if (_bt == null || !_bt.IsConnected())
			{
				Log.d("validConnection", "no client or not connected...fails");
				return false;
			}
		}

		Log.d("validConnection", "have valid connection");
		return true;
	}

	private void reset()
	{
		synchronized (adapterLock)
		{
			adapter = null;
			_bt = null;
		}
	}

	private void doClose()
	{
		Log.d("doClose", "entering...");
		if (adapter == null)
			return;

		synchronized (adapterLock)
		{
			try
			{
				if (_bt != null)
				{
					Log.d("doClose", "have client...");
					if (_NConnListener != null)
					{
						Log.d("doClose", "have connection listener...removing it");
						_bt.removeConnectedEventListener(_NConnListener);
					}
					Log.d("doClose", "closing client");
					_bt.Close();
				}

			}
			catch (Throwable t)
			{
				String msg = t.getLocalizedMessage();
				if (msg != null)
					Log.e("doClose", msg);

			}
			reset();
		}

		changes.firePropertyChange(ListenerData.STATUS_CHANGE.getValue(), Boolean.valueOf(true), Boolean.valueOf(false));
	}

	protected synchronized void initZephyr(Context context)
	{
		Log.d("initZephyr", "entering...");
		if (!isEnabled())
			return;

		Log.d("initZephyr", "we're enabled");
		boolean needReset = false;

		/*
		 * Sending a message to android that we are going to initiate a pairing
		 * request
		 */
		IntentFilter filter = new IntentFilter("android.bluetooth.device.action.PAIRING_REQUEST");
		/*
		 * Registering a new BTBroadcast receiver from the Main Activity context
		 * with pairing request event
		 */
		context.getApplicationContext().registerReceiver(new BTBroadcastReceiver(), filter);

		// Registering the BTBondReceiver in the application that the status of
		// the receiver has changed to Paired
		IntentFilter filter2 = new IntentFilter("android.bluetooth.device.action.BOND_STATE_CHANGED");
		context.getApplicationContext().registerReceiver(new BTBondReceiver(), filter2);

		init: if (adapter == null)
		{
			Log.d("initZephyr", "no adapter initialized...getting setup...");

			mgrHeartbeat();

			synchronized (adapterLock)
			{
				try
				{
					String BhMacID = DUMMY_MAC_ID;
					adapter = BluetoothAdapter.getDefaultAdapter();

					Set<BluetoothDevice> pairedDevices = adapter.getBondedDevices();

					if (pairedDevices.size() > 0)
					{
						for (BluetoothDevice device : pairedDevices)
						{
							if (device.getName().startsWith("HXM"))
							{
								BluetoothDevice btDevice = device;
								BhMacID = btDevice.getAddress();
								break;
							}
						}

					}

					BluetoothDevice Device = adapter.getRemoteDevice(BhMacID);
					String DeviceName = Device.getName();
					if (DeviceName == null)
					{
						Log.d("initZephyr", "no Zephyr found...try later");
						needReset = true;
						break init;
					}

					Log.d("initZephyr", "connecting to[" + DeviceName + "] macId[" + BhMacID + "]");

					adapter.cancelDiscovery();
					_bt = new BTClient(adapter, BhMacID);
					if (_bt != null)
					{
						Log.d("initZephyr", "have BTClient...creating connected listener...");
						_NConnListener = new NewConnectedListener(Newhandler, Newhandler);
						_bt.addConnectedEventListener(_NConnListener);

						Log.d("initZephyr", "listener added...starting...");
						_bt.start();
						Log.d("initZephyr", "BTClient start on thread[" + _bt.getId() + "] connected[" + _bt.IsConnected() + "] alive[" + _bt.isAlive() + "]");
					}
					else
					{
						Log.d("initZephyr", "no BTClient!...clearing adapter");
						needReset = true;
						break init;
					}
				}
				catch (Throwable t)
				{
					Log.d("initZephyr", "connection flamed...clearing adapter");
					needReset = true;
					break init;
				}
			}
		}
		else
		{
			Date now = new Date();
			if ((now.getTime() - STALE_TIME) > getLastDataDate().getTime())
			{
				Log.d("initZephyr", "BTClient is stale...closing");
				doClose();
			}
		}

		if (needReset)
		{
			reset();
		}

		Log.d("initZephyr", "...exiting");
	}

	private class BTBondReceiver extends BroadcastReceiver
	{
		@Override
		public void onReceive(Context context, Intent intent)
		{
			Bundle b = intent.getExtras();
			BluetoothDevice device = adapter.getRemoteDevice(b.get("android.bluetooth.device.extra.DEVICE").toString());
			Log.d("BTBondReceiver::onReceive", "have device[" + device.getName() + "]");
		}
	}

	private class BTBroadcastReceiver extends BroadcastReceiver
	{
		@Override
		public void onReceive(Context context, Intent intent)
		{
			Bundle b = intent.getExtras();
			try
			{
				BluetoothDevice device = adapter.getRemoteDevice(b.get("android.bluetooth.device.extra.DEVICE").toString());
				Method m = BluetoothDevice.class.getMethod("convertPinToBytes", new Class[] { String.class });
				byte[] pin = (byte[]) m.invoke(device, "1234");
				m = device.getClass().getMethod("setPin", new Class[] { pin.getClass() });
				Object result = m.invoke(device, pin);
				Log.d("BTBroadcastReceiver::onReceive", "have device[" + device.getName() + "]");
			}
			catch (SecurityException e1)
			{
				e1.printStackTrace();
			}
			catch (NoSuchMethodException e1)
			{
				e1.printStackTrace();
			}
			catch (IllegalArgumentException e)
			{
				e.printStackTrace();
			}
			catch (IllegalAccessException e)
			{
				e.printStackTrace();
			}
			catch (InvocationTargetException e)
			{
				e.printStackTrace();
			}
		}
	}

	protected void mgrHeartbeat()
	{
		setLastDataDate(new Date()); // set manager heart beat :-)
	}

	final Handler Newhandler = new Handler()
	{
		public void handleMessage(Message msg)
		{
			if (!isEnabled())
				return;

			mgrHeartbeat();

			switch (MsgData.get(msg.what))
			{
				case HEART_RATE:
					String HeartRatetext = msg.getData().getString("HeartRate");
					Log.d("Newhandler::handlMessage", "heart rate on the wire[" + HeartRatetext + "]");
					int newHeartRate = Integer.parseInt(HeartRatetext);
					if (newHeartRate < 0)
					{
						newHeartRate *= -1;
					}

					if (newHeartRate != zHeartRate)
					{
						changes.firePropertyChange(ListenerData.HEART_RATE_CHANGE.getValue(), Integer.valueOf(zHeartRate), Integer.valueOf(newHeartRate));
						Log.d("Newhandler::handleMessage", "Heart Rate is[" + HeartRatetext + "]");
						zHeartRate = newHeartRate;
						setDirty(true);
					}
					break;

				case INSTANT_SPEED:
					String InstantSpeedtext = msg.getData().getString("InstantSpeed");
					double newInstantSpeed = Double.parseDouble(InstantSpeedtext);
					if (newInstantSpeed != zInstantSpeed)
					{
						changes.firePropertyChange(ListenerData.INSTANT_SPEED_CHANGE.getValue(), Double.valueOf(zInstantSpeed), Double.valueOf(newInstantSpeed));
						Log.d("Newhandler::handleMessage", "speed is[" + InstantSpeedtext + "]");
						zInstantSpeed = newInstantSpeed;

						changes.firePropertyChange(ListenerData.STATUS_CHANGE.getValue(), Boolean.valueOf(false), Boolean.valueOf(true));

						setDirty(true);
					}
					break;

				case BATTERY_CHARGE:
					String batteryCharge = msg.getData().getString("BatteryCharge");
					int newBatteryCharge = Integer.parseInt(batteryCharge);
					if (newBatteryCharge < (zBatteryCharge - 5) || newBatteryCharge > (zBatteryCharge + 5))
					{
						changes.firePropertyChange(ListenerData.BATTERY_CHARGE_CHANGE.getValue(), Integer.valueOf(zBatteryCharge), Integer.valueOf(newBatteryCharge));
						Log.d("Newhandler::handleMessage", "charge is[" + batteryCharge + "]");
						zBatteryCharge = newBatteryCharge;
						setDirty(true);
					}
					break;
				default:
					break;

			}
		}

	};

	protected BluetoothAdapter getAdapter()
	{
		return adapter;
	}

	protected synchronized void setAdapter(BluetoothAdapter adapter)
	{
		this.adapter = adapter;
	}

	protected BTClient get_bt()
	{
		return _bt;
	}

	protected synchronized void set_bt(BTClient _bt)
	{
		this._bt = _bt;
	}

	protected ZephyrProtocol get_protocol()
	{
		return _protocol;
	}

	protected void set_protocol(ZephyrProtocol _protocol)
	{
		this._protocol = _protocol;
	}

	protected NewConnectedListener get_NConnListener()
	{
		return _NConnListener;
	}

	protected void set_NConnListener(NewConnectedListener _NConnListener)
	{
		this._NConnListener = _NConnListener;
	}

	protected Context getMyContext()
	{
		return myContext;
	}

	protected synchronized void setMyContext(Context aContext)
	{
		myContext = aContext;
	}

	protected AppWidgetManager getMyWidgetMgr()
	{
		return myWidgetMgr;
	}

	protected void setMyWidgetMgr(AppWidgetManager myWidgetMgr)
	{
		this.myWidgetMgr = myWidgetMgr;
	}

	protected int getzHeartRate()
	{
		return zHeartRate;
	}

	protected synchronized void setzHeartRate(int heartRate)
	{
		zHeartRate = heartRate;
	}

	protected double getzInstantSpeed()
	{
		return zInstantSpeed;
	}

	protected synchronized void setzInstantSpeed(double instantSpeed)
	{
		zInstantSpeed = instantSpeed;
	}

	protected int getzBatteryCharge()
	{
		return zBatteryCharge;
	}

	protected synchronized void setzBatteryCharge(int batteryCharge)
	{
		zBatteryCharge = batteryCharge;
	}

	protected Handler getNewhandler()
	{
		return Newhandler;
	}

	protected boolean isEnabled()
	{
		return enabled;
	}

	/**
	 * set the state of updating
	 * 
	 * @param enabled
	 *            boolean - true/false flag
	 */
	protected void setEnabled(boolean enabled)
	{
		this.enabled = enabled;
	}

	/**
	 * convenience method to enable update
	 */
	protected void enable()
	{
		setEnabled(true);
	}

	/**
	 * convenience method to disable update
	 */
	protected void disable()
	{
		setEnabled(false);
	}

	protected static boolean isDirty()
	{
		return dirty;
	}

	protected static void setDirty(boolean dirty)
	{
		ZephyrHxmMgr.dirty = dirty;
	}

	protected Date getLastDataDate()
	{
		return lastDataDate;
	}

	protected void setLastDataDate(Date lastDataDate)
	{
		this.lastDataDate = lastDataDate;
	}

}

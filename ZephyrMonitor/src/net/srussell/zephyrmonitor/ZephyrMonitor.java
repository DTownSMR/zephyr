package net.srussell.zephyrmonitor;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Set;

import net.srussell.zephyrmonitor.util.SystemUiHider;
import zephyr.android.HxMBT.BTClient;
import zephyr.android.HxMBT.ZephyrProtocol;
import android.annotation.TargetApi;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;

/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 * 
 * @see SystemUiHider
 */
public class ZephyrMonitor extends Activity implements ZephyrHxM
{
	/*
	 * Zephyr variables
	 */
	/**
	 * non-null when connected
	 */
	BluetoothAdapter adapter = null;
	BTClient _bt = null;
	ZephyrProtocol _protocol = null;
	NewConnectedListener _NConnListener = null;

	/**
	 * Whether or not the system UI should be auto-hidden after
	 * {@link #AUTO_HIDE_DELAY_MILLIS} milliseconds.
	 */
	private static final boolean AUTO_HIDE = true;

	/**
	 * If {@link #AUTO_HIDE} is set, the number of milliseconds to wait after
	 * user interaction before hiding the system UI.
	 */
	private static final int AUTO_HIDE_DELAY_MILLIS = 3000;

	/**
	 * If set, will toggle the system UI visibility upon interaction. Otherwise,
	 * will show the system UI visibility upon interaction.
	 */
	private static final boolean TOGGLE_ON_CLICK = true;

	/**
	 * The flags to pass to {@link SystemUiHider#getInstance}.
	 */
	private static final int HIDER_FLAGS = SystemUiHider.FLAG_HIDE_NAVIGATION;

	/**
	 * The instance of the {@link SystemUiHider} for this activity.
	 */
	private SystemUiHider mSystemUiHider;

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		setContentView(R.layout.activity_zephyr_monitor);

		final View controlsView = findViewById(R.id.fullscreen_content_controls);
		final View contentView = findViewById(R.id.fullscreen_content);

		// Set up an instance of SystemUiHider to control the system UI for
		// this activity.
		mSystemUiHider = SystemUiHider.getInstance(this, contentView, HIDER_FLAGS);
		mSystemUiHider.setup();
		mSystemUiHider.setOnVisibilityChangeListener(new SystemUiHider.OnVisibilityChangeListener()
		{
			// Cached values.
			int mControlsHeight;
			int mShortAnimTime;

			@Override
			@TargetApi(Build.VERSION_CODES.HONEYCOMB_MR2)
			public void onVisibilityChange(boolean visible)
			{
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR2)
				{
					// If the ViewPropertyAnimator API is available
					// (Honeycomb MR2 and later), use it to animate the
					// in-layout UI controls at the bottom of the
					// screen.
					if (mControlsHeight == 0)
					{
						mControlsHeight = controlsView.getHeight();
					}
					if (mShortAnimTime == 0)
					{
						mShortAnimTime = getResources().getInteger(android.R.integer.config_shortAnimTime);
					}
					controlsView.animate().translationY(visible ? 0 : mControlsHeight).setDuration(mShortAnimTime);
				}
				else
				{
					// If the ViewPropertyAnimator APIs aren't
					// available, simply show or hide the in-layout UI
					// controls.
					controlsView.setVisibility(visible ? View.VISIBLE : View.GONE);
				}

				if (visible && AUTO_HIDE)
				{
					// Schedule a hide().
					delayedHide(AUTO_HIDE_DELAY_MILLIS);
				}

				if (adapter == null)
					initZephyr();
			}
		});

		// Set up the user interaction to manually show or hide the system UI.
		contentView.setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View view)
			{
				if (TOGGLE_ON_CLICK)
				{
					mSystemUiHider.toggle();
				}
				else
				{
					mSystemUiHider.show();
				}
			}
		});

		// Upon interacting with UI controls, delay any scheduled hide()
		// operations to prevent the jarring behavior of controls going away
		// while interacting with the UI.
		findViewById(R.id.dummy_button).setOnTouchListener(mDelayHideTouchListener);

	}

	@Override
	protected void onPostCreate(Bundle savedInstanceState)
	{
		super.onPostCreate(savedInstanceState);

		// OK to init zephyr now
		initZephyr();

		// Trigger the initial hide() shortly after the activity has been
		// created, to briefly hint to the user that UI controls
		// are available.
		delayedHide(100);
	}

	@Override
	protected void onDestroy()
	{
		super.onDestroy();

		try
		{
			if (adapter != null)
			{
				_bt.Close();
				adapter = null;
			}
		}
		catch (Throwable t)
		{
			log(t.getLocalizedMessage());
		}
	}

	/**
	 * Touch listener to use for in-layout UI controls to delay hiding the
	 * system UI. This is to prevent the jarring behavior of controls going away
	 * while interacting with activity UI.
	 */
	View.OnTouchListener mDelayHideTouchListener = new View.OnTouchListener()
	{
		@Override
		public boolean onTouch(View view, MotionEvent motionEvent)
		{
			if (AUTO_HIDE)
			{
				delayedHide(AUTO_HIDE_DELAY_MILLIS);
			}
			return false;
		}
	};

	Handler mHideHandler = new Handler();
	Runnable mHideRunnable = new Runnable()
	{
		@Override
		public void run()
		{
			mSystemUiHider.hide();
		}
	};

	/**
	 * Schedules a call to hide() in [delay] milliseconds, canceling any
	 * previously scheduled calls.
	 */
	private void delayedHide(int delayMillis)
	{
		mHideHandler.removeCallbacks(mHideRunnable);
		mHideHandler.postDelayed(mHideRunnable, delayMillis);
	}

	private void initZephyr()
	{
		log("in initZephyr...");

		try
		{
			/*
			 * Sending a message to android that we are going to initiate a
			 * pairing request
			 */
			IntentFilter filter = new IntentFilter("android.bluetooth.device.action.PAIRING_REQUEST");
			/*
			 * Registering a new BTBroadcast receiver from the Main Activity
			 * context with pairing request event
			 */
			this.getApplicationContext().registerReceiver(new BTBroadcastReceiver(), filter);
			// Registering the BTBondReceiver in the application that the status of
			// the receiver has changed to Paired
			IntentFilter filter2 = new IntentFilter("android.bluetooth.device.action.BOND_STATE_CHANGED");
			this.getApplicationContext().registerReceiver(new BTBondReceiver(), filter2);
		}
		catch (Throwable t)
		{
			log("oh snap!");
			log(t.getLocalizedMessage());
			return;
		}

		log("setting status...");
		// Obtaining the handle to act on the CONNECT button
		TextView ts = (TextView) findViewById(R.id.status);
		ts.setText("Not Connected to HxM ! !");

		log("checking if we have adapter set yet...");
		if (adapter == null)
		{
			TextView tv = (TextView) findViewById(R.id.heartRate);
			tv.setText("---");

			TextView tv1 = (TextView) findViewById(R.id.speed);
			tv1.setText("---");

			try
			{
				log("connecting...");
				adapter = connect(ts);
				log("connected!");
				tv.setText("000");
				tv1.setText("0.0");
			}
			catch (Throwable t)
			{
				log(t.getLocalizedMessage());
				return;
			}

		}
	}

	/**
	 * handle connecting to the HxM
	 * 
	 * @param ts
	 *            TextView - a TextView widget to place an updated status
	 *            message
	 */
	private BluetoothAdapter connect(TextView ts)
	{
		log("in connect...");
		String BhMacID = "00:00:00:00:00:00";
		BluetoothAdapter myAdapter = BluetoothAdapter.getDefaultAdapter();

		Set<BluetoothDevice> pairedDevices = myAdapter.getBondedDevices();

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

		BluetoothDevice Device = myAdapter.getRemoteDevice(BhMacID);
		String DeviceName = Device.getName();
		_bt = new BTClient(myAdapter, BhMacID);
		_NConnListener = new NewConnectedListener(Newhandler, Newhandler);
		_bt.addConnectedEventListener(_NConnListener);

		if (_bt.IsConnected())
		{
			_bt.start();
			String ErrorText = "Connected to HxM " + DeviceName;
			ts.setText(ErrorText);
		}
		else
		{
			String ErrorText = "Unable to Connect !";
			ts.setText(ErrorText);

			myAdapter = null;
		}

		log("returning adapter[" + myAdapter + "]");
		return myAdapter;
	}

	private class BTBondReceiver extends BroadcastReceiver
	{
		@Override
		public void onReceive(Context context, Intent intent)
		{
			Bundle b = intent.getExtras();
			BluetoothDevice device = adapter.getRemoteDevice(b.get("android.bluetooth.device.extra.DEVICE").toString());
			Log.d("Bond state", "BOND_STATED = " + device.getBondState());
		}
	}

	private class BTBroadcastReceiver extends BroadcastReceiver
	{
		@Override
		public void onReceive(Context context, Intent intent)
		{
			Log.d("BTIntent", intent.getAction());
			Bundle b = intent.getExtras();
			Log.d("BTIntent", b.get("android.bluetooth.device.extra.DEVICE").toString());
			Log.d("BTIntent", b.get("android.bluetooth.device.extra.PAIRING_VARIANT").toString());
			try
			{
				BluetoothDevice device = adapter.getRemoteDevice(b.get("android.bluetooth.device.extra.DEVICE").toString());
				Method m = BluetoothDevice.class.getMethod("convertPinToBytes", new Class[] { String.class });
				byte[] pin = (byte[]) m.invoke(device, "1234");
				m = device.getClass().getMethod("setPin", new Class[] { pin.getClass() });
				Object result = m.invoke(device, pin);
				Log.d("BTTest", result.toString());
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

	final Handler Newhandler = new Handler()
	{
		public void handleMessage(Message msg)
		{
			log("handling msg...");
			TextView tv;
			switch (MsgData.get(msg.what))
			{
				case HEART_RATE:
					String heartRatetext = msg.getData().getString("HeartRate");
					tv = (TextView) findViewById(R.id.heartRate);
					System.out.println("Heart Rate Info is " + heartRatetext);
					if (tv != null)
						tv.setText(heartRatetext);
					break;

				case INSTANT_SPEED:
					String instantSpeedtext = msg.getData().getString("InstantSpeed");
					tv = (TextView) findViewById(R.id.speed);
					if (tv != null)
						tv.setText(instantSpeedtext);
					break;

				case BATTERY_CHARGE:
					String batteryCharge = msg.getData().getString("BatteryCharge");
					tv = (TextView) findViewById(R.id.batteryCharge);
					if (tv != null)
						tv.setText(batteryCharge);
					break;
			}
		}

	};

	public static void log(String msg)
	{
		if (msg != null)
			System.out.println(msg);
	}

}

package net.srussell.zephyrmonitor;

import android.app.Activity;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.widget.TextView;
import zephyr.android.HxMBT.*;

public class NewConnectedListener extends ConnectListenerImpl implements ZephyrHxM
{
	private Handler _OldHandler;
	private Handler _aNewHandler;
	private int GP_MSG_ID = 0x20;
	private int GP_HANDLER_ID = 0x20;
	private int HR_SPD_DIST_PACKET = 0x26;

	private HRSpeedDistPacketInfo HRSpeedDistPacket = new HRSpeedDistPacketInfo();

	public NewConnectedListener(Handler handler, Handler _NewHandler)
	{
		super(handler, null);
		_OldHandler = handler;
		_aNewHandler = _NewHandler;
	}

	public void Connected(ConnectedEvent<BTClient> eventArgs)
	{
		System.out.println(String.format("Connected to BioHarness %s.", eventArgs.getSource().getDevice().getName()));

		// Creates a new ZephyrProtocol object and passes it the BTComms object
		ZephyrProtocol _protocol = new ZephyrProtocol(eventArgs.getSource().getComms());
		// ZephyrProtocol _protocol = new
		// ZephyrProtocol(eventArgs.getSource().getComms(), );
		
		_protocol.addZephyrPacketEventListener(new ZephyrPacketListener()
		{
			public void ReceivedPacket(ZephyrPacketEvent eventArgs)
			{
				ZephyrPacketArgs msg = eventArgs.getPacket();
				byte CRCFailStatus;
				byte RcvdBytes;

				CRCFailStatus = msg.getCRCStatus();
				RcvdBytes = msg.getNumRvcdBytes();
				if (HR_SPD_DIST_PACKET == msg.getMsgID())
				{

					byte[] DataArray = msg.getBytes();

					/*
					 * pull heart rate
					 */
					int heartRate = HRSpeedDistPacket.GetHeartRate(DataArray);
					Message text1 = _aNewHandler.obtainMessage(MsgData.HEART_RATE.getValue());
					Bundle b1 = new Bundle();
					b1.putString("HeartRate", String.valueOf(heartRate));
					text1.setData(b1);
					_aNewHandler.sendMessage(text1);
					System.out.println("Heart Rate is " + heartRate);

					/*
					 * pull speed
					 */
					double instantSpeed = HRSpeedDistPacket.GetInstantSpeed(DataArray);
					text1 = _aNewHandler.obtainMessage(MsgData.INSTANT_SPEED.getValue());
					b1.putString("InstantSpeed", String.valueOf(instantSpeed));
					text1.setData(b1);
					_aNewHandler.sendMessage(text1);
					System.out.println("Instant Speed is " + instantSpeed);

					/*
					 * pull battery
					 */
					int batteryCharge = HRSpeedDistPacket.GetBatteryChargeInd(DataArray);
					text1 = _aNewHandler.obtainMessage(MsgData.BATTERY_CHARGE.getValue());
					b1.putString("BatteryCharge", String.valueOf(batteryCharge));
					text1.setData(b1);
					_aNewHandler.sendMessage(text1);
					System.out.println("Battery Charge is " + batteryCharge);

				}
			}
		});
	}

}
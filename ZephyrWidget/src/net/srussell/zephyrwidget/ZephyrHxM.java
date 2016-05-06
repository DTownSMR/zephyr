package net.srussell.zephyrwidget;

/**
 * http://www.srussell.net
 * 
 * (c) Copyright Scott M. Russell. 2013, All rights reserved.
 */

/**
 * 
 * interface for a ZephyrHxM heart rate monitor
 * 
 * @author Scott Russell
 */
public interface ZephyrHxM
{

	/**
	 * enum of standard HxM Data Message bits. The enum value for each bit is
	 * taken from it's offset in the STX packet.
	 */
	public enum MsgData
	{
		BATTERY_CHARGE(11), HEART_RATE(12), DISTANCE(50), INSTANT_SPEED(52), STRIDES(54);

		private final int value;

		private MsgData(int value)
		{
			this.value = value;
		}

		public int getValue()
		{
			return value;
		}

		public static MsgData get(int x)
		{
			for (MsgData md : MsgData.values())
			{
				if (md.value == x)
					return md;
			}
			throw new IllegalArgumentException("invalid MsgData");
		}
	}

	/**
	 * enum of listener value bits
	 */
	public enum ListenerData
	{
		HEART_RATE_CHANGE("heartRate"), BATTERY_CHARGE_CHANGE("batteryCharge"), DISTANCE_CHANGE("distance"), INSTANT_SPEED_CHANGE("speed"), STRIDES_CHANGE("strides"), STATUS_CHANGE("status");

		private final String value;

		private ListenerData(String value)
		{
			this.value = value;
		}

		public String getValue()
		{
			return value;
		}

		public static ListenerData get(String x)
		{
			for (ListenerData ld : ListenerData.values())
			{
				if (x.trim().compareTo(ld.value) == 0)
					return ld;
			}
			throw new IllegalArgumentException("invalid ListenerData");
		}
	}

}
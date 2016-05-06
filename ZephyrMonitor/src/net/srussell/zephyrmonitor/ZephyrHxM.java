package net.srussell.zephyrmonitor;

public interface ZephyrHxM
{
	/**
	 * enum of standard HxM Data Message bits. The enum value for each bit is taken from it's offset in the STX packet.
	 *
	 */
	public enum MsgData
	{
		BATTERY_CHARGE(11),
		HEART_RATE(12),
		DISTANCE(50),
		INSTANT_SPEED(52),
		STRIDES(54);
		
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
			for( MsgData md: MsgData.values() )
			{
				if(md.value == x ) return md;
			}
			throw new IllegalArgumentException("invalid MsgData");
		}
	}
	
	
	
}
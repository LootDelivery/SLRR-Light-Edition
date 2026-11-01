package java.game.cars;

import java.game.parts.rgearpart.reciprocatingrgearpart.*;

public class Manta_rim_F_TM extends Wheel
{
	public Manta_rim_F_TM ( int id )
	{
		name = "Opel Manta 400 front tarmac rim 5.0x15 ET+20";

		rim_type = rtRACE;
		SetupWheel( 15.0, 5.0, 0.0);

		// cause SetupWheel isn't working properly
		setSlotPos( 1, new Vector3(0.020, 0, 0), null );
	}
}

package java.game.cars;

import java.game.parts.rgearpart.reciprocatingrgearpart.*;

public class Manta_rim_F_MD extends Wheel
{
	public Manta_rim_F_MD ( int id )
	{
		name = "Opel Manta 400 front mud rim 4.0x14 ET+20";

		rim_type = rtRACE;
		SetupWheel( 14.0, 4.0, 0.0);

		// cause SetupWheel isn't working properly
		setSlotPos( 1, new Vector3(0.020, 0, 0), null );
	}
}

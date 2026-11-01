package java.game.cars;

import java.game.parts.rgearpart.reciprocatingrgearpart.*;

public class Manta_rim_R_MD extends Wheel
{
	public Manta_rim_R_MD ( int id )
	{
		name = "Opel Manta 400 rear mud rim 5.0x14 ET+10";

		rim_type = rtRACE;
		SetupWheel( 14.0, 5.0, 10.0);

		// cause SetupWheel isn't working properly
		setSlotPos( 1, new Vector3(0.010, 0, 0), null );
	}
}

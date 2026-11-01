package java.game.cars;

import java.game.parts.rgearpart.reciprocatingrgearpart.*;

public class Manta_rim_R_SN extends Wheel
{
	public Manta_rim_R_SN ( int id )
	{
		name = "Opel Manta 400 rear snow rim 5.0x14 ET+10";

		rim_type = rtRACE;
		SetupWheel( 14.0, 5.0, 0.0);

		// cause SetupWheel isn't working properly
		setSlotPos( 1, new Vector3(0.020, 0, 0), null );
	}
}

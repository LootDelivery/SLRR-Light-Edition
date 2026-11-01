package java.game.cars;

import java.game.parts.rgearpart.reciprocatingrgearpart.*;

public class Manta_rim_R_TM extends Wheel
{
	public Manta_rim_R_TM ( int id )
	{
		name = "Opel Manta 400 rear tarmac rim 8.0x15 ET+10";

		rim_type = rtRACE;
		SetupWheel( 15.0, 8.0, 0.0);

		// cause SetupWheel isn't working properly
		setSlotPos( 1, new Vector3(0.010, 0, 0), null );
	}
}

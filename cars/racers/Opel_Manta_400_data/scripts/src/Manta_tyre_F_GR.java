package java.game.cars;

import java.game.parts.rgearpart.reciprocatingrgearpart.*;

public class Manta_tyre_F_GR extends Tyre
{
	public Manta_tyre_F_GR ( int id )
	{
		name_prefix = "Opel Manta 400 front gravel tyre";
		tyre_size_label = "155/50 R15";

		render_types[ 0] = 0;		// for  5.0 inch wide rims //
		render_types[ 1] = 0;		// for  5.5 inch wide rims //
		render_types[ 2] = 0;		// for  6.0 inch wide rims //
		render_types[ 3] = 0;		// for  6.5 inch wide rims //
		render_types[ 4] = 0;		// for  7.0 inch wide rims //
		render_types[ 5] = 0;		// for  7.5 inch wide rims //
		render_types[ 6] = 0;		// for  8.0 inch wide rims //
		render_types[ 7] = 0;		// for  8.5 inch wide rims //
		render_types[ 8] = 0;		// for  9.0 inch wide rims //
		render_types[ 9] = 0;		// for  9.5 inch wide rims //
		render_types[10] = 0;		// for 10.0 inch wide rims //
		render_types[11] = 0;		// for 10.5 inch wide rims //
		render_types[12] = 0;		// for 11.0 inch wide rims //
		render_types[13] = 0;		// for 11.5 inch wide rims //
		render_types[14] = 0;		// for 12.0 inch wide rims //
		render_types[15] = 0;		// for 12.5 inch wide rims //
		render_types[16] = 0;		// for 13.0 inch wide rims //
		render_types[17] = 0;		// for 13.5 inch wide rims //
		render_types[18] = 0;		// for 14.0 inch wide rims //
		render_types[19] = 0;		// for 14.5 inch wide rims //
		render_types[20] = 0;		// for 15.0 inch wide rims //
		render_types[21] = cars.racers.Opel_Manta_400:0x00000094r;	// this rendertype as default //

		SetupTyre( 155.0, 50.0, 15.0, 5.0, tcSUPERSPORT, 2.0 );

		calcStuffs(); minRimWidth = maxRimWidth = 5.0;
		name = name_prefix + " " + tyre_size_label;
	}

	public void updatevariables()
	{
		Wheel rim = partOnSlot( 1 );

		if( rim )
		{
			if( rim.getWheelID() % 2 == 0 )
				setRenderType( cars.racers.Opel_Manta_400:0x00000094r ); // left side
			else
				setRenderType( cars.racers.Opel_Manta_400:0x00000098r ); // right side

			SetupTyre( 155.0, 50.0, 15.0, rim.rim_width, tcSUPERSPORT );

			super.updatevariables();

			name = name_prefix + " " + tyre_size_label;
		}
	}
}

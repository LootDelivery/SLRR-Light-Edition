package java.game.cars;

import java.util.*;
import java.util.resource.*;
import java.game.parts.*;
import java.game.parts.bodypart.*;


public class Manta_F_bumper extends Bumper
{
	public Manta_F_bumper( int id )
	{
		super( id );
		carCategory = PACKAGE;

		name = "Opel Manta 400 front bumper";
		description = "";

		value = tHUF2USD(94.903);
		brand_new_prestige_value = 27.43;
	}

	public void addStockParts( Descriptor desc )
	{
		super.addStockParts( desc );

		Descriptor d = new Descriptor( cars.racers.Opel_Manta_400:0x0000004Er, desc.optical, desc.power, desc.wear, desc.tear );
		addPart( cars.racers.Opel_Manta_400:0x000000B1r, "F numberplate", d );
	}
}

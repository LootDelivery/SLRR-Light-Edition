package java.game.cars;

import java.util.*;
import java.util.resource.*;
import java.game.parts.enginepart.*;


public class Manta_universal_exhaust_pipe extends ExhaustPipe
{
	public Manta_universal_exhaust_pipe( int id )
	{
		super( id );
		carCategory = PACKAGE;
		name = "Opel Manta 400 universal muffler exhaust pipe";

		description = "";

		value = tHUF2USD(185.000);
		brand_new_prestige_value = 45.00;
		setMaxWear(kmToMaxWear(500000.0));

		mufflerSlotIDList = new Vector();
		mufflerSlotIDList.addElement( new Integer(2) );
	}
}

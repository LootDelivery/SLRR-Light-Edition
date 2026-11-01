package java.game.cars;

import java.util.*;
import java.util.resource.*;
import java.game.parts.bodypart.*;


public class Manta_FL_door extends FrontDoor
{
	public Manta_FL_door( int id )
	{
		super( id );
		carCategory = PACKAGE;

		name = "Opel Manta 400 driver's door";
		description = "";

		brand_new_prestige_value = 34.29;
		value = tHUF2USD(123.374);
	}
}

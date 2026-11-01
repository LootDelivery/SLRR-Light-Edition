package java.game.cars;

import java.util.*;
import java.util.resource.*;
import java.game.parts.bodypart.*;


public class Manta_FR_door extends FrontDoor
{
	public Manta_FR_door( int id )
	{
		super( id );
		carCategory = PACKAGE;

		name = "Opel Manta 400 passenger's door";
		description = "";

		brand_new_prestige_value = 34.29;
 		value = tHUF2USD(123.374);
	}
}

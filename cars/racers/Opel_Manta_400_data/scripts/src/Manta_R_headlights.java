package java.game.cars;

import java.util.*;
import java.util.resource.*;
import java.game.parts.bodypart.*;


public class Manta_R_headlights extends Headlights
{
	public Manta_R_headlights( int id )
	{
		super( id );
		carCategory = PACKAGE;
		name = "Opel Manta 400 right headlights";

		description = "";

 		value = tHUF2USD(37.961);
		brand_new_prestige_value = 34.29;
	}
}

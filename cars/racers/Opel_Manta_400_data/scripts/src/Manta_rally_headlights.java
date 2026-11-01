package java.game.cars;

import java.util.*;
import java.util.resource.*;
import java.game.parts.bodypart.*;


public class Manta_rally_headlights extends Headlights
{
	public Manta_rally_headlights( int id )
	{
		super( id );
		carCategory = PACKAGE;
		name = "Opel Manta 400 rally headlights";
		description = "";

		value = tHUF2USD(213.000);
		brand_new_prestige_value = 70.00;
	}
}

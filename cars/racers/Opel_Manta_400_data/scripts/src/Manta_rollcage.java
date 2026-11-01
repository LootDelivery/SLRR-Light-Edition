package java.game.cars;

import java.util.*;
import java.util.resource.*;
import java.game.parts.bodypart.*;


public class Manta_rollcage extends RollCage
{
	public Manta_rollcage( int id )
	{
		super( id );
		carCategory = PACKAGE;
		name = "Opel Manta 400 rollcage";
		description = "";
		brand_new_prestige_value = 27.43;

 		value = tHUF2USD(94.903);
	}
}

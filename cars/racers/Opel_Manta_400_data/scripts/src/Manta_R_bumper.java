package java.game.cars;

import java.util.*;
import java.util.resource.*;
import java.game.parts.bodypart.*;


public class Manta_R_bumper extends Bumper
{
	public Manta_R_bumper( int id )
	{
		super( id );
		carCategory = PACKAGE;
		name = "Opel Manta 400 rear bumper";
		description = "";
		brand_new_prestige_value = 27.43;

 		value = tHUF2USD(85.413);
	}
}

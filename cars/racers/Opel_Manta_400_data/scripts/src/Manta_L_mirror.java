package java.game.cars;

import java.util.*;
import java.util.resource.*;
import java.game.parts.bodypart.*;


public class Manta_L_mirror extends Mirror
{
	public Manta_L_mirror( int id )
	{
		super( id );
		carCategory = PACKAGE;

		name = "Opel Manta 400 left mirror";
		description = "";

		brand_new_prestige_value = 24.29;
 		value = tHUF2USD(27.452);
	}
}

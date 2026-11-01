package java.game.cars;

import java.util.*;
import java.util.resource.*;
import java.game.parts.bodypart.*;


public class Manta_trunk extends Trunk
{
	public Manta_trunk( int id )
	{
		super( id );
		carCategory = PACKAGE;
		name = "Opel Manta 400 trunk";
		description = "";
		brand_new_prestige_value = 27.43;

		value = tHUF2USD(132.864);
	}
}

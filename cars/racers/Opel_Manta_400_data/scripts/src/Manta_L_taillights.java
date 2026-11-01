package java.game.cars;

import java.util.*;
import java.util.resource.*;
import java.game.parts.bodypart.*;


public class Manta_L_taillights extends Taillights
{
	public Manta_L_taillights( int id )
	{
		super( id );
		carCategory = PACKAGE;
		name = "Opel Manta 400 left taillights";
		description = "";
		brand_new_prestige_value = 34.29;

		value = tHUF2USD(37.961);
	}
}

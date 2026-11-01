package java.game.cars;

import java.util.*;
import java.util.resource.*;
import java.game.parts.bodypart.*;


public class Manta_F_windshield extends Windshield
{
	public Manta_F_windshield( int id )
	{
		super( id );
		carCategory = PACKAGE;

		name = "Opel Manta 400 front windshield";
		description = "";

		brand_new_prestige_value = 34.29;
		value = tHUF2USD(227.767);
	}
}

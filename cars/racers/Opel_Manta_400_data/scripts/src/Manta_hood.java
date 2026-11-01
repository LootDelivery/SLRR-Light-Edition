package java.game.cars;

import java.util.*;
import java.util.resource.*;
import java.game.parts.bodypart.*;


public class Manta_hood extends Hood
{
	public Manta_hood( int id )
	{
		super( id );
		carCategory = PACKAGE;
		name = "Opel Manta 400 hood";
		description = "";
		brand_new_prestige_value = 27.43;

 		value = tHUF2USD(341.651);
	}
}

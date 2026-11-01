package java.game.cars;

import java.util.*;
import java.util.resource.*;
import java.game.parts.bodypart.*;


public class Manta_FR_seat extends FrontSeat
{
	public Manta_FR_seat( int id )
	{
		super( id );
		carCategory = COMMON;
		name = "Opel Manta 400 passenger's seat";
		description = "";
		brand_new_prestige_value = 34.29;

 		value = tHUF2USD(94.903);
	}
}

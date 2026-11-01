package java.game.cars;

import java.util.*;
import java.util.resource.*;
import java.game.parts.bodypart.*;


public class Manta_trunk_locks extends DecorativeBodyPart
{
	public Manta_trunk_locks( int id )
	{
		super( id );
		carCategory = PACKAGE;

		name = "Opel Manta 400 trunk locks";
		description = "";

		brand_new_prestige_value = 27.43;
		value = tHUF2USD(18.981);
	}
}

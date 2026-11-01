package java.game.cars;

import java.util.*;
import java.util.resource.*;
import java.game.parts.bodypart.*;


public class Manta_R_numberplate extends DecorativeBodyPart
{
	public Manta_R_numberplate( int id )
	{
		super( id );
		carCategory = PACKAGE;

		name = "Opel Manta 400 rear numberplate [paintable]";
		description = "";

		brand_new_prestige_value = 14.29;
		value = tHUF2USD(12.337);
	}
}

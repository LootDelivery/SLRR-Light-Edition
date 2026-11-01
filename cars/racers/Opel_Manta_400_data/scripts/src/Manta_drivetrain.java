package java.game.cars;

import java.util.*;
import java.util.resource.*;
import java.game.parts.bodypart.*;


public class Manta_drivetrain extends DecorativeBodyPart
{
	public Manta_drivetrain( int id )
	{
		super( id );
		carCategory = PACKAGE;

		name = "Opel Manta 400 drivetrain";
		description = "";

		brand_new_prestige_value = 27.43;
		value = tHUF2USD(237.258);
	}
}

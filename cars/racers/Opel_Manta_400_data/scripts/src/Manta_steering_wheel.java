package java.game.cars;

import java.util.*;
import java.util.resource.*;
import java.game.parts.bodypart.*;


public class Manta_steering_wheel extends SteeringWheel
{
	public Manta_steering_wheel( int id )
	{
		super( id );
		carCategory = COMMON;
		name = "Opel Manta 400 steering wheel";
		diameter = 380.0;
		hub_depth = 3.2;
		description = "";
		brand_new_prestige_value = 34.29;

		value = tHUF2USD(37.961);
	}
}

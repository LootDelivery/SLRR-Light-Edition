package java.game.cars;

import java.util.*;
import java.util.resource.*;
import java.game.parts.bodypart.*;


public class Manta_RL_window extends Window
{
	public Manta_RL_window( int id )
	{
		super( id );
		carCategory = PACKAGE;
		name = "Opel Manta 400 rear left window";
		description = "";
		brand_new_prestige_value = 27.43;

		value = tHUF2USD(37.961);
	}
}

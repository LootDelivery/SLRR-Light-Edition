package java.game.parts.mufflers;

import java.game.parts.enginepart.*;

public class muffler_02 extends ExhaustTip
{
	public muffler_02( int id )
	{
		super( id );
		carCategory = COMMON;
		name = "Tsurikawa Muffler 3";

		value = tHUF2USD(30.000);
		brand_new_prestige_value = 40.00;
		setMaxWear(kmToMaxWear(500000.0));
	}
}

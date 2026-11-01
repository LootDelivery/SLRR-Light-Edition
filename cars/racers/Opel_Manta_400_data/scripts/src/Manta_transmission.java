package java.game.cars;

import java.util.*;
import java.util.resource.*;
import java.game.parts.enginepart.slidingenginepart.reciprocatingenginepart.*;


public class Manta_transmission extends Transmission
{
	public Manta_transmission( int id )
	{
		super( id );
		name = "Opel Manta 400 transmission";
		description = "";

		value = tHUF2USD(800);
		brand_new_prestige_value = 114.96;
		setMaxWear(kmToMaxWear(720000.0));

		gears		= 5;
		ratio[0]	= 0;
		ratio[1]	= 3.6;
		ratio[2]	= 1.9;
		ratio[3]	= 1.4;
		ratio[4]	= 1.0;
		ratio[5]	= 0.8;
		ratio[6]	= -3.5;
		ratio[7]	= -3.5;
		end_ratio	= 3.73;
		starter_torque	= 40.0;

		adjustable_gears = 1+2+4; // forward gears //

		amount_of_oil_needed = 0.0; // internally lubricated //
		best_lubrication_sliction=1.0;
		worst_lubrication_sliction=0.9;

		maxRPM = 14000.0;
		inertia = kgToInertia(6.5);

		check4warnings();

		drive_type = DT_RWD;
		drive_front_min = 0.00;
		drive_front_max = 0.50;
		def_drive_front = drive_front_min;
		drive_front = def_drive_front;

		diff_lock_min = 0.15;
		diff_lock_max = 1.00;
		def_diff_lock = diff_lock_min;
		diff_lock = def_diff_lock;
	}
}

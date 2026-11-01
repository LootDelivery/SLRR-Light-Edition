package java.game.cars;

import java.util.*;
import java.game.*;
import java.game.parts.*;
import java.game.parts.enginepart.airfueldeliverysystem.*;


public class Opel_Manta_400 extends Opel_Manta_400_models
{
	public Opel_Manta_400( int id )
	{
		super( id );
		carCategory = PACKAGE;

		makerName = "Adam Opel AG";
		model = MODEL_MANTA_400;
		vehicleName = "Opel Manta 400";
		name = "Opel Manta 400 chassis";
		description = "In 1979, Opel collaborated with German tuner Irmscher and Cosworth in UK to create the Opel Manta 400. Irmscher designed the exterior and interior of the Manta 400. Cosworth built a 2.4-litre engine that was mounted with the 16-valve head to provide massive output, and the opportunity to make several tune-ups for the rally drivers.";

		value = mHUF2USD(6.0);
		brand_new_prestige_value = 80.0;

		fully_stripped_drag = 0.55;
		brake_balance_can_be_set = 1;

		exhaustSlotIDList = new Vector();
		exhaustSlotIDList.addElement( new Integer(39) );
	}

	public void addStockParts( Descriptor desc )
	{
		// stock 1 stuffs //

		stock_parts_list_E  = new int[23];
		stock_parts_list_E[ 0] = parts.engines.Einvagen_Duhen_Ishima_Focer:0x000000DFr; // "Ishima_IshiBox_block" //
		stock_parts_list_E[ 1] = parts.engines.Einvagen_Duhen_Ishima_Focer:0x000000E0r; // "Ishima_90er_crankshaft" //
		stock_parts_list_E[ 2] = parts.engines.Einvagen_Duhen_Ishima_Focer:0x000000E3r; // "Ishima_129er_connecting_rods" //
		stock_parts_list_E[ 3] = parts.engines.Einvagen_Duhen_Ishima_Focer:0x000000E5r; // "Ishima_93er_HC_pistons" //
		stock_parts_list_E[ 4] = parts.engines.Einvagen_Duhen_Ishima_Focer:0x000000E9r; // "Ishima_WR_head_cylinder_head" //
		stock_parts_list_E[ 5] = parts.engines.Einvagen_Duhen_Ishima_Focer:0x000000EAr; // "Ishima_exhaust_camshaft" //
		stock_parts_list_E[ 6] = parts.engines.Einvagen_Duhen_Ishima_Focer:0x000000EBr; // "Ishima_intake_camshaft" //
		stock_parts_list_E[ 7] = parts.engines.Einvagen_Duhen_Ishima_Focer:0x000000ECr; // "Ishima_camshaft_bearing_bridge" //
		stock_parts_list_E[ 8] = parts.engines.Einvagen_Duhen_Ishima_Focer:0x000000E8r; // "Ishima_crankshaft_bearing_bridge" //
		stock_parts_list_E[ 9] = parts.engines.Einvagen_Duhen_Ishima_Focer:0x000000E6r; // "Ishima_oil_pan" //
		stock_parts_list_E[10] = parts.engines.Einvagen_Duhen_Ishima_Focer:0x000000EFr; // "Ishima_WR_SuperTurizmo_cylinder_head_cover" //
		stock_parts_list_E[11] = parts.engines.Einvagen_Duhen_Ishima_Focer:0x00000109r; // "Shimutshibu_RC_HIB535T4_intake_manifold" //
		stock_parts_list_E[12] = parts.engines.Einvagen_Duhen_Ishima_Focer:0x0000010Br; // "Shimutshibu_RC_IR4P4SI_fuel_rail" //
		stock_parts_list_E[13] = parts.engines.Einvagen_Duhen_Ishima_Focer:0x0000010Ar; // "Shimutshibu_RC_23W44EX_exhaust_header" //
		stock_parts_list_E[14] = parts.engines.Einvagen_Duhen_Ishima_Focer:0x0000010Cr; // "Shimutshibu_RC_MWFCR22W12_turbocharger" //
		stock_parts_list_E[15] = parts.engines.Einvagen_Duhen_Ishima_Focer:0x000000E7r; // "Ishima_alternator" //
		stock_parts_list_E[16] = parts.engines.Baiern_Emer:0x00000045r; // "Baiern_Emer_flywheel" //
		stock_parts_list_E[17] = parts.engines.Baiern_Emer:0x00000043r; // "Baiern_Emer_clutch" //
		stock_parts_list_E[18] = cars.racers.Opel_Manta_400:0x0000010Ar; // "5spd transmission" //
		stock_parts_list_E[19] = parts.engines.Einvagen_Duhen_Ishima_Focer:0x000000F0r; // "Ishima_camshaft_drive_belt" //
		stock_parts_list_E[20] = parts.engines.Einvagen_Duhen_Ishima_Focer:0x00000054r; // "turbocharger_air_out_pipe" //
		stock_parts_list_E[21] = parts.engines.Einvagen_Duhen_Ishima_Focer:0x00000055r; // "turbocharger_exhaust_pipe" //
		stock_parts_list_E[22] = parts:0x000053FFr; // "stock battery" //

		stock_parts_list_FL = new int[2];
		stock_parts_list_FL[ 0] = cars.racers.Opel_Manta_400:0x000000A3r; // "L headlights" //
		stock_parts_list_FL[ 1] = cars.racers.Opel_Manta_400:0x000000AEr; // "FL quarterpanel" //

		stock_parts_list_FR = new int[2];
		stock_parts_list_FR[ 0] = cars.racers.Opel_Manta_400:0x000000BAr; // "R headlights" //
		stock_parts_list_FR[ 1] = cars.racers.Opel_Manta_400:0x000000B8r; // "FR quarterpanel" //

		stock_parts_list_RL = new int[2];
		stock_parts_list_RL[ 0] = cars.racers.Opel_Manta_400:0x000000A5r; // "L taillights" //
		stock_parts_list_RL[ 1] = cars.racers.Opel_Manta_400:0x000000B4r; // "RL window" //

		stock_parts_list_RR = new int[2];
		stock_parts_list_RR[ 0] = cars.racers.Opel_Manta_400:0x000000BBr; // "R taillights" //
		stock_parts_list_RR[ 1] = cars.racers.Opel_Manta_400:0x000000B5r; // "RR window" //

		stock_parts_list_F  = new int[4];
		stock_parts_list_F[ 0] = cars.racers.Opel_Manta_400:0x000000AFr; // "F bumper" //
		stock_parts_list_F[ 1] = cars.racers.Opel_Manta_400:0x000000C1r; // "F grill" //
		stock_parts_list_F[ 2] = cars.racers.Opel_Manta_400:0x000000A4r; // "hood" //
		stock_parts_list_F[ 3] = cars.racers.Opel_Manta_400:0x000000BDr; // "F windshield" //

		stock_parts_list_Rr = new int[7];
		stock_parts_list_Rr[ 0] = cars.racers.Opel_Manta_400:0x000000B3r; // "R bumper" //
		stock_parts_list_Rr[ 1] = cars.racers.Opel_Manta_400:0x000000A7r; // "R windshield" //
		stock_parts_list_Rr[ 2] = cars.racers.Opel_Manta_400:0x000000A6r; // "trunk" //
		stock_parts_list_Rr[ 3] = cars.racers.Opel_Manta_400:0x000000B2r; // "trunk equipment" //
		stock_parts_list_Rr[ 4] = cars.racers.Opel_Manta_400:0x000000B9r; // "trunk locks" //
		stock_parts_list_Rr[ 5] = cars.racers.Opel_Manta_400:0x000000ACr; // "rollcage" //
		stock_parts_list_Rr[ 6] = cars.racers.Opel_Manta_400:0x000000B0r; // "drivetrain" //

		stock_parts_list_L  = new int[4];
		stock_parts_list_L[ 0] = cars.racers.Opel_Manta_400:0x000000A8r; // "FL door" //
		stock_parts_list_L[ 1] = cars.racers.Opel_Manta_400:0x000000ADr; // "FL window" //
		stock_parts_list_L[ 2] = cars.racers.Opel_Manta_400:0x000000BFr; // "L mirror" //
		stock_parts_list_L[ 3] = cars.racers.Opel_Manta_400:0x000000AAr; // "FL seat" //

		stock_parts_list_R  = new int[4];
		stock_parts_list_R[ 0] = cars.racers.Opel_Manta_400:0x000000B6r; // "FR door" //
		stock_parts_list_R[ 1] = cars.racers.Opel_Manta_400:0x000000A9r; // "FR window" //
		stock_parts_list_R[ 2] = cars.racers.Opel_Manta_400:0x000000C0r; // "R mirror" //
		stock_parts_list_R[ 3] = cars.racers.Opel_Manta_400:0x000000BCr; // "FR seat" //

		// stage 1 stuffs //

		stg_1_parts_list_E  = new int[21];
		stg_1_parts_list_E[ 0] = parts.engines.Einvagen_Duhen_Ishima_Focer:0x0000000Ar; // "Duhen_D22V_block" //
		stg_1_parts_list_E[ 1] = parts.engines.Einvagen_Duhen_Ishima_Focer:0x000000BBr; // "Duhen_80er_crankshaft" //
		stg_1_parts_list_E[ 2] = parts.engines.Einvagen_Duhen_Ishima_Focer:0x000000BEr; // "Duhen_crankshaft_bearing_bridge" //
		stg_1_parts_list_E[ 3] = parts.engines.Einvagen_Duhen_Ishima_Focer:0x000000C0r; // "Duhen_oil_pan" //
		stg_1_parts_list_E[ 4] = parts.engines.Einvagen_Duhen_Ishima_Focer:0x00000087r; // "Duhen_146er_connecting_rods" //
		stg_1_parts_list_E[ 5] = parts.engines.Einvagen_Duhen_Ishima_Focer:0x00000093r; // "Duhen_80er_HC_pistons" /
		stg_1_parts_list_E[ 6] = parts.engines.Einvagen_Duhen_Ishima_Focer:0x000000C1r; // "Duhen_alternator" //
		stg_1_parts_list_E[ 7] = parts.engines.Baiern_Emer:0x00000045r; // "flywheel" //
		stg_1_parts_list_E[ 8] = parts.engines.Baiern_Emer:0x00000043r; // "clutch" //
		stg_1_parts_list_E[ 9] = cars.racers.Opel_Manta_400:0x0000010Ar; // "5spd transmission" //
		stg_1_parts_list_E[10] = parts.engines.Einvagen_Duhen_Ishima_Focer:0x000000E9r; // "Ishima_WR_head_cylinder_head" //
		stg_1_parts_list_E[11] = parts.engines.Einvagen_Duhen_Ishima_Focer:0x000000C3r; // "Duhen_DVC_exhaust_camshaft" //
		stg_1_parts_list_E[12] = parts.engines.Einvagen_Duhen_Ishima_Focer:0x000000C2r; // "Duhen_DVC_intake_camshaft" //
		stg_1_parts_list_E[13] = parts.engines.Einvagen_Duhen_Ishima_Focer:0x000000ECr; // "Ishima_camshaft_bearing_bridge" //
		stg_1_parts_list_E[14] = parts.engines.Einvagen_Duhen_Ishima_Focer:0x000000EFr; // "Ishima_WR_SuperTurizmo_cylinder_head_cover" //
		stg_1_parts_list_E[15] = parts.engines.Einvagen_Duhen_Ishima_Focer:0x000000C7r; // "Duhen_camshaft_drive_belt" //
		stg_1_parts_list_E[16] = parts.engines.Einvagen_Duhen_Ishima_Focer:0x000000C8r; // "Duhen_SolarWind_exhaust_header" //
		stg_1_parts_list_E[17] = parts.engines.Einvagen_Duhen_Ishima_Focer:0x0000010Dr; // "Shimutshibu_RC_MWFCR22W12_turbocharger" //
		stg_1_parts_list_E[18] = parts.engines.Einvagen_Duhen_Ishima_Focer:0x00000109r; // "Shimutshibu_RC_HIB535T4_intake_manifold" //
		stg_1_parts_list_E[19] = parts.engines.Einvagen_Duhen_Ishima_Focer:0x000000B8r; // "SL_Tuners_methanol_fuel_rail" //
		stg_1_parts_list_E[20] = parts:0x000053FFr; // "stock battery" //

		// stage 2 stuffs //

		stg_2_parts_list_E  = new int[21];
		stg_2_parts_list_E[ 0] = parts.engines.Einvagen_Duhen_Ishima_Focer:0x0000000Ar; // "Duhen_D22V_block" //
		stg_2_parts_list_E[ 1] = parts.engines.Einvagen_Duhen_Ishima_Focer:0x000000BBr; // "Duhen_80er_crankshaft" //
		stg_2_parts_list_E[ 2] = parts.engines.Einvagen_Duhen_Ishima_Focer:0x000000BEr; // "Duhen_crankshaft_bearing_bridge" //
		stg_2_parts_list_E[ 3] = parts.engines.Einvagen_Duhen_Ishima_Focer:0x000000C0r; // "Duhen_oil_pan" //
		stg_2_parts_list_E[ 4] = parts.engines.Einvagen_Duhen_Ishima_Focer:0x00000087r; // "Duhen_146er_connecting_rods" //
		stg_2_parts_list_E[ 5] = parts.engines.Einvagen_Duhen_Ishima_Focer:0x00000093r; // "Duhen_80er_HC_pistons" /
		stg_2_parts_list_E[ 6] = parts.engines.Einvagen_Duhen_Ishima_Focer:0x000000C1r; // "Duhen_alternator" //
		stg_2_parts_list_E[ 7] = parts.engines.Baiern_Emer:0x00000045r; // "flywheel" //
		stg_2_parts_list_E[ 8] = parts.engines.Baiern_Emer:0x00000043r; // "clutch" //
		stg_2_parts_list_E[ 9] = cars.racers.Opel_Manta_400:0x0000010Ar; // "5spd transmission" //
		stg_2_parts_list_E[10] = parts.engines.Einvagen_Duhen_Ishima_Focer:0x000000E9r; // "Ishima_WR_head_cylinder_head" //
		stg_2_parts_list_E[11] = parts.engines.Einvagen_Duhen_Ishima_Focer:0x000000C3r; // "Duhen_DVC_exhaust_camshaft" //
		stg_2_parts_list_E[12] = parts.engines.Einvagen_Duhen_Ishima_Focer:0x000000C2r; // "Duhen_DVC_intake_camshaft" //
		stg_2_parts_list_E[13] = parts.engines.Einvagen_Duhen_Ishima_Focer:0x000000ECr; // "Ishima_camshaft_bearing_bridge" //
		stg_2_parts_list_E[14] = parts.engines.Einvagen_Duhen_Ishima_Focer:0x000000EFr; // "Ishima_WR_SuperTurizmo_cylinder_head_cover" //
		stg_2_parts_list_E[15] = parts.engines.Einvagen_Duhen_Ishima_Focer:0x000000C7r; // "Duhen_camshaft_drive_belt" //
		stg_2_parts_list_E[16] = parts.engines.Einvagen_Duhen_Ishima_Focer:0x000000C8r; // "Duhen_SolarWind_exhaust_header" //
		stg_2_parts_list_E[17] = parts.engines.Einvagen_Duhen_Ishima_Focer:0x0000010Dr; // "Shimutshibu_RC_MWFCR22W12_turbocharger" //
		stg_2_parts_list_E[18] = parts.engines.Einvagen_Duhen_Ishima_Focer:0x00000109r; // "Shimutshibu_RC_HIB535T4_intake_manifold" //
		stg_2_parts_list_E[19] = parts.engines.Einvagen_Duhen_Ishima_Focer:0x000000B8r; // "SL_Tuners_methanol_fuel_rail" //
		stg_2_parts_list_E[20] = parts:0x000053FFr; // "stock battery" //

	// running gear parts lists //

		// stock 1 stuffs //

		stock_parts_list_RGear_suspensions = new int[4];
		stock_parts_list_RGear_suspensions[ 0] = parts:0x00000215r; // "Focer_WRC_FL_McPherson_strut" //
		stock_parts_list_RGear_suspensions[ 1] = parts:0x00000216r; // "Focer_WRC_FR_McPherson_strut" //
		stock_parts_list_RGear_suspensions[ 2] = parts:0x00000217r; // "Focer_WRC_RL_trailing_arm" //
		stock_parts_list_RGear_suspensions[ 3] = parts:0x00000218r; // "Focer_WRC_RR_trailing_arm" //

		stock_parts_list_RGear_shocks = new int[4];
		stock_parts_list_RGear_shocks[ 0] = stock_parts_list_RGear_shocks[ 1] = parts:0x000001D4r; // "shock_absorber_Focer_WRC_front" //
		stock_parts_list_RGear_shocks[ 2] = stock_parts_list_RGear_shocks[ 3] = parts:0x000001D5r; // "shock_absorber_Focer_WRC_rear" //

		stock_parts_list_RGear_springs = new int[4];
		stock_parts_list_RGear_springs[ 0] = stock_parts_list_RGear_springs[ 1] = parts:0x000001EAr; // "spring_Focer_WRC_front" //
		stock_parts_list_RGear_springs[ 2] = stock_parts_list_RGear_springs[ 3] = parts:0x000001EAr; // "spring_Focer_WRC_front" //

		stock_parts_list_RGear_brakes = new int[4];
		stock_parts_list_RGear_brakes[ 0] = stock_parts_list_RGear_brakes[ 1] = parts:0x0000017Ar; // "brake_Focer_WRC_front" //
		stock_parts_list_RGear_brakes[ 2] = stock_parts_list_RGear_brakes[ 3] = parts:0x0000017Br; // "brake_Focer_WRC_rear" //

		stock_parts_list_RGear_sways = new int[2];
		stock_parts_list_RGear_sways[ 0] = parts:0x000001A7r; // "swaybar_Focer_WRC_front" //
		stock_parts_list_RGear_sways[ 1] = parts:0x000001A9r; // "swaybar_Focer_WRC_rear" //

		stock_parts_list_RGear_wheels = new int[4];
		stock_parts_list_RGear_wheels[ 0] = stock_parts_list_RGear_wheels[ 1] = cars.racers.Opel_Manta_400:0x0000036Er; // "rim_F_TM" //
		stock_parts_list_RGear_wheels[ 2] = stock_parts_list_RGear_wheels[ 3] = cars.racers.Opel_Manta_400:0x00000390r; // "rim_R_TM" //

		stock_parts_list_RGear_tyres = new int[4];
		stock_parts_list_RGear_tyres[ 0] = stock_parts_list_RGear_tyres[ 1] = cars.racers.Opel_Manta_400:0x00000399r; // "tyre_F_TM" //
		stock_parts_list_RGear_tyres[ 2] = stock_parts_list_RGear_tyres[ 3] = cars.racers.Opel_Manta_400:0x0000039Fr; // "tyre_R_TM" //

	// other parts lists //

		super.addStockParts( desc );

		Descriptor d = new Descriptor( cars.racers.Opel_Manta_400:0x0000004Er, desc.optical, desc.power, desc.wear, desc.tear );
		addPart( cars.racers.Opel_Manta_400:0x000000B7r, "R numberplate", d );
		addPart( cars.racers.Opel_Manta_400:0x000000BEr, "F mudflaps", d );

		addPart( cars.racers.Opel_Manta_400:0x000000ABr, "steering wheel", desc );
		addPart( cars.racers.Opel_Manta_400:0x00000095r, "stock exhaust pipe", desc );

		if (desc.power > 1.3)
		{
			NOSInjectorSystem N2Oinjector=addPart( parts.engines.Einvagen_Duhen_Ishima_Focer:0x00000052r, "NOS injector" );
			N2Oinjector.nitro_consumption = clampTo(N2Oinjector.maxconsumption*((desc.power-1.3)/0.7*0.500+0.500),N2Oinjector.minconsumption,N2Oinjector.maxconsumption);
			if (desc.power < 1.6)
			{
				addPart( parts:0x000001C1r, "12pds canister" );
				addPart( parts:0x000001C1r, "12pds canister" );
			}
			else
			{
				addPart( parts:0x000001BFr, "24pds canister" );
				addPart( parts:0x000001BFr, "24pds canister" );
			}			
		}
	}
}

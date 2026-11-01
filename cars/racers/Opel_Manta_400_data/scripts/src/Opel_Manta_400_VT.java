package java.game.cars;

import java.game.*;

public class Opel_Manta_400_VT extends VehicleType
{
	public Opel_Manta_400_VT( int id )
	{
		VehicleModel vmd;

	// DEMO mode //
		// Opel Manta 400 //
			vmd=new VehicleModel( cars.racers.Opel_Manta_400:0x00000006r, VS_DEMO );
			vtdarr.addElement( vmd );
			vmd.prevalence	= 1.0;
			vmd.minPower	= 1.0;	vmd.maxPower	= 1.5;
			vmd.minOptical	= 1.0;	vmd.maxOptical	= 1.5;
			vmd.stockPrestige=599;	vmd.fullPrestige= 632;
			vmd.stockQM = qm_stock_Shimutshibu_Focer_WRC;	vmd.fullQM = qm_full_Shimutshibu_Focer_WRC;
			vmd.vehicleName = "Opel Manta 400";

		prevalence = 0.0;


	// CAREER mode -> used car dealer //
		// Opel Manta 400 //
			vmd=new VehicleModel( cars.racers.Opel_Manta_400:0x00000006r, VS_USED );
			vtdarr.addElement( vmd );
			vmd.prevalence = 200.0;
			vmd.minPower	= 0.25;	vmd.maxPower	= 1.25;
			vmd.minOptical	= 0.25;	vmd.maxOptical	= 1.00;
			vmd.minTear 	= 0.10;	vmd.maxTear 	= 0.90;
			vmd.minWear 	= 0.10;	vmd.maxWear 	= 0.75;
			vmd.stockPrestige=599;	vmd.fullPrestige= 632;
			vmd.stockQM = qm_stock_Shimutshibu_Focer_WRC;	vmd.fullQM = qm_full_Shimutshibu_Focer_WRC;
			vmd.vehicleName = "Opel Manta 400";
			prevalence += vmd.prevalence;
			vmd.addColorIndex(GameLogic.RID_CARCOLOR_Used_Rusty_Cherry);
			vmd.addColorIndex(GameLogic.RID_CARCOLOR_Used_Rusty_Smaragd);
			vmd.addColorIndex(GameLogic.RID_CARCOLOR_Used_Rusty_Nacht);
			vmd.addColorIndex(GameLogic.RID_CARCOLOR_Used_Rusty_Zucker);


	// CAREER mode -> new car dealer //
		// Opel Manta 400 //
			vmd=new VehicleModel( cars.racers.Opel_Manta_400:0x00000006r, VS_STOCK );
			vtdarr.addElement( vmd );
			vmd.prevalence = 50.0;
			vmd.minPower	= 1.0;	vmd.maxPower	= 1.0;
			vmd.minOptical	= 1.0;	vmd.maxOptical	= 1.0;
			vmd.minTear 	= 1.0;	vmd.maxTear 	= 1.0;
			vmd.minWear 	= 1.0;	vmd.maxWear 	= 1.0;
			vmd.stockPrestige=599;	vmd.fullPrestige= 632;
			vmd.stockQM = qm_stock_Shimutshibu_Focer_WRC;	vmd.fullQM = qm_full_Shimutshibu_Focer_WRC;
			vmd.vehicleName = "Opel Manta 400";
			prevalence += vmd.prevalence;


	// CAREER mode -> races //
		// Opel Manta 400 //
			vmd=new VehicleModel( cars.racers.Opel_Manta_400:0x00000006r, VS_DRACE | VS_NRACE );
			vtdarr.addElement( vmd );
			vmd.prevalence = 400.0;
			vmd.minPower	= 1.0;	vmd.maxPower	= 1.5;
			vmd.minOptical	= 1.0;	vmd.maxOptical	= 1.5;
			vmd.minTear 	= 1.0;	vmd.maxTear 	= 1.0;
			vmd.minWear 	= 1.0;	vmd.maxWear 	= 1.0;
			vmd.stockPrestige=599;	vmd.fullPrestige= 632;
			vmd.stockQM = qm_stock_Shimutshibu_Focer_WRC;	vmd.fullQM = qm_full_Shimutshibu_Focer_WRC;
			vmd.vehicleName = "Opel Manta 400";
			prevalence += vmd.prevalence;

			vmd=new VehicleModel( cars.racers.Opel_Manta_400:0x00000006r, VS_RRACE );
			vtdarr.addElement( vmd );
			vmd.prevalence = 600.0;
			vmd.minPower	= 1.5;	vmd.maxPower	= 2.0;
			vmd.minOptical	= 1.5;	vmd.maxOptical	= 2.0;
			vmd.minTear 	= 1.0;	vmd.maxTear 	= 1.0;
			vmd.minWear 	= 1.0;	vmd.maxWear 	= 1.0;
			vmd.stockPrestige=599;	vmd.fullPrestige= 632;
			vmd.stockQM = qm_stock_Shimutshibu_Focer_WRC;	vmd.fullQM = qm_full_Shimutshibu_Focer_WRC;
			vmd.vehicleName = "Opel Manta 400";


		// make color indexes //
		addColorIndex(GameLogic.RID_CARCOLOR_Baiern_Devils_eye_red);
		addColorIndex(GameLogic.RID_CARCOLOR_Baiern_Spring_yellow);

		addColorIndex(GameLogic.RID_CARCOLOR_Einvagen_Zucker);
		addColorIndex(GameLogic.RID_CARCOLOR_Einvagen_Tornado_rot);
		addColorIndex(GameLogic.RID_CARCOLOR_Einvagen_Nacht);
		addColorIndex(GameLogic.RID_CARCOLOR_Einvagen_Smaragd);
		addColorIndex(GameLogic.RID_CARCOLOR_Einvagen_Black_mage);
		addColorIndex(GameLogic.RID_CARCOLOR_Einvagen_Hamvas_Grun);
		addColorIndex(GameLogic.RID_CARCOLOR_Einvagen_Indigo);
		addColorIndex(GameLogic.RID_CARCOLOR_Einvagen_Jazz);
		addColorIndex(GameLogic.RID_CARCOLOR_Einvagen_Antracit);
		addColorIndex(GameLogic.RID_CARCOLOR_Einvagen_Mercator_Blau);
		addColorIndex(GameLogic.RID_CARCOLOR_Einvagen_Murano);
		addColorIndex(GameLogic.RID_CARCOLOR_Einvagen_Champagner);
		addColorIndex(GameLogic.RID_CARCOLOR_Einvagen_Ozean);
		addColorIndex(GameLogic.RID_CARCOLOR_Einvagen_Reflex);
		addColorIndex(GameLogic.RID_CARCOLOR_Einvagen_Saratoga);

//		prevalence *= 1000.0;
	}
}

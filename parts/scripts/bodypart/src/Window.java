package java.game.parts.bodypart;

import java.io.*;
import java.util.*;
import java.util.resource.*;
import java.render.osd.*;
import java.game.*;
import java.game.cars.*;
import java.game.parts.*;
import java.game.parts.enginepart.*;
import java.game.parts.rgearpart.*;
import java.game.parts.rgearpart.reciprocatingrgearpart.*;

public class Window extends BodyPart
{
	GlassOpacityTuner glassOpacityTuner = new GlassOpacityTuner();

	public Window( int id )
	{
		super( id );

		name = "Window";

		prestige_calc_weight = 15.0;
		drag_reduction = 0.03;
	}

	public int isTuneable()
	{
		return 1;
	}

	public void buildTuningMenu( Menu m )
	{
		glassOpacityTuner.buildTuningMenu(m, this);
	}

	public void handleMessage( Event m )
	{
		glassOpacityTuner.handleMessage(m, this);
	}

	public void refreshGlassOpacityPreview()
	{
		glassOpacityTuner.onInventoryPreview(this);
	}

	public void endTuningSession( int cancelled )
	{
		glassOpacityTuner.endTuningSession(cancelled, this);
	}

	public void save( File saveGame )
	{
		super.save( saveGame );
		glassOpacityTuner.save( saveGame );
	}

	public void load( File saveGame )
	{
		super.load( saveGame );
		// Legacy v2 records have no opacity payload.
		if (file_ver >= 3)
			glassOpacityTuner.load( saveGame, file_ver );
	}

	public void install()
	{
		super.install();
		glassOpacityTuner.onInstalled( this );
	}

}

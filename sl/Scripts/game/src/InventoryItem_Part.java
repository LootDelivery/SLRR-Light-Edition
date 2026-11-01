package java.game;

import java.util.*;
import java.util.resource.*;
import java.render.*;
import java.render.osd.*;
import java.sound.*;
import java.game.parts.*;

public class InventoryItem_Part extends InventoryItem
{
	private	Part	partXXX;
	private	int	partTypeID;

	int	compatibility;

	public InventoryItem_Part( Inventory inv, int id )
	{
		super( inv );
		setPart( id );
	}

	public InventoryItem_Part( Inventory inv, Part p )
	{
		super( inv );

		// Move the real part into this inventory item's local root.
		p.command( "remove 0 "+localroot.id() );
		p.setParent( localroot );
		p.setPos( new Vector3( 0,0,0 ) );
		setPart( p );
	}

	public void	setPart( int id )
	{
		partTypeID=id;
		partXXX=null;
	}

	public void	setPart( Part p )
	{
		partTypeID=0;
		partXXX=p;
	}

	public Part getPart()
	{
		if( !partXXX )
		{
			if( !partTypeID )
			{
				return null;
			}
			else
			{
				GameRef xa = new GameRef();
				partXXX = xa.create( localroot, new GameRef(partTypeID), "0,0,0,0,0,0", "part_created_by_inventory" );
				partTypeID=0;
			}
		}

		return partXXX;
	}

	public int getTypeID()
	{
		if(partTypeID) return partTypeID;
		return 0;
	}

	public void flush()
	{
		if( partXXX )
		{
			partTypeID=partXXX.getInfo( GameType.GII_TYPE );
			partXXX.destroy();
			partXXX=null;
		}
	}

	public int getName()
	{
		return getPart().name;
	}

	public int getDescription()
	{
		return getPart().description;
	}

	public int getPrice()
	{
		return getPart().currentPrice();
	}

	public int getLogo()
	{
		return getPart().getLogo();
	}

	public void copyToInventory( Inventory other )
	{
		other.addItem( getPart().getInfo( GameType.GII_TYPE ) );
	}

	public String installToCar( Vehicle car, Vector3 pos )
	{
		String error;

		if( car )
		{
			Part part = getPart();

			GameRef xpart = new GameRef();
			int[] slotId = part.install_OK( car, 0, xpart, 0, pos );
			if( slotId )
			{
				if( !(error=part.installCheck( xpart.getScriptInstance(), slotId )) )
				{
					if (pos)
						part.command( "install 0 "+car.id()+" 0 0 0 "+pos.x+" "+pos.y+" "+pos.z );
					else
						part.command( "install 0 "+car.id() );
				}
			}
			else
				error = "";

			if( !error )
			{
				setPart( null );
			}
		}

		return error;
	}

	public String getInfo()
	{
		Part p = getPart();

		int percentCondition=p.getConditionNoAttach()*100;
		String flags = "";
		if( p.isComplex() )
			flags = flags + "+";
		if( p.isTuneable() )
			flags = flags + "T";
		if( !p.isStreetLegal() )
			flags = flags + "!";

		if( flags )
			flags = " [" + flags + "]";

		return p.name + flags + " (" + percentCondition + "%)";
	}

	public void show( InventoryPanel ip )
	{
		Part p = getPart();

		if( p )
		{
			p.setMatrix( null, null );
			ip.createDefCamera( getPart().getInfo( GameType.GII_SIZE )/100.0, 1 );
			ip.createDefLight();
			refreshGlassOpacityPreview();
		}
	}

	// Called on show and while this preview is focused, so delayed render children
	// can receive the saved appearance once the engine creates them.
	public void refreshGlassOpacityPreview()
	{
		Part p = getPart();
		if( p )
		{
			p.refreshGlassOpacityPreview();
		}
	}

	public void hide( InventoryPanel ip )
	{
		ip.cleanup();
	}
}

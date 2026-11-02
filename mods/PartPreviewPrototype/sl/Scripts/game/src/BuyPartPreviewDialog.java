package java.game;

import java.io.*;
import java.util.*;
import java.util.resource.*;
import java.render.*;
import java.render.osd.*;
import java.render.osd.dialog.*;
import java.game.parts.*;

/**
 * Experimental purchase confirmation with an interactive 3D part preview.
 *
 * The preview is a separately-created instance of the catalog part. It must
 * never rotate or otherwise modify the InventoryItem's original Part.
 *
 * Mouse position is read from MouseCursor.getPos(); physical mouse X/Y axes
 * are relative movement inputs and must not be treated as absolute cursor
 * coordinates.
 */
public class BuyPartPreviewDialog extends PartPreviewDialogBase
{
	InventoryItem item;
	Part sourcePart;
	Part previewPart;

	Dummy previewRoot;
	RenderRef previewLight;
	Viewport previewViewport;
	Camera previewCamera;

	Vector3 savedPosition;
	Ypr savedOrientation;

	float modelSize = 1.0;
	float boundsCenterX;
	float boundsCenterY;
	float boundsCenterZ;
	float nestedMeshCenterX;
	float nestedMeshCenterY;
	float nestedMeshCenterZ;
	float nestedMeshSizeX;
	float nestedMeshSizeY;
	float nestedMeshSizeZ;
	int hasNestedMeshBounds;
	int nestedMeshResourceID;
	float nestedCenterOffsetX;
	float nestedCenterOffsetY;
	float nestedCenterOffsetZ;
	float nestedCenterBaseYaw;
	float nestedCenterBasePitch;
	float nestedCenterBaseRoll;
	int nestedCenterOffsetReady;
	int nestedCenterWaitFrames;
	int nestedCenterWaitLogged;
	int nestedCenterVerifyPending;
	int nestedCenterVerifyInitialDone;
	int nestedCenterVerifyRotationDone;
	int nestedCenterApplyLogged;
	float yaw;
	float pitch;
	float roll;
	float zoom = 1.0;

	float lastFrameTime;
	float lastMouseX;
	float lastMouseY;
	float lastWheel;
	float autoResume = 1.0;

	int dragging;
	int animateTraceFrames;

	String previewTraceData = "";

	private void tracePreview( String marker )
	{
		previewTraceData = previewTraceData + marker + "\r\n";

		File traceFile = new File( GameLogic.tempSaveDir + "PartPreviewTrace.txt" );
		if( traceFile.open( File.MODE_WRITE ) )
		{
			traceFile.write( previewTraceData );
			traceFile.close();
		}
	}

	final static int RID_PREVIEW_LIGHT = misc.garage:0x00000024r;

	// Native query returns signed fixed-point coordinates (world units * 1000).
	final static int AABB_QUERY = 0x584F4242;
	final static int AABB_INVALID = 0x80000000;
	final static float AABB_SCALE = 1000.0;
	// Read-only native handle dump for the type-3 GII_RENDERINSTANCE child.
	final static int RENDER_RAW_QUERY_BASE = 100;

	final static float AUTO_ROTATION_SPEED = 0.32;
	// Mouse sensitivity: horizontal (yaw) is intentionally faster than vertical (pitch).
	final static float DRAG_YAW_SCALE = 3.5;
	final static float DRAG_PITCH_SCALE = 2.2;

	// Lower the camera slightly so the model sits a little higher in the viewport.
	// Only the isolated preview transform is adjusted to center its runtime AABB.
	final static float CAMERA_VERTICAL_OFFSET = 0.08;
	final static float MIN_ZOOM = 0.65;
	final static float MAX_ZOOM = 2.25;
	final static float MOUSE_DEAD_ZONE = 0.0005;

	public BuyPartPreviewDialog( Controller ctrl, int myFlags, InventoryItem sourceItem, int price )
	{
		super( ctrl, myFlags, "BUY PART" );

		item = sourceItem;
		if( item instanceof InventoryItem_Part )
			sourcePart = ((InventoryItem_Part)item).getPart();

		Osd ui = getPreviewOsd();
		ui.createText( "PART SPECIFICATIONS", Frontend.mediumFont, Text.ALIGN_LEFT, 0.16, -0.72 );
		ui.createText( "" + item.getName(), Frontend.largeFont, Text.ALIGN_LEFT, 0.16, -0.48 );
		ui.createText( "$" + price, Frontend.mediumFont, Text.ALIGN_LEFT, 0.16, -0.27 );

		String details = "" + item.getDescription() + "\n\n" + compatibilityText();
		ui.createTextBox( details, Frontend.smallFont, Text.ALIGN_LEFT, 0.16, 0.12, 0.72, null, 12 );
		ui.createText( "LEFT MOUSE: ROTATE    WHEEL: ZOOM", Frontend.smallFont, Text.ALIGN_LEFT, 0.16, 0.68 );
	}

	private String compatibilityText()
	{
		if( !(item instanceof InventoryItem_Part) )
			return "Part compatibility unavailable.";

		int compatibility = ((InventoryItem_Part)item).compatibility;
		if( !compatibility )
			return "NOT compatible with your current car / parts bin!";

		String note = "";
		if( compatibility & 1 )
			note = note + "Installable right away to your car";
		if( compatibility & 2 )
		{
			if( note )
				note = note + " and ";
			note = note + "interchangeable with your car's parts";
		}
		if( compatibility & 4 )
		{
			if( note )
				note = note + " and ";
			note = note + "parts-bin compatible";
		}
		return note;
	}

	private int cursorOverPreview( float x, float y )
	{
		// MouseCursor.getPos() uses the normalized OSD coordinate space.
		if( x > -0.92 && x < -0.03 && y > -0.82 && y < 0.72 )
			return 1;
		return 0;
	}

	private void traceResourceNode( ResourceRef node, String label )
	{
		if( !node )
		{
			tracePreview( label + " null" );
			return;
		}

		tracePreview(
			label + " id=" + node.id() +
			" type=" + node.type() +
			" parentID=" + node.getParentID()
		);
	}

	private void traceRenderBoneDiagnostics( GameRef renderRef, String prefix )
	{
		// INSTANCE_RENDER+0xF4 is optional. Do not query its child fields when null:
		// the native dispatcher then returns the invalid sentinel for every child.
		int f4Pointer = renderRef.getInfo( AABB_QUERY, 251 );
		tracePreview( prefix + " F4 pointer=" + f4Pointer );
		if( f4Pointer )
		{
			for( int axis = 252; axis <= 272; axis++ )
			{
				int value = renderRef.getInfo( AABB_QUERY, axis );
				tracePreview( prefix + " F4 axis=" + axis + " raw=" + value );
			}
		}

		// These probes follow the same native list helpers used by 0x005433E0.
		int auxiliaryListEmpty = renderRef.getInfo( AABB_QUERY, 273 );
		tracePreview( prefix + " auxiliary-list-empty=" + auxiliaryListEmpty );

		int firstNode = renderRef.getInfo( AABB_QUERY, 274 );
		tracePreview( prefix + " first-list-node=" + firstNode );
		if( firstNode && firstNode != AABB_INVALID )
		{
			for( int axis = 275; axis <= 293; axis++ )
			{
				int value = renderRef.getInfo( AABB_QUERY, axis );
				tracePreview( prefix + " first-node axis=" + axis + " raw=" + value );
			}
		}

		int bone00Node = renderRef.getInfo( AABB_QUERY, 294 );
		tracePreview( prefix + " bone00-node=" + bone00Node );
		if( bone00Node && bone00Node != AABB_INVALID )
		{
			for( int axis = 295; axis <= 318; axis++ )
			{
				int value = renderRef.getInfo( AABB_QUERY, axis );
				tracePreview( prefix + " bone00 axis=" + axis + " raw=" + value );
			}
		}
	}

	private void traceRenderResourceDiagnostics( GameRef renderRef, String prefix )
	{
		// 0x541FE0 is called with the embedded render object at outer+0xD8.
		// Its +0x84 field is a typed resource descriptor (native type 14).
		// Dump the otherwise-unobserved middle fields and the descriptor header.
		for( int axis = 319; axis <= 361; axis++ )
		{
			int value = renderRef.getInfo( AABB_QUERY, axis );
			tracePreview( prefix + " embedded+" + (0x54 + (axis - 319) * 4) + "=" + value );
		}

		int resourcePointer = renderRef.getInfo( AABB_QUERY, 362 );
		tracePreview( prefix + " embedded+0x84 resource-pointer=" + resourcePointer );
		if( resourcePointer && resourcePointer != AABB_INVALID )
		{
			for( int axis = 363; axis <= 384; axis++ )
			{
				int value = renderRef.getInfo( AABB_QUERY, axis );
				tracePreview( prefix + " resource-header+" + ((axis - 363) * 4) + "=" + value );
			}
		}

		int collectionPointer = renderRef.getInfo( AABB_QUERY, 385 );
		tracePreview( prefix + " embedded+0x160 collection-pointer=" + collectionPointer );
	}

	private void traceRenderNativePose( GameRef renderRef, String prefix )
	{
		// INSTANCE_RENDER mode, position and uniform scale are at +0x80..+0x98.
		for( int axis = 172; axis <= 178; axis++ )
		{
			int value = renderRef.getInfo( AABB_QUERY, axis );
			tracePreview( prefix + " instancePose axis=" + axis + " raw=" + value );
		}

		// The first render-list matrix is the one consumed by native 0x5433E0.
		for( int axis = 295; axis <= 310; axis++ )
		{
			int value = renderRef.getInfo( AABB_QUERY, axis );
			tracePreview( prefix + " boneMatrix[" + (axis - 295) + "]=" + value );
		}
	}


	private void traceRenderPayloadDiagnostics( GameRef renderRef, String prefix )
	{
		// Native 0x419860 returns the embedded payload at ResourceRef +0x80.
		// Its +0x18 and +0x38 links are consumed by native 0x541FE0.
		for( int axis = 386; axis <= 417; axis++ )
		{
			int value = renderRef.getInfo( AABB_QUERY, axis );
			tracePreview( prefix + " resource-payload+" + ((axis - 386) * 4) + "=" + value );
		}

		int childResource = renderRef.getInfo( AABB_QUERY, 418 );
		tracePreview( prefix + " resource-payload+0x18 child-resource=" + childResource );
		int nestedMeshID = 0;
		if( childResource && childResource != AABB_INVALID )
		{
			for( int axis = 419; axis <= 439; axis++ )
			{
				int value = renderRef.getInfo( AABB_QUERY, axis );
				tracePreview( prefix + " child-resource+" + ((axis - 419) * 4) + "=" + value );
				if( axis == 439 )
					nestedMeshID = value;
			}
			for( int axis = 440; axis <= 471; axis++ )
			{
				int value = renderRef.getInfo( AABB_QUERY, axis );
				tracePreview( prefix + " child-payload+" + ((axis - 440) * 4) + "=" + value );
			}
		}

		// The embedded resource owns a type-5 RESOURCE_MESH child. Try resolving
		// its runtime resource ID through the ordinary Java GameRef constructor,
		// then reuse the already-installed type-5 AABB path (axes 0..11).
		if( nestedMeshID > 0 )
		{
			tracePreview( prefix + " nested mesh GameRef before construction id=" + nestedMeshID );
			GameRef nestedMeshRef = new GameRef( nestedMeshID );
			int nestedMeshType = nestedMeshRef.type();
			tracePreview(
				prefix + " nested mesh GameRef type=" + nestedMeshType +
				" id=" + nestedMeshRef.id()
			);

			int minXFixed = AABB_INVALID;
			int minYFixed = AABB_INVALID;
			int minZFixed = AABB_INVALID;
			int maxXFixed = AABB_INVALID;
			int maxYFixed = AABB_INVALID;
			int maxZFixed = AABB_INVALID;
			for( int axis = 0; axis <= 11; axis++ )
			{
				int value = nestedMeshRef.getInfo( AABB_QUERY, axis );
				tracePreview( prefix + " nested mesh AABB axis=" + axis + " raw=" + value );
				if( axis == 0 ) minXFixed = value;
				if( axis == 1 ) minYFixed = value;
				if( axis == 2 ) minZFixed = value;
				if( axis == 3 ) maxXFixed = value;
				if( axis == 4 ) maxYFixed = value;
				if( axis == 5 ) maxZFixed = value;
			}

			if( nestedMeshType == 5 &&
				minXFixed != AABB_INVALID && minYFixed != AABB_INVALID && minZFixed != AABB_INVALID &&
				maxXFixed != AABB_INVALID && maxYFixed != AABB_INVALID && maxZFixed != AABB_INVALID )
			{
				float minX = minXFixed / AABB_SCALE;
				float minY = minYFixed / AABB_SCALE;
				float minZ = minZFixed / AABB_SCALE;
				float maxX = maxXFixed / AABB_SCALE;
				float maxY = maxYFixed / AABB_SCALE;
				float maxZ = maxZFixed / AABB_SCALE;
				float sizeX = maxX - minX;
				float sizeY = maxY - minY;
				float sizeZ = maxZ - minZ;

				if( sizeX > 0.001 && sizeY > 0.001 && sizeZ > 0.001 )
				{
					nestedMeshCenterX = (minX + maxX) * 0.5;
					nestedMeshCenterY = (minY + maxY) * 0.5;
					nestedMeshCenterZ = (minZ + maxZ) * 0.5;
					nestedMeshSizeX = sizeX;
					nestedMeshSizeY = sizeY;
					nestedMeshSizeZ = sizeZ;
					nestedMeshResourceID = nestedMeshRef.id();
					tracePreview( prefix + " nested mesh identity stored=" + nestedMeshResourceID );
					hasNestedMeshBounds = 1;
					tracePreview(
						prefix + " nested mesh AABB min=" + minX + "," + minY + "," + minZ +
						" max=" + maxX + "," + maxY + "," + maxZ +
						" center=" + nestedMeshCenterX + "," + nestedMeshCenterY + "," + nestedMeshCenterZ +
						" size=" + nestedMeshSizeX + "," + nestedMeshSizeY + "," + nestedMeshSizeZ
					);
				}
			}
		}
		else
			tracePreview( prefix + " nested mesh ID unavailable; AABB probe skipped" );

		int collection = renderRef.getInfo( AABB_QUERY, 472 );
		tracePreview( prefix + " resource-payload+0x38 collection=" + collection );
		if( collection && collection != AABB_INVALID )
		{
			for( int axis = 473; axis <= 488; axis++ )
			{
				int value = renderRef.getInfo( AABB_QUERY, axis );
				tracePreview( prefix + " collection+" + ((axis - 473) * 4) + "=" + value );
			}
		}
	}

	private void tracePartResourceTree()
	{
		tracePreview(
			"R00 Part id=" + previewPart.id() +
			" type=" + previewPart.type()
		);

		int renderInstance = previewPart.getInfo( GameType.GII_RENDERINSTANCE );
		int renderInfo = previewPart.getInfo( GameType.GII_RENDER );
		tracePreview(
			"R01 GII_RENDERINSTANCE=" + renderInstance +
			" GII_RENDER=" + renderInfo
		);

		if( renderInstance )
		{
			// GII_RENDERINSTANCE identifies the actual INSTANCE_RENDER child.
			// Re-wrap that same runtime ID to query its own GameType information
			// and RenderRef transform without changing the instance.
			GameRef renderGameRef = new GameRef( renderInstance );
			tracePreview(
				"R02 render GameRef type=" + renderGameRef.type() +
				" id=" + renderGameRef.id() +
				" parentID=" + renderGameRef.getParentID()
			);

			int renderChildMesh = renderGameRef.getInfo( GameType.GII_RENDER );
			int renderChildInstance = renderGameRef.getInfo( GameType.GII_RENDERINSTANCE );
			int renderChildSize = renderGameRef.getInfo( GameType.GII_SIZE );
			int renderChildType = renderGameRef.getInfo( GameType.GII_TYPE );
			tracePreview(
				"R03 render-child info GII_RENDER=" + renderChildMesh +
				" GII_RENDERINSTANCE=" + renderChildInstance +
				" GII_SIZE=" + renderChildSize +
				" GII_TYPE=" + renderChildType
			);

			Vector3 renderGamePosition = renderGameRef.getPos();
			Ypr renderGameOrientation = renderGameRef.getOri();
			if( renderGamePosition && renderGameOrientation )
				tracePreview(
					"R04 GameRef transform pos=" + renderGamePosition.x + "," +
					renderGamePosition.y + "," + renderGamePosition.z +
					" ypr=" + renderGameOrientation.y + "," +
					renderGameOrientation.p + "," + renderGameOrientation.r
				);
			else
				tracePreview( "R04 GameRef transform contains null" );

			// The custom native query returns raw DWORDs from the type-3
			// render-instance handle. No native pointer is dereferenced here.
			for( int rawField = 0; rawField < 20; rawField++ )
			{
				int rawValue = renderGameRef.getInfo( AABB_QUERY, RENDER_RAW_QUERY_BASE + rawField );
				tracePreview( "R05 render-handle raw offset=" + (rawField * 4) + " value=" + rawValue );
			}
			for( int rawField = 20; rawField < 56; rawField++ )
			{
				int rawValue = renderGameRef.getInfo( AABB_QUERY, 160 + rawField - 20 );
				tracePreview( "R07 render-handle raw offset=" + (rawField * 4) + " value=" + rawValue );
			}
			for( int innerField = 0; innerField < 21; innerField++ )
			{
				int rawValue = renderGameRef.getInfo( AABB_QUERY, 200 + innerField );
				tracePreview( "R08 embedded-render raw offset=" + (innerField * 4) + " value=" + rawValue );
			}
			for( int innerField = 0; innerField < 21; innerField++ )
			{
				int rawValue = renderGameRef.getInfo( AABB_QUERY, 230 + innerField );
				tracePreview( "R09 embedded-render raw offset=" + (0x100 + innerField * 4) + " value=" + rawValue );
			}

			traceRenderBoneDiagnostics( renderGameRef, "R11" );
			traceRenderResourceDiagnostics( renderGameRef, "R12" );
			traceRenderPayloadDiagnostics( renderGameRef, "R13" );

			// Native lookup 0x005413C0 follows a sorted linked render-record list.
			// Axis 122 is its terminal node; the terminal's +0x04 field is zero.
			// Only the first real record owns the matrix at +0x54.
			for( int listAxis = 120; listAxis <= 138; listAxis++ )
			{
				int listValue = renderGameRef.getInfo( AABB_QUERY, listAxis );
				tracePreview( "R06 render-record axis=" + listAxis + " raw=" + listValue );
			}
			int terminalNext = renderGameRef.getInfo( AABB_QUERY, 140 );
			tracePreview( "R06 terminal node next=" + terminalNext );
		}

		// ResourceRef exposes a generic child/sibling traversal API. Dump only
		// a bounded portion of the preview Part tree; this is read-only.
		ResourceRef child = previewPart.getFirstChild();
		int childIndex = 0;
		while( child && childIndex < 32 )
		{
			traceResourceNode( child, "R10 child[" + childIndex + "]" );

			ResourceRef nested = child.getFirstChild();
			int nestedIndex = 0;
			while( nested && nestedIndex < 8 )
			{
				traceResourceNode( nested, "R20 child[" + childIndex + "." + nestedIndex + "]" );
				nested = nested.getNextChild();
				nestedIndex++;
			}

			tracePreview(
				"R21 child[" + childIndex + "] nestedCountLogged=" + nestedIndex
			);
			child = child.getNextChild();
			childIndex++;
		}
		tracePreview( "R30 directChildCountLogged=" + childIndex );
	}

	private void createPreview()
	{
		previewTraceData = "";
		File.delete( GameLogic.tempSaveDir + "PartPreviewTrace.txt" );
		boundsCenterX = 0.0;
		boundsCenterY = 0.0;
		boundsCenterZ = 0.0;
		hasNestedMeshBounds = 0;
		nestedMeshResourceID = 0;
		nestedCenterOffsetReady = 0;
		nestedCenterWaitFrames = 0;
		nestedCenterWaitLogged = 0;
		nestedCenterVerifyPending = 0;
		nestedCenterVerifyInitialDone = 0;
		nestedCenterVerifyRotationDone = 0;
		nestedCenterApplyLogged = 0;
		int hasRuntimeBounds = 0;
		float runtimeModelSize = 0.0;
		tracePreview( "00 createPreview begin" );

		if( !sourcePart )
		{
			tracePreview( "01 sourcePart is null; preview aborted" );
			return;
		}

		tracePreview( "02 before sourcePart.getInfo(GII_TYPE)" );
		int partTypeID = sourcePart.getInfo( GameType.GII_TYPE );
		tracePreview( "03 after sourcePart.getInfo(GII_TYPE) value=" + partTypeID );
		if( !partTypeID )
		{
			tracePreview( "04 partTypeID is zero; preview aborted" );
			return;
		}

		// Build an independent scene root and instantiate the same part type
		// under it. The catalog's InventoryItem and its Part remain untouched.
		tracePreview( "05 before previewRoot creation" );
		previewRoot = new Dummy( GameType.WORLDTREEROOT );
		tracePreview( "06 after previewRoot creation" );

		GameRef factory = new GameRef();
		tracePreview( "07 before factory.create" );
		previewPart = factory.create(
			previewRoot,
			new GameRef( partTypeID ),
			"0,0,0,0,0,0",
			"part_purchase_preview"
		);
		tracePreview( "08 after factory.create" );

		if( !previewPart )
		{
			tracePreview( "09 previewPart is null; destroying previewRoot" );
			previewRoot.destroy();
			previewRoot = null;
			tracePreview( "0A preview creation aborted" );
			return;
		}

		tracePreview( "0B before previewPart.setMatrix" );
		previewPart.setMatrix( null, null );
		tracePreview( "0C after previewPart.setMatrix" );
		tracePreview( "0D before refreshGlassOpacityPreview" );
		previewPart.refreshGlassOpacityPreview();
		tracePreview( "0E after refreshGlassOpacityPreview" );
		tracePartResourceTree();

		// The preview instance owns this live mesh resource. Query its current
		// CPU vertex buffers through the reserved GameRef.getInfo(II)I route.
		tracePreview( "10 before previewPart.getMesh" );
		int previewMeshID = previewPart.getMesh();
		tracePreview( "11 after previewPart.getMesh value=" + previewMeshID );
		if( previewMeshID )
		{
			tracePreview( "12 before preview mesh GameRef construction" );
			GameRef previewMeshRef = new GameRef( previewMeshID );
			tracePreview( "13 after preview mesh GameRef construction" );
			tracePreview( "14 using mesh owned by previewPart; no extra resource load" );

			tracePreview( "16 before previewMeshRef.type" );
			int previewMeshType = previewMeshRef.type();
			tracePreview( "17 after previewMeshRef.type value=" + previewMeshType );

			if( previewMeshType == 5 )
			{
				tracePreview( "20 before AABB query axis=0 MinX" );
				int minXFixed = previewMeshRef.getInfo( AABB_QUERY, 0 );
				tracePreview( "21 after AABB query axis=0 value=" + minXFixed );

				tracePreview( "22 before AABB query axis=1 MinY" );
				int minYFixed = previewMeshRef.getInfo( AABB_QUERY, 1 );
				tracePreview( "23 after AABB query axis=1 value=" + minYFixed );

				tracePreview( "24 before AABB query axis=2 MinZ" );
				int minZFixed = previewMeshRef.getInfo( AABB_QUERY, 2 );
				tracePreview( "25 after AABB query axis=2 value=" + minZFixed );

				tracePreview( "26 before AABB query axis=3 MaxX" );
				int maxXFixed = previewMeshRef.getInfo( AABB_QUERY, 3 );
				tracePreview( "27 after AABB query axis=3 value=" + maxXFixed );

				tracePreview( "28 before AABB query axis=4 MaxY" );
				int maxYFixed = previewMeshRef.getInfo( AABB_QUERY, 4 );
				tracePreview( "29 after AABB query axis=4 value=" + maxYFixed );

				tracePreview( "2A before AABB query axis=5 MaxZ" );
				int maxZFixed = previewMeshRef.getInfo( AABB_QUERY, 5 );
				tracePreview( "2B after AABB query axis=5 value=" + maxZFixed );

				// Diagnostic: compare the live vertex scan against the root bounds
				// accumulated by the original SCX loader at load time.
				tracePreview( "30 before stored root AABB query axis=6 MinX" );
				int rootMinXFixed = previewMeshRef.getInfo( AABB_QUERY, 6 );
				tracePreview( "31 after stored root AABB query axis=6 value=" + rootMinXFixed );

				tracePreview( "32 before stored root AABB query axis=7 MinY" );
				int rootMinYFixed = previewMeshRef.getInfo( AABB_QUERY, 7 );
				tracePreview( "33 after stored root AABB query axis=7 value=" + rootMinYFixed );

				tracePreview( "34 before stored root AABB query axis=8 MinZ" );
				int rootMinZFixed = previewMeshRef.getInfo( AABB_QUERY, 8 );
				tracePreview( "35 after stored root AABB query axis=8 value=" + rootMinZFixed );

				tracePreview( "36 before stored root AABB query axis=9 MaxX" );
				int rootMaxXFixed = previewMeshRef.getInfo( AABB_QUERY, 9 );
				tracePreview( "37 after stored root AABB query axis=9 value=" + rootMaxXFixed );

				tracePreview( "38 before stored root AABB query axis=10 MaxY" );
				int rootMaxYFixed = previewMeshRef.getInfo( AABB_QUERY, 10 );
				tracePreview( "39 after stored root AABB query axis=10 value=" + rootMaxYFixed );

				tracePreview( "3A before stored root AABB query axis=11 MaxZ" );
				int rootMaxZFixed = previewMeshRef.getInfo( AABB_QUERY, 11 );
				tracePreview( "3B after stored root AABB query axis=11 value=" + rootMaxZFixed );

				tracePreview(
					"3C stored loader AABB min=" + rootMinXFixed + "," + rootMinYFixed + "," + rootMinZFixed +
					" max=" + rootMaxXFixed + "," + rootMaxYFixed + "," + rootMaxZFixed
				);
				// Temporary read-only dump of the first two runtime groups.
				// Axes 12..41 return raw native field values, not coordinates:
				// 12=root group count, 13=root group array pointer;
				// 14..27=group 0, 28..41=group 1 (wrapper, inner object,
				// child array/count/capacity, inner vtable/fields, vtable slots).
				for( int diagAxis = 12; diagAxis <= 61; diagAxis++ )
				{
					int diagValue = previewMeshRef.getInfo( AABB_QUERY, diagAxis );
					tracePreview( "GD" + diagAxis + " raw=" + diagValue );
				}

				if( minXFixed != AABB_INVALID && minYFixed != AABB_INVALID && minZFixed != AABB_INVALID &&
					maxXFixed != AABB_INVALID && maxYFixed != AABB_INVALID && maxZFixed != AABB_INVALID )
				{
					float minX = minXFixed / AABB_SCALE;
					float minY = minYFixed / AABB_SCALE;
					float minZ = minZFixed / AABB_SCALE;
					float maxX = maxXFixed / AABB_SCALE;
					float maxY = maxYFixed / AABB_SCALE;
					float maxZ = maxZFixed / AABB_SCALE;

					float sizeX = maxX - minX;
					float sizeY = maxY - minY;
					float sizeZ = maxZ - minZ;
					runtimeModelSize = sizeX;
					if( sizeY > runtimeModelSize ) runtimeModelSize = sizeY;
					if( sizeZ > runtimeModelSize ) runtimeModelSize = sizeZ;

					if( sizeX >= 0.0 && sizeY >= 0.0 && sizeZ >= 0.0 && runtimeModelSize > 0.001 )
					{
						boundsCenterX = (minX + maxX) * 0.5;
						boundsCenterY = (minY + maxY) * 0.5;
						boundsCenterZ = (minZ + maxZ) * 0.5;
						hasRuntimeBounds = 1;
						tracePreview(
							"3D AABB valid meshID=" + previewMeshID +
							" min=" + minXFixed + "," + minYFixed + "," + minZFixed +
							" max=" + maxXFixed + "," + maxYFixed + "," + maxZFixed +
							" center=" + boundsCenterX + "," + boundsCenterY + "," + boundsCenterZ +
							" size=" + runtimeModelSize
						);
					}
					else
						tracePreview( "2D AABB rejected: inconsistent or zero extent" );
				}
				else
					tracePreview( "2E AABB query returned invalid sentinel" );
			}
			else
				tracePreview( "1F AABB skipped: resource is not RESOURCE_MESH (type 5)" );

			// No explicit unload: previewPart owns the mesh resource reference.
			tracePreview( "2F mesh reference scope ended; no separate resource unload" );
		}
		else
			tracePreview( "19 previewPart.getMesh returned 0; AABB skipped" );

		// A nested resource may be a different submesh from Part.getMesh().
		// Use nested centering when it is the actual direct mesh, or when the
		// Part has no direct mesh (e.g. suspension springs). If both resources
		// exist but identify different meshes, retain the established direct
		// Part.getMesh() bounds instead of centering an unrelated engine submesh.
		if( hasRuntimeBounds && hasNestedMeshBounds &&
			previewMeshID && nestedMeshResourceID != previewMeshID )
		{
			hasNestedMeshBounds = 0;
			tracePreview(
				"NESTED_CENTER skipped: nested meshID=" + nestedMeshResourceID +
				" differs from direct meshID=" + previewMeshID
			);
		}

		tracePreview( "30 before previewPart.getPos" );
		savedPosition = previewPart.getPos();
		tracePreview( "31 after previewPart.getPos" );
		if( savedPosition )
			savedPosition = new Vector3( savedPosition );
		else
			savedPosition = new Vector3( 0.0, 0.0, 0.0 );
		tracePreview( "31A savedPosition=" + savedPosition.x + "," + savedPosition.y + "," + savedPosition.z );

		tracePreview( "32 before previewPart.getOri" );
		savedOrientation = previewPart.getOri();
		tracePreview( "33 after previewPart.getOri" );
		if( savedOrientation )
			savedOrientation = new Ypr( savedOrientation );
		else
			savedOrientation = new Ypr( 0.0, 0.0, 0.0 );
		tracePreview( "33A savedOrientation=" + savedOrientation.y + "," + savedOrientation.p + "," + savedOrientation.r );

		yaw = savedOrientation.y;
		pitch = savedOrientation.p;
		roll = savedOrientation.r;

		tracePreview( "34 before previewPart.getInfo(GII_SIZE) fallback" );
		modelSize = previewPart.getInfo( GameType.GII_SIZE ) / 100.0;
		tracePreview( "35 after previewPart.getInfo(GII_SIZE) fallback value=" + modelSize );
		if( modelSize <= 0.0 )
			modelSize = 1.0;

		// Keep the proven legacy GII_SIZE camera scale. The AABB extent is not
		// guaranteed to use the same fitting convention as GII_SIZE.
		tracePreview( "35A camera modelSize keeps GII_SIZE=" + modelSize + " live AABB extent=" + runtimeModelSize + " hasBounds=" + hasRuntimeBounds );

		// Center and fit the isolated preview, leaving the source catalog Part untouched.
		applyPartTransform();
		tracePreview( "35A after applying AABB-centered preview transform" );

		// Independent light: do not reuse the catalog panel's render root.
		tracePreview( "36 before preview RenderRef creation" );
		previewLight = new RenderRef( previewRoot, RID_PREVIEW_LIGHT, null );
		tracePreview( "37 after preview RenderRef creation" );

		tracePreview( "38 before preview Viewport creation" );
		previewViewport = new Viewport( getPreviewViewportPriority(), 0.05, 0.15, 0.43, 0.66 );
		tracePreview( "39 after preview Viewport creation" );
		previewViewport.activate( Viewport.RENDERFLAG_CLEARDEPTH | Viewport.RENDERFLAG_CLEARTARGET );
		tracePreview( "3A after preview Viewport.activate" );
		previewCamera = new Camera( previewRoot, previewViewport, 1, 110, 0.001, 10.0 );
		tracePreview( "3B after preview Camera creation" );

		updateCamera();
		tracePreview( "3C after updateCamera" );

		Vector3 cursorPosition = Input.cursor.getPos();
		if( cursorPosition )
		{
			lastMouseX = cursorPosition.x;
			lastMouseY = cursorPosition.y;
		}

		lastFrameTime = System.currentTime();
		lastWheel = Input.getAxis( 1, 2 );
		dragging = 0;
		autoResume = 1.0;
		tracePreview( "3D createPreview complete" );
	}

	private Vector3 rotateOffsetFromBase( Vector3 offset, Ypr baseOrientation, Ypr targetOrientation )
	{
		// Convert the captured world-space offset back into the Part's reference
		// orientation, then apply the requested orientation. Basis vectors avoid
		// relying on Euler-angle subtraction (which is not a valid inverse for
		// combined yaw/pitch/roll).
		Vector3 baseX = new Vector3( 1.0, 0.0, 0.0 );
		Vector3 baseY = new Vector3( 0.0, 1.0, 0.0 );
		Vector3 baseZ = new Vector3( 0.0, 0.0, 1.0 );
		baseX.rotate( baseOrientation );
		baseY.rotate( baseOrientation );
		baseZ.rotate( baseOrientation );

		float localX = offset.x * baseX.x + offset.y * baseX.y + offset.z * baseX.z;
		float localY = offset.x * baseY.x + offset.y * baseY.y + offset.z * baseY.z;
		float localZ = offset.x * baseZ.x + offset.y * baseZ.y + offset.z * baseZ.z;

		Vector3 targetX = new Vector3( 1.0, 0.0, 0.0 );
		Vector3 targetY = new Vector3( 0.0, 1.0, 0.0 );
		Vector3 targetZ = new Vector3( 0.0, 0.0, 1.0 );
		targetX.rotate( targetOrientation );
		targetY.rotate( targetOrientation );
		targetZ.rotate( targetOrientation );

		return new Vector3(
			localX * targetX.x + localY * targetY.x + localZ * targetZ.x,
			localX * targetX.y + localY * targetY.y + localZ * targetZ.y,
			localX * targetX.z + localY * targetY.z + localZ * targetZ.z
		);
	}

	private void verifyNestedCenterAfterRender( String label )
	{
		if( !previewPart )
			return;

		int nestedRenderInstance = previewPart.getInfo( GameType.GII_RENDERINSTANCE );
		if( !nestedRenderInstance )
		{
			tracePreview( label + " render-instance missing" );
			return;
		}

		GameRef nestedRenderRef = new GameRef( nestedRenderInstance );
		int centerXFixed = nestedRenderRef.getInfo( AABB_QUERY, 500 );
		int centerYFixed = nestedRenderRef.getInfo( AABB_QUERY, 501 );
		int centerZFixed = nestedRenderRef.getInfo( AABB_QUERY, 502 );

		Vector3 partPosition = previewPart.getPos();
		Ypr partOrientation = previewPart.getOri();
		if( !partPosition )
			partPosition = new Vector3( 0.0, 0.0, 0.0 );
		if( !partOrientation )
			partOrientation = new Ypr( 0.0, 0.0, 0.0 );

		if( centerXFixed == AABB_INVALID ||
			centerYFixed == AABB_INVALID ||
			centerZFixed == AABB_INVALID )
		{
			tracePreview(
				label + " invalid=" + centerXFixed + "," + centerYFixed + "," + centerZFixed +
				" partPos=" + partPosition.x + "," + partPosition.y + "," + partPosition.z
			);
			return;
		}

		tracePreview(
			label + " worldCenter=" +
			(centerXFixed / AABB_SCALE) + "," +
			(centerYFixed / AABB_SCALE) + "," +
			(centerZFixed / AABB_SCALE) +
			" partPos=" + partPosition.x + "," + partPosition.y + "," + partPosition.z +
			" partYpr=" + partOrientation.y + "," + partOrientation.p + "," + partOrientation.r
		);
	}

	private void applyPartTransform()
	{
		if( previewPart )
		{
			Ypr targetOrientation = new Ypr( yaw, pitch, roll );
			Vector3 previewPivot = new Vector3(
				savedPosition.x + boundsCenterX,
				savedPosition.y + boundsCenterY,
				savedPosition.z + boundsCenterZ
			);
			previewPivot.rotate( targetOrientation );

			if( hasNestedMeshBounds )
			{
				// Capture the child mesh's center once, after the native render
				// instance has a valid scale. Re-reading a world-space center every
				// frame creates a feedback loop: Java applies a transform based on
				// a render pose that may belong to the previous frame.
				if( !nestedCenterOffsetReady )
				{
					int nestedRenderInstance = previewPart.getInfo( GameType.GII_RENDERINSTANCE );
					if( nestedRenderInstance )
					{
						GameRef nestedRenderRef = new GameRef( nestedRenderInstance );
						int centerXFixed = nestedRenderRef.getInfo( AABB_QUERY, 500 );
						int centerYFixed = nestedRenderRef.getInfo( AABB_QUERY, 501 );
						int centerZFixed = nestedRenderRef.getInfo( AABB_QUERY, 502 );

						if( centerXFixed != AABB_INVALID &&
							centerYFixed != AABB_INVALID &&
							centerZFixed != AABB_INVALID )
						{
							Vector3 currentPartPosition = previewPart.getPos();
							if( !currentPartPosition )
								currentPartPosition = new Vector3( 0.0, 0.0, 0.0 );

							Ypr currentPartOrientation = previewPart.getOri();
							if( !currentPartOrientation )
								currentPartOrientation = new Ypr( savedOrientation );

							float nativeCenterX = centerXFixed / AABB_SCALE;
							float nativeCenterY = centerYFixed / AABB_SCALE;
							float nativeCenterZ = centerZFixed / AABB_SCALE;
							nestedCenterOffsetX = nativeCenterX - currentPartPosition.x;
							nestedCenterOffsetY = nativeCenterY - currentPartPosition.y;
							nestedCenterOffsetZ = nativeCenterZ - currentPartPosition.z;
							nestedCenterBaseYaw = currentPartOrientation.y;
							nestedCenterBasePitch = currentPartOrientation.p;
							nestedCenterBaseRoll = currentPartOrientation.r;
							nestedCenterOffsetReady = 1;

							tracePreview(
								"NESTED_CENTER captured offset=" +
								nestedCenterOffsetX + "," + nestedCenterOffsetY + "," + nestedCenterOffsetZ +
								" baseYpr=" + nestedCenterBaseYaw + "," +
								nestedCenterBasePitch + "," + nestedCenterBaseRoll +
								" nativeWorldCenter=" + nativeCenterX + "," + nativeCenterY + "," + nativeCenterZ +
								" currentPartPos=" + currentPartPosition.x + "," + currentPartPosition.y + "," + currentPartPosition.z
							);
						}
						else
						if( !nestedCenterWaitLogged )
						{
							tracePreview(
								"NESTED_CENTER waiting for render pose; native query invalid"
							);
							nestedCenterWaitLogged = 1;
						}
					}
				}

				if( nestedCenterOffsetReady )
				{
					Vector3 capturedOffset = new Vector3(
						nestedCenterOffsetX,
						nestedCenterOffsetY,
						nestedCenterOffsetZ
					);
					Vector3 rotatedNestedOffset = rotateOffsetFromBase(
						capturedOffset,
						new Ypr( nestedCenterBaseYaw, nestedCenterBasePitch, nestedCenterBaseRoll ),
						targetOrientation
					);

					Vector3 rotatedMountOffset = new Vector3( savedPosition );
					rotatedMountOffset.rotate( targetOrientation );
					previewPivot = new Vector3(
						rotatedMountOffset.x + rotatedNestedOffset.x,
						rotatedMountOffset.y + rotatedNestedOffset.y,
						rotatedMountOffset.z + rotatedNestedOffset.z
					);
				}
			}

			Vector3 previewPosition = new Vector3(
				-previewPivot.x,
				-previewPivot.y,
				-previewPivot.z
			);

			previewPart.setMatrix( previewPosition, targetOrientation );

			// One-shot trace of the exact parent transform requested by Java.
			// This helps distinguish the calculated center from any later native
			// transform or visual-origin discrepancy.
			if( nestedCenterOffsetReady && !nestedCenterApplyLogged )
			{
				Vector3 appliedPartPosition = previewPart.getPos();
				if( !appliedPartPosition )
					appliedPartPosition = new Vector3( 0.0, 0.0, 0.0 );
				tracePreview(
					"NESTED_CENTER_APPLY savedPosition=" + savedPosition.x + "," + savedPosition.y + "," + savedPosition.z +
					" pivot=" + previewPivot.x + "," + previewPivot.y + "," + previewPivot.z +
					" requestedPartPos=" + previewPosition.x + "," + previewPosition.y + "," + previewPosition.z +
					" actualPartPos=" + appliedPartPosition.x + "," + appliedPartPosition.y + "," + appliedPartPosition.z
				);
				nestedCenterApplyLogged = 1;
			}

			// Read-only verification on the following render frame. This does
			// not feed the measured result back into the Part transform.
			if( nestedCenterOffsetReady )
			{
				if( !nestedCenterVerifyInitialDone && !nestedCenterVerifyPending )
					nestedCenterVerifyPending = 1;
				else
				if( nestedCenterVerifyInitialDone && !nestedCenterVerifyRotationDone &&
					!nestedCenterVerifyPending )
				{
					float yawDelta = yaw - nestedCenterBaseYaw;
					float pitchDelta = pitch - nestedCenterBasePitch;
					float rollDelta = roll - nestedCenterBaseRoll;
					if( yawDelta < 0.0 ) yawDelta = -yawDelta;
					if( pitchDelta < 0.0 ) pitchDelta = -pitchDelta;
					if( rollDelta < 0.0 ) rollDelta = -rollDelta;
					if( yawDelta > 0.25 || pitchDelta > 0.25 || rollDelta > 0.25 )
						nestedCenterVerifyPending = 2;
				}
			}
		}
	}

	private void updateCamera()
	{
		if( previewCamera )
		{
			Vector3 cameraPosition = new Vector3( 0.0, modelSize * zoom - modelSize * CAMERA_VERTICAL_OFFSET, modelSize * zoom );
			cameraPosition.rotate( new Ypr( -2.5, 0.0, 0.0 ) );
			previewCamera.setMatrix( cameraPosition, new Ypr( -2.5, -0.7, 0.0 ) );
		}
	}

	public void animate()
	{
		if( !previewPart || !previewCamera )
			return;

		// Verify the result after the previous render pass has consumed
		// setMatrix(). One check at the base pose, one after a meaningful turn.
		if( nestedCenterVerifyPending == 1 )
		{
			verifyNestedCenterAfterRender( "NESTED_CENTER_VERIFY_BASE" );
			nestedCenterVerifyPending = 0;
			nestedCenterVerifyInitialDone = 1;
		}
		else
		if( nestedCenterVerifyPending == 2 )
		{
			verifyNestedCenterAfterRender( "NESTED_CENTER_VERIFY_ROTATED" );
			nestedCenterVerifyPending = 0;
			nestedCenterVerifyRotationDone = 1;
		}

		int traceFrame = 0;
		if( animateTraceFrames < 3 )
		{
			animateTraceFrames++;
			traceFrame = 1;
			tracePreview( "A0 animate begin frame=" + animateTraceFrames );
		}

		float now = System.currentTime();
		if( traceFrame ) tracePreview( "A1 after System.currentTime" );
		float delta = now - lastFrameTime;
		lastFrameTime = now;
		if( delta <= 0.0 || delta > 0.1 )
			delta = 0.016;

		// Read the integrated cursor position, not raw DirectInput mouse deltas.
		Vector3 cursorPosition = Input.cursor.getPos();
		if( traceFrame ) tracePreview( "A2 after Input.cursor.getPos" );
		if( !cursorPosition )
		{
			if( traceFrame ) tracePreview( "A3 cursorPosition is null; animate returned" );
			return;
		}

		float mouseX = cursorPosition.x;
		float mouseY = cursorPosition.y;
		float leftButton = Input.getAxis( 1, 3 );
		if( traceFrame ) tracePreview( "A4 after left mouse Input.getAxis" );
		float wheel = Input.getAxis( 1, 2 );
		if( traceFrame ) tracePreview( "A5 after wheel Input.getAxis" );
		int overPreview = cursorOverPreview( mouseX, mouseY );

		// Keep the reference pose fixed until the nested render child has
		// produced its first valid AABB center. This normally takes only a
		// couple of render frames; unsupported resource paths time out.
		int waitingForNestedCenter = hasNestedMeshBounds &&
			!nestedCenterOffsetReady && nestedCenterWaitFrames < 30;
		if( waitingForNestedCenter )
		{
			nestedCenterWaitFrames++;
			dragging = 0;
			lastMouseX = mouseX;
			lastMouseY = mouseY;
		}
		else
		if( leftButton > 0.5 )
		{
			if( !dragging && overPreview )
			{
				dragging = 1;
				lastMouseX = mouseX;
				lastMouseY = mouseY;
				autoResume = 0.0;
			}
			else
			if( dragging )
			{
				float mouseDeltaX = mouseX - lastMouseX;
				float mouseDeltaY = mouseY - lastMouseY;

				// Ignore tiny cursor noise instead of feeding it into rotation.
				if( mouseDeltaX > -MOUSE_DEAD_ZONE && mouseDeltaX < MOUSE_DEAD_ZONE )
					mouseDeltaX = 0.0;
				if( mouseDeltaY > -MOUSE_DEAD_ZONE && mouseDeltaY < MOUSE_DEAD_ZONE )
					mouseDeltaY = 0.0;

				yaw = yaw + mouseDeltaX * DRAG_YAW_SCALE;
				pitch = pitch + mouseDeltaY * DRAG_PITCH_SCALE;
				if( pitch < -1.1 ) pitch = -1.1;
				if( pitch > 1.1 ) pitch = 1.1;

				lastMouseX = mouseX;
				lastMouseY = mouseY;
			}
		}
		else
		{
			dragging = 0;
			autoResume = autoResume + delta * 2.0;
			if( autoResume > 1.0 ) autoResume = 1.0;
			yaw = yaw + AUTO_ROTATION_SPEED * delta * autoResume;
		}

		// Keep the existing one-step-per-wheel-transition zoom handling.
		if( overPreview )
		{
			if( wheel > 0.2 && lastWheel <= 0.2 )
				zoom = zoom - 0.10;
			else
			if( wheel < -0.2 && lastWheel >= -0.2 )
				zoom = zoom + 0.10;
		}
		lastWheel = wheel;

		if( zoom < MIN_ZOOM ) zoom = MIN_ZOOM;
		if( zoom > MAX_ZOOM ) zoom = MAX_ZOOM;

		if( traceFrame ) tracePreview( "A6 before applyPartTransform" );
		applyPartTransform();
		if( traceFrame ) tracePreview( "A7 after applyPartTransform" );
		updateCamera();
		if( traceFrame ) tracePreview( "A8 after updateCamera" );
	}

	public void show()
	{
		createPreview();
		tracePreview( "40 before super.show" );
		super.show();
		tracePreview( "41 after super.show" );

		tracePreview( "42 before enableAnimateHook" );
		enableAnimateHook();
		tracePreview( "43 after enableAnimateHook" );
	}

	public void hide()
	{
		tracePreview( "H00 hide begin" );
		tracePreview( "H01 before disableAnimateHook" );
		disableAnimateHook();
		tracePreview( "H02 after disableAnimateHook" );

		if( previewCamera )
		{
			tracePreview( "H10 before previewCamera.destroy" );
			previewCamera.destroy();
			previewCamera = null;
			tracePreview( "H11 after previewCamera.destroy" );
		}

		if( previewViewport )
		{
			tracePreview( "H20 before previewViewport.deactivate" );
			previewViewport.deactivate();
			tracePreview( "H21 after previewViewport.deactivate" );
			previewViewport.destroy();
			previewViewport = null;
			tracePreview( "H22 after previewViewport.destroy" );
		}

		if( previewLight )
		{
			tracePreview( "H30 before previewLight.destroy" );
			previewLight.destroy();
			previewLight = null;
			tracePreview( "H31 after previewLight.destroy" );
		}

		if( previewPart )
		{
			tracePreview( "H40 before previewPart.destroy" );
			previewPart.destroy();
			previewPart = null;
			tracePreview( "H41 after previewPart.destroy" );
		}

		if( previewRoot )
		{
			tracePreview( "H50 before previewRoot.destroy" );
			previewRoot.destroy();
			previewRoot = null;
			tracePreview( "H51 after previewRoot.destroy" );
		}

		tracePreview( "H60 before super.hide" );
		super.hide();
		tracePreview( "H61 after super.hide" );
	}
}

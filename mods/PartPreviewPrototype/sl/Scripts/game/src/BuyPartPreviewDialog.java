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
	GameRef orbitRenderRef;

	Vector3 savedPosition;

	float cameraOrbitCenterX;
	float cameraOrbitCenterY;
	float cameraOrbitCenterZ;
	int cameraOrbitCenterReady;
	int cameraOrbitCenterWaitFrames;
	Ypr savedOrientation;

	float modelSize = 1.0;
	float boundsCenterX;
	float boundsCenterY;
	float boundsCenterZ;
	float runtimeBoundsExtent;
	int hasRuntimeBounds;
	float cameraBoundsMinX;
	float cameraBoundsMinY;
	float cameraBoundsMinZ;
	float cameraBoundsMaxX;
	float cameraBoundsMaxY;
	float cameraBoundsMaxZ;
	int cameraBoundsValidCount;
	int cameraBoundsDiscoveredCount;
	float nestedMeshCenterX;
	float nestedMeshCenterY;
	float nestedMeshCenterZ;
	float nestedMeshSizeX;
	float nestedMeshSizeY;
	float nestedMeshSizeZ;
	int hasNestedMeshBounds;
	int nestedMeshResourceID;
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
	int renderTransformTraceFrames;
	int renderTransformTraceReady;
	int renderTransformTraceNodeX;
	int renderTransformTraceNodeY;
	int renderTransformTraceNodeZ;
	int renderTransformTracePositionX;
	int renderTransformTracePositionY;
	int renderTransformTracePositionZ;
	int renderTransformTraceScaleX;
	int renderTransformTraceScaleY;
	int renderTransformTraceScaleZ;
	int renderTransformTraceRecordX;
	int renderTransformTraceRecordY;
	int renderTransformTraceRecordZ;
	int renderTransformTracePivotX;
	int renderTransformTracePivotY;
	int renderTransformTracePivotZ;

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
	// The preview Part itself stays at its original transform.
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

		int firstName0 = renderRef.getInfo( AABB_QUERY, 275 );
		int firstName1 = renderRef.getInfo( AABB_QUERY, 276 );
		tracePreview( prefix + " first-node nameRaw=" + firstName0 + "," + firstName1 );

		int bone00Node = renderRef.getInfo( AABB_QUERY, 294 );
		if( bone00Node == AABB_INVALID )
			tracePreview( prefix + " bone00-node=INVALID" );
		else
			tracePreview( prefix + " bone00-node=" + bone00Node );
		if( bone00Node && bone00Node != AABB_INVALID )
		{
			for( int axis = 295; axis <= 318; axis++ )
			{
				int value = renderRef.getInfo( AABB_QUERY, axis );
				tracePreview( prefix + " bone00 axis=" + axis + " raw=" + value );
			}
		}

		// Probe the exact uppercase spelling independently. Do not assume it
		// exists: the tested VStanced preview currently exposes lowercase bone00.
		int bone00UpperNode = renderRef.getInfo( AABB_QUERY, 489 );
		if( bone00UpperNode == AABB_INVALID )
			tracePreview( prefix + " Bone00-uppercase-node=INVALID" );
		else
			tracePreview( prefix + " Bone00-uppercase-node=" + bone00UpperNode );
		if( bone00UpperNode && bone00UpperNode != AABB_INVALID )
		{
			for( int axis = 490; axis <= 498; axis++ )
			{
				int value = renderRef.getInfo( AABB_QUERY, axis );
				tracePreview( prefix + " Bone00-uppercase matrix axis=" + axis + " raw=" + value );
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
		runtimeBoundsExtent = 0.0;
		hasRuntimeBounds = 0;
		hasNestedMeshBounds = 0;
		nestedMeshResourceID = 0;
		orbitRenderRef = null;
		cameraOrbitCenterX = 0.0;
		cameraOrbitCenterY = 0.0;
		cameraOrbitCenterZ = 0.0;
		cameraOrbitCenterReady = 0;
		cameraOrbitCenterWaitFrames = 0;
		renderTransformTraceFrames = 0;
		renderTransformTraceReady = 0;
		renderTransformTraceNodeX = AABB_INVALID;
		renderTransformTraceNodeY = AABB_INVALID;
		renderTransformTraceNodeZ = AABB_INVALID;
		renderTransformTracePositionX = AABB_INVALID;
		renderTransformTracePositionY = AABB_INVALID;
		renderTransformTracePositionZ = AABB_INVALID;
		renderTransformTraceScaleX = AABB_INVALID;
		renderTransformTraceScaleY = AABB_INVALID;
		renderTransformTraceScaleZ = AABB_INVALID;
		renderTransformTraceRecordX = AABB_INVALID;
		renderTransformTraceRecordY = AABB_INVALID;
		renderTransformTraceRecordZ = AABB_INVALID;
		renderTransformTracePivotX = AABB_INVALID;
		renderTransformTracePivotY = AABB_INVALID;
		renderTransformTracePivotZ = AABB_INVALID;
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
					runtimeBoundsExtent = sizeX;
					if( sizeY > runtimeBoundsExtent ) runtimeBoundsExtent = sizeY;
					if( sizeZ > runtimeBoundsExtent ) runtimeBoundsExtent = sizeZ;

					if( sizeX >= 0.0 && sizeY >= 0.0 && sizeZ >= 0.0 && runtimeBoundsExtent > 0.001 )
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
							" size=" + runtimeBoundsExtent
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
		// Keep this identity check for diagnostics only: bounds no longer select
		// a pivot or alter the Part transform in the camera-orbit preview.
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

		// Cache the root AABB fallback now, before the first camera matrix is set.
		// A later valid native nested-mesh center may replace it once.
		cacheRootBoundsOrbitCenter();

		tracePreview( "34 before previewPart.getInfo(GII_SIZE) fallback" );
		modelSize = previewPart.getInfo( GameType.GII_SIZE ) / 100.0;
		tracePreview( "35 after previewPart.getInfo(GII_SIZE) fallback value=" + modelSize );
		if( modelSize <= 0.0 )
			modelSize = 1.0;

		// GII_SIZE is only a fallback. captureCameraOrbitCenter() will enlarge
		// modelSize when the transformed component bounds prove it is too small.
		tracePreview( "35A camera modelSize initial GII_SIZE=" + modelSize + " live local AABB extent=" + runtimeBoundsExtent + " hasBounds=" + hasRuntimeBounds );

		// Keep the preview Part at its original origin. Rotation is applied to the camera.
		tracePreview( "35A preview Part remains unchanged; camera orbit will control rotation" );

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

		// Defer the first camera matrix until all preview render components
		// have published valid world-space bounds. This avoids showing the model
		// around a guessed origin and then jumping to a late native center.
		tracePreview( "3C camera matrix deferred until render-component bounds are ready" );

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

	// Orbit the camera around the unchanged part instead of moving the part.
	// SLRR's Ypr.mul(other) computes other * this (Math3D.cpp /
	// java_lang_Ypr_mul), so the factors below build:
	// inverse = Rz(-roll) * Rx(-pitch) * Ry(-yaw).
	private Ypr buildInversePreviewOrientation()
	{
		Ypr inverse = new Ypr( -yaw, 0.0, 0.0 );
		inverse.mul( new Ypr( 0.0, -pitch, 0.0 ) );
		inverse.mul( new Ypr( 0.0, 0.0, -roll ) );
		return inverse;
	}

	// Convert the already-captured root mesh AABB center from Part-local
	// coordinates into previewRoot/world coordinates. This is a one-time cache.
	private void cacheRootBoundsOrbitCenter()
	{
		if( hasRuntimeBounds )
		{
			Vector3 rootBoundsCenter = new Vector3(
				boundsCenterX,
				boundsCenterY,
				boundsCenterZ
			);
			rootBoundsCenter.rotate( savedOrientation );

			cameraOrbitCenterX = savedPosition.x + rootBoundsCenter.x;
			cameraOrbitCenterY = savedPosition.y + rootBoundsCenter.y;
			cameraOrbitCenterZ = savedPosition.z + rootBoundsCenter.z;
			tracePreview(
				"CAMERA_ORBIT_CENTER root AABB cached previewRoot=" +
				cameraOrbitCenterX + "," + cameraOrbitCenterY + "," + cameraOrbitCenterZ +
				" local=" + boundsCenterX + "," + boundsCenterY + "," + boundsCenterZ
			);
		}
		else
		{
			cameraOrbitCenterX = 0.0;
			cameraOrbitCenterY = 0.0;
			cameraOrbitCenterZ = 0.0;
			tracePreview( "CAMERA_ORBIT_CENTER root AABB unavailable; provisional origin" );
		}
	}

	// Track the native render-node -> INSTANCE_RENDER initialization.
	// Native 0x5433E0 copies the selected node transform into the render instance
	// after the render record becomes active. Log only the first few frames and
	// any later changes, keeping the probe read-only and bounded.
	private void traceNativeTransformTimeline()
	{
		if( renderTransformTraceFrames >= 45 )
			return;

		if( !orbitRenderRef )
		{
			int renderInstance = previewPart.getInfo( GameType.GII_RENDERINSTANCE );
			if( renderInstance )
				orbitRenderRef = new GameRef( renderInstance );
		}
		if( !orbitRenderRef )
			return;

		// Match the native 0x5433E0 node-selection branch.
		int auxiliaryListEmpty = orbitRenderRef.getInfo( AABB_QUERY, 273 );
		int nodeTranslationX = AABB_INVALID;
		int nodeTranslationY = AABB_INVALID;
		int nodeTranslationZ = AABB_INVALID;
		if( auxiliaryListEmpty == 1 )
		{
			nodeTranslationX = orbitRenderRef.getInfo( AABB_QUERY, 290 );
			nodeTranslationY = orbitRenderRef.getInfo( AABB_QUERY, 291 );
			nodeTranslationZ = orbitRenderRef.getInfo( AABB_QUERY, 292 );
		}
		else
		if( auxiliaryListEmpty == 0 )
		{
			nodeTranslationX = orbitRenderRef.getInfo( AABB_QUERY, 307 );
			nodeTranslationY = orbitRenderRef.getInfo( AABB_QUERY, 308 );
			nodeTranslationZ = orbitRenderRef.getInfo( AABB_QUERY, 309 );
		}

		// Raw IEEE-754 DWORDs from INSTANCE_RENDER and the selected render record.
		int instancePositionX = orbitRenderRef.getInfo( AABB_QUERY, 173 );
		int instancePositionY = orbitRenderRef.getInfo( AABB_QUERY, 174 );
		int instancePositionZ = orbitRenderRef.getInfo( AABB_QUERY, 175 );
		int instanceScaleX = orbitRenderRef.getInfo( AABB_QUERY, 176 );
		int instanceScaleY = orbitRenderRef.getInfo( AABB_QUERY, 177 );
		int instanceScaleZ = orbitRenderRef.getInfo( AABB_QUERY, 178 );
		int renderRecordTranslationX = orbitRenderRef.getInfo( AABB_QUERY, 135 );
		int renderRecordTranslationY = orbitRenderRef.getInfo( AABB_QUERY, 136 );
		int renderRecordTranslationZ = orbitRenderRef.getInfo( AABB_QUERY, 137 );

		// These three axes return the actual native INSTANCE_RENDER origin in
		// fixed-point world units (*1000), separately from AABB center axes 500..502.
		int nativePivotX = orbitRenderRef.getInfo( AABB_QUERY, 510 );
		int nativePivotY = orbitRenderRef.getInfo( AABB_QUERY, 511 );
		int nativePivotZ = orbitRenderRef.getInfo( AABB_QUERY, 512 );

		int changed = 0;
		if( !renderTransformTraceReady )
			changed = 1;
		else
		if( nodeTranslationX != renderTransformTraceNodeX ||
			nodeTranslationY != renderTransformTraceNodeY ||
			nodeTranslationZ != renderTransformTraceNodeZ ||
			instancePositionX != renderTransformTracePositionX ||
			instancePositionY != renderTransformTracePositionY ||
			instancePositionZ != renderTransformTracePositionZ ||
			instanceScaleX != renderTransformTraceScaleX ||
			instanceScaleY != renderTransformTraceScaleY ||
			instanceScaleZ != renderTransformTraceScaleZ ||
			renderRecordTranslationX != renderTransformTraceRecordX ||
			renderRecordTranslationY != renderTransformTraceRecordY ||
			renderRecordTranslationZ != renderTransformTraceRecordZ ||
			nativePivotX != renderTransformTracePivotX ||
			nativePivotY != renderTransformTracePivotY ||
			nativePivotZ != renderTransformTracePivotZ )
			changed = 1;

		if( renderTransformTraceFrames < 4 || changed )
		{
			String selectedNodeText = "INVALID";
			if( nodeTranslationX != AABB_INVALID &&
				nodeTranslationY != AABB_INVALID &&
				nodeTranslationZ != AABB_INVALID )
				selectedNodeText = "" + nodeTranslationX + "," + nodeTranslationY + "," + nodeTranslationZ;

			String nativePivotText = "INVALID";
			if( nativePivotX != AABB_INVALID &&
				nativePivotY != AABB_INVALID &&
				nativePivotZ != AABB_INVALID )
				nativePivotText = "" + nativePivotX + "," + nativePivotY + "," + nativePivotZ;

			tracePreview(
				"TRANSFORM_TIMELINE frame=" + renderTransformTraceFrames +
				" auxListEmpty=" + auxiliaryListEmpty +
				" recordTraw=" + renderRecordTranslationX + "," + renderRecordTranslationY + "," + renderRecordTranslationZ +
				" selectedNodeTraw=" + selectedNodeText +
				" instancePosRaw=" + instancePositionX + "," + instancePositionY + "," + instancePositionZ +
				" instanceScaleRaw=" + instanceScaleX + "," + instanceScaleY + "," + instanceScaleZ +
				" nativePivotFixed=" + nativePivotText
			);
		}

		renderTransformTraceNodeX = nodeTranslationX;
		renderTransformTraceNodeY = nodeTranslationY;
		renderTransformTraceNodeZ = nodeTranslationZ;
		renderTransformTracePositionX = instancePositionX;
		renderTransformTracePositionY = instancePositionY;
		renderTransformTracePositionZ = instancePositionZ;
		renderTransformTraceScaleX = instanceScaleX;
		renderTransformTraceScaleY = instanceScaleY;
		renderTransformTraceScaleZ = instanceScaleZ;
		renderTransformTraceRecordX = renderRecordTranslationX;
		renderTransformTraceRecordY = renderRecordTranslationY;
		renderTransformTraceRecordZ = renderRecordTranslationZ;
		renderTransformTracePivotX = nativePivotX;
		renderTransformTracePivotY = nativePivotY;
		renderTransformTracePivotZ = nativePivotZ;
		renderTransformTraceReady = 1;
		renderTransformTraceFrames++;
	}

	// Add one render component to the current assembly bounds. This uses no
	// Java arrays: the SLRR script parser treats array signatures inconsistently.
	private void addCameraRenderComponent( int renderInstanceID, int rootRenderInstance )
	{
		if( !renderInstanceID )
			return;
		if( renderInstanceID == rootRenderInstance && cameraBoundsDiscoveredCount > 0 )
			return;

		cameraBoundsDiscoveredCount++;
		GameRef componentRenderRef = new GameRef( renderInstanceID );
		int centerXFixed = componentRenderRef.getInfo( AABB_QUERY, 500 );
		int centerYFixed = componentRenderRef.getInfo( AABB_QUERY, 501 );
		int centerZFixed = componentRenderRef.getInfo( AABB_QUERY, 502 );

		if( centerXFixed == AABB_INVALID ||
			centerYFixed == AABB_INVALID ||
			centerZFixed == AABB_INVALID )
		{
			if( cameraOrbitCenterWaitFrames == 1 || cameraOrbitCenterWaitFrames >= 60 )
				tracePreview(
					"CAMERA_ORBIT_CENTER component id=" + renderInstanceID +
					" bounds=PENDING values=" + centerXFixed + "," + centerYFixed + "," + centerZFixed
				);
			return;
		}

		float centerX = centerXFixed / AABB_SCALE;
		float centerY = centerYFixed / AABB_SCALE;
		float centerZ = centerZFixed / AABB_SCALE;
		float componentRadius = queryRenderComponentRadius( componentRenderRef );
		if( componentRadius < 0.0 )
		{
			if( cameraOrbitCenterWaitFrames == 1 || cameraOrbitCenterWaitFrames >= 60 )
				tracePreview( "CAMERA_ORBIT_CENTER component id=" + renderInstanceID + " radius unavailable; center-only fallback" );
			componentRadius = 0.0;
		}

		if( cameraOrbitCenterWaitFrames == 1 || cameraOrbitCenterWaitFrames >= 60 )
			tracePreview(
				"CAMERA_ORBIT_CENTER component id=" + renderInstanceID +
				" center=" + centerX + "," + centerY + "," + centerZ +
				" radius=" + componentRadius
			);

		if( cameraBoundsValidCount == 0 )
		{
			cameraBoundsMinX = centerX - componentRadius;
			cameraBoundsMinY = centerY - componentRadius;
			cameraBoundsMinZ = centerZ - componentRadius;
			cameraBoundsMaxX = centerX + componentRadius;
			cameraBoundsMaxY = centerY + componentRadius;
			cameraBoundsMaxZ = centerZ + componentRadius;
		}
		else
		{
			if( centerX - componentRadius < cameraBoundsMinX ) cameraBoundsMinX = centerX - componentRadius;
			if( centerY - componentRadius < cameraBoundsMinY ) cameraBoundsMinY = centerY - componentRadius;
			if( centerZ - componentRadius < cameraBoundsMinZ ) cameraBoundsMinZ = centerZ - componentRadius;
			if( centerX + componentRadius > cameraBoundsMaxX ) cameraBoundsMaxX = centerX + componentRadius;
			if( centerY + componentRadius > cameraBoundsMaxY ) cameraBoundsMaxY = centerY + componentRadius;
			if( centerZ + componentRadius > cameraBoundsMaxZ ) cameraBoundsMaxZ = centerZ + componentRadius;
		}
		cameraBoundsValidCount++;

		if( renderInstanceID == rootRenderInstance )
			traceRenderNativePose( componentRenderRef, "CAMERA_ORBIT_CENTER_ROOT" );
	}

	// Conservative local-AABB sphere radius after INSTANCE_RENDER scale.
	// The sum of half-extents is used instead of Math.sqrt to stay compatible
	// with the old SLRR Java runtime while still enclosing all AABB corners.
	private float queryRenderComponentRadius( GameRef componentRenderRef )
	{
		int meshID = componentRenderRef.getInfo( AABB_QUERY, 439 );
		if( !meshID || meshID == AABB_INVALID )
			return -1.0;

		GameRef meshRef = new GameRef( meshID );
		if( meshRef.type() != 5 )
			return -1.0;

		int minXFixed = meshRef.getInfo( AABB_QUERY, 0 );
		int minYFixed = meshRef.getInfo( AABB_QUERY, 1 );
		int minZFixed = meshRef.getInfo( AABB_QUERY, 2 );
		int maxXFixed = meshRef.getInfo( AABB_QUERY, 3 );
		int maxYFixed = meshRef.getInfo( AABB_QUERY, 4 );
		int maxZFixed = meshRef.getInfo( AABB_QUERY, 5 );
		int scaleXFixed = componentRenderRef.getInfo( AABB_QUERY, 513 );
		int scaleYFixed = componentRenderRef.getInfo( AABB_QUERY, 514 );
		int scaleZFixed = componentRenderRef.getInfo( AABB_QUERY, 515 );

		if( minXFixed == AABB_INVALID || minYFixed == AABB_INVALID || minZFixed == AABB_INVALID ||
			maxXFixed == AABB_INVALID || maxYFixed == AABB_INVALID || maxZFixed == AABB_INVALID ||
			scaleXFixed == AABB_INVALID || scaleYFixed == AABB_INVALID || scaleZFixed == AABB_INVALID )
			return -1.0;

		float scaleX = scaleXFixed / AABB_SCALE;
		float scaleY = scaleYFixed / AABB_SCALE;
		float scaleZ = scaleZFixed / AABB_SCALE;
		if( scaleX < 0.0 ) scaleX = -scaleX;
		if( scaleY < 0.0 ) scaleY = -scaleY;
		if( scaleZ < 0.0 ) scaleZ = -scaleZ;

		float halfX = (maxXFixed - minXFixed) / 2000.0 * scaleX;
		float halfY = (maxYFixed - minYFixed) / 2000.0 * scaleY;
		float halfZ = (maxZFixed - minZFixed) / 2000.0 * scaleZ;
		return halfX + halfY + halfZ;
	}

	// Query root + direct + one-level nested type-3 children without arrays.
	// Native AABB center axes are already in previewRoot coordinates.
	private int captureCameraOrbitCenter()
	{
		if( cameraOrbitCenterReady )
			return 1;

		cameraOrbitCenterWaitFrames++;
		cameraBoundsValidCount = 0;
		cameraBoundsDiscoveredCount = 0;

		int rootRenderInstance = previewPart.getInfo( GameType.GII_RENDERINSTANCE );
		if( rootRenderInstance )
		{
			orbitRenderRef = new GameRef( rootRenderInstance );
			addCameraRenderComponent( rootRenderInstance, rootRenderInstance );
		}

		ResourceRef child = previewPart.getFirstChild();
		int childIndex = 0;
		while( child && childIndex < 64 )
		{
			if( child.type() == 3 )
			{
				if( child.id() != rootRenderInstance )
					addCameraRenderComponent( child.id(), rootRenderInstance );
			}

			ResourceRef nested = child.getFirstChild();
			int nestedIndex = 0;
			while( nested && nestedIndex < 32 )
			{
				if( nested.type() == 3 )
				{
					if( nested.id() != rootRenderInstance )
						addCameraRenderComponent( nested.id(), rootRenderInstance );
				}
				nested = nested.getNextChild();
				nestedIndex++;
			}
			child = child.getNextChild();
			childIndex++;
		}

		if( cameraOrbitCenterWaitFrames == 1 || cameraOrbitCenterWaitFrames >= 60 )
			tracePreview(
				"CAMERA_ORBIT_CENTER components discovered=" + cameraBoundsDiscoveredCount +
				" valid=" + cameraBoundsValidCount +
			" rootRenderInstance=" + rootRenderInstance
			);

		if( cameraBoundsValidCount > 0 &&
			(cameraBoundsValidCount == cameraBoundsDiscoveredCount || cameraOrbitCenterWaitFrames >= 60) )
		{
			cameraOrbitCenterX = (cameraBoundsMinX + cameraBoundsMaxX) * 0.5;
			cameraOrbitCenterY = (cameraBoundsMinY + cameraBoundsMaxY) * 0.5;
			cameraOrbitCenterZ = (cameraBoundsMinZ + cameraBoundsMaxZ) * 0.5;

			float halfX = (cameraBoundsMaxX - cameraBoundsMinX) * 0.5;
			float halfY = (cameraBoundsMaxY - cameraBoundsMinY) * 0.5;
			float halfZ = (cameraBoundsMaxZ - cameraBoundsMinZ) * 0.5;
			float assemblyRadius = halfX + halfY + halfZ;
			float previousModelSize = modelSize;
			if( assemblyRadius > modelSize )
				modelSize = assemblyRadius;

			tracePreview(
				"CAMERA_FIT assemblyCenter=" + cameraOrbitCenterX + "," + cameraOrbitCenterY + "," + cameraOrbitCenterZ +
				" assemblyRadius=" + assemblyRadius +
				" oldGII_SIZE=" + previousModelSize +
				" cameraModelSize=" + modelSize +
				" valid=" + cameraBoundsValidCount + " discovered=" + cameraBoundsDiscoveredCount
			);
			cameraOrbitCenterReady = 1;
			return 1;
		}

		if( cameraOrbitCenterWaitFrames >= 60 )
		{
			if( hasRuntimeBounds && runtimeBoundsExtent * 0.5 > modelSize )
				modelSize = runtimeBoundsExtent * 0.5;
			tracePreview(
				"CAMERA_FIT timeout; fallback modelSize=" + modelSize +
				" valid=" + cameraBoundsValidCount + " discovered=" + cameraBoundsDiscoveredCount
			);
			cameraOrbitCenterReady = 1;
			return 1;
		}

		return 0;
	}

	private void updateCamera()
	{
		if( previewCamera )
		{
			Ypr inversePartOrientation = buildInversePreviewOrientation();

			// Keep the legacy camera vector as a radius from the orbit center.
			// Translate the camera's base pose to the AABB center, then rotate
			// that radius around the center without moving the preview Part.
			Vector3 cameraOffset = new Vector3(
				0.0,
				modelSize * zoom - modelSize * CAMERA_VERTICAL_OFFSET,
				modelSize * zoom
			);
			cameraOffset.rotate( new Ypr( -2.5, 0.0, 0.0 ) );
			cameraOffset.rotate( inversePartOrientation );

			Vector3 cameraPosition = new Vector3(
				cameraOrbitCenterX + cameraOffset.x,
				cameraOrbitCenterY + cameraOffset.y,
				cameraOrbitCenterZ + cameraOffset.z
			);

			// Rotate the camera basis by the same inverse rotation.
			Ypr cameraOrientation = new Ypr( -2.5, -0.7, 0.0 );
			cameraOrientation.mul( inversePartOrientation );
			previewCamera.setMatrix( cameraPosition, cameraOrientation );
		}
	}

	public void animate()
	{
		if( !previewPart || !previewCamera )
			return;

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

		traceNativeTransformTimeline();

		// Wait for the native render instance to publish its first valid
		// world-space AABB. Do not rotate around a provisional origin.
		if( !captureCameraOrbitCenter() )
		{
			dragging = 0;
			autoResume = 0.0;
			lastMouseX = mouseX;
			lastMouseY = mouseY;
			lastWheel = wheel;
			// Keep the camera untouched while native component bounds are pending.
			return;
		}

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

		updateCamera();
		if( traceFrame ) tracePreview( "A6 after camera orbit update" );
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

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
	Dummy cameraOrbitPivot;
	RenderRef previewLight;
	Viewport previewViewport;
	Camera previewCamera;
	GameRef orbitRenderRef;

	Vector3 savedPosition;

	float cameraOrbitCenterX;
	float cameraOrbitCenterY;
	float cameraOrbitCenterZ;
	int cameraOrbitCenterReady;
	int cameraOrbitPivotReady;
	int cameraOrbitCenterWaitFrames;
	int cameraOrbitCandidateReady;
	int cameraOrbitCandidateXFixed;
	int cameraOrbitCandidateYFixed;
	int cameraOrbitCandidateZFixed;
	int cameraOrbitDiagnosticsLogged;
	int cameraAimDiagnosticsLogged;
	int cameraNativePoseInitialLogged;
	int cameraNativePoseChangeLogged;
	int cameraNativePoseChangePending;
	int previewMeshResourceID;
	Ypr savedOrientation;

	float modelSize = 1.0;
	float boundsCenterX;
	float boundsCenterY;
	float boundsCenterZ;
	float runtimeBoundsExtent;
	int hasRuntimeBounds;
	float nestedMeshCenterX;
	float nestedMeshCenterY;
	float nestedMeshCenterZ;
	float nestedMeshSizeX;
	float nestedMeshSizeY;
	float nestedMeshSizeZ;
	int hasNestedMeshBounds;
	int nestedMeshResourceID;
	int cameraFitApplied;
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
	// Target camera distance: 1.5 times the native scaled-AABB bounding-sphere radius.
	final static float CAMERA_FIT_DISTANCE_RATIO = 1.50;
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

	private void traceRenderRecordDiagnostics( GameRef renderRef, String prefix )
	{
		// Axes 120..156 expose the first two linked render records.
		// A nonzero axis 140 means a third record exists, but this EXE build
		// does not yet expose its fields.
		for( int axis = 120; axis <= 156; axis++ )
		{
			int value = renderRef.getInfo( AABB_QUERY, axis );
			String field = "unknown";
			if( axis == 120 ) field = "record0 pointer";
			if( axis == 121 ) field = "record0 resource key";
			if( axis == 122 ) field = "record0 next";
			if( axis >= 123 && axis <= 138 )
				field = "record0 matrix[" + (axis - 123) + "]";
			if( axis == 139 ) field = "record1 resource key";
			if( axis == 140 ) field = "record1 next / record2 pointer";
			if( axis >= 141 && axis <= 156 )
				field = "record1 matrix[" + (axis - 141) + "]";
			tracePreview( prefix + " axis=" + axis + " " + field + " raw=" + value );
		}
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

	// Read back the actual INSTANCE_RENDER pose owned by the preview Camera.
	// This is diagnostic only: it does not write to the camera or the Part.
	private void traceCameraNativePose( String phase )
	{
		int cameraResourceID = previewCamera.id();
		tracePreview(
			"CAMERA_NATIVE phase=" + phase +
			" resourceID=" + cameraResourceID
		);
		if( !cameraResourceID )
			return;

		GameRef cameraGameRef = new GameRef( cameraResourceID );
		int cameraType = cameraGameRef.type();
		int cameraParentID = cameraGameRef.getParentID();
		tracePreview(
			"CAMERA_NATIVE wrapperID=" + cameraGameRef.id() +
			" type=" + cameraType +
			" parentID=" + cameraParentID +
			" expectedPivotID=" + cameraOrbitPivot.id()
		);

		// The camera's setMatrix position is local to previewRoot. The orbit
		// center from the render instance is in root/world coordinates, so
		// record the parent transform before comparing those coordinate spaces.
		Vector3 rootPosition = previewRoot.getPos();
		Ypr rootOrientation = previewRoot.getOri();
		tracePreview(
			"CAMERA_NATIVE_ROOT pos=" +
			rootPosition.x + "," + rootPosition.y + "," + rootPosition.z +
			" ypr=" + rootOrientation.y + "," +
			rootOrientation.p + "," + rootOrientation.r
		);
		Vector3 pivotPosition = cameraOrbitPivot.getPos();
		Ypr pivotOrientation = cameraOrbitPivot.getOri();
		if( pivotPosition && pivotOrientation )
			tracePreview(
				"CAMERA_NATIVE_PIVOT pos=" +
				pivotPosition.x + "," + pivotPosition.y + "," + pivotPosition.z +
				" ypr=" + pivotOrientation.y + "," +
				pivotOrientation.p + "," + pivotOrientation.r
			);
		else
			tracePreview( "CAMERA_NATIVE_PIVOT readback contains null" );
		Vector3 partPosition = previewPart.getPos();
		Ypr partOrientation = previewPart.getOri();
		tracePreview(
			"CAMERA_NATIVE_PART pos=" +
			partPosition.x + "," + partPosition.y + "," + partPosition.z +
			" ypr=" + partOrientation.y + "," +
			partOrientation.p + "," + partOrientation.r
		);
		if( cameraType != 3 )
		{
			tracePreview( "CAMERA_NATIVE readback skipped: wrapper is not INSTANCE_RENDER" );
			return;
		}

		// Axes 172..178 expose the live instance position and scale as raw float bits.
		for( int axis = 172; axis <= 178; axis++ )
		{
			int value = cameraGameRef.getInfo( AABB_QUERY, axis );
			tracePreview(
				"CAMERA_NATIVE instance axis=" + axis +
				" rawFloatBits=" + value
			);
		}

		int auxiliaryListEmpty = cameraGameRef.getInfo( AABB_QUERY, 273 );
		int firstNode = cameraGameRef.getInfo( AABB_QUERY, 274 );
		tracePreview(
			"CAMERA_NATIVE nodeSelection auxiliaryListEmpty=" + auxiliaryListEmpty +
			" firstNode=" + firstNode
		);
		if( firstNode && firstNode != AABB_INVALID )
		{
			for( int axis = 278; axis <= 293; axis++ )
			{
				int value = cameraGameRef.getInfo( AABB_QUERY, axis );
				tracePreview(
					"CAMERA_NATIVE firstNode matrix[" + (axis - 278) +
					"] rawFloatBits=" + value
				);
			}
		}

		int bone00Node = cameraGameRef.getInfo( AABB_QUERY, 294 );
		tracePreview( "CAMERA_NATIVE bone00Node=" + bone00Node );
		if( bone00Node && bone00Node != AABB_INVALID )
		{
			for( int axis = 295; axis <= 310; axis++ )
			{
				int value = cameraGameRef.getInfo( AABB_QUERY, axis );
				tracePreview(
					"CAMERA_NATIVE bone00 matrix[" + (axis - 295) +
					"] rawFloatBits=" + value
				);
			}
		}

		// Axes 510..512 are the native INSTANCE_RENDER pivot position (*1000).
		for( int axis = 510; axis <= 512; axis++ )
		{
			int value = cameraGameRef.getInfo( AABB_QUERY, axis );
			tracePreview(
				"CAMERA_NATIVE nativePosition axis=" + axis +
				" fixed=" + value
			);
		}

		traceRenderRecordDiagnostics( cameraGameRef, "CAMERA_NATIVE render-record" );
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




	// One-shot snapshot immediately before the first native center query.
	// Captures the exact pose and render-list nodes used by axes 500..502.
	private void traceCameraOrbitInputs( GameRef renderRef )
	{
		tracePreview( "CAMERA_ORBIT_DIAG ===== runtime inputs frame=" + cameraOrbitCenterWaitFrames + " =====" );
		tracePreview(
			"CAMERA_ORBIT_DIAG resources directMesh=" + previewMeshResourceID +
			" nestedMesh=" + nestedMeshResourceID +
			" hasNestedBounds=" + hasNestedMeshBounds
		);
		tracePreview(
			"CAMERA_ORBIT_DIAG nested local AABB center=" +
			nestedMeshCenterX + "," + nestedMeshCenterY + "," + nestedMeshCenterZ +
			" size=" + nestedMeshSizeX + "," + nestedMeshSizeY + "," + nestedMeshSizeZ
		);
		tracePreview(
			"CAMERA_ORBIT_DIAG direct local AABB center=" +
			boundsCenterX + "," + boundsCenterY + "," + boundsCenterZ +
			" extent=" + runtimeBoundsExtent + " hasRuntimeBounds=" + hasRuntimeBounds
		);
		tracePreview(
			"CAMERA_ORBIT_DIAG initial Part transform pos=" +
			savedPosition.x + "," + savedPosition.y + "," + savedPosition.z +
			" ypr=" + savedOrientation.y + "," + savedOrientation.p + "," + savedOrientation.r
		);

		// Axes 172..178 expose INSTANCE_RENDER +0x80..+0x98.
		// +0x84..+0x8c are position; +0x90..+0x98 are per-axis scale.
		for( int axis = 172; axis <= 178; axis++ )
		{
			int value = renderRef.getInfo( AABB_QUERY, axis );
			String field = "unknown";
			if( axis == 172 ) field = "handle+0x80";
			if( axis == 173 ) field = "position.x";
			if( axis == 174 ) field = "position.y";
			if( axis == 175 ) field = "position.z";
			if( axis == 176 ) field = "scale.x";
			if( axis == 177 ) field = "scale.y";
			if( axis == 178 ) field = "scale.z";
			tracePreview( "CAMERA_ORBIT_DIAG instance " + field + " axis=" + axis + " rawFloatBits=" + value );
		}

		// Empty auxiliary list selects the first node; otherwise native code
		// scans the list for a lowercase "bone00" entry.
		int auxiliaryListEmpty = renderRef.getInfo( AABB_QUERY, 273 );
		int firstNode = renderRef.getInfo( AABB_QUERY, 274 );
		tracePreview(
			"CAMERA_ORBIT_DIAG node selection auxiliaryListEmpty=" + auxiliaryListEmpty +
			" firstNode=" + firstNode
		);
		if( firstNode && firstNode != AABB_INVALID )
		{
			for( int axis = 275; axis <= 277; axis++ )
			{
				int value = renderRef.getInfo( AABB_QUERY, axis );
				tracePreview( "CAMERA_ORBIT_DIAG firstNode header axis=" + axis + " raw=" + value );
			}
			for( int axis = 278; axis <= 293; axis++ )
			{
				int value = renderRef.getInfo( AABB_QUERY, axis );
				tracePreview(
					"CAMERA_ORBIT_DIAG firstNode matrix[" + (axis - 278) +
					"] axis=" + axis + " rawFloatBits=" + value
				);
			}
		}
		else
			tracePreview( "CAMERA_ORBIT_DIAG firstNode matrix unavailable" );

		int bone00Node = renderRef.getInfo( AABB_QUERY, 294 );
		int selectedNode = 0;
		if( auxiliaryListEmpty )
			selectedNode = firstNode;
		else
			selectedNode = bone00Node;
		tracePreview(
			"CAMERA_ORBIT_DIAG lowercase bone00 node=" + bone00Node +
			" selectedNode=" + selectedNode
		);
		if( bone00Node && bone00Node != AABB_INVALID )
		{
			for( int axis = 295; axis <= 310; axis++ )
			{
				int value = renderRef.getInfo( AABB_QUERY, axis );
				tracePreview(
					"CAMERA_ORBIT_DIAG bone00 matrix[" + (axis - 295) +
					"] axis=" + axis + " rawFloatBits=" + value
				);
			}
		}
		else
			tracePreview( "CAMERA_ORBIT_DIAG bone00 matrix unavailable" );

		// Read the next node in the native bone list. The local AABB scan sees
		// unskinned XYZ positions only; this matrix lets us determine whether a
		// second bone (for example bone01) contributes to the visible mesh.
		int secondBoneNode = renderRef.getInfo( AABB_QUERY, 516 );
		tracePreview( "CAMERA_ORBIT_DIAG secondBone node=" + secondBoneNode );
		if( secondBoneNode && secondBoneNode != AABB_INVALID )
		{
			int secondName0 = renderRef.getInfo( AABB_QUERY, 517 );
			int secondName1 = renderRef.getInfo( AABB_QUERY, 518 );
			tracePreview(
				"CAMERA_ORBIT_DIAG secondBone nameRaw=" +
				secondName0 + "," + secondName1
			);
			for( int axis = 519; axis <= 534; axis++ )
			{
				int value = renderRef.getInfo( AABB_QUERY, axis );
				tracePreview(
					"CAMERA_ORBIT_DIAG secondBone matrix[" + (axis - 519) +
					"] rawFloatBits=" + value
				);
			}
			int thirdBoneNode = renderRef.getInfo( AABB_QUERY, 535 );
			tracePreview( "CAMERA_ORBIT_DIAG thirdBone node=" + thirdBoneNode );
		}
		else
			tracePreview( "CAMERA_ORBIT_DIAG secondBone unavailable" );

		traceRenderRecordDiagnostics( renderRef, "CAMERA_ORBIT render-record" );
		tracePreview( "CAMERA_ORBIT_DIAG ===== end runtime inputs =====" );
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
			for( int axis = 12; axis <= 61; axis++ )
			{
				int value = nestedMeshRef.getInfo( AABB_QUERY, axis );
				tracePreview( prefix + " nested mesh group diagnostic axis=" + axis + " raw=" + value );
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

	// Read-only summary for every direct INSTANCE_RENDER child of a Part.
	// The active center path currently starts from GII_RENDERINSTANCE (one child);
	// this probe shows whether other render children own additional mesh AABBs.
	private void traceRenderChildCenterSummary( GameRef renderRef, String prefix )
	{
		tracePreview(
			prefix + " id=" + renderRef.id() +
			" type=" + renderRef.type() +
			" parentID=" + renderRef.getParentID()
		);

		int childResource = renderRef.getInfo( AABB_QUERY, 418 );
		int nestedMeshID = 0;
		if( childResource && childResource != AABB_INVALID )
			nestedMeshID = renderRef.getInfo( AABB_QUERY, 439 );

		int collection = renderRef.getInfo( AABB_QUERY, 472 );
		tracePreview(
			prefix + " childResource=" + childResource +
			" nestedMeshID=" + nestedMeshID +
			" collection=" + collection
		);

		if( nestedMeshID > 0 )
		{
			GameRef meshRef = new GameRef( nestedMeshID );
			int meshType = meshRef.type();
			tracePreview( prefix + " meshType=" + meshType + " meshID=" + meshRef.id() );
			if( meshType == 5 )
			{
				int minX = meshRef.getInfo( AABB_QUERY, 0 );
				int minY = meshRef.getInfo( AABB_QUERY, 1 );
				int minZ = meshRef.getInfo( AABB_QUERY, 2 );
				int maxX = meshRef.getInfo( AABB_QUERY, 3 );
				int maxY = meshRef.getInfo( AABB_QUERY, 4 );
				int maxZ = meshRef.getInfo( AABB_QUERY, 5 );
				int centerX = renderRef.getInfo( AABB_QUERY, 536 );
				int centerY = renderRef.getInfo( AABB_QUERY, 537 );
				int centerZ = renderRef.getInfo( AABB_QUERY, 538 );
				tracePreview(
					prefix + " meshAABB min=" + minX + "," + minY + "," + minZ +
					" max=" + maxX + "," + maxY + "," + maxZ +
					" transformedCenter=" + centerX + "," + centerY + "," + centerZ
				);
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

			// Log both records, including the second record's resource key and matrix.
			traceRenderRecordDiagnostics( renderGameRef, "R06 render-record" );
		}

		// ResourceRef exposes a generic child/sibling traversal API. Dump only
		// a bounded portion of the preview Part tree; this is read-only.
		ResourceRef child = previewPart.getFirstChild();
		int childIndex = 0;
		while( child && childIndex < 32 )
		{
			traceResourceNode( child, "R10 child[" + childIndex + "]" );
			if( child.type() == 3 )
			{
				GameRef childRenderRef = new GameRef( child.id() );
				traceRenderChildCenterSummary(
					childRenderRef,
					"R31 renderChild[" + childIndex + "]"
				);
			}

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
		cameraFitApplied = 0;
		orbitRenderRef = null;
		cameraOrbitPivot = null;
		cameraOrbitPivotReady = 0;
		cameraOrbitCenterX = 0.0;
		cameraOrbitCenterY = 0.0;
		cameraOrbitCenterZ = 0.0;
		cameraOrbitCenterReady = 0;
		cameraOrbitCenterWaitFrames = 0;
		cameraOrbitCandidateReady = 0;
		cameraOrbitCandidateXFixed = AABB_INVALID;
		cameraOrbitCandidateYFixed = AABB_INVALID;
		cameraOrbitCandidateZFixed = AABB_INVALID;
		cameraOrbitDiagnosticsLogged = 0;
		cameraAimDiagnosticsLogged = 0;
		cameraNativePoseInitialLogged = 0;
		cameraNativePoseChangeLogged = 0;
		cameraNativePoseChangePending = 0;
		previewMeshResourceID = 0;
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

		// The camera gets its own transform parent at the visual orbit center.
		// The preview Part remains a sibling under previewRoot and is not moved.
		tracePreview( "06A before camera orbit pivot creation" );
		cameraOrbitPivot = new Dummy( previewRoot );
		tracePreview( "06B after camera orbit pivot creation id=" + cameraOrbitPivot.id() );

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
			tracePreview( "09 previewPart is null; destroying camera orbit pivot and previewRoot" );
			if( cameraOrbitPivot )
			{
				cameraOrbitPivot.destroy();
				cameraOrbitPivot = null;
			}
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
		previewMeshResourceID = previewMeshID;
		tracePreview( "11 after previewPart.getMesh value=" + previewMeshID );
		int directNestedMatch = 0;
		if( previewMeshResourceID && previewMeshResourceID == nestedMeshResourceID )
			directNestedMatch = 1;
		tracePreview(
			"MESH_IDENTITY direct=" + previewMeshResourceID +
			" nested=" + nestedMeshResourceID +
			" directNestedMatch=" + directNestedMatch
		);
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
		// Never let a different nested mesh silently replace the direct AABB.
		if( hasNestedMeshBounds && previewMeshID && nestedMeshResourceID != previewMeshID )
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

		// GII_SIZE is provisional. Once native pose is initialized, the live nested
		// mesh AABB and native scale replace it with a geometry-based camera fit.
		tracePreview( "35A camera modelSize provisional GII_SIZE fallback=" + modelSize + " live local AABB extent=" + runtimeBoundsExtent + " hasBounds=" + hasRuntimeBounds );

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
		previewCamera = new Camera( cameraOrbitPivot, previewViewport, 1, 110, 0.001, 10.0 );
		tracePreview( "3B after preview Camera creation" );

		// Give the camera a valid matrix immediately. Use the cached direct AABB
		// center if available, otherwise previewRoot origin. A stable native
		// center may replace this provisional target once after render init.
		updateCamera();
		tracePreview( "3C initial camera matrix set with provisional orbit center" );

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

	// Positive square root using Newton iterations. Avoid relying on a library
	// square-root implementation in the legacy SLRR JVM.
	private float cameraSqrt( float value )
	{
		if( value <= 0.0 )
			return 0.0;

		float estimate = value;
		if( estimate < 1.0 )
			estimate = 1.0;

		for( int i = 0; i < 8; i++ )
			estimate = ( estimate + value / estimate ) * 0.5;

		return estimate;
	}

	// Replace the provisional GII_SIZE camera distance with a fit derived from
	// the actual nested mesh bounds and the initialized native INSTANCE_RENDER
	// scale. The half-diagonal is invariant under a rigid node rotation.
	// Axes 513..515 are provided by the already-installed component-scale EXE.
	private void fitCameraDistanceFromNestedBounds( GameRef renderRef )
	{
		if( cameraFitApplied || !hasNestedMeshBounds )
			return;

		int scaleXFixed = renderRef.getInfo( AABB_QUERY, 513 );
		int scaleYFixed = renderRef.getInfo( AABB_QUERY, 514 );
		int scaleZFixed = renderRef.getInfo( AABB_QUERY, 515 );

		if( scaleXFixed == AABB_INVALID ||
			scaleYFixed == AABB_INVALID ||
			scaleZFixed == AABB_INVALID )
		{
			tracePreview( "CAMERA_FIT scale axes unavailable; keeping GII_SIZE fallback" );
			return;
		}

		float scaleX = scaleXFixed / AABB_SCALE;
		float scaleY = scaleYFixed / AABB_SCALE;
		float scaleZ = scaleZFixed / AABB_SCALE;
		if( scaleX < 0.0 ) scaleX = -scaleX;
		if( scaleY < 0.0 ) scaleY = -scaleY;
		if( scaleZ < 0.0 ) scaleZ = -scaleZ;

		if( scaleX <= 0.0001 || scaleY <= 0.0001 || scaleZ <= 0.0001 )
			return;

		float halfX = nestedMeshSizeX * scaleX * 0.5;
		float halfY = nestedMeshSizeY * scaleY * 0.5;
		float halfZ = nestedMeshSizeZ * scaleZ * 0.5;
		float radiusSquared = halfX * halfX + halfY * halfY + halfZ * halfZ;
		if( radiusSquared <= 0.000001 )
			return;

		float radius = cameraSqrt( radiusSquared );
		float cameraOffsetY = zoom - CAMERA_VERTICAL_OFFSET;
		float cameraOffsetZ = zoom;
		float cameraOffsetLength = cameraSqrt(
			cameraOffsetY * cameraOffsetY + cameraOffsetZ * cameraOffsetZ
		);
		if( cameraOffsetLength <= 0.0001 )
			return;

		float previousModelSize = modelSize;
		float fittedModelSize = radius * CAMERA_FIT_DISTANCE_RATIO /
			( 10.0 * cameraOffsetLength );
		if( fittedModelSize <= 0.0001 )
			return;

		// GII_SIZE is the legacy minimum framing distance. AABB may move the
		// camera farther away for large/off-center meshes, but must never pull
		// it closer for tiny details such as mirrors, springs or small hardware.
		String fitDecision = "kept-GII_SIZE-minimum";
		if( fittedModelSize > previousModelSize )
		{
			modelSize = fittedModelSize;
			fitDecision = "moved-camera-back";
		}

		cameraFitApplied = 1;
		tracePreview(
			"CAMERA_FIT evaluated radiusNative=" + radius +
			" scale=" + scaleX + "," + scaleY + "," + scaleZ +
			" previousGII_SIZE=" + previousModelSize +
			" requiredModelSize=" + fittedModelSize +
			" finalModelSize=" + modelSize +
			" decision=" + fitDecision +
			" targetDistanceRatio=" + CAMERA_FIT_DISTANCE_RATIO
		);
	}

	// Compare fixed-point native center samples. One unit equals 0.001 world unit.
	private int cameraCenterSamplesMatch( int x, int y, int z )
	{
		if( x < cameraOrbitCandidateXFixed - 1 || x > cameraOrbitCandidateXFixed + 1 )
			return 0;
		if( y < cameraOrbitCandidateYFixed - 1 || y > cameraOrbitCandidateYFixed + 1 )
			return 0;
		if( z < cameraOrbitCandidateZFixed - 1 || z > cameraOrbitCandidateZFixed + 1 )
			return 0;
		return 1;
	}

	// Move only the camera's parent frame to the cached geometric center.
	// The preview Part stays under previewRoot and is never translated.
	private void positionCameraOrbitPivot()
	{
		if( cameraOrbitPivotReady || !cameraOrbitPivot )
			return;

		Vector3 pivotTarget = new Vector3(
			cameraOrbitCenterX,
			cameraOrbitCenterY,
			cameraOrbitCenterZ
		);
		tracePreview(
			"CAMERA_PIVOT_SET before setPos target=" +
			pivotTarget.x + "," + pivotTarget.y + "," + pivotTarget.z
		);
		cameraOrbitPivot.setPos( pivotTarget );

		Vector3 actualPosition = cameraOrbitPivot.getPos();
		if( actualPosition )
			tracePreview(
				"CAMERA_PIVOT_SET after setPos actual=" +
				actualPosition.x + "," + actualPosition.y + "," + actualPosition.z
			);
		else
			tracePreview( "CAMERA_PIVOT_SET readback=null" );

		cameraOrbitPivotReady = 1;
	}

	// Capture one stable center after native render initialization. Once ready,
	// this method returns immediately and never queries bounds again.
	private int captureCameraOrbitCenter()
	{
		if( cameraOrbitCenterReady )
			return 1;

		cameraOrbitCenterWaitFrames++;
		if( cameraOrbitCenterWaitFrames <= 2 )
		{
			if( cameraOrbitCenterWaitFrames == 1 )
				tracePreview( "CAMERA_ORBIT_CENTER skipping first two animation frames" );
			return 0;
		}

		// Query the native center from this exact INSTANCE_RENDER. The native
		// handler resolves and validates its own embedded type-5 mesh. Do not
		// gate it on Java's separate ResourceRef walk or compare against
		// Part.getMesh(): that getter may be zero or refer to another resource.
		if( !orbitRenderRef )
		{
			int renderInstance = previewPart.getInfo( GameType.GII_RENDERINSTANCE );
			if( renderInstance )
			{
				orbitRenderRef = new GameRef( renderInstance );
				tracePreview( "CAMERA_ORBIT_CENTER renderInstance=" + renderInstance );
			}
		}

		if( orbitRenderRef )
		{
			if( !cameraOrbitDiagnosticsLogged )
			{
				traceCameraOrbitInputs( orbitRenderRef );
				cameraOrbitDiagnosticsLogged = 1;
			}

			int centerXFixed = orbitRenderRef.getInfo( AABB_QUERY, 536 );
			int centerYFixed = orbitRenderRef.getInfo( AABB_QUERY, 537 );
			int centerZFixed = orbitRenderRef.getInfo( AABB_QUERY, 538 );

			if( centerXFixed != AABB_INVALID &&
				centerYFixed != AABB_INVALID &&
				centerZFixed != AABB_INVALID )
			{
				// A valid native center also proves that render scale is initialized.
				// Fit the camera once, before the first stable-center capture completes.
				fitCameraDistanceFromNestedBounds( orbitRenderRef );

				if( !cameraOrbitCandidateReady )
				{
					cameraOrbitCandidateXFixed = centerXFixed;
					cameraOrbitCandidateYFixed = centerYFixed;
					cameraOrbitCandidateZFixed = centerZFixed;
					cameraOrbitCandidateReady = 1;
					tracePreview(
						"CAMERA_ORBIT_CENTER candidate=" +
						centerXFixed + "," + centerYFixed + "," + centerZFixed +
						" frame=" + cameraOrbitCenterWaitFrames
					);
				}
				else
				if( cameraCenterSamplesMatch( centerXFixed, centerYFixed, centerZFixed ) )
				{
					// The native center is expressed in the render instance's parent space.
					// Resolve only the two parent spaces we can prove here. For a Part
					// parent, sample its live transform now (not the createPreview snapshot).
					int renderParentID = orbitRenderRef.getParentID();
					Vector3 renderParentCenter = new Vector3(
						centerXFixed / AABB_SCALE,
						centerYFixed / AABB_SCALE,
						centerZFixed / AABB_SCALE
					);
					String parentTransform = "unsupported-parent";
					int parentResolved = 0;
					if( renderParentID == previewPart.id() )
					{
						Vector3 currentPartPosition = previewPart.getPos();
						Ypr currentPartOrientation = previewPart.getOri();
						if( currentPartPosition )
							currentPartPosition = new Vector3( currentPartPosition );
						else
							currentPartPosition = new Vector3( savedPosition );
						if( currentPartOrientation )
							currentPartOrientation = new Ypr( currentPartOrientation );
						else
							currentPartOrientation = new Ypr( savedOrientation );
						renderParentCenter.rotate( currentPartOrientation );
						cameraOrbitCenterX = currentPartPosition.x + renderParentCenter.x;
						cameraOrbitCenterY = currentPartPosition.y + renderParentCenter.y;
						cameraOrbitCenterZ = currentPartPosition.z + renderParentCenter.z;
						parentTransform = "previewPart-to-previewRoot-live-transform";
						parentResolved = 1;
					}
					else
					if( renderParentID == previewRoot.id() )
					{
						// A root-parented render instance is already in previewRoot space.
						cameraOrbitCenterX = renderParentCenter.x;
						cameraOrbitCenterY = renderParentCenter.y;
						cameraOrbitCenterZ = renderParentCenter.z;
						parentTransform = "already-previewRoot";
						parentResolved = 1;
					}
					if( parentResolved )
					{
						positionCameraOrbitPivot();
						cameraOrbitCenterReady = 1;
						tracePreview(
							"CAMERA_ORBIT_CENTER captured root world=" +
							cameraOrbitCenterX + "," + cameraOrbitCenterY + "," + cameraOrbitCenterZ +
							" renderParentCenter=" + (centerXFixed / AABB_SCALE) + "," +
							(centerYFixed / AABB_SCALE) + "," + (centerZFixed / AABB_SCALE) +
							" renderParentID=" + renderParentID +
							" previewPartID=" + previewPart.id() +
							" previewRootID=" + previewRoot.id() +
							" transform=" + parentTransform +
							" samples=" + cameraOrbitCandidateXFixed + "," + cameraOrbitCandidateYFixed + "," + cameraOrbitCandidateZFixed +
							" / " + centerXFixed + "," + centerYFixed + "," + centerZFixed
						);
						return 1;
					}
					else
					{
						cameraOrbitCandidateReady = 0;
						if( cameraOrbitCenterWaitFrames == 3 || cameraOrbitCenterWaitFrames % 5 == 0 )
							tracePreview(
								"CAMERA_ORBIT_CENTER rejected unsupported parent=" + renderParentID +
								" previewPartID=" + previewPart.id() +
								" previewRootID=" + previewRoot.id()
							);
					}
				}
				else
				{
					cameraOrbitCandidateXFixed = centerXFixed;
					cameraOrbitCandidateYFixed = centerYFixed;
					cameraOrbitCandidateZFixed = centerZFixed;
					if( cameraOrbitCenterWaitFrames <= 5 || cameraOrbitCenterWaitFrames % 5 == 0 )
						tracePreview(
							"CAMERA_ORBIT_CENTER sample moved; candidate replaced=" +
							centerXFixed + "," + centerYFixed + "," + centerZFixed +
							" frame=" + cameraOrbitCenterWaitFrames
						);
				}
			}
			else
			{
				cameraOrbitCandidateReady = 0;
				if( cameraOrbitCenterWaitFrames == 3 || cameraOrbitCenterWaitFrames >= 30 )
					tracePreview(
						"CAMERA_ORBIT_CENTER native sample invalid=" +
						centerXFixed + "," + centerYFixed + "," + centerZFixed
					);
			}
		}

		if( cameraOrbitCenterWaitFrames >= 30 )
		{
			if( hasRuntimeBounds )
				tracePreview(
					"CAMERA_ORBIT_CENTER timeout; using cached direct AABB=" +
					cameraOrbitCenterX + "," + cameraOrbitCenterY + "," + cameraOrbitCenterZ
				);
			else
			{
				cameraOrbitCenterX = 0.0;
				cameraOrbitCenterY = 0.0;
				cameraOrbitCenterZ = 0.0;
				tracePreview( "CAMERA_ORBIT_CENTER timeout; using previewRoot origin" );
			}
			positionCameraOrbitPivot();
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

			// Camera is a child of cameraOrbitPivot, which is positioned at the
			// cached geometric center. Here we set only the local orbit radius.
			Vector3 cameraOffset = new Vector3(
				0.0,
				modelSize * zoom - modelSize * CAMERA_VERTICAL_OFFSET,
				modelSize * zoom
			);
			cameraOffset.rotate( new Ypr( -2.5, 0.0, 0.0 ) );
			cameraOffset.rotate( inversePartOrientation );

			// This is a local transform relative to cameraOrbitPivot.
			Vector3 cameraPosition = new Vector3( cameraOffset );

			// In native coordinates:
			// cameraPosition*10 = orbitCenter*10 + cameraOffset*10.
			// Therefore the exact direction toward the orbit center is simply
			// -cameraOffset. It is already rotated by inversePartOrientation.
			Vector3 cameraDirection = new Vector3(
				-cameraOffset.x,
				-cameraOffset.y,
				-cameraOffset.z
			);
			Ypr cameraOrientation = new Ypr( cameraDirection );

			if( cameraOrbitCenterReady && !cameraAimDiagnosticsLogged )
			{
				tracePreview(
					"CAMERA_AIM pivotRootJava=" + cameraOrbitCenterX + "," + cameraOrbitCenterY + "," + cameraOrbitCenterZ +
					" pivotRootNative=" + (cameraOrbitCenterX * 10.0) + "," +
					(cameraOrbitCenterY * 10.0) + "," + (cameraOrbitCenterZ * 10.0) +
					" cameraLocalJava=" + cameraPosition.x + "," + cameraPosition.y + "," + cameraPosition.z +
					" cameraLocalNative=" + (cameraPosition.x * 10.0) + "," +
					(cameraPosition.y * 10.0) + "," + (cameraPosition.z * 10.0) +
					" directionNative=" + (cameraDirection.x * 10.0) + "," +
					(cameraDirection.y * 10.0) + "," + (cameraDirection.z * 10.0) +
					" ypr=" + cameraOrientation.y + "," + cameraOrientation.p + "," + cameraOrientation.r
				);
				cameraAimDiagnosticsLogged = 1;
			}

			// Record the exact Java-side values supplied to setMatrix. The second
			// sample is reserved for an actual mouse drag, not automatic spin.
			if( cameraOrbitCenterReady &&
				( !cameraNativePoseInitialLogged ||
					( cameraNativePoseChangePending && !cameraNativePoseChangeLogged ) ) )
			{
				String phase = "initial";
				if( cameraNativePoseInitialLogged )
					phase = "after-user-drag";
				tracePreview(
					"CAMERA_SET_INPUT phase=" + phase +
					" yawPitchRoll=" + yaw + "," + pitch + "," + roll +
					" cameraLocalJava=" + cameraPosition.x + "," + cameraPosition.y + "," + cameraPosition.z +
					" orientation=" + cameraOrientation.y + "," +
					cameraOrientation.p + "," + cameraOrientation.r
				);
			}

			previewCamera.setMatrix( cameraPosition, cameraOrientation );

			// Read back the actual native Camera transform once at startup and
			// once after a deliberate mouse drag. No native state is modified.
			if( cameraOrbitCenterReady && !cameraNativePoseInitialLogged )
			{
				traceCameraNativePose( "initial" );
				cameraNativePoseInitialLogged = 1;
			}
			else
			if( cameraNativePoseChangePending && !cameraNativePoseChangeLogged )
			{
				traceCameraNativePose( "after-user-drag" );
				cameraNativePoseChangeLogged = 1;
				cameraNativePoseChangePending = 0;
			}
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

		// Center capture must not depend on cursor availability. After the
		// center is finalized, animate() performs no bounds/center queries.
		int centerReady = captureCameraOrbitCenter();

		if( !centerReady )
		{
			updateCamera();
			dragging = 0;
			autoResume = 0.0;
			Vector3 waitingCursor = Input.cursor.getPos();
			if( waitingCursor )
			{
				lastMouseX = waitingCursor.x;
				lastMouseY = waitingCursor.y;
			}
			lastWheel = Input.getAxis( 1, 2 );
			if( traceFrame ) tracePreview( "A3 camera updated while orbit center is pending" );
			return;
		}

		// The one-shot center is now frozen. From here on, animate only updates
		// camera orbit/input; it does not inspect meshes or native bounds.
		Vector3 cursorPosition = Input.cursor.getPos();
		if( traceFrame ) tracePreview( "A2 after Input.cursor.getPos" );
		if( !cursorPosition )
		{
			if( traceFrame ) tracePreview( "A3 cursorPosition is null; camera center remains cached" );
			updateCamera();
			return;
		}

		float mouseX = cursorPosition.x;
		float mouseY = cursorPosition.y;
		float leftButton = Input.getAxis( 1, 3 );
		if( traceFrame ) tracePreview( "A4 after left mouse Input.getAxis" );
		float wheel = Input.getAxis( 1, 2 );
		if( traceFrame ) tracePreview( "A5 after wheel Input.getAxis" );
		int overPreview = cursorOverPreview( mouseX, mouseY );

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
				if( !cameraNativePoseChangeLogged &&
					( mouseDeltaX != 0.0 || mouseDeltaY != 0.0 ) )
					cameraNativePoseChangePending = 1;
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

		if( cameraOrbitPivot )
		{
			tracePreview( "H45 before cameraOrbitPivot.destroy" );
			cameraOrbitPivot.destroy();
			cameraOrbitPivot = null;
			tracePreview( "H46 after cameraOrbitPivot.destroy" );
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

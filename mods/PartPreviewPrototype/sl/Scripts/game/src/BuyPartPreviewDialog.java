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

	private void createPreview()
	{
		previewTraceData = "";
		File.delete( GameLogic.tempSaveDir + "PartPreviewTrace.txt" );
		boundsCenterX = 0.0;
		boundsCenterY = 0.0;
		boundsCenterZ = 0.0;
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

	private void applyPartTransform()
	{
		if( previewPart )
		{
			Vector3 rotatedCenter = new Vector3( boundsCenterX, boundsCenterY, boundsCenterZ );
			rotatedCenter.rotate( new Ypr( yaw, pitch, roll ) );

			// This Part lives under a private preview root whose camera targets
			// the root origin. Do not preserve the part's original install/mount
			// position here: remove it entirely and place the rotated mesh center
			// at (0, 0, 0). savedPosition is kept only as a diagnostic reference.
			Vector3 previewPosition = new Vector3(
				-rotatedCenter.x,
				-rotatedCenter.y,
				-rotatedCenter.z
			);

			// Log the initial transform once; animateTraceFrames becomes non-zero
			// before the per-frame update path runs.
			if( animateTraceFrames == 0 )
				tracePreview(
					"T00 center=" + boundsCenterX + "," + boundsCenterY + "," + boundsCenterZ +
					" rotatedCenter=" + rotatedCenter.x + "," + rotatedCenter.y + "," + rotatedCenter.z +
					" sourcePos=" + savedPosition.x + "," + savedPosition.y + "," + savedPosition.z +
					" finalPos=" + previewPosition.x + "," + previewPosition.y + "," + previewPosition.z +
					" ypr=" + yaw + "," + pitch + "," + roll
				);

			previewPart.setMatrix( previewPosition, new Ypr( yaw, pitch, roll ) );

			// Read the transform back once immediately after the first setter call.
			// This distinguishes a rejected/overridden Part transform from an
			// internal mesh/child offset that is not represented by Part.getPos().
			if( animateTraceFrames == 0 )
			{
				Vector3 appliedPosition = previewPart.getPos();
				Ypr appliedOrientation = previewPart.getOri();
				if( appliedPosition && appliedOrientation )
					tracePreview(
						"T01 after setMatrix readbackPos=" + appliedPosition.x + "," + appliedPosition.y + "," + appliedPosition.z +
						" readbackYpr=" + appliedOrientation.y + "," + appliedOrientation.p + "," + appliedOrientation.r
					);
				else
					tracePreview( "T01 after setMatrix readback returned null" );
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

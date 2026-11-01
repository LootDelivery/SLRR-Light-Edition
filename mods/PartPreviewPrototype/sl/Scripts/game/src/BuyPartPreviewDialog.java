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

	final static int RID_PREVIEW_LIGHT = misc.garage:0x00000024r;

	final static float AUTO_ROTATION_SPEED = 0.32;
	// Mouse sensitivity: horizontal (yaw) is intentionally faster than vertical (pitch).
	final static float DRAG_YAW_SCALE = 3.5;
	final static float DRAG_PITCH_SCALE = 2.2;

	// Lower the camera slightly so the model sits a little higher in the viewport.
	// This changes framing only; the preview part's pivot and transform stay intact.
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
		if( !sourcePart )
			return;

		int partTypeID = sourcePart.getInfo( GameType.GII_TYPE );
		if( !partTypeID )
			return;

		// Build an independent scene root and instantiate the same part type
		// under it. The catalog's InventoryItem and its Part remain untouched.
		previewRoot = new Dummy( GameType.WORLDTREEROOT );
		GameRef factory = new GameRef();
		previewPart = factory.create(
			previewRoot,
			new GameRef( partTypeID ),
			"0,0,0,0,0,0",
			"part_purchase_preview"
		);

		if( !previewPart )
		{
			previewRoot.destroy();
			previewRoot = null;
			return;
		}

		// Temporary native AABB probe. Each axis uses a reserved getInfo(int) query ID.
		// Results are signed fixed-point integers (model units * 10000).
		// Keep preview rotation unchanged until these values are verified in-game.
		int bboxX = previewPart.getInfo( 0x584F4242 );
		int bboxY = previewPart.getInfo( 0x584F4243 );
		int bboxZ = previewPart.getInfo( 0x584F4244 );
		System.log( "[PartPreviewBBox] mesh=" + previewPart.getMesh() +
			", center_fixed=" + bboxX + "," + bboxY + "," + bboxZ );

		previewPart.setMatrix( null, null );
		previewPart.refreshGlassOpacityPreview();

		savedPosition = previewPart.getPos();
		if( savedPosition )
			savedPosition = new Vector3( savedPosition );
		else
			savedPosition = new Vector3( 0.0, 0.0, 0.0 );

		savedOrientation = previewPart.getOri();
		if( savedOrientation )
			savedOrientation = new Ypr( savedOrientation );
		else
			savedOrientation = new Ypr( 0.0, 0.0, 0.0 );

		yaw = savedOrientation.y;
		pitch = savedOrientation.p;
		roll = savedOrientation.r;

		modelSize = previewPart.getInfo( GameType.GII_SIZE ) / 100.0;
		if( modelSize <= 0.0 )
			modelSize = 1.0;

		// Independent light: do not reuse the catalog panel's render root.
		previewLight = new RenderRef( previewRoot, RID_PREVIEW_LIGHT, null );

		previewViewport = new Viewport( getPreviewViewportPriority(), 0.05, 0.15, 0.43, 0.66 );
		previewViewport.activate( Viewport.RENDERFLAG_CLEARDEPTH | Viewport.RENDERFLAG_CLEARTARGET );
		previewCamera = new Camera( previewRoot, previewViewport, 1, 110, 0.001, 10.0 );

		updateCamera();

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
	}

	private void applyPartTransform()
	{
		if( previewPart )
			previewPart.setMatrix( savedPosition, new Ypr( yaw, pitch, roll ) );
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

		float now = System.currentTime();
		float delta = now - lastFrameTime;
		lastFrameTime = now;
		if( delta <= 0.0 || delta > 0.1 )
			delta = 0.016;

		// Read the integrated cursor position, not raw DirectInput mouse deltas.
		Vector3 cursorPosition = Input.cursor.getPos();
		if( !cursorPosition )
			return;

		float mouseX = cursorPosition.x;
		float mouseY = cursorPosition.y;
		float leftButton = Input.getAxis( 1, 3 );
		float wheel = Input.getAxis( 1, 2 );
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

		applyPartTransform();
		updateCamera();
	}

	public void show()
	{
		createPreview();
		super.show();
		enableAnimateHook();
	}

	public void hide()
	{
		disableAnimateHook();

		if( previewCamera )
		{
			previewCamera.destroy();
			previewCamera = null;
		}

		if( previewViewport )
		{
			previewViewport.deactivate();
			previewViewport.destroy();
			previewViewport = null;
		}

		if( previewLight )
		{
			previewLight.destroy();
			previewLight = null;
		}

		if( previewPart )
		{
			previewPart.destroy();
			previewPart = null;
		}

		if( previewRoot )
		{
			previewRoot.destroy();
			previewRoot = null;
		}

		super.hide();
	}
}

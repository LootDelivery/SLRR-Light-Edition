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
 * Input device/axis IDs match the stock ControlSet defaults in this game tree:
 * SysMouse=1, cursor X/Y=0/1, wheel=2, left button=3.
 */
public class BuyPartPreviewDialog extends PartPreviewDialogBase
{
	InventoryItem item;
	Part part;

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

	final static float AUTO_ROTATION_SPEED = 0.32;
	final static float DRAG_YAW_SCALE = 2.8;
	final static float DRAG_PITCH_SCALE = 2.2;
	final static float MIN_ZOOM = 0.65;
	final static float MAX_ZOOM = 2.25;

	public BuyPartPreviewDialog( Controller ctrl, int myFlags, InventoryItem sourceItem, int price )
	{
		super( ctrl, myFlags, "BUY PART" );

		item = sourceItem;
		if( item instanceof InventoryItem_Part )
			part = ((InventoryItem_Part)item).getPart();

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
			note = note + "Installable right away to your car. ";
		if( compatibility & 2 )
			note = note + "Compatible with your car, but requires preparation. ";
		if( compatibility & 4 )
			note = note + "Compatible with a part in your inventory.";
		return note;
	}

	private int cursorOverPreview( float x, float y )
	{
		// Mouse axes are normalized to [-1,1]. Preview viewport is the left panel.
		if( x > -0.92 && x < -0.03 && y > -0.82 && y < 0.72 )
			return 1;
		return 0;
	}

	private void createPreview()
	{
		if( !part )
			return;

		savedPosition = part.getPos();
		if( savedPosition )
			savedPosition = new Vector3( savedPosition );
		else
			savedPosition = new Vector3( 0.0, 0.0, 0.0 );

		savedOrientation = part.getOri();
		if( savedOrientation )
			savedOrientation = new Ypr( savedOrientation );
		else
			savedOrientation = new Ypr( 0.0, 0.0, 0.0 );

		yaw = savedOrientation.y;
		pitch = savedOrientation.p;
		roll = savedOrientation.r;

		modelSize = part.getInfo( GameType.GII_SIZE ) / 100.0;
		if( modelSize <= 0.0 )
			modelSize = 1.0;

		// The catalog item already owns this root and its inventory preview light.
		// Reuse it: the part is not cloned, removed or re-parented.
		previewViewport = new Viewport( getPreviewViewportPriority(), 0.05, 0.15, 0.43, 0.66 );
		previewViewport.activate( Viewport.RENDERFLAG_CLEARDEPTH | Viewport.RENDERFLAG_CLEARTARGET );
		previewCamera = new Camera( item.localroot, previewViewport, 1, 110.0, 0.001, 10.0 );

		updateCamera();
		applyPartTransform();

		lastFrameTime = System.currentTime();
		lastWheel = Input.getAxis( 1, 2 );
		dragging = 0;
		autoResume = 1.0;
	}

	private void applyPartTransform()
	{
		if( part )
			part.setMatrix( savedPosition, new Ypr( yaw, pitch, roll ) );
	}

	private void updateCamera()
	{
		if( previewCamera )
		{
			Vector3 cameraPosition = new Vector3( 0.0, modelSize * zoom, modelSize * zoom );
			cameraPosition.rotate( new Ypr( -2.5, 0.0, 0.0 ) );
			previewCamera.setMatrix( cameraPosition, new Ypr( -2.5, -0.7, 0.0 ) );
		}
	}

	public void animate()
	{
		if( !part || !previewCamera )
			return;

		float now = System.currentTime();
		float delta = now - lastFrameTime;
		lastFrameTime = now;
		if( delta <= 0.0 || delta > 0.1 )
			delta = 0.016;

		float mouseX = Input.getAxis( 1, 0 );
		float mouseY = Input.getAxis( 1, 1 );
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
				yaw = yaw + (mouseX - lastMouseX) * DRAG_YAW_SCALE;
				pitch = pitch + (mouseY - lastMouseY) * DRAG_PITCH_SCALE;
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

		// Treat each wheel transition as one zoom step; it cannot keep zooming
		// merely because an axis value remains non-zero for more than one frame.
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

		if( part && savedPosition && savedOrientation )
			part.setMatrix( savedPosition, savedOrientation );

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

		super.hide();
	}
}

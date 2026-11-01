package java.render.osd.dialog;

import java.io.*;
import java.util.*;
import java.util.resource.*;
import java.render.*;
import java.render.osd.*;
import java.io.Controller;

/**
 * Package bridge for the part preview dialog.
 * This class exposes Dialog's package-private OSD without changing Dialog.java.
 */
public class PartPreviewDialogBase extends Dialog
{
	public PartPreviewDialogBase( Controller ctrl, int myFlags, String myTitle )
	{
		super( ctrl, myFlags, myTitle, "YES;NO", 0.9, 2.0 );
		escapeCmd = 1;
	}

	protected Osd getPreviewOsd()
	{
		return osd;
	}

	protected int getPreviewViewportPriority()
	{
		return priority;
	}
}

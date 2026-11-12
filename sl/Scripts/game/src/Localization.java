package java.game;

import java.io.*;
import java.util.*;

/**
 * Lightweight external localization loader.
 *
 * Prototype parser for UTF-8 INI files. Missing or empty translations
 * always fall back to the original string supplied by the caller.
 */
public class Localization
{
	static Vector keys = new Vector();
	static Vector values = new Vector();
	static int loaded = 0;

	/**
	 * Load a UTF-8 INI file.
	 *
	 * Supported syntax:
	 *   [section.name]
	 *   key=value
	 *   # or ; comments
	 *
	 * Section names are prepended to keys with a dot.
	 * Malformed lines are ignored. Later duplicate keys take precedence.
	 */
	public static int load( String fileName )
	{
		keys.removeAllElements();
		values.removeAllElements();
		loaded = 0;

		try
		{
			BufferedReader reader = new BufferedReader(
				new InputStreamReader( new FileInputStream( fileName ), "UTF-8" )
			);

			String section = "";
			String line = reader.readLine();

			while( line != null )
			{
				line = line.trim();

				if( line.length() > 0 )
				{
					char first = line.charAt( 0 );

					if( first != '#' && first != ';' )
					{
						if( first == '[' && line.charAt( line.length()-1 ) == ']' )
						{
							section = line.substring( 1, line.length()-1 ).trim();
						}
						else
						{
							int separator = line.indexOf( "=" );

							if( separator > 0 )
							{
								String key = line.substring( 0, separator ).trim();
								String value = line.substring( separator+1 ).trim();

								if( key.length() > 0 )
								{
									if( section.length() > 0 )
										key = section + "." + key;

									keys.addElement( key );
									values.addElement( value );
								}
							}
						}
					}
				}

				line = reader.readLine();
			}

			reader.close();
			loaded = 1;
			return 1;
		}
		catch( Exception error )
		{
			keys.removeAllElements();
			values.removeAllElements();
			loaded = 0;
			return 0;
		}
	}

	/**
	 * Return a translation or the original text when no usable value exists.
	 */
	public static String get( String key, String fallback )
	{
		if( key == null )
			return fallback;

		for( int i = keys.size()-1; i >= 0; i-- )
		{
			String storedKey = (String)keys.elementAt( i );

			if( storedKey.equals( key ) )
			{
				String value = (String)values.elementAt( i );

				if( value != null && value.length() > 0 )
					return value;

				return fallback;
			}
		}

		return fallback;
	}

	public static int isLoaded()
	{
		return loaded;
	}
}

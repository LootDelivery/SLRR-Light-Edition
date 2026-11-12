package java.game;

/*
 * SLRR Light Edition localization manager.
 *
 * Keep this class compatible with the TUFA script compiler:
 * no java.io streams, collections, exceptions, boolean return values,
 * or Java-only APIs are used here.
 */
public class Localization
{
	static int language = 1;

	public static void initTestRussian()
	{
		language = 1;
	}

	public static void setLanguage( int index )
	{
		if( index == 0 )
			language = 0;
		else
		if( index == 1 )
			language = 1;
	}

	public static void toggleLanguage()
	{
		if( language == 0 )
			language = 1;
		else
			language = 0;
	}

	public static int getLanguageIndex()
	{
		return language;
	}

	public static String get( String key, String fallback )
	{
		String value;

		if( key == null )
			return fallback;

		if( language == 1 )
			value = RU.get( key );
		else
			value = EN.get( key );

		if( value == null || value.equals( "" ) )
			return fallback;

		return value;
	}
}

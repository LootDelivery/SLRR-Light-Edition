package java.game;

/*
 * Localization API smoke test for SLRR.
 *
 * This build intentionally does not read external files. The current
 * SLRR File wrapper exposes serialized readString(), but no raw text
 * line/byte reader. Standard Java streams and exception handling are
 * not supported by the target script compiler.
 */
public class Localization
{
	static int loaded = 0;

	/*
	 * Temporary hard-coded Russian strings to verify the MainMenu OSD
	 * integration and Cyrillic rendering independently from file I/O.
	 */
	public static void initTestRussian()
	{
		loaded = 1;
	}

	public static String get( String key, String fallback )
	{
		if( key == null )
			return fallback;

		if( key.equals( "menu.main.title" ) )
			return "ГЛАВНОЕ МЕНЮ";

		if( key.equals( "menu.main.new_career" ) )
			return "НОВАЯ КАРЬЕРА";

		if( key.equals( "menu.main.load_career" ) )
			return "ЗАГРУЗИТЬ КАРЬЕРУ";

		if( key.equals( "menu.main.load_car" ) )
			return "ЗАГРУЗИТЬ АВТО";

		if( key.equals( "menu.main.save_car" ) )
			return "СОХРАНИТЬ АВТО";

		if( key.equals( "menu.main.options" ) )
			return "НАСТРОЙКИ";

		if( key.equals( "menu.main.extras" ) )
			return "ДОПОЛНИТЕЛЬНО";

		if( key.equals( "menu.main.exit" ) )
			return "ВЫХОД";

		if( key.equals( "menu.main.back_to_garage" ) )
			return "В ГАРАЖ";

		return fallback;
	}

	public static int isLoaded()
	{
		return loaded;
	}
}

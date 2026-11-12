package java.localization;

/* Russian strings for the main menu. */
public class RU
{
	public static String get( String key )
	{
		if( key == null )
			return "";

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

		if( key.equals( "menu.main.language" ) )
			return "ЯЗЫК: РУССКИЙ";

		return "";
	}
}

package java.localization;

/* English strings for the main menu. */
public class EN
{
	public static String get( String key )
	{
		if( key == null )
			return "";

		if( key.equals( "menu.main.title" ) )
			return "MAIN MENU";

		if( key.equals( "menu.main.new_career" ) )
			return "NEW CAREER";

		if( key.equals( "menu.main.load_career" ) )
			return "LOAD CAREER";

		if( key.equals( "menu.main.load_car" ) )
			return "LOAD CAR";

		if( key.equals( "menu.main.save_car" ) )
			return "SAVE CAR";

		if( key.equals( "menu.main.options" ) )
			return "OPTIONS";

		if( key.equals( "menu.main.extras" ) )
			return "EXTRAS";

		if( key.equals( "menu.main.exit" ) )
			return "EXIT";

		if( key.equals( "menu.main.back_to_garage" ) )
			return "BACK TO GARAGE";

		if( key.equals( "menu.main.language" ) )
			return "LANGUAGE: ENGLISH";

		return "";
	}
}

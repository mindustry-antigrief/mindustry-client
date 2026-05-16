package mindustry.client.ui;

import arc.*;

public enum CursednessLevel {
	NORMAL,
	UHH,
	OHNO,
	CURSED,
	WWWHHHHHYYYY;
	//Warning: do not change the order.
	public static CursednessLevel fromInteger(int x) {
		return switch(x) {
			case 0 -> NORMAL;
			case 1 -> UHH;
			case 2 -> OHNO;
			case 3 -> CURSED;
			case 4 -> WWWHHHHHYYYY;
			default -> NORMAL;
		};
	}
	public static CursednessLevel get(){
		return Core.settings != null ? fromInteger(Core.settings.getInt("cursednesslevel", 1)) : CursednessLevel.NORMAL;
	}
	public static boolean atLeast(CursednessLevel level){
		return get().ordinal() >= level.ordinal();
	}
}

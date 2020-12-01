package mindustry.client.utils;

import arc.struct.Seq;

public class Autocomplete {

    public static void initialize() {
        BlockEmotes.initialize();
    }

    public static String getCompletion(String input) {
        return closest(input).peek().getCompletion(input);
    }

    public static String getHover(String input) {
        return closest(input).peek().getHover(input);
    }

    public static boolean matches(String input) {
        return closest(input).peek().matches(input) > 0.5f;
    }

    public static Seq<Autocompleteable> closest(String input) {
        Seq<Autocompleteable> all = BlockEmotes.closest(input).addAll(PlayerCompletion.closest(input));
        if (all == null) return null;
        return all.sort(item -> item.matches(input));
    }
}

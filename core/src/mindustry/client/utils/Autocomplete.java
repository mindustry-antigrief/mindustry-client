package mindustry.client.utils;

import arc.struct.*;
import org.jetbrains.annotations.*;

public class Autocomplete { // FINISHME: Rewrite the entire autocompletion system to not suck as much
    public static Seq<Autocompleter> autocompleters = new Seq<>();

    public static void initialize() {
        autocompleters.forEach(Autocompleter::initialize);
    }

    public static boolean matches(String input) {
        return closest(input).peek().matches(input) > 0.5f;
    }

    @NotNull public static Seq<Autocompleteable> closest(String input) {
        return autocompleters.flatMap(a -> a.closest(input)).sort(item -> item.matches(input));
//        Seq<Autocompleteable> all = autocompleters.reduce(new Seq<>(), (a, b) -> a.closest(input).addAll(b));
//        return all.sort(item -> item.matches(input));
    }
}

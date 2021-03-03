package mindustry.client.utils;

import arc.struct.*;

public interface Autocompleter {
    default void initialize() { }

    Autocompleteable getCompletion(String input);

    boolean matches(String input);

    Seq<Autocompleteable> closest(String input);
}

package mindustry.client.utils;

import arc.struct.*;

public interface Autocompleter {
    default void initialize() { }

    Seq<Autocompleteable> closest(String input);
}

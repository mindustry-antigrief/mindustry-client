package mindustry.client.utils;

import arc.struct.Seq;

public interface Autocompleter {
    default void initialize() { }

    Autocompleteable getCompletion(String input);

    boolean matches(String input);

    Seq<Autocompleteable> closest(String input);
}

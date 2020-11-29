package mindustry.client.utils;

public interface Autocompleteable {

    /** How well the given string matches, on a scale from 0-1 */
    float matches(String input);

    /** Returns the completion of the given input that is displayed. */
    String getHover(String input);

    /** Returns the completion of the given input. */
    String getCompletion(String input);
}

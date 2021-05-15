package mindustry.client.utils;

import arc.struct.*;
import mindustry.gen.*;
import mindustry.ui.*;

public class BlockEmotes implements Autocompleter {

    private final Seq<BlockEmote> emotes = new Seq<>();
    public void initialize() {
        Fonts.stringIcons.each((name, ch) -> emotes.add(new BlockEmotes.BlockEmote(ch, name)));

        for (ObjectIntMap.Entry<String> entry : Iconc.codes) {
            emotes.add(new BlockEmote(Character.toString((char)entry.value), entry.key));
        }
    }

    public Autocompleteable getCompletion(String input) {
        return bestMatch(input);
    }

    private BlockEmote bestMatch(String input) {
        return emotes.max(e -> e.matches(input));
    }

    @Override
    public boolean matches(String input) {
        Autocompleteable match = bestMatch(input);
        if (match == null) {
            return false;
        }
        return match.matches(input) > 0.5f;
    }

    public Seq<Autocompleteable> closest(String input) {
        return emotes.sort(item -> item.matches(input)).map(item -> item);
    }

    private static class BlockEmote implements Autocompleteable {
        private final String unicode, name;

        public BlockEmote(String unicode, String name) {
            this.unicode = unicode;
            this.name = name;
        }

        @Override
        public float matches(String input) {
            if (!input.contains(":")) return 0f;

            int count = 0;
            for (char c : input.toCharArray()) {
                if (c == ':') {
                    count++;
                }
            }
            if (count % 2 == 0) return 0f;

            Seq<String> items = new Seq<>(input.split(":"));
            if (items.size == 0) return 0f;
            String text = items.peek();
            float dst = BiasedLevenshtein.biasedLevenshteinInsensitive(text, name);
            dst *= -1;
            dst += name.length();
            dst /= name.length();
            return dst;
        }

        @Override
        public String getCompletion(String input) {
            Seq<String> items = new Seq<>(input.split(":"));
            if (items.isEmpty()) return input;
            items.pop();
            String start = items.reduce("", String::concat);
            return start + unicode;
        }

        @Override
        public String getHover(String input) {
            if (!input.contains(":")) return input;
            Seq<String> items = new Seq<>(input.split(":"));
            if (items.size == 0) return input;
            String text = items.peek();
            return input.replace(":" + text, ":" + name + ":");
        }
    }
}

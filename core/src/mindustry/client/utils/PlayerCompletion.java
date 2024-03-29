package mindustry.client.utils;

import arc.struct.*;
import arc.util.*;
import mindustry.gen.*;

public class PlayerCompletion implements Autocompleter {

    public Seq<Autocompleteable> closest(String input) {
        return Groups.player.array.map(PlayerMatcher::new).sort(p -> p.matches(input)).as();
    }

    private static class PlayerMatcher implements Autocompleteable {
        private final String name;
        private final String matchName;

        public PlayerMatcher(Player player) {
            name = Strings.stripColors(player.name);
            matchName = Strings.stripColors(player.name.replaceAll("\\s", ""));
        }

        @Override
        public float matches(String input) {
            String text = getLast(input);
            if (text == null) return 0f;

            float dst = ClientUtils.biasedLevenshtein(text, matchName);
            dst *= -1;
            dst += matchName.length();
            dst /= matchName.length();
            return dst;
        }

        @Override
        public String getCompletion(String input) {
            String text = getLast(input);
            if (text == null) return input;

            return input.replace("@" + text, name);
        }

        @Override
        public String getHover(String input) {
            String text = getLast(input);
            if (text == null) return input;

            return input.replace("@" + text, name);
        }

        private String getLast(String input) {
            Seq<String> strings = new Seq<>(input.split("\\s"));
            if (strings.isEmpty()) {
                return null;
            }
            String text = strings.peek();
            if (!text.startsWith("@")) return null;
            return text.replaceAll("@", "");
        }
    }
}

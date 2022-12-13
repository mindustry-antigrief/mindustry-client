package mindustry.client.utils;

import arc.struct.*;
import arc.util.*;
import arc.util.serialization.*;
import mindustry.*;
import mindustry.client.*;

public class CommandCompletion implements Autocompleter {
    private static final Seq<CommandCompletable> commands = new Seq<>();

    @Override
    public void initialize() {
        reset(true);

        Vars.netClient.addPacketHandler("commandList", list -> {
            Log.debug("Received Command List: " + list);
            var json = Jval.read(list);
            var cmds = json.get("commands").asObject();
            if (!cmds.isEmpty()) {
                reset(false);
                var prefix = json.getString("prefix", "/");
                for (var c : cmds) {
                    commands.add(new CommandCompletable(c.key, c.key + " " + c.value.asString(), prefix));
                }
            }
        });
    }

    public static void reset(boolean addServer) {
        commands.clear();
        addCommands(ClientVars.clientCommandHandler);
        if (addServer) addCommands(Vars.netServer.clientCommands);
    }

    private static void addCommands(CommandHandler handler) {
        commands.addAll(handler.getCommandList().map(c -> new CommandCompletable(c.text, c.text + " " + c.paramText, handler.getPrefix())));
    }

    @Override
    public Autocompleteable getCompletion(String input) {
        return bestMatch(input);
    }

    private Autocompleteable bestMatch(String input) {
        return commands.max(e -> e.matches(input));
    }

    @Override
    public boolean matches(String input) {
        return closest(input).any();
    }

    @Override
    public Seq<Autocompleteable> closest(String input) {
        return commands.sort(item -> item.matches(input)).as();
    }

    private static class CommandCompletable implements Autocompleteable {
        private final String command;
        private final String usage;

        public CommandCompletable(String command, String usage, String prefix) {
            this.command = prefix + command;
            this.usage = prefix + usage;
        }

        @Override
        public float matches(String input) {
            if (input == null) return 0f;
            if (input.split("\\s").length > 1) return 0f;
            if (!input.startsWith(String.valueOf(command.charAt(0)))) return 0f;

            float dst = BiasedLevenshtein.biasedLevenshteinInsensitive(input, command);
            dst *= -1;
            dst += command.length();
            dst /= command.length();
            return dst;
        }

        @Override
        public String getCompletion(String input) {
            return command;
        }

        @Override
        public String getHover(String input) {
            return usage;
        }
    }
}

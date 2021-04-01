package mindustry.client.utils;

import arc.struct.*;
import arc.util.*;
import mindustry.*;
import mindustry.client.*;

public class CommandCompletion implements Autocompleter {
    private final Seq<CommandCompletable> commands = new Seq<>();

    @Override
    public void initialize() {
        String prefix = Reflect.get(Vars.netServer.clientCommands, "prefix");
        String finalPrefix = prefix;
        commands.addAll(Vars.netServer.clientCommands.getCommandList().map(inp -> new CommandCompletable(inp.text, inp.text + inp.paramText, finalPrefix)));
        prefix = Reflect.get(ClientVars.clientCommandHandler, "prefix");
        String finalPrefix1 = prefix;
        commands.addAll(ClientVars.clientCommandHandler.getCommandList().map(inp -> new CommandCompletable(inp.text, inp.text + inp.paramText, finalPrefix1)));
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
        return commands.sort(item -> item.matches(input)).map(item -> item);
    }

    private class CommandCompletable implements Autocompleteable {
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

            float dst = BiasedLevenshtein.biasedLevenshtein(input, command);
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

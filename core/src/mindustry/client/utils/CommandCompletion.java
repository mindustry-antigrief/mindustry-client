package mindustry.client.utils;

import arc.struct.*;
import arc.util.*;
import arc.util.serialization.*;
import mindustry.*;
import mindustry.client.*;
import java.util.*;

public class CommandCompletion implements Autocompleter {
    private static final Seq<CommandCompletable> commands = new Seq<>();

    @Override
    public void initialize() {
        reset(true);

        Vars.netClient.addPacketHandler("commandList", list -> {
            Log.debug("Received Command List: @", list);
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
    public Seq<Autocompleteable> closest(String input) {
        return commands.sort(item -> item.matches(input)).as();
    }

    private static class CommandCompletable implements Autocompleteable {
        private final String command;
        private final String usage;
        private final String[] usageWords;

        public CommandCompletable(String command, String usage, String prefix) {
            this.command = prefix + command;
            this.usage = prefix + usage;
            this.usageWords = this.usage.split("\\s");
        }

        @Override
        public float matches(String input) {
            if (input == null) return 0f;
            if (input.split("\\s", -1).length > 1){
                if(input.startsWith(command + " ")) return 0.8f; //this should be low priority so the user can see the args, but also autocomplete other things
                return 0f;
            }
            if (!input.startsWith(String.valueOf(command.charAt(0)))) return 0f;

            float dst = ClientUtils.biasedLevenshtein(input, command);
            dst *= -1;
            dst += command.length();
            dst /= command.length();
            return dst;
        }

        @Override
        public String getCompletion(String input) {
            if (input.split("\\s", -1).length > 1) return input; //if the user has started typing arguments, this isn't actually an autocomplete request
            return command;
        }

        @Override
        public String getHover(String input) {
            var words = input.split("\\s", -1);
            if(words.length == 1) return usage;
            //User has started typing arguments
            var joiner = new StringJoiner(" ");
            int start = words.length - 1;
            for(int i = 0; i < words.length - 1; i ++){
                joiner.add(words[i]);
                if(words[i].equals("%c") || words[i].equals("%h")) start++;
            }
            for(int i = start; i < usageWords.length; i ++){
                joiner.add(i == start ? "[accent]" + usageWords[i] + "[]" : usageWords[i]);
            }
            return joiner.toString();
        }
    }
}

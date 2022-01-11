package mindustry.client.utils;

import arc.util.Strings;
import mindustry.gen.Groups;
import mindustry.world.blocks.logic.LogicBlock;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import static mindustry.Vars.player;

public class ProcessorPatcher {

    private static Pattern PATTERN = Pattern.compile("(.*\\n)?ubind (@?[^ ]+)\\nsensor (\\w+) @unit @flag\\nop add (\\w+) \\4 1\\njump [0-9]+ greaterThanEq \\4 \\d+\\njump [0-9]+ notEqual ([^ ]+) \\3\\nset \\4 0\\n?(.*)", Pattern.MULTILINE | Pattern.DOTALL);
    private static Pattern JUMP_PATTERN = Pattern.compile("jump (\\d+)(.*)", Pattern.MULTILINE);

    public static int countProcessors() {
        return Groups.build.count(building -> building.team == player.team() && building instanceof LogicBlock.LogicBuild build && needFix(build.code));
    }

    public static boolean needFix(String code) {
        return PATTERN.matcher(code).find();
    }

    public static String patch(String code) {
        Matcher matcher = PATTERN.matcher(code);
        if (!matcher.find()) {
            return code;
        }
        StringBuffer buffer = new StringBuffer();
        int bindLine = (int) IntStream.range(0, matcher.end(1) + 1).filter(i -> code.charAt(i) == '\n').count();
        replaceJumps(buffer, matcher.group(1), bindLine);
        buffer.append("ubind ").append(matcher.group(2)).append('\n');
        buffer.append("sensor ").append(matcher.group(3)).append(" @unit @flag\n");
        buffer.append("jump ").append(bindLine).append(" notEqual ").append(matcher.group(3)).append(' ').append(matcher.group(5)).append('\n');
        replaceJumps(buffer, matcher.group(6), bindLine);
        return buffer.toString();
    }

    private static void replaceJumps(StringBuffer buffer, String code, int bindLine) {
        Matcher matcher = JUMP_PATTERN.matcher(code);
        while(matcher.find()) {
            int line = Strings.parseInt(matcher.group(1));
            matcher.appendReplacement(buffer, String.format("jump %d$2", line < bindLine ? line : line - 3));
        }
        matcher.appendTail(buffer);
    }

}

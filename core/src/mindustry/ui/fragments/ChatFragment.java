package mindustry.ui.fragments;

import arc.*;
import arc.Input.*;
import arc.input.*;
import arc.struct.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.scene.*;
import arc.scene.ui.*;
import arc.scene.ui.Label.*;
import arc.scene.ui.layout.*;
import arc.struct.Array;
import arc.util.*;
import arc.util.CommandHandler.*;
import mindustry.*;
import mindustry.client.ui.*;
import mindustry.client.utils.*;
import mindustry.entities.type.*;
import mindustry.gen.*;
import mindustry.input.*;
import mindustry.ui.*;

import java.util.regex.*;

import static arc.Core.*;
import static mindustry.Vars.net;
import static mindustry.Vars.*;

public class ChatFragment extends Table{
    private final static int messagesShown = 10;
    private Array<ChatMessage> messages = new Array<>();
    private float fadetime;
    private boolean shown = false;
    public ResizingTextField chatfield;
    public Label autocomplete;
    private Label fieldlabel = new Label(">");
    private BitmapFont font;
    private GlyphLayout layout = new GlyphLayout();
    private float offsetx = Scl.scl(4), offsety = Scl.scl(4), fontoffsetx = Scl.scl(2), chatspace = Scl.scl(50);
    private Color shadowColor = new Color(0, 0, 0, 0.4f);
    private float textspacing = Scl.scl(10);
    private Array<String> history = new Array<>();
    private int historyPos = 0;
    private int scrollPos = 0;
    private Fragment container = new Fragment(){
        @Override
        public void build(Group parent){
            scene.add(ChatFragment.this);
        }
    };

    public ChatFragment(){
        super();

        setFillParent(true);
        font = Fonts.def;

        visible(() -> {
            if(!net.active() && messages.size > 0){
                clearMessages();

                if(shown){
                    hide();
                }
            }

            return net.active();
        });

        update(() -> {

            if(net.active() && input.keyTap(Binding.chat) && (scene.getKeyboardFocus() == chatfield || scene.getKeyboardFocus() == null)){
                toggle();
            }

            if(shown){
                float max = chatfield.getGlyphPositions().get(chatfield.getText().length());
                chatfield.setWidth(max + 5F);
                autocomplete.setX(max + 8F);
                boolean shown = false;
                String completion = null;

                if(chatfield.getText().startsWith("/") || chatfield.getText().startsWith("!")){
                    Array<String> commands = new Array<>();
                    for(Command command : netServer.clientCommands.getCommandList()){
                        commands.add("/" + command.text);
                    }
                    String match = commands.min((str) -> distance(chatfield.getText(), str));
                    autocomplete.setText(match.substring(Math.min(chatfield.getText().length(), match.length())));
                    completion = match;
                    shown = true;
                }
                String[] words2 = chatfield.getText().split("\\s");
                if(words2.length > 0){
                    String text = words2[words2.length - 1];

                    Player closestMatch = playerGroup.all().min((p) -> distance(text, p.name));
                    boolean containsName = distance(text, closestMatch.name) < 2 && text.length() > 3;
                    if(containsName){
                        shown = true;
                        autocomplete.setText(closestMatch.name.substring(Math.min(text.length(), closestMatch.name.length())));
                        completion = closestMatch.name;
                    }
                    if(!shown){
                        autocomplete.setText("");
                    }
                }
                if(input.keyTap(KeyCode.TAB)){
                    if(completion != null){
                        String[] words = chatfield.getText().split("\\s");
                        chatfield.setText(chatfield.getText().replace(words[words.length - 1], completion));
                        chatfield.setCursorPosition(chatfield.getText().length());
                    }
                }
                if(input.keyTap(Binding.chat_history_prev) && historyPos < history.size - 1){
                    if(historyPos == 0) history.set(0, chatfield.getText());
                    historyPos++;
                    updateChat();
                }
                if(input.keyTap(Binding.chat_history_next) && historyPos > 0){
                    historyPos--;
                    updateChat();
                }
                scrollPos = (int)Mathf.clamp(scrollPos + input.axis(Binding.chat_scroll), 0, Math.max(0, messages.size - messagesShown));
            }
        });

        history.insert(0, "");
        setup();
    }

    public float distance(String word, String word2){
        Pattern regex = Pattern.compile("\\[[^\\[\\]]+\\]");
        Matcher m = regex.matcher(word.toLowerCase().replace("]", " ]"));
        String a2 = m.replaceAll("").replace(" ", "");

        m = regex.matcher(word2.toLowerCase().replace("]", " ]"));
        String b2 = m.replaceAll("").replace(" ", "");

        if(b2.toLowerCase().contains(word)){
            if(b2.toLowerCase().startsWith(a2)){
                // Discount for if the word starts with the input
                return 0.25F * Levenshtein.distance(a2.toLowerCase(), b2.toLowerCase());
            }else{
                // Discount for if the word contains the input
                return 0.5F * Levenshtein.distance(a2.toLowerCase(), b2.toLowerCase());
            }
        }
        return Levenshtein.distance(a2, b2);
    }


    public Fragment container(){
        return container;
    }

    public void clearMessages(){
        messages.clear();
        history.clear();
        history.insert(0, "");
    }

    private void setup(){
        fieldlabel.setStyle(new LabelStyle(fieldlabel.getStyle()));
        fieldlabel.getStyle().font = font;
        fieldlabel.setStyle(fieldlabel.getStyle());

        chatfield = new ResizingTextField("", new TextField.TextFieldStyle(scene.getStyle(TextField.TextFieldStyle.class)));
        chatfield.setMaxLength(Vars.maxTextLength);
        chatfield.getStyle().background = null;
        chatfield.getStyle().font = Fonts.chat;
        chatfield.getStyle().fontColor = Color.white;
        chatfield.setStyle(chatfield.getStyle());
        autocomplete = new Label("", new Label.LabelStyle(scene.getStyle(Label.LabelStyle.class)));
        autocomplete.getStyle().background = null;
        autocomplete.getStyle().font = Fonts.chat;
        autocomplete.getStyle().fontColor = Color.lightGray;
        autocomplete.setStyle(autocomplete.getStyle());
        autocomplete.visible(() -> chatfield.isVisible());

        bottom().left().marginBottom(offsety).marginLeft(offsetx * 2).add(fieldlabel).padBottom(6f);
        HorizontalGroup group = new HorizontalGroup();
        group.addChild(chatfield);
        group.addChild(autocomplete);
        add(group).padBottom(offsety).padLeft(offsetx).growX().padRight(offsetx).height(28);
//        add(autocomplete).padBottom(offsety).padLeft(offsetx).growX().padRight(offsetx).height(28);

        if(Vars.mobile){
            marginBottom(105f);
            marginRight(240f);
        }
    }

    @Override
    public void draw(){
        float opacity = Core.settings.getInt("chatopacity") / 100f;
        float textWidth = Math.min(Core.graphics.getWidth()/1.5f, Scl.scl(700f));

        Draw.color(shadowColor);

        if(shown){
            Fill.crect(offsetx, chatfield.getY(), chatfield.getWidth() + 15f, chatfield.getHeight() - 1);
        }

        super.draw();

        float spacing = chatspace;

        chatfield.visible(shown);
        fieldlabel.visible(shown);

        Draw.color(shadowColor);
        Draw.alpha(shadowColor.a * opacity);

        float theight = offsety + spacing + getMarginBottom();
        for(int i = scrollPos; i < messages.size && i < messagesShown + scrollPos && (i < fadetime || shown); i++){

            layout.setText(font, messages.get(i).formattedMessage, Color.white, textWidth, Align.bottomLeft, true);
            theight += layout.height + textspacing;
            if(i - scrollPos == 0) theight -= textspacing + 1;

            font.getCache().clear();
            font.getCache().addText(messages.get(i).formattedMessage, fontoffsetx + offsetx, offsety + theight, textWidth, Align.bottomLeft, true);

            if(!shown && fadetime - i < 1f && fadetime - i >= 0f){
                font.getCache().setAlphas((fadetime - i) * opacity);
                Draw.color(0, 0, 0, shadowColor.a * (fadetime - i) * opacity);
            }else{
                font.getCache().setAlphas(opacity);
            }

            Fill.crect(offsetx, theight - layout.height - 2, textWidth + Scl.scl(4f), layout.height + textspacing);
            Draw.color(shadowColor);
            Draw.alpha(opacity * shadowColor.a);

            font.getCache().draw();
        }

        Draw.color();

        if(fadetime > 0 && !shown)
            fadetime -= Time.delta() / 180f;
    }

    private boolean equals(String a, String b){
        Pattern regex = Pattern.compile("\\[[^\\[\\]]+\\]");
        Matcher m = regex.matcher(a.toLowerCase().replace("]", " ]"));
        String a2 = m.replaceAll("").replace(" ", "");

        m = regex.matcher(b.toLowerCase().replace("]", " ]"));
        String b2 = m.replaceAll("").replace(" ", "");

        return a2.equals(b2);
    }

    public void sendMessage(){
        String message = chatfield.getText();
        clearChatInput();

        if(message.replaceAll(" ", "").isEmpty()) return;

        history.insert(1, message);

        if(message.startsWith("/")){
            try{
                message = message.substring(((String)PrivateAccessRemover.getPrivateField(netServer.clientCommands, "prefix")).length());
            }catch(NullPointerException ignored){} //if we get this it's already too late

            String commandstr = message.contains(" ") ? message.substring(0, message.indexOf(" ")) : message;
            String argstr = message.contains(" ") ? message.substring(commandstr.length() + 1) : "";

            Array<String> result = new Array<>();
            ObjectMap<String, CommandHandler.Command> commands = (ObjectMap<String, CommandHandler.Command>)PrivateAccessRemover.getPrivateField(netServer.clientCommands, "commands");
            Command command = commands.get(commandstr);
            if(command != null){
                if(localCommands.contains(command)){

                    int index = 0;
                    boolean satisfied = false;

                    while(true){
                        if(index >= command.params.length && !argstr.isEmpty()){
                            return;
                        }else if(argstr.isEmpty()) break;

                        if(command.params[index].optional || index >= command.params.length - 1 || command.params[index + 1].optional){
                            satisfied = true;
                        }

                        if(command.params[index].variadic){
                            result.add(argstr);
                            break;
                        }

                        int next = argstr.indexOf(" ");
                        if(next == -1){
                            if(!satisfied){
                                return;
                            }
                            result.add(argstr);
                            break;
                        }else{
                            String arg = argstr.substring(0, next);
                            argstr = argstr.substring(arg.length() + 1);
                            result.add(arg);
                        }

                        index++;
                    }

                    if(!satisfied && command.params.length > 0 && !command.params[0].optional){
                        return;
                    }

                    ((CommandRunner)PrivateAccessRemover.getPrivateField(command, "runner")).accept(result.toArray(String.class), player);
//                    command.runner.accept(result.toArray(String.class), player);

                    return;
                }else{
                    Call.sendChatMessage("/" + message);
                    return;
                }
            }else{
                Call.sendChatMessage("/" + message);
                return;
            }
        }
        Call.sendChatMessage(message);
    }

    public Player findPlayerByName(String name){
        Player found = null;
        if(name.length() > 1 && name.startsWith("#") && Strings.canParseInt(name.substring(1))){
            int id = Strings.parseInt(name.substring(1));
            found = playerGroup.find(p -> p.id == id);
        }else{
            found = playerGroup.find(p -> equals(p.name, name));
        }
        return found;
    }

    public void toggle(){

        if(!shown){
            scene.setKeyboardFocus(chatfield);
            shown = !shown;
            if(mobile){
                TextInput input = new TextInput();
                input.maxLength = maxTextLength;
                input.accepted = text -> {
                    chatfield.setText(text);
                    sendMessage();
                    hide();
                    Core.input.setOnscreenKeyboardVisible(false);
                };
                input.canceled = this::hide;
                Core.input.getTextInput(input);
            }else{
                chatfield.fireClick();
            }
        }else{
            scene.setKeyboardFocus(null);
            shown = !shown;
            scrollPos = 0;
            sendMessage();
        }
    }

    public void hide(){
        scene.setKeyboardFocus(null);
        shown = false;
        clearChatInput();
    }

    public void updateChat(){
        chatfield.setText(history.get(historyPos));
        chatfield.setCursorPosition(chatfield.getText().length());
    }

    public void clearChatInput(){
        historyPos = 0;
        history.set(0, "");
        chatfield.setText("");
    }

    public boolean shown(){
        return shown;
    }

    public void addMessage(String message, String sender){
        messages.insert(0, new ChatMessage(message, sender));

        fadetime += 1f;
        fadetime = Math.min(fadetime, messagesShown) + 1f;
        
        if(scrollPos > 0) scrollPos++;
    }

    private static class ChatMessage{
        public final String sender;
        public final String message;
        public final String formattedMessage;

        public ChatMessage(String message, String sender){
            this.message = message;
            this.sender = sender;
            if(sender == null){ //no sender, this is a server message?
                formattedMessage = message;
            }else{
                formattedMessage = "[CORAL][[" + sender + "[CORAL]]:[WHITE] " + message;
            }
        }
    }

}

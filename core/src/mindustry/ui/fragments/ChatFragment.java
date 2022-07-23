package mindustry.ui.fragments;

import arc.*;
import arc.Input.*;
import arc.func.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.scene.*;
import arc.scene.ui.*;
import arc.scene.ui.Label.*;
import arc.scene.ui.TextField.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
import mindustry.*;
import mindustry.client.*;
import mindustry.client.ui.*;
import mindustry.client.utils.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.input.*;
import mindustry.ui.*;

import java.util.*;

import static arc.Core.*;
import static mindustry.Vars.*;

public class ChatFragment extends Table{
    private static final int messagesShown = 10;
    private static final ImageButton.ImageButtonStyle uploadStyle = new ImageButton.ImageButtonStyle(Styles.emptyi);
    public Seq<ChatMessage> messages = new Seq<>();
    private float fadetime;
    private boolean shown = false;
    public TextField chatfield;
    private Label fieldlabel = new Label(">");
    private ChatMode mode = ChatMode.normal;
    private Font font;
    private GlyphLayout layout = new GlyphLayout();
    private float offsetx = Scl.scl(4), offsety = Scl.scl(4), fontoffsetx = Scl.scl(2), chatspace = Scl.scl(50);
    private Color shadowColor = new Color(0, 0, 0, 0.5f);
    private float textspacing = Scl.scl(10);
    private Seq<String> history = new Seq<>();
    private int historyPos = 0;
    private int scrollPos = 0;
    private Fragment container = new Fragment(){
        @Override
        public void build(Group parent){
            scene.add(ChatFragment.this);
        }
    };
    private Seq<Autocompleteable> completion = new Seq<>();
    private int completionPos = -1;

    public ChatFragment(){
        super();

        setFillParent(true);
        font = Fonts.def;

        visible(() -> {
            if (state.isMenu() && messages.size > 0) {
                if (shown) hide();
                clearMessages();
            }
            return ui.hudfrag.shown;
        });

        update(() -> {

            if(input.keyTap(Binding.chat) && (scene.getKeyboardFocus() == chatfield || scene.getKeyboardFocus() == null || ui.minimapfrag.shown()) && !ui.scriptfrag.shown()){
                toggle();
            }

            if(shown){
                if(input.keyTap(Binding.chat_history_prev) && historyPos < history.size - 1){
                    if(historyPos == 0) history.set(0, chatfield.getText().replaceFirst("^" + mode.normalizedPrefix(), ""));
                    historyPos++;
                    updateChat();
                }
                if(input.keyTap(Binding.chat_history_next) && historyPos > 0){
                    historyPos--;
                    updateChat();
                }
                if (input.keyTap(Binding.chat_autocomplete) && completion.any() && mode == ChatMode.normal) {
                    completionPos = Mathf.clamp(completionPos, 0, completion.size - 1);
                    chatfield.setText(completion.get(completionPos).getCompletion(chatfield.getText()) + " ");
                    updateCursor();
                } else if (input.keyTap(Binding.chat_mode)) {
                    nextMode();
                }
                scrollPos = (int)Mathf.clamp(scrollPos + input.axis(Binding.chat_scroll), 0, Math.max(0, messages.size - messagesShown));
            }
        });

        history.insert(0, "");
        setup();
    }

    // FINISHME: Why is this so complex???
    void updateCompletion() {
        if (Autocomplete.matches(chatfield.getText())) {
            Seq<Autocompleteable> oldCompletion = completion.copy();
            completion = Autocomplete.closest(chatfield.getText()).filter(item -> item.matches(chatfield.getText()) > 0.5f);
            completion.reverse();
            completion.truncate(4);
            completion.reverse();
            if (!Arrays.equals(completion.items, oldCompletion.items)) {
                completionPos = completion.size - 1;
            }
        } else {
            completion.clear();
        }
    }

    public Fragment container(){
        return container;
    }

    public void clearMessages(){
        if (!settings.getBool("clearchatonleave")) return;
        messages.clear();
        history.clear();
        history.insert(0, "");
    }

    private void setup(){
        uploadStyle.imageCheckedColor = Pal.accent;

        fieldlabel.setStyle(new LabelStyle(fieldlabel.getStyle()));
        fieldlabel.getStyle().font = font;
        fieldlabel.setStyle(fieldlabel.getStyle());

        chatfield = new TextField("", new TextFieldStyle(scene.getStyle(TextFieldStyle.class)));
        chatfield.updateVisibility();
        chatfield.setFocusTraversal(false);
        chatfield.setProgrammaticChangeEvents(true);
        chatfield.setFilter((f, c) -> c != '\t'); // Using .changed(...) and allowing tabs causes problems for tab completion and cursor position, .typed(...) doesn't do what I need
        chatfield.changed(() -> {
            chatfield.setMaxLength(chatfield.getText().startsWith("!js ") ? 0 : maxTextLength - 2); // Scuffed way to allow long js

            var replacement = switch (chatfield.getText().replaceFirst("^" + mode.normalizedPrefix(), "")) {
                case "!r " -> "!e " + ClientVars.lastCertName + " ";
                case "!b " -> "!builder ";
                case "!cu ", "!cr " -> "!cursor ";
                case "!u " -> "!unit ";
                case "!!" -> "! !";
                case "!h " -> "!here ";
                default -> null;
            };
            if (replacement != null) {
                app.post(() -> { // .changed(...) is called in the middle of the typed char being processed, workaround is to update cursor on the next frame
                    chatfield.setText((chatfield.getText().startsWith(mode.normalizedPrefix()) ? mode.normalizedPrefix() : "") + replacement);
                    updateCursor();
                });
            }

            updateCompletion();
        });
        chatfield.setMaxLength(Vars.maxTextLength);
        chatfield.getStyle().background = null;
        chatfield.getStyle().fontColor = Color.white;
        chatfield.setStyle(chatfield.getStyle());
        chatfield.setOnlyFontChars(false);

        bottom().left().marginBottom(offsety).marginLeft(offsetx * 2);
        button(Icon.uploadSmall, uploadStyle, UploadDialog.INSTANCE::show).padRight(5f).tooltip("Upload Images").visible(() -> shown).checked(h -> UploadDialog.INSTANCE.hasImage()); // FINISHME: Bundle
        add(fieldlabel).padBottom(6f);

        add(chatfield).padBottom(offsety).padLeft(offsetx).growX().padRight(offsetx).height(28);

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
            Fill.crect(offsetx, chatfield.y + scene.marginBottom, chatfield.getWidth() + 15f, chatfield.getHeight() - 1);
        }

        super.draw();

        float spacing = chatspace;

        chatfield.visible = shown;
        fieldlabel.visible = shown;

        Draw.color(shadowColor);
        Draw.alpha(shadowColor.a * opacity);

//        float blockFragX = ui.hudfrag.blockfrag.blockPane.localToStageCoordinates(Vec2.ZERO).x;
//        Vec2.ZERO.setZero();

        float theight = offsety + spacing + getMarginBottom() + scene.marginBottom;
        for(int i = scrollPos; i < messages.size && i < messagesShown + scrollPos && (i < fadetime || shown); i++){
            ChatMessage msg = messages.get(i);

            layout.setText(font, msg.formattedMessage, Color.white, textWidth, Align.bottomLeft, true);
            theight += layout.height + textspacing;
            if(i - scrollPos == 0) theight -= textspacing + 1;

            font.getCache().clear();
            font.getCache().setColor(Color.white);
            font.getCache().addText(msg.formattedMessage, fontoffsetx + offsetx, offsety + theight, textWidth, Align.bottomLeft, true);

            Color color = messages.get(i).backgroundColor;
            if (color == null) {
                color = shadowColor;
                color.a = shadowColor.a;
            } else {
                color.a = .8f;
            }

            if(!shown && fadetime - i < 1f && fadetime - i >= 0f){
                font.getCache().setAlphas((fadetime - i) * opacity);
                Draw.color(color.r, color.g, color.b, shadowColor.a * (fadetime - i) * opacity);
            }else{
                font.getCache().setAlphas(opacity);
                Draw.color(color);
            }

            Fill.crect(offsetx, theight - layout.height - 2, textWidth + Scl.scl(4f), layout.height + textspacing);
            Draw.color(shadowColor);
            Draw.alpha(opacity * shadowColor.a);

            font.getCache().draw();

            if (msg.attachments.size() != 0) {
                Draw.color();
                if (!shown) Draw.alpha(Mathf.clamp(fadetime - i, 0, 1) * opacity);
                float x = textWidth - 10f;
                float y = offsety + theight - layout.height;
                Icon.imageSmall.draw(x, y, layout.height, layout.height);
                Tmp.r3.set(x, y, layout.height, layout.height);
                if (Tmp.r3.contains(input.mouse()) && input.keyTap(Binding.select)) {
                    new AttachmentDialog(msg.unformatted, msg.attachments);
                }
            }
        }


        if(fadetime > 0 && !shown){
            fadetime -= Time.delta / 180f;
        }

        if (completion.any() && shown) {
            float pos = Reflect.<FloatSeq>get(chatfield, "glyphPositions").peek() + chatfield.x;
            StringBuilder contents = new StringBuilder();
            int index = 0;
            for (Autocompleteable auto : completion) {
                String completion = auto.getHover(chatfield.getText());
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < Math.min(completion.length(), chatfield.getText().length()); i++) {
                    if (completion.charAt(i) == chatfield.getText().charAt(i)) {
                        sb.append(completion.charAt(i));
                    } else {
                        break;
                    }
                }
                String ending = completion.substring(sb.length());
                if (index == completionPos) {
                    contents.append("[#a9d8ff]");
                }
                contents.append(ending);
                contents.append("[]\n");
                index++;
            }
            font.getCache().clear();
//            float height = font.getCache().getLayouts().sumf(item -> item.height);
            float height = font.getData().lineHeight * completion.size;
//            System.out.println(height);
            font.getCache().addText(contents.toString(), pos, 10f + height);
            Draw.color(shadowColor);
            Fill.crect(pos, 10f + font.getData().lineHeight, font.getCache().getLayouts().max(item -> item.width).width, height - font.getData().lineHeight);
            Draw.color();
            font.getCache().draw();
        }
        Draw.color();
    }

    private void sendMessage(){
        String message = chatfield.getText().trim();
        // FINISHME: make it so you need to press enter twice to send a message starting with /e
        clearChatInput();

        //avoid sending prefix-empty messages
        if(message.isEmpty() || (message.startsWith(mode.prefix) && message.substring(mode.prefix.length()).isEmpty())) return;

        history.insert(1, message.replaceFirst("^" + mode.normalizedPrefix(), ""));

        // Allow sending commands with chat modes; "/t /help" becomes "/help", "/a !go" becomes "!go"
        for (ChatMode mode : ChatMode.all) {
            message = message.replaceFirst("^" + mode.prefix + " ([/!])", "$1");
        }

        //check if it's a command
        CommandHandler.CommandResponse response = ClientVars.clientCommandHandler.handleMessage(message, player);
        if(response.type == CommandHandler.ResponseType.noCommand){ //no command to handle
            String msg = Main.INSTANCE.sign(message);
            Call.sendChatMessage(msg);
            if (message.startsWith(netServer.clientCommands.getPrefix() + "sync")) { // /sync
                ClientVars.syncing = true;
            }
            if (!message.startsWith(netServer.clientCommands.getPrefix())) { // Only fire when not running any command
                Events.fire(new EventType.SendChatMessageEvent(msg));
            }

        }else{

            //a command was sent, now get the output
            if(response.type != CommandHandler.ResponseType.valid){
                String text;

                //send usage
                if(response.type == CommandHandler.ResponseType.manyArguments){
                    text = "[scarlet]Too many arguments. Usage:[lightgray] " + response.command.text + "[gray] " + response.command.paramText;
                }else if(response.type == CommandHandler.ResponseType.fewArguments){
                    text = "[scarlet]Too few arguments. Usage:[lightgray] " + response.command.text + "[gray] " + response.command.paramText;
                }else{ //unknown command
                    int minDst = 0;
                    CommandHandler.Command closest = null;

                    for(CommandHandler.Command command : ClientVars.clientCommandHandler.getCommandList()){
                        int dst = Strings.levenshtein(command.text, response.runCommand);
                        if(dst < 3 && (closest == null || dst < minDst)){
                            minDst = dst;
                            closest = command;
                        }
                    }

                    if(closest != null){
                        text = "[scarlet]Unknown command. Did you mean \"[lightgray]" + closest.text + "[]\"?";
                    }else{
                        text = "[scarlet]Unknown command. Check [lightgray]!help[scarlet].";
                    }
                }

                player.sendMessage(text);
            }
        }
    }

    public void toggle(){

        if(!shown){
            scene.setKeyboardFocus(chatfield);
            shown = true;
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
            //sending chat has a delay; workaround for issue #1943
            Time.runTask(2f, () ->{
                scene.setKeyboardFocus(null);
                shown = false;
                scrollPos = 0;
                sendMessage();
                UploadDialog.INSTANCE.clearImages();
            });
        }
    }

    public void hide(){
        scene.setKeyboardFocus(null);
        shown = false;
        UploadDialog.INSTANCE.clearImages();
        clearChatInput();
    }

    public void updateChat(){
        chatfield.setMaxLength(history.get(historyPos).startsWith("!js ") ? 0 : maxTextLength - 2);
        chatfield.setText(mode.normalizedPrefix() + history.get(historyPos));
        updateCursor();
    }

    public void nextMode(){
        ChatMode prev = mode;

        do{
            mode = mode.next();
        }while(!mode.isValid());

        if(chatfield.getText().startsWith(prev.normalizedPrefix())){
            chatfield.setText(mode.normalizedPrefix() + chatfield.getText().substring(prev.normalizedPrefix().length()));
        }else{
            chatfield.setText(mode.normalizedPrefix());
        }

        updateCursor();
    }

    public void clearChatInput(){
        historyPos = 0;
        history.set(0, "");
        chatfield.setText(mode.normalizedPrefix());
        updateCursor();
    }

    public void updateCursor(){
        chatfield.setCursorPosition(chatfield.getText().length());
    }

    public boolean shown(){
        return shown;
    }

    public ChatMessage addMessage(String message, String sender, Color background, String prefix, String unformatted){
        if(sender == null && message == null) return null;
        ChatMessage msg = new ChatMessage(message, sender, background == null ? null : background.cpy(), prefix, unformatted);
        messages.insert(0, msg);

        doFade(6); // fadetime was originally incremented by 2f, that works out to 6s

        if(scrollPos > 0) scrollPos++;
        return msg;
    }

    public ChatMessage addMessage(String message, String sender, Color background, String prefix){
        return addMessage(message, sender, background, prefix, message);
    }

    public ChatMessage addMessage(String message, String sender, Color background){ // FINISHME: Remove this, merge sender with message
        return addMessage(message, sender, background, "");
    }

    public ChatMessage addMessage(String message, Color background, String unformatted){ // FINISHME: Refactor this
        return addMessage(message, null, background, "", unformatted);
    }

    public ChatMessage addMessage(String message, Color background){ // FINISHME: Do a v132 cleanup of this whole mess
        return addMessage(message, null, background);
    }

    /** @deprecated Kept for mod compatibility */
    @Deprecated
    public void addMessage(String ignored, String message){
        addMessage(message);
    }

    public void addMessage(String message){
        addMessage(message, null, null, "");
    }

    public void doFade(float seconds){
        fadetime += seconds/3; // Seconds/3 since this is scaled by 3 anyways fadetime -= Time.delta / 180f;
        fadetime = Math.min(fadetime, messagesShown);
    }


    public static class ChatMessage{
        public String sender;
        public String message;
        public String formattedMessage;
        public Color backgroundColor;
        public String prefix;
        public String unformatted;
        public List<Image> attachments = new ArrayList<>();

        public ChatMessage(String message, String sender, Color color, String prefix, String unformatted){
            this.message = message;
            this.sender = sender;
            this.prefix = prefix;
            this.unformatted = unformatted;
            backgroundColor = color;
            format();
        }

        public void format() {
            if(sender == null){ //no sender, this is a server message?
                formattedMessage = message == null ? prefix : prefix + message;
            } else {
                formattedMessage = prefix + "[coral][[[white]" + sender + "[coral]]:[white] " + unformatted;
            }
        }
    }

    private enum ChatMode{
        normal(""),
        team("/t"),
        admin("/a", player::admin),
        client("!c")
        ;

        public String prefix;
        public Boolp valid;
        public static final ChatMode[] all = values();

        ChatMode(String prefix){
            this.prefix = prefix;
            this.valid = () -> true;
        }

        ChatMode(String prefix, Boolp valid){
            this.prefix = prefix;
            this.valid = valid;
        }

        public ChatMode next(){
            return all[(ordinal() + 1) % all.length];
        }

        public String normalizedPrefix(){
            return prefix.isEmpty() ? "" : prefix + " ";
        }

        public boolean isValid(){
            return valid.get();
        }
    }
}

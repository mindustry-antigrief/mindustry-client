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
import mindustry.game.EventType.*;
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
    private Seq<Autocompleteable> completion = new Seq<>(); // FINISHME: The autocompletion system is awful.
    private int completionPos = -1;
    private static final Color hoverColor = Color.sky.cpy().mul(0.5f);

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

            if(input.keyTap(Binding.chat) && (scene.getKeyboardFocus() == chatfield || scene.getKeyboardFocus() == null || ui.minimapfrag.shown()) && !ui.consolefrag.shown()){
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
                if (input.keyTap(Binding.chat_autocomplete) && completion.any() /*&& mode == ChatMode.normal*/) {
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

    // FINISHME: Awful.
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

    public void build(Group parent){
        scene.add(this);
    }

    public void clearMessages(){
        if(!settings.getBool("clearchatonleave")) return;
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

            // FINISHME: Implement proper replacement & string interpolation system
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
        chatfield.typed(this::handleType);

        bottom().left().marginBottom(offsety).marginLeft(offsetx * 2).add(fieldlabel).padBottom(6f);

        add(chatfield).padBottom(offsety).padLeft(offsetx).growX().padRight(offsetx).height(28);

        if(Vars.mobile){
            marginBottom(105f);
            marginRight(240f);
        }
    }

    //no mobile support.
    private void handleType(char c){
        int cursor = chatfield.getCursorPosition();
        if(c == ':'){
            int index = chatfield.getText().lastIndexOf(':', cursor - 2);
            if(index >= 0 && index < cursor){
                String text = chatfield.getText().substring(index + 1, cursor - 1);
                String uni = Fonts.getUnicodeStr(text);
                if(uni != null && uni.length() > 0){
                    chatfield.setText(chatfield.getText().substring(0, index) + uni + chatfield.getText().substring(cursor));
                    chatfield.setCursorPosition(index + uni.length());
                }
            }
        }
    }

    protected void rect(float x, float y, float w, float h){
        //prevents texture bindings; the string lookup is irrelevant as it is only called <10 times per frame, and maps are very fast anyway
        Draw.rect("whiteui", x + w/2f, y + h/2f, w, h);
    }

    private IntSeq litUp = new IntSeq();

    @Override
    public void draw(){
        float opacity = Core.settings.getInt("chatopacity") / 100f;
        float textWidth = Math.min(Core.graphics.getWidth()/1.5f, Scl.scl(700f));

        Draw.color(shadowColor);

        if(shown){
            rect(offsetx, chatfield.y + scene.marginBottom, chatfield.getWidth() + 15f, chatfield.getHeight() - 1);
        }

        super.draw();

        float spacing = chatspace;

        chatfield.visible = shown;
        fieldlabel.visible = shown;

        Draw.color(shadowColor, shadowColor.a * opacity);

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

            rect(offsetx, theight - layout.height - 2, textWidth + Scl.scl(4f), layout.height + textspacing);

            msg.start = theight - layout.height - 2;
            msg.height = layout.height + textspacing;
            float mousey = input.mouseY();
            float mousex = input.mouseX();
            if (mousey > msg.start && mousey < msg.start + msg.height && msg.buttons != null) {
                litUp.clear();
                var co = Tmp.c1.set(Draw.getColor()); // Save current color for later
                for (var g : font.getCache().getLayouts()) {
                    for (var r : g.runs) {
                        float x = r.x + r.xAdvances.get(0) + fontoffsetx + offsetx;
                        int j = 0;
                        for (var c : r.glyphs) {
                            int idx = r.textPositions.get(j++);
                            float w = r.xAdvances.get(j);
                            float liney = r.y + theight - font.getLineHeight() + 2;
                            for (var area : msg.buttons) {
                                if (idx >= area.start && idx < area.end) {
                                    if (mousex > x && mousex <= x + w && mousey > liney && mousey < liney + font.getLineHeight()) {
                                        for (int k = area.start; k < area.end; k++) {
                                            litUp.add(k);
                                        }
                                        if (Core.input.keyTap(Binding.select)) {
                                            area.lambda.run();
                                        }
                                        if (control.input instanceof DesktopInput) {
                                            Core.graphics.cursor(Graphics.Cursor.SystemCursor.hand);
                                        }
                                    }
                                }
                            }
                            x += w;
                        }
                    }
                }

                Draw.color(hoverColor);
                for (var g : font.getCache().getLayouts()) {
                    for (var r : g.runs) {
                        float x = r.x + r.xAdvances.get(0) + fontoffsetx + offsetx;
                        int j = 0;
                        for (var c : r.glyphs) {
                            int idx = r.textPositions.get(j++);
                            float w = r.xAdvances.get(j);
                            float liney = r.y + theight - font.getLineHeight() + 2;
                            if (litUp.contains(idx)) {
                                rect(x, liney, w, font.getLineHeight());
                            }
                            x += w;
                        }
                    }
                }
                Draw.color(co); // Reset color
            }
            Draw.color(shadowColor, shadowColor.a * opacity);

            font.getCache().draw();

            if (msg.attachments != null && msg.attachments.any()) {
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

        StringBuilder messageBuild = new StringBuilder(message);

        for (var entry : ClientVars.containsCommandHandler.entries()){ // s l o w
            String prefix = entry.key;
            int pos = -1;
            while (true) {
                pos = messageBuild.indexOf(prefix, pos + 1);
                if(pos == -1 || pos == messageBuild.length() - 1) break;
                String tmp = messageBuild.substring(pos + 1);
                if(tmp.startsWith(prefix)){ // double prefix - escaped
                    messageBuild.deleteCharAt(pos);
                    continue;
                }
                for(var pair : entry.value){
                    String cmd = pair.getFirst();
                    if(tmp.startsWith(cmd)){
                        String replace = pair.getSecond().get();
                        messageBuild.replace(pos, pos + cmd.length() + 1, replace);
                        pos += replace.length() - 1;
                        break;
                    }
                }
            }
        }
        message = messageBuild.toString();

        //check if it's a command
        CommandHandler.CommandResponse response = ClientVars.clientCommandHandler.handleMessage(message, player);
        if(response.type == CommandHandler.ResponseType.noCommand){ //no command to handle
            String msg = Main.INSTANCE.sign(message);
            Events.fire(new ClientChatEvent(message));
            Call.sendChatMessage(msg);
            if (message.startsWith(netServer.clientCommands.getPrefix() + "sync")) { // /sync
                ClientVars.syncing = true;
            }
            if (!message.startsWith(netServer.clientCommands.getPrefix())) { // Only fire when not running any command
                Events.fire(new EventType.SendChatMessageEvent(msg));
            }
            return;
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
            return;
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
            Time.runTask(2f, () -> {
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

    /**
     * Adds a ChatMessage.
     * @param message     The message as formatted by the server
     * @param sender      The sender of the message
     * @param background  The background color of the message
     * @param prefix      The client-added prefix of the message, such as the wrench icon
     * @param unformatted The raw text of the message without the sender header
     */
    public ChatMessage addMessage(String message, String sender, Color background, String prefix, String unformatted){
        if(sender == null && message == null) return null;
        ChatMessage msg = new ChatMessage(message, sender, background == null ? null : background.cpy(), prefix, unformatted);
        messages.insert(0, msg);

        if (messages.size >= 100) { // Free up memory by disposing of stuff in old messages
            var msg100 = messages.get(99);
            msg100.attachments = null;
            msg100.buttons = null;
        }

        doFade(6); // fadetime was originally incremented by 2f, that works out to 6s

        if(scrollPos > 0) scrollPos++;
        return msg;
    }

    /** Alias for {@link #addMessage(String)} that returns a ChatMessage since return type changes are binary incompatible and break mods */
    public ChatMessage addMsg(String message) {
        return addMessage(message, null, null, "", message);
    }

    /** Adds a message, see {@link #addMsg} for ChatMessage return type */
    public void addMessage(String message) {
        addMsg(message);
    }

    /** @deprecated Kept for mod compatibility */
    @Deprecated
    public void addMessage(String ignored, String message){
        addMessage(message, null, null, "", message);
    }

    public void doFade(float seconds){
        fadetime += seconds/3; // Seconds/3 since this is scaled by 3 anyways fadetime -= Time.delta / 180f;
        fadetime = Math.min(fadetime, messagesShown);
    }

    public static class ClickableArea {
        public int start, end;
        public Runnable lambda;

        public ClickableArea(int start, int end, Runnable lambda) {
            this.start = start;
            this.end = end;
            this.lambda = lambda;
        }
    }

    public static class ChatMessage{
        /** The sender (i.e. "bar") */
        public String sender;
        /** The full formatted message **as sent by the server** (i.e. "[bar]: hello", but with color tags) */
        public String message;
        /** The message as reformatted by the client (i.e. "(checkmark) [bar]: hello" but with color tags */
        public String formattedMessage = "";
        /** The background color of the message. */
        public Color backgroundColor;
        /** The prefix of the message, as added by the client.  This is usually an icon, such as a wrench or checkmark. */
        public String prefix;
        /** The content of the message (i.e. "gg") */
        public String unformatted;
        @Nullable public Seq<Image> attachments = new Seq<>(); // This seq is deleted after 100 new messages to save ram
        public float start, height;
        @Nullable public Seq<ClickableArea> buttons = new Seq<>(); // This seq is deleted after 100 new messages to save ram

        /**
         * Creates a new ChatMessage.
         * @param message     The message as formatted by the server
         * @param sender      The sender of the message
         * @param color       The background color of the message
         * @param prefix      The client-added prefix of the message, such as the wrench icon
         * @param unformatted The raw text of the message without the sender header
         */
        public ChatMessage(String message, String sender, Color color, String prefix, String unformatted){
            this.message = message;
            this.sender = sender;
            this.prefix = prefix;
            this.unformatted = unformatted;
            backgroundColor = color;
            format(false);
        }

        public ChatMessage addButton(int start, int end, Runnable lambda) {
            if (buttons != null) buttons.add(new ClickableArea(start, end, lambda));
            return this;
        }

        public ChatMessage addButton(String text, Runnable lambda) {
            int i = formattedMessage.indexOf(text);
            return addButton(i, i + text.length(), lambda);
        }

        private void format(boolean moveButtons) {
            int initial = formattedMessage.length();
            if(sender == null){ //no sender, this is a server message?
                formattedMessage = prefix + (message == null ? "" : message);
            } else {
                formattedMessage = prefix + "[coral][[[white]" + sender + "[coral]]:[white] " + unformatted;
            }
            int shift = formattedMessage.length() - initial;
            if (moveButtons && buttons != null) {
                for (var b : buttons) {
                    b.start += shift;
                    b.end += shift;
                }
            }
        }

        public void format() {
            format(true);
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

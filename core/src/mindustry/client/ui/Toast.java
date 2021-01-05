package mindustry.client.ui;

import arc.Core;
import arc.math.Interp;
import arc.scene.actions.Actions;
import arc.scene.actions.TranslateByAction;
import arc.scene.ui.layout.Table;
import arc.util.Align;
import mindustry.gen.Tex;
import java.time.Instant;

/** An info toast that pops down from the top of the screen. */
public class Toast extends Table {
    /** The last time fadeTime was reset.  In unix epoch time. */
    private long lastReset;
    private final Table container;
    private ToastState state = ToastState.FADING_IN;
    private long fadeTime;
    private TranslateByAction translateByAction;

    public Toast(float fadeTime, float fadeDuration) {
        super(Tex.button);
        setFadeTime(fadeTime);
        container = Core.scene.table();
        container.top().add(this);
        setTranslation(0f, getPrefHeight());
        translateByAction = Actions.translateBy(0f, -getPrefHeight(), fadeDuration, Interp.fade);
        color.a = 0f;
        addAction(Actions.sequence(Actions.parallel(translateByAction, Actions.fadeIn(fadeDuration, Interp.pow4)), Actions.run(() -> state = ToastState.NORMAL)));
        setOrigin(Align.bottom);
        update(() -> {
            if (state == ToastState.NORMAL) {
                if (Instant.now().toEpochMilli() >= lastReset + this.fadeTime) {
                    state = ToastState.FADING_OUT;
                    addAction(Actions.sequence(Actions.parallel(Actions.translateBy(0f, getPrefHeight(), fadeDuration, Interp.fade),
                            Actions.fadeOut(fadeDuration, Interp.pow4)), Actions.remove()));
                }
            }
        });
    }

    public Toast() {
        this(1f, 1f);
    }

    public Toast(float fadeTime){
        this(fadeTime, 1f);
    }

    /** Number of seconds until it fades. */
    public void setFadeTime(float fadeTime) {
        this.fadeTime = (long)(fadeTime * 1000);
        lastReset = Instant.now().toEpochMilli();
    }

    private enum ToastState { FADING_IN, NORMAL, FADING_OUT }
}

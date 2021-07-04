package mindustry.client.ui;

import arc.*;
import arc.math.*;
import arc.scene.actions.*;
import arc.scene.ui.layout.*;
import arc.util.*;
import mindustry.gen.*;

import java.time.*;

/** An info toast that pops down from the top of the screen. */
public class Toast extends Table {
    /** Time since fadeAfter was set (unix epoch),  */
    private long lastReset;
    private final Table container;
    private ToastState state = ToastState.FADING_IN;
    private long fadeAfter;
    private TranslateByAction translateByAction;

    /** @param fadeAfter begins fading after this many seconds
     * @param fadeDuration fades over this many seconds */
    public Toast(float fadeAfter, float fadeDuration) {
        super(Tex.button);
        setFadeAfter(fadeAfter);
        container = Core.scene.table();
        container.top().add(this);
        container.marginTop(Core.scene.find("coreinfo").getPrefHeight() / Scl.scl() / 2);
        setTranslation(0f, getPrefHeight());
        translateByAction = Actions.translateBy(0f, -getPrefHeight(), fadeDuration, Interp.fade);
        color.a = 0f;
        addAction(Actions.sequence(Actions.parallel(translateByAction, Actions.fadeIn(fadeDuration, Interp.pow4)), Actions.run(() -> state = ToastState.NORMAL)));
        setOrigin(Align.bottom);
        update(() -> {
            if (state == ToastState.NORMAL) {
                if (Instant.now().toEpochMilli() >= lastReset + this.fadeAfter) {
                    state = ToastState.FADING_OUT;
                    addAction(Actions.sequence(Actions.parallel(Actions.translateBy(0f, getPrefHeight(), fadeDuration, Interp.fade),
                            Actions.fadeOut(fadeDuration, Interp.pow4)), Actions.remove()));
                }
            }
        });
    }

    /** Creates a {@link #Toast(float, float)} with {@code fadeAfter} and {@code fadeDuration} of 1s */
    public Toast() {
        this(1f, 1f);
    }

    /** Creates a {@link #Toast(float, float)} with a {@code fadeDuration} of 1s */
    public Toast(float fadeAfter){
        this(fadeAfter, 1f);
    }

    /** Number of seconds until it fades. */
    public void setFadeAfter(float fadeTime) {
        this.fadeAfter = (long)(fadeTime * 1000);
        lastReset = Instant.now().toEpochMilli();
    }

    private enum ToastState { FADING_IN, NORMAL, FADING_OUT }
}

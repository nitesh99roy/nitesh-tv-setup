package com.gathin.tvhome;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.graphics.Color;
import android.os.Handler;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.PathInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Choreographs the "Welcome <name>" block on the right of the home screen.
 *
 * Coming Home plays the full intro: WELCOME settles from wide tracking, a glowing pen writes the
 * name letter by letter, the letters fill in as the pen line fades, the accent line grows and a
 * spark runs along it. Moving between dock and grid only fades the block. While it is visible a
 * soft shine in the focused app's colour sweeps the name and line every few seconds.
 */
final class Welcome {
    private static final long SHINE_EVERY_MS = 8000L;
    private static final float LABEL_SPACING = 0.32f;

    private final View block;
    private final TextView label;
    private final WelcomeNameView name;
    private final AccentLineView accent;
    private final View weatherRow;
    private final WeatherIconView weatherIcon;
    private final TextView weatherTemp;
    private final TextView weatherText;
    private final Handler handler;
    private final float density;
    private final boolean enabled;

    private boolean shown;
    private boolean introPlayed;
    private AnimatorSet intro;
    private AnimatorSet shineSet;
    private ValueAnimator tintAnim;

    private final Runnable shineLoop = new Runnable() {
        @Override
        public void run() {
            runShine();
            handler.postDelayed(this, SHINE_EVERY_MS);
        }
    };

    Welcome(Activity a, Handler handler, float density) {
        this.handler = handler;
        this.density = density;
        block = a.findViewById(R.id.welcome);
        label = a.findViewById(R.id.welcomeLabel);
        name = a.findViewById(R.id.welcomeName);
        accent = a.findViewById(R.id.welcomeAccent);
        weatherRow = a.findViewById(R.id.welcomeWeather);
        weatherIcon = a.findViewById(R.id.weatherIcon);
        weatherTemp = a.findViewById(R.id.weatherTemp);
        weatherText = a.findViewById(R.id.weatherText);
        enabled = name.textWidth() > 0f;
        if (!enabled) {
            block.setVisibility(View.GONE);
            return;
        }
        // The name view keeps room around its text for the pen glow; cancel it with negative
        // margins so the text itself lines up with the Settings pill and sits 8dp under WELCOME.
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) name.getLayoutParams();
        int p = (int) name.pad;
        lp.topMargin = (int) (8 * density) - p;
        lp.bottomMargin = -p;
        lp.setMarginEnd(-p);
        name.setLayoutParams(lp);
    }

    /** Called when Home comes back to the front: hide everything, the next show() plays the intro. */
    void reset() {
        stop();
        shown = false;
        introPlayed = false;
        block.setAlpha(0f);
    }

    /** Pause everything while another app is in front. */
    void stop() {
        handler.removeCallbacks(shineLoop);
        if (intro != null) intro.end();
        if (shineSet != null) shineSet.end();
        block.animate().cancel();
    }

    /** Centre the block in the free space between the top buttons and the dock, then reveal it. */
    void show(float topLimit, float bottomLimit) {
        if (!enabled || shown) return;
        shown = true;
        float h = block.getHeight();
        float y = Math.max(topLimit, topLimit + (bottomLimit - topLimit - h) / 2f);
        block.animate().cancel();
        block.setTranslationY(0f);
        block.setY(y);
        handler.removeCallbacks(shineLoop);

        if (!introPlayed) {
            introPlayed = true;
            block.setAlpha(1f);
            playIntro();
            handler.postDelayed(shineLoop, 5200);
        } else {
            float rise = 10f * density;
            block.setY(y + rise);
            block.animate().alpha(1f).translationYBy(-rise).setDuration(ms(280))
                    .setInterpolator(new DecelerateInterpolator(2f)).start();
            handler.postDelayed(shineLoop, 1800);
        }
    }

    void hide() {
        if (!shown) return;
        shown = false;
        handler.removeCallbacks(shineLoop);
        if (intro != null) intro.end();
        if (shineSet != null) shineSet.end();
        block.animate().cancel();
        block.animate().alpha(0f).setDuration(ms(160)).start();
    }

    /** Follow the ambient colour of the focused dock app: pen, glow, shine and accent line. */
    void setTint(int color) {
        if (!enabled) return;
        int target = lighten(color | 0xFF000000, 0.2f);
        name.setTint(color);
        if (tintAnim != null) tintAnim.cancel();
        if (!shown) {
            accent.setColor(target);
            return;
        }
        tintAnim = ValueAnimator.ofObject(new ArgbEvaluator(), accent.getColor(), target);
        tintAnim.setDuration(ms(450));
        tintAnim.addUpdateListener(an -> accent.setColor((int) an.getAnimatedValue()));
        tintAnim.start();
    }

    /** Fill in the weather row; it stays hidden until there is a reading. */
    void setWeather(Weather.Result r) {
        if (!enabled || r == null) return;
        weatherIcon.set(WeatherIconView.kindFor(r.code), r.day);
        weatherTemp.setText(r.temp + "°");
        String text = r.describe();
        if (r.place != null && !r.place.isEmpty()) text += "  ·  " + r.place;
        weatherText.setText(text);
        if (weatherRow.getVisibility() != View.VISIBLE) {
            weatherRow.setVisibility(View.VISIBLE);
            if (shown && intro == null) {
                weatherRow.setAlpha(0f);
                weatherRow.animate().alpha(1f).setDuration(ms(500)).start();
            }
        }
    }

    // ---- choreography ----------------------------------------------------------------------------

    private void playIntro() {
        if (intro != null) intro.end();

        // Start state.
        label.setAlpha(0f);
        label.setLetterSpacing(0.9f);
        name.setIntro(0f, 0f, 1f);
        name.setShine(-1f);
        accent.setGrow(0f);
        accent.setSpark(-1f);
        weatherRow.setAlpha(0f);

        // WELCOME: letters drift together from wide tracking while fading in.
        ValueAnimator labelIn = ValueAnimator.ofFloat(0f, 1f);
        labelIn.setDuration(ms(900));
        labelIn.setInterpolator(new DecelerateInterpolator(2.2f));
        labelIn.addUpdateListener(an -> {
            float t = (float) an.getAnimatedValue();
            label.setAlpha(t);
            label.setLetterSpacing(0.9f + (LABEL_SPACING - 0.9f) * t);
        });

        // The name writes itself, then fills in while the pen line fades.
        ValueAnimator write = ValueAnimator.ofFloat(0f, 1f);
        write.setStartDelay(ms(250));
        write.setDuration(ms(2300));
        write.setInterpolator(new LinearInterpolator());
        write.addUpdateListener(an -> name.setWrite((float) an.getAnimatedValue()));

        // Accent line grows from the right, then a spark runs along it.
        ValueAnimator grow = ValueAnimator.ofFloat(0f, 1f);
        grow.setStartDelay(ms(1700));
        grow.setDuration(ms(700));
        grow.setInterpolator(new DecelerateInterpolator(2.5f));
        grow.addUpdateListener(an -> accent.setGrow((float) an.getAnimatedValue()));

        // Weather rises in last, once the name has settled.
        float lift = 8f * density;
        ValueAnimator weatherIn = ValueAnimator.ofFloat(0f, 1f);
        weatherIn.setStartDelay(ms(2500));
        weatherIn.setDuration(ms(650));
        weatherIn.setInterpolator(new DecelerateInterpolator(2f));
        weatherIn.addUpdateListener(an -> {
            float t = (float) an.getAnimatedValue();
            weatherRow.setAlpha(t);
            weatherRow.setTranslationY(lift * (1f - t));
        });

        AnimatorSet set = new AnimatorSet();
        set.playTogether(labelIn, write, grow, sparkAnim(2350), weatherIn);
        set.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                // Snap to the resting state (also when ended early by hide or stop).
                label.setAlpha(1f);
                label.setLetterSpacing(LABEL_SPACING);
                name.setIntro(1f, 1f, 0f);
                accent.setGrow(1f);
                accent.setSpark(-1f);
                weatherRow.setAlpha(1f);
                weatherRow.setTranslationY(0f);
                if (intro == animation) intro = null;
            }
        });
        intro = set;
        set.start();
    }

    private void runShine() {
        if (!shown || intro != null) return;
        if (shineSet != null) shineSet.end();
        ValueAnimator sweep = ValueAnimator.ofFloat(0f, 1f);
        sweep.setDuration(ms(1500));
        sweep.setInterpolator(new PathInterpolator(0.4f, 0f, 0.2f, 1f));
        sweep.addUpdateListener(an -> name.setShine((float) an.getAnimatedValue()));
        AnimatorSet set = new AnimatorSet();
        set.playTogether(sweep, sparkAnim(900));
        set.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                name.setShine(-1f);
                accent.setSpark(-1f);
                if (shineSet == animation) shineSet = null;
            }
        });
        shineSet = set;
        set.start();
    }

    private ValueAnimator sparkAnim(long delay) {
        ValueAnimator spark = ValueAnimator.ofFloat(0f, 1f);
        spark.setStartDelay(ms(delay));
        spark.setDuration(ms(750));
        spark.setInterpolator(new PathInterpolator(0.3f, 0f, 0.3f, 1f));
        spark.addUpdateListener(an -> accent.setSpark((float) an.getAnimatedValue()));
        return spark;
    }

    /**
     * Durations in real milliseconds. Xiaomi ships this TV with animator_duration_scale 0.6, which
     * would rush the handwriting; divide it back out so the intro keeps its pace. With animations
     * switched off (scale 0) everything still ends at once, which is what that setting asks for.
     */
    static long ms(long real) {
        float scale = ValueAnimator.getDurationScale();
        return scale > 0f ? (long) (real / scale) : real;
    }

    private static float clamp(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }

    /** Mix a colour towards white; amount 1 = pure white. */
    static int lighten(int c, float amount) {
        int r = Color.red(c), g = Color.green(c), b = Color.blue(c);
        r += (int) ((255 - r) * amount);
        g += (int) ((255 - g) * amount);
        b += (int) ((255 - b) * amount);
        return Color.rgb(r, g, b);
    }
}

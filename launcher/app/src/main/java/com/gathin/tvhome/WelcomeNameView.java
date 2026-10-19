package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * The welcome name, drawn by hand so it can write itself: a thin glowing pen traces every letter's
 * outline from left to right, then the letters fill in while the pen line fades. Afterwards a soft
 * shine in the focused app's colour can sweep across it. All state is driven from {@link Welcome}.
 */
public class WelcomeNameView extends View {
    static final int BASE = 0xFFDADADA;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pen = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<Path> contours = new ArrayList<>();
    private final List<Float> lengths = new ArrayList<>();
    private final PathMeasure measure = new PathMeasure();
    private final Path segment = new Path();
    private final float[] pos = new float[2];
    private final Matrix matrix = new Matrix();
    private final float density;
    final float pad;   // room around the text for the pen glow; Welcome cancels it with negative margins

    private String text = "";
    private float total;
    private float baseline;
    private int tint = Color.WHITE;

    private float trace = 1f;      // 0..1 share of the outline drawn
    private float fillAlpha = 1f;  // 0..1
    private float strokeAlpha = 0f;
    private float shine = -1f;     // 0..1 while a shine sweeps, -1 otherwise
    private LinearGradient shineGradient;
    private RadialGradient penGradient;
    private float penRadius;

    public WelcomeNameView(Context c, AttributeSet a) {
        super(c, a);
        density = getResources().getDisplayMetrics().density;
        pad = 10f * density;
        penRadius = 9f * density;
        fill.setTypeface(getResources().getFont(R.font.outfit));
        fill.setFontVariationSettings("'wght' 250");
        fill.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 56f, getResources().getDisplayMetrics()));
        fill.setLetterSpacing(-0.02f);
        fill.setColor(BASE);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(1.4f * density);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        setText(c.getString(R.string.welcome_name).trim());
        setTint(Color.WHITE);
    }

    void setText(String t) {
        text = t;
        Paint.FontMetrics fm = fill.getFontMetrics();
        baseline = pad - fm.ascent;
        Path all = new Path();
        fill.getTextPath(text, 0, text.length(), pad, baseline, all);
        contours.clear();
        lengths.clear();
        total = 0f;
        measure.setPath(all, false);
        do {
            float len = measure.getLength();
            if (len <= 0f) continue;
            Path p = new Path();
            measure.getSegment(0f, len, p, true);
            contours.add(p);
            lengths.add(len);
            total += len;
        } while (measure.nextContour());
        requestLayout();
        invalidate();
    }

    void setTint(int color) {
        tint = color | 0xFF000000;
        stroke.setColor(Welcome.lighten(tint, 0.55f));
        int head = Welcome.lighten(tint, 0.9f);
        penGradient = new RadialGradient(0f, 0f, penRadius,
                new int[]{head, (Welcome.lighten(tint, 0.4f) & 0x00FFFFFF) | 0x66000000, 0x00000000 | (tint & 0x00FFFFFF)},
                new float[]{0f, 0.35f, 1f}, Shader.TileMode.CLAMP);
        pen.setShader(penGradient);
        shineGradient = null;
        invalidate();
    }

    /** Intro state: trace 0..1, fill 0..1, stroke line opacity 0..1. */
    void setIntro(float trace, float fillAlpha, float strokeAlpha) {
        this.trace = trace;
        this.fillAlpha = fillAlpha;
        this.strokeAlpha = strokeAlpha;
        invalidate();
    }

    void setShine(float s) {
        shine = s;
        invalidate();
    }

    float textWidth() {
        return fill.measureText(text);
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        Paint.FontMetrics fm = fill.getFontMetrics();
        int w = (int) Math.ceil(fill.measureText(text) + 2 * pad);
        int h = (int) Math.ceil(fm.descent - fm.ascent + 2 * pad);
        setMeasuredDimension(text.isEmpty() ? 0 : w, text.isEmpty() ? 0 : h);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (text.isEmpty()) return;

        // Fill, optionally with the shine band sliding through it.
        if (fillAlpha > 0f) {
            if (shine >= 0f) {
                float w = textWidth();
                float band = w * 0.55f;
                if (shineGradient == null) {
                    int hi = Welcome.lighten(tint, 0.8f);
                    shineGradient = new LinearGradient(0f, 0f, band, 0f,
                            new int[]{BASE, BASE, hi, Color.WHITE, hi, BASE, BASE},
                            new float[]{0f, 0.28f, 0.44f, 0.5f, 0.56f, 0.72f, 1f}, Shader.TileMode.CLAMP);
                }
                matrix.setTranslate(pad - band + (w + band) * shine, 0f);
                shineGradient.setLocalMatrix(matrix);
                fill.setShader(shineGradient);
            } else {
                fill.setShader(null);
            }
            fill.setAlpha((int) (255 * fillAlpha));
            canvas.drawText(text, pad, baseline, fill);
        }

        // The pen: outlines drawn letter by letter up to `trace`, with a glowing head.
        if (strokeAlpha > 0f && trace > 0f) {
            stroke.setAlpha((int) (255 * strokeAlpha));
            float target = trace * total;
            boolean head = false;
            for (int i = 0; i < contours.size() && target > 0f; i++) {
                float len = lengths.get(i);
                if (target >= len) {
                    canvas.drawPath(contours.get(i), stroke);
                    target -= len;
                } else {
                    measure.setPath(contours.get(i), false);
                    segment.reset();
                    measure.getSegment(0f, target, segment, true);
                    canvas.drawPath(segment, stroke);
                    measure.getPosTan(target, pos, null);
                    head = true;
                    target = 0f;
                }
            }
            if (head && trace < 1f) {
                matrix.setTranslate(pos[0], pos[1]);
                penGradient.setLocalMatrix(matrix);
                pen.setAlpha((int) (255 * strokeAlpha));
                canvas.drawCircle(pos[0], pos[1], penRadius, pen);
            }
        }
    }
}

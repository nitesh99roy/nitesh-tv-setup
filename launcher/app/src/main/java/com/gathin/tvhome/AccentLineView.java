package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

/** The short line under the welcome name: grows from the right edge, and can carry a spark of light. */
public class AccentLineView extends View {
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint spark = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Matrix matrix = new Matrix();
    private LinearGradient sparkGradient;
    private float grow = 1f;    // 0..1 of the full width, anchored right
    private float sparkPos = -1f;

    public AccentLineView(Context c, AttributeSet a) {
        super(c, a);
        line.setColor(Color.WHITE);
    }

    void setColor(int color) {
        line.setColor(color);
        invalidate();
    }

    int getColor() {
        return line.getColor();
    }

    void setGrow(float g) {
        grow = g;
        invalidate();
    }

    void setSpark(float s) {
        sparkPos = s;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth(), h = getHeight();
        if (grow <= 0f || w <= 0f) return;
        rect.set(w - w * grow, 0f, w, h);
        canvas.drawRoundRect(rect, h / 2f, h / 2f, line);
        if (sparkPos >= 0f && sparkPos <= 1f) {
            float band = w * 0.5f;
            if (sparkGradient == null) {
                sparkGradient = new LinearGradient(0f, 0f, band, 0f,
                        new int[]{0x00FFFFFF, 0xFFFFFFFF, 0x00FFFFFF}, null, Shader.TileMode.CLAMP);
                spark.setShader(sparkGradient);
            }
            // Runs right-to-left, the way the line grew.
            matrix.setTranslate(w - (w + band) * sparkPos, 0f);
            sparkGradient.setLocalMatrix(matrix);
            canvas.drawRoundRect(rect, h / 2f, h / 2f, spark);
        }
    }
}

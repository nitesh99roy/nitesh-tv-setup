package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

/**
 * A thin outline weather glyph drawn in code, to match the launcher's light type: sun, moon, cloud,
 * rain, drizzle, snow, storm and fog. Drawn on a 24x24 grid scaled to the view.
 */
public class WeatherIconView extends View {
    static final int CLEAR = 0, PARTLY = 1, CLOUD = 2, FOG = 3, DRIZZLE = 4, RAIN = 5, SNOW = 6, STORM = 7;

    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path cloud = new Path();
    private final Path tmp = new Path();
    private int kind = CLEAR;
    private boolean day = true;

    public WeatherIconView(Context c, AttributeSet a) {
        super(c, a);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeCap(Paint.Cap.ROUND);
        line.setStrokeJoin(Paint.Join.ROUND);
        line.setColor(0xD9FFFFFF);
        dot.setColor(0xD9FFFFFF);
    }

    void set(int kind, boolean day) {
        this.kind = kind;
        this.day = day;
        invalidate();
    }

    /** WMO weather code (as used by Open-Meteo) to a glyph. */
    static int kindFor(int code) {
        if (code == 0) return CLEAR;
        if (code == 1 || code == 2) return PARTLY;
        if (code == 3) return CLOUD;
        if (code == 45 || code == 48) return FOG;
        if (code >= 51 && code <= 57) return DRIZZLE;
        if ((code >= 61 && code <= 67) || (code >= 80 && code <= 82)) return RAIN;
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) return SNOW;
        if (code >= 95) return STORM;
        return CLOUD;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float s = Math.min(getWidth(), getHeight()) / 24f;
        if (s <= 0f) return;
        canvas.save();
        canvas.scale(s, s);
        line.setStrokeWidth(1.5f);
        switch (kind) {
            case CLEAR:
                if (day) sun(canvas, 12f, 12f, 4.2f, true);
                else moon(canvas, 12f, 12f, 7f);
                break;
            case PARTLY:
                buildCloud(1.5f, 3f);
                canvas.save();
                canvas.clipOutPath(cloud);
                if (day) sun(canvas, 9f, 9f, 3.2f, true);
                else moon(canvas, 9f, 8.5f, 5f);
                canvas.restore();
                canvas.drawPath(cloud, line);
                break;
            case CLOUD:
                buildCloud(0f, 1f);
                canvas.drawPath(cloud, line);
                break;
            case FOG:
                canvas.drawLine(4f, 8f, 20f, 8f, line);
                canvas.drawLine(2.5f, 12f, 17.5f, 12f, line);
                canvas.drawLine(6f, 16f, 21.5f, 16f, line);
                canvas.drawLine(4f, 20f, 15f, 20f, line);
                break;
            case DRIZZLE:
            case RAIN:
                buildCloud(0f, -2.5f);
                canvas.drawPath(cloud, line);
                int drops = kind == RAIN ? 3 : 2;
                float len = kind == RAIN ? 3.4f : 1.6f;
                for (int i = 0; i < drops; i++) {
                    float x = (drops == 3 ? 8.5f + i * 4f : 10f + i * 4f);
                    canvas.drawLine(x, 18.5f, x - 1.1f, 18.5f + len, line);
                }
                break;
            case SNOW:
                buildCloud(0f, -2.5f);
                canvas.drawPath(cloud, line);
                for (int i = 0; i < 3; i++) {
                    canvas.drawCircle(8.5f + i * 4f, 19.8f + (i % 2) * 2f, 0.95f, dot);
                }
                break;
            case STORM:
                buildCloud(0f, -2.5f);
                canvas.drawPath(cloud, line);
                tmp.reset();
                tmp.moveTo(13.4f, 17.5f);
                tmp.lineTo(10.6f, 20.8f);
                tmp.lineTo(13.6f, 20.8f);
                tmp.lineTo(11.2f, 23.6f);
                canvas.drawPath(tmp, line);
                break;
            default:
                break;
        }
        canvas.restore();
    }

    private void sun(Canvas c, float cx, float cy, float r, boolean rays) {
        c.drawCircle(cx, cy, r, line);
        if (!rays) return;
        for (int i = 0; i < 8; i++) {
            double a = Math.PI / 4 * i;
            float cos = (float) Math.cos(a), sin = (float) Math.sin(a);
            c.drawLine(cx + cos * (r + 2f), cy + sin * (r + 2f), cx + cos * (r + 3.6f), cy + sin * (r + 3.6f), line);
        }
    }

    private void moon(Canvas c, float cx, float cy, float r) {
        Path body = new Path();
        body.addCircle(cx, cy, r, Path.Direction.CW);
        Path bite = new Path();
        bite.addCircle(cx + r * 0.55f, cy - r * 0.42f, r * 0.85f, Path.Direction.CW);
        body.op(bite, Path.Op.DIFFERENCE);
        c.drawPath(body, line);
    }

    /** A soft three-lobed cloud with a flat base, offset by (dx, dy) on the 24 grid. */
    private void buildCloud(float dx, float dy) {
        cloud.reset();
        tmp.reset();
        cloud.addCircle(8f + dx, 14f + dy, 4f, Path.Direction.CW);
        tmp.addCircle(13f + dx, 10.8f + dy, 5.2f, Path.Direction.CW);
        cloud.op(tmp, Path.Op.UNION);
        tmp.reset();
        tmp.addCircle(17.6f + dx, 14.6f + dy, 3.4f, Path.Direction.CW);
        cloud.op(tmp, Path.Op.UNION);
        tmp.reset();
        tmp.addRoundRect(8f + dx, 12f + dy, 17.6f + dx, 18f + dy, 0.5f, 0.5f, Path.Direction.CW);
        cloud.op(tmp, Path.Op.UNION);
        tmp.reset();
    }
}

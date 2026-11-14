package ua.pp.poe.schedule;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

/** Шкала під нижньою смугою: цифри кожної години + рисочки на півгодини. */
public class AxisView extends View {

    private static final int LABEL = Color.parseColor("#C7CFDB");
    private static final int TICK = Color.parseColor("#4A5563");

    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tick = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float d;

    public AxisView(Context ctx) {
        super(ctx);
        d = getResources().getDisplayMetrics().density;
        text.setColor(LABEL);
        text.setTextSize(15 * d);
        text.setFakeBoldText(true);
        tick.setColor(TICK);
        tick.setStrokeWidth(1.5f * d);
    }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        float w = getWidth() / 48f;
        float h = getHeight();
        for (int hour = 0; hour < 24; hour++) {
            float x = hour * 2 * w;
            c.drawLine(x, 0, x, 7 * d, tick);              // рисочка години
            c.drawText(String.format("%02d", hour), x + 2 * d, h - 3 * d, text);
            c.drawLine(x + w, 0, x + w, 4 * d, tick);      // півгодини
        }
    }
}

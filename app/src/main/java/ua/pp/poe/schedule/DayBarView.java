package ua.pp.poe.schedule;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

import java.util.List;

/** Смуга доби: 48 півгодинних клітинок, розтягнута на всю ширину екрана. */
public class DayBarView extends View {

    private static final int ON = Color.parseColor("#2E7D4F");
    private static final int OFF = Color.parseColor("#B3403A");
    private static final int SWITCH = Color.parseColor("#C99A2E");
    private static final int EMPTY = Color.parseColor("#2A2F36");

    private final List<Integer> cells;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tick = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();

    public DayBarView(Context ctx, List<Integer> cells) {
        super(ctx);
        this.cells = cells;
        tick.setColor(Color.parseColor("#121417"));
        tick.setStrokeWidth(Math.max(1f, getResources().getDisplayMetrics().density));
    }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        int n = cells.size() > 0 ? cells.size() : 48;
        float w = getWidth() / (float) n;
        float h = getHeight();
        for (int i = 0; i < n; i++) {
            int v = i < cells.size() ? cells.get(i) : 0;
            fill.setColor(v == 1 ? ON : v == 2 ? OFF : v == 3 ? SWITCH : EMPTY);
            r.set(i * w, 0, (i + 1) * w, h);
            c.drawRect(r, fill);
            if (i % 6 == 0 && i > 0) {          // кожні 3 години — тонка межа
                c.drawLine(i * w, 0, i * w, h, tick);
            }
        }
    }
}

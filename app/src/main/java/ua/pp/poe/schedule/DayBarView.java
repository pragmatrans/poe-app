package ua.pp.poe.schedule;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

import java.util.Calendar;
import java.util.List;

/**
 * Смуга доби: 48 півгодинних клітинок + підписи кожної години + лінія «зараз».
 * Розміри підібрані під поганий зір: високі клітинки, великі цифри, контраст.
 */
public class DayBarView extends View {

    private static final int ON = Color.parseColor("#2FA360");
    private static final int OFF = Color.parseColor("#D24B45");
    private static final int SWITCH = Color.parseColor("#E0A32E");
    private static final int EMPTY = Color.parseColor("#2A2F36");
    private static final int GRID = Color.parseColor("#121417");
    private static final int LABEL = Color.parseColor("#C7CFDB");
    private static final int NOW = Color.parseColor("#FFFFFF");

    private final List<Integer> cells;
    private final boolean today;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint grid = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint nowLine = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final float d;

    public DayBarView(Context ctx, List<Integer> cells, boolean today) {
        super(ctx);
        this.cells = cells;
        this.today = today;
        d = getResources().getDisplayMetrics().density;
        grid.setColor(GRID);
        grid.setStrokeWidth(2f * d);
        text.setColor(LABEL);
        text.setTextSize(13 * d);
        text.setTextAlign(Paint.Align.LEFT);
        text.setFakeBoldText(true);
        nowLine.setColor(NOW);
        nowLine.setStrokeWidth(2.5f * d);
    }

    /** Висота смуги: 66dp клітинки + 20dp на підписи годин. */
    public static int heightDp() {
        return 86;
    }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        int n = cells.isEmpty() ? 48 : cells.size();
        float w = getWidth() / (float) n;
        float labelsH = 20 * d;
        float h = getHeight() - labelsH;

        for (int i = 0; i < n; i++) {
            int v = i < cells.size() ? cells.get(i) : 0;
            fill.setColor(v == 1 ? ON : v == 2 ? OFF : v == 3 ? SWITCH : EMPTY);
            r.set(i * w, 0, (i + 1) * w + 0.5f, h);
            c.drawRect(r, fill);
        }
        // кожна година: межа + підпис
        for (int hour = 0; hour <= 24; hour++) {
            float x = hour * 2 * w;
            c.drawLine(x, 0, x, h, grid);
            if (hour < 24) {
                c.drawText(String.format("%02d", hour), x + 2 * d, getHeight() - 4 * d, text);
            }
        }
        // «зараз» — біла риска
        if (today) {
            Calendar cal = Calendar.getInstance();
            float mins = cal.get(Calendar.HOUR_OF_DAY) * 60f + cal.get(Calendar.MINUTE);
            float x = (mins / 30f) * w;
            c.drawLine(x, 0, x, h, nowLine);
        }
    }
}

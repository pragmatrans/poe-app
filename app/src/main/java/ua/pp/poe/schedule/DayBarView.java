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
 * Одна смуга доби: 48 півгодинних клітинок на всю ширину, без підписів під нею
 * (шкала годин малюється один раз під нижньою смугою).
 * Сітка: товста межа на кожну годину, тонка риска на кожні 30 хвилин.
 * Біла вертикаль — поточний час (тільки для сьогодні).
 */
public class DayBarView extends View {

    private static final int ON = Color.parseColor("#2FA360");
    private static final int OFF = Color.parseColor("#D24B45");
    private static final int SWITCH = Color.parseColor("#E0A32E");
    private static final int EMPTY = Color.parseColor("#2A2F36");
    private static final int HOUR_LINE = Color.parseColor("#0B0D10");
    private static final int HALF_LINE = Color.parseColor("#20262E");
    private static final int NOW = Color.parseColor("#FFFFFF");

    private final List<Integer> cells;
    private final boolean today;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hourLine = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint halfLine = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint nowLine = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final float d;

    public DayBarView(Context ctx, List<Integer> cells, boolean today) {
        super(ctx);
        this.cells = cells;
        this.today = today;
        d = getResources().getDisplayMetrics().density;
        hourLine.setColor(HOUR_LINE);
        hourLine.setStrokeWidth(2.5f * d);
        halfLine.setColor(HALF_LINE);
        halfLine.setStrokeWidth(1f * d);
        nowLine.setColor(NOW);
        nowLine.setStrokeWidth(2.5f * d);
    }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        int n = cells.isEmpty() ? 48 : cells.size();
        float w = getWidth() / (float) n;
        float h = getHeight();

        for (int i = 0; i < n; i++) {
            int v = i < cells.size() ? cells.get(i) : 0;
            fill.setColor(v == 1 ? ON : v == 2 ? OFF : v == 3 ? SWITCH : EMPTY);
            r.set(i * w, 0, (i + 1) * w + 0.5f, h);
            c.drawRect(r, fill);
        }
        // півгодини: коротка риска посередині висоти — щоб «півклітинки» було видно
        for (int i = 1; i < n; i += 2) {
            float x = i * w;
            c.drawLine(x, h * 0.22f, x, h * 0.78f, halfLine);
        }
        // години: межа на всю висоту
        for (int hour = 0; hour <= 24; hour++) {
            float x = hour * 2 * w;
            c.drawLine(x, 0, x, h, hourLine);
        }
        if (today) {
            Calendar cal = Calendar.getInstance();
            float mins = cal.get(Calendar.HOUR_OF_DAY) * 60f + cal.get(Calendar.MINUTE);
            float x = (mins / 30f) * w;
            c.drawLine(x, 0, x, h, nowLine);
        }
    }
}

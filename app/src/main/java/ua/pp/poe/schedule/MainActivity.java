package ua.pp.poe.schedule;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/**
 * Графік відключень poe.pl.ua, перемальований під екран планшета.
 *
 * Дані — не з сайту (він ріже не-українські IP і домашню адресу), а з JSON, який віддає наш
 * вартовий на UA VPS: спершу через WireGuard (свіжі), запасним шляхом — опублікований у хмарі.
 *
 * Екран: заголовок з датою і часом даних, далі по одній смузі на чергу — підпис черги
 * всередині перших клітинок, шкала годин одна під нижньою смугою. Все влізає без прокрутки.
 */
public class MainActivity extends Activity {

    private static final String URL_JSON =
            "https://raw.githubusercontent.com/pragmatrans/poe-data/main/schedule.json";
    private static final long REFRESH_MS = 15 * 60 * 1000L;


    private SwipeRefreshLayout swipe;
    private LinearLayout root;      // заголовок
    private LinearLayout body;      // смуги + шкала
    private TextView status;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Runnable refresher = new Runnable() {
        @Override public void run() {
            load();
            handler.postDelayed(this, REFRESH_MS);
        }
    };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);

        swipe = new SwipeRefreshLayout(this);
        swipe.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override public void onRefresh() { load(); }
        });

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.parseColor("#121417"));
        scroll.setFillViewport(true);

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(12);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        swipe.addView(scroll);
        setContentView(swipe);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        status = new TextView(this);
        status.setTextColor(Color.parseColor("#E8ECF1"));
        status.setTextSize(19);
        status.setTypeface(Typeface.DEFAULT_BOLD);
        status.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { load(); }
        });
        top.addView(status, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        root.addView(top);

        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.topMargin = dp(6);
        root.addView(body, blp);

        load();
        handler.postDelayed(refresher, REFRESH_MS);
    }

    @Override protected void onDestroy() {
        handler.removeCallbacks(refresher);
        super.onDestroy();
    }

    /** Ходить за готовим JSON і перемальовує. */
    private void load() {
        new Thread(new Runnable() {
            @Override public void run() {
                String err = null;
                String bodyText = null;
                String source = null;
                if (bodyText == null) {
                    try {
                        bodyText = get(URL_JSON, 15000);   // 2) запасний шлях — хмара
                        source = "хмара";
                    } catch (Exception e) {
                        err = e.getClass().getSimpleName();
                    }
                }
                final String fErr = err;
                final String fBody = bodyText;
                final String fSrc = source;
                handler.post(new Runnable() {
                    @Override public void run() {
                        if (fErr != null) showError(fErr);
                        else if (!render(fBody, fSrc)) showError("JSON не розібрався");
                    }
                });
            }
        }).start();
    }

    /** Один GET із таймаутом; кидає виняток, якщо не 200. */
    private String get(String url, int timeoutMs) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(timeoutMs);
        c.setReadTimeout(timeoutMs + 8000);
        c.setRequestProperty("Cache-Control", "no-cache");
        BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = r.readLine()) != null) sb.append(line).append('\n');
        r.close();
        if (c.getResponseCode() != 200) throw new Exception("HTTP " + c.getResponseCode());
        return sb.toString();
    }

    private void stopSpinner() {
        if (swipe != null && swipe.isRefreshing()) swipe.setRefreshing(false);
    }

    private void showError(String msg) {
        stopSpinner();
        status.setTextColor(Color.parseColor("#FF8A80"));
        status.setText("немає даних · " + msg);
    }

    private boolean render(String json, String source) {
        try {
            JSONObject data = new JSONObject(json);
            String updated = data.optString("updated", "?");
            List<String> dates = pickDates(data);

            stopSpinner();
            status.setTextColor(Color.parseColor("#E8ECF1"));
            String head = humanDayMonth(dates.get(0));
            if (dates.size() > 1) head += " + " + humanDayMonth(dates.get(1));
            status.setText(head + "  ·  дані " + clock(updated)
                    + (source == null ? "" : "  ·  " + source));

            body.removeAllViews();

            JSONObject queues = data.optJSONObject("queues");
            if (queues == null) return false;
            int multiCount = dates.size();
            int rows = queues.length() * Math.max(1, multiCount);
            int barH = barHeight(rows, multiCount);

            boolean multi = dates.size() > 1;
            for (String date : dates) {
                if (multi) body.addView(dayLabel(date));
                boolean first = true;
                Iterator<String> it = queues.keys();
                while (it.hasNext()) {
                    String q = it.next();
                    JSONObject days = queues.getJSONObject(q).optJSONObject("days");
                    if (days == null || !days.has(date)) continue;
                    List<Integer> cells = new ArrayList<>();
                    JSONArray arr = days.getJSONObject(date).optJSONArray("cells");
                    if (arr != null) for (int i = 0; i < arr.length(); i++) cells.add(arr.optInt(i));
                    body.addView(row(q, cells, date, first, barH));
                    first = false;
                }
                body.addView(axis());     // своя шкала під кожною групою днів
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Рядок: смуга на всю ширину, підпис черги намальований усередині. */
    private View row(String q, List<Integer> cells, String date, boolean first, int barH) {
        DayBarView bar = new DayBarView(this, cells, isToday(date), "Ч-" + q);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, barH);
        lp.topMargin = first ? 0 : dp(8);
        bar.setLayoutParams(lp);
        return bar;
    }

    /** Шкала: підписи годин і рисочки, з відступом під колонку черг. */
    private View axis() {
        AxisView a = new AxisView(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(30));
        lp.topMargin = dp(2);
        a.setLayoutParams(lp);
        return a;
    }

    /** Дати для показу: сьогодні+завтра, якщо є; інакше дві найновіші. Максимум дві. */
    private List<String> pickDates(JSONObject data) {
        String today = new SimpleDateFormat("dd-MM-yyyy", Locale.US).format(new Date());
        List<String> seen = new ArrayList<>();
        JSONObject queues = data.optJSONObject("queues");
        if (queues != null) {
            Iterator<String> q = queues.keys();
            while (q.hasNext()) {
                JSONObject days = queues.optJSONObject(q.next()).optJSONObject("days");
                if (days == null) continue;
                Iterator<String> d = days.keys();
                while (d.hasNext()) {
                    String key = d.next();
                    if (!seen.contains(key)) seen.add(key);
                }
            }
        }
        Collections.sort(seen);
        List<String> out = new ArrayList<>();
        if (seen.contains(today)) out.add(today);
        for (String d : seen) if (!d.equals(today)) out.add(d);
        if (out.isEmpty()) out.add(today);
        return out.size() > 2 ? out.subList(0, 2) : out;
    }

    /** Підпис групи днів, коли показуємо два дні. */
    private View dayLabel(String date) {
        TextView v = new TextView(this);
        v.setText(humanDayMonth(date));
        v.setTextSize(13);
        v.setTextColor(Color.parseColor("#8A94A6"));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(6);
        v.setLayoutParams(lp);
        return v;
    }

    /** "14 листопада 2026 13:17" → "14 листопада"; невідомий формат — як є. */
    private String humanDayMonth(String day) {
        String[] p = day.split("-");
        String[] mm = {"", "січня", "лютого", "березня", "квітня", "травня", "червня",
                "липня", "серпня", "вересня", "жовтня", "листопада", "грудня"};
        try {
            return Integer.parseInt(p[0]) + " " + mm[Integer.parseInt(p[1])];
        } catch (Exception e) {
            return day;
        }
    }

    /** З "14 листопада 2026 13:17" витягує "13:17". */
    private String clock(String s) {
        int i = s.lastIndexOf(' ');
        return i > 0 ? s.substring(i + 1) : s;
    }

    private boolean isToday(String day) {
        String t = new SimpleDateFormat("dd-MM-yyyy", Locale.US).format(new Date());
        return t.equals(day);
    }


    /** Висота смуги: ділимо доступну висоту екрана на кількість черг (не менше 92dp). */
    private int barHeight(int n, int dayGroups) {
        int screen = getResources().getDisplayMetrics().heightPixels;
        int fixed = dp(24 + 46 + 12) + dp(38) * dayGroups + dp(26) * Math.max(0, dayGroups - 1);
        int gaps = dp(8) * Math.max(0, n - 1);
        int h = (screen - fixed - gaps) / Math.max(1, n);
        return Math.max(n > 4 ? dp(52) : dp(84), h);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}

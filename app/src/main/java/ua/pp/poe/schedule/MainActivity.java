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

    private static final String URL_WG =
            "http://10.66.66.1:8099/schedule.json";          // свіжі дані через WireGuard
    private static final String URL_JSON =
            "https://raw.githubusercontent.com/pragmatrans/poe-data/main/schedule.json";
    private static final long REFRESH_MS = 15 * 60 * 1000L;
    private static final int TAG_W_DP = 92;

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

        status = new TextView(this);
        status.setTextColor(Color.parseColor("#E8ECF1"));
        status.setTextSize(19);
        status.setTypeface(Typeface.DEFAULT_BOLD);
        status.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { load(); }
        });
        root.addView(status);

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
                try {
                    bodyText = get(URL_WG, 5000);          // 1) тунель — свіжі дані
                    source = "тунель";
                } catch (Exception e) {
                    bodyText = null;
                }
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
            String date = pickDate(data);

            stopSpinner();
            status.setTextColor(Color.parseColor("#E8ECF1"));
            status.setText(humanDayMonth(date) + "  ·  дані " + clock(updated)
                    + (source == null ? "" : "  ·  " + source));

            body.removeAllViews();

            JSONObject queues = data.optJSONObject("queues");
            if (queues == null) return false;
            Iterator<String> it = queues.keys();
            boolean first = true;
            while (it.hasNext()) {
                String q = it.next();
                JSONObject entry = queues.getJSONObject(q);
                JSONObject days = entry.optJSONObject("days");
                if (days == null || !days.has(date)) continue;

                List<Integer> cells = new ArrayList<>();
                JSONArray arr = days.getJSONObject(date).optJSONArray("cells");
                if (arr != null) for (int i = 0; i < arr.length(); i++) cells.add(arr.optInt(i));

                body.addView(row(q, cells, date, first));
                first = false;
            }
            // шкала годин одна, під останньою смугою
            body.addView(axis());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Рядок: підпис черги в перших клітинках + смуга на решту ширини. */
    private View row(String q, List<Integer> cells, String date, boolean first) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.topMargin = first ? 0 : dp(8);
        row.setLayoutParams(rlp);

        TextView tag = new TextView(this);
        tag.setText("Ч-" + q);
        tag.setTextSize(21);
        tag.setTypeface(Typeface.DEFAULT_BOLD);
        tag.setTextColor(Color.parseColor("#E8ECF1"));
        tag.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        row.addView(tag, new LinearLayout.LayoutParams(dp(TAG_W_DP),
                ViewGroup.LayoutParams.MATCH_PARENT));

        DayBarView bar = new DayBarView(this, cells, isToday(date));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(0, dp(96), 1f);
        row.addView(bar, blp);
        return row;
    }

    /** Шкала: підписи годин і рисочки, з відступом під колонку черг. */
    private View axis() {
        AxisView a = new AxisView(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(26));
        lp.leftMargin = dp(TAG_W_DP);
        lp.topMargin = dp(2);
        a.setLayoutParams(lp);
        return a;
    }

    /** Яку дату показувати: сьогоднішню, інакше найближчу з наявних. */
    private String pickDate(JSONObject data) {
        String today = new SimpleDateFormat("dd-MM-yyyy", Locale.US).format(new Date());
        JSONObject queues = data.optJSONObject("queues");
        List<String> seen = new ArrayList<>();
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
        if (seen.contains(today)) return today;
        return seen.isEmpty() ? today : seen.get(seen.size() - 1);
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

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}

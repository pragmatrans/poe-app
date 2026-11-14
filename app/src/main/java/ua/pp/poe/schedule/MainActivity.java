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
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Графік відключень poe.pl.ua, перемальований під екран планшета.
 *
 * Дані беремо не з сайту (він ріже не-українські IP і домашню адресу), а з JSON, який публікує
 * наш вартовий на UA VPS. Тому ні проксі, ні VPN, ні браузера тут немає.
 */
public class MainActivity extends Activity {

    private static final String URL_WG =
            "http://10.66.66.1:8099/schedule.json";          // свіжі дані через WireGuard
    private static final String URL_JSON =
            "https://raw.githubusercontent.com/pragmatrans/poe-data/main/schedule.json";
    private static final long REFRESH_MS = 15 * 60 * 1000L;

    private SwipeRefreshLayout swipe;
    private LinearLayout root;
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
        int pad = dp(14);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        swipe.addView(scroll);
        setContentView(swipe);

        title("Відключення · poe.pl.ua");
        legend();
        status = new TextView(this);
        status.setTextColor(Color.parseColor("#8A94A6"));
        status.setTextSize(13);
        status.setGravity(Gravity.START);
        root.addView(status);

        load();
        handler.postDelayed(refresher, REFRESH_MS);
    }

    @Override protected void onDestroy() {
        handler.removeCallbacks(refresher);
        super.onDestroy();
    }

    /** Ходить за готовим JSON і перемальовує. */
    private void load() {
        status.setText("оновлюю…");
        status.setOnClickListener(null);
        new Thread(new Runnable() {
            @Override public void run() {
                String err = null;
                String body = null;
                String source = null;
                // 1) тунель — свіжі дані (вартовий фетчить сайт у момент запиту)
                try {
                    body = get(URL_WG, 5000);
                    source = "тунель";
                } catch (Exception e) {
                    body = null;
                }
                // 2) запасний шлях — опублікований JSON (працює без тунеля, але з затримкою)
                if (body == null) {
                    try {
                        body = get(URL_JSON, 15000);
                        source = "хмара";
                    } catch (Exception e) {
                        err = e.getClass().getSimpleName() + ": " + e.getMessage();
                    }
                }
                final String fErr = err;
                final String fBody = body;
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


    /** Легенда кольорів великим шрифтом — щоб не вгадувати відтінки. */
    private void legend() {
        TextView v = new TextView(this);
        String t = "■ є електрика    ■ нема    ■ перемикання";
        android.text.SpannableString sp = new android.text.SpannableString(t);
        sp.setSpan(new android.text.style.ForegroundColorSpan(Color.parseColor("#2FA360")), 0, 1, 0);
        int i2 = t.indexOf('■', 1), i3 = t.indexOf('■', i2 + 1);
        sp.setSpan(new android.text.style.ForegroundColorSpan(Color.parseColor("#D24B45")), i2, i2 + 1, 0);
        sp.setSpan(new android.text.style.ForegroundColorSpan(Color.parseColor("#E0A32E")), i3, i3 + 1, 0);
        v.setText(sp);
        v.setTextSize(15);
        v.setPadding(0, dp(6), 0, dp(2));
        root.addView(v);
    }

    private void stopSpinner() {
        if (swipe != null && swipe.isRefreshing()) swipe.setRefreshing(false);
    }

    /** Чи це сьогоднішня дата (формат dd-MM-yyyy). */
    private boolean isToday(String day) {
        String t = new java.text.SimpleDateFormat("dd-MM-yyyy", java.util.Locale.US)
                .format(new java.util.Date());
        return t.equals(day);
    }

    private void showError(String msg) {
        stopSpinner();
        status.setText("не вдалось оновити — " + msg + "\nторкнись, щоб спробувати ще");
        status.setTextColor(Color.parseColor("#FF8A80"));
        status.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { load(); }
        });
    }

    private boolean render(String body, String source) {
        try {
            JSONObject data = new JSONObject(body);
            String updated = data.optString("updated", "?");
            long age = data.optLong("age_sec", -1);
            String ageTxt = age < 0 ? "" :
                    (age < 90 ? "  ·  дані щойно" : "  ·  дані " + (age / 60) + " хв тому");
            stopSpinner();
            status.setTextColor(Color.parseColor("#8A94A6"));
            status.setText("оновлено: " + updated + ageTxt
                    + "  ·  " + (source == null ? "?" : source)
                    + "  ·  торкнись, щоб оновити");
            status.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { load(); }
            });

            // лишаємо заголовок і статус, решту перемальовуємо
            while (root.getChildCount() > 3) root.removeViewAt(3);  // 0=заголовок, 1=легенда, 2=статус

            JSONObject queues = data.optJSONObject("queues");
            if (queues == null) return false;
            Iterator<String> it = queues.keys();
            while (it.hasNext()) {
                String q = it.next();
                JSONObject entry = queues.getJSONObject(q);
                LinearLayout card = new LinearLayout(MainActivity.this);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setBackgroundColor(Color.parseColor("#1B1F24"));
                card.setPadding(dp(10), dp(10), dp(10), dp(10));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.bottomMargin = dp(12);
                card.setLayoutParams(lp);

                TextView head = new TextView(MainActivity.this);
                head.setTextSize(20);
                head.setTypeface(Typeface.DEFAULT_BOLD);
                head.setTextColor(Color.parseColor("#E8ECF1"));
                String now = entry.optString("now", "");
                head.setText("Черга " + q + (now.isEmpty() ? "" : "   ·   " + now));
                card.addView(head);

                JSONObject days = entry.optJSONObject("days");
                if (days != null) {
                    Iterator<String> dit = days.keys();
                    while (dit.hasNext()) {
                        String day = dit.next();
                        JSONObject d = days.getJSONObject(day);
                        card.addView(dayBlock(day, d));
                    }
                }
                root.addView(card);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Один день: заголовок, смуга 48 півгодин, підпис вікон. */
    private View dayBlock(String day, JSONObject d) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(8);
        box.setLayoutParams(lp);

        TextView label = new TextView(this);
        label.setTextSize(13);
        label.setTextColor(Color.parseColor("#C7CFDB"));
        label.setText(humanDate(day));
        box.addView(label);

        List<Integer> cells = new ArrayList<>();
        JSONArray arr = d.optJSONArray("cells");
        if (arr != null) for (int i = 0; i < arr.length(); i++) cells.add(arr.optInt(i));
        DayBarView bar = new DayBarView(this, cells, isToday(day));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(DayBarView.heightDp()));
        blp.topMargin = dp(4);
        bar.setLayoutParams(blp);
        box.addView(bar);

        StringBuilder sb = new StringBuilder();
        JSONArray off = d.optJSONArray("off");
        if (off != null && off.length() > 0) {
            sb.append("нема: ");
            for (int i = 0; i < off.length(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(off.optString(i));
            }
        } else {
            sb.append("нема: без відключень");
        }
        TextView windows = new TextView(this);
        windows.setTextSize(13);
        windows.setTextColor(Color.parseColor("#8A94A6"));
        windows.setText(sb.toString());
        box.addView(windows);
        return box;
    }

    private String humanDate(String d) {
        String[] p = d.split("-");
        if (p.length != 3) return d;
        String[] mm = {"", "січня", "лютого", "березня", "квітня", "травня", "червня",
                "липня", "серпня", "вересня", "жовтня", "листопада", "грудня"};
        try {
            int m = Integer.parseInt(p[1]);
            return Integer.parseInt(p[0]) + " " + mm[m] + " " + p[2];
        } catch (Exception e) {
            return d;
        }
    }

    private void title(String t) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextSize(22);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setTextColor(Color.parseColor("#E8ECF1"));
        v.setPadding(0, 0, 0, dp(4));
        root.addView(v);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}

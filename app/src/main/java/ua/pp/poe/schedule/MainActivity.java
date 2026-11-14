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

    private static final String URL_JSON =
            "https://raw.githubusercontent.com/pragmatrans/poe-data/main/schedule.json";
    private static final long REFRESH_MS = 15 * 60 * 1000L;

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

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.parseColor("#121417"));
        scroll.setFillViewport(true);

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(14);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(scroll);

        title("Відключення · poe.pl.ua");
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
                try {
                    HttpURLConnection c = (HttpURLConnection) new URL(URL_JSON).openConnection();
                    c.setConnectTimeout(12000);
                    c.setReadTimeout(20000);
                    c.setRequestProperty("Cache-Control", "no-cache");
                    BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = r.readLine()) != null) sb.append(line).append('\n');
                    r.close();
                    if (c.getResponseCode() != 200) err = "сервер віддав " + c.getResponseCode();
                    else body = sb.toString();
                } catch (Exception e) {
                    err = e.getClass().getSimpleName() + ": " + e.getMessage();
                }
                final String fErr = err;
                final String fBody = body;
                handler.post(new Runnable() {
                    @Override public void run() {
                        if (fErr != null) showError(fErr);
                        else if (!render(fBody)) showError("JSON не розібрався");
                    }
                });
            }
        }).start();
    }

    private void showError(String msg) {
        status.setText("не вдалось оновити — " + msg + "\nторкнись, щоб спробувати ще");
        status.setTextColor(Color.parseColor("#FF8A80"));
        status.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { load(); }
        });
    }

    private boolean render(String body) {
        try {
            JSONObject data = new JSONObject(body);
            String updated = data.optString("updated", "?");
            status.setTextColor(Color.parseColor("#8A94A6"));
            status.setText("оновлено: " + updated + "  ·  торкнись, щоб оновити");
            status.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { load(); }
            });

            // лишаємо заголовок і статус, решту перемальовуємо
            while (root.getChildCount() > 2) root.removeViewAt(2);

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
        DayBarView bar = new DayBarView(this, cells);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46));
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

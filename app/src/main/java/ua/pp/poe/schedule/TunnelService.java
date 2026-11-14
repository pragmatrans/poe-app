package ua.pp.poe.schedule;

import android.app.Service;
import android.content.Intent;
import android.net.VpnService;
import android.os.IBinder;

import com.wireguard.android.backend.GoBackend;
import com.wireguard.android.backend.Tunnel;
import com.wireguard.config.Config;

/**
 * Мінімальний VpnService навколо офіційного WireGuard-бекенду.
 * Тунель піднімається лише на нашу підсеть 10.66.66.0/24 — решта трафіку йде як звичайно.
 */
public class TunnelService extends VpnService implements Tunnel.StateListener {

    private static Tunnel tunnel;
    private GoBackend backend;

    @Override public void onCreate() {
        super.onCreate();
        backend = new GoBackend(this);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String cfg = intent == null ? null : intent.getStringExtra("cfg");
        if (cfg == null) return START_NOT_STICKY;
        try {
            Config config = Config.parse(cfg);
            if (tunnel != null) {
                try { tunnel.down(); } catch (Exception ignored) { }
            }
            tunnel = new Tunnel() {
                @Override public String getName() { return "poe"; }
                @Override public void onStateChange(State newState) { }
            };
            tunnel.setStateListener(this);
            backend.setState(tunnel, Tunnel.State.UP, config);
        } catch (Exception e) {
            // не вдалось — додаток піде запасним шляхом (публікований JSON)
        }
        return START_STICKY;
    }

    @Override public void onStateChange(Tunnel.State state) { }

    @Override public void onRevoke() {
        stopSelf();
    }

    @Override public void onDestroy() {
        try {
            if (tunnel != null) backend.setState(tunnel, Tunnel.State.DOWN, null);
        } catch (Exception ignored) { }
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent i) {
        return super.onBind(i);
    }
}

package ua.pp.poe.schedule;

import android.content.Intent;
import android.net.VpnService;

import com.wireguard.android.backend.GoBackend;
import com.wireguard.android.backend.Tunnel;
import com.wireguard.config.Config;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

/**
 * Мінімальний VpnService навколо офіційного WireGuard-бекенду.
 * Тунель піднімається лише на нашу підсеть 10.66.66.0/24 — решта трафіку йде як звичайно.
 */
public class TunnelService extends VpnService {

    private GoBackend backend;
    private Tunnel tunnel;

    @Override public void onCreate() {
        super.onCreate();
        backend = new GoBackend(this);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        final String cfg = intent == null ? null : intent.getStringExtra("cfg");
        if (cfg == null) return START_NOT_STICKY;
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    Config config = Config.parse(new ByteArrayInputStream(
                            cfg.getBytes(StandardCharsets.UTF_8)));
                    tunnel = new Tunnel() {
                        @Override public String getName() { return "poe"; }
                        @Override public void onStateChange(State newState) { }
                    };
                    backend.setState(tunnel, Tunnel.State.UP, config);
                } catch (Exception e) {
                    // не піднявся — додаток піде запасним шляхом (опублікований JSON)
                }
            }
        }).start();
        return START_STICKY;
    }

    @Override public void onDestroy() {
        try {
            if (tunnel != null) backend.setState(tunnel, Tunnel.State.DOWN, null);
        } catch (Exception ignored) { }
        super.onDestroy();
    }
}

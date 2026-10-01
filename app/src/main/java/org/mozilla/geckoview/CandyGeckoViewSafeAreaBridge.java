package org.mozilla.geckoview;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import androidx.core.view.WindowInsetsCompat;

/**
 * Bridges safe-area overrides and themed loading covers for Candy-owned browser hosts.
 *
 * <p>GeckoView 155 defines the system-bar and cutout union, but reads it from an internal
 * global-layout listener. Candy owns the root inset listener and can host multiple GeckoViews, so
 * it forwards the same union to each renderer explicitly. It also sends zero when native margins
 * or a Compose safe-drawing host own the inset. Keeping this bridge in GeckoView's package provides
 * a compile-checked path without reflection. The same bridge uses Gecko's effective runtime theme
 * for its native loading cover. Remove it when GeckoView exposes public per-view safe-area and
 * effective loading-theme APIs.
 */
public abstract class CandyGeckoViewSafeAreaBridge extends GeckoView {
    protected CandyGeckoViewSafeAreaBridge(Context context) {
        super(context);
        // GeckoView initializes its SurfaceView cover to white, before a session is available.
        boolean dark = (context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        coverUntilFirstPaint(dark ? DEFAULT_DARK_COLOR : Color.WHITE);
    }

    @Override
    public void setSession(GeckoSession session) {
        if (session == getSession()) {
            return;
        }
        // The runtime also accounts for explicit website appearance and nested app night overrides.
        GeckoRuntime runtime = session.getRuntime();
        if (runtime != null) {
            coverUntilFirstPaint(runtime.usesDarkTheme() ? DEFAULT_DARK_COLOR : Color.WHITE);
        }
        super.setSession(session);
    }

    protected final void dispatchCandySafeAreaInsets(
            int top,
            int right,
            int bottom,
            int left
    ) {
        GeckoSession session = getSession();
        GeckoDisplay display = session != null ? session.getDisplay() : null;
        if (display != null) {
            display.safeAreaInsetsChanged(top, right, bottom, left);
        }
    }

    /**
     * Forwards Candy's effective IME insets to this display. Dispatching insets to the child view
     * does not invoke GeckoView's keyboard listener, which is registered on the Activity root.
     */
    protected final void dispatchCandyWindowInsets(WindowInsetsCompat insets) {
        GeckoSession session = getSession();
        GeckoDisplay display = session != null ? session.getDisplay() : null;
        if (display != null) {
            display.windowInsetsChanged(insets);
        }
    }
}

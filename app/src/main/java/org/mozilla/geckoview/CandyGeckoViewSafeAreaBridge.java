package org.mozilla.geckoview;

import android.content.Context;

/**
 * Exposes an explicit safe-area override for Candy-owned native and Compose safe-area hosts.
 *
 * <p>GeckoView 155 defines the system-bar and cutout union, but reads it from an internal
 * global-layout listener. Candy owns the root inset listener and can host multiple GeckoViews, so
 * it forwards the same union to each renderer explicitly. It also sends zero when native margins
 * or a Compose safe-drawing host own the inset. Keeping this bridge in GeckoView's package provides
 * a compile-checked path without reflection. Remove it when GeckoView exposes a public per-view
 * safe-area API.
 */
public abstract class CandyGeckoViewSafeAreaBridge extends GeckoView {
    protected CandyGeckoViewSafeAreaBridge(Context context) {
        super(context);
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
}

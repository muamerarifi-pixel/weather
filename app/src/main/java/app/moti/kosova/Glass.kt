package app.moti.kosova

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.View
import android.view.WindowInsets

/**
 * Frosted-glass window: the app sits on the home-screen wallpaper and, on Android 12+,
 * the wallpaper behind it is blurred by the system.
 */
object Glass {

    fun dp(ctx: Context, v: Int): Int = (v * ctx.resources.displayMetrics.density + 0.5f).toInt()

    fun setup(a: Activity) {
        val w = a.window
        if (Build.VERSION.SDK_INT >= 30) {
            w.setDecorFitsSystemWindows(false)
        } else {
            legacyFullscreen(a)
        }
        transparentBars(a)
        if (Build.VERSION.SDK_INT >= 29) {
            w.isNavigationBarContrastEnforced = false
            w.isStatusBarContrastEnforced = false
        }
        updateBlur(a)
    }

    @Suppress("DEPRECATION")
    private fun legacyFullscreen(a: Activity) {
        a.window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
    }

    @Suppress("DEPRECATION")
    private fun transparentBars(a: Activity) {
        a.window.statusBarColor = Color.TRANSPARENT
        a.window.navigationBarColor = Color.TRANSPARENT
    }

    /** Real blur when the phone supports it (and battery saver is off); otherwise a darker tint. */
    fun updateBlur(a: Activity) {
        val w = a.window
        var blur = false
        if (Build.VERSION.SDK_INT >= 31) {
            blur = a.windowManager.isCrossWindowBlurEnabled
            w.setBackgroundBlurRadius(if (blur) dp(a, 36) else 0)
        }
        w.setBackgroundDrawable(ColorDrawable(if (blur) 0x8C0B1220.toInt() else 0xE60B1220.toInt()))
    }

    /** Keeps content clear of the status bar, navigation bar and keyboard. */
    fun applyInsets(v: View) {
        val l = v.paddingLeft
        val t = v.paddingTop
        val r = v.paddingRight
        val b = v.paddingBottom
        v.setOnApplyWindowInsetsListener { view, insets ->
            if (Build.VERSION.SDK_INT >= 30) {
                val i = insets.getInsets(
                    WindowInsets.Type.systemBars() or WindowInsets.Type.ime() or WindowInsets.Type.displayCutout()
                )
                view.setPadding(l + i.left, t + i.top, r + i.right, b + i.bottom)
            } else {
                legacyPadding(view, insets, l, t, r, b)
            }
            insets
        }
        v.requestApplyInsets()
    }

    @Suppress("DEPRECATION")
    private fun legacyPadding(view: View, insets: WindowInsets, l: Int, t: Int, r: Int, b: Int) {
        view.setPadding(
            l + insets.systemWindowInsetLeft,
            t + insets.systemWindowInsetTop,
            r + insets.systemWindowInsetRight,
            b + insets.systemWindowInsetBottom,
        )
    }
}

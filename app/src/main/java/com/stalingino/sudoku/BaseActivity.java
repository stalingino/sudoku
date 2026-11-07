package com.stalingino.sudoku;

import android.app.ActionBar;
import android.app.Activity;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.Toolbar;

import java.util.Locale;

/**
 * Shared screen chrome: a Toolbar inside {@code R.id.toolbar_container}, and edge-to-edge
 * drawing (enforced on Android 15+) with the system-bar insets applied as padding so nothing
 * slides under the status or navigation bar.
 */
public abstract class BaseActivity extends Activity {

    /** Call after setContentView. The layout needs root, toolbar_container, toolbar and content. */
    @SuppressWarnings("deprecation")
    protected void setupChrome(CharSequence title, boolean showUp) {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setActionBar(toolbar);
        ActionBar ab = getActionBar();
        if (ab != null) {
            ab.setTitle(title);
            ab.setDisplayHomeAsUpEnabled(showUp);
        }

        View root = findViewById(R.id.root);
        View bar = findViewById(R.id.toolbar_container);
        View content = findViewById(R.id.content);
        boolean night = isNight();

        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(false);
            WindowInsetsController c = getWindow().getInsetsController();
            if (c != null) {
                c.setSystemBarsAppearance(night ? 0 : WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                        WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                                | WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS);
            }
        } else {
            int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
            if (!night && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
            root.setSystemUiVisibility(flags);
        }

        int cLeft = content.getPaddingLeft(), cRight = content.getPaddingRight(), cBottom = content.getPaddingBottom();
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int left, top, right, bottom;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                android.graphics.Insets i = insets.getInsets(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                left = i.left; top = i.top; right = i.right; bottom = i.bottom;
            } else {
                left = insets.getSystemWindowInsetLeft();
                top = insets.getSystemWindowInsetTop();
                right = insets.getSystemWindowInsetRight();
                bottom = insets.getSystemWindowInsetBottom();
            }
            bar.setPadding(left, top, right, 0);
            content.setPadding(cLeft + left, content.getPaddingTop(), cRight + right, cBottom + bottom);
            return insets;
        });
        root.requestApplyInsets();
    }

    protected boolean isNight() {
        return (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public static String formatTime(long s) {
        return s >= 3600
                ? String.format(Locale.US, "%d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60)
                : String.format(Locale.US, "%02d:%02d", s / 60, s % 60);
    }

    public static String starString(int stars) {
        StringBuilder sb = new StringBuilder(3);
        for (int i = 0; i < 3; i++) sb.append(i < stars ? '★' : '☆');
        return sb.toString();
    }

    public static int difficultyColor(Difficulty d) {
        switch (d) {
            case EASY: return R.color.diff_easy;
            case MEDIUM: return R.color.diff_medium;
            case HARD: return R.color.diff_hard;
            default: return R.color.diff_expert;
        }
    }
}

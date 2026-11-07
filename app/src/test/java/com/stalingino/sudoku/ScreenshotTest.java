package com.stalingino.sudoku;

import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Looper;
import android.view.View;

import java.io.File;
import java.io.FileOutputStream;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/**
 * Renders the main screens to PNG files for manual review. Runs only when the
 * screenshotDir system property is set (./gradlew testDebugUnitTest -PscreenshotDir=...).
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 34, qualifiers = "w411dp-h891dp-xxhdpi")
public class ScreenshotTest {
    private File dir;

    @Before
    public void setUp() {
        String path = System.getProperty("screenshotDir");
        Assume.assumeTrue(path != null && !path.isEmpty());
        dir = new File(path);
        dir.mkdirs();
        Context app = RuntimeEnvironment.getApplication();
        app.getSharedPreferences("sudoku_store", Context.MODE_PRIVATE).edit().clear().commit();
        PuzzleStore.resetInstance();
        PuzzleStore store = PuzzleStore.get(app);
        // Some progress so the screens have something to show.
        for (int i = 1; i <= 6; i++) {
            PuzzleRecord r = store.level(Difficulty.EASY, i);
            r.timesSolved = 1;
            r.stars = i % 3 + 1;
            r.bestSeconds = 200 + i * 17;
        }
        PuzzleRecord r = store.level(Difficulty.EASY, 7);
        r.progress = r.puzzle.replaceFirst("0", "4");
        r.elapsedSeconds = 95;
        store.save();
        store.setCurrent(r);
    }

    private void capture(Activity a, String name) throws Exception {
        shadowOf(Looper.getMainLooper()).idle();
        View root = a.getWindow().getDecorView();
        int w = 1233, h = 2673; // 411x891dp at xxhdpi
        root.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, w, h);
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        root.draw(new Canvas(b));
        try (FileOutputStream out = new FileOutputStream(new File(dir, name + ".png"))) {
            b.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
    }

    private void all(String suffix) throws Exception {
        capture(Robolectric.buildActivity(HomeActivity.class).setup().get(), "home" + suffix);
        capture(Robolectric.buildActivity(LevelsActivity.class).setup().get(), "levels" + suffix);
        Intent i = new Intent(RuntimeEnvironment.getApplication(), GameActivity.class)
                .putExtra(GameActivity.EXTRA_ID, PuzzleRecord.levelId(Difficulty.EASY, 7));
        capture(Robolectric.buildActivity(GameActivity.class, i).setup().get(), "game" + suffix);
    }

    @Test
    public void light() throws Exception {
        all("");
    }

    @Test
    @Config(qualifiers = "+night")
    public void dark() throws Exception {
        all("_dark");
    }
}

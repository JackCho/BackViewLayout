package me.ele.backviewlayout;

import android.view.View;
import android.view.ViewTreeObserver;

/**
 * Created by chensimin on 14/11/14.
 */
public class ViewLayoutObserver {

    private ViewLayoutObserver() {

    }

    public static void whenLayoutFinished(final View view, final Runnable runnable) {
        view.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                view.getViewTreeObserver().removeGlobalOnLayoutListener(this);
                if (runnable != null) {
                    runnable.run();
                }
            }
        });

    }
}

package com.amazonaws.services.chime.sdkdemo.util;

import android.os.Handler;

public class Exclusive {

    private static Handler mExclusiveHandler = new Handler();
    private static boolean isQuickPushpop = false;
    private static boolean isTabSwitch = false;

    private Runnable mAbortRunnable;

    private Exclusive() {}

    public static class ActivityExclusive extends Exclusive {
        public ActivityExclusive abort(Runnable runnable) {
            super.abort(runnable);
            return this;
        }
        public void start(final Runnable runnable) {
            super.start(runnable);
        }
    }

    public static class TabExclusive extends Exclusive {
        public TabExclusive abort(Runnable runnable) {
            super.abort(runnable);
            return this;
        }
        public void go(final Runnable runnable) {
            super.go(runnable);
        }
    }

    public static class NormalExclusive extends Exclusive {
        public NormalExclusive abort(Runnable runnable) {
            super.abort(runnable);
            return this;
        }
        public void tap(Runnable runnable) {
            super.tap(runnable);
        }
    }

    public static TabExclusive Tab() {
        return new TabExclusive();
    }

    public static ActivityExclusive Activity() {
        return new ActivityExclusive();
    }

    public static NormalExclusive Normal() {
        return new NormalExclusive();
    }

    private Exclusive abort(Runnable runnable) {
        mAbortRunnable = runnable;
        return this;
    }

    private ExclusiveListener makeListener(final Runnable runnable) {
        return new ExclusiveListener<Void>() {
            @Override
            public Void onExclusive() {
                runnable.run();
                return null;
            }

            @Override
            public void onAbort() {
                if (mAbortRunnable != null) {
                    mAbortRunnable.run();
                }
            }
        };
    }

    private void go(final Runnable runnable) {
        switchTab(makeListener(runnable));
    }

    private void start(final Runnable runnable) {
        switchView(makeListener(runnable));
    }

    private void tap(final Runnable runnable) {
        normalAction(makeListener(runnable));
    }

    /*
     * Tab切替の場合にはexecuteを使って
     */
    public static <T> T switchTab(ExclusiveListener<T> el) {
        if (isTabSwitch) {
            //LogUtils.w("Exclusive Touch : Abort this Tab switch because the last not completed.");
            el.onAbort();
            return null;
        }
        isTabSwitch = true;

        T t = el.onExclusive();

        mExclusiveHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                isTabSwitch = false;
            }
        }, LONG_TIME);
        return t;
    }

    /*
     * 画面Pop、Pushの場合にはexecuteを使って
     */
    public static <T> T switchView(ExclusiveListener<T> el) {
        if (isQuickPushpop) {
            //LogUtils.w("Exclusive Touch : Abort this operation because the last not completed.");
            el.onAbort();
            return null;
        }
        isQuickPushpop = true;

        T t = el.onExclusive();

        mExclusiveHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                isQuickPushpop = false;
            }
        }, LONG_TIME);
        return t;
    }

    private static boolean isQuickClick = false;
    /*
     * 早速タップを防止する場合にはclickを使って
     * 画面遷移の途中にタップを許さないようにする
     */
    public static <T> T normalAction(ExclusiveListener<T> el) {
        if (isQuickClick || isQuickPushpop) {
//            LogUtils.w("Exclusive Touch : Abort because clicking is fast.");
            return null;
        }
        isQuickClick = true;

        T t = el.onExclusive();

        mExclusiveHandler.postDelayed(recovery, LONG_TIME);
        return t;
    }

    private static final int LONG_TIME = 800;
    private static final int SHORT_TIME = 200;
    private static Runnable recovery = new Runnable() {
        @Override
        public void run() {
            isQuickClick = false;
        }
    };

    public static void recoveryClick() {
        mExclusiveHandler.removeCallbacks(recovery);
        mExclusiveHandler.postDelayed(recovery, SHORT_TIME);
    }

    public interface ExclusiveListener<T> {
        T onExclusive();
        void onAbort();
    }
}
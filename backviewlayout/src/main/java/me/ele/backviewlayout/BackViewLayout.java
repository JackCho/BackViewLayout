/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package me.ele.backviewlayout;

import android.content.Context;
import android.content.res.TypedArray;
import android.support.v4.view.MotionEventCompat;
import android.support.v4.view.ViewCompat;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.view.animation.Interpolator;
import android.view.animation.Transformation;
import android.widget.AbsListView;
import android.widget.ScrollView;

import java.util.concurrent.atomic.AtomicBoolean;


public class BackViewLayout extends ViewGroup {
    private static final String LOG_TAG = BackViewLayout.class.getSimpleName();

    private static final long RETURN_TO_ORIGINAL_POSITION_TIMEOUT = 300;
    private static final float MAX_SWIPE_DISTANCE_FACTOR = .6f;
    private static final int REFRESH_TRIGGER_DISTANCE = 120;
    private static final int INVALID_POINTER = -1;

    private View mTarget; //the content that gets pulled down
    private int mOriginalOffsetTop;
    private boolean mRefreshing = false;
    private int mTouchSlop;
    private float mDistanceToTriggerSync = -1;
    private int mMediumAnimationDuration;
    private int mCurrentTargetOffsetTop;

    private View mBackView;

    private float mInitialMotionY;
    private float mLastMotionY;
    private boolean mIsBeingDragged;
    private int mActivePointerId = INVALID_POINTER;
    private boolean layouted;

    // Target is returning to its start offset because it was cancelled or a
    // refresh was triggered.
    private boolean mReturning;
    private static final Interpolator SINTERPOLATOR = new Interpolator() {
        public float getInterpolation(float t) {
            t -= 1.0f;
            return t * t * t * t * t + 1.0f;
        }
    };

    private static final int[] LAYOUT_ATTRS = new int[]{
            android.R.attr.enabled, R.attr.backViewLayout
    };


    private class ReturnToStartPositionRunnable implements Runnable {

        private final AnimationListener mReturnToStartPositionListener = new BaseAnimationListener() {
            @Override
            public void onAnimationEnd(Animation animation) {
                // Once the target content has returned to its start position, reset
                // the target offset to 0
                mCurrentTargetOffsetTop = 0;
                mReturning = false;
            }
        };

        private class AnimateToStartPosition extends Animation {

            private int from;
            private AtomicBoolean stoped;

            public AnimateToStartPosition() {
                stoped = new AtomicBoolean(false);
            }

            public void reset() {
                super.reset();
                stoped.set(false);
            }

            public void stop() {
                stoped.set(true);
            }

            public void setFromPosition(int from) {
                this.from = from;
            }


            @Override
            public void applyTransformation(float interpolatedTime, Transformation t) {
                if (!stoped.get()) {
                    int targetTop = (from + (int) ((mOriginalOffsetTop - from) * interpolatedTime));
                    int offset = targetTop - mTarget.getTop();
                    final int currentTop = mTarget.getTop();
                    int nextTop = offset + currentTop;
                    if (nextTop < 0) {
                        offset = 0 - currentTop;
                    }
                    setTargetOffsetTopAndBottom(offset);
                }
            }
        }

        ;

        private AnimateToStartPosition mAnimateToStartPosition = new AnimateToStartPosition();

        @Override
        public void run() {
            ensureTarget();
            mReturning = true;
            animateOffsetToStartPosition(mCurrentTargetOffsetTop + getPaddingTop(), mReturnToStartPositionListener);
        }

        private void animateOffsetToStartPosition(int from, AnimationListener listener) {
            mAnimateToStartPosition.reset();
            mAnimateToStartPosition.setDuration(mMediumAnimationDuration);
            mAnimateToStartPosition.setInterpolator(SINTERPOLATOR);
            mAnimateToStartPosition.setFromPosition(from);
            mAnimateToStartPosition.setAnimationListener(listener);
            mTarget.startAnimation(mAnimateToStartPosition);
        }

        public void cancel() {
            removeCallbacks(this);
            mAnimateToStartPosition.stop();
        }
    }

    private final class ReturnToRefreshingPositionRunnable implements Runnable {

        private final AnimationListener mReturnToRefreshingPositionListener = new BaseAnimationListener() {
            @Override
            public void onAnimationEnd(Animation animation) {
                mCurrentTargetOffsetTop = (int) mDistanceToTriggerSync;
                startRefresh();
                mReturning = false;
            }
        };

        private class AnimateToRefreshingPosition extends Animation {

            private int from;
            private AtomicBoolean stoped;

            public AnimateToRefreshingPosition() {
                stoped = new AtomicBoolean(false);
            }

            public void reset() {
                super.reset();
                stoped.set(false);
            }

            public void stop() {
                stoped.set(true);
            }

            public void setFromPosition(int from) {
                this.from = from;
            }

            @Override
            public void applyTransformation(float interpolatedTime, Transformation t) {
                if (!stoped.get()) {
                    int translation = (int) ((mDistanceToTriggerSync - from) * interpolatedTime);
                    int targetTop = from + translation;
                    int currentTop = mTarget.getTop();
                    int offset = (targetTop - currentTop);
                    setTargetOffsetTopAndBottom(offset);
                }
            }
        }

        ;

        private final AnimateToRefreshingPosition mAnimateToRefreshingPosition = new AnimateToRefreshingPosition();

        public void run() {
            ensureTarget();
            if (mTarget instanceof AbsListView) {
                ((AbsListView) mTarget).setSelection(0);
            }
            if (mTarget instanceof ScrollView) {
                ScrollView scrollView = (ScrollView) mTarget;
                scrollView.scrollTo(scrollView.getScrollX(), 0);
            }

            animateOffsetToRefreshingPosition(mCurrentTargetOffsetTop + getPaddingTop(), mReturnToRefreshingPositionListener);
        }

        private void animateOffsetToRefreshingPosition(int from, AnimationListener listener) {
            mAnimateToRefreshingPosition.reset();
            mAnimateToRefreshingPosition.setDuration(mMediumAnimationDuration);
            mAnimateToRefreshingPosition.setInterpolator(SINTERPOLATOR);
            mAnimateToRefreshingPosition.setFromPosition(from);
            mAnimateToRefreshingPosition.setAnimationListener(listener);
            mTarget.startAnimation(mAnimateToRefreshingPosition);
        }

        public void cancel() {
            removeCallbacks(this);
            mAnimateToRefreshingPosition.stop();
        }
    }

    ;

    private class CancelPullRunnable implements Runnable {

        @Override
        public void run() {
            mReturning = true;
            animateToFinished();
        }

        public void cancel() {
            removeCallbacks(this);
        }

    }

    private final ReturnToRefreshingPositionRunnable returnToRefreshingPositionRunnable = new ReturnToRefreshingPositionRunnable();
    private final ReturnToStartPositionRunnable returnToStartPositionRunnable = new ReturnToStartPositionRunnable();
    private final CancelPullRunnable cancelPullRunnable = new CancelPullRunnable();


    /**
     * Simple constructor to use when creating a SwipeRefreshLayout from code.
     *
     * @param context
     */
    public BackViewLayout(Context context) {
        this(context, null);
    }

    /**
     * Constructor that is called when inflating SwipeRefreshLayout from XML.
     *
     * @param context
     * @param attrs
     */
    public BackViewLayout(Context context, AttributeSet attrs) {
        super(context, attrs);

        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();

        mMediumAnimationDuration = getResources().getInteger(
                android.R.integer.config_longAnimTime);

        setWillNotDraw(false);

        final TypedArray a = context.obtainStyledAttributes(attrs, LAYOUT_ATTRS);
        setEnabled(a.getBoolean(0, true));
        int mRefreshViewLayoutId = a.getResourceId(1, -1);
        if (mRefreshViewLayoutId > 0) {
            mBackView = LayoutInflater.from(context).inflate(mRefreshViewLayoutId, this, false);
        } else {
            mBackView = LayoutInflater.from(getContext()).inflate(R.layout.default_back_view, this, false);
            mBackView.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        }
        addView(mBackView);
        a.recycle();
        ViewLayoutObserver.whenLayoutFinished(this, new Runnable() {
            @Override
            public void run() {
                layouted = true;
            }
        });
    }


    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        removeCallbacks();
    }

    private void removeCallbacks() {
        returnToStartPositionRunnable.cancel();
        returnToRefreshingPositionRunnable.cancel();
        cancelPullRunnable.cancel();
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        removeCallbacks();
    }

    /**
     * Notify the widget that refresh state has changed. Do not call this when
     * refresh is triggered by a swipe gesture.
     *
     * @param refreshing Whether or not the view should show refresh progress.
     */
    private void setRefreshing(boolean refreshing) {
        if (mRefreshing != refreshing) {
            ensureTarget();
            mRefreshing = refreshing;
        }
    }

    private void postRunnable(final Runnable runnable) {
        if (layouted) {
            post(runnable);
        } else {
            ViewLayoutObserver.whenLayoutFinished(this, new Runnable() {
                @Override
                public void run() {
                    layouted = true;
                    post(runnable);
                }
            });
        }
    }

    private void animateToFinished() {
        removeCallbacks();
        postRunnable(returnToStartPositionRunnable);
    }

    /**
     * @return Whether the SwipeRefreshWidget is actively showing refresh
     * progress.
     */

    private void ensureTarget() {
        // Don't bother getting the parent height if the parent hasn't been laid out yet.
        if (mTarget == null) {
            checkChild();
            mTarget = getChildAt(1);
            mOriginalOffsetTop = mTarget.getTop() + getPaddingTop();
        }
        if (mDistanceToTriggerSync == -1) {
            if (mBackView != null) {
                mDistanceToTriggerSync = mBackView.getMeasuredHeight();
                return;
            }
            if (getParent() != null && ((View) getParent()).getHeight() > 0) {
                final DisplayMetrics metrics = getResources().getDisplayMetrics();
                mDistanceToTriggerSync = (int) Math.min(
                        ((View) getParent()).getHeight() * MAX_SWIPE_DISTANCE_FACTOR,
                        REFRESH_TRIGGER_DISTANCE * metrics.density);
            }
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        final int width = getMeasuredWidth();
        final int height = getMeasuredHeight();
        if (getChildCount() == 0) {
            return;
        }

        final int childLeft = getPaddingLeft();
        final int childTop = mCurrentTargetOffsetTop + getPaddingTop();
        final int childWidth = width - getPaddingLeft() - getPaddingRight();
        final int childHeight = height - getPaddingTop() - getPaddingBottom();

        mBackView.layout(childLeft, childTop
                , childLeft + mBackView.getMeasuredWidth(), childTop + mBackView.getMeasuredHeight());
        View content = getChildAt(1);
        if (content != null) {
            content.layout(childLeft, childTop, childLeft + childWidth, childTop + childHeight);
        }
    }

    @Override
    public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        measureChildren(widthMeasureSpec, heightMeasureSpec);
        ensureTarget();
    }

    private void checkChild() {
        if (((mBackView == null && getChildCount() > 1) || (mBackView != null && getChildCount() > 2)) && !isInEditMode()) {
            throw new IllegalStateException(getClass().getSimpleName() + " can host only one direct child");
        }
    }

    /**
     * @return Whether it is possible for the child view of this layout to
     * scroll up. Override this if the child view is a custom view.
     */
    private boolean canChildScrollUp() {
        if (android.os.Build.VERSION.SDK_INT < 14) {
            if (mTarget instanceof AbsListView) {
                final AbsListView absListView = (AbsListView) mTarget;
                return absListView.getChildCount() > 0
                        && (absListView.getFirstVisiblePosition() > 0 || absListView.getChildAt(0)
                        .getTop() < absListView.getPaddingTop());
            } else {
                return mTarget.getScrollY() > 0;
            }
        } else {
            return ViewCompat.canScrollVertically(mTarget, -1);
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        ensureTarget();
        final int action = MotionEventCompat.getActionMasked(ev);


        if (!isEnabled() || mReturning || canChildScrollUp() || !canDrag) {
            // Fail fast if we're not in a state where a swipe is possible
            return false;
        }

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                mLastMotionY = mInitialMotionY = ev.getY();
                mActivePointerId = MotionEventCompat.getPointerId(ev, 0);
                mIsBeingDragged = false;
                break;

            case MotionEvent.ACTION_MOVE:
                if (mActivePointerId == INVALID_POINTER) {
                    Log.e(LOG_TAG, "Got ACTION_MOVE event but don't have an active pointer id.");
                    return false;
                }

                final int pointerIndex = MotionEventCompat.findPointerIndex(ev, mActivePointerId);
                if (pointerIndex < 0) {
                    Log.e(LOG_TAG, "Got ACTION_MOVE event but have an invalid active pointer id.");
                    return false;
                }

                final float y = MotionEventCompat.getY(ev, pointerIndex);
                final float yDiff = y - mInitialMotionY;
                if (yDiff > mTouchSlop) {
                    mLastMotionY = y;
                    mIsBeingDragged = true;
                }
                break;

            case MotionEventCompat.ACTION_POINTER_UP:
                onSecondaryPointerUp(ev);
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mIsBeingDragged = false;
                mActivePointerId = INVALID_POINTER;
                break;
        }

        return mIsBeingDragged;
    }

    private boolean canDrag = true;

    @Override
    public void requestDisallowInterceptTouchEvent(boolean b) {
        // Nope.
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        final int action = MotionEventCompat.getActionMasked(ev);

        if (!isEnabled() || mReturning || canChildScrollUp() || !canDrag) {
            // Fail fast if we're not in a state where a swipe is possible
            return false;
        }
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                mLastMotionY = mInitialMotionY = ev.getY();
                mActivePointerId = MotionEventCompat.getPointerId(ev, 0);
                mIsBeingDragged = false;
                break;

            case MotionEvent.ACTION_MOVE:
                final int pointerIndex = MotionEventCompat.findPointerIndex(ev, mActivePointerId);
                if (pointerIndex < 0) {
                    Log.e(LOG_TAG, "Got ACTION_MOVE event but have an invalid active pointer id.");
                    return false;
                }

                final float y = MotionEventCompat.getY(ev, pointerIndex);
                final float yDiff = y - mInitialMotionY;
                final float yDelta = y - mLastMotionY;

                if (!mIsBeingDragged && yDiff > mTouchSlop) {
                    mIsBeingDragged = true;
                }

                if (mIsBeingDragged) {
//                    final int targetTop = mCurrentTargetOffsetTop + (yDiff > 0 ? (int)(yDiff * 0.6) : (int) yDiff);
//                    final int targetTop = (int) (mCurrentTargetOffsetTop + yDiff);
                    updateContentOffsetTop((int) yDelta);
                    if (mLastMotionY > y && mTarget.getTop() == getPaddingTop()) {
                        removeCallbacks(cancelPullRunnable);
                    } else {
                        updatePositionTimeout();
                    }
                    mLastMotionY = y;
                }
                break;

            case MotionEventCompat.ACTION_POINTER_DOWN: {
                final int index = MotionEventCompat.getActionIndex(ev);
                mLastMotionY = MotionEventCompat.getY(ev, index);
                mActivePointerId = MotionEventCompat.getPointerId(ev, index);
                break;
            }

            case MotionEventCompat.ACTION_POINTER_UP:
                onSecondaryPointerUp(ev);
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mIsBeingDragged = false;
                mActivePointerId = INVALID_POINTER;
                post(cancelPullRunnable);
                return false;
        }

        return true;
    }

    private void startRefresh() {
        removeCallbacks();
        setRefreshing(true);
    }

    private void updateContentOffsetTop(int offset) {
        removeCallbacks();
        final int currentTop = mTarget.getTop();
        int nextTop = offset + currentTop;
        if (nextTop <= 0) {
            offset = -currentTop;
        } else if (nextTop > 2 * mDistanceToTriggerSync && offset > 0) {
            offset *= 0.2;
        }
        setTargetOffsetTopAndBottom(offset);
    }

    private void setTargetOffsetTopAndBottom(final int offset) {
        if (offset == 0) {
            return;
        }
        mTarget.offsetTopAndBottom(offset);
        mCurrentTargetOffsetTop = mTarget.getTop();
    }


    private void updatePositionTimeout() {
        removeCallbacks(cancelPullRunnable);
        postDelayed(cancelPullRunnable, RETURN_TO_ORIGINAL_POSITION_TIMEOUT);
    }

    private void onSecondaryPointerUp(MotionEvent ev) {
        final int pointerIndex = MotionEventCompat.getActionIndex(ev);
        final int pointerId = MotionEventCompat.getPointerId(ev, pointerIndex);
        if (pointerId == mActivePointerId) {
            // This was our active pointer going up. Choose a new
            // active pointer and adjust accordingly.
            final int newPointerIndex = pointerIndex == 0 ? 1 : 0;
            mLastMotionY = MotionEventCompat.getY(ev, newPointerIndex);
            mActivePointerId = MotionEventCompat.getPointerId(ev, newPointerIndex);
        }
    }


    /**
     * Simple AnimationListener to avoid having to implement unneeded methods in
     * AnimationListeners.
     */
    private class BaseAnimationListener implements AnimationListener {
        @Override
        public void onAnimationStart(Animation animation) {
        }

        @Override
        public void onAnimationEnd(Animation animation) {
        }

        @Override
        public void onAnimationRepeat(Animation animation) {
        }
    }

}

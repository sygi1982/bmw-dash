/*******************************************************************************
 * Copyright (c) 2015 Grzegorz Sygieda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 *******************************************************************************/

package com.sygmi.mybmw.dash;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.RemoteViews;

public class FaderView extends View {

    private final String DIR_LEFT_TO_RIGHT = "left2Right";// default
    private final String DIR_BOTTOM_TO_TOP = "bottom2Top";

    private int mValue = 0;
    private int mLastRequestedValue = 0;
    private int mMaxValue = 0;
    private String mDirection = DIR_LEFT_TO_RIGHT;

    public FaderView(final Context context, final AttributeSet attrs, final int defStyle) {
        super(context, attrs, defStyle);

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.MyFaderView, defStyle, 0);
        String dir = a.getString(R.styleable.MyFaderView_direction);
        if (dir != null) {
            mDirection = new String(dir);
        }

        a.recycle();
    }

    public FaderView(final Context context, final AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public FaderView(final Context context) {
        super(context);
    }

    public void setValue(int value) {
        if (value >= 0 && value < mMaxValue) {
            // normalize mValue
            mLastRequestedValue = value;
            if (mDirection.equals(DIR_LEFT_TO_RIGHT)) {
                mValue = value * getWidth() / mMaxValue;
            } else if (mDirection.equals(DIR_BOTTOM_TO_TOP)) {
                mValue = value * getHeight() / mMaxValue;
            }
            invalidate();
        }
    }

    @Override
    protected void onSizeChanged (int w, int h, int oldw, int oldh) {
        setValue(mLastRequestedValue);
        super.onSizeChanged(w, h, oldw, oldw);
    }

    public void setMaxRange(int value) {
        mMaxValue = value;
    }

    @Override
    protected void onDraw(Canvas canvas) {

        canvas.drawColor(Color.TRANSPARENT);
        Paint p = new Paint();
        p.setAntiAlias(true);
        p.setColor(Color.BLACK);
        p.setStyle(Paint.Style.FILL);
        // opacity
        p.setAlpha(0xFF);

        // fader view has gradient_left2right fixed gradient_left2right background, so make proper(black) mask here
        if (mDirection.equals(DIR_LEFT_TO_RIGHT)) {
            canvas.drawRect(mValue, 0, getWidth(), getHeight(), p);
        } else if (mDirection.equals(DIR_BOTTOM_TO_TOP)) {
            canvas.drawRect(0, 0, getWidth(), getHeight() - mValue, p);
        }
    }
}
/*******************************************************************************
 * Copyright (c) 2015 Grzegorz Sygieda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 *******************************************************************************/

package com.sygmi;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class FakeDevice extends CanDriver implements Runnable {

    private static final String TAG = "FakeDevice";

    public final static String DEVICE_NAME = "FAKEDEVICE";

    private Handler mFeedHandler;
    private Thread mFeedThread;
    private Lock mLock = new ReentrantLock();
    private Condition mCondition = mLock.newCondition();
    private ArrayList<CanFrame> mFrames = new ArrayList<CanFrame>();
    private int mCounter = 0;

    public FakeDevice(Context context) {
        super(context);

        mFeedHandler = new Handler(Looper.getMainLooper());
        mFeedThread = new Thread(this);
        mFeedThread.start();
        mIsConnected = true;

        setProduct(DEVICE_NAME);
    }

    @Override
    public void run() {
        if (mIsConnected) {
            mLock.lock();
            String data;
            CanFrame frame;

            data = "S1B4N20C4000000000000";  // Speed
            frame = new CanFrame();
            //Log.w(TAG, "RX DATA " + data);
            Helpers.string2canframe(data, (Object) frame);
            mFrames.add(frame);
            data = "SAAN00000000340D0000";  // RPM
            frame = new CanFrame();
            //Log.w(TAG, "RX DATA " + data);
            Helpers.string2canframe(data, (Object) frame);
            mFrames.add(frame);
            data = "S1D0N8B00000000000000"; // Engine temp.
            frame = new CanFrame();
            //Log.w(TAG, "RX DATA " + data);
            Helpers.string2canframe(data, (Object) frame);
            mFrames.add(frame);
            data = "S1C2N8D725B5AFFFFFFFF"; // PDC
            frame = new CanFrame();
            //Log.w(TAG, "RX DATA " + data);
            Helpers.string2canframe(data, (Object) frame);
            mFrames.add(frame);
            data = "S1D6NC00" + String.format("%X", mCounter);
            mCounter += 1;
            mCounter %= 15;
            frame = new CanFrame();
            //Log.w(TAG, "RX DATA " + data);
            Helpers.string2canframe(data, (Object) frame);
            mFrames.add(frame);

            mCondition.signal();
            mLock.unlock();
        }
        mFeedHandler.postDelayed(this, 1000);
    }

    @Override
    public boolean initiate(int baudRate, int mode) {
        super.initiate(baudRate, mode);
        return true;
    }

    @Override
    public void destroy() {
        mIsConnected = false;
        mLock.lock();
        mCondition.signalAll();
        mLock.unlock();
        mFeedThread.interrupt();
        super.destroy();
    }

    @Override
    public boolean send(CanDriver.CanFrame frame) {
        return false;
    }

    @Override
    public boolean receive(CanDriver.CanFrame frame) {
        boolean status = false;

        mLock.lock();
        try {
            if (mFrames.isEmpty()) {
                //Log.w(TAG, "waiting ... ");
                mCondition.await();
            }

            if (!mFrames.isEmpty()) {
                //Log.w(TAG, "receive ... ");
                CanFrame tmp = mFrames.remove(0);
                frame.clone(tmp);
                status = true;
            }
        } catch (InterruptedException e) {
        } finally {
            mLock.unlock();
        }

        return status;
    }
}


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

public final class X2Can extends CanDriver implements Runnable  {

    private static final String TAG = "X2Can";

    public final static String DEVICE_NAME = "MYBMWDASH";

    private final byte CANCTRL_MODE_SWAP_CHANNEL = 64;

    private Handler mExceptionHandler;
    private Thread mHelperThread;
    private CanDriverMonitor mMonitor = null;

    static {
        // only native IP socket supported for now
        System.loadLibrary("x2can-jni");
    }

    public X2Can(Context context) {
        super(context);

        mExceptionHandler = new Handler(Looper.getMainLooper());
        mHelperThread = new Thread(this);
        mHelperThread.start();
    }

    @Override
    public void run() {
        if (mIsConnected) {
            int weigth = checkException(true);
            Log.d(TAG, "Checking for device  exception...." + weigth);
            if (weigth > 10) {
                mMonitor.onException(-1);
            }
        }
        mExceptionHandler.postDelayed(this, 1000);
    }

    @Override
    public boolean initiate(int baudRate, int mode) {
        super.initiate(baudRate, mode);

        // skip non-standard baudRates
        if (baudRate > 1000) {
            return false;
        }

	    return init(baudRate, mode | CANCTRL_MODE_SWAP_CHANNEL, 100);
   }

    @Override
    public void destroy() {
        mHelperThread.interrupt();
        deinit();
        mIsConnected = false;
        super.destroy();
    }

    @Override
    public boolean send(CanFrame frame) {
        return sendFrame((Object)frame);
    }

    @Override
    public boolean receive(CanFrame frame) {
        return receiveFrame((Object)frame);
    }

    @Override
    public boolean setAcceptFilter(int idMin, int idMax) {
        return setFilter(CanDriver.FILTER_IN, idMin, idMax);
    }

    @Override
    public boolean setRejectFilter(int idMin, int idMax) {
        return setFilter(CanDriver.FILTER_OUT, idMin, idMax);
    }

    @Override
    public boolean wipe(int what) {
        return flush(what);
    }

    /* native methods */
    private native boolean init(int baudRate, int mode, int fifoSize);
    private native boolean deinit();
    private native boolean sendFrame(Object frame);
    private native boolean receiveFrame(Object frame);
    private native boolean setFilter(int type, int idMin, int idMax);
    private native boolean flush(int what);
    private native int checkException(boolean clear);
}

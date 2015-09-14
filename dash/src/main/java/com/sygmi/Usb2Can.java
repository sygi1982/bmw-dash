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

public final class Usb2Can extends CanDriver implements Runnable {

    private static final String TAG = "Usb2Can";

    private Handler mExceptionHandler;
    private Thread mHelperThread;
    private CanDriverMonitor mMonitor = null;

    static {
        System.loadLibrary("usb2can-jni");
    }

    public Usb2Can(Context context) {
        super(context);

        /* Controller service context */
        mMonitor = (CanDriverMonitor)context;

        mExceptionHandler = new Handler(Looper.getMainLooper());
        mHelperThread = new Thread(this);
        mHelperThread.start();
    }

    @Override
    public void run() {
        if (mIsConnected) {
            int weigth = checkException(true);
            //Log.d(TAG, "Checking for device  exception...." + weigth);
            if (weigth > 10) {
                mMonitor.onException(-1);
            }
        }
        mExceptionHandler.postDelayed(this, 1000);
    }

    @Override
    public boolean initiate(int baudRate, int mode) {
        super.initiate(baudRate, mode);

        boolean status = false;

        Log.w(TAG, "Looking for FTDI device ....");

        String path = Helpers.findFtdiDevice(mContext);
        if (path == null) {
            Log.w(TAG, "No ftdi device found !");
            return false;
        }

        Log.w(TAG, "FTDI device found. Giving permissions");

        Helpers.grantFilePermissions(path, "0777");

        // skip non-standard baudRates
        if (baudRate > 1000) {
            return false;
        }

        status = init(baudRate, mode, 100);
        if (status) {
            mIsConnected = true;
        }

	    return status;
   }

    @Override
    public void destroy() {
        mIsConnected = false;
        mHelperThread.interrupt();
        deinit();
        super.destroy();
    }

    @Override
    public boolean send(CanDriver.CanFrame frame) {
        return sendFrame((Object)frame);
    }

    @Override
    public boolean receive(CanDriver.CanFrame frame) {
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

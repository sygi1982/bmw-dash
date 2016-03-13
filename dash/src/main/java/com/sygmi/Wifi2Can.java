/*******************************************************************************
 * Copyright (c) 2015 Grzegorz Sygieda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 *******************************************************************************/

package com.sygmi;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;


/* This is simple Wifi driver that fetches hex string data from device
 * Device shall be connected do AP.
 * Only RX path is available */
public final class Wifi2Can extends CanDriver implements ComLink.ComLinkObserver {

    private static final String TAG = "Wifi2Can";

    public final static String DEVICE_NAME = "MYBMWDASH";

    private ComLink mLink = null;

    private BufferedReader mInput = null;
    private OutputStream mOutput = null;

    private CanDriverMonitor mMonitor = null;

    private Socket mSocket;

    public Wifi2Can(Context context) {
        super(context);

        /* Controller service context */
        mMonitor = (CanDriverMonitor)context;

        setProduct(DEVICE_NAME);
    }

    @Override
    public void onException(int code) {
        /* Just forward the exception code */
        mMonitor.onException(code);
    }

    @Override
    public boolean initiate(int baudRate, int mode) {
        super.initiate(baudRate, mode);

        try {
            mSocket = new Socket("192.168.4.1", 8888);
            mOutput = mSocket.getOutputStream();
            mInput = new BufferedReader(new InputStreamReader(mSocket.getInputStream()));
        } catch (IOException e) {
            Log.w(TAG, "Problem when connecting with server" + e.toString());
            return false;
        }

        mLink = new ComLink(this, mInput, mOutput);

        mIsConnected = true;

        return mLink != null;
    }

    @Override
    public boolean setAcceptFilter(int idMin, int idMax) {
        if (mLink != null) {
            return mLink.setFiltering(idMin, idMax);
        }

        return false;
    }

    @Override
    public void destroy() {

        if (mLink != null) {
            mLink.destroy();
        }

        mIsConnected = false;

        super.destroy();
    }

    @Override
    public boolean receive(CanFrame frame) {
        if (mLink != null) {
            return mLink.receive(frame);
        }

        return false;
    }
}


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
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;


/* This is simple Wifi driver that fetches hex string data from device
 * Device shall be connected do AP.
 * Only RX path is available */
public final class Wifi2Can extends CanDriver implements ComLink.ComLinkObserver {

    private static final String TAG = "Wifi2Can";

    public final static String DEVICE_NAME = "MYBMWDASH";

    private final static int DEVICE_PORT = 8888;

    private ComLink mLink = null;

    private BufferedReader mInput = null;
    private OutputStream mOutput = null;

    private CanDriverMonitor mMonitor = null;

    private Socket mSocket;

    private String mIpAddress;

    public Wifi2Can(Context context, String ipAddress) {
        super(context);

        /* Controller service context */
        mMonitor = (CanDriverMonitor)context;

        mIpAddress = ipAddress;

        Log.d(TAG, "IP address : " + mIpAddress);

        setProduct(DEVICE_NAME);
    }

    public static boolean validateHost(String address) {

        if (address == null || address.isEmpty()) {
            return false;
        }

        try {
            Object obj = InetAddress.getByName(address);
            return obj instanceof Inet4Address;
        } catch (final UnknownHostException ex) {
            return false;
        }
    }

    @Override
    public void onException(int code) {
        /* Just forward the exception code */
        mMonitor.onException(code);
    }

    @Override
    public boolean initiate(int baudRate, int mode) {
        super.initiate(baudRate, mode);

        if (validateHost(mIpAddress) == false) {
            Log.w(TAG, "Incorrect host address found ....");
            return false;
        }

        try {
            mSocket = new Socket(mIpAddress, DEVICE_PORT);
            mOutput = mSocket.getOutputStream();
            mInput = new BufferedReader(new InputStreamReader(mSocket.getInputStream()));
        } catch (IOException e) {
            Log.w(TAG, "Problem when connecting with server " + e.toString());
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

        try {
            if (mSocket != null) {
                mSocket.close();
            }
        } catch (IOException e) {
            Log.w(TAG, "Problem when closing IP socket " + e.toString());
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


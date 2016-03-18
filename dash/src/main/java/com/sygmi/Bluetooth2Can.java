/*******************************************************************************
 * Copyright (c) 2015 Grzegorz Sygieda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 *******************************************************************************/

package com.sygmi;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;

/* This is simple BT driver that fetches hex string data from device
 * Device shall be paired using Android settings menu.
 * Only RX path is available */
public final class Bluetooth2Can extends CanDriver implements ComLink.ComLinkObserver {

    private static final String TAG = "Bluetooth2Can";

    /* Serial port profile */
    private final static UUID SSP_UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");
    public final static String DEVICE_NAME = "MYBMWDASH";

    private BluetoothAdapter mAdapter = null;
    private BluetoothSocket mSocket = null;
    private BluetoothDevice mDevice = null;

    private BufferedReader mInput = null;
    private OutputStream mOutput = null;

    private ComLink mLink = null;

    private CanDriverMonitor mMonitor = null;

    public Bluetooth2Can(Context context) {
        super(context);

        /* Controller service context */
        mMonitor = (CanDriverMonitor)context;

        mAdapter = BluetoothAdapter.getDefaultAdapter();

        setProduct(DEVICE_NAME);
    }

    private void setBluetooth(boolean enable) {

        boolean isEnabled = mAdapter.isEnabled();

        if (enable && !isEnabled) {
            mAdapter.enable();
            while (!mAdapter.isEnabled());
        }
        else if(!enable && isEnabled) {
            mAdapter.disable();
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

        if (mAdapter == null) {
            Log.w(TAG, "No bluetooth adapter found ....");
            return false;
        }

        setBluetooth(true);

        Set<BluetoothDevice> bondedDevices = mAdapter.getBondedDevices();
        if (bondedDevices.size() == 0) {
            Log.w(TAG, "No paired devices found. Please pair with any " + DEVICE_NAME);
            return false;
        }

        for (BluetoothDevice dev : bondedDevices) {
            if (dev.getName().endsWith(DEVICE_NAME)) {
                mDevice = dev;
            }
        }

        if (mDevice == null) {
            Log.w(TAG, "No " + DEVICE_NAME + " device");
            return false;
        }

        try {
            mSocket = mDevice.createRfcommSocketToServiceRecord(SSP_UUID);
            mSocket.connect();
            mOutput = mSocket.getOutputStream();
            mInput = new BufferedReader(new InputStreamReader(mSocket.getInputStream()));
        } catch (IOException e) {
            Log.w(TAG, "Problem when connecting with bluetooth device " + e.toString());
            return false;
        }

        mLink = new ComLink(this, mInput, mOutput);

        /* We skip baudrate and mode because those parameters are stored in non NVRAM in the device
         * Notice: hardware filters are used */
        mIsConnected = true;

        return true;
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
            Log.w(TAG, "Problem when closing bluetooth socket " + e.toString());
        }

        setBluetooth(false);
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


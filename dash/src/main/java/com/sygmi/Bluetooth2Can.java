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
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/* This is simple BT driver that fetches hex string data from device
 * Device shall be paired using Android settings menu. BT shall be turned on
 * Only receiving is available */
public final class Bluetooth2Can extends CanDriver {

    private static final String TAG = "Bluetooth2Can";

    /* Serial port profile */
    private final static UUID SSP_UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");
    public final static String DEVICE_NAME = "MYBMWDASH";

    private final static String FWCMD_READ_INFO = "C01\r";
    private final static String FWCMD_READ_FRAMES = "C02\r";

    private BluetoothAdapter mAdapter = null;
    private BluetoothSocket mSocket = null;
    private BluetoothDevice mDevice = null;

    private BufferedReader mInput = null;
    private OutputStream mOutput = null;

    private Thread mPollThread = null;

    private ArrayList<CanFrame> mFrames = null;

    private int mFilters = 0;

    private Lock mLock = new ReentrantLock();
    private Condition mCondition = mLock.newCondition();

    private CanDriverMonitor mMonitor = null;

    public Bluetooth2Can(Context context) {
        super(context);

        /* Controller service context */
        mMonitor = (CanDriverMonitor)context;

        mAdapter = BluetoothAdapter.getDefaultAdapter();

        setProduct(DEVICE_NAME);
    }

    @Override
    public boolean initiate(int baudRate, int mode) {
        super.initiate(baudRate, mode);

        if (mAdapter == null) {
            Log.w(TAG, "No bluetooth adapter found ....");
            return false;
        }

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
            Log.w(TAG, "No " + DEVICE_NAME + "device");
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

        /* We skip baudrate and mode because those parameters are stored in non NVRAM in the device
         * Notice: hardware filters are used */

        mFrames = new ArrayList<CanFrame>();

        mPollThread = new Thread(new PollThread());
        mPollThread.start();

        mIsConnected = true;

        return true;
    }

    @Override
    public boolean setAcceptFilter(int idMin, int idMax) {
        /* Notice: we have set correct filter mapping in the FW side
        * We have some dummy calculation here */
        int count = idMax - idMin;
        mFilters += count;
        if (count == 0)
            mFilters++;

        return true;
    }

    @Override
    public void destroy() {

        try {
            if (mSocket != null) {
                mSocket.close();
            }
        } catch (IOException e) {
            Log.w(TAG, "Problem when closing bluetooth device " + e.toString());
        }

        mLock.lock();
        mCondition.signalAll();
        mLock.unlock();

        try {
            if (mPollThread != null) {
                mPollThread.interrupt();
                mPollThread.join();
            }
        } catch (InterruptedException e) {
        }

        mIsConnected = false;
        super.destroy();
    }

    @Override
    public boolean receive(CanFrame frame) {
        boolean status = false;

        mLock.lock();
        try {
            if (mFrames.isEmpty()) {
                Log.w(TAG, "waiting ... ");
                mCondition.await();
            }

            if (!mFrames.isEmpty()) {
                Log.w(TAG, "receive ... ");
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

    private class PollThread implements Runnable {

        @Override
        public void run() {

            while (!Thread.currentThread().isInterrupted()) {

                try {
                    String cmd = FWCMD_READ_FRAMES;  // read command, firmware specific
                    mOutput.write(cmd.getBytes());
                    mOutput.flush();
                } catch (IOException e) {
                    Log.w(TAG, "Write error" + e.toString());
                    mMonitor.onException(-1);
                    break;
                }

                mLock.lock();
                try {
                    for (int i = 0; i < mFilters; i++ ) {
                        if (!mInput.ready())
                            break;

                        String data = null;
                        CanFrame frame = new CanFrame();
                        data = mInput.readLine();

                        Log.w(TAG, "RX DATA " + data);
                        Helpers.string2canframe(data, (Object) frame);
                        mFrames.add(frame);
                        mCondition.signal();
                    }
                } catch (IOException e) {
                    Log.w(TAG, "Read error" + e.toString());
                    mMonitor.onException(-1);
                    break;
                } finally {
                    mLock.unlock();
                }

                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    break;
                }

            }
        }
    }


}


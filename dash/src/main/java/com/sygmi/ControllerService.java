/*******************************************************************************
 * Copyright (c) 2015 Grzegorz Sygieda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 *******************************************************************************/

package com.sygmi;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;

public class ControllerService extends Service implements CanDriver.CanDriverMonitor,Watchdog.WatchdogMaster {

    private static final String TAG = "CANControllerService";

    public static final int DEVICE_USB = 0;
    public static final int DEVICE_WIFI = 1;
    public static final int DEVICE_BLUETOOTH = 2;
    public static final int DEVICE_FAKE = 0xFF;

    private static final int MSG_CONTROLLER_ATTACHED = 0;
    private static final int MSG_CONTROLLER_DETACHED = 1;
    private static final int MSG_CONTROLLER_DATA_RECEIVED = 2;
    private static final int MSG_CONTROLLER_TIMEOUT = 3;
    private static final int MSG_CONTROLLER_ERROR = 0xFF;

    private static final int MAX_CONNECTION_ATTEMPTS = 3;

    private final LocalBinder mBinder = new LocalBinder();

    public static final int TYPE_DEFAULT = DEVICE_USB;
    public static final int MODE_DEFAULT = CanDriver.MODE_NORMAL;
    public static final int BAUDRATE_DEFAULT = 100;  // 100kbps
    public static final int TIMEOUT_DEFAULT = 0;

    public static final String EXTRA_TYPE = "extra.TYPE";
    public static final String EXTRA_MODE = "extra.MODE";
    public static final String EXTRA_BAUDRATE = "extra.BAUDRATE";
    public static final String EXTRA_IDS = "extra.IDS";
    public static final String EXTRA_TIMEOUT = "extra.TIMEOUT";
    public static final String EXTRA_AUX_DATA = "extra.AUX_DATA";

    private IControllerObserver mObserver = null;
    private int mType = -1;
    private int mMode = -1;
    private int mBaudrate = -1;
    private int mScanPeriod = 10;  // 10ms
    private int[] mIds = null;
    private String mAuxData = null;

    private boolean mAttached = false;

    private Thread mServiceThread = null;
    private Watchdog mWatchdog = null;

    private int mTimeout = -1;

    private int mConnectAttempt = MAX_CONNECTION_ATTEMPTS;

    /* CAN device */
    private CanDriver mDevice = null;

    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {

            switch (msg.what) {
                case MSG_CONTROLLER_ATTACHED:
                    String label = (String) msg.obj;
                    Log.w(TAG, "Controller attached !");
                    mAttached = true;
                    if (mObserver != null) {
                        mObserver.onConnected(label);
                    }
                    break;
                case MSG_CONTROLLER_DETACHED:
                    Log.w(TAG, "Controller detached !");
                    if (mObserver != null) {
                        mObserver.onDisconnected();
                    }
                    mAttached = false;
                    break;
                case MSG_CONTROLLER_DATA_RECEIVED:
                    String data = (String) msg.obj;
                    //Log.w(TAG, "Controller received data: " + data);
                    if (mObserver != null && mAttached) {
                        mObserver.onDataReceived(data);
                    }
                    break;
                case MSG_CONTROLLER_TIMEOUT:
                    Log.w(TAG, "Controller timeout !");
                    if (mObserver != null) {
                        mObserver.onTimeout();
                    }
                    mAttached = false;
                    break;
                case MSG_CONTROLLER_ERROR:
                    String err = (String) msg.obj;
                    Log.w(TAG, "Controller error occured: " + err);
                    if (mObserver != null) {
                        mObserver.onError(err);
                    }
                    mAttached = false;
                    break;
            }
        }
    };

    public interface IControllerObserver {
        public void onConnected(String label);
        public void onDisconnected();
        public void onDataReceived(String event);
        public void onTimeout();
        public void onError(String error);
    }

    public int getDeviceType() {
        if (mDevice instanceof Usb2Can) {
            return DEVICE_USB;
        } else if (mDevice instanceof X2Can) {
            return DEVICE_WIFI;
        } else if (mDevice instanceof Bluetooth2Can) {
            return DEVICE_BLUETOOTH;
        } else if (mDevice instanceof FakeDevice) {
            return DEVICE_FAKE;
        } else {
            return -1;
        }
    }

    public int getMode() {
        return mMode;
    }

    public boolean canConnect() {
        return mConnectAttempt > 0;
    }  // not used

    public class LocalBinder extends Binder {
        public ControllerService getService() {
            return ControllerService.this;
        }
    }

    public boolean isAttached() {
        return mAttached;
    }

    public void registerObserver(IControllerObserver observer) {
        if (mObserver != null) {
            return;
        }
        mObserver = observer;
    }

    public void setScanPeriod(int scanPeriod) {
        if (scanPeriod > 10) {  // prevent too small values
            mScanPeriod = scanPeriod;
        }
    }

    @Override
    public void onCreate() {

        super.onCreate();

        mServiceThread = new Thread(new ServiceThread());
    }

    public void startPoll() {

        Log.w(TAG, "Starting service poll thread !");
        mWatchdog = new Watchdog(mTimeout);
        mWatchdog.setMaster(this);

        if (mServiceThread.getState() == Thread.State.NEW) {
            mServiceThread.start();
        }
    }

    public void stopPoll() {
        stopSelf();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        if (intent == null) {
            return START_NOT_STICKY;
        }

        mType = intent.getIntExtra(EXTRA_TYPE, TYPE_DEFAULT);
        mMode = intent.getIntExtra(EXTRA_MODE, MODE_DEFAULT);
        mBaudrate = intent.getIntExtra(EXTRA_BAUDRATE, BAUDRATE_DEFAULT);
        mIds = intent.getIntArrayExtra(EXTRA_IDS);
        mTimeout = intent.getIntExtra(EXTRA_TIMEOUT, TIMEOUT_DEFAULT);
        mAuxData = intent.getStringExtra(EXTRA_AUX_DATA);

        Log.w(TAG, "Starting service");

        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {

        Log.w(TAG, "Stopping service");

        mDevice.destroy();

        try {
            mServiceThread.interrupt();
            mServiceThread.join();
        } catch (InterruptedException e) {
        }

        mDevice = null;
        mWatchdog.hug();

        super.onDestroy();

        Log.w(TAG, "Service stopped !");
    }

    @Override
    public IBinder onBind(Intent dummy) {
        return mBinder;
    }

    @Override
    public void onException(int code) {
        Log.w(TAG, "Got device exception:  " + code);
        String reason = "Device exception occured !";
        mHandler.obtainMessage(MSG_CONTROLLER_ERROR, reason).sendToTarget();
    }

    @Override
    public void onHauu() {
        mHandler.obtainMessage(MSG_CONTROLLER_TIMEOUT).sendToTarget();
        Log.w(TAG, "Got watchdog bark !");
    }

    private class ServiceThread implements Runnable {

        @Override
        public void run() {

            boolean status;

            switch (mType) {
                case DEVICE_USB:
                    mDevice = new Usb2Can(ControllerService.this);
                    break;
                case DEVICE_WIFI:
                    mDevice = new Wifi2Can(ControllerService.this, mAuxData);
                    //mDevice = new X2Can(ControllerService.this);
                    break;
                case DEVICE_BLUETOOTH:
                    mDevice = new Bluetooth2Can(ControllerService.this);
                    break;
                default:
                case DEVICE_FAKE:
                    mDevice = new FakeDevice(ControllerService.this);
                    break;
            }

            status = mDevice.initiate(mBaudrate, mMode);
            mWatchdog.giveMeat(mTimeout);

            if (status) {
                String label = mDevice.getProduct();

                mHandler.obtainMessage(MSG_CONTROLLER_ATTACHED, label).sendToTarget();
            } else {
                String reason = "Problem when connecting to device !";
                mHandler.obtainMessage(MSG_CONTROLLER_ERROR, reason).sendToTarget();
                return;
            }

            for (int i = 0; i < mIds.length; i++) {
                Log.w(TAG, "Accepting filter for id " + String.format("%X", mIds[i]));
                mDevice.setAcceptFilter(mIds[i], mIds[i]);
            }

            CanDriver.CanFrame frame = new CanDriver.CanFrame();

            while (!Thread.currentThread().isInterrupted()) {
                // start HW and loop for rx data
                boolean lastStatus = true;

                mWatchdog.giveMeat(mTimeout);
                status = mDevice.receive(frame);

                // skip rtr type frames
                if ((frame.info & CanDriver.FRAME_RTR) == CanDriver.FRAME_RTR) {
                    status = false;
                    lastStatus = true;
                }

                // we should not never get 0 length frame !
                if ((frame.info & CanDriver.FRAME_DLC) == 0) {
                    status = false;
                }

                if (status) {
                    String hexFrame = Helpers.frame2hex(frame.id, frame.data,
                            frame.info & CanDriver.FRAME_DLC);

                    mHandler.obtainMessage(MSG_CONTROLLER_DATA_RECEIVED, hexFrame).sendToTarget();
                } else if (!lastStatus) {
                    mHandler.obtainMessage(MSG_CONTROLLER_TIMEOUT).sendToTarget();
                }

                lastStatus = status;

                try {
                    Thread.sleep(mScanPeriod);
                } catch (InterruptedException e) {
                    break;
                }
            }

            mHandler.obtainMessage(MSG_CONTROLLER_DETACHED).sendToTarget();
        }
    }

}

/*******************************************************************************
 * Copyright (c) 2015 Grzegorz Sygieda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 *******************************************************************************/

package com.sygmi;

import android.app.Service;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
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

    public static final String STR_DEVICE_USB = "USB";
    public static final String STR_DEVICE_WIFI = "WIFI";
    public static final String STR_DEVICE_BLUETOOTH = "BLUETOOTH";
    public static final String STR_DEVICE_FAKE = "FAKE";

    private static final int MSG_CONTROLLER_ATTACHED = 0;
    private static final int MSG_CONTROLLER_DETACHED = 1;
    private static final int MSG_CONTROLLER_DATA_RECEIVED = 2;
    private static final int MSG_CONTROLLER_TIMEOUT = 3;
    private static final int MSG_CONTROLLER_DISCOVERED = 4;
    private static final int MSG_CONTROLLER_ERROR = 0xFF;

    private static final int ATTACH_TIMEOUT = 10 * 1000;  // 10sec
    private static final int GUARD_TIMEOUT = 3 * 1000;  // 3sec

    private static final int MAX_CONNECTION_ATTEMPTS = 3;

    private final LocalBinder mBinder = new LocalBinder();

    public static final int TYPE_DEFAULT = DEVICE_USB;
    public static final int MODE_DEFAULT = CanDriver.MODE_NORMAL;
    public static final int BAUDRATE_DEFAULT = 100;  // 100kbps

    public static final String EXTRA_TYPE = "extra.TYPE";
    public static final String EXTRA_MODE = "extra.MODE";
    public static final String EXTRA_BAUDRATE = "extra.BAUDRATE";
    public static final String EXTRA_IDS = "extra.IDS";

    private IControllerObserver mObserver = null;
    private int mType = -1;
    private int mMode = -1;
    private int mBaudrate = -1;
    private int mScanPeriod = 10;  // 10ms
    private int[] mIds = null;

    private boolean mAttached = false;

    private Thread mServiceThread = null;
    private Watchdog mWatchdog = null;

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
                case MSG_CONTROLLER_DISCOVERED:
                    String type = (String) msg.obj;
                    Log.w(TAG, "Controller device discovered occured " + type);
                    if (mObserver != null) {
                        if (type.equals(STR_DEVICE_USB)) {
                            mObserver.onDiscovered(DEVICE_USB);
                        } else if (type.equals(STR_DEVICE_WIFI)) {
                            mObserver.onDiscovered(DEVICE_WIFI);
                        } else if (type.equals(STR_DEVICE_BLUETOOTH)) {
                            mObserver.onDiscovered(DEVICE_BLUETOOTH);
                        } else if (type.equals(STR_DEVICE_FAKE)) {
                            mObserver.onDiscovered(DEVICE_FAKE);
                        } else {
                            mObserver.onDiscovered(-1);
                        }
                    }
                    break;
            }
        }
    };

    public interface IControllerObserver {
        public void onConnected(String label);
        public void onDisconnected();
        public void onDataReceived(String event);
        public void onTimeout();
        public void onDiscovered(int deviceType);
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

        mServiceThread = new Thread(new ServiceThread());
    }

    public void startPoll() {

        Log.w(TAG, "Starting service poll thread !");
        mWatchdog = new Watchdog(ATTACH_TIMEOUT); // 10 sec for attaching process
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

        IntentFilter devFilter = new IntentFilter();
        devFilter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        devFilter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        devFilter.addAction(BluetoothDevice.ACTION_FOUND);
        devFilter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        devFilter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        devFilter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);

        registerReceiver(mDeviceStateReceiver, devFilter);

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
        unregisterReceiver(mDeviceStateReceiver);

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
                    mDevice = new X2Can(ControllerService.this);
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
            mWatchdog.giveMeat(GUARD_TIMEOUT);

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

                mWatchdog.giveMeat(GUARD_TIMEOUT);
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

    private final BroadcastReceiver mDeviceStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
                UsbDevice usbDevice = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (usbDevice != null) {
                    Log.d(TAG, "USB device attached");
                    mHandler.obtainMessage(MSG_CONTROLLER_DISCOVERED, STR_DEVICE_USB).sendToTarget();
                }
            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                UsbDevice usbDevice = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (usbDevice != null) {
                    Log.d(TAG, "USB device detached");
                    //stopCanService(); // shall be already in stopping state but lets do stop one more time
                }
            } else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
                BluetoothDevice btDevice = (BluetoothDevice) intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (btDevice != null) {
                    Log.d(TAG, "Bluetoooth dettached");
                    if (btDevice.getName() != null &&
                            btDevice.getName().compareTo(Bluetooth2Can.DEVICE_NAME) == 0) {
                        //if (mConnection!= null && mConnection.)
                        //startCanService();
                    }
                }
            } else if (WifiManager.NETWORK_STATE_CHANGED_ACTION.equals(action)) {
                NetworkInfo info = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
                if (info.isConnected()){
                    Log.d(TAG, "Wifi device attached");
                    // TODO: check if wifi name equals something reasonable
                    //startCanService();
                } else{
                    Log.d(TAG, "Wifi device dettached");
                    //stopCanService(); // shall be already in stopping state but lets do stop one more time
                }
            }
        }
    };
}

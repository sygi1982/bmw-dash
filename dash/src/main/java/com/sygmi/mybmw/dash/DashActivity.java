/*******************************************************************************
 * Copyright (c) 2015 Grzegorz Sygieda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 *******************************************************************************/

package com.sygmi.mybmw.dash;

import com.sygmi.BMWSniffer;
import com.sygmi.ControllerService;
import com.sygmi.EndpointStateService;
import com.sygmi.FaderEffect;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import org.codeandmagic.android.gauge.GaugeView;

public class DashActivity extends Activity implements ControllerService.IControllerObserver {

    private static final String TAG = DashActivity.class.getSimpleName();

    private final static int DEFAULT_VISUAL_DELAY = 1000;  // ms

    private static final int SETTINGS_RESULT = 1;

    private TextView mRPMText = null;
    private TextView mSpeedText = null;
    private TextView mEngineTempText = null;
    private GaugeView mSpeedGauge = null;
    private FaderView mRpmFaderView = null;

    private View mStatusView = null;

    private ControllerService mControllerService = null;
    private BMWSniffer mSniffer = null;

    private RPMTasker mRPMTasker = new RPMTasker();
    private SpeedTasker mSpeedTasker = new SpeedTasker();
    private EngineTempTasker mEngineTempTasker = new EngineTempTasker();

    private boolean mConnected = false;
    private int mConnectionType = -1;
    private boolean mStartDemo = false;
    private int mRefreshRate = -1;
    private int mEndpointTimeout = -1;
    private String mWifiIpAddress = null;

    private boolean mIsVisible = false;

    private BroadcastReceiver mLocalReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            String action = intent.getAction();

            if (action.equals(Intent.ACTION_SCREEN_OFF)) {
                Log.d(TAG, "SCREEN OFF");
                stopCanService();
            } else if (action.equals(Intent.ACTION_USER_PRESENT)) {
                Log.d(TAG, "SCREEN ON");
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        startCanService();
                    }
                }, 500);
            } else if (action.equals(EndpointStateService.ENDPOINT_DISCOVERED)) {
                String endpoint = intent.getStringExtra(EndpointStateService.ENDPOINT_TYPE);
                if (mapDevType2String(mConnectionType).equals(endpoint)) {
                    showPopup("Endpoint discovered: " + endpoint);
                    startCanService();
                }
            } else if (action.equals(EndpointStateService.ENDPOINT_LOST)) {
                String endpoint = intent.getStringExtra(EndpointStateService.ENDPOINT_TYPE);
                if (mapDevType2String(mConnectionType).equals(endpoint)) {
                    showPopup("Endpoint lost: " + endpoint);
                    stopCanService();
                }
            }
        }
    };

    private final ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            mControllerService = ((ControllerService.LocalBinder) service).getService();

            Log.w(TAG, "Connected to Controller service !");

            if (mControllerService != null) {
                mControllerService.registerObserver(DashActivity.this);
                mControllerService.startPoll();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName className) {
            mControllerService = null;

            Log.w(TAG, "Disconnected from Controller service !");
            mConnected = false;
        }
    };

    private class UiTasker implements FaderEffect.IFaderObserver {

        FaderEffect mActualFader = null;
        int lastValue = 0;

        public void setFader(FaderEffect f) {
            if (mActualFader != null) {
                mActualFader.stop();
                mActualFader = null;
            }
            // update fader
            mActualFader = f;
        }

        FaderEffect getFader() {
            return mActualFader;
        }

        public int getLastValue() {
            return lastValue;
        }

        @Override
        public void onStep(final int val) {
            lastValue = val;
        }

        @Override
        public void onFinish(final int val) {
            lastValue = val;
            setFader(null);
        }
    }

    private class RPMTasker extends UiTasker {

        @Override
        public void onStep(final int val) {
            //Log.w(TAG, "New rpm step value " + val);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mRPMText.setText(String.valueOf(val) + " [rpm]");
                    mRpmFaderView.setValue(val);
                }
            });
            super.onStep(val);
        }
    }

    private class SpeedTasker extends UiTasker {

        @Override
        public void onStep(final int val) {
            //Log.w(TAG, "New speed step value " + val);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mSpeedText.setText(String.valueOf(val) + " [kmh]");
                    mSpeedGauge.setTargetValue((float) val);
                }
            });
            super.onStep(val);
        }
    }

    private class EngineTempTasker extends UiTasker {

        @Override
        public void onStep(final int val) {
            //Log.w(TAG, "New engine temperature value " + val);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mEngineTempText.setText(String.valueOf(val) + " [C]");
                }
            });
            super.onStep(val);
        }
    }

    private void resetVisualControls() {
        FaderEffect f;

        f = new FaderEffect(mRPMTasker, mRPMTasker.getLastValue(), 0, DEFAULT_VISUAL_DELAY);
        mRPMTasker.setFader(f);
        f = new FaderEffect(mSpeedTasker, mSpeedTasker.getLastValue(), 0, DEFAULT_VISUAL_DELAY);
        mSpeedTasker.setFader(f);
        f = new FaderEffect(mEngineTempTasker, mEngineTempTasker.getLastValue(), 0, DEFAULT_VISUAL_DELAY);
        mEngineTempTasker.setFader(f);
    }

    private void restoreVisualControls() {
        FaderEffect f;

        f = mRPMTasker.getFader();
        if (f == null) {
            f = new FaderEffect(mRPMTasker, mRPMTasker.getLastValue(), mRPMTasker.getLastValue(), DEFAULT_VISUAL_DELAY);
            mRPMTasker.setFader(f);
        } else {
            mRPMTasker.onStep(mRPMTasker.getLastValue());
        }
        f = mSpeedTasker.getFader();
        if (f == null) {
            f = new FaderEffect(mSpeedTasker, mSpeedTasker.getLastValue(), mSpeedTasker.getLastValue(), DEFAULT_VISUAL_DELAY);
            mSpeedTasker.setFader(f);
        } else {
            mSpeedTasker.onStep(mSpeedTasker.getLastValue());
        }
        f = mEngineTempTasker.getFader();
        if (f == null) {
            f = new FaderEffect(mEngineTempTasker, mEngineTempTasker.getLastValue(), mEngineTempTasker.getLastValue(), DEFAULT_VISUAL_DELAY);
            mEngineTempTasker.setFader(f);
        } else {
            mEngineTempTasker.onStep(mEngineTempTasker.getLastValue());
        }
    }

    private void startDemo4VisualControls() {
        FaderEffect f;

        f = new FaderEffect(mRPMTasker, mRPMTasker.getLastValue(), BMWSniffer.MAX_RPM, DEFAULT_VISUAL_DELAY);
        mRPMTasker.setFader(f);
        f = new FaderEffect(mSpeedTasker, mSpeedTasker.getLastValue(), BMWSniffer.MAX_SPEED, DEFAULT_VISUAL_DELAY);
        mSpeedTasker.setFader(f);
        f = new FaderEffect(mEngineTempTasker, mEngineTempTasker.getLastValue(), BMWSniffer.MAX_ENGINE_TEMP, DEFAULT_VISUAL_DELAY);
        mEngineTempTasker.setFader(f);

        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                resetVisualControls();
            }
        }, DEFAULT_VISUAL_DELAY * 2);
    }

    @Override
    public void onConnected(String label) {
        showPopup("CAN Controller connected: " + label);
        mSniffer = new BMWSniffer();
        mSniffer.setSnooper(new BMWSniffer.IBMWSnooper() {

            @Override
            public void onEngineRPMUpdated(int oldVal, int newVal) {
                Log.w(TAG, "Got new rpm from sniffer " + newVal);
                // make 1000ms fading
                if (newVal >= 0) {
                    FaderEffect f = new FaderEffect(mRPMTasker, oldVal, newVal, DEFAULT_VISUAL_DELAY);
                    mRPMTasker.setFader(f);  // store fader reference
                }
            }

            public void onVehicleSpeedUpdated(int oldVal, int newVal) {
                Log.w(TAG, "Got new speed from sniffer " + newVal);
                // make 1000ms
                if (newVal >= 0) {
                    FaderEffect f = new FaderEffect(mSpeedTasker, oldVal, newVal, DEFAULT_VISUAL_DELAY);
                    mSpeedTasker.setFader(f);  // store fader reference
                }
            }

            public void onParkDistanceChanged(int pdcType, byte sensor1, byte sensor2, byte sensor3, byte sensor4) {
                Log.w(TAG, "Got new PDC values " + sensor1 + " " + sensor2 + " " + sensor3 + " " + sensor4);
                // TODO: switch to PDC fragment
            }

            public void onEngineTemperatureUpdated(int oldVal, int newVal) {
                Log.w(TAG, "Got new engine temperature from sniffer " + newVal);
                // make 1000ms
                if (newVal >= 0) {
                    FaderEffect f = new FaderEffect(mEngineTempTasker, oldVal, newVal, DEFAULT_VISUAL_DELAY);
                    mEngineTempTasker.setFader(f);  // store fader reference
                }
            }

            public void onSteeringWheelInputTriggered(int type) {
                Log.w(TAG, "Got MFL input event " + type);
                if (type == BMWSniffer.MFL_TYPE_PHONE) {
                    TelephonyManager telManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
                    if (telManager.getCallState() == TelephonyManager.CALL_STATE_RINGING) {
                        /* There is lot of different approaches to answer incoming a phone call
                         * however not working with different android versions
                         * so use auto answer option when BT headset is connected */

                        // do nothing
                    }
                }
            }

            @Override
            public void onDebug(String info) {
                Log.w(TAG, "[Debug] " + info);
            }

            @Override
            public void onError(String error) {
                Log.w(TAG, "Got sniffer error: " + error);
            }

        });

        mStatusView.setBackgroundColor(Color.GREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    public void onDisconnected() {
        showPopup("CAN Controller disconnected !");
        mSniffer = null;
        mStatusView.setBackgroundColor(Color.RED);
        resetVisualControls();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    public void onDataReceived(String data) {
        //Log.w(TAG, "CAN Controller data received !");
        try {
            mSniffer.sniff(data);
        } catch (BMWSniffer.BMWSnifferException excp) {
            Log.w(TAG, "Sniffer exception : " + excp.getReason());
        }
    }

    @Override
    public void onTimeout() {
        showPopup("CAN Controller timeout - connection with bus lost !");
        stopCanService();
    }

    @Override
    public void onError(String error) {
        showPopup("CAN Controller unexpected error: " + error);
        stopCanService();
    }

    private void startCanService() {

        if (mConnected ==  true || mIsVisible == false)
            return;

        Intent startIntent = new Intent(DashActivity.this, ControllerService.class);
        startIntent.putExtra(ControllerService.EXTRA_TYPE, mConnectionType);
        startIntent.putExtra(ControllerService.EXTRA_BAUDRATE, ControllerService.BAUDRATE_DEFAULT);
        startIntent.putExtra(ControllerService.EXTRA_MODE, ControllerService.MODE_DEFAULT);
        startIntent.putExtra(ControllerService.EXTRA_IDS, BMWSniffer.getIds());
        startIntent.putExtra(ControllerService.EXTRA_TIMEOUT, mEndpointTimeout);
        if (mConnectionType == ControllerService.DEVICE_WIFI) {
            startIntent.putExtra(ControllerService.EXTRA_AUX_DATA, mWifiIpAddress);
        }
        if (mConnectionType == ControllerService.DEVICE_BLUETOOTH) {
            startIntent.putExtra(ControllerService.EXTRA_AUX_DATA,
                    String.valueOf(BluetoothAdapter.getDefaultAdapter().isEnabled()));
        }
        startService(startIntent);
        bindService(startIntent, mConnection, Context.BIND_AUTO_CREATE);
        mConnected = true;
    }

    private void stopCanService() {

        if (mConnected == false)
            return;

        mControllerService = null;
        Intent stopIntent = new Intent(DashActivity.this, ControllerService.class);
        stopService(stopIntent);
        unbindService(mConnection);
        mConnected = false;
    }

    private String mapDevType2String(int type) {
        String result;
        switch (type) {
            default:
            case ControllerService.DEVICE_USB:
                result = EndpointStateService.DEVICE_USB;
                break;
            case ControllerService.DEVICE_WIFI:
                result = EndpointStateService.DEVICE_WIFI;
                break;
            case ControllerService.DEVICE_BLUETOOTH:
                result = EndpointStateService.DEVICE_BLUETOOTH;
                break;
            case ControllerService.DEVICE_FAKE:
                result = EndpointStateService.DEVICE_FAKE;
                break;
        }

        return result;
    }

    private void updateStatusIndicator(int type) {
        String status = mapDevType2String(type);

        ((TextView) mStatusView).setText(status);

        if (mControllerService != null && mControllerService.isAttached()) {
            mStatusView.setBackgroundColor(Color.GREEN);
        } else {
            mStatusView.setBackgroundColor(Color.RED);
        }
    }

    private void setupWidgets() {
        mSpeedGauge = (GaugeView) findViewById(R.id.gauge_view);
        mRpmFaderView = (FaderView) findViewById(R.id.faderView);
        mRpmFaderView.setMaxRange(BMWSniffer.MAX_RPM);
        mRPMText = (TextView) findViewById(R.id.rpm_content);
        mSpeedText = (TextView) findViewById(R.id.speed_content);
        mEngineTempText = (TextView) findViewById(R.id.enginetemp_content);
        mStatusView = findViewById(R.id.connection_status);
        mStatusView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startCanService();
            }
        });
    }

    private void getPrefs() {
        // Restore preferences
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        mConnectionType = Integer.parseInt(sharedPrefs.getString(SettingsActivity.ATTR_DEV_TYPE,
                SettingsActivity.ATTR_DEV_TYPE_DEFAULT_VAL));
        mStartDemo = sharedPrefs.getBoolean(SettingsActivity.ATTR_START_DEMO, false);
        mRefreshRate = Integer.parseInt(sharedPrefs.getString(SettingsActivity.ATTR_REFRESH_RATE,
                SettingsActivity.ATTR_REFRESH_RATE_DEFAULT_VAL));
        mEndpointTimeout = Integer.parseInt(sharedPrefs.getString(SettingsActivity.ATTR_ENDPOINT_TIMEOUT,
                SettingsActivity.ATTR_ENDPOINT_TIMEOUT_DEFAULT_VAL));
        mWifiIpAddress = sharedPrefs.getString(SettingsActivity.ATTR_WIFI_ENDPOINT_ADDR,
                SettingsActivity.ATTR_WIFI_ENDPOINT_ADDR_DEFAULT_VAL);
    }

    private void showPopup(String text) {
        Log.d(TAG, text);
        Toast.makeText(getApplicationContext(), text, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");

        setContentView(R.layout.activity_main);

        // hide action bar components
        getActionBar().setDisplayShowHomeEnabled(false);
        getActionBar().setDisplayShowTitleEnabled(false);

        setupWidgets();
        getPrefs();

        mIsVisible = true;

        updateStatusIndicator(mConnectionType);
        if (mStartDemo) {
            startDemo4VisualControls();
        } else {
            startCanService();  // try to start service
        }

        IntentFilter filter = new IntentFilter(Intent.ACTION_USER_PRESENT);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(EndpointStateService.ENDPOINT_DISCOVERED);
        filter.addAction(EndpointStateService.ENDPOINT_LOST);
        registerReceiver(mLocalReceiver, filter);

        Intent intent = new Intent(getApplicationContext(), EndpointStateService.class);
        startService(intent);
    }

    @Override
    protected void onDestroy() {
        // only called when app is killed
        stopCanService();
        Log.d(TAG, "onDestroy");
        unregisterReceiver(mLocalReceiver);
        super.onDestroy();
    }

    @Override
    protected void onPause() {
        Log.d(TAG, "onPause");
        mIsVisible = false;
        super.onPause();
    }

    @Override
    protected void onResume() {
        Log.d(TAG, "onResume");
        mIsVisible = true;
        super.onResume();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig)
    {
        Log.d(TAG, "onConfigurationChanged");
        setContentView(R.layout.activity_main);
        setupWidgets();
        updateStatusIndicator(mConnectionType);
        restoreVisualControls();
        super.onConfigurationChanged(newConfig);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        new MenuInflater(this).inflate(R.menu.options_menu, menu);
        return (super.onCreateOptionsMenu(menu));
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {
            case R.id.settings_menu:
                Intent i = new Intent(getApplicationContext(), SettingsActivity.class);
                startActivityForResult(i, SETTINGS_RESULT);
                break;
        }

        return false;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == SETTINGS_RESULT) {
            getPrefs();
            updateStatusIndicator(mConnectionType);
            stopCanService();
        }
    }
}


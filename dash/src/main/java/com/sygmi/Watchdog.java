/*******************************************************************************
 * Copyright (c) 2015 Grzegorz Sygieda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 *******************************************************************************/

package com.sygmi;

import android.os.Handler;
import android.os.Message;

public class Watchdog {

    private final int MSG_ANGRY = 0xDEADBEEF;
    private int mRest = -1;
    private WatchdogMaster mMaster = null;

    public interface WatchdogMaster {
        public void onHauu();
    }

    private final Handler mWatcher = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            if (msg.what == MSG_ANGRY) {
                if (mMaster!= null) {
                    mMaster.onHauu();
                }                   
            }
        }

    };

    public Watchdog(int rest) {
        mRest = rest;
    }

    public void setMaster(WatchdogMaster master) {
        if (mMaster != null)
            return;
        mMaster = master;
    }

    public void poke() {
        Message angry = mWatcher.obtainMessage(MSG_ANGRY);
        mWatcher.sendMessageDelayed(angry, mRest);
    }

    public void hug() {
        mWatcher.removeMessages(MSG_ANGRY);
    }
    public void giveMeat(int rest) {

        if (rest > 0) {
            mRest = rest;
        } 
        
        hug();
        poke();
    }

}

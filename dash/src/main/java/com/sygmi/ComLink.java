/*******************************************************************************
 * Copyright (c) 2015 Grzegorz Sygieda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 *******************************************************************************/

package com.sygmi;

import android.util.Log;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/* This is simple communication link driver that fetches hex string data from custom device
 * Only RX path is available */
public final class ComLink {

    private static final String TAG = "ComLink";

    private final static String FWCMD_READ_INFO = "C01\r";  // not used for now
    private final static String FWCMD_READ_FRAMES = "C02\r";

    private BufferedReader mInput = null;
    private OutputStream mOutput = null;

    private Thread mPollThread = null;

    private ArrayList<CanDriver.CanFrame> mFrames = null;

    private int mFilters = 0;

    private Lock mLock = new ReentrantLock();
    private Condition mCondition = mLock.newCondition();

    private ComLinkObserver mObserver = null;

    public interface ComLinkObserver {
        public void onException(int code);
    }

    public ComLink(ComLinkObserver observer, BufferedReader input, OutputStream output) {

        mObserver = observer;
        mInput = input;
        mOutput = output;

        mFrames = new ArrayList<CanDriver.CanFrame>();

        mPollThread = new Thread(new PollThread());
        mPollThread.start();
    }

    public void destroy() {

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
    }

    public boolean receive(CanDriver.CanFrame frame) {
        boolean status = false;

        mLock.lock();
        try {
            if (mFrames.isEmpty()) {
                Log.w(TAG, "waiting ... ");
                mCondition.await();
            }

            if (!mFrames.isEmpty()) {
                Log.w(TAG, "receive ... ");
                CanDriver.CanFrame tmp = mFrames.remove(0);
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
                    mObserver.onException(-1);
                    break;
                }

                mLock.lock();
                try {
                    for (int i = 0; i < mFilters; i++ ) {
                        if (!mInput.ready())
                            break;

                        String data = null;
                        CanDriver.CanFrame frame = new CanDriver.CanFrame();
                        data = mInput.readLine();

                        Log.w(TAG, "RX DATA " + data);
                        Helpers.string2canframe(data, (Object) frame);
                        mFrames.add(frame);
                        mCondition.signal();
                    }
                } catch (IOException e) {
                    Log.w(TAG, "Read error" + e.toString());
                    mObserver.onException(-1);
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

    public boolean setFiltering(int idMin, int idMax) {
        /* Notice: we have set correct filter mapping in the FW side
        * We have some dummy calculation here */
        int count = idMax - idMin;
        mFilters += count;
        if (count == 0)
            mFilters++;

        return true;
    }
}


/*******************************************************************************
 * Copyright (c) 2015 Grzegorz Sygieda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 *******************************************************************************/

package com.sygmi;

import android.content.Context;

public abstract class CanDriver {

    protected String mProduct = null;
    protected String mFirmwareVersion = null;
    protected String mLibraryVersion = null;
    protected String mDeviceSN = null;
    protected String mUniqueID = null;

    protected Context mContext = null;

    protected boolean mIsConnected = false;

    public final static int MODE_NORMAL = 0;
    public final static int MODE_LISTEN_ONLY = 2;
    public final static int MODE_LOOPBACK = 4;

    // can message frame info flags
    public final static byte FRAME_NORET      = (byte)(0x80);  /* No retransmission */
    public final static byte FRAME_EXT        = (byte)(0x20);  /* Extended frame format */
    public final static byte FRAME_STD        = (byte)(0x00);  /* Standard frame format */
    public final static byte FRAME_RTR        = (byte)(0x10);  /* Remote transmission request*/ 
    public final static byte FRAME_DLC        = (byte)(0xF);   /* DLC*/

    public final static int FILTER_OUT = 0;
    public final static int FILTER_IN = 1;

    public final static int INGRESS = 0x1;
    public final static int EGRESS = 0x2;

    public interface CanDriverMonitor {
        public void onException(int code);
    }

    public CanDriver(Context context) {
        mContext = context;
    }

    // called from JNI
    protected void setProduct(String str) {
        mProduct = str;
    }

    public String getProduct() {
        return mProduct;
    }

    // called from JNI
    protected void setFirmwareVersion(String str) {
        mFirmwareVersion = str;
    }

    public String getFirmwareVersion() {
        return mFirmwareVersion;
    }

    // called from JNI
    protected void setLibraryVersion(String str) {
        mLibraryVersion = str;
    }

    public String getLibraryVersion() {
        return mLibraryVersion;
    }

    // called from JNI
    protected void setDeviceSN(String str) {
        mDeviceSN = str;
    }

    public String getDeviceSN() {
        return mDeviceSN;
    }

    // called from JNI
    protected void setUniqueId(String str) {
        mUniqueID = str;
    }

    public String getUniqueId() {
        return mUniqueID;
    }

    public boolean initiate(int baudRate, int mode) {
        return false;
    }

    public void destroy() {
        mProduct = null;
        mFirmwareVersion = null;
        mLibraryVersion = null;
        mDeviceSN = null;
        mUniqueID = null;
    }

    public boolean send(CanFrame frame) {
        return false;
    }

    public boolean receive(CanFrame frame) {
        return false;
    }

    public boolean setAcceptFilter(int idMin, int idMax) {
        return false;
    }

    public boolean setRejectFilter(int idMin, int idMax) {
        return false;
    }

    public boolean wipe(int what) {
        return false;
    }

    public static class CanFrame {

        final static int MAX_DATA_SIZE = 8;

        public int id = 0;
        public byte info = 0;
        public byte[] data = new byte[MAX_DATA_SIZE];
	    public int timeStamp = 0;

	    public CanFrame() {

	    }

        public CanFrame(CanFrame frame) {

            clone(frame);
        }

        public void clone(CanFrame frame) {

            this.id = frame.id;
            this.info = frame.info;
            System.arraycopy(frame.data, 0, this.data, 0, frame.info & FRAME_DLC);
            this.timeStamp = frame.timeStamp;
        }

    }

}

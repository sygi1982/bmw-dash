/*******************************************************************************
 * Copyright (c) 2015 Grzegorz Sygieda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 *******************************************************************************/

package com.sygmi;

import java.util.HashMap;
import java.util.Map;

public class BMWSniffer {

    public final static int MAX_RPM = 5000;
    public final static int MAX_SPEED = 260;
    public final static int MAX_ENGINE_TEMP = 150;

    public final static int PDC_TYPE_FRONT = 0;
    public final static int PDC_TYPE_REAR = 1;

    public final static int MFL_TYPE_PHONE = 0;

    private IBMWSnooper mSnooper = null;

    private Map<String, Sniffer> mEvents = new HashMap<String, Sniffer>();

    // ids supported
    private static final int[] ids = new int[]{ 0xAA,    // RPM
                                                0x1B4,   // Speed
                                                0x1C2,   // PDC (Park Distance Control)
                                                0x1D0,   // Engine temperature
                                                0x1D6 }; // MFL (Steering Wheel)

    public static int[] getIds() {
        return ids;
    }

    public BMWSniffer() {

        mEvents.put(String.format("%X", ids[0]), new EngineRPM());
        mEvents.put(String.format("%X", ids[1]), new VehicleSpeed());
        mEvents.put(String.format("%X", ids[2]), new ParkDistanceControl());
        mEvents.put(String.format("%X", ids[3]), new EngineTemperature());
        mEvents.put(String.format("%X", ids[4]), new SteeringWheelInput());
    }

    public void setSnooper(IBMWSnooper snooper) {
        if (mSnooper != null) {
            return;
        }
        mSnooper = snooper;
    }

    private abstract class Sniffer {

        protected int mValue = -1;

        abstract void job(long data, int len);
    }   

    public void sniff(String hex) throws BMWSnifferException {

        if (mSnooper == null) {
            throw new BMWSnifferException("Snooper not set!");
        }

        Helpers.FrameBundle fb = new Helpers.FrameBundle();

        boolean status = Helpers.hex2frame(hex, fb);
        if (!status) {
            mSnooper.onError("error when parsing hex: " + hex);
            return;
        }

        Sniffer sniffer = mEvents.get(String.format("%X", fb.id));

        if (sniffer != null) {
            sniffer.job(fb.data, fb.len);

        } else {
            mSnooper.onError("id not mapped " + fb.id);
        }
    }

    private class EngineRPM extends Sniffer {

        @Override
        public void job(long data, int len) {

            if (len != 8) {
                mSnooper.onError("len for rpm id do not match, should be 8 but received " + len);
                return;
            }

            mSnooper.onDebug("debug rpm hex " + String.format("%X", (data >> 16) & 0xFFFF));
            short value = (short)((data >> 16) & (long)0xFFFF);

            value = Short.reverseBytes(value);
            value /=4;

            if (value >= 0 && value < MAX_RPM && value != mValue) {
                mSnooper.onEngineRPMUpdated(mValue, value);
                mValue = value;
            }
        }
    }

    private class VehicleSpeed extends Sniffer {

        @Override
        public void job(long data, int len) {

            if (len != 8) {
                mSnooper.onError("len for speed id do not match, should be 8 but received " + len);
                return;
            }

            mSnooper.onDebug("debug speed hex " + String.format("%X", (data >> 48) & 0xFFFF));
            short value = (short)((data >> 48) & (long)0xFFFF);
            value -= 192;
            value = Short.reverseBytes(value);
            value /= 16;
            value *= 1.6f;  // conversion mph to kmh

            if (value >= 0 && value < MAX_SPEED && value != mValue) {
                mSnooper.onVehicleSpeedUpdated(mValue, value);
                mValue = value;
            }
        }
    }

    private class ParkDistanceControl extends Sniffer {

        @Override
        public void job(long data, int len) {

            if (len != 8) {
                mSnooper.onError("len for PDC id do not match, should be 8 but received " + len);
                return;
            }

            mSnooper.onDebug("debug PDC hex " + String.format("%X", data));
            // only rear value for now
            int value = (int)(data & (long)0xFFFFFFFF);

            byte sensor1 = (byte)((data >> 24) & 0xFF);
            byte sensor2 = (byte)((data >> 24) & 0xFF);
            byte sensor3 = (byte)((data >> 8) & 0xFF);
            byte sensor4 = (byte)(data & 0xFF);

            if (value != mValue) {
                mSnooper.onParkDistanceChanged(PDC_TYPE_REAR, sensor1, sensor2, sensor3, sensor4);
                mValue = value;
            }
        }
    }

    private class EngineTemperature extends Sniffer {

        @Override
        public void job(long data, int len) {

            if (len != 8) {
                mSnooper.onError("len for engine temp. id do not match, should be 8 but received " + len);
                return;
            }

            mSnooper.onDebug("debug engine temp. hex " + String.format("%X", (data >> 56) & 0xFF));
            byte value = (byte)((data >> 56) & (long)0xFF);
            value -= 48;

            if (value >= 0 && value < MAX_ENGINE_TEMP && value != mValue) {
                mSnooper.onEngineTemperatureUpdated(mValue, value);
                mValue = value;
            }
        }
    }

    private class SteeringWheelInput extends Sniffer {

        @Override
        public void job(long data, int len) {

            if (len != 2) {
                mSnooper.onError("len for MFL id do not match, should be 2 but received " + len);
                return;
            }

            mSnooper.onDebug("debug MFL hex " + String.format("%X", (data >> 56) & 0xFF));
            short value = (short)(data & (long)0xFFFF);

            //TODO: mask what needed

            if (value != mValue) {

                switch (mValue) {
                    case 0xC001:
                        mSnooper.onSteeringWheelInputTriggered(MFL_TYPE_PHONE);
                        break;
                    default:
                        mSnooper.onDebug("skipping MFL hex " + String.format("%X", value));
                        break;
                }
                mValue = value;
            }
        }
    }

    public interface IBMWSnooper {

        public void onEngineRPMUpdated(int oldRPM, int newRPM);

        public void onVehicleSpeedUpdated(int oldSpeed, int newSpeed);

        public void onParkDistanceChanged(int pdcType, byte sensor1, byte sensor2, byte sensor3, byte sensor4);

        public void onEngineTemperatureUpdated(int oldTemperature, int newTemperature);

        public void onSteeringWheelInputTriggered(int type);

        public void onDebug(String msg);

        public void onError(String msg);
    }

    public class BMWSnifferException extends Exception {

        private String mReason;

        public BMWSnifferException(String reason) {
            mReason = reason;
        }

        public String getReason() {
            return mReason;
        }
    }
}

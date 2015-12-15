/*******************************************************************************
 * Copyright (c) 2015 Grzegorz Sygieda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 *******************************************************************************/

package com.sygmi;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;

import android.content.Context;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;

public final class Helpers {

    static {
        System.loadLibrary("helpers-jni");
    }

    public static boolean requestSuperUser() {

        Process sh;
        try {
            sh = Runtime.getRuntime().exec("su", null,null);
        }
        catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            return false;
        }

        return true;
    }

    public static boolean grantFilePermissions(String filename, String permissions) {

        Process sh;
        try {
            sh = Runtime.getRuntime().exec("su", null,null);
            OutputStream  os = sh.getOutputStream();
            os.write(("chmod " + permissions + " "+ filename).getBytes("ASCII"));
            os.flush();
            os.close();
            sh.waitFor();
        } 
        catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            return false;
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            return false;
        }	
		
        return true;
    }
    
    public static String timeStampString(int timestamp)
    {
        String text = String.format("%02d", (timestamp >> 22) & 0x1F).substring(0, 2) + ":";
    	text += String.format("%02d", (timestamp >> 16) & 0x3F).substring(0, 2) + ":";
    	text += String.format("%02d", (timestamp >> 10) & 0x3F).substring(0, 2) + ".";
    	text += String.format("%03d", (timestamp) & 0x3FF).substring(0, 3);

        return new String(text);
    }

    public static String findFtdiDevice(Context context) {

        UsbManager usbManager = (UsbManager)context.getSystemService(Context.USB_SERVICE);

        // Find the first available device
        for (UsbDevice device : usbManager.getDeviceList().values()) {
        	if(device.getVendorId() == 0x0403) {	// FTDI
        		if(device.getProductId() == 0x6001) {  //ft232,245
        			return device.getDeviceName();
        		}
        	}
        }
	
        return null;
    }

    public static String frame2hex(int id, byte[] data, int len) {

        /*Yet another hex format eg. AA,12345678 */

        String out = String.format("%X", id);
        out += ","; // dummy separator
        for (int i = 0; i < len; i++) {
            out += String.format("%02X", data[i]);
        }

        return new String(out);
    }

    public static class FrameBundle {
        int id;
        long data;
        int len;
    }

    public static boolean hex2frame(String in, FrameBundle fb) {

        String[] out = in.split(",");
        if (out != null) {
            try {
                fb.id = Integer.parseInt(out[0], 16);
            } catch (NumberFormatException e) {
                return false;
            }
            fb.len = out[1].length() / 2;
            try {
                fb.data = new BigInteger(out[1], 16).longValue();
            } catch (NumberFormatException e) {
                return false;
            }

            return true;
        }

        return false;
    }

    /* native methods */
    public static native String canframe2string(Object frame);
    public static native boolean string2canframe(String data, Object frame);

}

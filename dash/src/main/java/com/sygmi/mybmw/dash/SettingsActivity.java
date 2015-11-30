/*******************************************************************************
 * Copyright (c) 2015 Grzegorz Sygieda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 *******************************************************************************/

package com.sygmi.mybmw.dash;

import android.os.Bundle;
import android.preference.PreferenceActivity;

public class SettingsActivity extends PreferenceActivity{

    public static final String ATTR_DEV_TYPE = "prefDevType";
    public static final String ATTR_START_DEMO = "prefStartDemo";
    public static final String ATTR_REFRESH_RATE = "prefRefreshRate";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.settings);
    }
}

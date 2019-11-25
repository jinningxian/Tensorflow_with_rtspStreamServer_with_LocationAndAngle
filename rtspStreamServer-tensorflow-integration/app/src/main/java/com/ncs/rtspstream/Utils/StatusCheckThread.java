package com.ncs.rtspstream.Utils;

import android.os.Handler;

import java.util.TimerTask;

public class StatusCheckThread {

    Handler mHandler;
    TimerTask statusCheck;

    boolean returnStatus = false;


    public StatusCheckThread(int statusForCheck)
    {
        mHandler = new Handler();

    }


}

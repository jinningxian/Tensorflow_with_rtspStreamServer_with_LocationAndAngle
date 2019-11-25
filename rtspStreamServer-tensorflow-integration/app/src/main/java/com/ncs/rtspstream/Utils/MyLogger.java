package com.ncs.rtspstream.Utils;

/**
 * Description: MyLogger <br/>
 * Date: 2017/3/16 <br/>
 *
 * @author seven.hu@ubtrobot.com
 */

import android.text.TextUtils;
import android.util.Log;

public class MyLogger {

    private final static boolean DEBUG = true;
    private final static String TAG = "FaceApiDemo";
    private final static int LEVEL = Log.VERBOSE;
    private final static int LOG_MAX_LENGTH = 4000;

    private MyLogger(){

    }
    private static class InstanceHolder{
        private final static MyLogger INSTANCE = new MyLogger();
    }
    public static MyLogger log(){
        return InstanceHolder.INSTANCE;
    }

    /**
     * Get The Current Function Name
     *
     * @return
     */
    private String getFunctionName() {
        StackTraceElement[] sts = Thread.currentThread().getStackTrace();
        if (sts == null) {
            return null;
        }
        for (StackTraceElement st : sts) {
            if (st.isNativeMethod()) {
                continue;
            }
            if (st.getClassName().equals(Thread.class.getName())) {
                continue;
            }
            if (st.getClassName().equals(this.getClass().getName())) {
                continue;
            }
//
//            return KEY + "[ " + Thread.currentThread().getName() + ": " + st.getFileName() + ":"
//                    + st.getClassName() + ":" + st.getMethodName() + ":" + st.getLineNumber() + " ]";
            return "";
        }
        return null;
    }

    /**
     * The Log Level:i
     */
    public void i(Object str) {

        if(!DEBUG){
            return;
        }

        if (LEVEL <= Log.INFO) {

            String name = getFunctionName();
            if(TextUtils.isEmpty(name)){
                printLog(Log.INFO, str.toString());
                return;
            }
            printLog(Log.INFO, name + " - " + str);
        }


    }

    /**
     * The Log Level:d
     */
    public void d(Object str) {

        if(!DEBUG){
            return;
        }

        if (LEVEL <= Log.DEBUG) {

            String name = getFunctionName();
            if (TextUtils.isEmpty(name)) {
                printLog(Log.INFO, str.toString());
                return;
            }
            printLog(Log.INFO, name + " - " + str);
        }
    }

    /**
     * The Log Level:v
     */
    public void v(Object str) {

        if(!DEBUG){
            return;
        }

        if (LEVEL <= Log.VERBOSE) {
            String name = getFunctionName();
            if (TextUtils.isEmpty(name)) {
                printLog(Log.VERBOSE, str.toString());
                return;
            }
            printLog(Log.VERBOSE, name + " - " + str);
        }
    }

    /**
     * The Log Level:w
     */
    public void w(Object str) {

        if(!DEBUG){
            return;
        }

        if (LEVEL <= Log.WARN) {
            String name = getFunctionName();
            if (TextUtils.isEmpty(name)) {
                printLog(Log.WARN, str.toString());
                return;
            }
            printLog(Log.WARN, name + " - " + str);
        }
    }

    /**
     * The Log Level:e
     */
    public void e(Object str) {

        if(!DEBUG){
            return;
        }

        if (LEVEL <= Log.ERROR) {
            String name = getFunctionName();
            if (TextUtils.isEmpty(name)) {
                printLog(Log.ERROR, str.toString());
                return;
            }
            printLog(Log.ERROR, name + " - " + str);
        }
    }

    /**
     * The Log Level:e
     */
    public void e(Exception ex) {

        if(!DEBUG){
            return;
        }

        if (LEVEL <= Log.ERROR) {
            Log.e(TAG, "error", ex);
        }

    }

    /**
     * The Log Level:e
     */
    public void e(String log, Throwable tr) {

        if(!DEBUG){
            return;
        }

        String line = getFunctionName();
        Log.e(TAG, "{Thread:" + Thread.currentThread().getName() + "}" + "["  + line
                + ":] " + log + "\n", tr);
    }

    public void printLog(int level, String logText) {

        int index = 0;
        String sub;

        logText = logText.trim();

        while (index < logText.length()) {

            if (logText.length() <= index + LOG_MAX_LENGTH) {
                sub = logText.substring(index);
            } else {
                sub = logText.substring(index, index + LOG_MAX_LENGTH);
            }

            index += LOG_MAX_LENGTH;

            switch (level) {
                case Log.INFO:
                    Log.i(TAG, sub.trim());
                    break;
                case Log.DEBUG:
                    Log.d(TAG, sub.trim());
                    break;
                case Log.ERROR:
                    Log.e(TAG, sub.trim());
                    break;
                case Log.WARN:
                    Log.w(TAG, sub.trim());
                    break;
                case Log.VERBOSE:
                    Log.v(TAG, sub.trim());
                    break;
                default:
                    Log.e(TAG, sub.trim());
                    break;
            }
        }
    }

}


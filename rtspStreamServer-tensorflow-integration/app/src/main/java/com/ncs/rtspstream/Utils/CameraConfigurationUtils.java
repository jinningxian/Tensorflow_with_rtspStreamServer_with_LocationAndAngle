package com.ncs.rtspstream.Utils;

import android.graphics.Rect;
import android.graphics.RectF;
import android.hardware.Camera;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Description: configure the Camera.<br/>
 * Date: 2017/6/16 <br/>
 *
 * @author seven.hu@ubtrobot.com
 */

public class CameraConfigurationUtils {

    private static final int MIN_FPS = 10;
    private static final int MAX_FPS = 20;
    private static final float MAX_EXPOSURE_COMPENSATION = 1.5f;
    private static final float MIN_EXPOSURE_COMPENSATION = 0.0f;


    public static Camera.Size getOptimalPreviewSize(Camera.Parameters parameters, int previewWidth, int previewHeight) {

        List<Camera.Size> sizes = parameters.getSupportedPreviewSizes();
        if(sizes == null || sizes.size() <= 0){
            MyLogger.log().e("Camera getSupportedPreviewSizes is null");
            return null;
        }

//        for (Camera.Size size : parameters.getSupportedPreviewSizes()) {
//            MyLogger.log().d("Camera Supported SIZE:" + size.width + " * " + size.height);
//        }

        double targetRatio = (double) previewWidth / previewHeight;
        double minDiff = Double.MAX_VALUE;
        Camera.Size optimalSize = null;

        for (Camera.Size size : sizes) {
            double ratio = (double) size.width / size.height;

            if (Math.abs(ratio - targetRatio) > 0.1){
                continue;
            }

            if (Math.abs(size.height - previewHeight) < minDiff) {
                optimalSize = size;
                minDiff = Math.abs(size.height - previewHeight);
            }
        }

        if(optimalSize != null){
            return optimalSize;
        }

        minDiff = Double.MAX_VALUE;
        for (Camera.Size size : sizes) {
            if (Math.abs(size.height - previewHeight) < minDiff) {
                optimalSize = size;
                minDiff = Math.abs(size.height - previewHeight);
            }
        }
        return optimalSize;
    }

    public static void setBestPreviewFPS(Camera.Parameters parameters) {

        List<int[]> supportedPreviewFpsRanges = parameters.getSupportedPreviewFpsRange();
        MyLogger.log().d( "Supported FPS ranges: " + toString(supportedPreviewFpsRanges));

        if(supportedPreviewFpsRanges == null || supportedPreviewFpsRanges.isEmpty()){
            MyLogger.log().d( "Supported FPS ranges is null");
            return;
        }
        MyLogger.log().d( "Supported FPS ranges number = " + supportedPreviewFpsRanges.size());

        int[] suitableFPSRange = null;
        for (int[] fpsRange : supportedPreviewFpsRanges) {
            int thisMin = fpsRange[Camera.Parameters.PREVIEW_FPS_MIN_INDEX];
            int thisMax = fpsRange[Camera.Parameters.PREVIEW_FPS_MAX_INDEX];
            if (thisMin >= MIN_FPS * 1000 && thisMax <= MAX_FPS * 1000) {
                suitableFPSRange = fpsRange;
                break;
            }
        }

        if (suitableFPSRange == null) {
            MyLogger.log().d("No suitable FPS range?");
            return;
        }

        int[] currentFpsRange = new int[2];
        parameters.getPreviewFpsRange(currentFpsRange);
        if (Arrays.equals(currentFpsRange, suitableFPSRange)) {
            MyLogger.log().d("FPS range already set to " + Arrays.toString(suitableFPSRange));
        } else {
            MyLogger.log().d("Setting FPS range to " + Arrays.toString(suitableFPSRange));
            parameters.setPreviewFpsRange(suitableFPSRange[Camera.Parameters.PREVIEW_FPS_MIN_INDEX],
                    suitableFPSRange[Camera.Parameters.PREVIEW_FPS_MAX_INDEX]);
        }
    }

    public static List<Integer> getSupportedPreviewFormats(Camera.Parameters parameters){

        return parameters.getSupportedPreviewFormats();
    }

    public static void setMaxExposure(Camera.Parameters parameters) {

        //获取到的是级别
        int minExposure = parameters.getMinExposureCompensation();
        int maxExposure = parameters.getMaxExposureCompensation();
        //每个级别相差的曝光度
        float step = parameters.getExposureCompensationStep();
        MyLogger.log().d("Exposure parameter list:"+ minExposure + " - " + maxExposure + "," + step);
        if ((minExposure != 0 || maxExposure != 0) && step > 0.0f) {
            // Clamp value:
            if (parameters.getExposureCompensation() == maxExposure) {
                MyLogger.log().d("Exposure compensation already set to maxExposure = " + maxExposure);
            } else {
                MyLogger.log().d("Setting exposure compensation to maxExposure = " + maxExposure);
                parameters.setExposureCompensation(maxExposure);
            }
        } else {
            MyLogger.log().d( "Camera does not support exposure compensation");
        }
    }

    public static void setFocus(Camera.Parameters parameters, boolean autoFocus, boolean disableContinuous) {

        List<String> supportedFocusModes = parameters.getSupportedFocusModes();
        String focusMode = null;
        if (autoFocus) {

            if (disableContinuous) {
                focusMode = findSettableValue("focus mode", supportedFocusModes,
                        Camera.Parameters.FOCUS_MODE_AUTO);
            } else {
                //设置连续聚焦
                focusMode = findSettableValue("focus mode",
                        supportedFocusModes,
                        Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE,
                        Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO,
                        Camera.Parameters.FOCUS_MODE_AUTO);
            }
        }

        // Maybe selected auto-focus but not available, so fall through here:
        if (focusMode == null) {
            focusMode = findSettableValue("focus mode",
                    supportedFocusModes,
                    Camera.Parameters.FOCUS_MODE_MACRO,
                    Camera.Parameters.FOCUS_MODE_EDOF);
        }

        if (focusMode != null) {
            if (focusMode.equals(parameters.getFocusMode())) {
                MyLogger.log().d("Focus mode already set to " + focusMode);
            } else {
                parameters.setFocusMode(focusMode);
            }
        }
    }

    public static void setFocusArea(Camera.Parameters parameters, int width, int height, int previewWidth, int previewHeight) {

        if(width * height <= 0){
            MyLogger.log().d( "focus area width or height can't be zero.");
            return;
        }

        if(parameters.getMaxNumFocusAreas() <= 0){
            MyLogger.log().d("Device does not support focus areas");
            return;
        }

        MyLogger.log().d("Old focus areas: " + toString(parameters.getFocusAreas()));
        List<Camera.Area> middleArea = buildMiddleArea(width,height,previewWidth,previewHeight);
        MyLogger.log().d("Setting focus area to : " + toString(middleArea));
        parameters.setFocusAreas(middleArea);
    }

    public static void setMetering(Camera.Parameters parameters, int width, int height, int previewWidth, int previewHeight) {

        if(width * height <= 0){
            MyLogger.log().d( "Device does not support metering areas");
            return;
        }

        if (parameters.getMaxNumMeteringAreas() <= 0) {
            MyLogger.log().d( "Device does not support metering areas");
            return;
        }

        MyLogger.log().d("Old metering areas: " + parameters.getMeteringAreas());
        List<Camera.Area> middleArea = buildMiddleArea(width,height,previewWidth,previewHeight);
        MyLogger.log().d("Setting metering area to : " + toString(middleArea));
        parameters.setMeteringAreas(middleArea);
    }

    public static void setWhiteBalance(Camera.Parameters parameters){

        List<String> supportedWhiteBalance = parameters.getSupportedWhiteBalance();
        if(supportedWhiteBalance == null || supportedWhiteBalance.isEmpty()){
            MyLogger.log().d( "Camera does not support white balance.");
            return;
        }

        String whiteBalance = parameters.getWhiteBalance();
        MyLogger.log().d("current white balance is " + whiteBalance);
    }

    private static List<Camera.Area> buildMiddleArea(int width, int height, int previewWidth, int previewHeight) {

        Rect rect = calculateCameraArea(width, height, previewWidth, previewHeight);
        return Collections.singletonList(new Camera.Area(rect, 800));
    }


    private static String findSettableValue(String name, Collection<String> supportedValues, String... desiredValues) {

        MyLogger.log().d("Requesting " + name + " value from among: " + Arrays.toString(desiredValues));
        MyLogger.log().d("Supported " + name + " values: " + supportedValues);
        if (supportedValues != null) {
            for (String desiredValue : desiredValues) {
                if (supportedValues.contains(desiredValue)) {
                    MyLogger.log().d("Can set " + name + " to: " + desiredValue);
                    return desiredValue;
                }
            }
        }
        MyLogger.log().d("No supported values match");
        return null;
    }


    private static String toString(Collection<int[]> arrays) {
        if (arrays == null || arrays.isEmpty()) {
            return "[]";
        }
        StringBuilder buffer = new StringBuilder();
        buffer.append('[');
        Iterator<int[]> it = arrays.iterator();
        while (it.hasNext()) {
            buffer.append(Arrays.toString(it.next()));
            if (it.hasNext()) {
                buffer.append(", ");
            }
        }
        buffer.append(']');
        return buffer.toString();
    }

    private static String toString(Iterable<Camera.Area> areas) {
        if (areas == null) {
            return null;
        }
        StringBuilder result = new StringBuilder();
        for (Camera.Area area : areas) {
            result.append(area.rect).append(':').append(area.weight).append(' ');
        }
        return result.toString();
    }

    private static Rect calculateCameraArea(int width, int height, int previewWidth, int previewHeight) {

        int cameraAreaHeight = (height * 2000 / previewHeight ) / 2;
        int cameraAreaHalfWidth =  (width * 2000 / previewWidth ) / 2;

        RectF rectF = new RectF(clamp(-cameraAreaHalfWidth, -1000, 1000)
                , clamp(-cameraAreaHeight, -1000, 1000)
                , clamp(cameraAreaHalfWidth, -1000, 1000)
                , clamp(cameraAreaHeight, -1000, 1000));

        return new Rect(Math.round(rectF.left), Math.round(rectF.top), Math.round(rectF.right), Math.round(rectF.bottom));
    }

    private static int clamp(int x, int min, int max) {
        if (x > max) {
            return max;
        }
        if (x < min) {
            return min;
        }
        return x;
    }


}

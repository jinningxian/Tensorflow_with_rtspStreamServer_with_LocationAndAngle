package com.ncs.rtspstream.Utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Environment;

import com.ubtechinc.cruzr.sdk.recognition2.model.RawImage;
import com.ubtechinc.cruzr.sdk.recognition2.utils.ImageUtils;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Description: TODO <br/>
 * Date: 2017/2/7 <br/>
 *
 * @author seven.hu@ubtrobot.com
 */

public class AppUtils {

    private static final String APP_ROOT_DIR = "FaceRepo";
    private static final String FACE_DATA_DIR = "face";

    /**
     * get the path of faces data.
     */
    public static String getFaceDir(){
        return Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator + APP_ROOT_DIR + File.separator + FACE_DATA_DIR;
    }

    /**
     * save a RawImage to a picture.
     * @return the path of the picture.
     */
    public static Uri save(RawImage image, Rect rect, boolean isMirror){
        String desDir = getFaceDir();
        FileUtils.queryOrCreateDir(desDir);

        SimpleDateFormat format = new SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault());
        Date date = new Date(System.currentTimeMillis());
        String path = desDir + File.separator + "IMG" + format.format(date) + ".jpg";
        MyLogger.log().d("save a face image to ---> " + path);

        if(!ImageUtils.save(image,rect,isMirror,path,100)){
            return null;
        }

        return FileUtils.pathToUri(path);
    }

    /**
     * save the face feature.
     * @param features
     */
    public static void saveFeature(Context context, Uri uri, byte[] features){
        SharedPreferences sp = context.getSharedPreferences("feature", Context.MODE_PRIVATE);
        sp.edit().putString(uri.toString(),bytesToHexString(features)).apply();
    }

    /**
     * get all the features.
     */
    public static Map<byte[], Uri> getAllFeatures(Context context){
        SharedPreferences sp = context.getSharedPreferences("feature", Context.MODE_PRIVATE);
        HashMap<String, String> datas = (HashMap<String, String>) sp.getAll();
        Set<Map.Entry<String, String>> set = datas.entrySet();
        Map<byte[], Uri> features = new HashMap<>();
        for(Map.Entry<String, String> entry : set){
            String featureSource = entry.getValue();
            features.put(StringToBytes(featureSource), Uri.parse(entry.getKey()));
        }
        return features;
    }


    public static String bytesToHexString(byte[] bArray) {
        if(bArray == null){
            return null;
        }
        if(bArray.length == 0){
            return "";
        }
        StringBuffer sb = new StringBuffer(bArray.length);
        String sTemp;
        for (int i = 0; i < bArray.length; i++) {
            sTemp = Integer.toHexString(0xFF & bArray[i]);
            if (sTemp.length() < 2)
                sb.append(0);
            sb.append(sTemp.toUpperCase());
        }
        return sb.toString();
    }

    public static byte[] StringToBytes(String data){
        String hexString=data.toUpperCase().trim();
        if (hexString.length()%2!=0) {
            return null;
        }
        byte[] retData=new byte[hexString.length()/2];
        for(int i=0;i<hexString.length();i++)
        {
            int int_ch;
            char hex_char1 = hexString.charAt(i);
            int int_ch1;
            if(hex_char1 >= '0' && hex_char1 <='9')
                int_ch1 = (hex_char1-48)*16;
            else if(hex_char1 >= 'A' && hex_char1 <='F')
                int_ch1 = (hex_char1-55)*16;
            else
                return null;
            i++;
            char hex_char2 = hexString.charAt(i);
            int int_ch2;
            if(hex_char2 >= '0' && hex_char2 <='9')
                int_ch2 = (hex_char2-48);
            else if(hex_char2 >= 'A' && hex_char2 <='F')
                int_ch2 = hex_char2-55;
            else
                return null;
            int_ch = int_ch1+int_ch2;
            retData[i/2]=(byte) int_ch;
        }
        return retData;
    }

}

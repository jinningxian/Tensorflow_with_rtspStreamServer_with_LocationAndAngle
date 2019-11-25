package com.ncs.rtspstream.Utils;

import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * Description: FileUtils <br/>
 * Date: 2017/2/7 <br/>
 *
 * @author seven.hu@ubtrobot.com
 */

public class FileUtils {

    private static final String TAG = "FileUtils";

    public static boolean queryOrCreateDir(String dir){

        if(TextUtils.isEmpty(dir)){
            return false;
        }

        File file = new File(dir);

        if(!file.exists()){
            return file.mkdirs();
        }

        return true;
    }

    public static boolean exists(String path){

        if(TextUtils.isEmpty(path)){
            return false;
        }

        File file = new File(path);
        if(!file.isFile()){
            return false;
        }

        return file.exists();
    }

    public static String[] list(String path){

        if(TextUtils.isEmpty(path)){
            return null;
        }

        File file = new File(path);
        return file.list();
    }

    public static boolean deleteFile(File file){

        if(file == null){
            return true;
        }

        if (!file.exists()) {
            return true;
        }

        if (file.isFile()) {
            return file.delete();
        }

        else {

            if(file.isDirectory()) {
                String[] subPaths = file.list();

                for(String subPath: subPaths){
                    if(!deleteFile(subPath)){
                        return false;
                    }
                }
            }

            return file.delete();
        }
    }

    public static boolean cleanDirectory(String dir){

        if(TextUtils.isEmpty(dir)){
            return false;
        }

        File file = new File(dir);

        if (!file.exists()) {
            return false;
        }

        if (!file.isDirectory()) {
            return false;
        }

        String[] subPaths = file.list();
        for(String subPath: subPaths){
            if(!deleteFile(dir + File.separator + subPath)){
                return false;
            }
        }

        return true;
    }


    public static boolean deleteFile(String path) {

        if (TextUtils.isEmpty(path)) {
            return true;
        }

        File file = new File(path);

        return deleteFile(file);
    }

    public static boolean deleteFile(Uri uri) {

        if (uri == null) {
            return true;
        }

        File file = new File(uri.getPath());
        return deleteFile(file);
    }

    /**
     * get file size
     * <ul>
     * <li>if path is null or empty, return -1</li>
     * <li>if path exist and it is a file, return file size, else return -1</li>
     * <ul>
     *
     * @param path
     * @return returns the length of this file in bytes. returns -1 if the file does not exist.
     */
    public static long getFileSize(String path) {

        if (TextUtils.isEmpty(path)) {
            return -1;
        }

        File file = new File(path);
        return (file.exists() && file.isFile() ? file.length() : -1);
    }


    public static byte[] readFile(String path){

        File file = new File(path);
        if(!file.exists()){
            return null;
        }

        try {
            byte[] content;
            FileInputStream fis = new FileInputStream(file);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();

            int len;
            byte[] buffer = new byte[1024];
            while ((len = fis.read(buffer)) != -1){
                bos.write(buffer,0,len);
            }

            content = bos.toByteArray();
            fis.close();
            bos.flush();
            bos.close();
            return content;

        } catch (Exception exception){
            exception.printStackTrace();
            return null;
        }
    }

    public static StringBuilder readFile(String filePath, String charsetName) {
        File file = new File(filePath);
        StringBuilder fileContent = new StringBuilder("");

        if (!file.isFile()) {
            return null;
        }

        BufferedReader reader = null;

        try {
            InputStreamReader is = new InputStreamReader(new FileInputStream(file), charsetName);
            reader = new BufferedReader(is);

            String line;
            while ((line = reader.readLine()) != null) {
                Log.d(TAG, "line: " + line);

                if (!fileContent.toString().equals("")) {
                    fileContent.append("\r\n");
                }

                fileContent.append(line);
            }
            return fileContent;
        } catch (IOException e) {
            throw new RuntimeException("IOException", e);
        } finally {
            IOUtils.closeQuietly(reader);
        }
    }

    public static Uri pathToUri(String path){

        File file = new File(path);
        return Uri.fromFile(file);
    }

    public static boolean createFile(String absoluteFileName) {

        File file = new File(absoluteFileName);
        if(file.exists()) {
            Log.d(TAG, "create " + absoluteFileName + "success, file already exists!");
            return true;
        }

        if (absoluteFileName.endsWith(File.separator)) {
            Log.e(TAG, "create " + absoluteFileName + "fail, absoluteFileName is a directory!");
            return false;
        }

        if(!file.getParentFile().exists()) {
            if(!file.getParentFile().mkdirs()) {
                Log.e(TAG, "create " + absoluteFileName + " parent directory fail!");
                return false;
            }
        }

        try {
            if (file.createNewFile()) {
                return true;
            } else {
                Log.e(TAG, "create " + absoluteFileName + " fail!");
                return false;
            }
        } catch (IOException e) {
            e.printStackTrace();
            Log.e(TAG, "create " + absoluteFileName + " fail!");
            return false;
        }
    }

}

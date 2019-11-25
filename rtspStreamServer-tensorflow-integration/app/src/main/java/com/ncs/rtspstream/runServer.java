package com.ncs.rtspstream;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.WindowManager;

import net.majorkernelpanic.streaming.Session;
import net.majorkernelpanic.streaming.SessionBuilder;
import net.majorkernelpanic.streaming.audio.AudioQuality;
import net.majorkernelpanic.streaming.gl.SurfaceView;
import net.majorkernelpanic.streaming.rtsp.RtspServer;
import net.majorkernelpanic.streaming.video.VideoQuality;

public class runServer extends Activity {

    private final static String TAG = "runServer";

    private SurfaceView mSurfaceView;
    private Session mSession;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_run_server);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

        mSurfaceView = (SurfaceView) findViewById(R.id.surface);
        mSurfaceView.getHolder().addCallback(surfaceCallback);

        // Sets the port of the RTSP server to 1234
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(this).edit();
        editor.putString(RtspServer.KEY_PORT, String.valueOf(1234));
        editor.commit();

        //mSession =
                SessionBuilder.getInstance()
                        //.setSurfaceView(mSurfaceView)
                        //.setPreviewOrientation(90)
                        .setContext(getApplicationContext())
                        .setAudioEncoder(SessionBuilder.AUDIO_NONE)
                        .setAudioQuality(new AudioQuality(16000,128*1024))
                        .setVideoEncoder(SessionBuilder.VIDEO_H264)
                        .setVideoQuality(new VideoQuality(320,240,20,500000))
                        .setCallback(sessionCallback)
                        .setDestination("192.168.21.20");
        //.build();
        //mSession.start();

        this.startService(new Intent(this, RtspServer.class));
        //Intent intent = new Intent(this,MainActivity.class);
        //startActivity(intent);

    }
    protected  void onStart(){
        super.onStart();
    }


    private Session.Callback sessionCallback = new Session.Callback() {
        @Override
        public void onBitrateUpdate(long bitrate) {
            Log.i("runServer" , "birate Updated: "+bitrate);
        }

        @Override
        public void onSessionError(int reason, int streamType, Exception e) {
            e.printStackTrace();
        }

        @Override
        public void onPreviewStarted() {
            Log.i("runServer", "Preview started");
        }

        @Override
        public void onSessionConfigured() {
            Log.i("runServer", "Preview configured");
            mSession.start();
            //startService(new Intent(runServer.this, RtspServer.class));
            Log.d("runServer", mSession.getSessionDescription());
        }

        @Override
        public void onSessionStarted() {
            //startActivity(new Intent(runServer.this, MainActivity.class));
        }

        @Override
        public void onSessionStopped() {

        }
    };

    private SurfaceHolder.Callback surfaceCallback = new SurfaceHolder.Callback() {
        @Override
        public void surfaceCreated(SurfaceHolder surfaceHolder) {

            /*mSession =
            SessionBuilder.getInstance()
                    .setSurfaceView(mSurfaceView)
                    .setPreviewOrientation(90)
                    .setContext(getApplicationContext())
                    .setAudioEncoder(SessionBuilder.AUDIO_NONE)
                    .setAudioQuality(new AudioQuality(16000,128*1024))
                    .setVideoEncoder(SessionBuilder.VIDEO_H264)
            .setVideoQuality(new VideoQuality(320,240,20,500000))
            .setCallback(sessionCallback)
            .setDestination("192.168.21.20")
            .build();
            mSession.start();


            getApplicationContext().startService(new Intent(runServer.this, RtspServer.class));*/
            //Intent intent = new Intent(runServer.this,diffSteaming.class);
            //startActivity(intent);
        }

        @Override
        public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i1, int i2) {

        }

        @Override
        public void surfaceDestroyed(SurfaceHolder surfaceHolder) {

        }
    };
}

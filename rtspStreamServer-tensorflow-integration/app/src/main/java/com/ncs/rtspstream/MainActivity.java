package com.ncs.rtspstream;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.graphics.PixelFormat;
import android.hardware.Camera;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.app.AppCompatActivity;
import android.text.format.Formatter;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import net.majorkernelpanic.streaming.SessionBuilder;
import net.majorkernelpanic.streaming.gl.SurfaceView;
import net.majorkernelpanic.streaming.rtsp.RtspServer;
import net.majorkernelpanic.streaming.video.VideoQuality;

import detection.MediaFileHandler;
import detection.customview.OverlayView;
import detection.tracking.MultiBoxTracker;


/**
 * A straightforward example of how to use the RTSP server included in libstreaming.
 */
public class MainActivity extends AppCompatActivity {

    private final static String TAG = "MainActivity";

    public static SurfaceView mSurfaceView;
    public static boolean surfaceVisibility = true;
    private ImageView menuButton;
    private Button startStopButton;
    private TextView rtspStreamLink;
    private SharedPreferences mSharedPreference;
//    private Button showHide;

    private TextView framerateView;
    private TextView resolutionView;
    private TextView bitrateView;

    private String ip;

    private SurfaceView sv; // supposedly fake surfaceview
    //private ConstraintLayout parentLayout;
//    private ConstraintSet constraintSet;
    //private MediaRecorder mMediaRecorder;
    private SurfaceView mSurfaceView2;

    public static boolean save = false;
    private Button saveButton;
    public ImageView snapshot;
    OverlayView trackingOverlay;
    private MultiBoxTracker tracker;
    private Fragment fragment;
    private Button adminVisitorToggle;
    private MediaFileHandler mMediaFileHandler;

    private boolean showSurface = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_tensorflow_example);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        tracker = new MultiBoxTracker(this);
//        setContentView(R.layout.activity_main);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

        mSurfaceView = (SurfaceView) findViewById(R.id.surfaceView);
        //mSurfaceView.setZ(5);

//        mSurfaceView2 = findViewById(R.id.surfaceView2);
        //mSurfaceView2.setZ(0);

//        parentLayout = findViewById(R.id.parentLayout);
//        constraintSet = new ConstraintSet();

//        showHide = findViewById(R.id.show_hide);
        createFakeView();

        rtspStreamLink = findViewById(R.id.RTSPAddress);

        WifiManager wm = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        ip = Formatter.formatIpAddress(wm.getConnectionInfo().getIpAddress());

        /*menuButton = findViewById(R.id.menu);
        menuButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(MainActivity.this, Settings.class);
                //intent.putExtra("")
                startActivityForResult(intent, 0);
            }
        });*/

        startStopButton = findViewById(R.id.startStop);

        framerateView = findViewById(R.id.showFramerate);
        bitrateView = findViewById(R.id.showBitrate);
        resolutionView = findViewById(R.id.showResolution);

        framerateView.setText("");
        bitrateView.setText("");
        resolutionView.setText("");

        adminVisitorToggle = findViewById(R.id.toggleView);

        adminVisitorToggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //showSurface = !showSurface;
                if (adminVisitorToggle.getText().equals("Change to Visitor View")) {
                    adminVisitorToggle.setText("Change to Admin View");
                    mSurfaceView.setVisibility(View.INVISIBLE);
                    updateView(true, sv);
                    /*mMediaFileHandler = new MediaFileHandler(getApplicationContext());
                    mMediaFileHandler.audioBroadcast(new File("/data/sftpuser/download/test2.mp3"));*/

                }
                else {
                    adminVisitorToggle.setText("Change to Visitor View");
                    mSurfaceView.setVisibility(View.VISIBLE);
                    updateView(true, mSurfaceView);
                    mSurfaceView.setZOrderMediaOverlay(true);
                    //mSurfaceView.setZOrderOnTop(false);
                }
                //constraintSet.applyTo(parentLayout);
            }
        });

        /*saveButton = findViewById(R.id.saveImage);
        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                save = !save;
            }
        });*/

//        snapshot = findViewById(R.id.snapshot);
        /*fragment =
                new LegacyCameraConnectionFragment(this, R.layout.camera_connection_fragment_tracking, new Size(640, 480));
        mCamera = ((LegacyCameraConnectionFragment) fragment).camera;*/
       /* trackingOverlay = (OverlayView) findViewById(R.id.tracking_overlay);
        trackingOverlay.addCallback(
                new OverlayView.DrawCallback() {
                    @Override
                    public void drawCallback(final Canvas canvas) {
                        tracker.draw(canvas);
                    }
                });

        tracker.setFrameConfiguration(mCamera.getParameters().getPreviewSize().width, mCamera.getParameters().getPreviewSize().height, 0);*/
       mSurfaceView.setVisibility(View.INVISIBLE);
    }

    public static boolean faceDetected = false;

    @Override
    protected void onStart() {
        super.onStart();

        mSharedPreference  = getSharedPreferences("robotConfigFile", MODE_PRIVATE);

        // set context for mediahandler
        MediaFileHandler.mContext = getApplicationContext();


        // start and configure the stream server
        updateView(true, sv);
        Toast.makeText(this, "Stream Server Started!", Toast.LENGTH_SHORT).show();

        // start MQTT TODO: done in App.jave
//        startMqtt();
        //set transparent view?
        //mSurfaceView.getHolder().setFormat(PixelFormat.TRANSPARENT);
        // Starts the RTSP server
        this.startService(new Intent(getApplicationContext(),RtspServer.class));
        App.mMediaFileHandler = new MediaFileHandler(getApplicationContext());

        // TODO: see if the preview will start automatically, yes it does
        // when an rtsp instance starts, the surface is left as is, but no buffer can be extracted

           /* mSurfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
                @Override
                public void surfaceCreated(SurfaceHolder surfaceHolder) {
                    try {
                        mCamera.setPreviewDisplay(mSurfaceView.getHolder());
                        mCamera.setDisplayOrientation(0);
                        mCamera.startPreview();
                    }catch (Exception e){
                        e.printStackTrace();
                    }
                }

                @Override
                public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i1, int i2) {

                }

                @Override
                public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
                }
            });*/
            /*try {
                mCamera.setPreviewDisplay(mSurfaceView.getHolder());
                mCamera.setPreviewCallbackWithBuffer(new Camera.PreviewCallback() {
                    @Override
                    public void onPreviewFrame(byte[] bytes, Camera camera) {
                        Log.i(TAG, "I think this also activates the camera");
                    }
                });
                mCamera.startPreview();
            }catch (Exception e){
                e.printStackTrace();
            }*/

        //testing invisible surface view
        /*if (Build.VERSION.SDK_INT >= 23) {
            if (!Settings.canDrawOverlays(this)) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, 0);
            }
        }
        else {
            //startService(new Intent(this, CameraService.class));
            }
*/

        // Initialize the SessionBuilder

        // moved from onCreate due to preference issue
        /*startStopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Toggle button, start word is "Stop Server"
                // Start State
                if (startStopButton.getText().equals("Start Server")){
                    Toast.makeText(MainActivity.this, "Stream Server has started!", Toast.LENGTH_LONG).show();
                    rtspStreamLink.setText("rtsp://" + ip + ":" + mSharedPreference.getString(RtspServer.KEY_PORT, "8086"));
                    startStopButton.setText("Stop Server");
                    // Starts the RTSP server
                    MainActivity.this.startService(new Intent(getApplicationContext(),RtspServer.class));
                }
                // Stop State
                else{
                    Toast.makeText(MainActivity.this, "Stream Server Stopped!", Toast.LENGTH_LONG).show();
                    rtspStreamLink.setText("");
                    startStopButton.setText("Start Server");
                    // Stops the RTSP server
                    MainActivity.this.stopService(new Intent(getApplicationContext(),RtspServer.class));
                }
            }
        });*/
    }

    public Camera mCamera;

    @Override
    protected void onPause(){
        super.onPause();
        mCamera.unlock();
    }

    @Override
    protected  void onDestroy(){
        super.onDestroy();
        //mCamera.unlock();
        mCamera.addCallbackBuffer(null);
        mCamera.release();
        cameraRelease = true;
        App.mMediaFileHandler.stop();
        App.mMediaFileHandler = null;
    }

    public static boolean cameraRelease = false;

    @Override
    protected void onResume(){
        super.onResume();
        if (mCamera != null){
            try{
                mCamera.reconnect();
            }catch (Exception e){
                e.printStackTrace();
            }
        } else{
            mCamera = Camera.open(0); // only open front camera
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK){
            Toast.makeText(this, "Reconfiguring server, please restart on client side", Toast.LENGTH_SHORT).show();
            updateView(true, sv);
            // Starts the RTSP server
            MainActivity.this.startService(new Intent(getApplicationContext(), RtspServer.class));

            Toast.makeText(MainActivity.this, "Stream Server has started!", Toast.LENGTH_LONG).show();
            rtspStreamLink.setText("rtsp://" + ip + ":" + mSharedPreference.getString(RtspServer.KEY_PORT, "8086"));
            startStopButton.setText("Stop Server");

        }
    }

    public void updateView(boolean rebuildSession, SurfaceView surface){
        // show ip address and link to rtsp stream
        WifiManager wm = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        ip = Formatter.formatIpAddress(wm.getConnectionInfo().getIpAddress());
        rtspStreamLink.setText("rtsp://" + ip + ":" + mSharedPreference.getString(RtspServer.KEY_PORT, "8086"));

        // we do not need to handle port cuz it'll autoconfigure within the RtspServer on its own

        // the following default values has been proven to work
        int resX = Integer.parseInt(mSharedPreference.getString("ResolutionX", "640"));
        int resY = Integer.parseInt(mSharedPreference.getString("ResolutionY", "480"));
        int videoBitrate = Integer.parseInt(mSharedPreference.getString("VideoBitrate", "500000"));
        int framerate = Integer.parseInt(mSharedPreference.getString("Framerate", "10"));

        framerateView.setText(String.valueOf(framerate));
        bitrateView.setText(String.valueOf(videoBitrate));
        resolutionView.setText(String.valueOf(resX) + "X" + String.valueOf(resY));

        if (rebuildSession == true){
            // open camera, create preview, then pass into sessionBuilder
            if (mCamera == null)
                mCamera = Camera.open(0);
            Log.i("updateView", "configuring session");
            configureSession(surface, resX, resY, framerate, videoBitrate);
        }
    }

    public void configureSession(SurfaceView surfaceview, int x, int y, int fr, int vbr){
        //this.stopService(new Intent(getApplicationContext(), RtspServer.class));

        // Configures the SessionBuilder
        SessionBuilder.getInstance()
                .setSurfaceView(surfaceview)
                .setPreviewOrientation(0)
                .setContext(getApplicationContext())
                .setVideoQuality(new VideoQuality(x,y,fr,vbr))
                .setAudioEncoder(SessionBuilder.AUDIO_AAC)
                .setVideoEncoder(SessionBuilder.VIDEO_H264)
                .setCamera(mCamera)
                .setContext(this);

        //this.startService(new Intent(getApplicationContext(), RtspServer.class));
    }

    public void createFakeView(){
        WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        LayoutInflater inflater =(LayoutInflater)getSystemService(LAYOUT_INFLATER_SERVICE);
        RelativeLayout layout=(RelativeLayout) inflater.inflate(R.layout.fake_preview,null);
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(1, 1,
                WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY,
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT);

        wm.addView(layout, params);

        sv = (SurfaceView) layout.findViewById(R.id.surfaceView_fake);
        SurfaceHolder sh = sv.getHolder();
        sv.setZOrderOnTop(true);
        sh.setFormat(PixelFormat.TRANSPARENT);
    }

    /*@Override
    public void onImageAvailable(ImageReader imageReader) {

    }

    @Override
    public void onPointerCaptureChanged(boolean hasCapture) {

    }*/


}


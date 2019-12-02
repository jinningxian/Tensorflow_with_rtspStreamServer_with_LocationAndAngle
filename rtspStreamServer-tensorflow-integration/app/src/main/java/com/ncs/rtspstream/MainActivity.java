package com.ncs.rtspstream;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.hardware.Camera;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.app.AppCompatActivity;
import android.text.format.Formatter;
import android.util.Log;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.ubtechinc.cruzr.sdk.navigation.NavigationApi;

import net.majorkernelpanic.streaming.SessionBuilder;
import net.majorkernelpanic.streaming.gl.SurfaceView;
import net.majorkernelpanic.streaming.hw.EncoderDebugger;
import net.majorkernelpanic.streaming.hw.NV21Convertor;
import net.majorkernelpanic.streaming.rtsp.RtspServer;
import net.majorkernelpanic.streaming.video.ImageUtils;
import net.majorkernelpanic.streaming.video.Point;
import net.majorkernelpanic.streaming.video.VideoQuality;
import net.majorkernelpanic.streaming.video.VideoStream;

import java.nio.ByteBuffer;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

import detection.BitmapHandler;
import detection.MediaFileHandler;
import detection.MqttHelper;
import detection.customview.OverlayView;
import detection.tflite.Classifier;
import detection.tflite.TFLiteObjectDetectionAPIModel;
import detection.tracking.MultiBoxTracker;

import static com.ncs.rtspstream.App.displayPosition;
import static com.ncs.rtspstream.App.robotWorkStatus;

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

        videoAnalyticsStart(mCamera);
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


    //---------------------start VA on startup--------------------------------------------
    private AssetManager assetManager;
    public Classifier detector;
    // Configuration values for the prepackaged SSD model.
    private static final int TF_OD_API_INPUT_SIZE = 300;
    private static final boolean TF_OD_API_IS_QUANTIZED = true;
    private static final String TF_OD_API_MODEL_FILE = "detect.tflite";
    private static final String TF_OD_API_LABELS_FILE = "file:///android_asset/labelmap.txt";
    private static final DetectorMode MODE = DetectorMode.TF_OD_API;
    //private static final DetectorMode MODE = DetectorMode.TF_OD_API;
    // Minimum detection confidence to track a detection.
    private static final float MINIMUM_CONFIDENCE_TF_OD_API = 0.5f;
    private static final boolean MAINTAIN_ASPECT = false;
    private static final Size DESIRED_PREVIEW_SIZE = new Size(640, 480);
    private static final boolean SAVE_PREVIEW_BITMAP = false;
    private static final float TEXT_SIZE_DIP = 10;

    Runnable imageConverter;

    private enum DetectorMode {
        TF_OD_API;
    }

    private void videoAnalyticsStart(Camera camera){
        mCamera.startPreview();
        assetManager = getApplicationContext().getAssets();
        try {
            detector =
                    TFLiteObjectDetectionAPIModel.create(
                            assetManager,
                            TF_OD_API_MODEL_FILE,
                            TF_OD_API_LABELS_FILE,
                            TF_OD_API_INPUT_SIZE,
                            TF_OD_API_IS_QUANTIZED);
            cropSize = TF_OD_API_INPUT_SIZE;
        }catch (Exception e){
            e.printStackTrace();
        }


        Camera.PreviewCallback callback = new Camera.PreviewCallback() {
            long now = System.nanoTime()/1000, oldnow = now, i=0;
            @Override
            synchronized public void onPreviewFrame(final byte[] data, Camera camera) {
                oldnow = now;
                now = System.nanoTime()/1000;
                try {
                    if (MainActivity.cameraRelease == true){
                        Log.i(TAG, "Camera has been released!");
                        return;
                    }
                    runDetection(data, camera);
                } finally {
                    mCamera.addCallbackBuffer(data);
                }
            }
        };
    }

    private long timestamp = 0, lastProcessingTimeMs;
    private boolean computingDetection = false;
    public Bitmap croppedBitmap, cropCopyBitmap;
    private Matrix frameToCropTransform;
    private Matrix cropToFrameTransform;
    public MqttHelper mqttHelper = App.mqttHelper;
    boolean ans = false;
    private int[] rgbBytes;
    private Bitmap image;
    private int cropSize;
    private int previewWidth, previewHeight;
    private boolean readyForNextImage = false;

    private void runDetection(byte[] data, Camera camera){
        imageConverter =
                new Runnable() {
                    @Override
                    public void run() {
                        ImageUtils.convertYUV420SPToARGB8888(
                                data,
                                camera.getParameters().getPreviewSize().width,
                                camera.getParameters().getPreviewSize().height,
                                rgbBytes);
                        image = Bitmap.createBitmap(camera.getParameters().getPreviewSize().width, camera.getParameters().getPreviewSize().height, Bitmap.Config.ARGB_8888);
                        image.setPixels(rgbBytes, 0, camera.getParameters().getPreviewSize().width, 0, 0, camera.getParameters().getPreviewSize().width, camera.getParameters().getPreviewSize().height);
                    }
                };
        Camera.Size previewSize = camera.getParameters().getPreviewSize();
        int previewHeight = previewSize.height;
        int previewWidth = previewSize.width;
        rgbBytes = new int[previewWidth * previewHeight];
        imageConverter.run();
        croppedBitmap = Bitmap.createBitmap(cropSize, cropSize, Bitmap.Config.ARGB_8888);
        processImage();
    }

    protected void processImage() {
        ++timestamp;
        final long currTimestamp = timestamp;
        //trackingOverlay.postInvalidate();

        frameToCropTransform =
                ImageUtils.getTransformationMatrix(
                        previewWidth, previewHeight,
                        cropSize, cropSize,
                        0, MAINTAIN_ASPECT);
        cropToFrameTransform = new Matrix();
        frameToCropTransform.invert(cropToFrameTransform);

        // No mutex needed as this method is not reentrant.
        if (computingDetection) {
            //readyForNextImage(); // just add callback buffer and toggle an isprocessingframe
            readyForNextImage = false;
            return;
        }
        computingDetection = true;
        Log.i(TAG, "Preparing image " + currTimestamp + " for detection in bg thread.");

        image.setPixels(rgbBytes, 0, previewWidth, 0, 0, previewWidth, previewHeight);

        //readyForNextImage(); // just add callback buffer and toggle an isprocessingframe

        final Canvas canvas = new Canvas(croppedBitmap);
        canvas.drawBitmap(image, frameToCropTransform, null);
        // For examining the actual TF input.
        if (SAVE_PREVIEW_BITMAP) {
            ImageUtils.saveBitmap(croppedBitmap);
        }
        if (detectInBackground != null){
            detectInBackground.run();
        }

    }

    public Runnable detectInBackground = new Thread() {
        @Override
        public void run() {
            if (mCamera != null) {
                Log.i(TAG,"Running detection on image " + System.nanoTime()/1000);
                final long startTime = SystemClock.uptimeMillis();
                final List<Classifier.Recognition> results = detector.recognizeImage(croppedBitmap);
                lastProcessingTimeMs = SystemClock.uptimeMillis() - startTime;

                cropCopyBitmap = Bitmap.createBitmap(croppedBitmap);
                final Canvas canvas = new Canvas(cropCopyBitmap);
                final Paint paint = new Paint();
                paint.setColor(Color.RED);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(2.0f);

                float minimumConfidence = MINIMUM_CONFIDENCE_TF_OD_API;
                switch (MODE) {
                    case TF_OD_API:
                        minimumConfidence = MINIMUM_CONFIDENCE_TF_OD_API;
                        break;
                }

                final List<Classifier.Recognition> mappedRecognitions =
                        new LinkedList<Classifier.Recognition>();

                for (final Classifier.Recognition result : results) {
                    final RectF location = result.getLocation();
                    if (result.getTitle().equals("person") && location != null && result.getConfidence() >= minimumConfidence) {
                        //App.receivedNotification = false;
                        ans = false;
                        canvas.drawRect(location, paint);
                        cropToFrameTransform.mapRect(location);
                        result.setLocation(location);
                        mappedRecognitions.add(result);
                        if(displayPosition!=null) {
                            Point nPoint = new Point(displayPosition[0],displayPosition[1],(displayPosition[2]+180));
                            ans = PointDetectAction(nPoint);

                            Log.d(" RESULT0 ", "\nRESULT SHOW"+"\nCurrent Detection->" +
                                    "\n X: " + displayPosition[0]+" Y: " + displayPosition[1] +
                                    "\n X: " + currentDetectPoint.x+" Y: " + currentDetectPoint.y +
                                    "\nUPDATEs Detection: " + ans);

                            if(ans){
                                //objectIn9Sectors(location);
                                MainActivity.faceDetected = true;
                                robotWorkStatus = 1;
                                //App.receivedNotification  = false;
                                Log.d(" RESULT1 ","X: " + displayPosition[0]+" Y: " + displayPosition[1]);
                            }

                        }

                    }
                }

                if (MainActivity.faceDetected == true){//CameraActivity.faceDetected == true) {
                    DateFormat dateFormat = new SimpleDateFormat("yyyymmdd_hhmmss");
                    Date date = new Date();
                    BitmapHandler bitmapHandler = new BitmapHandler(cropCopyBitmap, dateFormat.format(date) + ".jpg", 1L);
                    Log.i("FileFormat", dateFormat.format(date) + ".jpg");
                    try {
                        bitmapHandler.save();
//						bitmapHandler.uploadFile("192.168.21.236", "robotmanager", 9300, "robotmanager", "sdcard/" + bitmapHandler.getFilename(), "/home/godzilla/mount/web/html/sftp/NCS");
//                bitmapHandler.uploadFile("192.168.21.194","sftpuser", 22,"q1w2e3r4","sdcard/" + bitmapHandler.getFilename(),"/data/sftpuser/upload");
                        bitmapHandler.uploadFile("172.18.4.35", "robotmanager", 9300, "robotmanager", "sdcard/" + bitmapHandler.getFilename(), "/home/godzilla/mount/web/html/sftp/NCS");
                        if (mqttHelper.isMqttConnected() && ans) {
                            Log.i(TAG, "detected something");
                            NavigationApi.get().stopNavigationService();
                            mqttHelper.publishRbNotification("Human Detected", bitmapHandler.getFilename(), "5c899c07-7b0a-4f1c-810e-f4bb419e1547");
                        }else{
                            Log.w(TAG, "Error, mqtt not connected!");
                        }

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                computingDetection = false;

                //tracker.trackResults(mappedRecognitions, currTimestamp);
                //trackingOverlay.postInvalidate();

							/*computingDetection = false;
							((Activity)SessionBuilder.getInstance().getContext()).runOnUiThread(
									new Runnable() {
										@Override
										public void run() {
											*//*screenshot.setImageBitmap(cropCopyBitmap);

											showFrameInfo(previewWidth + "x" + previewHeight);
											showCropInfo(cropCopyBitmap.getWidth() + "x" + cropCopyBitmap.getHeight());
											showInference(lastProcessingTimeMs + "ms");*//*
										}
									});*/
/*
							// ----------------received notification------------------
							if (receivedNotification == true) {
								JSONObject jsonObject;
								try {
									jsonObject = new JSONObject(payload.toString());
									String toastMessage = jsonObject.getString("status").equals("acknowledged") ? "Notification Acknowledged!" : payload;
									Log.i("DectectorActivity", toastMessage);
									Toast.makeText(DetectorActivity.this, toastMessage, Toast.LENGTH_LONG).show();
								} catch (Exception e) {
									e.printStackTrace();
								}
								//Toast.makeText(DetectorActivity.this, payload, Toast.LENGTH_LONG).show();
								receivedNotification = false;
							}*/
            }

        }
    };

    public static int CAMERADISTANCETODETECT = 107; //107 = 5 meters; ensure camera can only view 5 meters
    public static Point currentDetectPoint = null;

    private double distance(Point current, Point checkPoint){
        return Math.sqrt(Math.pow((current.x-checkPoint.x),2)+ Math.pow((current.y-checkPoint.y),2));
    }
    public boolean PointDetectAction(Point p){
        if(currentDetectPoint == null || distance(currentDetectPoint,p)>CAMERADISTANCETODETECT){
            currentDetectPoint = p;
            return true;
        }return false;
    }

}


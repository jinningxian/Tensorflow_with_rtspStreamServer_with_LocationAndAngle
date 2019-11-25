package com.ncs.rtspstream;

import android.app.Activity;
import android.app.Application;
import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;

import com.ncs.rtspstream.Models.RobotTask;
import com.ncs.rtspstream.Utils.MapPointHelper;
import com.ubtechinc.cruzr.sdk.dance.DanceConnectionListener;
import com.ubtechinc.cruzr.sdk.dance.DanceControlApi;
import com.ubtechinc.cruzr.sdk.navigation.NavigationApi;
import com.ubtechinc.cruzr.sdk.navigation.model.MapModel;
import com.ubtechinc.cruzr.sdk.navigation.model.MapPointModel;
import com.ubtechinc.cruzr.sdk.navigation.utils.Convert;
import com.ubtechinc.cruzr.sdk.navigation.utils.MyLogger;
import com.ubtechinc.cruzr.sdk.ros.RosConstant;
import com.ubtechinc.cruzr.sdk.ros.RosRobotApi;
import com.ubtechinc.cruzr.sdk.speech.SpeechRobotApi;
import com.ubtechinc.cruzr.serverlibutil.aidl.BatteryInfo;
import com.ubtechinc.cruzr.serverlibutil.aidl.Position;
import com.ubtechinc.cruzr.serverlibutil.interfaces.InitListener;
import com.ubtechinc.cruzr.serverlibutil.interfaces.NavigationApiCallBackListener;
import com.ubtechinc.cruzr.serverlibutil.interfaces.RemoteCommonListener;
import com.ubtechinc.cruzr.serverlibutil.interfaces.RemoteDiagnosticDataListener;
import com.ubtechinc.cruzr.serverlibutil.interfaces.SpeechTtsListener;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

import detection.MediaFileHandler;
import detection.MqttHelper;
import rx.Observable;
import rx.functions.Action1;
import rx.subscriptions.CompositeSubscription;

public class App extends Application {

    public static final String INITIAL_MAP = "5ggarage";
    public static int currentLevel = 3;

    private static final String TAG = "App";
    private static final String INSIDE_ELEVATOR_POS = "lift";
    public static App app;
    private List<MapPointModel> mapPointModels = new ArrayList<>();
    private int mSetCurMapSectionId = -1;
    private int mSetLocalizeId = -1;
    public static MqttHelper mqttHelper;
    private static final int interval = 1000; // Every 1 second
    Handler mHandler = new Handler();

    // for passing the robot location to the directory app when robot finished the task
    public boolean taskFromRobot = false;
    public String placeName;
    public static int destinationLevel;
    public static int xPositionForMap;
    public static int yPositionForMap;

    private CompositeSubscription subscriptions = new CompositeSubscription();

    //FOR UI TO STORE CURRENT CONFIG
    public static boolean directory = true;
    public static boolean faq = true;
    public static boolean tour = true;
    public static boolean registor = true;
    public boolean cancelTask = false;
    public static String mainBackground = "blue";
    public static String logo = "ncs_logo";
    //public static ArrayList<Question> questions = new java.util.ArrayList<>();
    public static int robotWorkStatus = 0; // 0 = idle, 1 = robot is working
    private RobotTask robotTask;
    public Activity activity;

    public MapPointHelper mapPointHelper;
    public static List<String> danceNameList;

    public void onCreate() {
        super.onCreate();
        app = this;
        startMqtt();


        // ------ Robot Initialisation ------------------------------
        RosRobotApi.get().initializ(this, new InitListener() {
            @Override
            public void onInit() {
                // Initialization successful
            }
        });
        NavigationApi.get().initializ(this);
        NavigationApi.get().setNavigationApiCallBackListener(navigationApiCallBackListener);
        SpeechRobotApi.get().initializ(getApplicationContext(), 9010, new InitListener() {
            @Override
            public void onInit() {

            }
        });
        DanceControlApi.getInstance().initialize(this, new DanceConnectionListener() {
            @Override
            public void onConnected() {
                danceNameList = DanceControlApi.getInstance().getDanceList();
            }

            @Override
            public void onDisconnected() {

            }

            @Override
            public void onReconnected() {

            }
        });
        registfeedback();
        // -----------------------------------------------------------

        //MapPointHashMap.initializeHashMap();
        mapPointHelper = new MapPointHelper();
        mapPointHelper.initialiseMapID();
        //initSpeechRelatedAI();

    }

    // -------------------- For MQTT -----------------------------
    //public static MqttHelper mqttHelper;
    public boolean receivedNotification = false;
    public String payload;

    public void startMqtt() {
        mqttHelper = new MqttHelper(getApplicationContext());
        mqttHelper.setCallback(new MqttCallbackExtended() {
            @Override
            public void connectComplete(boolean reconnect, String serverURI) {
                Log.w(TAG, serverURI);
            }

            @Override
            public void connectionLost(Throwable cause) {

            }

            @Override
            public void messageArrived(String topic, MqttMessage message) throws Exception {
                //Log.w(TAG, message.toString());
                //-----------------Robot Acknowledge-----------------------------------
                if (topic.equals(mqttHelper.subscribeRobotNotificationTopic)) {
                    //System.out.println("MQTT " + message);
                    payload = message.toString();
                    //JSONObject jsonObject= new JSONObject(message.toString());
                    //Log.i("CameraActivity", jsonObject.getString("status"));
                    //if (jsonObject.getString("status").equals("acknowledged"))
                    receivedNotification = true;
                    //else System.out.println("Not acknowledged!");
                } else if (topic.equals(mqttHelper.subscribeTaskTopic)) {
                    System.out.println("MQTT " + message);
                    getTask(message);
                    /*
                    TODO use back this method to update FAQ list
                     */
                    //updateFaqlist(mqttMessage);
                }
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {

            }
        });
    }

    //------------------------------- Callback function for RosRobotApi---------------------

    public int remoteCommonListenerSectionId =0;
    public String remoteCommonListenerMessage = "";

    private RemoteCommonListener remoteCommonListener = new RemoteCommonListener() {
        @Override
        public void onResult(int sectionId, int status, String message) {
            Log.d("ROS", "remotecommonlistener sectionId = " + sectionId + ", status = " + status);

            // this is for other usage that you want to get back the status
            if(remoteCommonListenerSectionId == sectionId) {
                switch (status) {
                    case RosConstant.Action.ACTION_START:
                        TtsPlay("Start " + remoteCommonListenerMessage);
                        break;
                    case RosConstant.Action.ACTION_FINISHED:
                        TtsPlay(remoteCommonListenerMessage + " Successfully");
                        break;
                    case RosConstant.Action.ACTION_CANCEL:
                        TtsPlay("Cancel " + remoteCommonListenerMessage);
                        break;
                    case RosConstant.Action.ACTION_BE_IMPEDED:
                        TtsPlay(remoteCommonListenerMessage + " Impeded");
                        break;
                    case RosConstant.Action.ACTION_FAILED:
                        TtsPlay(remoteCommonListenerMessage + " Failed, please move to the initial pos and try again.");
                        break;
                    default:
                        break;
                }
                return;
            }

            if (mSetCurMapSectionId == sectionId) {
                Log.d("ROS", "remotecommonlistener " + status);
                switch (status) {
                    case RosConstant.Action.ACTION_START:
                        Log.d("ROS", "setmap remotecommonlistener actionStart");

//                        // Set Map does not have ACTION_START Callback
//                        Log.d("ROS", "setmap remotecommonlistener actionStart");
//                        switch (robotTask.getTaskType()) {
//                            case "LOCALIZE":
//                                mqttHelper.publishTaskStatus("LOCALIZE", "EXECUTING", "");
//                                break;
//                            default:
//                                break;
//                        }
                        break;
                    case RosConstant.Action.ACTION_FINISHED:
                        Log.d("ROS", "setmap remotecommonlistener actionFinish");
                        // Get Lift Position from Switched Map
                        //MapPointHashMap.setMapPointPos(robotTask.getPositionName(), pn, RosRobotApi.get().getCurrentMap());
                        float [] pos = mapPointHelper.getMapPointPos(robotTask.getPositionName(),RosRobotApi.get().getCurrentMap());
                        // Force localize
                        mSetLocalizeId = RosRobotApi.get().syncToRos(pos[0], pos[1], pos[2]);

                        break;
                    case RosConstant.Action.ACTION_CANCEL:
                        Log.d("ROS", "setmap remotecommonlistener actionCancel");
                        switch (robotTask.getTaskType()) {
                            case "LOCALIZE":
                                Log.d("ROS", "SynctoRos action cancel");
                                mqttHelper.publishTaskStatus("LOCALIZE", "CANCELLED", "{\"reasonFail\":\"Action Cancelled\"}");
                                break;
                            default:
                                break;
                        }
                        break;
                    case RosConstant.Action.ACTION_BE_IMPEDED:
                        Log.d("ROS", "setmap remotecommonlistener actionImpeded");
                        switch (robotTask.getTaskType()) {
                            case "LOCALIZE":
                                Log.d("ROS", "SynctoRos action impeded");
                                mqttHelper.publishTaskStatus("LOCALIZE", "EXECUTING", "{\"reasonFail\":\"Action Impeded\"}");
                                break;
                            default:
                                break;
                        }
                        break;
                    case RosConstant.Action.ACTION_FAILED:
                    case RosConstant.Action.ACTION_DEVICE_CONFLICT:
                        Log.d("ROS", "setmap remotecommonlistener actionFail");
                        switch (robotTask.getTaskType()) {
                            case "LOCALIZE":
                                Log.d("ROS", "SynctoRos action failed");
                                mqttHelper.publishTaskStatus("LOCALIZE", "FAILED", "{\"reasonFail\":\"Action Failed\"}");
                                break;
                            default:
                                break;
                        }
                        break;
                    case RosConstant.Action.ACTION_EMERGENCY_STOPPED:
                        Log.d("ROS", "setmap remotecommonlistener actionFail");
                        switch (robotTask.getTaskType()) {
                            case "LOCALIZE":
                                Log.d("ROS", "SynctoRos action emergency stopped");
                                mqttHelper.publishTaskStatus("LOCALIZE", "FAILED", "{\"reasonFail\":\"Action Emergency Stopped\"}");
                                break;
                            default:
                                break;
                        }
                        break;
                    case RosConstant.Action.ACTION_ACCESS_FORBIDDEN:
                        Log.d("ROS", "setmap remotecommonlistener actionFail");
                        switch (robotTask.getTaskType()) {
                            case "LOCALIZE":
                                Log.d("ROS", "SynctoRos action access forbidden");
                                mqttHelper.publishTaskStatus("LOCALIZE", "FAILED", "{\"reasonFail\":\"Action Access Forbidden\"}");
                                break;
                            default:
                                break;
                        }
                        break;
                    case RosConstant.Action.ACTION_UNIMPLEMENTED:
                        Log.d("ROS", "setmap remotecommonlistener action unimplemented");
                        switch (robotTask.getTaskType()) {
                            case "LOCALIZE":
                                Log.d("ROS", "SynctoRos action unimplemented");
                                mqttHelper.publishTaskStatus("LOCALIZE", "FAILED", "{\"reasonFail\":\"Action unimplemented\"}");
                                break;
                            default:
                                break;
                        }
                        break;
                    default:
                        break;
                }
                return;
            }

            if (mSetLocalizeId == sectionId) {
                switch (status) {
                    case RosConstant.Action.ACTION_START:
                        Log.d("ROS", "SynctoRos action start");
                        break;
                    case RosConstant.Action.ACTION_FINISHED:
                        switch (robotTask.getTaskType()) {
                            case "LOCALIZE":
                                Log.d("ROS", "SynctoRos action finish");
                                mqttHelper.publishTaskStatus("LOCALIZE", "COMPLETED", "");
                                break;
                            default:
                                break;
                        }
                        break;
                    case RosConstant.Action.ACTION_CANCEL:
                        switch (robotTask.getTaskType()) {
                            case "LOCALIZE":
                                Log.d("ROS", "SynctoRos action cancel");
                                mqttHelper.publishTaskStatus("LOCALIZE", "CANCELLED", "");
                                break;
                            default:
                                break;
                        }
                        break;
                    case RosConstant.Action.ACTION_FAILED:
                        switch (robotTask.getTaskType()) {
                            case "LOCALIZE":
                                Log.d("ROS", "SynctoRos action failed");
                                mqttHelper.publishTaskStatus("LOCALIZE", "FAILED", "{\"reasonFail\":\"Failed\"}");
                                break;
                            default:
                                break;
                        }
                        break;
                    case RosConstant.Action.ACTION_BE_IMPEDED:
                        switch (robotTask.getTaskType()) {
                            case "LOCALIZE":
                                Log.d("ROS", "SynctoRos action impeded");
                                mqttHelper.publishTaskStatus("LOCALIZE", "EXECUTING", "{\"reasonFail\":\"Action Impeded\"}");
                                break;
                            default:
                                break;
                        }
                        break;
                    case RosConstant.Action.ACTION_DEVICE_CONFLICT:
                        switch (robotTask.getTaskType()) {
                            case "LOCALIZE":
                                Log.d("ROS", "SynctoRos action device conflict");
                                mqttHelper.publishTaskStatus("LOCALIZE", "FAILED", "{\"reasonFail\":\"Action Device Conflict\"}");
                                break;
                            default:
                                break;
                        }
                        break;
                    case RosConstant.Action.ACTION_EMERGENCY_STOPPED:
                        switch (robotTask.getTaskType()) {
                            case "LOCALIZE":
                                Log.d("ROS", "SynctoRos action emergency stopped");
                                mqttHelper.publishTaskStatus("LOCALIZE", "FAILED", "{\"reasonFail\":\"Action Emergency Stopped\"}");
                                break;
                            default:
                                break;
                        }
                        break;
                    case RosConstant.Action.ACTION_ACCESS_FORBIDDEN:
                        switch (robotTask.getTaskType()) {
                            case "LOCALIZE":
                                Log.d("ROS", "SynctoRos action access forbidden");
                                mqttHelper.publishTaskStatus("LOCALIZE", "FAILED", "{\"reasonFail\":\"Action Forbidden\"}");
                                break;
                            default:
                                break;
                        }
                        break;
                    case RosConstant.Action.ACTION_UNIMPLEMENTED:
                        switch (robotTask.getTaskType()) {
                            case "LOCALIZE":
                                Log.d("ROS", "SynctoRos action unimplemented");
                                mqttHelper.publishTaskStatus("LOCALIZE", "FAILED", "{\"reasonFail\":\"Action Unimplemented\"}");
                                break;
                            default:
                                break;
                        }
                        break;

                    default:
                        break;
                }
                return;
            }
        }
    };
    // -----------------------------------------------------------------------------------------------

    //---------------------------------------Robot speech--------------------------------------------
    public void TtsPlay(String text) {
        SpeechRobotApi.get().speechStartTTS(text, new SpeechTtsListener() {
            @Override
            public void onAbort() {

            }

            @Override
            public void onEnd() {
                /*activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if(speakingIcon!=null&&mText!=null) {
                            speakingIcon.setVisibility(View.INVISIBLE);
                            mText.setVisibility(View.INVISIBLE);
                        }
                    }
                });*/
            }
        });
    }
    //-----------------------------------------------------------------------------------------------

    //------------------------------------MQTT------------------------------------------------------

    Runnable mHandlerTask = new Runnable() {

        int i = 0;

        @Override
        public void run() {
            i++;
            publishToTopic();
            mHandler.postDelayed(mHandlerTask, interval);
        }
    };

    void startPublishing() {
        mHandlerTask.run();
    }

    public static int[] displayPosition;

    private MapModel mapModel;

    public MapModel queryMapModelByMapName(String mapName) {
        MyLogger.mLog().d("queryMapModelByMapName mapName:" + mapName);
        if (TextUtils.isEmpty(mapName)) {
            MyLogger.mLog().e("queryMapModelByMapName error: mapName is null.");
            return null;
        } else {
            ContentResolver contentResolver = getContentResolver();
            Uri uri = Uri.parse("content://com.ubtechinc.cruzr.map.mapProvider/map");
            String[] projection = null;
            String selection = "map_name = ?";
            String[] selectionArgs = new String[]{mapName};
            Cursor cursor = contentResolver.query(uri, (String[]) projection, selection, selectionArgs, (String) null);
            MapModel mapModel = null;
            if (cursor != null && cursor.moveToFirst()) {
                mapModel = new MapModel();
                mapModel.setId(cursor.getInt(cursor.getColumnIndex("id")));
                mapModel.setMap_name(cursor.getString(cursor.getColumnIndex("map_name")));
                mapModel.setMap_original(cursor.getString(cursor.getColumnIndex("map_original")));
                mapModel.setMap_modify(cursor.getString(cursor.getColumnIndex("map_modify")));
                mapModel.setMap_data_url(cursor.getString(cursor.getColumnIndex("map_data_url")));
                mapModel.setResolution(cursor.getString(cursor.getColumnIndex("resolution")));
                mapModel.setBmp_x(cursor.getString(cursor.getColumnIndex("bmp_x")));
                mapModel.setBmp_y(cursor.getString(cursor.getColumnIndex("bmp_y")));
                mapModel.setBmp_w(cursor.getString(cursor.getColumnIndex("bmp_w")));
                mapModel.setBmp_h(cursor.getString(cursor.getColumnIndex("bmp_h")));
                mapModel.setDisplay_w(cursor.getString(cursor.getColumnIndex("display_w")));
                mapModel.setDisplay_h(cursor.getString(cursor.getColumnIndex("display_h")));
                mapModel.setRail_mode(cursor.getString(cursor.getColumnIndex("rail_mode")));
                mapModel.setRail_mode_in_use(cursor.getString(cursor.getColumnIndex("rail_mode_in_use")));
                boolean isCruiserRandom = cursor.getInt(cursor.getColumnIndex("cruiser_random")) == 1;
                mapModel.setCruiser_random(isCruiserRandom);
                cursor.close();
            }

            MyLogger.mLog().d("mapModel:" + (mapModel == null ? "is null!" : Convert.toJson(mapModel)));
            return mapModel;
        }
    }

    public void publishToTopic() {
        mapModel = queryMapModelByMapName(RosRobotApi.get().getCurrentMap());
        BatteryInfo batteryInfo = RosRobotApi.get().getBattery();
        Position currentPosition = RosRobotApi.get().getPosition(true);
        if (null == mapModel || null == currentPosition) {
            return;
        }
        String mapVersionId = mapPointHelper.getMapVersionIdFromMapName(RosRobotApi.get().getCurrentMap());
        System.out.println("Xpos: " + currentPosition.x + "Ypos: " + currentPosition.y + "Heading: " + currentPosition.theta);
        displayPosition = convertMapPositionToDisplayPosition(mapModel, currentPosition);
        Log.d(TAG, "MAIN Battery Info is " + batteryInfo.battery);
        Log.d(TAG,"MAIN MapVerID is " + mapVersionId);
        Log.d(TAG, "MAIN Position X is " + displayPosition[0]);
        Log.d(TAG, "MAIN Position Y is " + displayPosition[1]);
        Log.d(TAG, "MAIN Position Heading is " + displayPosition[2]);
//        mqttHelper.publishRobotStatus(batteryInfo.battery, robotTask.getMapVerId(), displayPosition[0], displayPosition[1], displayPosition[2]);
        mqttHelper.publishRobotStatus(batteryInfo.battery, mapVersionId, displayPosition[0], displayPosition[1], displayPosition[2]);
        Log.d(" Rbot ", ""+ displayPosition[0]+" "+displayPosition[1]+" " + displayPosition[2] );
    }

    // For Ubtech
    public int[] convertMapPositionToDisplayPosition(MapModel mapModel, Position position) {
        if (null == mapModel || null == position) {
            MyLogger.mLog().e("convertMapPositionToDisplayPosition mapModel or position is null! " +
                    "Return null!");

            return null;
        }

        System.out.println("Current Pos Coordinate x: " + position.x + " y: " + position.y);

        float resolution = Float.valueOf(mapModel.getResolution());
        float mapLeftTopX = Float.valueOf(mapModel.getBmp_x());
        float mapLeftTopY = Float.valueOf(mapModel.getBmp_y());

        int x = (int) (-mapLeftTopX + position.x / resolution);
        int y = (int) (mapLeftTopY - position.y / resolution);
        int theta = (int) (180 * position.theta / Math.PI);

        return new int[]{x, y, theta};
    }

    public void registfeedback() {
        // It takes about 2s to initialize the RosRobotApi then you can register common callback
        subscriptions.add(Observable.timer(10000, TimeUnit.MILLISECONDS).subscribe(new Action1<Long>() {
            @Override
            public void call(Long aLong) {
                Log.d("Testing", "In the call() function ");
                RosRobotApi.get().registerCommonCallback(remoteCommonListener);
                RosRobotApi.get().registerDiagnosticDataCallback(new RemoteDiagnosticDataListener() {
                    @Override
                    public void onResult(int key, String info) {
                        Log.i("TAG", "key =" + key + "info" + info);
                    }
                });

                //TODO: Change to use status check for connection
                if (mqttHelper.isMqttConnected()) {
                    startPublishing();
                }

                //TODO:
//                setProperty("nav.mode", "2");

//                // Get Map Point by Position Name from Targeted Map
//                List<MapPointModel> mapPointModels = NavigationApi.get().queryAllMapPointByMapName("A1Map");
//                for (MapPointModel point : mapPointModels) {
//                    if (point.getPointName().equals("InsideLiftA")) {
//                        // Divide 100: Conversion from meters to centimeters
//                        Log.d(TAG, "MapModel X is : " + point.getMapX());
//                        Log.d(TAG, "MapModel Y is : " + point.getMapY());
//                        Log.d(TAG, "MapModel Theta is : " + point.getTheta());
//                    }
//                }
            }
        }));
    }

    private boolean endGOTO;

    private NavigationApiCallBackListener navigationApiCallBackListener = new NavigationApiCallBackListener() {
        @Override
        public void onNavigationResult(int status, float x, float y, float theta) {
            MyLogger.mLog().d("onNavigationResult x:" + x + ", y:" + y + ", theta:" + theta + ", on going status:" + status);
            // play music here (ENDGOTO)
        }

        //DirectoryActivity d = new DirectoryActivity();

        @Override
        public void onRemoteCommonResult(String pointName, int status, String message) {
            switch (status) {
                case RosConstant.Action.ACTION_START:
                    robotWorkStatus = 1;
                    Log.d("ROS","Navigation Start " + robotTask.getPositionName());
                    switch (robotTask.getTaskType()) {
                        case "GOTO":
                            mqttHelper.publishTaskStatus("GOTO", "EXECUTING", "");
                            Log.d("ROS","GOTO Start " + robotTask.getPositionName());

                            if(!activity.getClass().getSimpleName().equals("RobotWorkingSplashActivity")){
                                Log.d("ROS","Activity to close " + activity.getClass().getSimpleName());

                               /* // if it is not at robotworkingsplashmeansrobotis not busy previously so i need to show busy states
                                Intent intent = new Intent(activity, RobotWorkingSplashActivity.class).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);;
                                if(taskFromRobot){
                                    intent.putExtra("task_from_robot",taskFromRobot);
                                    intent.putExtra("place_name", placeName);
                                    intent.putExtra("level", destinationLevel);
                                    intent.putExtra("position_x", xPositionForMap);
                                    intent.putExtra("position_y", yPositionForMap);
                                }
                                activity.startActivity(intent);*/

                            }
                            break;
                        case "GOTO_BROADCAST":
                            mqttHelper.publishTaskStatus("GOTO", "EXECUTING", "");
                            mMediaFileHandler.audioBroadcast(playFile);
                            Log.d("ROS","GOTO Start " + robotTask.getPositionName());
                        default:
                            break;
                    }
                    MyLogger.mLog().d("onResult pointName:" + pointName + "status:" + status + "Navigate Start!");
                    break;
                case RosConstant.Action.ACTION_ON_GOING:

                    robotWorkStatus = 1;

                    Log.d("ROS","Navigation ongoing " + robotTask.getPositionName());
                    MyLogger.mLog().d("onResult pointName:" + pointName + "status:" + status + "Navigate Ongoing!");
                    switch (robotTask.getTaskType()) {
                        case "GOTO":
                            Log.d("ROS","GOTO Ongoing " + robotTask.getPositionName());
                            break;
                        case "GOTO_BROADCAST":
                            Log.d("ROS","GOTO Ongoing " + robotTask.getPositionName());
                            try {
                                if (robotTask.getParameters().getString("bcastInBtw").equals("true")) {
                                    Log.i(TAG, "Playing at bcastBeforeGOTO: ");
                                    mMediaFileHandler.audioBroadcast(playFile);
                                }
                            }catch (JSONException e){
                                e.printStackTrace();
                            }
                        default:
                            break;
                    }
                    break;
                case RosConstant.Action.ACTION_FINISHED:
                    // check whether still got task.
                    if (currentTaskNo==totalTaskNo)
                    {
                        if(taskFromRobot)
                        {
                            TtsPlay("You have reached the destination, please proceed");
                            RosRobotApi.get().run("nod");

                        }
                        taskFromRobot=false;
                        robotWorkStatus = 0;
                    }
                    Log.d("ROS","Navigation action finish " + robotTask.getPositionName());
                    switch (robotTask.getTaskType()) {
                        case "GOTO":
                            //robotWorkStatus = 1;
                            if(currentTaskNo!=totalTaskNo)
                            {
                                // this is for cornered case where when robot is already at lift lobby and then ask to go other places, robot will not have
                                // action starts so we need to start the busy state in action finish
                                if(!activity.getClass().getSimpleName().equals("RobotWorkingSplashActivity")){
                                    Log.d("ROS","Activity to close " + activity.getClass().getSimpleName());

                                    /*// if it is not at robotworkingsplashmeansrobotis not busy previously so i need to show busy states
                                    Intent intent = new Intent(activity, RobotWorkingSplashActivity.class).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);;
                                    if(taskFromRobot){
                                        intent.putExtra("task_from_robot",taskFromRobot);
                                        intent.putExtra("place_name", placeName);
                                        intent.putExtra("level", destinationLevel);
                                        intent.putExtra("position_x", xPositionForMap);
                                        intent.putExtra("position_y", yPositionForMap);
                                    }
                                    activity.startActivity(intent);*/
                                }
                            }
                            // When the robot has finished a navigation task
                            //d.finish();
                            if (!pointName.contains(INSIDE_ELEVATOR_POS) && RosRobotApi.get().getProperty("nav.mode", "9999").equals("2")) {
                                // Set Normal Navigation Mode
                                setProperty("nav.mode", "0");
                            }
                            Log.d("ROS","GOTO Finished " + robotTask.getPositionName());
                            mqttHelper.publishTaskStatus(robotTask.getTaskType(), "COMPLETED", "");
                            break;

                        case "GOTO_BROADCAST":
                            //-------------------------------play audio at end
                            if(endGOTO == true) {
                                Log.i(TAG, "Playing at bcastEndGOTO");
                                //mMediaFileHandler = new MediaFileHandler(getApplicationContext());
                                mMediaFileHandler.audioBroadcast(playFile);
                            }
                            // ------------------------------end audio
                            if(currentTaskNo!=totalTaskNo)
                            {
                                // this is for cornered case where when robot is already at lift lobby and then ask to go other places, robot will not have
                                // action starts so we need to start the busy state in action finish
                                if(!activity.getClass().getSimpleName().equals("RobotWorkingSplashActivity")){
                                    Log.d("ROS","Activity to close " + activity.getClass().getSimpleName());
                                }
                            }
                            // When the robot has finished a navigation task
                            //d.finish();
                            if (!pointName.contains(INSIDE_ELEVATOR_POS) && RosRobotApi.get().getProperty("nav.mode", "9999").equals("2")) {
                                // Set Normal Navigation Mode
                                setProperty("nav.mode", "0");
                            }
                            Log.d("ROS","GOTO Finished " + robotTask.getPositionName());
                            mqttHelper.publishTaskStatus(robotTask.getTaskType(), "COMPLETED", "");
                            break;


                        default:
                            break;
                    }
                    MyLogger.mLog().d("onResult pointName:" + pointName + "status:" + status + "Navigate Successfully!");
                    break;
                case RosConstant.Action.ACTION_CANCEL:
                    robotWorkStatus = 0;

                    Log.d("ROS","Navigation cancel " + robotTask.getPositionName());

                    // Cancel Navigation
                    switch (robotTask.getTaskType()) {
                        case "GOTO":
                            mqttHelper.publishTaskStatus(robotTask.getTaskType(), "CANCELLED", "");
                            Log.d("ROS","GOTO Cancelled " + robotTask.getPositionName());
                            break;
                        default:
                            break;
                    }
                    MyLogger.mLog().d("onResult pointName:" + pointName + "status:" + status + "Navigate Cancelled!");
                    break;
                case RosConstant.Action.ACTION_BE_IMPEDED:
                    Log.d("ROS","Navigation impeded " + robotTask.getPositionName());

                    robotWorkStatus = 0;
                    // When something obstructing the robot
                    switch (robotTask.getTaskType()) {
                        case "GOTO":
                            mqttHelper.publishTaskStatus(robotTask.getTaskType(), "EXECUTING", "{\"reasonFail\":\"Impeded\"}");
                            Log.d("ROS","GOTO Action Impeded " + robotTask.getPositionName());
                            break;
                        default:
                            break;
                    }
                    MyLogger.mLog().d("onResult pointName:" + pointName + "status:" + status + "Navigate Obstructed, continue to retry!");
                    break;
                case RosConstant.Action.ACTION_FAILED:
                    Log.d("ROS","Navigation failed " + robotTask.getPositionName());

                    robotWorkStatus = 0;
                    // After 1 minute, if robot cannot get through, it will consider it as navigation has failed
                    switch (robotTask.getTaskType()) {
                        case "GOTO":
                            mqttHelper.publishTaskStatus(robotTask.getTaskType(), "FAILED", "{\"reasonFail\":\"Failed\"}");
                            Log.d("ROS","GOTO Failed " + robotTask.getPositionName());
                            break;
                        default:
                            break;
                    }
                    MyLogger.mLog().d("onResult pointName:" + pointName + "status:" + status + "Navigate Failed, stopping navigation!");
                    break;
                case RosConstant.Action.ACTION_ABNORMAL_SUSPEND:
                    Log.d("ROS","Navigation abnormally suspended " + robotTask.getPositionName());

                    robotWorkStatus = 0;
                    switch (robotTask.getTaskType()) {
                        case "GOTO":
                            mqttHelper.publishTaskStatus(robotTask.getTaskType(), "FAILED", "{\"reasonFail\":\"Abnormal Suspend\"}");
                            Log.d("ROS","GOTO Abnormal Suspended " + robotTask.getPositionName());
                            break;
                        default:
                            break;
                    }
                    break;
                case RosConstant.Action.ACTION_DEVICE_CONFLICT:
                    robotWorkStatus = 0;
                    Log.d("ROS","Navigation device conflict " + robotTask.getPositionName());
                    switch (robotTask.getTaskType()) {
                        case "GOTO":
                            mqttHelper.publishTaskStatus(robotTask.getTaskType(), "FAILED", "{\"reasonFail\":\"Device Conflict\"}");
                            Log.d("ROS","GOTO Device Conflicted " + robotTask.getPositionName());
                            break;
                        default:
                            break;
                    }
                    break;
                case RosConstant.Action.ACTION_EMERGENCY_STOPPED:
                    Log.d("ROS","Navigation emergency stopped " + robotTask.getPositionName());
                    robotWorkStatus = 0;
                    switch (robotTask.getTaskType()) {
                        case "GOTO":
                            mqttHelper.publishTaskStatus(robotTask.getTaskType(), "FAILED", "{\"reasonFail\":\"Emergency Stopped\"}");
                            Log.d("ROS","GOTO Emergency Stopped " + robotTask.getPositionName());
                            break;
                        default:
                            break;
                    }
                    break;
                case RosConstant.Action.ACTION_ACCESS_FORBIDDEN:
                    robotWorkStatus = 0;
                    Log.d("ROS","Navigation access forbidden " + robotTask.getPositionName());

                    switch (robotTask.getTaskType()) {
                        case "GOTO":
                            mqttHelper.publishTaskStatus(robotTask.getTaskType(), "FAILED", "{\"reasonFail\":\"Access Forbidden\"}");
                            Log.d("ROS","GOTO Access Forbidden " + robotTask.getPositionName());
                            break;
                        default:
                            break;
                    }


                    break;
                case RosConstant.Action.ACTION_UNIMPLEMENTED:
                    robotWorkStatus = 0;
                    switch (robotTask.getTaskType()) {
                        case "GOTO":
                            mqttHelper.publishTaskStatus(robotTask.getTaskType(), "FAILED", "{\"reasonFail\":\"Action Unimplemented\"}");
                            Log.d("ROS","GOTO unimplemented " + robotTask.getPositionName());
                            break;
                        default:
                            break;
                    }
                    break;
                default:
                    break;
            }
        }
    };

    public static String replaceBlank(String result) {
        return result.replaceAll("\\s", "");
    }

    public static int setProperty(String key, String value) {
        String result = "0";
        try {
            RosRobotApi api = (RosRobotApi) RosRobotApi.get();
            Class<?> pp = api.getClass();
            Method mm = pp.getDeclaredMethod("setProperty", new Class[]{String.class, String.class});
            mm.setAccessible(true);
//            Object[] args = new Object[]{new String[]{key, value}};
            result = (mm.invoke(api, key, value)).toString();
            result = replaceBlank(result);
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
        if (result.equals("")) {
            result = "0";
        }
        return Integer.valueOf(result);
    }

    public File playFile;

    public void robotTaskExecuteAlgo(final RobotTask robotTask, final JSONObject reader) throws JSONException {

        switch (robotTask.getTaskType()) {
            case "GOTO":
                if(robotTask.getModificationType().equals("CANCEL"))
                {
                    cancelTask = true;
                    NavigationApi.get().stopNavigationService();
                    taskFromRobot = false;
                    robotWorkStatus = 0;
                }
                else
                {
                    if(robotTask.isAbort())
                    {
                        // if abort -- true
                        if (robotTask.getPositionName().contains(INSIDE_ELEVATOR_POS) && !RosRobotApi.get().getProperty("nav.mode", "9999").equals("2")) {
                            // Set Lift Navigation Mode
                            setProperty("nav.mode", "2");
                        }
                        JSONObject parameters = reader.getJSONObject("parameters");
                        robotTask.setTts(parameters.getString("tts"));

                        TtsPlay(robotTask.getTts());
                        //MapPointHashMap.translateMapPointPos(robotTask.getPositionName(), pn);
                        NavigationApi.get().startNavigationService(robotTask.getPositionName());
                    }
                    else if(totalTaskNo>1&&currentTaskNo!=1)
                    {
                        // if it is the subtask of a full task.
                        if (robotTask.getPositionName().contains(INSIDE_ELEVATOR_POS) && !RosRobotApi.get().getProperty("nav.mode", "9999").equals("2")) {
                            // Set Lift Navigation Mode
                            setProperty("nav.mode", "2");
                        }
                        JSONObject parameters = reader.getJSONObject("parameters");
                        robotTask.setTts(parameters.getString("tts"));
                        TtsPlay(robotTask.getTts());
                        //MapPointHashMap.translateMapPointPos(robotTask.getPositionName(), pn);
                        NavigationApi.get().startNavigationService(robotTask.getPositionName());
                    }
                    else
                    {
                        //if robot is busy
                        if(robotWorkStatus==1)
                        {
                            // need to start a thread to wait until robot status is 0 then only continue to do the job here
                            final Handler taskHandler = new Handler();
                            final Timer timer = new Timer();
                            final TimerTask statusCheck = new TimerTask() {
                                @Override
                                public void run() {
                                    taskHandler.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            if(robotWorkStatus==0)
                                            {
                                                cancel();
                                                timer.cancel();
                                                if(cancelTask!=true) {
                                                    if (robotTask.getPositionName().contains(INSIDE_ELEVATOR_POS) && !RosRobotApi.get().getProperty("nav.mode", "9999").equals("2")) {
                                                        // Set Lift Navigation Mode
                                                        setProperty("nav.mode", "2");
                                                    }
                                                    JSONObject parameters = null;
                                                    try {
                                                        parameters = reader.getJSONObject("parameters");
                                                        robotTask.setTts(parameters.getString("tts"));
                                                    } catch (JSONException e) {
                                                        e.printStackTrace();
                                                    }
                                                    TtsPlay(robotTask.getTts());
                                                    //MapPointHashMap.translateMapPointPos(robotTask.getPositionName(), pn);
                                                    NavigationApi.get().startNavigationService(robotTask.getPositionName());
                                                }
                                                else
                                                {
                                                    cancelTask = false;
                                                    mqttHelper.publishTaskStatus(robotTask.getTaskType(), "CANCELLED", "{\"reasonFail\":\"Action Cancelled\"}");
                                                }
                                            }
                                        }
                                    });
                                }
                            };
                            timer.schedule(statusCheck,50,500);
                        }
                        else
                        {
                            // robotworkstatus is 0 means robot is idle, can execute the task now
                            if (robotTask.getPositionName().contains(INSIDE_ELEVATOR_POS) && !RosRobotApi.get().getProperty("nav.mode", "9999").equals("2")) {
                                // Set Lift Navigation Mode
                                setProperty("nav.mode", "2");
                            }
                            JSONObject parameters = reader.getJSONObject("parameters");
                            robotTask.setTts(parameters.getString("tts"));

                            TtsPlay(robotTask.getTts());
                            //MapPointHashMap.translateMapPointPos(robotTask.getPositionName(), pn);
                            NavigationApi.get().startNavigationService(robotTask.getPositionName());
                        }
                    }
                }
                break;
            case "LOCALIZE":
                if(robotTask.getModificationType().equals("CANCEL"))
                {
                    cancelTask = true;
                    NavigationApi.get().stopNavigationService();
                    robotWorkStatus = 0;
                    taskFromRobot = false;

                }
                else{
                    mqttHelper.publishTaskStatus("LOCALIZE", "EXECUTING", "");
                    mSetCurMapSectionId = RosRobotApi.get().setCurrentMap((mapPointHelper.mapNameIdHashMap.get(robotTask.getMapVerId())));
                    Log.d("Localise", "switch map to " + mapPointHelper.mapNameIdHashMap.get(robotTask.getMapVerId()));
                    currentLevel = mapPointHelper.getLevelByMapName((mapPointHelper.mapNameIdHashMap.get(robotTask.getMapVerId())));
                    if (mSetCurMapSectionId == 0) {
                        // Handle Set Map Failed
                        mqttHelper.publishTaskStatus("LOCALIZE", "FAILED", "");
                    }
                }

                break;
            case "GOTO_BROADCAST":
                // play audio and get sftp client here (audio handler)
                // also copy GOTO


                //-------------------------------play audio ----------------------------------------
                JSONObject parameters = robotTask.getParameters();
                playFile = new File(parameters.getString("audioVideoPath"));

                endGOTO = parameters.getString("bcastEndGOTO").equals("true"); // check if need to play at end, plays at listener
                Log.i(TAG, "File to be played is " + playFile.getName() + ", Full Path is: " + playFile.getAbsolutePath());

                Log.i(TAG, parameters.getString("bcastBeforeGOTO") + " , " + parameters.getString("bcastInBtw") + " , "  + parameters.getString("loopInBtw") + " , " + parameters.getString("bcastEndGOTO"));
                // broadcast before moving
                if (parameters.getString("bcastBeforeGOTO").equals("true")){
                    Log.i(TAG, "Playing at bcastBeforeGOTO: " + parameters.getString("bcastBeforeGOTO"));
                    mMediaFileHandler.audioBroadcast(playFile);
                }
                /*// play music during movement once, do a small delay then play sound
                if (parameters.getString("bcastInBtw").equals("true")){
                    Log.i(TAG, "Playing at bcastInBtw");
                    Runnable playSound = new Runnable() {
                        @Override
                        public void run() {
                            mMediaFileHandler.audioBroadcast(playFile);
                        }
                    };
                    Handler playInBtw = new Handler();
                    playInBtw.postDelayed(playSound, 3 * 1000);

                }*/
                // loop music during movement
                if (parameters.getString("loopInBtw").equals("true")){
                    Log.i(TAG, "Playing loop");
                    mMediaFileHandler.loopAudioBroadcast();
                }
                else if (parameters.getString("loopInBtw").equals("false")){
                    Log.i(TAG, "Stopping loop");
                    mMediaFileHandler.StopLoopAudioBroadcast();
                }

                if (parameters.getString("bcastBeforeGOTO").equals("false") && parameters.getString("bcastInBtw").equals("false") && parameters.getString("loopInBtw").equals("false")){
                    endGOTO = false;
                    mMediaFileHandler.stop();
                }
                // ------------------------------end audio------------------------------------------

                // --------------------------GOTO skillset---------------------------------------
                if(robotTask.getModificationType().equals("CANCEL"))
                {
                    cancelTask = true;
                    NavigationApi.get().stopNavigationService();
                    taskFromRobot = false;
                    robotWorkStatus = 0;
                }
                else
                {
                    if(robotTask.isAbort())
                    {
                        // if abort -- true
                        if (robotTask.getPositionName().contains(INSIDE_ELEVATOR_POS) && !RosRobotApi.get().getProperty("nav.mode", "9999").equals("2")) {
                            // Set Lift Navigation Mode
                            setProperty("nav.mode", "2");
                        }

                        NavigationApi.get().startNavigationService(robotTask.getPositionName());
                    }
                    else if(totalTaskNo>1&&currentTaskNo!=1)
                    {
                        // if it is the subtask of a full task.
                        if (robotTask.getPositionName().contains(INSIDE_ELEVATOR_POS) && !RosRobotApi.get().getProperty("nav.mode", "9999").equals("2")) {
                            // Set Lift Navigation Mode
                            setProperty("nav.mode", "2");
                        }
                        NavigationApi.get().startNavigationService(robotTask.getPositionName());
                    }
                    else
                    {
                        //if robot is busy
                        if(robotWorkStatus==1)
                        {
                            // need to start a thread to wait until robot status is 0 then only continue to do the job here
                            final Handler taskHandler = new Handler();
                            final Timer timer = new Timer();
                            final TimerTask statusCheck = new TimerTask() {
                                @Override
                                public void run() {
                                    taskHandler.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            if(robotWorkStatus==0)
                                            {
                                                cancel();
                                                timer.cancel();
                                                if(cancelTask!=true) {
                                                    if (robotTask.getPositionName().contains(INSIDE_ELEVATOR_POS) && !RosRobotApi.get().getProperty("nav.mode", "9999").equals("2")) {
                                                        // Set Lift Navigation Mode
                                                        setProperty("nav.mode", "2");
                                                    }
                                                    NavigationApi.get().startNavigationService(robotTask.getPositionName());
                                                }
                                                else
                                                {
                                                    cancelTask = false;
                                                    mqttHelper.publishTaskStatus(robotTask.getTaskType(), "CANCELLED", "{\"reasonFail\":\"Action Cancelled\"}");
                                                }
                                            }
                                        }
                                    });
                                }
                            };
                            timer.schedule(statusCheck,50,500);
                        }
                        else
                        {
                            // robotworkstatus is 0 means robot is idle, can execute the task now
                            if (robotTask.getPositionName().contains(INSIDE_ELEVATOR_POS) && !RosRobotApi.get().getProperty("nav.mode", "9999").equals("2")) {
                                // Set Lift Navigation Mode
                                setProperty("nav.mode", "2");
                            }
                            NavigationApi.get().startNavigationService(robotTask.getPositionName());
                        }
                    }
                }
                // ------------------------------end GOTO skillset----------------------------------
                break;
            default:
                break;
        }
    }
    // mediafilehandler for SFTP audio broadcast
    public static MediaFileHandler mMediaFileHandler;

    // context variable
    //public static Context context;

    /**
     * Extract the task received from mqtt and output the action
     *
     * @param mqttMessage
     * @throws JSONException
     */
    private int totalTaskNo =0;
    private int currentTaskNo=0;

    private void getTask(MqttMessage mqttMessage) throws JSONException {
        JSONObject reader = new JSONObject(mqttMessage.toString());
        robotTask = new RobotTask();
        robotTask.setModificationType(reader.getString("modificationType"));
        robotTask.setTaskType(reader.getString("taskType"));
        robotTask.setAbort(reader.getBoolean("abort"));
        totalTaskNo = (reader.getInt("totalTaskNo"));
        currentTaskNo = (reader.getInt("currTaskNo"));
        JSONObject point = reader.getJSONObject("point");
        robotTask.setMapVerId(point.getString("mapVerID"));
        robotTask.setPositionName(point.getString("positionName"));
        robotTask.setX(point.getDouble("x"));
        robotTask.setY(point.getDouble("y"));
        robotTask.setHeading(point.getInt("heading"));
        robotTask.setParameter(reader.getJSONObject("parameters"));
        Log.d(TAG, "Received Robot Task: " + "modificationType:" +robotTask.getModificationType() + " taskType:"+ robotTask.getTaskType() + " mapVerID:" + robotTask.getMapVerId() + " positionName:" + robotTask.getPositionName() + " x:" + robotTask.getX() + " y:" + robotTask.getY() + " heading:" + robotTask.getHeading());

        robotTaskExecuteAlgo(robotTask,reader);
//        if (robotTask.getModificationType().equals("CREATE") || robotTask.getModificationType().equals("TRIGGER")) {
//            switch (robotTask.getTaskType()) {
//                case "GOTO":
//                    if (robotTask.getPositionName().contains(INSIDE_ELEVATOR_POS) && !RosRobotApi.get().getProperty("nav.mode", "9999").equals("2")) {
//                        // Set Lift Navigation Mode
//                        setProperty("nav.mode", "2");
//                    }
//                    JSONObject parameters = reader.getJSONObject("parameters");
//                    robotTask.setTts(parameters.getString("tts"));
//
//                    TtsPlay(robotTask.getTts());
//                    //MapPointHashMap.translateMapPointPos(robotTask.getPositionName(), pn);
//
//                    NavigationApi.get().startNavigationService(robotTask.getPositionName());
//
//                    break;
//                case "LOCALIZE":
//                    mqttHelper.publishTaskStatus("LOCALIZE", "EXECUTING", "");
//                    mSetCurMapSectionId = RosRobotApi.get().setCurrentMap((mapPointHelper.mapNameIdHashMap.get(robotTask.getMapVerId())));
//                    Log.d("Localise", "switch map to " + mapPointHelper.mapNameIdHashMap.get(robotTask.getMapVerId()));
//
//                    if (mSetCurMapSectionId == 0) {
//                        // Handle Set Map Failed
//                        mqttHelper.publishTaskStatus("LOCALIZE", "FAILED", "");
//                    }
//                    break;
//                default:
//                    break;
//            }
//        } else {        // CANCEL
//            RosRobotApi.get().stopMove();
//            NavigationApi.get().stopNavigationService();
//        }
    }

    //---------------------------map things--------------------------------------------------------
    public static List<MapPointModel> getAllMapPointModelByMapName(String mapName) {
        List<MapPointModel> mapPointModels = NavigationApi.get().queryAllMapPointByMapName(mapName);
        return mapPointModels;
    }
    public void getNavigationPlaces(String userInput){
        // search
        //mDirectoryViewModel = ViewModelProviders.of(SearchActivity.class).get(PoiViewModel.class);

        //LiveData<List<PoiEntity>> poiEntities = mDirectoryViewModel.getSearchResult(userInput);// Updates new lists of results as user types
        // set destination
        //poi = poiEntities.getValue().get(0);


        List<MapPointModel> mapPointModelList = getAllMapPointModelByMapName(RosRobotApi.get().getCurrentMap());
        for(int i=0;i<mapPointModelList.size();i++)
        {   //currentLevel = 3;
            if(mapPointModelList.get(i).getPointName().equals(userInput))
            {
                // this is at current level
                RobotTask robotTask = new RobotTask();
                robotTask.setTaskType("GOTO");
                robotTask.setMapVerId(App.app.mapPointHelper.getMapVersionIdFromMapName(App.app.mapPointHelper.mapNameByLevelHashmap.get(3)));
                robotTask.setPositionName(userInput);
                robotTask.setX(0);
                robotTask.setY(0);
                robotTask.setHeading(0);
                robotTask.setTts("Please follow me");

                App.app.taskFromRobot = true;
                App.app.placeName = userInput;
                App.app.xPositionForMap = 0;
                App.app.yPositionForMap = 0;
                App.app.destinationLevel =currentLevel;

                App.app.mqttHelper.publishRobotTaskRequest(true,false,"HIGH",robotTask);//TODO: StartLiftService
                App.app.robotWorkStatus = 1;
                return;
            }
        }
        // robot cannot find a place
        TtsPlay("Sorry, I cannot get there yet");

        //TtsPlay("Sorry, I can't bring you there");

//        if(poi.getLevel()==App.app.currentLevel)
//        {
//            // start task
//            App.app.TtsPlay("Please follow me");
//            //NavigationApi.get().startNavigationService(translatorPlace(placeName));
//
//            RobotTask robotTask = new RobotTask();
//            robotTask.setTaskType("GOTO");
//            robotTask.setMapVerId(App.app.mapPointHelper.getMapVersionIdFromMapName(App.app.mapPointHelper.mapNameByLevelHashmap.get(poi.getLevel())));
//            robotTask.setPositionName(poi.getPlaceName());
//            robotTask.setX(0);
//            robotTask.setY(0);
//            robotTask.setHeading(0);
//            robotTask.setTts("");
//
//            App.app.taskFromRobot = true;
//            App.app.placeName = poi.getPlaceName();
//            App.app.xPositionForMap = poi.getX();
//            App.app.yPositionForMap = poi.getY();
//            App.app.destinationLevel = poi.getLevel();
//
//            App.app.mqttHelper.publishRobotTaskRequest(true,false,"HIGH",robotTask); // Just navigate on same floor.
//            App.app.robotWorkStatus = 1;
//        }
//        else
//        {
//            App.app.TtsPlay("We need to take the lift");
//            RobotTask robotTask = new RobotTask();
//            robotTask.setTaskType("GOTO");
//            robotTask.setMapVerId(App.app.mapPointHelper.getMapVersionIdFromMapName(App.app.mapPointHelper.mapNameByLevelHashmap.get(poi.getLevel())));
//            robotTask.setPositionName(poi.getPlaceName());// set position name
//            robotTask.setX(0);
//            robotTask.setY(0);
//            robotTask.setHeading(0);
//            robotTask.setTts("");
//
//            App.app.taskFromRobot = true;
//            App.app.placeName = poi.getPlaceName();
//            App.app.xPositionForMap = poi.getX();
//            App.app.yPositionForMap = poi.getY();
//            App.app.destinationLevel = poi.getLevel();
//
//            App.app.mqttHelper.publishRobotTaskRequest(true,true,"HIGH",robotTask);//TODO: StartLiftService
//            App.app.robotWorkStatus = 1;
//        }
        //List<PoiEntity> pointEntities2 = new PoiViewModel().getSearchResult()
    }


    public PoiEntity poi;

    public String translatorPlace(String place_name)
    {
        String markerName = place_name;

        if (place_name.equals("A1-1")) {
            markerName = place_name.replace(place_name, "Aoneone");
        }

        else if (place_name.equals("A1-2")) {
            markerName = place_name.replace(place_name, "Aonetwo");
        }

        else if (place_name.equals("A1-3")) {
            markerName = place_name.replace(place_name, "Aonethree");
        }
        else if (place_name.equals("Eduroom")) {
            markerName = place_name.replace(place_name, "Eduroom");
        }
        else if (place_name.equals("Healthcare Room")) {
            markerName = place_name.replace(place_name, "Healthcare");
        }
        else if (place_name.equals("Desk A")) {
            markerName = place_name.replace(place_name, "deska");
        }
        else if (place_name.equals("Desk B")) {
            markerName = place_name.replace(place_name, "deskb");
        }
        else if (place_name.equals("Discussion Area")) {
            markerName = place_name.replace(place_name, "discussionarea");
        }
        else if (place_name.equals("Presentation Area")) {
            markerName = place_name.replace(place_name, "presentation");
        }
        else if (place_name.equals("Work Area")) {
            markerName = place_name.replace(place_name, "workarea");
        }

        return markerName;
    }

    // ---------------------------Timeout class from conceirgeMain.class---------------------------------
    Runnable r;
    Handler timeOutHandler;
    public boolean timeout = false;

    public void timeOut() {
        timeOutHandler = new Handler();
        r = new Runnable() {
            @Override
            public void run() {
                Log.i(TAG, "Timeout Handler running, timeout is now: " + timeout);
                timeout = true;
            }
        };
        startHandler();
    }

    /**
     * Start a 10 second handler
     */
    public void startHandler() {
        Log.i(TAG, "Previously, timeout is:" + timeout);
        if (timeout == true){ timeout = false;}
        timeOutHandler.postDelayed(r, 10 * 1000);
    }

    public void stopHandler() {
        timeout = false;
        timeOutHandler.removeCallbacks(r);
    }



}

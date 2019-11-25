package detection;

import android.content.Context;
import android.util.Log;

import com.ncs.rtspstream.Models.RobotTask;

import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.DisconnectedBufferOptions;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.MqttPersistenceException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static com.ncs.rtspstream.App.destinationLevel;
import static com.ncs.rtspstream.App.displayPosition;
import static com.ncs.rtspstream.App.xPositionForMap;
import static com.ncs.rtspstream.App.yPositionForMap;

/**
 * This class establish the mqtt connection to the robot adaptor.
 * It also publish and subscribe to the robot adaptor topics
 *
 * @author Kelvin Khoo
 */
public class MqttHelper {

    private static final String TAG = "MqttHelper";
//    private static final String serverUri = "tcp://192.168.21.194:1883";                 // Intel NUC broker
//    private static final String serverUri = "tcp://192.168.21.57:1883";                 // SIT: localhost

    private static final String serverUri = "tcp://127.0.0.1:1883";                 // SIT: localhost
//    private static final String serverUri = "tcp://172.18.4.32:1883";                 // SIT: KY's Laptop
//    private static final String serverUri = "tcp://192.168.21.241";              // NCS: KY's Laptop
//    private static final String serverUri = "tcp://192.168.21.36:1883";               // Robot's Adaptor
//    private static final String serverUri = "tcp://postman.cloudmqtt.com:11516";   // CloudMQTT
//    private static final String serverUri = "tcp://192.168.21.72:1883";                 // Bryan Laptop broker
    private static final String username = "ymikzszg";
    private static final String password = "NI6tT_zFV1DF";

    private MqttAndroidClient client;
    private String clientId = MqttClient.generateClientId();
    private String publishRobotNotificationTopic = "RbNotification";
    private String publishRobotStatusTopic = "RbStatus";
    private String publishTaskStatusTopic = "RbTaskStatus";
    private String publishTaskRequestTopic = "RbTaskRequest";
    public String subscribeRobotNotificationTopic = "NotificationResp"; //"RtTask";
    public String subscribeTaskRequestStatusTopic = "RbTaskRequestStatus";
    public String subscribeFaqTopic = "test/message";
    public String subscribeConciergeProfileTopic = "test/main";

    // ------------------------------------copy from concierge
//    private String clientId = MqttClient.generateClientId();
//    private String publishRobotStatusTopic = "RbStatus";
//    private String publishTaskStatusTopic = "RbTaskStatus";
//    private String publishTaskRequestTopic = "RbTaskRequest";
    public String subscribeTaskTopic = "RtTask";
//    public String subscribeTaskRequestStatusTopic = "RbTaskRequestStatus";
//    public String subscribeFaqTopic = "test/message";
//    public String subscribeConciergeProfileTopic = "test/main";



    /**
     * Setup an MQTT Object to establish a new connection to the robot adaptor
     * @param context
     */
    public MqttHelper(Context context) {
//        client = new MqttAndroidClient(context, serverUri, clientId);
        client = new MqttAndroidClient(context, serverUri, clientId, new MemoryPersistence(), MqttAndroidClient.Ack.AUTO_ACK);
        client.setCallback(new MqttCallbackExtended() {
            @Override
            public void connectComplete(boolean reconnect, String serverURI) {
                Log.w(TAG, serverURI);
            }

            @Override
            public void connectionLost(Throwable cause) {

            }

            @Override
            public void messageArrived(String topic, MqttMessage message) throws Exception {
                Log.w(TAG, message.toString());

            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {

            }
        });
        connect();
    }

    /**
     * Let the MQTTHelper set the mqtt callback functions
     * @param callback
     */
    public void setCallback(MqttCallbackExtended callback) {
        client.setCallback(callback);
    }


    /**
     * Check is MQTTConnection Established
     * @return true if connected, else false
     */
    public boolean isMqttConnected()
    {
        return client.isConnected();
    }

    /**
     * To establish connection for the mqtt
     */
    private void connect() {
        MqttConnectOptions options = new MqttConnectOptions();
        options.setAutomaticReconnect(true);
        options.setCleanSession(false);
        options.setUserName(username);

        options.setPassword(password.toCharArray());

        try {
            client.connect(options, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    // We are connected
                    DisconnectedBufferOptions disconnectedBufferOptions = new DisconnectedBufferOptions();
                    disconnectedBufferOptions.setBufferEnabled(true);
                    disconnectedBufferOptions.setBufferSize(100);
                    disconnectedBufferOptions.setPersistBuffer(true);
                    disconnectedBufferOptions.setDeleteOldestMessages(false);
                    client.setBufferOpts(disconnectedBufferOptions);
                    Log.w(TAG, "Connection is successful");
                    subscribeToTopic();
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    // Something went wrong e.g. connection timeout or firewall problems
                    Log.w(TAG, "Failed to connect to: " + serverUri + exception.toString());
                }
            });
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    /**
     * To publish robot status topic to adaptor
     *
     * @param battPercentage
     * @param mapVerID
     * @param positionX
     * @param positionY
     * @param heading
     */
    public void     publishRobotStatus(double battPercentage, String mapVerID, double positionX, double positionY, double heading) {
        try {
            JSONObject obj = new JSONObject();
            obj.put("battPercentage", battPercentage);
            obj.put("mapVerID", mapVerID);
            obj.put("positionX", positionX);
            obj.put("positionY", positionY);
            obj.put("heading", heading);

            MqttMessage message = new MqttMessage();
            message.setPayload(obj.toString().getBytes(StandardCharsets.UTF_8));
            client.publish(publishRobotStatusTopic, message);
        } catch (MqttException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }



    public void publishTaskStatus(String taskTypeName, String taskStatusType, String taskStatusDetails) {
        try {
            JSONObject obj = new JSONObject();
            obj.put("taskType", taskTypeName);
            obj.put("taskStatusType", taskStatusType);
            obj.put("taskStatusDetails", taskStatusDetails);

            MqttMessage message = new MqttMessage();
            message.setPayload(obj.toString().getBytes(StandardCharsets.UTF_8));
            client.publish(publishTaskStatusTopic, message);
        } catch (MqttException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void publishRbNotification(String Description, String imagePath, String mapVerID)
    {
        try {
            JSONObject obj = new JSONObject();
            obj.put("ID", UUID.randomUUID().toString()); //changed to java.utils.UUID
            obj.put("type","picture");
            obj.put("description","Human Detected");
            obj.put("severity", "critical");
            obj.put("status","opened");
            obj.put("mapVerID", mapVerID);
            obj.put("positionX",displayPosition[0] );
            obj.put("positionY", displayPosition[1]);
            obj.put("heading", (displayPosition[2]+180));
            obj.put("details", "");
            String s = "/sftp/NCS/";
            obj.put("imagePath", s +imagePath);
            obj.put("videoPath", "");
            MqttMessage message = new MqttMessage();
            message.setPayload(obj.toString().getBytes(StandardCharsets.UTF_8));
            client.publish(publishRobotNotificationTopic, message);
        } catch (MqttException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    /**
     * Subscriber for all the topic.
     */
    public void subscribeToTopic() {
        //String subcriptionTopic1 = "test/main";
        try {
            //client.subscribe(subcriptionTopic1,0);
            client.subscribe(subscribeFaqTopic,0);
            client.subscribe(subscribeConciergeProfileTopic,0);
            client.subscribe(subscribeTaskRequestStatusTopic,0);
            client.subscribe(subscribeTaskTopic, 0);
            client.subscribe(subscribeRobotNotificationTopic, 0, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    Log.w("Mqtt","Subscribed!");
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    Log.w("Mqtt", "Subscribed fail!");
                }
            });

        } catch (MqttException ex) {
            System.err.println("Exceptionst subscribing");
            ex.printStackTrace();
        }
    }

    /**
     * Currently only support for sending one task at a time.
     * @param abort
     * @param requestRFM
     * @param priority
     * @param task
     */
    public void publishRobotTaskRequest(boolean abort, boolean requestRFM, String priority, RobotTask task)
    {
        try{
            JSONObject obj = new JSONObject();
            obj.put("requestID","");
            obj.put("modificationType", "CREATE");
            obj.put("abort",abort);
            obj.put("requestRfm", requestRFM);
            obj.put("priority",priority);
            JSONArray tasks = new JSONArray();

            JSONObject jsonTask = new JSONObject();
            jsonTask.put("taskType",task.getTaskType());
            JSONObject point = new JSONObject();
            point.put("mapVerID",task.getMapVerId());
            point.put("positionName", task.getPositionName());
            point.put("x",task.getX());
            point.put("y",task.getY());
            point.put("heading",task.getHeading());
            jsonTask.put("point",point);
            switch (task.getTaskType())
            {
                case "GOTO":
                    JSONObject parameters = new JSONObject();
                    parameters.put("tts", task.getTts());
                    jsonTask.put("parameters",parameters);
                    break;
                default:
                    break;
            }
            tasks.put(jsonTask);
            obj.put("tasks",tasks);
            MqttMessage message = new MqttMessage();
            message.setPayload(obj.toString().getBytes(StandardCharsets.UTF_8));
            client.publish(publishTaskRequestTopic, message);
        } catch (JSONException e) {
            e.printStackTrace();
        } catch (MqttPersistenceException e) {
            e.printStackTrace();
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }
}
package com.example.mqttnotifier;

import java.util.Calendar;

import org.eclipse.paho.client.mqttv3.IMqttAsyncClient;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.MqttSecurityException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;



import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat.Builder;
import android.util.Log;
import android.widget.Toast;

public class MQTTService extends Service {

	private static final String TAG = "MQTTService";
	private static boolean hasWifi = false;
	private static boolean hasMmobile = false;
	private Thread thread;
	private ConnectivityManager mConnMan;
	private volatile IMqttAsyncClient mqttClient;
	private String deviceId; 

	class MQTTBroadcastReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context context, Intent intent) {

			IMqttToken token;
			boolean hasConnectivity = false;
			boolean hasChanged = false;
			NetworkInfo infos[] = mConnMan.getAllNetworkInfo();


			// Check if N/W connectivity is available

			for (int i = 0; i < infos.length; i++){
				if (infos[i].getTypeName().equalsIgnoreCase("MOBILE")){
					if((infos[i].isConnected() != hasMmobile)){
						hasChanged = true;
						hasMmobile = infos[i].isConnected();
					}
					Log.d(TAG, infos[i].getTypeName() + " is " + infos[i].isConnected());

				} else if ( infos[i].getTypeName().equalsIgnoreCase("WIFI") ){ 
					if((infos[i].isConnected() != hasWifi)){
						hasChanged = true;
						hasWifi = infos[i].isConnected();
					}
					Log.d(TAG, infos[i].getTypeName() + " is " + infos[i].isConnected());
				}
			}

			hasConnectivity = hasMmobile || hasWifi;
			Log.v(TAG, "hasConn: " + hasConnectivity + " hasChange: " + hasChanged + " - "+(mqttClient == null || !mqttClient.isConnected()));

			if (hasConnectivity && hasChanged && (mqttClient == null || !mqttClient.isConnected())) {
				doConnect();
			} else if (!hasConnectivity && mqttClient != null && mqttClient.isConnected()) {
				Log.d(TAG, "doDisconnect()");
				try {
					token = mqttClient.disconnect();
					token.waitForCompletion(1000);
				} catch (MqttException e) {
					e.printStackTrace();
				}
			}
		}
	};

	public class MQTTBinder extends Binder {
		public MQTTService getService(){
			return MQTTService.this;
		}
	}

	@Override
	public void onCreate() {
		IntentFilter intentf = new IntentFilter();
		setClientID();
		intentf.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
		registerReceiver(new MQTTBroadcastReceiver(), new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
		mConnMan = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		Log.d(TAG, "onConfigurationChanged()");
		android.os.Debug.waitForDebugger();
		super.onConfigurationChanged(newConfig);

	}

	private void setClientID(){
		WifiManager wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
		WifiInfo wInfo = wifiManager.getConnectionInfo();
		deviceId = wInfo.getMacAddress();
		if(deviceId == null){
			deviceId = MqttAsyncClient.generateClientId();
		}
	}

	private void doConnect(){
		Log.d(TAG, "doConnect()");
		IMqttToken token;
		MqttConnectOptions options = new MqttConnectOptions();
		options.setCleanSession(true);
		try {
			mqttClient = new MqttAsyncClient("tcp://broker.mqttdashboard.com:1883", deviceId, new MemoryPersistence());
			token = mqttClient.connect();
			token.waitForCompletion(3500);
			mqttClient.setCallback(new MqttEventCallback());
			token = mqttClient.subscribe("d.prithiviraj@gmail.com", 0);
			token.waitForCompletion(5000);
		} catch (MqttSecurityException e) {
			e.printStackTrace();
		} catch (MqttException e) {
			switch (e.getReasonCode()) {
			case MqttException.REASON_CODE_BROKER_UNAVAILABLE:
			case MqttException.REASON_CODE_CLIENT_TIMEOUT:
			case MqttException.REASON_CODE_CONNECTION_LOST:
			case MqttException.REASON_CODE_SERVER_CONNECT_ERROR:
				Log.v(TAG, "c" +e.getMessage());
				e.printStackTrace();
				break;
			case MqttException.REASON_CODE_FAILED_AUTHENTICATION:
				Intent i = new Intent("RAISEALLARM");
				i.putExtra("ALLARM", e);
				Log.e(TAG, "b"+ e.getMessage());
				break;
			default:
				Log.e(TAG, "a" + e.getMessage());
			}
		}
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		Log.v(TAG, "onStartCommand()");
		return START_STICKY;
	}

	private class MqttEventCallback implements MqttCallback {

		@Override
		public void connectionLost(Throwable arg0) {


		}

		@Override
		public void deliveryComplete(IMqttDeliveryToken arg0) {

		}

		@Override
		@SuppressLint("NewApi")
		public void messageArrived(String topic, final MqttMessage msg) throws Exception {
			Log.i(TAG, "Message arrived from topic" + topic);
			Handler h = new Handler(getMainLooper());
			h.post(new Runnable() {
				@Override
				public void run() {
					/*  Intent launchA = new Intent(MQTTService.this, MainActivity.class);
        		    launchA.putExtra("message", msg.getPayload());
        		    //TODO write somethinkg that has some sense
        		    if(Build.VERSION.SDK_INT >= 11){
        		        launchA.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_REORDER_TO_FRONT|Intent.FLAG_ACTIVITY_NO_ANIMATION);
        		    } else {
        		        launchA.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        		    }
        		    startActivity(launchA); */
					Toast.makeText(getApplicationContext(), "MQTT Message:\n" + new String(msg.getPayload()), Toast.LENGTH_SHORT).show();
					notifcation(getApplicationContext(), new String(msg.getPayload()), new Intent(getApplicationContext(), MainActivity.class), 3);
				}
			});
		}
	}

	static void notifcation(Context context, String messageString, Intent intent, int notificationTitle) {
		
		
		int MessageID= 0;
		//Get the notification manage which we will use to display the notification
		String ns = Context.NOTIFICATION_SERVICE;
		NotificationManager mNotificationManager = (NotificationManager) context.getSystemService(ns);
		Calendar.getInstance().getTime().toString();
		long when = System.currentTimeMillis();

		//get the notification title from the application's strings.xml file
		//CharSequence contentTitle = context.getString(notificationTitle);
		CharSequence contentTitle = "GeoManager message received";

		//the message that will be displayed as the ticker
		String ticker = contentTitle + " " + messageString;

		//build the pending intent that will start the appropriate activity
		PendingIntent pendingIntent = PendingIntent.getActivity(context,
				0, intent, 0);

		//build the notification
		Builder notificationCompat = new Builder(context);
		notificationCompat.setAutoCancel(true)
		.setContentTitle(contentTitle)
		//.setContentIntent(pendingIntent)
		.setContentText(messageString)
		.setTicker(ticker)
		//.setWhen(when)
		.setSmallIcon(R.drawable.ic_launcher);

		Notification notification = notificationCompat.build();
		//display the notification
		MessageID++;
		mNotificationManager.notify(MessageID, notification);
	}


	public String getThread(){
		return Long.valueOf(thread.getId()).toString();
	}

	@Override
	public IBinder onBind(Intent intent) {
		Log.i(TAG, "onBind called");
		return null;
	}

}
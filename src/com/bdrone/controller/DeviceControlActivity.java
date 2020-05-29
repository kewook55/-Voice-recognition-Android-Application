/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.bdrone.controller;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.SystemClock;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ExpandableListView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;




/**
 * For a given BLE device, this Activity provides the user interface to connect, display data,
 * and display GATT services and characteristics supported by the device.  The Activity
 * communicates with {@code BluetoothLeService}, which in turn interacts with the
 * Bluetooth LE API.
 */
public class DeviceControlActivity extends Activity {
    private final static String TAG = DeviceControlActivity.class.getSimpleName();

    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";

    private TextView mConnectionState;
    private TextView mDataField;
    private String mDeviceName;
    private String mDeviceAddress;
    private ExpandableListView mGattServicesList;
    private BluetoothLeService mBluetoothLeService;
    private ArrayList<ArrayList<BluetoothGattCharacteristic>> mGattCharacteristics =
            new ArrayList<ArrayList<BluetoothGattCharacteristic>>();
    private boolean mConnected = false;
    private BluetoothGattCharacteristic mNotifyCharacteristic;
    
    byte sendByte[] = new byte[20]; 
	private byte dataPackage[] = new byte[8];

	private SeekBar sb;
	private byte throttle = 0;
	private byte roll = 100;
	private byte pitch = 100;
	private byte yaw = 100;
	 private TimerTask mTask;
    private Timer mTimer;
    int flag = 0;
	
	boolean send1 = true;
	
	private TextView sendValue;
	

    private final String LIST_NAME = "NAME";
    private final String LIST_UUID = "UUID";

    // Code to manage Service lifecycle.
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
            // Automatically connects to the device upon successful start-up initialization.
            mBluetoothLeService.connect(mDeviceAddress);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };

    // Handles various events fired by the Service.
    // ACTION_GATT_CONNECTED: connected to a GATT server.
    // ACTION_GATT_DISCONNECTED: disconnected from a GATT server.
    // ACTION_GATT_SERVICES_DISCOVERED: discovered GATT services.
    // ACTION_DATA_AVAILABLE: received data from the device.  This can be a result of read
    //                        or notification operations.
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                mConnected = true;
                updateConnectionState(R.string.connected);
                invalidateOptionsMenu();
            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                mConnected = false;
                updateConnectionState(R.string.disconnected);
                invalidateOptionsMenu();
                clearUI();
            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                // Show all the supported services and characteristics on the user interface.
                displayGattServices(mBluetoothLeService.getSupportedGattServices());
            	
                
        		Log.d(TAG,"======= Init Setting Data ");
        		updateCommandState("Init Data");

        		
            	Handler mHandler = new Handler();
            	mHandler.postDelayed(new Runnable() {

        			@Override
        			public void run() {
        		    	// notification enable
        		    	final BluetoothGattCharacteristic characteristic = mGattCharacteristics.get(1).get(0);
        		        mBluetoothLeService.setCharacteristicNotification(characteristic, true);
        			}
            	   
            	}, 1000);

                
            	
            } else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
            	
            	byte[] sendByte = intent.getByteArrayExtra("init");
            	
            	
            	
            	if((sendByte[0] == 0x55) && (sendByte[1] == 0x33)){
            		Log.d(TAG,"======= Init Setting Data ");
            		updateCommandState("Init Data");
               	}            	


	        	if((sendByte[0] == 0x55) && (sendByte[1] == 0x03)){
	        		Log.d(TAG,"======= SPP READ NOTIFY ");
            		updateCommandState("SPP READ");
	        	}
            	
            	
            	
            	
            		
            	
                displayData(intent.getStringExtra(BluetoothLeService.EXTRA_DATA));
            }
        }
    };
    
    
    public static String unHex(String arg) {        

        String str = "";
        for(int i=0;i<arg.length();i+=3)
        {
            String s = arg.substring(i, (i + 2));
            int decimal = Integer.parseInt(s, 16);
            str = str + (char) decimal;
        }       
        return str;
    }

    
    

   
    
    final protected static char[] hexArray = "0123456789ABCDEF".toCharArray();
    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 3];
        for ( int j = 0; j < bytes.length; j++ ) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 3] = hexArray[v >>> 4];
            hexChars[j * 3 + 1] = hexArray[v & 0x0F];
            hexChars[j * 3 + 2] = ' ';
        }
        return new String(hexChars);
    }
    
    public static String bytesToHex(byte bytedata) {
        char[] hexChars = new char[2];

        int v = bytedata & 0xFF;
        hexChars[0] = hexArray[v >>> 4];
        hexChars[1] = hexArray[v & 0x0F];

        return new String(hexChars);
    }
    
    

    
    
    

    // If a given GATT characteristic is selected, check for supported features.  This sample
    // demonstrates 'Read' and 'Notify' features.  See
    // http://d.android.com/reference/android/bluetooth/BluetoothGatt.html for the complete
    // list of supported characteristic features.
    private final ExpandableListView.OnChildClickListener servicesListClickListner =
            new ExpandableListView.OnChildClickListener() {
                @Override
                public boolean onChildClick(ExpandableListView parent, View v, int groupPosition,
                                            int childPosition, long id) {
                    if (mGattCharacteristics != null) {
                        final BluetoothGattCharacteristic characteristic =
                                mGattCharacteristics.get(groupPosition).get(childPosition);
                        final int charaProp = characteristic.getProperties();
                        Log.d(TAG,"TEST");
                        Log.d(TAG, "Selected uuid:" + characteristic.getUuid().toString());
                        
                        //Log.d("BLE", "UUID selected: ".append(characteristic.))
                        if ((charaProp | BluetoothGattCharacteristic.PROPERTY_READ) > 0) {
                            // If there is an active notification on a characteristic, clear
                            // it first so it doesn't update the data field on the user interface.
                            if (mNotifyCharacteristic != null) {
                                mBluetoothLeService.setCharacteristicNotification(
                                        mNotifyCharacteristic, false);
                                mNotifyCharacteristic = null;
                            }
                            mBluetoothLeService.readCharacteristic(characteristic);
                        }
                        if ((charaProp | BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
                            mNotifyCharacteristic = characteristic;
                            
                            
                            Log.d(TAG, "KN Selected uuid:" + mNotifyCharacteristic.getUuid().toString());
                            mBluetoothLeService.setCharacteristicNotification(characteristic, true);
                        }
                        return true;
                    }
                    return false;
                }
    };

    private void clearUI() {
        ///mGattServicesList.setAdapter((SimpleExpandableListAdapter) null);
        mDataField.setText(R.string.no_data);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.gatt_services_characteristics);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(this, new String[] {Manifest.permission.RECORD_AUDIO},5);
            toast("음성인식 권한 요청");
        }

        //ActionBar actionBar = getActionBar();
        //actionBar.setBackgroundDrawable(new ColorDrawable(0xFF0000FF));
        //int actionBarTitleId = Resources.getSystem().getIdentifier("action_bar_title", "id", "android");
        //if (actionBarTitleId > 0) {
        //    TextView title = (TextView) findViewById(actionBarTitleId);
        //    if (title != null) {
        //        title.setTextColor(Color.WHITE);
        //    }
        //}
       
//        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);



        final Intent intent = getIntent();
        mDeviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME);
        mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);

        // Sets up UI references.
        ((TextView) findViewById(R.id.device_address)).setText(mDeviceAddress);
       	mDeviceName = " BLE - SERIAL";
        ((TextView) findViewById(R.id.device_address)).setText(mDeviceAddress);
        
        
        
//        mGattServicesList = (ExpandableListView) findViewById(R.id.gatt_services_list);
//        mGattServicesList.setOnChildClickListener(servicesListClickListner);
        mConnectionState = (TextView) findViewById(R.id.connection_state);
        //mConnectionState.setTextColor(Color.WHITE);
        mDataField = (TextView) findViewById(R.id.data_value);
        
        
        
        sendValue = (TextView) findViewById(R.id.sendValue);
        
        
        
        sb = (SeekBar) findViewById(R.id.th_bar);
        sb.setProgress(throttle);
        
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
			
			@Override
			public void onStopTrackingTouch(SeekBar arg0) {
				// TODO Auto-generated method stub
				
			}
			
			@Override
			public void onStartTrackingTouch(SeekBar arg0) {
				// TODO Auto-generated method stub
				
			}
			
			@Override
			public void onProgressChanged(SeekBar arg0, int arg1, boolean arg2) {
				// TODO Auto-generated method stub
				
				throttle = (byte) ((arg1 & 0x000000FF) >> 0);
				
				sendData(roll, pitch, yaw, throttle);
				
				
				
				
				
			}
		});
        

		Log.d(TAG,"Timer  =======================================");
        mTask = new TimerTask() {
            @Override
            public void run() {
            	
				sendValue.setText("Throttle:"+bytesToHex(throttle));

            	sendData(roll, pitch, yaw, throttle);
            }
        };
         
        mTimer = new Timer();
        mTimer.schedule(mTask, 800,800);
        
        
        
        Button left_Button = (Button) findViewById(R.id.left_button);
        left_Button.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
            	
            	if(roll > 1)
            		roll = (byte) (roll - 1);
            	
            	sendData(roll, pitch, yaw, throttle);

            }
        });		
        Button right_Button = (Button) findViewById(R.id.right_button);
        right_Button.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
            	
            	if(roll < 200)
            		roll = (byte) (roll + 1);
            	
            	sendData(roll, pitch, yaw, throttle);

            }
        });		

        Button fwd_Button = (Button) findViewById(R.id.fwd_button);
        fwd_Button.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
            	
            	if(pitch < 200)
            		pitch = (byte) (pitch + 1);
            	
            	sendData(roll, pitch, yaw, throttle);

            }
        });		
        Button back_Button = (Button) findViewById(R.id.back_button);
        back_Button.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
            	
            	if(pitch > 1)
            		pitch = (byte) (pitch - 1);
            	
            	sendData(roll, pitch, yaw, throttle);

            }
        });		
        Button rec_Button = (Button) findViewById(R.id.rec_button);
        rec_Button.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                inputVoice();
            }
        });

        //비상착륙 버튼 구현
        Button emergency_Button = (Button) findViewById(R.id.emergency_button);
        emergency_Button.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {

                if(throttle > 1)
                    throttle = (byte) (0);

                sendData(roll, pitch, yaw, throttle);

            }
        });

        
        getActionBar().setTitle(mDeviceName);
        getActionBar().setDisplayHomeAsUpEnabled(true);
        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
        
        
        

        
    }


    //음성인식
    public void inputVoice() {
        try {
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, this.getPackageName());
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR");
            final SpeechRecognizer stt = SpeechRecognizer.createSpeechRecognizer(this);
            stt.setRecognitionListener(new RecognitionListener() {

                @Override
                public void onReadyForSpeech(Bundle params) {
                    // TODO Auto-generated method stub
                    toast("음성입력..");
                }

                @Override
                public void onBeginningOfSpeech() {
                    // TODO Auto-generated method stub

                }

                @Override
                public void onRmsChanged(float rmsdB) {
                    // TODO Auto-generated method stub

                }

                @Override
                public void onBufferReceived(byte[] buffer) {
                    // TODO Auto-generated method stub

                }

                @Override
                public void onEndOfSpeech() {
                    // TODO Auto-generated method stub

                }

                @Override
                public void onError(int error) {
                    // TODO Auto-generated method stub
                    toast("오류 발생 : " + error);
                    stt.destroy();
                }

                @SuppressWarnings("unchecked")
                @Override
                public void onResults(Bundle results) {
                    // TODO Auto-generated method stub
                    ArrayList<String> result = (ArrayList<String>) results.get(SpeechRecognizer.RESULTS_RECOGNITION);
                    replyAnswer(result.get(0));
                    stt.destroy();
                }

                @Override
                public void onPartialResults(Bundle partialResults) {
                    // TODO Auto-generated method stub

                }

                @Override
                public void onEvent(int eventType, Bundle params) {
                    // TODO Auto-generated method stub

                }

            });
            stt.startListening(intent);
        }catch(Exception e) {
            toast(e.toString());
        }
    }
    private void replyAnswer(String input) {

        try {
            if(input.equals("출발")){
                toast("드론을 띄웁니다.");
                throttle = (byte) (40);
              //  while(flag == 1) {
                    sendData(roll, pitch, yaw, throttle);
               // }
            }
            else if(input.equals("정지")){
                toast("드론이 착륙합니다.");
                throttle = (byte) (0);
                flag = 0;
                //sendData(roll, pitch, yaw, throttle);
            }
            else if(input.equals("올라가") || input.equals("올라") || input.equals("올려")){
                toast("드론을 더 올립니다.");
                throttle = (byte) (throttle + 20);

              //  sendData(roll, pitch, yaw, throttle);
            }
            else if(input.equals("내려와") || input.equals("내려") || input.equals("내려가")){
                toast("드론을 내립니다.");
                throttle = (byte) (throttle - 20);

              //  sendData(roll, pitch, yaw, throttle);
            }
            else if(input.equals("오른쪽")){
                toast("오른쪽으로 이동");
                if(roll < 200)
                    roll = (byte) (roll + 11);

            //    sendData(roll, pitch, yaw, throttle);

            }
            else if(input.equals("왼쪽")){
                toast("왼쪽으로 이동");
                if(roll > 1)
                    roll = (byte) (roll - 11);

             //   sendData(roll, pitch, yaw, throttle);

            }
            else if(input.equals("그만") || input.equals("제자리")){
                toast("드론 이동을 멈춥니다.");
            }
            else if(input.equals("앞으로")){
                toast("앞으로 이동");
                if(pitch < 200)
                    pitch = (byte) (pitch + 11);

            //    sendData(roll, pitch, yaw, throttle);

            }
            else if(input.equals("뒤로")){
                toast("뒤로 이동");
                if(pitch > 1)
                    pitch = (byte) (pitch - 11);

             //   sendData(roll, pitch, yaw, throttle);

            }
        }catch(Exception e) {
            toast(e.toString());
        }
    }
    //여기까지 음성인식 함수부분
    // 알림창(Toast) 띄우는 함수
    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }



    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        if (mBluetoothLeService != null) {
            final boolean result = mBluetoothLeService.connect(mDeviceAddress);
            Log.d(TAG, "Connect request result=" + result);
        }
        
        
        
        
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mGattUpdateReceiver);
    }

    @Override
    protected void onDestroy() {
    	
    	mTimer.cancel();
        super.onDestroy();
        unbindService(mServiceConnection);
        mBluetoothLeService = null;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.gatt_services, menu);
        
        if (mConnected) {
            menu.findItem(R.id.menu_connect).setVisible(false);
            menu.findItem(R.id.menu_disconnect).setVisible(true);
            
            
            
        } else {
            menu.findItem(R.id.menu_connect).setVisible(true);
            menu.findItem(R.id.menu_disconnect).setVisible(false);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.menu_connect:
                mBluetoothLeService.connect(mDeviceAddress);
                return true;
            case R.id.menu_disconnect:
                mBluetoothLeService.disconnect();
                return true;
            case android.R.id.home:
                onBackPressed();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void updateConnectionState(final int resourceId) {
//        runOnUiThread(new Runnable() {
//            @Override
//            public void run() {
//                //mConnectionState.setText(resourceId);
//            }
//        });
    }

    private void updateCommandState(final String str) {
      runOnUiThread(new Runnable() {
          @Override
          public void run() {
              mConnectionState.setText(str);
          }
      });
  }

    
    
    private void displayData(String data) {
        if (data != null) {
            mDataField.setText(data);
        }
    }

    // Demonstrates how to iterate through the supported GATT Services/Characteristics.
    // In this sample, we populate the data structure that is bound to the ExpandableListView
    // on the UI.
    private void displayGattServices(List<BluetoothGattService> gattServices) {
        if (gattServices == null) return;
        String uuid = null;
        String unknownServiceString = getResources().getString(R.string.unknown_service);
        String unknownCharaString = getResources().getString(R.string.unknown_characteristic);
        ArrayList<HashMap<String, String>> gattServiceData = new ArrayList<HashMap<String, String>>();
        ArrayList<ArrayList<HashMap<String, String>>> gattCharacteristicData
                = new ArrayList<ArrayList<HashMap<String, String>>>();
        mGattCharacteristics = new ArrayList<ArrayList<BluetoothGattCharacteristic>>();

        // Loops through available GATT Services.
        for (BluetoothGattService gattService : gattServices) {
            HashMap<String, String> currentServiceData = new HashMap<String, String>();
            uuid = gattService.getUuid().toString();
            
            Log.d(TAG,"service uuid : " + uuid);
            
            currentServiceData.put(
                    LIST_NAME, SampleGattAttributes.lookup(uuid, unknownServiceString));
            currentServiceData.put(LIST_UUID, uuid);
            gattServiceData.add(currentServiceData);

            ArrayList<HashMap<String, String>> gattCharacteristicGroupData =
                    new ArrayList<HashMap<String, String>>();
            List<BluetoothGattCharacteristic> gattCharacteristics =
                    gattService.getCharacteristics();
            ArrayList<BluetoothGattCharacteristic> charas =
                    new ArrayList<BluetoothGattCharacteristic>();

            // Loops through available Characteristics.
            for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {
                charas.add(gattCharacteristic);
                HashMap<String, String> currentCharaData = new HashMap<String, String>();
                uuid = gattCharacteristic.getUuid().toString();
                Log.d(TAG,"gattCharacteristic uuid : " + uuid);
                
                
                currentCharaData.put(
                        LIST_NAME, SampleGattAttributes.lookup(uuid, unknownCharaString));
                currentCharaData.put(LIST_UUID, uuid);
                gattCharacteristicGroupData.add(currentCharaData);
            }
            mGattCharacteristics.add(charas);
            gattCharacteristicData.add(gattCharacteristicGroupData);
        }

       /* SimpleExpandableListAdapter gattServiceAdapter = new SimpleExpandableListAdapter(
                this,
                gattServiceData,
                android.R.layout.simple_expandable_list_item_2,
                new String[] {LIST_NAME, LIST_UUID},
                new int[] { android.R.id.text1, android.R.id.text2 },
                gattCharacteristicData,
                android.R.layout.simple_expandable_list_item_2,
                new String[] {LIST_NAME, LIST_UUID},
                new int[] { android.R.id.text1, android.R.id.text2 }
        );
        mGattServicesList.setAdapter(gattServiceAdapter); */
        
        
        Log.d(TAG,"service read ok ");
    }
    
    
	class ClickEvent implements View.OnClickListener {
		@Override
		public void onClick(View v) {
		}

	}    
    

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }
    
    
    
    private void sendData(byte roll_value, byte pitch_value, byte yaw_value, byte throttle_value){
    	if(mGattCharacteristics != null){ 
    	final BluetoothGattCharacteristic characteristic = mGattCharacteristics.get(1).get(0);
    	
    	dataPackage[0] = (byte)0xF0;
    	dataPackage[1] = (byte)0xA1;
    	dataPackage[2] = roll_value;
    	dataPackage[3] = pitch_value;
    	dataPackage[4] = yaw_value;
    	dataPackage[5] = throttle_value;
    	dataPackage[6] = (byte)0x01;
    	
    	byte cs  = 0;
    	
    	cs += dataPackage[1];
    	cs += dataPackage[2];
    	cs += dataPackage[3];
    	cs += dataPackage[4];
    	cs += dataPackage[5];
    	cs += dataPackage[6];
    	
    	
    	dataPackage[7] = (byte)cs;
    	
    	SystemClock.sleep(10);
    	
    	mBluetoothLeService.writeCharacteristicsBytes(characteristic, dataPackage);
    	updateCommandState("Write Data");

    	}    	
    }
    
    
   
    
    
    
    
    
    
}

package com.jzheadley.droneproject.ui;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Vibrator;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.jzheadley.android_orientation_sensors.sensors.Orientation;
import com.jzheadley.android_orientation_sensors.utils.OrientationSensorInterface;
import com.jzheadley.droneproject.Constants;
import com.jzheadley.droneproject.R;
import com.jzheadley.droneproject.bluetooth.BluetoothChatService;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

public class ControllerDebugActivity extends AppCompatActivity implements OrientationSensorInterface {
    private static final String TAG = "ControllerDebugActivity";
    private static final int REQUEST_CONNECT_DEVICE_SECURE = 1;
    private static final int REQUEST_CONNECT_DEVICE_INSECURE = 2;

    private static long updateNumber = 0;
    @BindView(R.id.drone_up_btn)
    Button upBtn;
    @BindView(R.id.drone_down_btn)
    Button downBtn;
    @BindView(R.id.drone_takeoff_land_btn)
    Button takeoffLandBtn;
    @BindView(R.id.drone_emergency_btn)
    Button ohShitBtn;

    @BindView(R.id.flip_btn)
    Button flipBtn;

    @BindView(R.id.debug_controller_txtView)
    TextView controllerDebugTxt;
    private BluetoothChatService bluetoothChatService;
    private BluetoothChatService mChatService = null;
    private BluetoothAdapter mBluetoothAdapter = null;
    private Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            // FragmentActivity activity = getActivity();
            switch (msg.what) {
                case Constants.MESSAGE_STATE_CHANGE:
                    switch (msg.arg1) {
                        case BluetoothChatService.STATE_CONNECTED:
                            Log.d(TAG, "handleMessage: We connected to something...");
                            // Log.d(TAG, "handleMessage: " + mConnectedDevice);
                            // (getString(R.string.title_connected_to, mConnectedDeviceName));
                            // mConversationArrayAdapter.clear();
                            break;
                        case BluetoothChatService.STATE_CONNECTING:
                            Log.d(TAG, "handleMessage: Still trying to connect");
                            // setStatus(R.string.title_connecting);
                            break;
                        case BluetoothChatService.STATE_LISTEN:
                            Log.d(TAG, "handleMessage: Listening for connection?");
                        case BluetoothChatService.STATE_NONE:
                            Log.e(TAG, "handleMessage: Couldn't connect?");
                            // setStatus(R.string.title_not_connected);
                            break;
                    }
                    break;
                case Constants.MESSAGE_WRITE:
                    byte[] writeBuf = (byte[]) msg.obj;
                    // construct a string from the buffer
                    String writeMessage = new String(writeBuf);
                    // the message written is in writeMessage
                    Log.d(TAG, "handleMessageWriting: " + writeBuf);
                    break;
                case Constants.MESSAGE_READ:
                    byte[] readBuf = (byte[]) msg.obj;
                    // construct a string from the valid bytes in the buffer
                    String readMessage = new String(readBuf, 0, msg.arg1);
                    // Log.d(TAG, "handleMessageReading: " + readMessage);
                    // mConversationArrayAdapter.add(mConnectedDeviceName + ":  " + readMessage);
                    break;

            }
        }

    };
    private StringBuffer mOutStringBuffer;

    //private SensorManager mSensorManager;
    //private Sensor mSensor;
    private Orientation orientationSensor;
    private boolean hasTakenOff = false;

    public void vibrate() {
        Vibrator vib = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        vib.vibrate(300);
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_controller_debug);
        ButterKnife.bind(this);
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        bluetoothChatService = new BluetoothChatService(this, handler);
        // mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        // mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        // mSensorManager.registerListener(this, mSensor, SensorManager.SENSOR_DELAY_NORMAL);
        orientationSensor = new Orientation(this.getApplicationContext(), this);
        //------Turn Orientation sensor ON-------
        // set tolerance for any directions
        orientationSensor.init(1.0, 1.0, 1.0);
        // set output speed and turn initialized sensor on
        // 0 Normal
        // 1 UI
        // 2 GAME
        // 3 FASTEST
        orientationSensor.on(0);
        //---------------------------------------
        // turn orientation sensor off
        //orientationSensor.off();
        // return true or false
        orientationSensor.isSupport();
        upBtn.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                switch (motionEvent.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        Log.d(TAG, "onTouch: LOOP ACTION DOWN");
                        vibrate();
                        view.setPressed(true);
                        sendMessage(Constants.MESSAGE_UP_START + "");
                        return true;
                    // break;
                    case MotionEvent.ACTION_UP:
                        Log.d(TAG, "onTouch: LOOP ACTION UP");
                        view.setPressed(false);
                        sendMessage(Constants.MESSAGE_UP_STOP + "");
                        return true;
                }
                return true;
            }

        });
        downBtn.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                switch (motionEvent.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        vibrate();
                        view.setPressed(true);
                        Log.d(TAG, "onTouch: LOOP ACTION DOWN");
                        sendMessage(Constants.MESSAGE_DOWN_START + "");
                        return true;
                    // break;
                    case MotionEvent.ACTION_UP:
                        view.setPressed(false);
                        Log.d(TAG, "onTouch: LOOP ACTION UP");
                        sendMessage(Constants.MESSAGE_DOWN_STOP + "");
                        return true;
                }
                return true;
            }
        });

    }

    @Override
    protected void onStop() {
        super.onStop();
        mChatService.stop();
        orientationSensor.off();
    }

    /**
     * Set up the UI and background operations for chat.
     */
    private void setupChat() {
        Log.d(TAG, "setupChat()");

        // Initialize the BluetoothChatService to perform bluetooth connections
        mChatService = new BluetoothChatService(this, handler);

        // Initialize the buffer for outgoing messages
        mOutStringBuffer = new StringBuffer("");
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (mChatService == null) {
            setupChat();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.bluetooth_chat, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.secure_connect_scan: {
                // Launch the DeviceListActivity to see devices and do scan
                Intent serverIntent = new Intent(this, BluetoothDeviceListActivity.class);
                startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE_SECURE);
                return true;
            }
            case R.id.insecure_connect_scan: {
                // Launch the DeviceListActivity to see devices and do scan
                Intent serverIntent = new Intent(this, BluetoothDeviceListActivity.class);
                startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE_INSECURE);
                return true;
            }
        }
        return false;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.d(TAG, "onActivityResult: request: " + requestCode + " result: " + resultCode);
        switch (requestCode) {
            case REQUEST_CONNECT_DEVICE_SECURE:
                // When DeviceListActivity returns with a device to connect
                if (resultCode == Activity.RESULT_OK) {
                    connectDevice(data, true);
                }
                break;
            case REQUEST_CONNECT_DEVICE_INSECURE:
                // When DeviceListActivity returns with a device to connect
                if (resultCode == Activity.RESULT_OK) {
                    connectDevice(data, false);
                }
                break;

        }
    }

    /**
     * Establish connection with other device
     *
     * @param data   An {@link Intent} with {@link BluetoothDeviceListActivity#EXTRA_DEVICE_ADDRESS} extra.
     * @param secure Socket Security type - Secure (true) , Insecure (false)
     */
    private void connectDevice(Intent data, boolean secure) {
        // Get the device MAC address
        String address = data.getExtras().getString(BluetoothDeviceListActivity.EXTRA_DEVICE_ADDRESS);
        // Get the BluetoothDevice object
        Log.d(TAG, "connectDevice: " + address);
        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        Log.d(TAG, "connectDevice: " + device);
        // Attempt to connect to the device
        mChatService.connect(device, secure);
        if (mChatService.getState() == BluetoothChatService.STATE_CONNECTED) {
            Toast.makeText(this, "You are connected!", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Sends a message.
     *
     * @param message A string of text to send.
     */
    private void sendMessage(String message) {
        // Check that we're actually connected before trying anything
        if (mChatService.getState() != BluetoothChatService.STATE_CONNECTED) {
            //Toast.makeText(this, R.string.not_connected, Toast.LENGTH_SHORT).show();
            return;
        }


        // Check that there's actually something to send
        if (message.length() > 0) {
            // Get the message bytes and tell the BluetoothChatService to write
            byte[] send = message.getBytes();
            mChatService.write(send);
            controllerDebugTxt.setText(mOutStringBuffer);

            // Reset out string buffer to zero and clear the edit text field
            mOutStringBuffer.setLength(0);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        orientationSensor.on(0);
    }

    @Override
    public synchronized void orientation(Double AZIMUTH, Double PITCH, Double ROLL) {
        /*Log.d("Azimuth", String.valueOf(AZIMUTH));
        Log.d("PITCH", String.valueOf(PITCH));
        Log.d("ROLL", String.valueOf(ROLL));*/
        String values = AZIMUTH + " " + PITCH + " " + ROLL;
        //updateNumber++;
        //if (updateNumber%10 == 0) {
        sendMessage(values);
        //}
    }

    @OnClick(R.id.drone_emergency_btn)
    public void emergencyBtn() {
        sendMessage(Constants.MESSAGE_OHSHIT + "");
    }

    @OnClick(R.id.calibrate_drone_btn)
    public void calibrateBtnHandler() {
        sendMessage(Constants.MESSAGE_CALIBRATE + "");
    }

    @OnClick(R.id.drone_takeoff_land_btn)
    public void takeoffLandBtnHandler() {
        if (!hasTakenOff) {
            takeoffLandBtn.setText("Land");
            sendMessage(Constants.MESSAGE_TAKEOFF + "");
        } else {
            takeoffLandBtn.setText("Take Off");
            sendMessage(Constants.MESSAGE_LAND + "");
        }
        hasTakenOff = !hasTakenOff;
    }

    @OnClick(R.id.flip_btn)
    public void onFlipBtn() {
        sendMessage(Constants.MESSAGE_FLIP + "");
    }
}

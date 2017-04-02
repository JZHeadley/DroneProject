package com.jzheadley.droneproject.ui;

import android.app.Activity;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
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
import com.jzheadley.droneproject.drone.BebopDrone;
import com.jzheadley.droneproject.dynamics.DynamicsUtilities;
import com.jzheadley.droneproject.view.BebopVideoView;
import com.parrot.arsdk.arcommands.ARCOMMANDS_ARDRONE3_MEDIARECORDEVENT_PICTUREEVENTCHANGED_ERROR_ENUM;
import com.parrot.arsdk.arcommands.ARCOMMANDS_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_ENUM;
import com.parrot.arsdk.arcontroller.ARCONTROLLER_DEVICE_STATE_ENUM;
import com.parrot.arsdk.arcontroller.ARControllerCodec;
import com.parrot.arsdk.arcontroller.ARFrame;
import com.parrot.arsdk.ardiscovery.ARDiscoveryDeviceService;

import butterknife.BindView;
import butterknife.ButterKnife;

public class BebopActivity extends AppCompatActivity implements OrientationSensorInterface {
    private static final String TAG = "BebopActivity";
    private static final int REQUEST_CONNECT_DEVICE_SECURE = 1;
    private static final int REQUEST_CONNECT_DEVICE_INSECURE = 2;
    @BindView(R.id.videoView)
    BebopVideoView mVideoView;
    @BindView(R.id.batteryLabel)
    TextView mBatteryLabel;

    @BindView(R.id.VR_Btn)
    Button vrBtn;
    private BluetoothChatService bluetoothChatService;
    private BebopDrone mBebopDrone;
    private ProgressDialog mConnectionProgressDialog;
    private ProgressDialog mDownloadProgressDialog;
    private int mNbMaxDownload;
    private BluetoothChatService mChatService = null;
    private BluetoothAdapter mBluetoothAdapter = null;

    private int mCurrentDownloadIndex;
    private final BebopDrone.Listener mBebopListener = new BebopDrone.Listener() {
        @Override
        public void onDroneConnectionChanged(ARCONTROLLER_DEVICE_STATE_ENUM state) {
            switch (state) {
                case ARCONTROLLER_DEVICE_STATE_RUNNING:
                    mConnectionProgressDialog.dismiss();
                    break;

                case ARCONTROLLER_DEVICE_STATE_STOPPED:
                    // if the deviceController is stopped, go back to the previous activity
                    mConnectionProgressDialog.dismiss();
                    finish();
                    break;

                default:
                    break;
            }
        }

        @Override
        public void onBatteryChargeChanged(int batteryPercentage) {
            mBatteryLabel.setText(String.format("%d%%", batteryPercentage));
        }

        @Override
        public void onPilotingStateChanged(
                ARCOMMANDS_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_ENUM state) {
//            switch (state) {
//                case ARCOMMANDS_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_LANDED:
//                    mTakeOffLandBt.setText("Take off");
//                    mTakeOffLandBt.setEnabled(true);
//                    mDownloadBt.setEnabled(true);
//                    break;
//
//                case ARCOMMANDS_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_FLYING:
//                case ARCOMMANDS_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_HOVERING:
//                    mTakeOffLandBt.setText("Land");
//                    mTakeOffLandBt.setEnabled(true);
//                    mDownloadBt.setEnabled(false);
//                    break;
//
//                default:
//                    mTakeOffLandBt.setEnabled(false);
//                    mDownloadBt.setEnabled(false);
//            }
        }

        @Override
        public void onPictureTaken(
                ARCOMMANDS_ARDRONE3_MEDIARECORDEVENT_PICTUREEVENTCHANGED_ERROR_ENUM error) {
            Log.i(TAG, "Picture has been taken");
        }

        @Override
        public void configureDecoder(ARControllerCodec codec) {
            mVideoView.configureDecoder(codec);
        }

        @Override
        public void onFrameReceived(ARFrame frame) {
            mVideoView.displayFrame(frame);
        }

        @Override
        public void onMatchingMediasFound(int nbMedias) {
            mDownloadProgressDialog.dismiss();

            mNbMaxDownload = nbMedias;
            mCurrentDownloadIndex = 1;

            if (nbMedias > 0) {
                mDownloadProgressDialog =
                        new ProgressDialog(BebopActivity.this, R.style.AppCompatAlertDialogStyle);
                mDownloadProgressDialog.setIndeterminate(false);
                mDownloadProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                mDownloadProgressDialog.setMessage("Downloading medias");
                mDownloadProgressDialog.setMax(mNbMaxDownload * 100);
                mDownloadProgressDialog.setSecondaryProgress(mCurrentDownloadIndex * 100);
                mDownloadProgressDialog.setProgress(0);
                mDownloadProgressDialog.setCancelable(false);
                mDownloadProgressDialog.setButton(DialogInterface.BUTTON_NEGATIVE, "Cancel",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                mBebopDrone.cancelGetLastFlightMedias();
                            }
                        });
                mDownloadProgressDialog.show();
            }
        }

        @Override
        public void onDownloadProgressed(String mediaName, int progress) {
            mDownloadProgressDialog.setProgress(((mCurrentDownloadIndex - 1) * 100) + progress);
        }

        @Override
        public void onDownloadComplete(String mediaName) {
            mCurrentDownloadIndex++;
            mDownloadProgressDialog.setSecondaryProgress(mCurrentDownloadIndex * 100);

            if (mCurrentDownloadIndex > mNbMaxDownload) {
                mDownloadProgressDialog.dismiss();
                mDownloadProgressDialog = null;
            }
        }
    };
    private Handler handler =
            new Handler() {
                @Override
                public void handleMessage(Message msg) {
                    // FragmentActivity activity = getActivity();
                    Log.d(TAG, "handleMessage: " + msg.what);
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
                            break;
                        case Constants.MESSAGE_READ:
                            Log.d(TAG, "handleMessage: " + msg);
                            byte[] readBuf = (byte[]) msg.obj;
                            // construct a string from the valid bytes in the buffer
                            String readMessage = new String(readBuf, 0, msg.arg1);
                            Log.d(TAG, "handleMessageReading: " + readMessage);

                            int command = Integer.parseInt(readMessage.split(" ")[0]);

                            switch (command) {
                                case Constants.MESSAGE_OHSHIT:
                                    Log.d(TAG, "handleMessage: OhShit...");
                                    mBebopDrone.emergency();
                                    break;
                                case Constants.MESSAGE_UP_START:
                                    Log.d(TAG, "handleMessage: Going up");
                                    mBebopDrone.setGaz((byte) 50);
                                    break;
                                case Constants.MESSAGE_UP_STOP:
                                    Log.d(TAG, "handleMessage: Going up");
                                    mBebopDrone.setGaz((byte) 0);
                                    break;
                                case Constants.MESSAGE_DOWN_START:
                                    Log.d(TAG, "handleMessage: Going down");
                                    mBebopDrone.setGaz((byte) -50);
                                    break;
                                case Constants.MESSAGE_DOWN_STOP:
                                    Log.d(TAG, "handleMessage: Going down");
                                    mBebopDrone.setGaz((byte) 0);
                                    break;
                                case Constants.MESSAGE_TAKEOFF:
                                    Log.d(TAG, "handleMessage: TakingOff");
                                    mBebopDrone.takeOff();
                                    break;
                                case Constants.MESSAGE_LAND:
                                    Log.d(TAG, "handleMessage: Landing");
                                    mBebopDrone.land();
                                    break;
                                case Constants.MESSAGE_CALIBRATE:
                                    Log.d(TAG, "handleMessage: Calibrating");
                                    break;
                                default:
                                    Log.d(TAG, "handleMessage: " + readMessage.split(" "));
                                    break;

                            }
                            break;

                    }

                }

            };
    private StringBuffer mOutStringBuffer;

    @Override
    protected void onResume() {

        super.onResume();


        Orientation orientationSensor = new Orientation(this.getApplicationContext(), this);

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

    }

    @Override
    public void orientation(Double AZIMUTH, Double PITCH, Double ROLL) {
        DynamicsUtilities.viewZ = Math.toRadians(AZIMUTH);
        DynamicsUtilities.viewX = Math.toRadians(PITCH);
        DynamicsUtilities.viewY = Math.toRadians(ROLL);


        DynamicsUtilities.calcSlaveYaw();
        /*((TextView)findViewById(R.id.z)).setText(String.format("DZ: %.0f VZ: %.0f GL%.0f",
                Math.toDegrees(DynamicsUtilities.droneZ - DynamicsUtilities.droneZ0),
                Math.toDegrees(DynamicsUtilities.viewZ - DynamicsUtilities.viewZ0),
                Math.toDegrees(DynamicsUtilities.goLeftRad)));*/
        mBebopDrone.setYaw(DynamicsUtilities.yaw);

        DynamicsUtilities.calcFixedPitchRoll();
        ((TextView) findViewById(R.id.rollTxt)).setText(String.format("Pi:%d Ro:%d DZ:%.2f VZ:%.2f",
                DynamicsUtilities.pitch,
                DynamicsUtilities.roll,
                Math.toDegrees(DynamicsUtilities.droneZ - DynamicsUtilities.droneZ0),
                Math.toDegrees(DynamicsUtilities.viewZ - DynamicsUtilities.viewZ0)
        ));
        mBebopDrone.setPitch(DynamicsUtilities.pitch);
        mBebopDrone.setRoll(DynamicsUtilities.roll);
        mBebopDrone.setFlag(DynamicsUtilities.flag);

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bebop);
        ButterKnife.bind(this);
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        bluetoothChatService = new BluetoothChatService(this, handler);
        initIHM();
        Intent intent = getIntent();
        ARDiscoveryDeviceService service =
                intent.getParcelableExtra(DeviceListActivity.EXTRA_DEVICE_SERVICE);
        mBebopDrone = new BebopDrone(this, service);
        mBebopDrone.addListener(mBebopListener);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (mChatService == null) {
            setupChat();
        }

        // show a loading view while the bebop drone is connecting
        {
            if ((mBebopDrone != null)
                    && !(ARCONTROLLER_DEVICE_STATE_ENUM.ARCONTROLLER_DEVICE_STATE_RUNNING.equals(
                    mBebopDrone.getConnectionState()))) {
                mConnectionProgressDialog = new ProgressDialog(this, R.style.AppCompatAlertDialogStyle);
                mConnectionProgressDialog.setIndeterminate(true);
                mConnectionProgressDialog.setMessage("Connecting ...");
                mConnectionProgressDialog.setCancelable(false);
                mConnectionProgressDialog.show();

                // if the connection to the Bebop fails, finish the activity
                if (!mBebopDrone.connect()) {
                    Log.e(TAG, "onStart: Connection Failed");
                    finish();
                }
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (mBebopDrone != null) {
            mConnectionProgressDialog = new ProgressDialog(this, R.style.AppCompatAlertDialogStyle);
            mConnectionProgressDialog.setIndeterminate(true);
            mConnectionProgressDialog.setMessage("Disconnecting ...");
            mConnectionProgressDialog.setCancelable(false);
            mConnectionProgressDialog.show();

            if (!mBebopDrone.disconnect()) {
                finish();
            }
        }
    }

    @Override
    public void onDestroy() {
        mBebopDrone.dispose();
        super.onDestroy();
    }

    private void initIHM() {

        findViewById(R.id.emergencyBt).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mBebopDrone.emergency();
            }
        });

      /*  mDownloadBt.setEnabled(false);
        mDownloadBt.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mBebopDrone.getLastFlightMedias();

                mDownloadProgressDialog =
                        new ProgressDialog(BebopActivity.this, R.style.AppCompatAlertDialogStyle);
                mDownloadProgressDialog.setIndeterminate(true);
                mDownloadProgressDialog.setMessage("Fetching medias");
                mDownloadProgressDialog.setCancelable(false);
                mDownloadProgressDialog.setButton(DialogInterface.BUTTON_NEGATIVE, "Cancel",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                mBebopDrone.cancelGetLastFlightMedias();
                            }
                        });
                mDownloadProgressDialog.show();
            }
        });*/

        vrBtn.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {

                return true;
            }
        });
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
    }

    /**
     * Sends a message.
     *
     * @param message A string of text to send.
     */
    private void sendMessage(String message) {
        // Check that we're actually connected before trying anything
        if (mChatService.getState() != BluetoothChatService.STATE_CONNECTED) {
            Toast.makeText(this, R.string.not_connected, Toast.LENGTH_SHORT).show();
            return;
        }

        // Check that there's actually something to send
        if (message.length() > 0) {
            // Get the message bytes and tell the BluetoothChatService to write
            byte[] send = message.getBytes();
            mChatService.write(send);

            // Reset out string buffer to zero and clear the edit text field
            mOutStringBuffer.setLength(0);
            // controllerDebugTxt.setText(mOutStringBuffer);
        }
    }
}

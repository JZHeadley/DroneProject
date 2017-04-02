package com.jzheadley.droneproject.ui;

import com.google.vr.sdk.base.AndroidCompat;
import com.google.vr.sdk.base.Eye;
import com.google.vr.sdk.base.GvrActivity;
import com.google.vr.sdk.base.GvrView;
import com.google.vr.sdk.base.HeadTransform;
import com.google.vr.sdk.base.Viewport;

import android.app.Activity;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
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
import com.parrot.arsdk.arcontroller.ARCONTROLLER_ERROR_ENUM;
import com.parrot.arsdk.arcontroller.ARControllerCodec;
import com.parrot.arsdk.arcontroller.ARDeviceController;
import com.parrot.arsdk.arcontroller.ARDeviceControllerStreamListener;
import com.parrot.arsdk.arcontroller.ARFrame;
import com.parrot.arsdk.ardiscovery.ARDiscoveryDeviceService;

import javax.microedition.khronos.egl.EGLConfig;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

public class BebopActivity extends GvrActivity implements OrientationSensorInterface, GvrView.StereoRenderer, ARDeviceControllerStreamListener {

    private static final String TAG = "BebopActivity";
    private static final int REQUEST_CONNECT_DEVICE_SECURE = 1;
    private static final int REQUEST_CONNECT_DEVICE_INSECURE = 2;
    private static double az;
    private static double pit;
    private static double rol;
    @BindView(R.id.videoView)
    BebopVideoView mVideoView;

    @BindView(R.id.batteryLabel)
    TextView mBatteryLabel;

    @BindView(R.id.VR_Btn)
    Button vrBtn;

    @BindView(R.id.vrView)
    GvrView gvrView;
    @BindView(R.id.bluetooth_btn)
    Button bluetoothBtn;
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
                            String[] string = new String[3];
                            string = readMessage.split(" ");
                            int command = 0;

                            if (string.length == 3) {
                                try {
                                    az = Double.parseDouble(string[0]);
                                    pit = Double.parseDouble(string[1]);
                                    rol = Double.parseDouble(string[2]);
                                    ((TextView) findViewById(R.id.pitchTxt)).setText(String.format("RawPit:%.2f", pit));
                                    DynamicsUtilities.setRemoteAttitudeInDegrees(az, pit, rol);
                                } catch (Exception e) {
                                    Log.w(TAG, "handleMessage: ", e);
                                }

                            } else {
                                try {
                                    command = Integer.parseInt(string[0]);
                                } catch (NumberFormatException e) {
                                    Log.w(TAG, "handleMessage: ", e);

                                }
                            }

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
                                    DynamicsUtilities.calibrate();
                                    break;
                                case Constants.MESSAGE_FLIP:
                                    Log.d(TAG, "handleMessage: Calibrating");
                                    mBebopDrone.flip();
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
        orientationSensor.init(1.0, 1.0, 1.0);
        orientationSensor.on(0);
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


        DynamicsUtilities.calcInterimTilt();
        ((TextView) findViewById(R.id.azimuthTxt)).setText(String.format("Theta:%.1f",
                Math.toDegrees(DynamicsUtilities.thetaMoveRightFromCenterline)
        ));
        ((TextView) findViewById(R.id.rollTxt)).setText(String.format("Pi:%d Ro:%d RZ:%.2f DZ:%.2f VZ:%.2f",
                DynamicsUtilities.pitch,
                DynamicsUtilities.roll,
                Math.toDegrees(DynamicsUtilities.remZ - DynamicsUtilities.remZ0),
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
        ARDiscoveryDeviceService service = intent.getParcelableExtra(DeviceListActivity.EXTRA_DEVICE_SERVICE);
        mBebopDrone = new BebopDrone(this, service);
        mBebopDrone.addListener(mBebopListener);

        gvrView.setEGLConfigChooser(8, 8, 8, 8, 16, 8);
        gvrView.setTransitionViewEnabled(true);
        gvrView.setDistortionCorrectionEnabled(true);

        // This line is really important! It's what enables the low-latency
        // VR experience. Without it, you'll have a headache after five minutes.
        gvrView.setAsyncReprojectionEnabled(true);
        AndroidCompat.setSustainedPerformanceMode(this, true);
        gvrView.setRenderer(this);
        // setGvrView(gvrView);


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
        vrBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (gvrView.getVisibility() == View.INVISIBLE) {
                    mVideoView.setVisibility(View.INVISIBLE);
                    setGvrView(gvrView);
                    gvrView.setVisibility(View.VISIBLE);
                } else {
                    gvrView.setVisibility(View.INVISIBLE);
                    mVideoView.setVisibility(View.VISIBLE);
                }
            }
        });
    }

    /**
     * `hat.
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
        return true;
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

    @Override
    public void onNewFrame(HeadTransform headTransform) {

    }

    @Override
    public void onDrawEye(Eye eye) {
        // Log.d(TAG, "onDrawEye: " + eye);

    }

    @Override
    public void onFinishFrame(Viewport viewport) {

    }

    @Override
    public void onSurfaceChanged(int i, int i1) {
        Log.i(TAG, "onSurfaceChanged");
    }

    @Override
    public void onSurfaceCreated(EGLConfig eglConfig) {

    }

    @Override
    public void onRendererShutdown() {
        Log.i(TAG, "onRendererShutdown");
    }

    @Override
    public ARCONTROLLER_ERROR_ENUM configureDecoder(ARDeviceController deviceController, ARControllerCodec codec) {
        return null;
    }

    @Override
    public ARCONTROLLER_ERROR_ENUM onFrameReceived(ARDeviceController deviceController, ARFrame frame) {
        return null;
    }

    @Override
    public void onFrameTimeout(ARDeviceController deviceController) {

    }

    @OnClick(R.id.bluetooth_btn)
    public void bluetoothConnect() {
        Intent serverIntent = new Intent(this, BluetoothDeviceListActivity.class);
        startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE_SECURE);
    }
}

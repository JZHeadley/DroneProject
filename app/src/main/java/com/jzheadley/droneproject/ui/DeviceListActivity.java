package com.jzheadley.droneproject.ui;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.github.ivbaranov.rxbluetooth.RxBluetooth;
import com.jzheadley.droneproject.R;
import com.jzheadley.droneproject.discovery.DroneDiscoverer;
import com.parrot.arsdk.ARSDK;
import com.parrot.arsdk.ardiscovery.ARDISCOVERY_PRODUCT_ENUM;
import com.parrot.arsdk.ardiscovery.ARDiscoveryDeviceService;
import com.parrot.arsdk.ardiscovery.ARDiscoveryService;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class DeviceListActivity extends AppCompatActivity {
    public static final String EXTRA_DEVICE_SERVICE = "EXTRA_DEVICE_SERVICE";
    private static final String TAG = "DeviceListActivity";
    /**
     * List of runtime permission we need.
     */
    private static final String[] PERMISSIONS_NEEDED = new String[] {
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.READ_EXTERNAL_STORAGE
    };
    /**
     * Code for permission request result handling.
     */
    private static final int REQUEST_CODE_PERMISSIONS_REQUEST = 1;
    public static String EXTRA_DEVICE_ADDRESS = "device_address";

    // this block loads the native libraries
    // it is mandatory
    static {
        ARSDK.loadSDKLibs();
    }

    private final List<ARDiscoveryDeviceService> mDronesList = new ArrayList<>();
    private final BaseAdapter mAdapter = new BaseAdapter() {
        @Override
        public int getCount() {
            return mDronesList.size();
        }

        @Override
        public Object getItem(int position) {
            return mDronesList.get(position);
        }

        @Override
        public long getItemId(int position) {
            return 0;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            Log.d(TAG, "getView: Adding drone");
            View rowView = convertView;
            // reuse views
            if (rowView == null) {
                LayoutInflater inflater = getLayoutInflater();
                rowView = inflater.inflate(android.R.layout.simple_list_item_1, null);
                // configure view holder
                ViewHolder viewHolder = new ViewHolder();
                viewHolder.text = (TextView) rowView.findViewById(android.R.id.text1);
                rowView.setTag(viewHolder);
            }

            // fill data
            ViewHolder holder = (ViewHolder) rowView.getTag();
            ARDiscoveryDeviceService service = (ARDiscoveryDeviceService) getItem(position);
            holder.text.setText(service.getName() + " on " + service.getNetworkType());

            return rowView;
        }
    };
    private final DroneDiscoverer.Listener mDiscovererListener = new DroneDiscoverer.Listener() {

        @Override
        public void onDronesListUpdated(List<ARDiscoveryDeviceService> dronesList) {
            mDronesList.clear();
            mDronesList.addAll(dronesList);

            mAdapter.notifyDataSetChanged();
        }
    };
    public DroneDiscoverer mDroneDiscoverer;
    private RxBluetooth rxBluetooth;
    private BluetoothAdapter bluetoothAdapter;
    private Set<BluetoothDevice> pairedDevices;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_list);
        // rxBluetooth = new RxBluetooth(this);
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B329");
        pairedDevices = bluetoothAdapter.getBondedDevices();
        Log.d(TAG, "onCreate: " + pairedDevices.toString());
        final ListView listView = (ListView) findViewById(R.id.list);
        // Assign adapter to ListView
        listView.setAdapter(mAdapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                // launch the activity related to the type of discovery device service
                Intent intent = null;

                ARDiscoveryDeviceService service = (ARDiscoveryDeviceService) mAdapter.getItem(position);
                ARDISCOVERY_PRODUCT_ENUM product = ARDiscoveryService.getProductFromProductID(service.getProductID());
                switch (product) {
                    case ARDISCOVERY_PRODUCT_ARDRONE:
                    case ARDISCOVERY_PRODUCT_BEBOP_2:
                        intent = new Intent(DeviceListActivity.this, BebopActivity.class);
                        break;

                    default:
                        Log.e(TAG, "The type " + product + " is not supported by this sample");
                }

                if (intent != null) {
                    intent.putExtra(EXTRA_DEVICE_SERVICE, service);
                    startActivity(intent);
                }
            }
        });

        mDroneDiscoverer = new DroneDiscoverer(this);

        Set<String> permissionsToRequest = new HashSet<>();
        for (String permission : PERMISSIONS_NEEDED) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                if (ActivityCompat.shouldShowRequestPermissionRationale(this, permission)) {
                    Toast.makeText(this, "Please allow permission " + permission, Toast.LENGTH_LONG).show();
                    finish();
                    return;
                } else {
                    permissionsToRequest.add(permission);
                }
            }
        }
        if (permissionsToRequest.size() > 0) {
            ActivityCompat.requestPermissions(this,
                    permissionsToRequest.toArray(new String[permissionsToRequest.size()]),
                    REQUEST_CODE_PERMISSIONS_REQUEST);
        }

        mDroneDiscoverer.setup();
        mDroneDiscoverer.addListener(mDiscovererListener);

        // start discovering
        mDroneDiscoverer.startDiscovering();
    }

    @Override
    protected void onResume() {
        super.onResume();

        // setup the drone discoverer and register as listener
        mDroneDiscoverer.setup();
        mDroneDiscoverer.addListener(mDiscovererListener);

        // start discovering
        mDroneDiscoverer.startDiscovering();
    }

    @Override
    protected void onPause() {
        super.onPause();

        // clean the drone discoverer object
        mDroneDiscoverer.stopDiscovering();
        mDroneDiscoverer.cleanup();
        mDroneDiscoverer.removeListener(mDiscovererListener);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        boolean denied = false;
        if (permissions.length == 0) {
            // canceled, finish
            denied = true;
        } else {
            for (int i = 0; i < permissions.length; i++) {
                if (grantResults[i] == PackageManager.PERMISSION_DENIED) {
                    denied = true;
                }
            }
        }

        if (denied) {
            Toast.makeText(this, "At least one permission is missing.", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    static class ViewHolder {
        public TextView text;
    }

}

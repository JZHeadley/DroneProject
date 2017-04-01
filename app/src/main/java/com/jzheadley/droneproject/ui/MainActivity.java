package com.jzheadley.droneproject.ui;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.widget.Button;

import com.jzheadley.droneproject.R;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

public class MainActivity extends AppCompatActivity {

    @BindView(R.id.videoBtn)
    Button videoButton;

    @BindView(R.id.controllerBtn)
    Button controllerButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);
    }

    @OnClick(R.id.videoBtn)
    public void onVideoBtnClick() {
        Intent intent = new Intent(this, DeviceListActivity.class);
        startActivity(intent);
    }

    @OnClick(R.id.controllerBtn)
    public void onControllerBtnClick() {
        Intent intent = new Intent(this, ControllerDebugActivity.class);
        startActivity(intent);

    }
}

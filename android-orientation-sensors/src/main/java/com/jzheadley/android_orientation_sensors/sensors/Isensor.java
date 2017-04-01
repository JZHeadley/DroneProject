package com.jzheadley.android_orientation_sensors.sensors;

public interface Isensor {

    boolean isSupport();

    void on(int speed);

    void off();

    float getMaximumRange();
}

/*
 * Copyright 2015 MbientLab Inc. All rights reserved.
 *
 * IMPORTANT: Your use of this Software is limited to those specific rights
 * granted under the terms of a software license agreement between the user who
 * downloaded the software, his/her employer (which must be your employer) and
 * MbientLab Inc, (the "License").  You may not use this Software unless you
 * agree to abide by the terms of the License which can be found at
 * www.mbientlab.com/terms . The License limits your use, and you acknowledge,
 * that the  Software may not be modified, copied or distributed and can be used
 * solely and exclusively in conjunction with a MbientLab Inc, product.  Other
 * than for the foregoing purpose, you may not use, reproduce, copy, prepare
 * derivative works of, modify, distribute, perform, display or sell this
 * Software and/or its documentation for any purpose.
 *
 * YOU FURTHER ACKNOWLEDGE AND AGREE THAT THE SOFTWARE AND DOCUMENTATION ARE
 * PROVIDED "AS IS" WITHOUT WARRANTY OF ANY KIND, EITHER EXPRESS OR IMPLIED,
 * INCLUDING WITHOUT LIMITATION, ANY WARRANTY OF MERCHANTABILITY, TITLE,
 * NON-INFRINGEMENT AND FITNESS FOR A PARTICULAR PURPOSE. IN NO EVENT SHALL
 * MBIENTLAB OR ITS LICENSORS BE LIABLE OR OBLIGATED UNDER CONTRACT, NEGLIGENCE,
 * STRICT LIABILITY, CONTRIBUTION, BREACH OF WARRANTY, OR OTHER LEGAL EQUITABLE
 * THEORY ANY DIRECT OR INDIRECT DAMAGES OR EXPENSES INCLUDING BUT NOT LIMITED
 * TO ANY INCIDENTAL, SPECIAL, INDIRECT, PUNITIVE OR CONSEQUENTIAL DAMAGES, LOST
 * PROFITS OR LOST DATA, COST OF PROCUREMENT OF SUBSTITUTE GOODS, TECHNOLOGY,
 * SERVICES, OR ANY CLAIMS BY THIRD PARTIES (INCLUDING BUT NOT LIMITED TO ANY
 * DEFENSE THEREOF), OR OTHER SIMILAR COSTS.
 *
 * Should you have any questions regarding your right to use this Software,
 * contact MbientLab Inc, at www.mbientlab.com.
 */

package com.mbientlab.metawear.starter;

import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.support.v4.app.Fragment;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.mbientlab.metawear.MetaWearBoard;
import com.mbientlab.metawear.UnsupportedModuleException;
import com.mbientlab.metawear.android.BtleService;
import com.mbientlab.metawear.data.Acceleration;
import com.mbientlab.metawear.data.CartesianAxis;
import com.mbientlab.metawear.data.EulerAngle;
import com.mbientlab.metawear.data.FloatVector;
import com.mbientlab.metawear.data.Quaternion;
import com.mbientlab.metawear.module.Gpio;
import com.mbientlab.metawear.module.Logging;
import com.mbientlab.metawear.module.SensorFusionBosch;
import com.mbientlab.metawear.module.Timer;

import java.util.Calendar;

import bolts.Task;

/**
 * A placeholder fragment containing a simple view.
 */
public class DeviceSetupActivityFragment extends Fragment implements ServiceConnection {
    public interface FragmentSettings {
        BluetoothDevice getBtDevice();
    }

    private MetaWearBoard mwBoard= null;
    private FragmentSettings settings;
    private Logging logging;
    private Gpio gpio;
    private Timer timer;
    private SensorFusionBosch sensorFusion;
    private Timer.ScheduledTask scheduledTask;

    public DeviceSetupActivityFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);


        Activity owner= getActivity();
        if (!(owner instanceof FragmentSettings)) {
            throw new ClassCastException("Owning activity must implement the FragmentSettings interface");
        }

        settings= (FragmentSettings) owner;
        owner.getApplicationContext().bindService(new Intent(owner, BtleService.class), this, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        ///< Unbind the service when the activity is destroyed
        getActivity().getApplicationContext().unbindService(this);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        setRetainInstance(true);
        return inflater.inflate(R.layout.fragment_device_setup, container, false);
    }

    @Override
    public void onViewCreated(final View view, Bundle savedInstanceState) {
        view.findViewById(R.id.start).setOnClickListener(v -> {
            logging.clearEntries();
            logging.start(false);
            sensorFusion.eulerAngles().start();
            sensorFusion.linearAcceleration().start();
            sensorFusion.quaternion().start();
            sensorFusion.start();
            if(scheduledTask!=null)
                scheduledTask.start();
            else
                Log.e("Error: ","ScheduleTask == null");
        });

        view.findViewById(R.id.stop).setOnClickListener(v -> {
            logging.stop();
            sensorFusion.stop();
            sensorFusion.eulerAngles().stop();
            sensorFusion.linearAcceleration().stop();
            sensorFusion.quaternion().stop();
            if(scheduledTask != null)
                scheduledTask.stop();
            else
                Log.e("Error: ","ScheduleTask == null");
            logging.download().continueWith(ignored ->
                    Log.i("HAND RIGHT", "Log download complete"));
        });
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        mwBoard= ((BtleService.LocalBinder) service).getMetaWearBoard(settings.getBtDevice());
        ready();
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {

    }

    /**
     * Called when the app has reconnected to the board
     */
    public void reconnected() { }

    /**
     * Called when the mwBoard field is ready to be used
     */
    public void ready() {

        mwBoard.tearDown();
        logging = mwBoard.getModule(Logging.class);
        gpio = mwBoard.getModule(Gpio.class);
        timer = mwBoard.getModule(Timer.class);
        sensorFusion = mwBoard.getModule(SensorFusionBosch.class);

        sensorFusion.configure().mode(SensorFusionBosch.Mode.NDOF).
                accRange(SensorFusionBosch.AccRange.AR_2G).
                gyroRange(SensorFusionBosch.GyroRange.GR_250DPS).commit();

        sensorFusion.eulerAngles().addRoute(source ->
                source.log((msg, env) -> {
                    EulerAngle e = msg.value(EulerAngle.class);
                    Calendar c = msg.timestamp();
                    Log.i("FOOT LEFT", "EULER " + msg.timestamp().getTime() + e);
                }))
        .continueWith(task ->  {
            Log.i("Results TASK 1: ", "=====================");
            Log.i("Results error: ", ""+task.getError());
            Log.i("Results result: ", ""+ task.getResult());
            Log.i("Results isCancel: ", ""+task.isCancelled());
            Log.i("Results iscomplete: ", ""+task.isCompleted());
            Log.i("Results isFault: ", ""+task.isFaulted());
            return task.getResult();
        })
        .onSuccessTask(task ->  sensorFusion.linearAcceleration().addRoute(source ->
                source.log((msg, env) -> {
                    Acceleration a = msg.value(Acceleration.class);
                    Calendar c = msg.timestamp();
                    Log.i("FOOT LEFT", "Accel " + msg.timestamp().getTime() + a);
                })))
        .continueWith(task ->  {
        Log.i("Results TASK 2: ", "=====================");
        Log.i("Results error: ", ""+task.getError());
        Log.i("Results result: ", ""+ task.getResult());
        Log.i("Results isCancel: ", ""+task.isCancelled());
        Log.i("Results iscomplete: ", ""+task.isCompleted());
        Log.i("Results isFault: ", ""+task.isFaulted());
        return task.getResult();
        })
        .onSuccessTask(task ->
                gpio.getPin((byte) 0).analogAdc().addRoute(source -> source.log(
                (data, env) -> Log.i("GPIO 0", "" + data.value(Short.class)))))
        .onSuccessTask(ignored -> gpio.getPin((byte) 1).analogAdc().addRoute(source -> source.log(
                (data, env) -> Log.i("GPIO 1", "" + data.value(Short.class)))))
        .onSuccessTask(ignored -> mwBoard.getModule(Timer.class).schedule(33, false, () -> {
                gpio.getPin((byte) 0).analogAdc().read();
                gpio.getPin((byte) 1).analogAdc().read();
        }))
        .continueWith(task -> {
                Log.i("Results TASK 3: ", "=====================");
                Log.i("Results error: ", ""+task.getError());
                Log.i("Results result: ", ""+task.getResult());
                Log.i("Results isCancel: ", ""+task.isCancelled());
                Log.i("Results iscomplete: ", ""+task.isCompleted());
                Log.i("Results isFault: ", ""+task.isFaulted());
                return task;
        });

    }
}

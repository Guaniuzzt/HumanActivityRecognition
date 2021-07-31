package com.example.humanactivityrecognition;

import android.app.Activity;
import android.content.res.AssetFileDescriptor;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import androidx.wear.ambient.AmbientModeSupport;

import org.tensorflow.lite.Interpreter;

import com.example.humanactivityrecognition.databinding.ActivityMainBinding;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends Activity implements SensorEventListener {

    private TextView mTextView;
    private ActivityMainBinding binding;

    private static final int TIME_STAMP = 40;
    private static final String TAG = "MainActivity";

    private static List<Float> ax, ay, az;
    private static List<Float> gx, gy, gz;
    private static List<Float> hr;

    private SensorManager mSensorManager;
    private Sensor mAccelerometer, mGyroscope, mHeartrate;

    private int[] results;
    private ActivityClassifier classifier;

    private TextView standingTextView, walkingTextView, runningTextView, jumpingTextView, heartrateTextView;

    private Interpreter tflite;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());



        initLayoutItems();

        try{
            tflite = new Interpreter(loadModelFile());
        }catch (Exception ex){
            ex.printStackTrace();
        }


        hr = new ArrayList<>();
        ax = new ArrayList<>();
        ay = new ArrayList<>();
        az = new ArrayList<>();
        gx = new ArrayList<>();
        gy = new ArrayList<>();
        gz = new ArrayList<>();

        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mGyroscope = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        mHeartrate = mSensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE);

        //classifier = new ActivityClassifier(getApplicationContext());

        mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_FASTEST);
        mSensorManager.registerListener(this, mGyroscope, SensorManager.SENSOR_DELAY_FASTEST);
        mSensorManager.registerListener(this, mHeartrate, SensorManager.SENSOR_DELAY_FASTEST);

    }

    private void initLayoutItems() {

        standingTextView = findViewById(R.id.standing_TextView);
        walkingTextView = findViewById(R.id.walking_TextView);
        runningTextView = findViewById(R.id.running_TextView);
        jumpingTextView = findViewById(R.id.jumping_TextView);
        heartrateTextView = findViewById(R.id.heartrate_TextView);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {

        Sensor sensor = event.sensor;
        if (sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            ax.add(event.values[0]);
            ay.add(event.values[1]);
            az.add(event.values[2]);
        } else if (sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            gx.add(event.values[0]);
            gy.add(event.values[1]);
            gz.add(event.values[2]);
        } else if (sensor.getType() == Sensor.TYPE_HEART_RATE) {
            hr.add(event.values[0]);
            heartrateTextView.setText("heartrate: \t" + event.values[0]);
        }

        predictActivity();

    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    public int[] doInference(float[] input){
        int[] output = new int[4];
        tflite.run(input, output);
        return output;

    }

    private void predictActivity() {
        List<Float> data = new ArrayList<>();
        if (hr.size() >= TIME_STAMP && ax.size() >= TIME_STAMP && ay.size() >= TIME_STAMP && az.size() >= TIME_STAMP
                && gx.size() >= TIME_STAMP && gy.size() >= TIME_STAMP && gz.size() >= TIME_STAMP) {

            /*data.addAll(hr.subList(0, TIME_STAMP));

            data.addAll(ax.subList(0, TIME_STAMP));
            data.addAll(ay.subList(0, TIME_STAMP));
            data.addAll(az.subList(0, TIME_STAMP));

            data.addAll(gx.subList(0, TIME_STAMP));
            data.addAll(gy.subList(0, TIME_STAMP));
            data.addAll(gz.subList(0, TIME_STAMP));*/

            for(int i=0; i<TIME_STAMP; i++){
                data.add(hr.get(i));
                data.add(ax.get(i));
                data.add(ay.get(i));
                data.add(az.get(i));
                data.add(gx.get(i));
                data.add(gy.get(i));
                data.add(gz.get(i));
            }


            //results = classifier.predictProbabilities(toFloatArray(data));
            //Log.i(TAG, "predictActivity: " + Arrays.toString(results));
            float[] input = toFloatArray(data);
            results = doInference(input);



            standingTextView.setText("Standing: \t" + results[0]);
            walkingTextView.setText("Walking: \t" + results[1]);
            runningTextView.setText("Standing: \t" + results[2]);
            jumpingTextView.setText("Walking: \t" + results[3]);


            data.clear();
            hr.clear();
            ax.clear();
            ay.clear();
            az.clear();
            gx.clear();
            gy.clear();
            gz.clear();

        }
    }

    private float[] toFloatArray(List<Float> data) {
        int i = 0;
        float[] array = new float[data.size()];
        for (Float f : data) {
            array[i++] = (f != null ? f : Float.NaN);
        }
        return array;
    }

    private MappedByteBuffer loadModelFile() throws IOException{

        AssetFileDescriptor fileDescriptor = this.getAssets().openFd("model.tflite");
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel  =inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mSensorManager.registerListener(this,mAccelerometer, SensorManager.SENSOR_DELAY_FASTEST);
        mSensorManager.registerListener(this,mGyroscope, SensorManager.SENSOR_DELAY_FASTEST);
        mSensorManager.registerListener(this,mHeartrate, SensorManager.SENSOR_DELAY_FASTEST);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mSensorManager.unregisterListener(this);
    }


}

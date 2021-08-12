package com.example.humanactivityrecognition;

import android.app.Activity;
import android.content.Context;
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
import com.opencsv.CSVWriter;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

public class MainActivity extends Activity implements SensorEventListener {

    private TextView mTextView;
    private ActivityMainBinding binding;

    private static final int TIME_STAMP = 50;
    private static final String TAG = "MainActivity";

    //private  boolean hr_check = false;
    //private  boolean ac_check = false;
    //private  boolean gr_check = false;

    private static List<Float> ax, ay, az;
    private static List<Float> gx, gy, gz;
    private static List<Float> hr;

    private SensorManager mSensorManager;
    private Sensor mAccelerometer, mGyroscope, mHeartrate;

    private float[][] results;


    private TextView standingTextView, walkingTextView, runningTextView, jumpingTextView, activityTextView, heartrateTextiVew, predictedHeartRateTextView;

    private Interpreter tflite;

    private Interpreter walkingtflite;
    private Interpreter runningtflite;
    private Interpreter jumpingtflite;


    private FileWriter mFileWriter;



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

        try{
            walkingtflite = new Interpreter(loadWalkingModelFile());
        }catch (Exception ex){
            ex.printStackTrace();
        }

        try{
            runningtflite = new Interpreter(loadRunningFile());
        }catch (Exception ex){
            ex.printStackTrace();
        }

        try{
            jumpingtflite = new Interpreter(loadJumpingFile());
        }catch (Exception ex){
            ex.printStackTrace();
        }



        ax = new ArrayList<>();
        ay = new ArrayList<>();
        az = new ArrayList<>();
        gx = new ArrayList<>();
        gy = new ArrayList<>();
        gz = new ArrayList<>();
        hr = new ArrayList<>();

        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        //Log.d("sensorlist", mSensorManager.getSensorList(Sensor.TYPE_ALL).toString());
        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mGyroscope = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        mHeartrate = mSensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE);


        //classifier = new ActivityClassifier(getApplicationContext());

        mSensorManager.registerListener(this, mAccelerometer, 1000000000);
        mSensorManager.registerListener(this, mGyroscope, 10000);
        mSensorManager.registerListener(this, mHeartrate, 0);

    }

    private void initLayoutItems() {

        //standingTextView = findViewById(R.id.standing_TextView);
        //walkingTextView = findViewById(R.id.walking_TextView);
        //runningTextView = findViewById(R.id.running_TextView);
        //jumpingTextView = findViewById(R.id.jumping_TextView);
        activityTextView = findViewById(R.id.activty);
        heartrateTextiVew = findViewById(R.id.heartrate_TextView);
        predictedHeartRateTextView = findViewById(R.id.predicthr);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {

        Sensor sensor = event.sensor;
        if (sensor.getType() == Sensor.TYPE_ACCELEROMETER) {

            Log.d("sensor_ac_time" ,  getCurrentTimeStamp());
            ax.add(event.values[0]);
            ay.add(event.values[1]);
            az.add(event.values[2]);
            Log.d("sensor_ac_data" ,  event.values[0] + "/" + event.values[1]  + "/"  + event.values[2]);


        } else if (sensor.getType() == Sensor.TYPE_GYROSCOPE) {

            Log.d("sensor_gyr_time" ,  getCurrentTimeStamp());
            gx.add(event.values[0]);
            gy.add(event.values[1]);
            gz.add(event.values[2]);
            Log.d("sensor_gyr_data" ,  event.values[0] + "/" + event.values[1]  + "/"  + event.values[2]);


        }else if (sensor.getType() == Sensor.TYPE_HEART_RATE) {

            Log.d("sensor_hr_time" ,  getCurrentTimeStamp());
            hr.add(event.values[0]);
            hr.add(event.values[0]);
            hr.add(event.values[0]);
            hr.add(event.values[0]);
            hr.add(event.values[0]);
            heartrateTextiVew.setText("Current hr is: " + event.values[0]);
            Log.d("sensor_hr_data" ,  Arrays.toString(event.values));

        }
        try {
            predictActivity();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    public String getCurrentTimeStamp() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date());
    }

    public float[][] doInference(float[][][] input){
        float[][] output = new float[1][4];
        tflite.run(input, output);
        return output;
    }

    public float[][] doWalkingInference(float[][][] input){
        float[][] output = new float[1][1];
        walkingtflite.run(input, output);
        return output;
    }

    public float[][] doRunningInference(float[][][] input){
        float[][] output = new float[1][1];
        runningtflite.run(input, output);
        return output;
    }

    public float[][] doJumpingInference(float[][][] input){
        float[][] output = new float[1][1];
        jumpingtflite.run(input, output);
        return output;
    }

    private int findmaxindex(float[] temp){
        //find max value index
        int index = 0;
        float premax = temp[0];
        for(int i=1; i<4; i++){
            if(temp[i] > premax){
                index = i;
                premax = temp[i];
            }
        }
        return index;
    }

    private void writecsv(String fileName, String[] data) throws IOException {
        String baseDir = android.os.Environment.getExternalStorageDirectory().getAbsolutePath();
        //String fileName = "activity.csv";
        String filePath = baseDir + File.separator + fileName;
        File f = new File(filePath);
        CSVWriter writer;
        // File exist
        if(f.exists()&&!f.isDirectory()){
            mFileWriter = new FileWriter(filePath, true);
            writer = new CSVWriter(mFileWriter);
        }else{
            writer = new CSVWriter(new FileWriter(filePath));
        }

        writer.writeNext(data);
        writer.close();
        Log.d("file_created:", getCurrentTimeStamp());
    }

    private void predictActivity() throws IOException {
        float[][][] input = new float[1][TIME_STAMP][6];
        float[][][] input_for_hr = new float[1][TIME_STAMP][7];

        if (ax.size() >= TIME_STAMP && ay.size() >= TIME_STAMP && az.size() >= TIME_STAMP
                && gx.size() >= TIME_STAMP && gy.size() >= TIME_STAMP && gz.size() >= TIME_STAMP && hr.size() >= TIME_STAMP) {

            /*data.addAll(hr.subList(0, TIME_STAMP));

            data.addAll(ax.subList(0, TIME_STAMP));
            data.addAll(ay.subList(0, TIME_STAMP));
            data.addAll(az.subList(0, TIME_STAMP));

            data.addAll(gx.subList(0, TIME_STAMP));
            data.addAll(gy.subList(0, TIME_STAMP));
            data.addAll(gz.subList(0, TIME_STAMP));*/
            Log.d("sensor_ac_size",String.valueOf(ax.size()));
            Log.d("sensor_gx_size",String.valueOf(gx.size()));
            Log.d("sensor_hr_size",String.valueOf(gx.size()));
            for(int i=0; i<TIME_STAMP; i++){
                int j = 0;
                input[0][i][j++] = ax.get(i*8);
                input[0][i][j++] = ay.get(i*8);
                input[0][i][j++] = az.get(i*8);
                input[0][i][j++] = gx.get(i*8);
                input[0][i][j++] = gy.get(i*8);
                input[0][i][j] = gz.get(i*8);
            }

            for(int i=0; i<TIME_STAMP; i++){
                input_for_hr[0][i][0] = hr.get(i);
            }

            for(int i=0; i<TIME_STAMP; i++){
                int j= 1;
                input_for_hr[0][i][j++] = ax.get(i*8);
                input_for_hr[0][i][j++] = ay.get(i*8);
                input_for_hr[0][i][j++] = az.get(i*8);
                input_for_hr[0][i][j++] = gx.get(i*8);
                input_for_hr[0][i][j++] = gy.get(i*8);
                input_for_hr[0][i][j] = gz.get(i*8);
            }





            results = doInference(input);

            int index = findmaxindex(results[0]);
            //find max value index
            /*float[] temp = results[0];
            int index = 0;
            float premax = temp[0];
            for(int i=1; i<4; i++){
                if(temp[i] > premax){
                    index = i;
                    premax = temp[i];
                }
            }*/


            writecsv();




            //standingTextView.setText("Standing: \t" + results[0][0]);
            //alkingTextView.setText("Walking: \t" + results[0][1]);
            //runningTextView.setText("running: \t" + results[0][2]);
            //jumpingTextView.setText("Jumping: \t" + results[0][3]);

            //Log.d("results", temp[0] + "/ " + temp[1] + "/ " + temp[2]  + "/ " + temp[3]);
            float[][] predicthr;
            if(index == 0){
                activityTextView.setText("The activty is: " + "Standing" );
                Log.d("current", "Standing" );
            }else if(index == 1){
                activityTextView.setText("The activty is: " + "Walking" );
                Log.d("current", "Walking" );
                predicthr = doWalkingInference(input_for_hr);
                predictedHeartRateTextView.setText("Predict Heart is:" + predicthr[0][0]);
            }else if(index == 2){
                activityTextView.setText("The activty is: " + "Running" );
                Log.d("current", "Running" );
                predicthr = doRunningInference(input_for_hr);
                predictedHeartRateTextView.setText("Predict Heart is:" + predicthr[0][0]);
            }else if(index == 3){
                activityTextView.setText("The activty is: " + "Jumping" );
                Log.d("current", "Jumping" );
                predicthr = doJumpingInference(input_for_hr);
                predictedHeartRateTextView.setText("Predict Heart is:" + predicthr[0][0]);
            }

            ax.clear();
            ay.clear();
            az.clear();
            gx.clear();
            gy.clear();
            gz.clear();
            hr.clear();
        }
    }


    private MappedByteBuffer loadModelFile() throws IOException{

        AssetFileDescriptor fileDescriptor = this.getAssets().openFd("har_model3.tflite");
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel  =inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    private MappedByteBuffer loadWalkingModelFile() throws IOException{

        AssetFileDescriptor fileDescriptor = this.getAssets().openFd("walking_model.tflite");
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel  =inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    private MappedByteBuffer loadRunningFile() throws IOException{

        AssetFileDescriptor fileDescriptor = this.getAssets().openFd("running_model.tflite");
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel  =inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    private MappedByteBuffer loadJumpingFile() throws IOException{

        AssetFileDescriptor fileDescriptor = this.getAssets().openFd("jumping_model.tflite");
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel  =inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mSensorManager.registerListener(this, mAccelerometer, 2000000000);
        mSensorManager.registerListener(this, mGyroscope, 100000000);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mSensorManager.unregisterListener(this);
    }


}


















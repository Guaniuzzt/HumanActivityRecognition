package com.example.humanactivityrecognition;

import android.app.Activity;
import android.content.res.AssetFileDescriptor;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import org.tensorflow.lite.Interpreter;

import com.example.humanactivityrecognition.databinding.ActivityMainBinding;
import com.opencsv.CSVWriter;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
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


    private TextView activityTextView, heartrateTextiVew;
    private Button startbutton, stopbutton;

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



        startbutton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                collectdata();
            }
        });

        stopbutton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopSensor();
            }
        });

    }

    private void collectdata(){
        mSensorManager.registerListener(this, mAccelerometer, 2000000000);
        mSensorManager.registerListener(this, mGyroscope, 2000000000);
        mSensorManager.registerListener(this, mHeartrate, 0);
    }

    private void stopSensor(){
        mSensorManager.unregisterListener(this);
        ax.clear();
        ay.clear();
        az.clear();
        gx.clear();
        gy.clear();
        gz.clear();
        hr.clear();
    }

    private void initLayoutItems() {

        startbutton = findViewById(R.id.start_button);
        stopbutton = findViewById(R.id.stop_button);
        activityTextView = findViewById(R.id.activty);
        heartrateTextiVew = findViewById(R.id.heartrate_TextView);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {

        Sensor sensor = event.sensor;
        if (sensor.getType() == Sensor.TYPE_ACCELEROMETER) {

            Log.d("sensor_ac_time" ,  getCurrentTimeStamp());
            ax.add(event.values[0]/100);
            ay.add(event.values[1]/100);
            az.add(event.values[2]/100);
            Log.d("sensor_ac_data" ,  event.values[0] + "/" + event.values[1]  + "/"  + event.values[2]);


        } else if (sensor.getType() == Sensor.TYPE_GYROSCOPE) {

            Log.d("sensor_gyr_time" ,  getCurrentTimeStamp());
            gx.add(event.values[0]);
            gy.add(event.values[1]);
            gz.add(event.values[2]);
            Log.d("sensor_gyr_data" ,  event.values[0] + "/" + event.values[1]  + "/"  + event.values[2]);


        }else if (sensor.getType() == Sensor.TYPE_HEART_RATE) {

            Log.d("sensor_hr_time" ,  getCurrentTimeStamp());

            hr.add(event.values[0]/100);
            hr.add(event.values[0]/100);
            hr.add(event.values[0]/100);
            hr.add(event.values[0]/100);
            hr.add(event.values[0]/100);
            hr.add(event.values[0]/100);
            hr.add(event.values[0]/100);
            hr.add(event.values[0]/100);
            hr.add(event.values[0]/100);
            hr.add(event.values[0]/100);
            heartrateTextiVew.setText("Current hr is: " + event.values[0]);
            Log.d("sensor_hr_data" ,  Arrays.toString(event.values));
            String[] temp = new String[2];
            temp[0] = getCurrentTimeStamp();
            temp[1] = event.values[0] + "";
            try {
                writecsv("current_hr.csv",temp);
            } catch (IOException e) {
                e.printStackTrace();
            }
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

        if (ax.size() >= TIME_STAMP  && ay.size() >= TIME_STAMP && az.size() >= TIME_STAMP
                && gx.size() >= TIME_STAMP && gy.size() >= TIME_STAMP && gz.size() >= TIME_STAMP
                && hr.size() >= TIME_STAMP
        ) {


            Log.d("sensor_ac_size",ax.size() + "/" + ay.size() + "/" + az.size());
            Log.d("sensor_gy_size",gx.size()+ "/" + gy.size()+ "/" + gz.size() );
            Log.d("sensor_hr_size",String.valueOf(hr.size()));

            for(int i=0; i<TIME_STAMP; i++){
                int j = 0;

                input[0][i][j++] = ax.get(i * ax.size() / 50 );
                input[0][i][j++] = ay.get(i * ay.size() / 50);
                input[0][i][j++] = az.get(i * az.size() / 50);
                input[0][i][j++] = gx.get(i * gx.size() / 50 );
                input[0][i][j++] = gy.get(i * gy.size() / 50);
                input[0][i][j] = gz.get(i * gz.size() / 50);
            }


            for(int i=0; i<TIME_STAMP; i++){
                input_for_hr[0][i][0] = hr.get(i);
            }

            for(int i=0; i<TIME_STAMP; i++){
                int j= 1;
                input_for_hr[0][i][j++] = ax.get(i);
                input_for_hr[0][i][j++] = ay.get(i);
                input_for_hr[0][i][j++] = az.get(i);
                input_for_hr[0][i][j++] = gx.get(i);
                input_for_hr[0][i][j++] = gy.get(i);
                input_for_hr[0][i][j] = gz.get(i);
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







            //standingTextView.setText("Standing: \t" + results[0][0]);
            //alkingTextView.setText("Walking: \t" + results[0][1]);
            //runningTextView.setText("running: \t" + results[0][2]);
            //jumpingTextView.setText("Jumping: \t" + results[0][3]);

            //Log.d("results", temp[0] + "/ " + temp[1] + "/ " + temp[2]  + "/ " + temp[3]);
            float[][] predicthr = new float[0][0];
            String[] har = new String[2];
            String[] hr_result = new String[2];
            if(index == 0){
                activityTextView.setText("The activty is: " + "Standing" );
                Log.d("current", "Standing" );
                har[0] = getCurrentTimeStamp();
                har[1] = "standing";
            }else if(index == 1){
                activityTextView.setText("The activty is: " + "Walking" );
                Log.d("current", "Walking" );
                predicthr = doWalkingInference(input_for_hr);
                //predictedHeartRateTextView.setText("Predict Heart is:" + predicthr[0][0]);
                har[0] = getCurrentTimeStamp();
                har[1] = "walking";
                hr_result[0] = getCurrentTimeStamp();
                hr_result[1] = String.valueOf(predicthr[0][0]* 100);
                Log.d("predicted heart rate walking", hr_result[1] );
            }else if(index == 2){
                activityTextView.setText("The activty is: " + "Running" );
                Log.d("current", "Running" );
                predicthr = doRunningInference(input_for_hr);
               // predictedHeartRateTextView.setText("Predict Heart is:" + predicthr[0][0]);
                har[0] = getCurrentTimeStamp();
                har[1] = "running";
                hr_result[0] = getCurrentTimeStamp();
                hr_result[1] = String.valueOf(predicthr[0][0]* 100);
                Log.d("predicted heart rate running", hr_result[1] );
            }else if(index == 3){
                activityTextView.setText("The activty is: " + "Jumping Rope" );
                Log.d("current", "Jumping Rope" );
                predicthr = doJumpingInference(input_for_hr);
                //predictedHeartRateTextView.setText("Predict Heart is:" + predicthr[0][0]);
                har[0] = getCurrentTimeStamp();
                har[1] = "jumping";
                hr_result[0] = getCurrentTimeStamp();
                hr_result[1] = String.valueOf(predicthr[0][0]* 100) ;
                Log.d("predicted heart rate jumping", hr_result[1] );
            }
            writecsv("har_record.csv", har);
            writecsv("predicted_hr.csv", hr_result);

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

        AssetFileDescriptor fileDescriptor = this.getAssets().openFd("har_model.tflite");
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

        AssetFileDescriptor fileDescriptor = this.getAssets().openFd("jumping_model_93_36.tflite");
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
        mSensorManager.registerListener(this, mGyroscope, 200000000);
        mSensorManager.registerListener(this, mHeartrate, 0);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mSensorManager.unregisterListener(this);
    }


}


















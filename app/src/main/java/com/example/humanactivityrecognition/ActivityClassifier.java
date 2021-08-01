package com.example.humanactivityrecognition;

import android.content.Context;

import org.tensorflow.contrib.android.TensorFlowInferenceInterface;

public class ActivityClassifier {

    private static final String MODEL_FILE = "model.pb";
    private static final String INPUT_NODE = "lstm_1_input";
    private static final String[] OUTPUT_NODES = {"output/Softmax"};
    private static final String OUTPUT_NODE = "output/Softmax";
    private static final long[] INPUT_SIZE = {1,40,7};
    private static final int OUTPUT_SIZE = 4;


    static{
        System.loadLibrary("tensorflow_inference");
    }

    private TensorFlowInferenceInterface inferenceInterface;

    public ActivityClassifier(Context context){
        inferenceInterface = new TensorFlowInferenceInterface(context.getAssets(), MODEL_FILE);
    }

    public int[] predictProbabilities(float[] data){
        int[]  result = new int[OUTPUT_SIZE];
        inferenceInterface.feed(INPUT_NODE, data, INPUT_SIZE);
        inferenceInterface.run(OUTPUT_NODES);
        inferenceInterface.fetch(OUTPUT_NODE, result);
        return result;

    }
}

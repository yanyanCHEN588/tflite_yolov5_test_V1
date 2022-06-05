package com.example.tflite_yolov5_test_V1;

import android.content.Context;
import android.graphics.RectF;
import android.os.Bundle;

import com.example.tflite_yolov5_test_V1.camera.DetectorActivity;

import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.DocumentsContract;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;

import org.json.JSONArray;
//import org.tensorflow.lite.gpu.GpuDelegate;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.graphics.Bitmap;

import java.lang.Math;
public class MainActivity extends AppCompatActivity {

    final int REQUEST_OPEN_FILE = 1;
    final int REQUEST_OPEN_DIRECTORY = 9999;
    //permission
    private int inputSize = -1;
    private File[] process_files = null;
    private final int REQUEST_PERMISSION = 1000;
    private final String[] PERMISSIONS = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
    };

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        Toast.makeText(this, "onRequestPermissionResult", Toast.LENGTH_LONG).show();
        if (requestCode == REQUEST_PERMISSION){
            boolean result = isGranted();
            Toast.makeText(getApplicationContext(), result ? "OK" : "NG", Toast.LENGTH_SHORT).show();
        }
    }
    private boolean isGranted(){
        for (int i = 0; i < PERMISSIONS.length; i++){
            if (checkSelfPermission(PERMISSIONS[i]) != PackageManager.PERMISSION_GRANTED) {
                if (shouldShowRequestPermissionRationale(PERMISSIONS[i])) {
                    Toast.makeText(this, "permission is required", Toast.LENGTH_LONG).show();
                }
                return false;
            }
        }
        return true;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

    }

    public void OnOpenCameraButtonClick(View view){


        //radio value
            //TfliteRunner runner;
        TfliteRunMode.Mode runmode = getRunModeFromGUI();
        this.inputSize = getInputSizeFromGUI();

        Intent intent = new Intent(MainActivity.this, DetectorActivity.class);

        intent.putExtra("inputSize",this.inputSize);
        intent.putExtra("runmode",runmode);
        startActivity(intent);
    }
    public void OnOpenCalibView(View view){


        //設定"換頁"物件，且換到哪頁
        Intent intent = new Intent(MainActivity.this,CalibSensorActivity.class);
        //啟動換頁
        startActivity(intent);
    }
    private TfliteRunMode.Mode getRunModeFromGUI(){
        boolean model_float = ((RadioButton)findViewById(R.id.radioButton_modelFloat)).isChecked();
        boolean model_int8 = ((RadioButton)findViewById(R.id.radioButton_modelInt)).isChecked();
        boolean precision_fp32 = ((RadioButton)findViewById(R.id.radioButton_runFP32)).isChecked();
        boolean precision_fp16 = ((RadioButton)findViewById(R.id.radioButton_runFP16)).isChecked();
        boolean precision_int8 = ((RadioButton)findViewById(R.id.radioButton_runInt8)).isChecked();
        boolean delegate_none = ((RadioButton)findViewById(R.id.radioButton_delegateNone)).isChecked();
        boolean delegate_nnapi = ((RadioButton)findViewById(R.id.radioButton_delegateNNAPI)).isChecked();
        boolean[] gui_selected = {model_float, model_int8, precision_fp32, precision_fp16, precision_int8, delegate_none, delegate_nnapi};
        final Map<TfliteRunMode.Mode, boolean[]> candidates = new HashMap<TfliteRunMode.Mode, boolean[]>(){{
            put(TfliteRunMode.Mode.NONE_FP32,      new boolean[]{true, false, true, false, false, true, false});
            put(TfliteRunMode.Mode.NONE_FP16,      new boolean[]{true, false, false, true, false, true, false});
            put(TfliteRunMode.Mode.NNAPI_GPU_FP32, new boolean[]{true, false, true, false, false, false, true});
            put(TfliteRunMode.Mode.NNAPI_GPU_FP16, new boolean[]{true, false, false, true, false, false, true});
            put(TfliteRunMode.Mode.NONE_INT8,      new boolean[]{false, true, false, false, true, true, false});
            put(TfliteRunMode.Mode.NNAPI_DSP_INT8, new boolean[]{false, true, false, false, true, false, true});
        }};
        for(Map.Entry<TfliteRunMode.Mode, boolean[]> entry : candidates.entrySet()){
            if (Arrays.equals(gui_selected, entry.getValue())) return entry.getKey();
        }
        //not found
        return null;
    }
    public int getInputSizeFromGUI(){
        RadioButton input_640 = findViewById(R.id.radioButton_640);
        if (input_640.isChecked()) return 640;
        else return 320;
    }
    //Eliminate infeasible run configurations(model, precision)
    public void onModelFloatClick(View view) {
        RadioButton precision_int8 = findViewById(R.id.radioButton_runInt8);
        if (precision_int8.isChecked()){
            RadioButton precision_fp32 = findViewById(R.id.radioButton_runFP32);
            precision_fp32.setChecked(true);
        }
    }
    public void onModelIntClick(View view) {
        RadioButton precision_fp32 = findViewById(R.id.radioButton_runFP32);
        RadioButton precision_fp16 = findViewById(R.id.radioButton_runFP16);
        if (precision_fp32.isChecked() || precision_fp16.isChecked()){
            RadioButton precision_int8 = findViewById(R.id.radioButton_runInt8);
            precision_int8.setChecked(true);
        }
    }
    public void onPrecisionFPClick(View view){
        RadioButton model_int = findViewById(R.id.radioButton_modelInt);
        if (model_int.isChecked()) {
            RadioButton model_fp = findViewById(R.id.radioButton_modelFloat);
            model_fp.setChecked(true);
        }
    }
    public void onPrecisionIntClick(View view){
        RadioButton model_fp = findViewById(R.id.radioButton_modelFloat);
        if (model_fp.isChecked()) {
            RadioButton model_int = findViewById(R.id.radioButton_modelInt);
            model_int.setChecked(true);
        }
    }
}
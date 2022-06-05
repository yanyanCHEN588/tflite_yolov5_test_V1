package com.example.tflite_yolov5_test_V1;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.os.Bundle;

import android.os.Handler;
import android.os.VibrationEffect;
import android.os.Vibrator;

import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.util.HashMap;

import android.view.View.OnClickListener; //for implements OnClickListener

import com.example.tflite_yolov5_test_V1.compass.Compass; //import compass
import com.example.tflite_yolov5_test_V1.compass.SOTWFormatter;
import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;


public class CalibSensorActivity extends AppCompatActivity implements OnClickListener, SensorEventListener {

    private Compass compass;
    private SOTWFormatter sotwFormatter;
    //golbal variable
    TextView tvResult;

    float azi;
    private float pitchGet;
    //    TextView timerText;
    Button btGood,btKeep,btCenter;

    SoundPool soundPool;
    HashMap<Integer, Integer> soundMap=new HashMap<Integer, Integer>(); //不用宣布大小，利用put動態增加


    private void vibrate() {
                //for 26 <api <31
                Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                v.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE));

    }

    //sensor
    SensorManager mSensorManger;
    Sensor mAccelerometer,mRotationVector,mMagnetic;
    TextView tv_acce,tv_magnetic,tv_rotateVector;
    TextView tv_acceSTA,tv_magneticSTA,tv_pitchSTA,tv_rollSTA;

    int delay =10; //判斷時間條件應該放在這裡，不然進去onSensorChange每次都被刷新
    int flat=0;
    int riser=0;
    int flat_count=0;
    int riser_count=0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_calib_sensor);



        btGood = findViewById(R.id.bt_good);
        btKeep = findViewById(R.id.bt_keep);
        btCenter = findViewById(R.id.bt_center);
        //implements OnClickListener
        btGood.setOnClickListener(this);
        btKeep.setOnClickListener(this);
        btCenter.setOnClickListener(this);


        //設置音校屬性
        AudioAttributes attr = new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE) //設置音效使用場景
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build(); // 設置音樂類型
        //soundPool Setting
        soundPool = new SoundPool.Builder().setAudioAttributes(attr) // 將屬給予音效池
                .setMaxStreams(40) // 設置最多可以容納個音效數量，我先估計40個拉
                .build(); //

        // load 方法加載至指定音樂文件，並返回所加載的音樂ID然後給hashmap Int
        // 此處用hashmap來管理音樂文件 load 來自於raw文件
        soundMap.put(1, soundPool.load(this, R.raw.good, 1));
        soundMap.put(2, soundPool.load(this, R.raw.keep, 1));
        soundMap.put(3, soundPool.load(this, R.raw.objincenter, 1));
        soundMap.put(4, soundPool.load(this, R.raw.up, 1));
        soundMap.put(5, soundPool.load(this, R.raw.down, 1));
        soundMap.put(6, soundPool.load(this, R.raw.right, 1));
        soundMap.put(7, soundPool.load(this, R.raw.left, 1));
        soundMap.put(8, soundPool.load(this, R.raw.riser, 1));
        soundMap.put(9, soundPool.load(this, R.raw.flat, 1));
        soundMap.put(10, soundPool.load(this, R.raw.okazi, 1));
        soundMap.put(11, soundPool.load(this, R.raw.bigtrunright, 1));
        soundMap.put(12, soundPool.load(this, R.raw.bigturnleft, 1));
        soundMap.put(13, soundPool.load(this, R.raw.trunright, 1));
        soundMap.put(14, soundPool.load(this, R.raw.turnleft, 1));
        soundMap.put(15, soundPool.load(this, R.raw.slightlyright, 1));
        soundMap.put(16, soundPool.load(this, R.raw.slightlyleft, 1));
        soundMap.put(17, soundPool.load(this, R.raw.north, 1));
        soundMap.put(18, soundPool.load(this, R.raw.eastnorth, 1));
        soundMap.put(19, soundPool.load(this, R.raw.east, 1));
        soundMap.put(20, soundPool.load(this, R.raw.eastsouth, 1));
        soundMap.put(21, soundPool.load(this, R.raw.south, 1));
        soundMap.put(22, soundPool.load(this, R.raw.westsouth, 1));
        soundMap.put(23, soundPool.load(this, R.raw.west, 1));
        soundMap.put(24, soundPool.load(this, R.raw.westnorth, 1));
        soundMap.put(25, soundPool.load(this, R.raw.angel, 1));
        soundMap.put(26, soundPool.load(this, R.raw.pitchwarn, 1));
        soundMap.put(27, soundPool.load(this, R.raw.rollwarn, 1));









        tv_acce = findViewById(R.id.tv_acce);
        tv_magnetic = findViewById(R.id.tv_magnetic);
        tv_rotateVector = findViewById(R.id.tv_rotateVerctor);
        tv_magneticSTA = findViewById(R.id.tv_magneticSTA);
        tv_acceSTA = findViewById(R.id.tv_acceSTA);
        tv_pitchSTA = findViewById(R.id.tv_pitchSTA);
        tv_rollSTA = findViewById(R.id.tv_rollSTA);
        //在onCreata宣告物件 關於Sensor
        mSensorManger = (SensorManager) getSystemService(SENSOR_SERVICE);//由系統取得感測器管理員
        mAccelerometer = mSensorManger.getDefaultSensor(Sensor.TYPE_ACCELEROMETER); //取得加速度感應器
        mMagnetic = mSensorManger.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD); //取得地磁感應器
        mRotationVector = mSensorManger.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR); //取得旋轉向量感測器

        sotwFormatter = new SOTWFormatter(this);
        setupCompass();



    }








    //implements OnClickListener
    @Override
    public void onClick(View view) {
        // 判斷哪個按鈕被點擊而啟動對應聲音
        if (view.getId() == R.id.bt_good) {
            soundPool.play(soundMap.get(1), 1, 1, 0, 0, 1);
        } else if (view.getId() == R.id.bt_keep) {
            soundPool.play(soundMap.get(2), 1, 1, 0, 0, 1);
        } else if (view.getId() == R.id.bt_center) {
            soundPool.play(soundMap.get(3), 1, 1, 0, 0,1.5f); //調快聲音為1.5倍
        }
    }



    private void voicePlay(int status){
        if (status == 1) {
            soundPool.play(soundMap.get(1), 1, 1, 0, 0, 1);
        } else if (status == 2) {
            soundPool.play(soundMap.get(2), 1, 1, 0, 0, 1);
        } else if (status == 3) {
            soundPool.play(soundMap.get(3), 1, 1, 0, 0,1.5f); //調快聲音為1.5倍
        }
    }


    public void OnClickVibreate(View view){
        vibrate();
    }

    public void OnClickDirection(View view){

        int iAzimuth = (int)azi;
        int index = SOTWFormatter.findClosestIndex(iAzimuth);
        if (index == 8){index = 0;}
        soundPool.play(soundMap.get(index+17), 1, 1, 0, 0, 1);
    }
    public void OnClickNull(View view){
        vibrate();
    }
    @Override
    public void onSensorChanged(SensorEvent sensorEvent) { //加速度計值改變時
        float x,y,z;
        x=sensorEvent.values[0];
        y=sensorEvent.values[1];
        z=sensorEvent.values[2];
        String sensorValue = String.format("x軸: %1.2f\n\nY軸: %1.2f\n\nZ軸: %1.2f", x,y,z);


        if (sensorEvent.sensor.equals(mAccelerometer)) {
            tv_acce.setText(sensorValue);
            //判斷手機是直的還平的
            if (delay > 0) { //進入偵測狀態
                if (Math.abs(z) < 5) {
                    riser++;
                } else if (Math.abs(z) > 5) {
                    flat++;
                }
                delay--; //每偵測一次就+1 代表偵測次數
            } else { //結束偵測狀態
                if (riser > flat) {
                    riser_count++;
                    if (riser_count >= 2) {
                        tv_acceSTA.setText("直的");
                        riser_count = 0;
                    }
                } else {
                    flat_count++;
                    if (flat_count >= 2) {
                        tv_acceSTA.setText("平的");
                        flat_count = 0;
                    }
                }
                riser = 0;
                flat = 0;
                delay = 20; //偵測次數
            }
        }


        if (sensorEvent.sensor.equals(mMagnetic))
            tv_magnetic .setText(sensorValue);
        if (sensorEvent.sensor.equals(mRotationVector))
            tv_rotateVector .setText(sensorValue);



    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) { }//精確度改變時 目前不處理



    @Override
    protected void onStart() {
        super.onStart();
        Log.d("Compass", "start compass");
        compass.start();
    }
    @Override
    protected void onResume() {
        super.onResume();
        compass.start();

        //SENSOR_DELAY_UI 可以調整感應頻率
        mSensorManger.registerListener(this,mAccelerometer,SensorManager.SENSOR_DELAY_GAME); //註冊加速度感測的監聽物件
        mSensorManger.registerListener(this,mMagnetic,SensorManager.SENSOR_DELAY_UI); //註冊地磁感測的監聽物件
        mSensorManger.registerListener(this,mRotationVector,SensorManager.SENSOR_DELAY_UI); //註冊旋轉向量感測的監聽物件

    }

    @Override
    protected void onPause() {
        super.onPause();
        compass.stop();
        mSensorManger.unregisterListener(this);//取消 感測器的監聽 manger代表全部
    }
    private void setupCompass() {
        compass = new Compass(this);
        Compass.CompassListener cl = getCompassListener();
        compass.setListener(cl);
    }

    private void adjustSotwLabel(float azimuth,float pitch,float roll) {
        tv_magneticSTA.setText(sotwFormatter.format(azimuth));
        tv_pitchSTA.setText(String.format("%.2f", pitch));
        tv_rollSTA.setText(String.format("%.2f", roll));

    }
    private Compass.CompassListener getCompassListener() {
        return new Compass.CompassListener() {
            @Override
            public void onNewAzimuth(final float azimuth,final float pitch,final float roll) {
                // UI updates only in UI thread
                // https://stackoverflow.com/q/11140285/444966
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
//                        adjustArrow(azimuth);
                        if(pitch>-80){
                            azi=azimuth;
                        }else {Log.d("Compass", "手機拿太直請往下一點點");}
                        if(Math.abs(roll)>20){
                            Log.d("Compass", "手請不要左右歪");
                        }
                        adjustSotwLabel(azi,pitch,roll);

                    }
                });
            }
        };
    }
}
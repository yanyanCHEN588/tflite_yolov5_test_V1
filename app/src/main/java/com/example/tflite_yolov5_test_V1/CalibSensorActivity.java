package com.example.tflite_yolov5_test_V1;

import androidx.appcompat.app.AppCompatActivity;

import android.hardware.Sensor;

import android.hardware.SensorManager;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.os.Bundle;

import android.os.Handler;

import android.util.Log;
import android.view.View;

import android.widget.TextView;

import java.util.HashMap;

import com.example.tflite_yolov5_test_V1.compass.Compass; //import compass
import com.example.tflite_yolov5_test_V1.compass.SOTWFormatter;



public class CalibSensorActivity extends AppCompatActivity{

    private Compass compass;
    private SOTWFormatter sotwFormatter;
    //golbal variable

    float azi;

    SoundPool soundPool;
    HashMap<Integer, Integer> soundMap=new HashMap<Integer, Integer>(); //不用宣布大小，利用put動態增加

    //sensor
    SensorManager mSensorManger;
    Sensor mAccelerometer,mRotationVector,mMagnetic;
    TextView tv_acce,tv_magnetic,tv_rotateVector;
    TextView tv_acceSTA,tv_magneticSTA,tv_pitchSTA,tv_rollSTA;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_calib_sensor);



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
        soundMap.put(3, soundPool.load(this, R.raw.ding, 1));





        tv_magneticSTA = findViewById(R.id.tv_magneticSTA);



        sotwFormatter = new SOTWFormatter(this);
        setupCompass();


        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
//                soundPool.play(soundMap.get(3), 1, 1, 0, 0, 1f);
                finish();        // 退出此Activity
                // 這樣可以避免使用者按 back 後，又回到該 Activity。
            }
        }, 10000); //十秒後返回Main
    }

    public void OnClickDirection(View view){

        int iAzimuth = (int)azi;
        int index = SOTWFormatter.findClosestIndex(iAzimuth);
        if (index == 8){index = 0;}
        soundPool.play(soundMap.get(index+17), 1, 1, 0, 0, 1);
    }

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

    }

    @Override
    protected void onPause() {
        super.onPause();
        compass.stop();
    }
    private void setupCompass() {
        compass = new Compass(this);
        Compass.CompassListener cl = getCompassListener();
        compass.setListener(cl);
    }

    private void adjustSotwLabel(float azimuth,float pitch,float roll) {
        tv_magneticSTA.setText(sotwFormatter.format(azimuth));
//        tv_pitchSTA.setText(String.format("%.2f", pitch));
//        tv_rollSTA.setText(String.format("%.2f", roll));

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
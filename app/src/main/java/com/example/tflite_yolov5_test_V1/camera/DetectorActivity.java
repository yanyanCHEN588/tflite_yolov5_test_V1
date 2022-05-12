package com.example.tflite_yolov5_test_V1.camera;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.hardware.SensorManager;
import android.media.AudioAttributes;
import android.media.ImageReader;
import android.media.SoundPool;
import android.os.Bundle;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.util.Size;
import android.util.TypedValue;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.RotateAnimation;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.example.tflite_yolov5_test_V1.R;
import com.example.tflite_yolov5_test_V1.camera.env.BorderedText;
import com.example.tflite_yolov5_test_V1.camera.env.ImageUtils;
import com.example.tflite_yolov5_test_V1.camera.tracker.MultiBoxTracker;
import com.example.tflite_yolov5_test_V1.customview.OverlayView;
import com.example.tflite_yolov5_test_V1.TfliteRunner;
import com.example.tflite_yolov5_test_V1.TfliteRunMode;

import java.util.HashMap;
import java.util.List;

import com.example.tflite_yolov5_test_V1.compass.Compass; //import compass
import com.example.tflite_yolov5_test_V1.compass.SOTWFormatter;

public class DetectorActivity extends CameraActivity implements ImageReader.OnImageAvailableListener {

    private Compass compass;
    private SOTWFormatter sotwFormatter;
    private float azi;
    private float aziTarget=270f;
    private float currentAzimuth;
    private float rotateTheta;


    private  static final String TAG = "cameraINFO";
    private  static final String TAG2 = "testResult";
    private  static final String TAG3 = "compass";
//    private static final int TF_OD_API_INPUT_SIZE = 320; //ORI
    private  int TF_OD_API_INPUT_SIZE = 320;
    private static final boolean TF_OD_API_IS_QUANTIZED = true;
    private static final String TF_OD_API_MODEL_FILE = "detect.tflite";
    private static final String TF_OD_API_LABELS_FILE = "labelmap.txt";
    private  TfliteRunMode.Mode MODE = TfliteRunMode.Mode.NONE_INT8;
//    private  static final TfliteRunMode.Mode MODE = TfliteRunMode.Mode.NONE_INT8; //ORI
    // Minimum detection confidence to track a detection.
    private static final float MINIMUM_CONFIDENCE_TF_OD_API = 0.5f;
    private static final boolean MAINTAIN_ASPECT = false;
    private static final Size DESIRED_PREVIEW_SIZE = new Size(720, 720);
    private static final boolean SAVE_PREVIEW_BITMAP = false;
    private static final float TEXT_SIZE_DIP = 10;
    OverlayView trackingOverlay;
    private Integer sensorOrientation;

    private TfliteRunner detector;
    private long nowTime=0;
    private long lastProcessingTimeMs = 0;
    private long locateVoiceTime = 0;
    private long centerVoiceTime = 0;
    private long directionVoiceTime = 0;
    private Bitmap rgbFrameBitmap = null;
    private Bitmap croppedBitmap = null;
    private Bitmap cropCopyBitmap = null;

    private boolean computingDetection = false;

    private long timestamp = 0;

    private Matrix frameToCropTransform;
    private Matrix cropToFrameTransform;

    private MultiBoxTracker tracker;

    private BorderedText borderedText;

    TextView tv_magneticSTA; //for global

    //for sound
    SoundPool soundPool;
    HashMap<Integer, Integer> soundMap=new HashMap<Integer, Integer>(); //不用宣布大小，利用put動態增加

    //status
    Integer in_status=0;

    //物件指引至中心
    Float dist_x=0f;
    Float dist_y=0f;
    Integer voice_num=0;

    private void vibrate(){
        Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        v.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE));
    }

    protected Size getDesiredPreviewFrameSize() {
        return DESIRED_PREVIEW_SIZE;
    }

    protected int getLayoutId() {
        return R.layout.tfe_od_camera_connection_fragment_tracking;
    }

    public void OnClickDirection(View view){

        int iAzimuth = (int)azi;
        int index = SOTWFormatter.findClosestIndex(iAzimuth);
        if (index == 8){index = 0;}
        soundPool.play(soundMap.get(index+17), 1, 1, 0, 0, 1);
    }


    public float getConfThreshFromGUI(){ return ((float)((SeekBar)findViewById(R.id.conf_seekBar2)).getProgress()) / 100.0f;}
    public float getIoUThreshFromGUI(){ return ((float)((SeekBar)findViewById(R.id.iou_seekBar2)).getProgress()) / 100.0f;}
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //get mode from Main
        Intent intent=getIntent();
        int TF_OD_API_INPUT_SIZE_get=intent.getIntExtra("inputSize", 320);
        TfliteRunMode.Mode MODE_get = (TfliteRunMode.Mode)intent.getSerializableExtra("runmode");
        //assing value
        TF_OD_API_INPUT_SIZE = TF_OD_API_INPUT_SIZE_get;
        MODE=MODE_get;
        //show TextView
        TextView modeTextView = (TextView)findViewById(R.id.modeTextView); //here is local
        tv_magneticSTA = findViewById(R.id.tv_magneticSTA); //form global

        String modeText = String.format("Size:%d Mode:%s", TF_OD_API_INPUT_SIZE,MODE.toString());
        modeTextView.setText(modeText);

        SeekBar conf_seekBar = (SeekBar)findViewById(R.id.conf_seekBar2);
        conf_seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                TextView conf_textView = (TextView)findViewById(R.id.conf_TextView2);
                float thresh = (float)progress / 100.0f;
                conf_textView.setText(String.format("Confidence Threshold: %.2f", thresh));
                if (detector != null) detector.setConfThresh(thresh);
            }
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }
        });
        conf_seekBar.setMax(100);
        conf_seekBar.setProgress(25);//0.25
        SeekBar iou_seekBar = (SeekBar)findViewById(R.id.iou_seekBar2);
        iou_seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                TextView iou_textView = (TextView)findViewById(R.id.iou_TextView2);
                float thresh = (float)progress / 100.0f;
                iou_textView.setText(String.format("IoU Threshold: %.2f", thresh));
                if (detector != null) detector.setIoUThresh(thresh);
            }
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }
        });
        iou_seekBar.setMax(100);
        iou_seekBar.setProgress(45);//0.45

        //setting compass
        sotwFormatter = new SOTWFormatter(this);
        setupCompass();

        /*
        TODO:voiceControl
        將聲音變成class的方法使用
        設置音校屬性
        */
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
    }

    private void setupCompass() {
        compass = new Compass(this);
        Compass.CompassListener cl = getCompassListener();
        compass.setListener(cl);
    }
    @Override
    public void onResume() {
        super.onResume();
        compass.start();

    }
    @Override
    protected void onStart() {
        super.onStart();
        Log.d("Compass", "start compass");
        compass.start();
    }

    @Override
    public void onPause() {
        super.onPause();
        compass.stop();
    }

    @Override
    protected void setUseNNAPI(final boolean isChecked) {

    }

    @Override
    protected void setNumThreads(final int numThreads) {
        //runInBackground(() -> detector.setNumThreads(numThreads));
    }
    @Override
    public void onPreviewSizeChosen(final Size size, final int rotation) {
        final float textSizePx =
                TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP, TEXT_SIZE_DIP, getResources().getDisplayMetrics());
        borderedText = new BorderedText(textSizePx);
        borderedText.setTypeface(Typeface.MONOSPACE);

        tracker = new MultiBoxTracker(this);

        int cropSize = TF_OD_API_INPUT_SIZE;

        try {
            detector = new TfliteRunner(this, MODE, TF_OD_API_INPUT_SIZE, 0.25f, 0.45f);
            cropSize = TF_OD_API_INPUT_SIZE;
        } catch (final Exception e) {
            e.printStackTrace();
            Toast toast =
                    Toast.makeText(
                            getApplicationContext(), "Detector could not be initialized", Toast.LENGTH_SHORT);
            toast.show();
            finish();
        }

        previewWidth = size.getWidth();
        previewHeight = size.getHeight();
        int a = getScreenOrientation();
        sensorOrientation = rotation - getScreenOrientation();
        Log.i(TAG, "Camera orientation relative to screen canvas: "+ sensorOrientation);
        Log.i(TAG, "Initializing at size  "+previewWidth+"*"+previewHeight);

        rgbFrameBitmap = Bitmap.createBitmap(previewWidth, previewHeight, Bitmap.Config.ARGB_8888);
        croppedBitmap = Bitmap.createBitmap(cropSize, cropSize, Bitmap.Config.ARGB_8888);

        frameToCropTransform =
                ImageUtils.getTransformationMatrix(
                        previewWidth, previewHeight,
                        cropSize, cropSize,
                        sensorOrientation, MAINTAIN_ASPECT);

        cropToFrameTransform = new Matrix();
        frameToCropTransform.invert(cropToFrameTransform);

        trackingOverlay = (OverlayView) findViewById(R.id.tracking_overlay);
        trackingOverlay.addCallback(
                new OverlayView.DrawCallback() {
                    @Override
                    public void drawCallback(final Canvas canvas) {
                        tracker.draw(canvas);
                    }
                });

        tracker.setFrameConfiguration(getDesiredPreviewFrameSize(), TF_OD_API_INPUT_SIZE, sensorOrientation);
    }

    @Override
    protected void processImage() {
        trackingOverlay.postInvalidate();

        // No mutex needed as this method is not reentrant.
        if (computingDetection) {
            readyForNextImage();
            return;
        }
        computingDetection = true;

        rgbFrameBitmap.setPixels(getRgbBytes(), 0, previewWidth, 0, 0, previewWidth, previewHeight);

        readyForNextImage();

        final Canvas canvas = new Canvas(croppedBitmap);
        canvas.drawBitmap(rgbFrameBitmap, frameToCropTransform, null);

        runInBackground(
                new Runnable() {
                    @Override
                    public void run() {
                        nowTime = SystemClock.uptimeMillis();
                        float fps = (float)1000 / (float)(nowTime - lastProcessingTimeMs);
                        lastProcessingTimeMs = nowTime;


                        //ImageUtils.saveBitmap(croppedBitmap);
                        detector.setInput(croppedBitmap);
                        final List<TfliteRunner.Recognition> results = detector.runInference();

                        cropCopyBitmap = Bitmap.createBitmap(croppedBitmap);
                        final Canvas canvas = new Canvas(cropCopyBitmap); //設定canvas就是把crop當畫布結果
                        final Paint paint = new Paint();
                        paint.setColor(Color.YELLOW);//黃色畫筆
                        paint.setStyle(Paint.Style.STROKE);
                        paint.setStrokeWidth(2.0f);
                        final Paint paintBound = new Paint();
                        paintBound.setColor(Color.GREEN);//綠色畫筆
                        paintBound.setStyle(Paint.Style.STROKE);
                        paintBound.setStrokeWidth(2.0f);

                        //reverse
                        float frameHeight =720;
                        float detectorInputSize =TF_OD_API_INPUT_SIZE;

                        float frameToCanvasScale = Math.min((float)canvas.getHeight() / frameHeight, (float)canvas.getWidth() / frameHeight);
                        float scale_width = frameToCanvasScale * ((float)frameHeight / detectorInputSize);
                        float scale_height = frameToCanvasScale * ((float)frameHeight / detectorInputSize);
                        float x1 = (detectorInputSize *(float) (1.0/4.0))  * scale_width;
                        float y1 = (detectorInputSize *(float) (1.0/4.0)) * scale_height;
                        float x2 = (detectorInputSize *(float) (3.0/4.0)) * scale_width;
                        float y2 = (detectorInputSize *(float) (3.0/4.0)) * scale_height;

                        final RectF trackedPosD = new RectF(x1, y1, x2, y2);
                        canvas.drawRect(trackedPosD, paintBound); //由x1,y1,x2,y2畫出四個邊界框框

                        int resultNum=0; //紀錄現在的index
                        int targetIndex= 999; //最大面積的target
                        float maxArea=0f;
                        for (final TfliteRunner.Recognition result : results) {
                            //有看到指定物件　且 信心>0.2 就計算面積
                            if(result.getClass_idx()==3 && result.getConfidence()>=0.2f){
                                float area =result.getLocation().width()*result.getLocation().height();
                                //最大的面積該index會記錄起來
                                if (area > maxArea){
                                    maxArea=area;
                                    targetIndex=resultNum;
                                }
                            }
                            resultNum++;
                        }

//                        Log.d(TAG2,String.format("targetIndex=%d",targetIndex));
                        //這裡就是找到最大的唯一大面積物件index後的運算
                        if (results.size()!=0 && targetIndex!=999){

//                            Log.d(TAG2, String.format("cls_id =%d",results.get(targetIndex).getClass_idx()));
                            final TfliteRunner.Recognition result=results.get(targetIndex);
                            final RectF location = result.getLocation();
                            final String title = result.getTitle();
                            final Integer class_id = result.getClass_idx();
                            canvas.drawRect(location, paint);
                            canvas.drawPoint(location.centerX(),location.centerY(), paint);
                            Log.d(TAG2, "results  location"+ location+"title>"+title+" id:"+class_id);
//                                if(location.centerX()<trackedPosD.centerX()&& location.centerY()<trackedPosD.centerY()){
                            //判斷物件是否在中心
                            if(location.centerX()>trackedPosD.left && location.centerX()<trackedPosD.right  && location.centerY()>trackedPosD.top && location.centerY()<trackedPosD.bottom){
                                Log.d(TAG2, "in!!!!.............");
                                if(in_status==0){
                                    //進去中心只會撥放一次聲音了，但是會因為物間在中心邊界造成震盪，而標註框閃爍時會有重疊音
                                    in_status=1;
                                    soundPool.play(soundMap.get(3), 1, 1, 0, 0,1.5f);
                                }

                            }else if ( in_status==1 ){ //不在中心且已經有in_status代表進去中心過了
                                in_status=0;
                                vibrate();

                            }

                            //物件指引至中心
                            if (nowTime-locateVoiceTime>3000) {//偵測間隔時間差3s才放聲音
                                locateVoiceTime = nowTime;
                                //物件指引至中心 已經看到指定ID的物件了 class_id == XX
                                dist_x = location.centerX() - trackedPosD.centerX();
                                dist_y = location.centerY() - trackedPosD.centerY();
                                if (Math.abs(dist_x) > Math.abs(dist_y)) { //絕對距離 x>y 左右移動
                                    if (location.centerX() < trackedPosD.left) {//物件在左邊界外、要右移，但畫面是相反的，所以左移
                                        soundPool.play(soundMap.get(7), 1, 1, 0, 0, 1f);
                                    } else if (location.centerX() > trackedPosD.right) {//物件在右邊界外、要左移，但畫面是相反的，所以右移
                                        soundPool.play(soundMap.get(6), 1, 1, 0, 0, 1f);
                                    }
                                }

                                if (Math.abs(dist_x) < Math.abs(dist_y)) {//絕對距離 x<y  上下移動
                                    if (location.centerY() < trackedPosD.top) {//物件在上邊界外、要下移，但畫面是相反的，所以上移
                                        soundPool.play(soundMap.get(4), 1, 1, 0, 0, 1f);
                                    } else if (location.centerY() > trackedPosD.bottom) {//物件在下邊界外、要上移，但畫面是相反的，所以下移
                                        soundPool.play(soundMap.get(5), 1, 1, 0, 0, 1f);
                                    }
                                }

                            }

                        }
                        tracker.trackResults(results);
                        trackingOverlay.postInvalidate();

                        computingDetection = false;

                        runOnUiThread(
                                new Runnable() {
                                    @Override
                                    public void run() {
                                        TextView fpsTextView = (TextView)findViewById(R.id.textViewFPS);
                                        String fpsText = String.format("FPS: %.2f", fps);
                                        fpsTextView.setText(fpsText);
                                        TextView latencyTextView = (TextView)findViewById(R.id.textViewLatency);
                                        latencyTextView.setText(detector.getLastElapsedTimeLog());
                                    }
                                });
                    }
                });
    }


    private void adjustDirection(float azimuth) {
        int rotateStatus=0;
//        if(azimuth>180){azimuth=azimuth-360f;}
//        if(aziTarget>180){aziTarget=aziTarget-360f;}
        rotateTheta = azimuth - aziTarget;
        Log.d(TAG3, String.format("rotateTheta = %f",rotateTheta));
        if (Math.abs(rotateTheta)>90){
            if(rotateTheta>0){
                Log.d(TAG3, "大大向左轉校正 ");
                rotateStatus=2;
            }
            if(rotateTheta<0){
                Log.d(TAG3, "大大向右轉校正");
                rotateStatus=1;
            }
        }
        if(rotateTheta>45f && rotateTheta<=90f){
            Log.d(TAG3, "向左轉");
            rotateStatus=4;
        }
        if(rotateTheta>23.5f && rotateTheta<=45f){
            Log.d(TAG3, "稍微向左轉");
            rotateStatus=6;
        }
        if(rotateTheta<-45f && rotateTheta>=-90f){
            Log.d(TAG3, "向右轉");
            rotateStatus=3;
        }
        if(rotateTheta<-23.5f && rotateTheta>=-45f){
            Log.d(TAG3, "稍微向右轉");
            rotateStatus=5;
        }
        if (Math.abs(rotateTheta)<23.5f){
            Log.d(TAG3, "okAZI");
            rotateStatus=0;
        }
        //物件指引至中心
        if (nowTime-directionVoiceTime>4000) {//偵測間隔時間差3s才放聲音
            directionVoiceTime = nowTime;
            soundPool.play(soundMap.get(rotateStatus+10), 1, 1, 0, 0, 1);
            }


    }
    private void adjustSotwLabel(float azimuth) {
        tv_magneticSTA.setText(sotwFormatter.format(azimuth));
    }
    private Compass.CompassListener getCompassListener() {
        return new Compass.CompassListener() {
            @Override
            public void onNewAzimuth(final float azimuth) {
                // UI updates only in UI thread
                // https://stackoverflow.com/q/11140285/444966
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
//                        adjustArrow(azimuth);
                        adjustDirection(azimuth);
                        adjustSotwLabel(azimuth);
                        azi=azimuth;
                    }
                });
            }
        };
    }

}

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
import android.widget.AdapterView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.example.tflite_yolov5_test_V1.R;
import com.example.tflite_yolov5_test_V1.camera.env.BorderedText;
import com.example.tflite_yolov5_test_V1.camera.env.ImageUtils;
import com.example.tflite_yolov5_test_V1.camera.tracker.MultiBoxTracker;
import com.example.tflite_yolov5_test_V1.customview.OverlayView;
import com.example.tflite_yolov5_test_V1.TfliteRunner;
import com.example.tflite_yolov5_test_V1.TfliteRunMode;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import com.example.tflite_yolov5_test_V1.compass.Compass; //import compass
import com.example.tflite_yolov5_test_V1.compass.SOTWFormatter;

public class DetectorActivity extends CameraActivity implements ImageReader.OnImageAvailableListener {

    private Compass compass;
    private SOTWFormatter sotwFormatter;
    private float azi;
    private float aziTarget=999f;
    private float aziItem=999f;
    private float currentAzimuth;
    private float rotateTheta;
    private float thAreaRatio=0.8f;


    private  static final String TAG = "cameraINFO";
    private  static final String TAG2 = "testResult";
    private  static final String TAG3 = "compass";
    private  static final String TAG4 = "testGudance";
    private  static final String TAG5 = "voiceStatus";
    private  static final String TAG6 = "testCenterArea";
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
    private long recordAreaTime  = 0;
    private long directionVoiceTime = 0;
    private long wornPoseTime = 0;

    private Bitmap rgbFrameBitmap = null;
    private Bitmap croppedBitmap = null;
    private Bitmap cropCopyBitmap = null;

    private boolean computingDetection = false;

    private long timestamp = 0;

    private Matrix frameToCropTransform;
    private Matrix cropToFrameTransform;

    private MultiBoxTracker tracker;

    private BorderedText borderedText;

    private TextView tv_magneticSTA,tv_spinner; //for global

    private Spinner spinnerItem;
    private int targetItem=999;
    private int previousItem=999;

    //for sound
    private SoundPool soundPool;
    private HashMap<Integer, Integer> soundMap=new HashMap<Integer, Integer>(); //不用宣布大小，利用put動態增加

    //status
    private int in_status=0;
    private int wornStatus=0;
    private int poseStatus=0;
    private int rotateStatus=0;
    private int guidanceDirection=0;
    private int centerStatus=0;
    private int guidanceCenter=0;
    private int voiceCenterCounter=0;
    private int wornStatusCount=0;
    //物件指引至中心
    private Float dist_x=0f;
    private Float dist_y=0f;
    private Integer voice_num=0;

    //For guidance to direction
    float maxTargerArea = 0f;



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
        tv_spinner = findViewById(R.id.tv_item);

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

        Integer[] bigArr = new Integer[] {7,11};
        List<Integer> bigList = Arrays.asList(bigArr);
        Integer[] bigMidArr = new Integer[] {10,13,16};
        List<Integer> bigMidList = Arrays.asList(bigMidArr);
        Integer[] midArr = new Integer[] { 0,1,15 };
        List<Integer> midList = Arrays.asList(midArr);
        Integer[] midSmlArr = new Integer[] {2,3,4,5,6,9,12,14};
        List<Integer> midSmlList = Arrays.asList(midSmlArr);
        Integer[] smlArr = new Integer[] {8,14};
        List<Integer> smlList = Arrays.asList(smlArr);

        //選單init
        spinnerItem = findViewById(R.id.spinnerItem);

        spinnerItem.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
            //如果馬上選擇選單我要做什麼
                String[] SelectItem=getResources().getStringArray(R.array.SelectItem);
                int indexSP = spinnerItem.getSelectedItemPosition(); //被選取項目的位置
                tv_spinner.setText(SelectItem[indexSP]);

                if(previousItem !=targetItem ){ //上個物件狀態不等於剛剛選取的 >重設大家狀態
                    aziTarget=999f; //不再方向聲音
                    aziItem=999f;
                    maxTargerArea=0f;
                    recordAreaTime = 0;
                    in_status=0;
                    wornStatus=0;
                    poseStatus=0;
                    rotateStatus=0;
                    guidanceDirection=0;
                    centerStatus=0;
                    guidanceCenter=0;
                    voiceCenterCounter=0;
                    wornStatusCount=0;

                    previousItem=targetItem;
                    Log.d(TAG4, String.format("changeItem!"));
                }
                targetItem = (indexSP==0) ? 999:indexSP-1; //如果indexSP==0(None)就給999

                if(bigList.indexOf(targetItem) != -1){
                    thAreaRatio=0.8f;
                }else if(bigMidList.indexOf(targetItem) != -1){
                    thAreaRatio=0.5f;
                }else if(midList.indexOf(targetItem) != -1) {
                    thAreaRatio=0.25f;
                }else if(midSmlList.indexOf(targetItem) != -1){
                    thAreaRatio=0.11f;
                }else{
                    thAreaRatio=0.06f;
                }
                Log.d(TAG4, String.format("thAreaRatio=%f",thAreaRatio));

            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                targetItem=999;
            //沒有被選擇我什麼也不做:))
            }
        });


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
        soundMap.put(10, soundPool.load(this, R.raw.okaziman, 1));
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
        soundMap.put(28, soundPool.load(this, R.raw.fpslower, 1));
        soundMap.put(29, soundPool.load(this, R.raw.okazi, 1));
        soundMap.put(30, soundPool.load(this, R.raw.turnback, 1));
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

                        if (fps < 5f){wornStatus=28;}else {wornStatus=0;}
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
                        float thArea = thAreaRatio * detectorInputSize * scale_width * detectorInputSize* scale_height ; //目標物在畫面超過多少面積比例的標準
                        for (final TfliteRunner.Recognition result : results) {
                            if (targetItem!=999) {//有被清單選擇到
                                //有看到指定物件　且 信心>0.2 就計算面積
                                if (result.getClass_idx() == targetItem && result.getConfidence() >= 0.2f) {
                                    float area = result.getLocation().width() * result.getLocation().height();
                                    //最大的面積該index會記錄起來
                                    if (area > maxArea) {
                                        maxArea = area;
                                        targetIndex = resultNum;
                                    }
                                }
                            }
                            resultNum++;
                        }

                        if (maxArea>maxTargerArea){ //這偵目標物件面積>最大值
                            //record  now direction and recordTime
                            aziItem=azi;
                            recordAreaTime=nowTime;
                            maxTargerArea=maxArea;
                            Log.d(TAG4, String.format("record  now direction!"));

                        }

                        if(nowTime-recordAreaTime>5000 && targetItem!=999){//如果record時間差大於5秒 且非NONEitem
                            recordAreaTime=nowTime;
                            aziTarget = aziItem ; //設定目標方向 //只要aziTarget不是999就會自動進入引導
                            Log.d(TAG4, String.format("5 sec for setting aziTarget"));

                        }

                        //這裡就是找到最大的唯一大面積物件index後的運算
                        if (results.size()!=0 && targetIndex!=999&&guidanceDirection==1){

//                            Log.d(TAG2, String.format("cls_id =%d",results.get(targetIndex).getClass_idx()));
                            final TfliteRunner.Recognition result=results.get(targetIndex);
                            final RectF location = result.getLocation();
                            final String title = result.getTitle();
                            final Integer class_id = result.getClass_idx();
                            canvas.drawRect(location, paint);
                            canvas.drawPoint(location.centerX(),location.centerY(), paint);
                            Log.d(TAG2, "results  location"+ location+"title>"+title+" id:"+class_id);

                            centerStatus=0;
                            //判斷物件是否在中心
                            if(location.centerX()>trackedPosD.left && location.centerX()<trackedPosD.right  && location.centerY()>trackedPosD.top && location.centerY()<trackedPosD.bottom){
                                Log.d(TAG2, "in!!!!.............");
                                if(in_status==0){
                                    //TODO 進去中心只會撥放一次聲音了，但是會因為物間在中心邊界造成震盪，而標註框閃爍時會有重疊音
                                    in_status=1;
                                    centerStatus=3;
                                }
                                float nowArea=result.getLocation().width() * result.getLocation().height();
                                float testAreaRatio= nowArea/(detectorInputSize * scale_width * detectorInputSize* scale_height);
                                Log.d(TAG6, String.format("area =%f",testAreaRatio));
                                if(nowArea >thArea ){
                                    Log.d(TAG2, "area OK!~~~~~~");
                                    Log.d(TAG6, String.format("OK~~~~~~~~~~area =%f",nowArea));
                                    centerStatus=25;
                                    //音樂太慢響 OK了
                                    soundPool.play(soundMap.get(centerStatus), 1, 1, 0, 0, 1f);
                                }

                            }else if ( in_status==1 ){ //不在中心且已經有in_status代表進去中心過了
                                in_status=0;
                                vibrate();

                            }


                            //物件指引至中心 已經看到指定ID的物件了 class_id == XX
                            dist_x = location.centerX() - trackedPosD.centerX();
                            dist_y = location.centerY() - trackedPosD.centerY();
                            if (Math.abs(dist_x) > Math.abs(dist_y)) { //絕對距離 x>y 左右移動
                                if (location.centerX() < trackedPosD.left) {//物件在左邊界外、要右移，但畫面是相反的，所以左移
                                    centerStatus=7;
//                                        soundPool.play(soundMap.get(7), 1, 1, 0, 0, 1f);
                                } else if (location.centerX() > trackedPosD.right) {//物件在右邊界外、要左移，但畫面是相反的，所以右移
                                    centerStatus=6;
//                                        soundPool.play(soundMap.get(6), 1, 1, 0, 0, 1f);
                                }
                            }

                            if (Math.abs(dist_x) < Math.abs(dist_y)) {//絕對距離 x<y  上下移動
                                if (location.centerY() < trackedPosD.top) {//物件在上邊界外、要下移，但畫面是相反的，所以上移
                                    centerStatus=4;
                                } else if (location.centerY() > trackedPosD.bottom) {//物件在下邊界外、要上移，但畫面是相反的，所以下移
                                    centerStatus=5;
                                }
                            }




                        }

                        tracker.trackResults(results);
                        trackingOverlay.postInvalidate();

                        int voiceStatus=0;
                        //語音播放控制
                        if (nowTime-locateVoiceTime>2000) {//偵測間隔時間差2s才放聲音
                            if(wornStatus!=0){
                                wornStatusCount++;
                                if (wornStatusCount>2){
                                    voiceStatus=wornStatus;
                                    wornStatusCount=0;
                                }

                            }else if(poseStatus!=0){
                                voiceStatus = poseStatus;
                                poseStatus=0;
                            }else if(guidanceDirection!=1 ){ //方向不正確

                                voiceStatus = rotateStatus;
//                                rotateStatus=0;
//                                if(in_status==1){
//                                    voiceStatus=29; //物件有在中心，直接女生聲音方向正確(整合目標物在畫面中央了+方向正確)
//                                }
                            }else{

                                if(/*voiceCenterCounter<=2 &&*/centerStatus!=0 && in_status!=1){
                                    voiceStatus = centerStatus;
                                    centerStatus=0;
                                }
                                if(in_status==1){
                                    voiceCenterCounter++;
                                    if(voiceCenterCounter==1||voiceCenterCounter==5){voiceStatus = 3;}
                                }else{voiceCenterCounter=0;}

//                                voiceStatus = centerStatus;
                            }

                            guidanceDirection = (rotateStatus==10) ? 1 :0 ; //在方向正確給1,其餘都是給0 //放這裡至少方向正確會播放一次聲音
                            Log.d(TAG5, String.format("voiceStatus =%d",voiceStatus));
                            if(voiceStatus !=0 && voiceStatus!=99){
                                soundPool.play(soundMap.get(voiceStatus), 1, 1, 0, 0, 1f);
                                locateVoiceTime = nowTime;
                            }

                        }

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

        rotateTheta = azimuth - aziTarget;
        Log.d(TAG3, String.format("rotateTheta = %f",rotateTheta));
        if (Math.abs(rotateTheta)>90){
            if(azimuth >aziTarget){
//                rotateStatus = (rotateTheta<180f) ? 2 :1 ; //1是大大向右轉,2是大大向左轉
                if (rotateTheta<180f){
                    Log.d(TAG3, "大大向左轉校正");
                    rotateStatus=12;
                }
                else {
                    Log.d(TAG3, "大大向右轉校正 ");
                    rotateStatus=11;
                }

            }
            if(aziTarget>azimuth){
//                rotateStatus = (aziTarget-azimuth<180f) ? 11 :12 ; //1是大大向右轉,2是大大向左轉
                if (aziTarget-azimuth<180f){
                    Log.d(TAG3, "大大向右轉校正");
                    rotateStatus=11;
                }
                else {
                    Log.d(TAG3, "大大向左轉校正 ");
                    rotateStatus=12;
                }

            }

        }
        if(rotateTheta>45f && rotateTheta<=90f){
            Log.d(TAG3, "向左轉");
            rotateStatus=14;
        }
        if(rotateTheta>23.5f && rotateTheta<=45f){
            Log.d(TAG3, "稍微向左轉");
            rotateStatus=16;
        }
        if(rotateTheta<-45f && rotateTheta>=-90f){
            Log.d(TAG3, "向右轉");
            rotateStatus=13;
        }
        if(rotateTheta<-23.5f && rotateTheta>=-45f){
            Log.d(TAG3, "稍微向右轉");
            rotateStatus=15;
        }
        if (Math.abs(rotateTheta)<23.5f){
            Log.d(TAG3, "okAZI");
            rotateStatus=10;
//            soundPool.play(soundMap.get(10), 1, 1, 0, 0, 1); //強制方向正確就要播音，不受時時間限制XXX 不行放這會有重複音
//            guidanceCenter=1; //方向正確才畫面引導
        }
//        //物件方向語音指引
//        if (nowTime-directionVoiceTime>3000) {//偵測間隔時間差3s才放聲音
//            directionVoiceTime = nowTime;
//            if(guidanceDirection!=1){//方向不正確就要播放聲音，正確則不再播
//            soundPool.play(soundMap.get(rotateStatus), 1, 1, 0, 0, 1);}
//            }
//        else { //雖然不在三秒內，但至少要給方向正確的聲音一次
//            if (guidanceDirection !=1 && rotateStatus==10 ){//準備切換為gudanceDirection正確前要給方向正確的聲音
//                soundPool.play(soundMap.get(rotateStatus), 1, 1, 0, 0, 1);
//            }
//        }
//        guidanceDirection = (rotateStatus==10) ? 1 :0 ; //在方向正確給1,其餘都是給0 //放這裡至少方向正確會播放一次聲音

    }
    private void adjustSotwLabel(float azimuth) {
        tv_magneticSTA.setText(sotwFormatter.format(azimuth));
    }

    private void adjustUserPose(float pitch , float roll){


        if(Math.abs(roll)>20){
            Log.d(TAG3, "手請不要左右歪");
            poseStatus=27;
        }
        else if(pitch<=-80){
            Log.d(TAG3, "手機拿太直請往下一點點");
            poseStatus=26;
        }else {poseStatus=0;}
//        if ((poseStatus==26 || poseStatus ==27)&&nowTime-wornPoseTime>3000) {//間隔時間差2s才放聲音
//            wornPoseTime = nowTime;
//            soundPool.play(soundMap.get(poseStatus), 1, 1, 0, 0, 1.3f);
//
//        }

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

                        if(pitch>-80){
                            azi=azimuth; //在這範圍才紀錄AZI 否則維持與提示
                        }
                        if(aziTarget != 999f){

                            adjustDirection(azi);
                            //手慢慢靠近畫面中央過程中，如果手歪掉就使方向等於沒有偵測
                            if(in_status==1 && Math.abs(roll)>15){
                                rotateStatus=0;
                            }
                        }
                        if(in_status!=1){adjustUserPose(pitch ,  roll);} //物件不在中心才檢測姿態


                        adjustSotwLabel(azi);

                    }
                });
            }
        };
    }

}

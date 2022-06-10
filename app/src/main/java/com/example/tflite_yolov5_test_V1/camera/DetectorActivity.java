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
import android.os.CountDownTimer;
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
    private float azi; //nowAzi
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
    private  static final String TAG7 = "testFpsDetect";
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

    private TextView tv_magneticSTA,tv_aziTarger; //for global

    private Spinner spinnerItem;
    private int targetItem=999;
    private int previousItem=999;
    private int resetItem=999;

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

    //count
    private int countSeeDirectionSatus=0; //有沒有在該方向看到物件計算的狀態
    private int countFPS;
    private int yesResults=0;
    private int countResultsFPS=0;
    int targetIndex= 999; //最大面積的target
    private int arraySecCount=0;

    //array
    private float arrayFrame[] = new float[5];
    private float[] arrayChoose = {0, 999f, 0, 0, 0, 0};


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

    public void OnClickReset(View view){ //同樣的物件重新尋找，就不用重複切換清單
        aziTarget=999f; //不再方向聲音
        tv_aziTarger.setText(String.valueOf(aziTarget));
        aziItem=999f;
        maxTargerArea=0f;
        arraySecCount=0;
        arrayChoose = new float[]{0, 999f, 0, 0, 0, 0}; //初始化arrayChoose
        Arrays.fill(arrayFrame, 0); //初始化arrayFrame
        recordAreaTime = 0;
        locateVoiceTime = 0;
        in_status=0;
        wornStatus=0;
        poseStatus=0;
        rotateStatus=0;
        guidanceDirection=0;
        centerStatus=0;
        guidanceCenter=0;
        voiceCenterCounter=0;
        wornStatusCount=0;
        targetItem=resetItem; //就是繼續上次選的物件

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
        tv_aziTarger = findViewById(R.id.tv_item);

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
//                tv_spinner.setText(SelectItem[indexSP]);

                if(previousItem !=targetItem ){ //上個物件狀態不等於剛剛選取的 >重設大家狀態
                    aziTarget=999f; //不再方向聲音
                    tv_aziTarger.setText(String.valueOf(aziTarget));
                    aziItem=999f;
                    maxTargerArea=0f;
                    arraySecCount=0;
                    arrayChoose = new float[]{0, 999f, 0, 0, 0, 0}; //初始化arrayChoose
                    Arrays.fill(arrayFrame, 0); //初始化arrayChoose
                    recordAreaTime = 0;
                    locateVoiceTime = 0;
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
                resetItem =targetItem; //for resetItem
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
        soundMap.put(31, soundPool.load(this, R.raw.ding, 1));
        soundMap.put(32, soundPool.load(this, R.raw.findagain, 1));
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
                        targetIndex= 999; //最大面積的target
                        float maxConfinence=0f;
                        float maxArea=0f;
                        float thArea = thAreaRatio * detectorInputSize * scale_width * detectorInputSize* scale_height ; //目標物在畫面超過多少面積比例的標準
                        for (final TfliteRunner.Recognition result : results) {
                            if (targetItem!=999) {//有被清單選擇到
                                //有看到指定物件　且 信心>0.2 就計算面積
                                if (result.getClass_idx() == targetItem && result.getConfidence() >= 0.25f) {
                                    float confinence = result.getConfidence();
//                                    float area = result.getLocation().width() * result.getLocation().height();
                                    //最大的面積該index會記錄起來
//                                    if (area > maxArea) {
//                                        maxArea = area;
//                                        targetIndex = resultNum;
//                                    }
                                    if (confinence > maxConfinence) {
                                        maxConfinence = confinence;
                                        targetIndex = resultNum;
                                    }
                                }
                            }
                            resultNum++;
                        }

                        //每一秒的抓取狀況
                        Log.d(TAG7, String.format("result len =%d",results.size()));
                        if(countFPS==0 && targetIndex!=999){
                            countFPS=1;//代表在看
                            arraySecCount++;
                            Arrays.fill(arrayFrame, 0); //初始化arrayFrame這一秒的array
                            resultsCount();
                        }
                        //計算一秒內檢查次數的張數
                        if(results.size()>=0 && countFPS==1){
                            if(targetIndex!=999&&targetItem!=7&&targetItem!=15){
                                final TfliteRunner.Recognition result=results.get(targetIndex);
                                arrayFrame[0]+=result.getConfidence();
                                arrayFrame[1]+=azi;
                                arrayFrame[2]+=result.getLocation().width() * result.getLocation().height();
                                yesResults++;
                            }

                            if(targetIndex!=999&&targetItem==7){
                                //如果目標物是椅子，需要高大於框25%才行紀錄
                                final TfliteRunner.Recognition result=results.get(targetIndex);
                                if(result.getLocation().width()*1.25f < result.getLocation().height()) {
                                    arrayFrame[0] += result.getConfidence();
                                    arrayFrame[1] += azi;
                                    arrayFrame[2] += result.getLocation().width() * result.getLocation().height();
                                    yesResults++;
                                }
                            }

                            if(targetIndex!=999&&targetItem==15){
                                //如果目標物是鍵盤，信心值要大於0.6才行紀錄
                                final TfliteRunner.Recognition result=results.get(targetIndex);
                                if(result.getConfidence()>0.7) {
                                    arrayFrame[0] += result.getConfidence();
                                    arrayFrame[1] += azi;
                                    arrayFrame[2] += result.getLocation().width() * result.getLocation().height();
                                    yesResults++;
                                }
                            }

                            countResultsFPS++;
                        }else {yesResults=0;}



                        if(nowTime-recordAreaTime>5000 && targetItem!=999){//如果record時間差大於5秒 且非NONEitem
                            aziTarget = arrayChoose[1] ; //設定目標方向 //只要aziTarget不是999就會自動進入引導
                            arraySecCount=0;
                            tv_aziTarger.setText(String.valueOf(aziTarget));
                            Log.d(TAG4, String.format("5 sec for setting aziTarget"));
                            recordAreaTime=nowTime;

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
                                    //reset Satus 除非按下重新啟動或是找開啟清單找新物件
                                    aziTarget=999f; //不再方向聲音
                                    tv_aziTarger.setText(String.valueOf(aziTarget));
                                    aziItem=999f;
                                    maxTargerArea=0f;
                                    arraySecCount=0;
                                    arrayChoose = new float[]{0, 999f, 0, 0, 0, 0}; //初始化arrayChoose
                                    Arrays.fill(arrayFrame, 0); //初始化arrayChoose
                                    recordAreaTime = 0;
                                    locateVoiceTime = 0;
                                    in_status=0;
                                    wornStatus=0;
                                    poseStatus=0;
                                    rotateStatus=0;
                                    guidanceDirection=0;
                                    centerStatus=0;
                                    guidanceCenter=0;
                                    voiceCenterCounter=0;
                                    wornStatusCount=0;
                                    targetItem=999; //reset targetItem

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
                                if(targetIndex!=999 && rotateStatus==10){//目標物有在畫面且方向正確就給女生聲音說方向正確
                                    voiceStatus=29;
                                }
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

                        //在方向正確後開始執行是不是真的看到
                        //在沒有看到的狀態下 且方向正確
                        if(countSeeDirectionSatus==0 &&guidanceDirection==1){
                            countSeeDirectionSatus=1;//代表在看
                            triggeredCount();


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
    private void resultsCount(){

        new CountDownTimer(1000, 10) {//照這樣是8秒倒數 //偵測率為0.2秒
            public void onTick(long millisUntilFinished) {
//                    String info_t1m = Long.toString(millisUntilFinished);
//                    Log.i("testCountDown", "method " + info_t1m);

//                mTextField.setText("seconds remaining: " + millisUntilFinished / 1000);
            }

            public void onFinish() {
                int resultPoint = 0; //這次新的得分
                int targetPoint = 0; //原本最大值的得分

                Log.i("testCountDown", String.format("ResultsFPS=%d", countResultsFPS));
                Log.i("testCountDown", String.format("see frame pre sec=%d", yesResults));
                Log.i("testCountDown", "method Done!");

                //取有抓到次數的平均
                arrayFrame[0] = arrayFrame[0] / yesResults; //confidence
                arrayFrame[1] = arrayFrame[1] / yesResults; //Azi
                arrayFrame[2] = (arrayFrame[2] / yesResults); //Area
                arrayFrame[3] = (float) yesResults; //detected pre sec
                arrayFrame[4] = arrayFrame[3] / (float) countResultsFPS;  //detected Rate pre sec (0~1)

                Log.i("testThreadResult", String.format("arrayFrame[0] = %f", arrayFrame[0]));
                Log.i("testThreadResult", String.format("arrayFrame[1] = %f", arrayFrame[1]));
                Log.i("testThreadResult", String.format("arrayFrame[2] = %f", arrayFrame[2]));
                Log.i("testThreadResult", String.format("arrayFrame[3] = %f", arrayFrame[3]));
                Log.i("testThreadResult", String.format("arrayFrame[4] = %f", arrayFrame[4]));



                //上面每秒看完後比較最大的喔!

                if (Math.abs(arrayFrame[0] - arrayChoose[0]) > 0.2) {
                    //兩者信心值差異大於0.2
                    if (arrayFrame[0] > arrayChoose[0]) {
                        resultPoint++;
                    } else {
                        targetPoint++;
                    }

                }

                if (Math.abs(arrayFrame[4] - arrayChoose[4]) > 0.2) {
                    //兩者出現率差異大於0.2
                    if (arrayFrame[4] > arrayChoose[4]) {
                        resultPoint++;
                        if(targetItem==7||targetItem==15){resultPoint++;} //椅子與鍵盤要出現率較為重要
                    } else {
                        targetPoint++;
                        if(targetItem==7||targetItem==15){resultPoint++;}//椅子與鍵盤要出現率較為重要
                    }

                }

                if (resultPoint == targetPoint) {
                    //前面兩項比完竟然平手的話 0:0 or 1:1
                    //再比較面積
                    if (arrayFrame[2] > arrayChoose[2]) {
                        resultPoint++;
                    } else {
                        targetPoint++;
                    }
                }

                if(resultPoint>targetPoint){
                    //最佳的存起來
                    arrayChoose[0] = arrayFrame[0] ; //confidence
                    arrayChoose[1] = arrayFrame[1] ; //Azi
                    arrayChoose[2] = arrayFrame[2] ; //Area
                    arrayChoose[3] = arrayFrame[3] ; //detected pre sec
                    arrayChoose[4] = arrayFrame[4] ;  //detected Rate pre sec (0~1)
                    recordAreaTime=nowTime;
                    soundPool.play(soundMap.get(31), 1, 1, 0, 0, 1f);


                }
                countResultsFPS = 0;
                countFPS = 0;
                yesResults = 0;

            }


        }.start();
    }

    //方向正確後進來確認看看三秒內有沒有指定物件
    private void triggeredCount() {

            new CountDownTimer(8000, 200) {//照這樣是8秒倒數 //偵測率為0.2秒
                int seeItem = 0;
                int nonseeItem = 0;
                int centerCount=0;
                public void onTick(long millisUntilFinished) {
                    if (centerStatus == 3) { //目標物在畫面中央，在這期間發生兩次後就不會觸發重新尋找

                        centerCount++;
                    }


                    if (guidanceDirection == 1 && centerCount<2 && millisUntilFinished>3000l) { //正確方向上才去紀錄 且進入中心次數小於兩次
                            //也要剩下時間大於3秒


                        if (targetIndex == 999) {
                            nonseeItem++;
                        } else {
                            seeItem++;
                        }
                    }

                    if (centerStatus != 0) {
                        //當有畫面引導就重新歸零
                        seeItem = 0;
                        nonseeItem = 0;
                    }

//                    String info_t1m = Long.toString(millisUntilFinished);
//                    Log.i("testCountDown", "method " + info_t1m);
//                mTextField.setText("seconds remaining: " + millisUntilFinished / 1000);
                }

                public void onFinish() {
                    //我想盡量避免重新尋找觸發
                    if((nonseeItem+seeItem)!=0 &&seeItem/(nonseeItem+seeItem) <0.8 &&guidanceDirection==1 && targetIndex==999 ){ //再確認一次現在是在方向正確上 且真的沒有看到
                        //看到率低於八成>>就要重新尋找
                        centerStatus=32;
                        soundPool.play(soundMap.get(centerStatus), 1, 1, 0, 0, 1f);

                        //重新尋找
                        aziTarget=999f; //不再方向聲音
                        tv_aziTarger.setText(String.valueOf(aziTarget));
                        aziItem=999f;
                        maxTargerArea=0f;
                        arraySecCount=0;
                        arrayChoose = new float[]{0, 999f, 0, 0, 0, 0}; //初始化arrayChoose
                        Arrays.fill(arrayFrame, 0); //初始化arrayChoose
                        recordAreaTime = 0;
                        locateVoiceTime = 0;
                        in_status=0;
                        wornStatus=0;
                        poseStatus=0;
                        rotateStatus=0;
                        guidanceDirection=0;
                        centerStatus=0;
                        guidanceCenter=0;
                        voiceCenterCounter=0;
                        wornStatusCount=0;
                        targetItem=resetItem; //就是繼續上次選的物件

                    }
//                    Log.i("testCountDown", "method Done!");
                    countSeeDirectionSatus=0; //reset 看的狀態
                }

            }.start();
    }
    private void adjustDirection(float azimuth) {

        float turnRight,turnLeft;
        if(azimuth>aziTarget){//now>target

            turnRight = (360-azimuth)+(aziTarget-0);
            turnLeft = (azimuth-aziTarget);
            //右轉比較小，就右轉
            if(turnRight<turnLeft){
                if (turnRight<=23.5f){
                    Log.d(TAG3, "okAZI");
                    rotateStatus=10;
                }else if(turnRight>23.5f && turnRight<=45f){
                    Log.d(TAG3, "稍微向右轉");
                    rotateStatus=15;
                }
                else if(turnRight>45f && turnRight<=90f){
                    Log.d(TAG3, "向右轉");
                    rotateStatus=13;
                }else {
                    Log.d(TAG3, "大大向右轉校正");
                    rotateStatus=11;
                }
            }
            //左轉比較小，就左轉
            if(turnRight>turnLeft){
                if (turnLeft<=23.5f){
                    Log.d(TAG3, "okAZI");
                    rotateStatus=10;
                }else if(turnLeft>23.5f && turnLeft<=45f){
                    Log.d(TAG3, "稍微向左轉");
                    rotateStatus=16;
                }
                else if(turnLeft>45f && turnLeft<=90f){
                    Log.d(TAG3, "向左轉");
                    rotateStatus=14;
                }else {
                    Log.d(TAG3, "大大向左轉校正 ");
                    rotateStatus=12;
                }
            }


        }
        if(aziTarget>azimuth){//now>target

            turnRight = (aziTarget-azimuth);
            turnLeft = (azimuth-0)+(360-aziTarget);

            //右轉比較小，就右轉
            if(turnRight<turnLeft){
                if (turnRight<=23.5f){
                    Log.d(TAG3, "okAZI");
                    rotateStatus=10;
                }else if(turnRight>23.5f && turnRight<=45f){
                    Log.d(TAG3, "稍微向右轉");
                    rotateStatus=15;
                }
                else if(turnRight>45f && turnRight<=90f){
                    Log.d(TAG3, "向右轉");
                    rotateStatus=13;
                }else {
                    Log.d(TAG3, "大大向右轉校正");
                    rotateStatus=11;
                }
            }
            //左轉比較小，就左轉
            if(turnRight>turnLeft){
                if (turnLeft<=23.5f){
                    Log.d(TAG3, "okAZI");
                    rotateStatus=10;
                }else if(turnLeft>23.5f && turnLeft<=45f){
                    Log.d(TAG3, "稍微向左轉");
                    rotateStatus=16;
                }
                else if(turnLeft>45f && turnLeft<=90f){
                    Log.d(TAG3, "向左轉");
                    rotateStatus=14;
                }else {
                    Log.d(TAG3, "大大向左轉校正 ");
                    rotateStatus=12;
                }
            }


        }


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

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
    private int modelVsionRadio=0;
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
    private HashMap<Integer, Integer> soundMap=new HashMap<Integer, Integer>(); //???????????????????????????put????????????

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
    private int dingStatus=0; //?????????????????????ding
    //?????????????????????
    private Float dist_x=0f;
    private Float dist_y=0f;
    private Integer voice_num=0;

    //For guidance to direction
    float maxTargerArea = 0f;

    //count
    private int countSeeDirectionSatus=0; //????????????????????????????????????????????????
    private int countFPS;
    private int yesResults=0;
    private int countResultsFPS=0;
    int targetIndex= 999; //???????????????target
    private int arraySecCount=0; //????????????????????????

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

    public void OnClickReset(View view){ //?????????????????????????????????????????????????????????
        aziTarget=999f; //??????????????????
        tv_aziTarger.setText(String.valueOf(aziTarget));
        aziItem=999f;
        maxTargerArea=0f;
        arraySecCount=0;
        arrayChoose = new float[]{0, 999f, 0, 0, 0, 0}; //?????????arrayChoose
        Arrays.fill(arrayFrame, 0); //?????????arrayFrame
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
        targetItem=resetItem; //??????????????????????????????

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
        modelVsionRadio=intent.getIntExtra("modelVersion", 0);
        String modelVersion = (modelVsionRadio==0) ? "S" : "M";
        //assing value
        TF_OD_API_INPUT_SIZE = TF_OD_API_INPUT_SIZE_get;
        MODE=MODE_get;
        //show TextView
        TextView modeTextView = (TextView)findViewById(R.id.modeTextView); //here is local
        tv_magneticSTA = findViewById(R.id.tv_magneticSTA); //form global
        tv_aziTarger = findViewById(R.id.tv_item);

        String modeText = String.format("Version:%s Size:%d Mode:%s", modelVersion,TF_OD_API_INPUT_SIZE,MODE.toString());
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

        Integer[] bigArr = new Integer[] {6,11};
        List<Integer> bigList = Arrays.asList(bigArr);
        Integer[] bigMidArr = new Integer[] {10};
        List<Integer> bigMidList = Arrays.asList(bigMidArr);
        Integer[] midArr = new Integer[] { 0,1,8 };
        List<Integer> midList = Arrays.asList(midArr);
        Integer[] midSmlArr = new Integer[] {2,3,4,5,9};
        List<Integer> midSmlList = Arrays.asList(midSmlArr);
        Integer[] smlArr = new Integer[] {7};
        List<Integer> smlList = Arrays.asList(smlArr);

        //??????init
        spinnerItem = findViewById(R.id.spinnerItem);

        spinnerItem.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
            //???????????????????????????????????????
                String[] SelectItem=getResources().getStringArray(R.array.SelectItem);
                int indexSP = spinnerItem.getSelectedItemPosition(); //????????????????????????
//                tv_spinner.setText(SelectItem[indexSP]);

                if(previousItem !=targetItem ){ //?????????????????????????????????????????? >??????????????????
                    aziTarget=999f; //??????????????????
                    tv_aziTarger.setText(String.valueOf(aziTarget));
                    aziItem=999f;
                    maxTargerArea=0f;
                    arraySecCount=0;
                    arrayChoose = new float[]{0, 999f, 0, 0, 0, 0}; //?????????arrayChoose
                    Arrays.fill(arrayFrame, 0); //?????????arrayChoose
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
                targetItem = (indexSP==0) ? 999:indexSP-1; //??????indexSP==0(None)??????999
                resetItem =targetItem; //for resetItem
                if(bigList.indexOf(targetItem) != -1){
                    thAreaRatio=0.75f;
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
            //?????????????????????????????????:))
            }
        });


        /*
        TODO:voiceControl
        ???????????????class???????????????
        ??????????????????
        */
        AudioAttributes attr = new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE) //????????????????????????
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build(); // ??????????????????
        //soundPool Setting
        soundPool = new SoundPool.Builder().setAudioAttributes(attr) // ?????????????????????
                .setMaxStreams(40) // ??????????????????????????????????????????????????????40??????
                .build(); //

        // load ???????????????????????????????????????????????????????????????ID?????????hashmap Int
        // ?????????hashmap????????????????????? load ?????????raw??????
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
            detector = new TfliteRunner(this,modelVsionRadio, MODE, TF_OD_API_INPUT_SIZE, 0.25f, 0.45f);
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
                        final Canvas canvas = new Canvas(cropCopyBitmap); //??????canvas?????????crop???????????????
                        final Paint paint = new Paint();
                        paint.setColor(Color.YELLOW);//????????????
                        paint.setStyle(Paint.Style.STROKE);
                        paint.setStrokeWidth(2.0f);
                        final Paint paintBound = new Paint();
                        paintBound.setColor(Color.GREEN);//????????????
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
                        canvas.drawRect(trackedPosD, paintBound); //???x1,y1,x2,y2????????????????????????



                        int resultNum=0; //???????????????index
                        targetIndex= 999; //???????????????target
                        float maxConfinence=0f;
                        float maxArea=0f;
                        float thArea = thAreaRatio * detectorInputSize * scale_width * detectorInputSize* scale_height ; //???????????????????????????????????????????????????
                        for (final TfliteRunner.Recognition result : results) {
                            if (targetItem!=999) {//?????????????????????
                                //??????????????????????????? ??????>0.2 ???????????????
                                if (result.getClass_idx() == targetItem && result.getConfidence() >= 0.25f) {
                                    float confinence = result.getConfidence();
//                                    float area = result.getLocation().width() * result.getLocation().height();
                                    //??????????????????index???????????????
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

                        //????????????????????????
                        Log.d(TAG7, String.format("result len =%d",results.size()));
                        if(countFPS==0 && targetIndex!=999){
                            countFPS=1;//????????????
                            arraySecCount++;
                            Arrays.fill(arrayFrame, 0); //?????????arrayFrame????????????array
                            resultsCount();
                        }
                        //????????????????????????????????????
                        if(results.size()>=0 && countFPS==1){
                            if(targetIndex!=999&&targetItem!=7&&targetItem!=15){
                                final TfliteRunner.Recognition result=results.get(targetIndex);
                                arrayFrame[0]+=result.getConfidence();
                                arrayFrame[1]+=azi;
                                arrayFrame[2]+=result.getLocation().width() * result.getLocation().height();
                                yesResults++;
                            }

                            if(targetIndex!=999&&targetItem==6){
                                //?????????????????????????????????????????????25%????????????
                                final TfliteRunner.Recognition result=results.get(targetIndex);
                                if(result.getLocation().width()*1.25f < result.getLocation().height()) {
                                    arrayFrame[0] += result.getConfidence();
                                    arrayFrame[1] += azi;
                                    arrayFrame[2] += result.getLocation().width() * result.getLocation().height();
                                    yesResults++;
                                }
                            }

                            if(targetIndex!=999&&targetItem==8){
                                //?????????????????????????????????????????????0.6????????????
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



                        if(nowTime-recordAreaTime>5000 && targetItem!=999){//??????record???????????????5??? ??????NONEitem
                            //??????????????????????????????ding>>???????????????????????????????????????ding
//                            if(aziTarget!=arrayChoose[1]){soundPool.play(soundMap.get(31), 1, 1, 0, 0, 1f);}
                            aziTarget = arrayChoose[1] ; //?????????????????? //??????aziTarget??????999????????????????????????
                            arraySecCount=0;
                            dingStatus=0;
                            tv_aziTarger.setText(String.valueOf(aziTarget));
                            Log.d(TAG4, String.format("5 sec for setting aziTarget"));
                            recordAreaTime=nowTime;

                        }

                        //????????????????????????????????????????????????index????????????
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
                            //???????????????????????????
                            if(location.centerX()>trackedPosD.left && location.centerX()<trackedPosD.right  && location.centerY()>trackedPosD.top && location.centerY()<trackedPosD.bottom){
                                Log.d(TAG2, "in!!!!.............");
                                if(in_status==0){
                                    //TODO ?????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????
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
                                    //??????????????? OK???
                                    soundPool.play(soundMap.get(centerStatus), 1, 1, 0, 0, 1f);
                                    //reset Satus ?????????????????????????????????????????????????????????
                                    aziTarget=999f; //??????????????????
                                    tv_aziTarger.setText(String.valueOf(aziTarget));
                                    aziItem=999f;
                                    maxTargerArea=0f;
                                    arraySecCount=0;
                                    arrayChoose = new float[]{0, 999f, 0, 0, 0, 0}; //?????????arrayChoose
                                    Arrays.fill(arrayFrame, 0); //?????????arrayChoose
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

                            }else if ( in_status==1 ){ //????????????????????????in_status????????????????????????
                                in_status=0;
                                vibrate();

                            }


                            //????????????????????? ??????????????????ID???????????? class_id == XX
                            dist_x = location.centerX() - trackedPosD.centerX();
                            dist_y = location.centerY() - trackedPosD.centerY();
                            if (Math.abs(dist_x) > Math.abs(dist_y)) { //???????????? x>y ????????????
                                if (location.centerX() < trackedPosD.left) {//????????????????????????????????????????????????????????????????????????
                                    centerStatus=7;
//                                        soundPool.play(soundMap.get(7), 1, 1, 0, 0, 1f);
                                } else if (location.centerX() > trackedPosD.right) {//????????????????????????????????????????????????????????????????????????
                                    centerStatus=6;
//                                        soundPool.play(soundMap.get(6), 1, 1, 0, 0, 1f);
                                }
                            }

                            if (Math.abs(dist_x) < Math.abs(dist_y)) {//???????????? x<y  ????????????
                                if (location.centerY() < trackedPosD.top) {//????????????????????????????????????????????????????????????????????????
                                    centerStatus=4;
                                } else if (location.centerY() > trackedPosD.bottom) {//????????????????????????????????????????????????????????????????????????
                                    centerStatus=5;
                                }
                            }




                        }

                        tracker.trackResults(results);
                        trackingOverlay.postInvalidate();

                        int voiceStatus=0;
                        //??????????????????
                        if (nowTime-locateVoiceTime>2000) {//?????????????????????2s????????????
                            if(wornStatus!=0){
                                wornStatusCount++;
                                if (wornStatusCount>3){
                                    voiceStatus=wornStatus;
                                    wornStatusCount=0;
                                }

                            }else if(poseStatus!=0){
                                voiceStatus = poseStatus;
                                poseStatus=0;
                            }else if(guidanceDirection!=1 ){ //???????????????

                                voiceStatus = rotateStatus;
                                if(targetIndex!=999 && rotateStatus==10){//?????????????????????????????????????????????????????????????????????
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

                            guidanceDirection = (rotateStatus==10) ? 1 :0 ; //??????????????????1,???????????????0 //????????????????????????????????????????????????
                            Log.d(TAG5, String.format("voiceStatus =%d",voiceStatus));
                            if(voiceStatus !=0 && voiceStatus!=99){
                                soundPool.play(soundMap.get(voiceStatus), 1, 1, 0, 0, 1f);
                                locateVoiceTime = nowTime;
                            }

                        }

                        //???????????????????????????????????????????????????
                        //??????????????????????????? ???????????????
                        if(countSeeDirectionSatus==0 &&guidanceDirection==1){
                            countSeeDirectionSatus=1;//????????????
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

        new CountDownTimer(1000, 10) {//????????????8????????? //????????????0.2???
            public void onTick(long millisUntilFinished) {
//                    String info_t1m = Long.toString(millisUntilFinished);
//                    Log.i("testCountDown", "method " + info_t1m);

//                mTextField.setText("seconds remaining: " + millisUntilFinished / 1000);
            }

            public void onFinish() {
                int resultPoint = 0; //??????????????????
                int targetPoint = 0; //????????????????????????

                Log.i("testCountDown", String.format("ResultsFPS=%d", countResultsFPS));
                Log.i("testCountDown", String.format("see frame pre sec=%d", yesResults));
                Log.i("testCountDown", "method Done!");

                //???????????????????????????
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



                //???????????????????????????????????????!

                if (Math.abs(arrayFrame[0] - arrayChoose[0]) > 0.2) {
                    //???????????????????????????0.2
                    if (arrayFrame[0] > arrayChoose[0]) {
                        resultPoint++;
                    } else {
                        targetPoint++;
                    }

                }

                if (Math.abs(arrayFrame[4] - arrayChoose[4]) > 0.2) {
                    //???????????????????????????0.2
                    if (arrayFrame[4] > arrayChoose[4]) {
                        resultPoint++;
                        if(targetItem==6||targetItem==8){resultPoint++;} //???????????????????????????????????????
                    } else {
                        targetPoint++;
                        if(targetItem==6||targetItem==8){resultPoint++;}//???????????????????????????????????????
                    }

                }

                if (resultPoint == targetPoint) {
                    //???????????????????????????????????? 0:0 or 1:1
                    //???????????????
                    if (arrayFrame[2] > arrayChoose[2]) {
                        resultPoint++;
                    } else {
                        targetPoint++;
                    }
                }

                if(resultPoint>targetPoint){
                    //??????????????????
                    arrayChoose[0] = arrayFrame[0] ; //confidence
                    arrayChoose[1] = arrayFrame[1] ; //Azi
                    arrayChoose[2] = arrayFrame[2] ; //Area
                    arrayChoose[3] = arrayFrame[3] ; //detected pre sec
                    arrayChoose[4] = arrayFrame[4] ;  //detected Rate pre sec (0~1)
                    recordAreaTime=nowTime;
                    if(dingStatus==0&&guidanceDirection!=1){
                        //??????????????????????????????????????????????????????
                        soundPool.play(soundMap.get(31), 1, 1, 0, 0, 1f);
                    }
                    dingStatus=1;
                }
                //???????????????????????????????????????
                //(?????????????????????????????????????????????90??????????????????85)
                if(arrayFrame[4]>0.9&&arrayFrame[0]>0.85){
                    aziTarget = arrayChoose[1] ; //?????????????????? //??????aziTarget??????999????????????????????????
                }
                countResultsFPS = 0;
                countFPS = 0;
                yesResults = 0;

            }


        }.start();
    }

    //???????????????????????????????????????????????????????????????
    private void triggeredCount() {

            new CountDownTimer(5000, 200) {//????????????5????????? //????????????0.2???
                int seeItem = 0;
                int nonseeItem = 0;
                int centerCount=0;
                public void onTick(long millisUntilFinished) {
                    if (centerStatus == 3) { //?????????????????????????????????????????????????????????????????????????????????

                        centerCount++;
                    }


                    if (guidanceDirection == 1 && centerCount<2 && millisUntilFinished>3000l) { //??????????????????????????? ?????????????????????????????????
                            //????????????????????????3???


                        if (targetIndex == 999) {
                            nonseeItem++;
                        } else {
                            seeItem++;
                        }
                    }

                    if (centerStatus != 0) {
                        //?????????????????????????????????
                        seeItem = 0;
                        nonseeItem = 0;
                    }

//                    String info_t1m = Long.toString(millisUntilFinished);
//                    Log.i("testCountDown", "method " + info_t1m);
//                mTextField.setText("seconds remaining: " + millisUntilFinished / 1000);
                }

                public void onFinish() {
                    //????????????????????????????????????
                    if((nonseeItem+seeItem)!=0 &&seeItem/(nonseeItem+seeItem) <0.8 &&guidanceDirection==1 && targetIndex==999 ){ //?????????????????????????????????????????? ?????????????????????
                        //?????????????????????>>??????????????????
                        centerStatus=32;
                        soundPool.play(soundMap.get(centerStatus), 1, 1, 0, 0, 1f);

                        //????????????
                        aziTarget=999f; //??????????????????
                        tv_aziTarger.setText(String.valueOf(aziTarget));
                        aziItem=999f;
                        maxTargerArea=0f;
                        arraySecCount=0;
                        arrayChoose = new float[]{0, 999f, 0, 0, 0, 0}; //?????????arrayChoose
                        Arrays.fill(arrayFrame, 0); //?????????arrayChoose
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
                        targetItem=resetItem; //??????????????????????????????

                    }
//                    Log.i("testCountDown", "method Done!");
                    countSeeDirectionSatus=0; //reset ????????????
                }

            }.start();
    }
    private void adjustDirection(float azimuth) {

        float turnRight,turnLeft;
        if(azimuth>aziTarget){//now>target

            turnRight = (360-azimuth)+(aziTarget-0);
            turnLeft = (azimuth-aziTarget);
            //???????????????????????????
            if(turnRight<turnLeft){
                if (turnRight<=23.5f){
                    Log.d(TAG3, "okAZI");
                    rotateStatus=10;
                }else if(turnRight>23.5f && turnRight<=45f){
                    Log.d(TAG3, "???????????????");
                    rotateStatus=15;
                }
                else if(turnRight>45f && turnRight<=90f){
                    Log.d(TAG3, "?????????");
                    rotateStatus=13;
                }else {
                    Log.d(TAG3, "?????????????????????");
                    rotateStatus=11;
                }
            }
            //???????????????????????????
            if(turnRight>turnLeft){
                if (turnLeft<=23.5f){
                    Log.d(TAG3, "okAZI");
                    rotateStatus=10;
                }else if(turnLeft>23.5f && turnLeft<=45f){
                    Log.d(TAG3, "???????????????");
                    rotateStatus=16;
                }
                else if(turnLeft>45f && turnLeft<=90f){
                    Log.d(TAG3, "?????????");
                    rotateStatus=14;
                }else {
                    Log.d(TAG3, "????????????????????? ");
                    rotateStatus=12;
                }
            }


        }
        if(aziTarget>azimuth){//now>target

            turnRight = (aziTarget-azimuth);
            turnLeft = (azimuth-0)+(360-aziTarget);

            //???????????????????????????
            if(turnRight<turnLeft){
                if (turnRight<=23.5f){
                    Log.d(TAG3, "okAZI");
                    rotateStatus=10;
                }else if(turnRight>23.5f && turnRight<=45f){
                    Log.d(TAG3, "???????????????");
                    rotateStatus=15;
                }
                else if(turnRight>45f && turnRight<=90f){
                    Log.d(TAG3, "?????????");
                    rotateStatus=13;
                }else {
                    Log.d(TAG3, "?????????????????????");
                    rotateStatus=11;
                }
            }
            //???????????????????????????
            if(turnRight>turnLeft){
                if (turnLeft<=23.5f){
                    Log.d(TAG3, "okAZI");
                    rotateStatus=10;
                }else if(turnLeft>23.5f && turnLeft<=45f){
                    Log.d(TAG3, "???????????????");
                    rotateStatus=16;
                }
                else if(turnLeft>45f && turnLeft<=90f){
                    Log.d(TAG3, "?????????");
                    rotateStatus=14;
                }else {
                    Log.d(TAG3, "????????????????????? ");
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
            Log.d(TAG3, "?????????????????????");
            poseStatus=27;
        }
        else if(pitch<=-80){
            Log.d(TAG3, "?????????????????????????????????");
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
                            azi=azimuth; //?????????????????????AZI ?????????????????????
                        }
                        if(aziTarget != 999f){

                            adjustDirection(azi);
                            //????????????????????????????????????????????????????????????????????????????????????
                            if(in_status==1 && Math.abs(roll)>15){
                                rotateStatus=0;
                            }
                        }
                        if(in_status!=1){adjustUserPose(pitch ,  roll);} //?????????????????????????????????


                        adjustSotwLabel(azi);

                    }
                });
            }
        };
    }

}

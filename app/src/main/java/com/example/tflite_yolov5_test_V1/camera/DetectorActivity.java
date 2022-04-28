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

public class DetectorActivity extends CameraActivity implements ImageReader.OnImageAvailableListener {

    private  static final String TAG = "cameraINFO";
    private  static final String TAG2 = "testResult";
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

    private long lastProcessingTimeMs = 0;
    private long locateVoiceTime = 0;
    private long centerVoiceTime = 0;
    private Bitmap rgbFrameBitmap = null;
    private Bitmap croppedBitmap = null;
    private Bitmap cropCopyBitmap = null;

    private boolean computingDetection = false;

    private long timestamp = 0;

    private Matrix frameToCropTransform;
    private Matrix cropToFrameTransform;

    private MultiBoxTracker tracker;

    private BorderedText borderedText;
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
        TextView modeTextView = (TextView)findViewById(R.id.modeTextView);
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

        //設置音校屬性
        AudioAttributes attr = new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE) //設置音效使用場景
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build(); // 設置音樂類型
        //soundPool Setting
        soundPool = new SoundPool.Builder().setAudioAttributes(attr) // 將屬給予音效池
                .setMaxStreams(20) // 設置最多可以容納個音效數量，我先估計20個拉
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
                        final long nowTime = SystemClock.uptimeMillis();
                        float fps = (float)1000 / (float)(nowTime - lastProcessingTimeMs);
                        lastProcessingTimeMs = nowTime;


                        //ImageUtils.saveBitmap(croppedBitmap);
                        detector.setInput(croppedBitmap);
                        final List<TfliteRunner.Recognition> results = detector.runInference();

                        cropCopyBitmap = Bitmap.createBitmap(croppedBitmap);
                        final Canvas canvas = new Canvas(cropCopyBitmap);
                        final Paint paint = new Paint();
                        paint.setColor(Color.YELLOW);
                        paint.setStyle(Paint.Style.STROKE);
                        paint.setStrokeWidth(2.0f);
                        final Paint paintBound = new Paint();
                        paintBound.setColor(Color.GREEN);
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
                        canvas.drawRect(trackedPosD, paintBound);


                        for (final TfliteRunner.Recognition result : results) {
                            final RectF location = result.getLocation();
                            final String title = result.getTitle();
                            final Integer class_id = result.getClass_idx();
                            //canvas.drawRect(location, paint);
                            //有看到指定物件
                            if (class_id == 3){ //assign id=3 "cup"
                                canvas.drawRect(location, paint);
                                canvas.drawPoint(location.centerX(),location.centerY(), paint);
                            Log.d(TAG2, "results  location"+ location+"title>"+title+" id:"+class_id);
//                                if(location.centerX()<trackedPosD.centerX()&& location.centerY()<trackedPosD.centerY()){
                                //判斷物件是否在中心
                                if(location.centerX()>trackedPosD.left && location.centerX()<trackedPosD.right  && location.centerY()>trackedPosD.top && location.centerY()<trackedPosD.bottom){
                                    Log.d(TAG2, "in!!!!.............");
                                    if(nowTime-centerVoiceTime > 5000){ //偵測間隔時間差5s才放聲音
                                        centerVoiceTime = nowTime;
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

}

package com.zzwtec.facedlibopencv.activity;

import android.content.Intent;
import android.graphics.Bitmap;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import com.zzwtec.facedlibopencv.R;
import com.zzwtec.facedlibopencv.face.ArcFace;
import com.zzwtec.facedlibopencv.jni.Face;
import com.zzwtec.facedlibopencv.util.CameraUtil;
import com.zzwtec.facedlibopencv.util.Constants;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.JavaCameraView;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Mat;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class VideoActivity extends AppCompatActivity implements CameraBridgeViewBase.CvCameraViewListener2 {

    private static String TAG = "VideoActivity";

    private Button button;
    private Camera mCamera;

    private Boolean initflag = false;
    private Mat mRgba;
    private Mat mRgb;
    private Mat mGray;
    private Mat mBgr;
    private Mat mDisplay;

    private int mWidth, mHeight, type = 1;

    private Bitmap mCacheBitmap;
    private Handler mHandler;
    private ImageView imageView;

    private volatile boolean check=false;

    private ArcFace mArcFace;

    private ExecutorService singleThreadExecutor = Executors.newSingleThreadExecutor();

    private JavaCameraView javaCameraView;
    private BaseLoaderCallback baseLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS: {
                    javaCameraView.enableView();
                }
                break;
                default:
                    super.onManagerConnected(status);
                    break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //保持屏幕常亮
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_video);
        mHandler =new Handler();
        imageView = (ImageView) findViewById(R.id.imageView);
        javaCameraView = (JavaCameraView) findViewById(R.id.javaCameraView);
        javaCameraView.setVisibility(SurfaceView.VISIBLE);
        int cameraDisplayRotation = CameraUtil.getCameraDisplayRotation(VideoActivity.this);
        javaCameraView.setCameraDisplayRotation(cameraDisplayRotation);
        if(MyApplication.getCameraInfoMap().get(MyApplication.getCurrentCameraId()) == Camera.CameraInfo.CAMERA_FACING_FRONT){
            javaCameraView.setCameraIndex(CameraBridgeViewBase.CAMERA_ID_FRONT); // 设置打开前置摄像头
        }else{
            javaCameraView.setCameraIndex(CameraBridgeViewBase.CAMERA_ID_BACK); // 设置打开后置摄像头
        }

        Camera camera = Camera.open(MyApplication.getCurrentCameraId());
        List<Camera.Size> rawSupportedSizes = camera.getParameters().getSupportedPreviewSizes();
        int maxW = 0;
        int maxH = 0;
        for(Camera.Size item : rawSupportedSizes){
            Log.i(TAG,"supportedSize width:"+item.width+" height:"+item.height);
            if(item.width > maxW){
                maxW = item.width;
                maxH = item.height;
            }
        }
        camera.release();
        javaCameraView.getLayoutParams().width=maxW;
        javaCameraView.getLayoutParams().height=maxH;

        javaCameraView.setCvCameraViewListener(this);
        javaCameraView.setClickable(true);
        javaCameraView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mCamera = javaCameraView.getCamera();
                if (mCamera!=null) mCamera.autoFocus(null);
            }
        });

        button = (Button)findViewById(R.id.button);
        button.setVisibility(View.VISIBLE);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                MyApplication.switchCameraId();
                if(MyApplication.getCameraInfoMap().get(MyApplication.getCurrentCameraId()) == Camera.CameraInfo.CAMERA_FACING_FRONT){
                    javaCameraView.setCameraIndex(CameraBridgeViewBase.CAMERA_ID_FRONT); // 设置打开前置摄像头
                }else{
                    javaCameraView.setCameraIndex(CameraBridgeViewBase.CAMERA_ID_BACK); // 设置打开后置摄像头
                }
                javaCameraView.changeCamera();
            }
        });

        //取得从上一个Activity当中传递过来的Intent对象
        Intent intent = getIntent();
        //从Intent当中根据key取得value
        if (intent != null) {
            int tempType = intent.getIntExtra("type",1);
            if(type != tempType){
                type = tempType;
                initflag = false;
            }
        }

        singleThreadExecutor.execute(new Runnable() {
            @Override
            public void run() {
                if(!initflag){
                    if(type == 1){ // 人脸特征标记
                        // FpsMeter: 5.20
//                        Face.initModel(Constants.getFaceShape5ModelPath(),0);

                        // FpsMeter: 4.7
                        Face.initModel(Constants.getFaceShape68ModelPath(),1);
                    }else if(type == 3){ // 人脸检测

                    }else if(type == 4){ // 人脸检测 通过dnn
                        Face.initModel(Constants.getHumanFaceModelPath(),2);
                    }else if(type == 2 || type == 5){ // 同步人脸识别 异步人脸识别
//                        Face.initModel(Constants.getFaceShape5ModelPath(),0);
                        Face.initModel(Constants.getFaceShape68ModelPath(),1);

                        Face.initModel(Constants.getFaceRecognitionV1ModelPath(),3);
                        Face.initFaceDescriptors(Constants.getFacePicDirectoryPath());
                        Face.getMaxFace(1);
                    }else if(type == 6 || type == 7){ // 虹软视频人脸同步识别  虹软视频人脸异步识别
                        if(mArcFace==null){
                            mArcFace = new ArcFace(16,1);
                        }
                        mArcFace.initDB(Constants.getFacePicDirectoryPath());
                    }

                    initflag = true;
                }
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if(mArcFace != null){
            mArcFace.destroy();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (javaCameraView != null)
            javaCameraView.disableView();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!OpenCVLoader.initDebug()) {
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION, this, baseLoaderCallback);
        } else {
            baseLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }

    @Override
    public void onCameraViewStarted(int width, int height) {
        imageView.getLayoutParams().width = width/6 ;
        imageView.getLayoutParams().height = height/6 ;
        mWidth = width;
        mHeight = height;
        mRgba = new Mat();
        mGray = new Mat();
        mBgr = new Mat();
        mRgb = new Mat();
        mDisplay = new Mat();
    }

    @Override
    public void onCameraViewStopped() {
        mRgba.release();
        mGray.release();
        mBgr.release();
        mRgb.release();
        mDisplay.release();
    }

    /**
     * 这个方法在子类有有效的对象时需要被调用，并且要把它传递给外部客户（通过回调），然后显示在屏幕上。
     * @param inputFrame
     * @return
     */
    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        Log.d(TAG, "VideoActivity onCameraFrame");

        if(initflag){
            if(type == 1){ // 人脸特征标记
                // FpsMeter: 4.89
                mGray = inputFrame.gray();
                mRgba = inputFrame.rgba();
                mDisplay = mRgba;
                Face.landMarks1(mGray.getNativeObjAddr(),3,mDisplay.getNativeObjAddr());
            }else if(type == 3){ // 人脸检测
                mGray = inputFrame.gray();
                mRgb = inputFrame.rgb();
                mDisplay = mRgb;
                Face.faceDetector(mGray.getNativeObjAddr(),3,mDisplay.getNativeObjAddr());
            }else if(type == 4){ // 人脸检测 通过dnn
                mGray = inputFrame.gray();
                mRgb = inputFrame.rgb();
                mDisplay = mRgb;
                Face.faceDetectorByDNN(mGray.getNativeObjAddr(),3,mDisplay.getNativeObjAddr());
            }else if(type == 2 ){ // 同步人脸识别
                mGray = inputFrame.gray();
                mRgb = inputFrame.rgb();
                mDisplay = mRgb;
                Face.faceRecognition(mGray.getNativeObjAddr(),3,mDisplay.getNativeObjAddr(),Constants.getFacePicDirectoryPath());
            }else if(type == 5){ //异步人脸识别
                mGray = inputFrame.gray();
                mRgb = inputFrame.rgb();
                mDisplay = mRgb;

                final Mat gray = mGray;
                final Mat display = mDisplay;
                if(!check){
                    check = true;
                    singleThreadExecutor.execute(
                            new Runnable() {
                                @Override
                                public void run() {
                                    int re = Face.faceRecognition(gray.getNativeObjAddr(),3,display.getNativeObjAddr(),Constants.getFacePicDirectoryPath());
                                    if(re>0){
                                        mHandler.post(new Runnable() {
                                            @Override
                                            public void run() {
                                                Toast.makeText(getApplicationContext(),"找到匹配的人",Toast.LENGTH_LONG);
                                                Bitmap srcBitmap = Bitmap.createBitmap(display.width(), display.height(), Bitmap.Config.ARGB_8888);
                                                Utils.matToBitmap(display, srcBitmap);
                                                imageView.setImageBitmap(srcBitmap);
                                            }
                                        });
                                    }
                                    check = false;
                                }
                            }
                    );
                }
            }else if(type == 6){ // 虹软视频人脸同步识别
                mRgb = inputFrame.rgb();
                Bitmap srcBitmap = Bitmap.createBitmap(mRgb.width(), mRgb.height(), Bitmap.Config.ARGB_8888);
                final Bitmap displayBitmap = Bitmap.createBitmap(srcBitmap.getWidth(), srcBitmap.getHeight(), srcBitmap.getConfig()); //建立一个空的BItMap
                Utils.matToBitmap(mRgb,srcBitmap);
                final float score = mArcFace.facerecognitionByDB(srcBitmap,displayBitmap);
                if(score==0){
                    mDisplay = mRgb;
                }else{
                    Utils.bitmapToMat(displayBitmap,mDisplay);
                    if(score > 0.5f){
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(getApplicationContext(),"找到匹配的人",Toast.LENGTH_LONG);
                                imageView.setImageBitmap(displayBitmap);
                            }
                        });
                    }
                }
            }else if(type == 7){ // 虹软视频人脸异步识别
                mRgb = inputFrame.rgb();
                mDisplay = mRgb;
                if(!check){
                    check = true;
                    singleThreadExecutor.execute(
                            new Runnable() {
                                @Override
                                public void run() {
                                    final Bitmap srcBitmap = Bitmap.createBitmap(mRgb.width(), mRgb.height(), Bitmap.Config.ARGB_8888);
                                    final Bitmap displayBitmap = Bitmap.createBitmap(srcBitmap.getWidth(), srcBitmap.getHeight(), srcBitmap.getConfig()); //建立一个空的BItMap
                                    Utils.matToBitmap(mRgb,srcBitmap);
                                    final float score = mArcFace.facerecognitionByDB(srcBitmap,displayBitmap);
                                    if(score > 0){
                                        mHandler.post(new Runnable() {
                                            @Override
                                            public void run() {
                                                Toast.makeText(getApplicationContext(),"找到匹配的人",Toast.LENGTH_LONG);
                                                imageView.setImageBitmap(displayBitmap);
                                            }
                                        });
                                    }
                                    check = false;
                                }
                            }
                    );
                }
            }
            return mDisplay;

        }else{
            mDisplay = inputFrame.rgba();
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Bitmap srcBitmap = Bitmap.createBitmap(mDisplay.width(), mDisplay.height(), Bitmap.Config.ARGB_8888);
                    Utils.matToBitmap(mDisplay,srcBitmap);
                    imageView.setImageBitmap(srcBitmap);
                }
            });
            return mDisplay;
        }
    }

    // Used to load the 'native-lib' library on application startup.
    static {
        System.loadLibrary("opencv_java3");
        System.loadLibrary("native-lib");
    }
}

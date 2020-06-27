package com.abhinav.Camera;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.util.Range;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class VideoActivity extends AppCompatActivity implements SurfaceHolder.Callback, Handler.Callback {

    private Button switch_mode;
    private TextView status;
    private ImageButton flip;
    private final static int CAMERA_PERMISSION_CODE = 0;
    private static String CAMERA_ID;
    private final static int MSG_SURFACE_CREATED = 0;
    private final static int MSG_CAMERA_OPENED = 1;
    private static int id=0;
    private ImageButton mButton;
    private boolean mIsRecordingVideo;
    private Size mVideoSize;

    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    static {
        ORIENTATIONS.append(Surface.ROTATION_0,90);
        ORIENTATIONS.append(Surface.ROTATION_90,0);
        ORIENTATIONS.append(Surface.ROTATION_180,270);
        ORIENTATIONS.append(Surface.ROTATION_270,180);
    }
    private Handler mHandler = new Handler(this);
    private SurfaceView mSurfaceView;
    private SurfaceHolder mSurfaceHolder;
    private Surface mCameraSurface;
    private boolean mIsCameraSurfaceCreated;
    private CameraManager mCameraManager;
    private CameraDevice mCameraDevice;
    private CameraDevice.StateCallback mCameraStateCallBack;
    private CameraCaptureSession.StateCallback mCameraCaptureSessionStateCallback;
    private CameraCaptureSession mCameraCaptureSession;

    private MediaRecorder mMediaRecorder;
    private static Size chooseVideoSize(Size[] choices) {
        for (Size size : choices) {
            if (size.getWidth() == size.getHeight() * 16 / 9 && size.getWidth() <= 1080) {
                return size;
            }
        }
        Log.e("TAG", "Couldn't find any suitable video size");
        return choices[0];
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d("**","oncreate");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video);
        switch_mode=findViewById(R.id.switch_mode);
        mSurfaceView = findViewById(R.id.surface_view);
        mSurfaceHolder = mSurfaceView.getHolder();
        flip =findViewById(R.id.flip);
        status=findViewById(R.id.status);
        getSupportActionBar().hide();
        mSurfaceHolder.addCallback(this);
        mButton = findViewById(R.id.button);
        mButton.setOnClickListener(new View.OnClickListener() { // step 1: define and display recording button and manage UI
            @Override
            public void onClick(View v) {
                if (!mIsRecordingVideo) {
                    startRecording();
                } else {
                    stopRecording();
                }
            }
        });
        flip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mCameraDevice.close();
                mCameraDevice=null;
                mIsCameraSurfaceCreated=true;
                if(id ==0) {
                    Log.d("**","camera to 1");
                    id =1;

                    try {
                        cameraHandle();
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }

                }
                else{
                    Log.d("**","camera to 0");
                    id =0;

                    try {
                        cameraHandle();
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }

                }

            }
        });

        switch_mode.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.d("**","photo activity");
                mCameraCaptureSession.close();
                mCameraDevice.close();
                mCameraCaptureSession = null;
                mCameraDevice = null;
                Intent intent=new Intent(getApplicationContext(),PhotoActivity.class);
                startActivity(intent);
            }
        });
    }

    private void startRecording() {

        if (mCameraDevice != null) {

            closeCameraSession();

            List<Surface> surfaceList = new ArrayList<Surface>();
            try {
                setupMediaRecorder();
            } catch (IOException | CameraAccessException e) {
                e.printStackTrace();
            }

            final CaptureRequest.Builder recordingBuilder;
            try {
                recordingBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
                recordingBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, getRange());
                surfaceList.add(mCameraSurface);
                recordingBuilder.addTarget(mCameraSurface);

                surfaceList.add(mMediaRecorder.getSurface());
                recordingBuilder.addTarget(mMediaRecorder.getSurface());
                mCameraDevice.createCaptureSession(surfaceList, new CameraCaptureSession.StateCallback() {
                    @Override
                    public void onConfigured(@NonNull CameraCaptureSession session) {
                        mCameraCaptureSession = session;

                        try {
                            mCameraCaptureSession.setRepeatingRequest(recordingBuilder.build(), null, null);
                        } catch (CameraAccessException e) {
                            e.printStackTrace();
                        }

                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                mMediaRecorder.start();
                                mButton.setImageResource(R.drawable.stop);
                                status.setVisibility(View.VISIBLE);
                                mIsRecordingVideo = true;
                            }
                        });
                    }

                    @Override
                    public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                    }
                }, mHandler);

            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }
    }

    private void stopRecording() {
        closeCameraSession();
        configureCamera();
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mMediaRecorder.stop();
                mMediaRecorder.reset();
                mButton.setImageResource(R.drawable.record);
                status.setVisibility(View.INVISIBLE);
                mIsRecordingVideo = false;
            }
        });
    }



    private void setupMediaRecorder() throws IOException, CameraAccessException {
        Log.d("**","media setup");
        if (mMediaRecorder == null) {
            mMediaRecorder = new MediaRecorder();
        }

        mMediaRecorder.setOutputFile(getOutputFile().getAbsolutePath());
        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        //mMediaRecorder.setVideoSize();
        mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        CameraCharacteristics characteristics = mCameraManager.getCameraCharacteristics(mCameraDevice.getId());
        int sensorOrientation =  characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        int surfaceRotation=ORIENTATIONS.get(rotation);
        int jpegOrientation =
                (surfaceRotation + sensorOrientation + 270) % 360;
        CamcorderProfile profile = CamcorderProfile.get(CamcorderProfile.QUALITY_720P);
        mMediaRecorder.setVideoFrameRate(profile.videoFrameRate);
        mMediaRecorder.setVideoSize(profile.videoFrameWidth, profile.videoFrameHeight);
        mMediaRecorder.setVideoEncodingBitRate(profile.videoBitRate);
        mMediaRecorder.setVideoSize(mVideoSize.getWidth(), mVideoSize.getHeight());
        mMediaRecorder.setAudioEncodingBitRate(profile.audioBitRate);
        mMediaRecorder.setAudioSamplingRate(profile.audioSampleRate);
        mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        mMediaRecorder.setOrientationHint(jpegOrientation);

        mMediaRecorder.prepare();
    }

    private File getOutputFile() {
        File dir = new File(Environment.getExternalStorageDirectory().toString(), "MyVideos");
        if (!dir.exists()) {
            dir.mkdir();
        }
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        File imageFile = new File (dir.getPath() + File.separator + "VID_"+timeStamp+".mp4");
        return imageFile;
    }


    @Override
    public void surfaceCreated(SurfaceHolder holdConfigureCamera) {
        Log.d("**","surface created");
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        Log.d("**","surface changed");
        mCameraSurface = holder.getSurface();
        mHandler.sendEmptyMessage(MSG_SURFACE_CREATED);
        mIsCameraSurfaceCreated = true;
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        mIsCameraSurfaceCreated = false;
        Log.d("**","surface destroyed");
    }


    @Override
    protected void onStart() {
        super.onStart();
        requestPermissions(new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.RECORD_AUDIO}, CAMERA_PERMISSION_CODE);
    }


    @SuppressLint("MissingPermission")
    private void cameraHandle() throws CameraAccessException {
        Log.d("**","handle camera");

        mCameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        CAMERA_ID=mCameraManager.getCameraIdList()[id];
        CameraCharacteristics characteristics = mCameraManager.getCameraCharacteristics(CAMERA_ID);
        StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        mVideoSize = chooseVideoSize(map.getOutputSizes(MediaRecorder.class));

        mCameraStateCallBack = new CameraDevice.StateCallback() {
            @Override
            public void onOpened(@NonNull CameraDevice camera) {
                Log.d("**","on opened");
                mCameraDevice = camera;
                mHandler.sendEmptyMessage(MSG_CAMERA_OPENED);
            }

            @Override
            public void onDisconnected(@NonNull CameraDevice camera){

            }

            @Override
            public void onError(@NonNull CameraDevice camera, int error) {
                Toast.makeText(getApplicationContext(),"Some Error Occurred",Toast.LENGTH_SHORT).show();
            }
        };

        try {
            mCameraManager.openCamera(CAMERA_ID, mCameraStateCallBack, new Handler());
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean handleMessage(@NonNull Message msg) {
        switch (msg.what) {
            case MSG_SURFACE_CREATED:
            case MSG_CAMERA_OPENED:
                if (mIsCameraSurfaceCreated && (mCameraDevice != null)) {
                    mIsCameraSurfaceCreated = false;
                    configureCamera();
                }
        }
        return true;
    }
    private void configureCamera() {

        List<Surface> surfaceList = new ArrayList<Surface>();
        surfaceList.add(mCameraSurface);

        mCameraCaptureSessionStateCallback = new CameraCaptureSession.StateCallback() {
            @Override
            public void onConfigured(@NonNull CameraCaptureSession session) {
                mCameraCaptureSession = session;

                try {
                    CaptureRequest.Builder previewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                    previewRequestBuilder.addTarget(mCameraSurface);
                    previewRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
                    previewRequestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,getRange());

                    mCameraCaptureSession.setRepeatingRequest(previewRequestBuilder.build(), null, null);
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }

            }

            @Override
            public void onConfigureFailed(@NonNull CameraCaptureSession session) {
            }
        };

        try {
            mCameraDevice.createCaptureSession(surfaceList, mCameraCaptureSessionStateCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

    }
    private Range<Integer> getRange() {
        CameraCharacteristics chars = null;
        try {
            chars = mCameraManager.getCameraCharacteristics(CAMERA_ID);
            Range<Integer>[] ranges = chars.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);
            Range<Integer> result = null;
            for (Range<Integer> range : ranges) {
                int upper = range.getUpper();
                // 10 - min range upper for my needs
                if (upper >= 10) {
                    if (result == null || upper < result.getUpper().intValue()) {
                        result = range;
                    }
                }
            }
            if (result == null) {
                result = ranges[0];
            }
            return result;
        } catch (CameraAccessException e) {
            e.printStackTrace();
            return null;
        }
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                try {
                    cameraHandle();
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }
            }
            else{
                requestPermissions(new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE}, CAMERA_PERMISSION_CODE);

            }
        }

    }

    private void closeCameraSession() {
        if (mCameraCaptureSession != null) {
            mCameraCaptureSession.close();
            mCameraCaptureSession = null;
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        closeCameraSession();
        mMediaRecorder = null;
        if(mCameraDevice!=null)
        mCameraDevice.close();
        mCameraDevice = null;
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        id =0;
        if(mCameraCaptureSession!=null)
            mCameraCaptureSession.close();
        if(mCameraDevice!=null)
            mCameraDevice.close();
        mCameraCaptureSession = null;
        mCameraDevice = null;

    }
}

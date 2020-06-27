package com.abhinav.Camera;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.OutputConfiguration;
import android.media.Image;
import android.media.ImageReader;
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
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class PhotoActivity extends AppCompatActivity implements SurfaceHolder.Callback, Handler.Callback {

    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    private File file;
    private ImageButton flip;
    private Button switch_mode;


    static {
        ORIENTATIONS.append(Surface.ROTATION_0,90);
        ORIENTATIONS.append(Surface.ROTATION_90,0);
        ORIENTATIONS.append(Surface.ROTATION_180,270);
        ORIENTATIONS.append(Surface.ROTATION_270,180);
    }
    private final static int CAMERA_PERMISSION_CODE = 0;
    private static String CAMERA_ID;
    private static int i=0;
    private final static int MSG_SURFACE_CREATED = 0;
    private final static int MSG_CAMERA_OPENED = 1;
    private ImageButton mButton;
    private CameraManager mCameraManager;
    private CameraDevice mCameraDevice;
    private CameraDevice.StateCallback mCameraStateCallBack;
    private CameraCaptureSession mCameraCaptureSession;
    private ImageReader reader=null;
    private Handler mHandler = new Handler(this);
    private SurfaceView mSurfaceView;
    private SurfaceHolder mSurfaceHolder;
    private Surface mCameraSurface;
    private boolean mIsCameraSurfaceCreated;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d("**","oncreate");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_photo);
        getSupportActionBar().hide();


        mSurfaceView = findViewById(R.id.surface_view);
        mSurfaceHolder = mSurfaceView.getHolder();
        mSurfaceHolder.addCallback(this);


        mButton = findViewById(R.id.button);
        flip =findViewById(R.id.flip);
        switch_mode =findViewById(R.id.switch_mode);

        mButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setCapturedImage();
            }
        });
        flip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
              mCameraDevice.close();
              mCameraDevice=null;
              mIsCameraSurfaceCreated=true;
              if(i==0) {
                  Log.d("**","camera to 1");
                  i=1;
                  try {
                      cameraHandle();
                  } catch (CameraAccessException e) {
                      e.printStackTrace();
                  }
              }
              else{
                  Log.d("**","camera to 0");
                  i=0;
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
                Log.d("**","video activity");
                mCameraCaptureSession.close();
                mCameraDevice.close();
                mCameraCaptureSession = null;
                mCameraDevice = null;
                Intent intent=new Intent(getApplicationContext(),VideoActivity.class);
                startActivity(intent);
            }
        });

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
        Log.d("**","surface destroyed");
        mIsCameraSurfaceCreated = false;

    }


    @Override
    protected void onStart() {
        super.onStart();
        requestPermissions(new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE}, CAMERA_PERMISSION_CODE);
    }

    @SuppressLint("MissingPermission")
    private void cameraHandle() throws CameraAccessException {
        Log.d("**","handle camera");

        mCameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        CAMERA_ID=mCameraManager.getCameraIdList()[i];
        //CameraCharacteristics characteristics = mCameraManager.getCameraCharacteristics(CAMERA_ID);
        mCameraStateCallBack = new CameraDevice.StateCallback() {
            @Override
            public void onOpened(@NonNull CameraDevice camera) {
                Log.d("**","on opened");
                mCameraDevice = camera;
                mHandler.sendEmptyMessage(MSG_CAMERA_OPENED);
            }

            @Override
            public void onDisconnected(@NonNull CameraDevice camera) {

            }

            @Override
            public void onError(@NonNull CameraDevice camera, int error) {
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
                    try {
                        configureCamera();
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }
        }
        return true;
    }

    private void setCapturedImage() {
        if (mCameraDevice != null) {
            if (mCameraCaptureSession != null) {
                try {
                    CameraManager manager = (CameraManager)getSystemService(Context.CAMERA_SERVICE);

                    CameraCharacteristics characteristics = manager.getCameraCharacteristics(mCameraDevice.getId());
                    int sensorOrientation =  characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
                    CaptureRequest.Builder captureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
                    captureRequestBuilder.addTarget(reader.getSurface());
                    captureRequestBuilder.set(CaptureRequest.CONTROL_MODE,CameraMetadata.CONTROL_MODE_AUTO);
                    captureRequestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, getRange());
                    int rotation = getWindowManager().getDefaultDisplay().getRotation();
                    int surfaceRotation=ORIENTATIONS.get(rotation);
                    int jpegOrientation =
                            (surfaceRotation + sensorOrientation + 270) % 360;

                    captureRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION,jpegOrientation);
                    file=getOutputFile();

                    ImageReader.OnImageAvailableListener readerListener = new ImageReader.OnImageAvailableListener() {
                        @Override
                        public void onImageAvailable(ImageReader reader) {
                            Image image = null;

                            image  = reader.acquireLatestImage();
                            ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                            byte[] bytes = new byte[buffer.capacity()];
                            buffer.get(bytes);
                            try {
                                save(bytes);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }finally {
                                if(image!=null)
                                {
                                    image.close();
                                }
                            }

                        }
                    };

                    reader.setOnImageAvailableListener(readerListener,mHandler);
                    mCameraCaptureSession.capture(captureRequestBuilder.build(), mCaptureCallback, mHandler);
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }
            }
        }
    }
    private void save(byte[] bytes) throws IOException {
        OutputStream outputStream = null;

        outputStream = new FileOutputStream(file);

        outputStream.write(bytes);

        outputStream.close();

    }


    private File getOutputFile() {
        File dir = new File(Environment.getExternalStorageDirectory().toString(), "MyPictures");
        if (!dir.exists()) {
            dir.mkdir();
        }
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        File imageFile = new File (dir.getPath() + File.separator + "PIC_"+timeStamp+".jpg");

        return imageFile;
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
    CameraCaptureSession.CaptureCallback mCaptureCallback = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureStarted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, long timestamp, long frameNumber) {
            super.onCaptureStarted(session, request, timestamp, frameNumber);
        }

        @Override
        public void onCaptureProgressed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureResult partialResult) {
            super.onCaptureProgressed(session, request, partialResult);
        }

        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
            super.onCaptureCompleted(session, request, result);
            Toast.makeText(getApplicationContext(),"Saved as"+file.getAbsolutePath(),Toast.LENGTH_SHORT).show();
        }
    };

    private CameraCaptureSession.StateCallback mCameraCaptureSessionStateCallback = new CameraCaptureSession.StateCallback() {
        @Override
        public void onConfigured(@NonNull CameraCaptureSession session) {
            mCameraCaptureSession = session;
            Log.d("**","on conf");

            try {
                CaptureRequest.Builder previewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                previewRequestBuilder.addTarget(mCameraSurface);
                previewRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
                //previewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
                previewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH);
               previewRequestBuilder.set(CaptureRequest.FLASH_MODE,CameraMetadata.FLASH_MODE_TORCH);
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

    private void configureCamera() throws CameraAccessException {
        Log.d("**","conf camera");
        if(mCameraDevice == null)
        {
            return;

        }

        CameraManager manager = (CameraManager)getSystemService(Context.CAMERA_SERVICE);
        CameraCharacteristics characteristics = manager.getCameraCharacteristics(mCameraDevice.getId());
        Size[] jpegSizes = null;

        jpegSizes = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP).getOutputSizes(ImageFormat.JPEG);

        int width = 640;
        int height = 480;

        if(jpegSizes!=null && jpegSizes.length>0)
        {
            width = jpegSizes[0].getWidth();
            height = jpegSizes[0].getHeight();
        }

        reader = ImageReader.newInstance(width,height,ImageFormat.JPEG,5);
        List<OutputConfiguration> outputConfigurationList = new ArrayList<OutputConfiguration>();

        OutputConfiguration previewStream = new OutputConfiguration(mCameraSurface);
        OutputConfiguration captureStream = new OutputConfiguration(reader.getSurface());

        outputConfigurationList.add(previewStream);
        outputConfigurationList.add(captureStream);

        try {
            mCameraDevice.createCaptureSessionByOutputConfigurations(outputConfigurationList, mCameraCaptureSessionStateCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
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

    @Override
    protected void onStop() {
        Log.d("**","on Stop photo");
        super.onStop();
        i=0;
        if(mCameraCaptureSession!=null)
       mCameraCaptureSession.close();
        if(mCameraDevice!=null)
        mCameraDevice.close();
        mCameraCaptureSession = null;
        mCameraDevice = null;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        i=0;
        if(mCameraCaptureSession!=null)
            mCameraCaptureSession.close();
        if(mCameraDevice!=null)
            mCameraDevice.close();
        mCameraCaptureSession = null;
        mCameraDevice = null;

    }
}

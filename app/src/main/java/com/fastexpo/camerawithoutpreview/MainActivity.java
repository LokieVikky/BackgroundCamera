package com.fastexpo.camerawithoutpreview;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.AspectRatio;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.content.ContextCompat;
import androidx.databinding.DataBindingUtil;

import com.fastexpo.camerawithoutpreview.databinding.ActivityMainBinding;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutionException;

public class MainActivity extends AppCompatActivity {

    ActivityMainBinding mBiniding;
    ImageCapture imageCapture;
    private static final String TAG = "MainActivity";

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mBiniding = DataBindingUtil.setContentView(MainActivity.this, R.layout.activity_main);

        requestPermissions(new String[]{Manifest.permission.CAMERA}, 12457);

        mBiniding.btnCapture.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                captureImage();
            }
        });


    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (grantResults.length > 0) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initializeCamera();
            }
        }

    }

    void initializeCamera() {

        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(new Runnable() {
            @Override
            public void run() {
                ProcessCameraProvider cameraProvider;
                try {
                    cameraProvider = cameraProviderFuture.get();
                    imageCapture = new ImageCapture.Builder()
                            .setFlashMode(ImageCapture.FLASH_MODE_OFF)
                            .setTargetAspectRatio(AspectRatio.RATIO_16_9)
                            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                            .build();
                    OrientationEventListener orientationEventListener = new OrientationEventListener(MainActivity.this) {
                        @Override
                        public void onOrientationChanged(int orientation) {
                            int rotation;
                            if (orientation >= 45 && orientation < 135) {
                                rotation = Surface.ROTATION_270;
                            } else if (orientation >= 135 && orientation < 225) {
                                rotation = Surface.ROTATION_180;
                            } else if (orientation >= 225 && orientation < 315) {
                                rotation = Surface.ROTATION_90;
                            } else {
                                rotation = Surface.ROTATION_0;
                            }

                            imageCapture.setTargetRotation(rotation);
                        }
                    };
                    orientationEventListener.enable();
                    cameraProvider.bindToLifecycle(MainActivity.this, CameraSelector.DEFAULT_FRONT_CAMERA, imageCapture);
                } catch (ExecutionException | InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }, ContextCompat.getMainExecutor(MainActivity.this));
    }


    void captureImage() {
        if (imageCapture == null) {
            return;
        }
        // For saving in storage not used now;
        ImageCapture.OutputFileOptions outputFileOptions =
                new ImageCapture.OutputFileOptions.Builder(new File("/")).build();
        imageCapture.takePicture(ContextCompat.getMainExecutor(MainActivity.this),
                new ImageCapture.OnImageCapturedCallback() {
                    @Override
                    public void onCaptureSuccess(@NonNull ImageProxy image) {
                        super.onCaptureSuccess(image);
                        ImageProxy.PlaneProxy planeProxy = image.getPlanes()[0];
                        ByteBuffer buffer = planeProxy.getBuffer();
                        byte[] bytes = new byte[buffer.remaining()];
                        buffer.get(bytes);
                        Bitmap bMap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                        Log.d(TAG, "onCaptureSuccess: ");
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        super.onError(exception);
                    }
                });
    }

}
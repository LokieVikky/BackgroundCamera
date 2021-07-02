package com.fastexpo.camerawithoutpreview;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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

import com.android.volley.AuthFailureError;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.VolleyLog;
import com.android.volley.toolbox.HttpHeaderParser;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.fastexpo.camerawithoutpreview.databinding.ActivityMainBinding;
import com.google.common.util.concurrent.ListenableFuture;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutionException;

public class MainActivity extends AppCompatActivity {

    ActivityMainBinding mBiniding;
    ImageCapture imageCapture;
    private static final String TAG = "MainActivity";

    // Server URL to POST Image
    String baseURL = "";

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
                        String base64Image = encodeTobase64(bMap);
                        image.close();
                        // Call API HERE
                        postImageToServer(base64Image);
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        super.onError(exception);
                    }
                });
    }

    public static String encodeTobase64(Bitmap image) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        image.compress(Bitmap.CompressFormat.PNG, 100, baos);
        byte[] b = baos.toByteArray();
        return Base64.encodeToString(b, Base64.DEFAULT);
    }

    void postImageToServer(String base64Image){
        try {
            JSONObject object = new JSONObject();
            object.put("ImageBase64",base64Image);
            httpRequest(MainActivity.this, object, Request.Method.POST, new VolleyCallback() {
                @Override
                public void OnSuccess(String object) {
                    Log.d(TAG, "OnSuccess: Image Uploaded");
                }

                @Override
                public void OnFailure(VolleyError error) {
                    Log.d(TAG, "OnSuccess: Unable to upload Image");
                }
            },10000);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public void httpRequest(Context mContext, @Nullable JSONObject message, final int method,
                            final VolleyCallback callBack, int timeOut) throws Exception {
        if (mContext == null) {
            throw new Exception("Null Context");
        }
        if (callBack == null) {
            throw new Exception("Null CallBack");
        }
        RequestQueue requestQueue = Volley.newRequestQueue(mContext);
        //String URL = Base_url + apiType;
        Response.Listener<String> responseListener = new Response.Listener<String>() {
            @Override
            public void onResponse(String response) {
                callBack.OnSuccess(response);
            }
        };

        Response.ErrorListener volleyErrorListener = new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                callBack.OnFailure(error);
            }
        };
        StringRequest stringRequest = new StringRequest(method, baseURL, responseListener, volleyErrorListener) {

            @Override
            public byte[] getBody() throws AuthFailureError {
                try {
                    return message == null ? null : message.toString().getBytes("utf-8");
                } catch (UnsupportedEncodingException uee) {
                    VolleyLog.wtf("Unsupported Encoding while trying to get the bytes of %s using %s", message.toString(), "utf-8");
                    return null;
                }
            }

            @Override
            protected Response<String> parseNetworkResponse(NetworkResponse response) {
                String responseString = new String(response.data);
                return Response.success(responseString, HttpHeaderParser.parseCacheHeaders(response));
            }
        };
        stringRequest.setRetryPolicy(new DefaultRetryPolicy(timeOut, 1, 1.0f));
        requestQueue.add(stringRequest);
    }

    public interface VolleyCallback {
        public void OnSuccess(String object);

        public void OnFailure(VolleyError error);
    }


}
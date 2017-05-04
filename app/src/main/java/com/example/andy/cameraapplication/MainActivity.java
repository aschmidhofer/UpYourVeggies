package com.example.andy.cameraapplication;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.drawable.Drawable;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.IntRange;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.constraint.ConstraintLayout;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.graphics.SurfaceTexture;
import android.view.Window;
import android.widget.ImageView;

import java.util.Arrays;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "UpYourVeggies";
    private static final int REQUEST_CAMERA_PERMISSION = 200;
    private static final int BITMAP_READY = 42;
    private static final int UPDATE_VIEW = 45;
    private TextureView previewiew;
    private ImageView overlay;
    private String cameraId;
    protected CameraDevice cameraDevice;
    protected CameraCaptureSession cameraCaptureSessions;
    protected CaptureRequest captureRequest;
    protected CaptureRequest.Builder captureRequestBuilder;
    private Size imageDimension;
    private Handler mBackgroundHandler;
    private Handler imageUpdateHandler = new Handler(Looper.getMainLooper()){

        @Override
        public void handleMessage(Message inputMessage) {
            if(inputMessage.what==BITMAP_READY){
                final int displaytime = 1000*10;
                ImageProcessor ipr = (ImageProcessor) inputMessage.obj;
                overlay.setImageBitmap(ipr.getBitmap());
                overlay.setAlpha(1.0f);
                Message m = imageUpdateHandler.obtainMessage(UPDATE_VIEW);
                imageUpdateHandler.sendMessageDelayed(m, displaytime);
                Snackbar.make(ipr.getView(), ipr.getMessageText(), Snackbar.LENGTH_LONG).setDuration(displaytime)
                        .setAction("Action", null).show();
            } else if(inputMessage.what==UPDATE_VIEW){
                createOverlay();
            }
        }

    };

    class ImageProcessor implements Runnable{
        private Message message;
        private View view;
        private String result;
        private Bitmap b;
        public void setMessage(Message m){message =m;}
        public void setView(View v){view =v;}
        public View getView(){return view;}
        public Bitmap getBitmap(){return b;}
        public String getMessageText(){return result;}
        @Override
        public void run(){

            if(null == previewiew) return;
            b = previewiew.getBitmap();
            int w = b.getWidth();
            int h = b.getHeight();
            int cx = w/2;
            int cy = h/2;
            int radius = Math.min(cx,cy);
            int rsq = radius*radius;

            // THIS IS SUPER INEFFICIENT
            // will be replaced in later version
            int gmin = 10;
            int greens = 0;
            int total = 0;
            for(int i = 0; i<w; i++){
                for (int j = 0; j<h; j++) {
                    int color = b.getPixel(i, j);
                    int r2y = (j - cy) * (j - cy);
                    int r2 = r2y + (i - cx) * (i - cx);
                    int green = Color.green(color);
                    int red = Color.red(color);
                    int blue = Color.blue(color);
                    if (r2 < rsq) { // inner cirlce
                        total++;

                        // Green? Min brightness and minimum gap (1/16 = 6.25%) of G above B and R
                        int ggap = green -  green/16;
                        if (green > gmin && ggap > red && ggap > blue){
                            greens++;
                        } else {
                            int gray = (red*77 + green*151 + blue*28)/256;
                            b.setPixel(i, j, Color.rgb(gray, gray, gray));
                        }
                    } else {
                        int darkgray = (red*77 + green*151 + blue*28)/256/2;
                        b.setPixel(i, j, Color.rgb(darkgray, darkgray, darkgray));
                    }
                }
            }

            int greenpercent = (int)(100*(greens*1.0f/total));
            result = greenpercent+"% of pixels are green";

            if(message!=null){
                imageUpdateHandler.sendMessage(message);
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ConstraintLayout lo = (ConstraintLayout) findViewById(R.id.layoutmain);
        overlay = (ImageView) findViewById(R.id.overlayview);

        final FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                ImageProcessor ipr = new ImageProcessor();
                Message m = imageUpdateHandler.obtainMessage(BITMAP_READY,ipr);
                ipr.setMessage(m);
                ipr.setView(view);
                Thread t = new Thread(ipr);
                t.start(); // start background thread

                Snackbar.make(view, "Please wait...", Snackbar.LENGTH_INDEFINITE)
                        .setAction("Action", null).show();
            }
        });

        previewiew = (TextureView) findViewById(R.id.camerapreview);
        assert previewiew != null;
        previewiew.setSurfaceTextureListener(previewListener);

    }

    private TextureView.SurfaceTextureListener previewListener = new TextureView.SurfaceTextureListener(){
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            //open your camera here
            openCamera();
        }
        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
            // ignore for now
        }
        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return false;
        }
        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        }
    };

    private void openCamera() {
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        Log.d(TAG, "opening camera");
        try {
            cameraId = manager.getCameraIdList()[0];
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            assert map != null;
            imageDimension = map.getOutputSizes(SurfaceTexture.class)[0];
            // Add permission for camera and let user grant the permission
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_CAMERA_PERMISSION);
                return;
            }
            manager.openCamera(cameraId, stateCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        Log.e(TAG, "openCamera X");
    }


    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice camera) {
            //This is called when the camera is open
            Log.e(TAG, "onOpened");
            cameraDevice = camera;
            createCameraPreview();
        }
        @Override
        public void onDisconnected(CameraDevice camera) {
            cameraDevice.close();
        }
        @Override
        public void onError(CameraDevice camera, int error) {
            cameraDevice.close();
            cameraDevice = null;
        }
    };

    protected void createCameraPreview() {
        try {
            SurfaceTexture texture = previewiew.getSurfaceTexture();
            assert texture != null;
            texture.setDefaultBufferSize(imageDimension.getWidth(), imageDimension.getHeight());
            Surface surface = new Surface(texture);
            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            captureRequestBuilder.addTarget(surface);
            cameraDevice.createCaptureSession(Arrays.asList(surface), new CameraCaptureSession.StateCallback(){
                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                    //The camera is already closed
                    if (null == cameraDevice) {
                        return;
                    }
                    // When the session is ready, we start displaying the preview.
                    cameraCaptureSessions = cameraCaptureSession;
                    updatePreview();
                }
                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                    Log.e(TAG, "failed to configure camera capture session");
                }
            }, null);
            // draw overlay
            createOverlay();
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void createOverlay(){
        Bitmap b = previewiew.getBitmap();
        int w = b.getWidth();
        int h = b.getHeight();
        //Log.d(TAG, "size "+ w + " x "+h);
        Bitmap tempBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas tempCanvas = new Canvas(tempBitmap);
        tempCanvas.drawColor(Color.GRAY);
        Paint p = new Paint();
        p.setColor(Color.TRANSPARENT);
        p.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        int radius = Math.min(w,h)/2;
        tempCanvas.drawCircle(w/2,h/2,radius,p);
        overlay.setImageBitmap(tempBitmap);
        overlay.setAlpha(0.5f);
    }

    protected void updatePreview() {
        if(null == cameraDevice) {
            Log.e(TAG, "updatePreview error");
            return;
        }
        captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
        try {
            cameraCaptureSessions.setRepeatingRequest(captureRequestBuilder.build(), null, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }
    private void closeCamera() {
        if (null != cameraDevice) {
            cameraDevice.close();
            cameraDevice = null;
        }
    }


    @Override
    protected void onResume() {
        super.onResume();
        Log.v(TAG, "onResume");
        if (previewiew.isAvailable()) {
            openCamera();
        }
    }
    @Override
    protected void onPause() {
        Log.v(TAG, "onPause");
        closeCamera();
        super.onPause();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}

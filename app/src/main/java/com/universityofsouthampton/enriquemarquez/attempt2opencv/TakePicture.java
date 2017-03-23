package com.universityofsouthampton.enriquemarquez.attempt2opencv;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.PictureCallback;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.Display;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

import org.opencv.android.Utils;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Rect;
import org.opencv.core.Size;
import org.opencv.objdetect.CascadeClassifier;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;

public class TakePicture extends Activity{

    private Camera mCamera;
    private CameraPreview mPreview;
    private PictureCallback mPicture;
    private Button capture, switchCamera, back;
    private Context myContext;
    private LinearLayout cameraPreview;
    private boolean cameraFront = false;
    private int faceCountNow;
    Mat faceMat;

    private static final String TAG = "OPENCV";
    //CASCADE CLASSIFIER
    File mCascadeFile;
    CascadeClassifier myFaceDetector;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_take_picture);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        myContext = this;
    }

    // Sets up the components
    private void initialize() {
        try {
            InputStream is = getResources().openRawResource(R.raw.lbpcascade_frontalface);
            File cascadeDir = getDir("cascade", Context.MODE_PRIVATE);
            //LOAD CASCADE CLASSIFIER
            mCascadeFile = new File(cascadeDir, "lbpcascade_frontalface.xml");
            FileOutputStream os = new FileOutputStream(mCascadeFile);
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
            }
            is.close();
            os.close();
            myFaceDetector = new CascadeClassifier(mCascadeFile.getAbsolutePath());
            if (myFaceDetector.empty()){
                Log.e(TAG,"FAILED TO LOAD CASCADE CLASSIFIER");
            }else
                Log.i(TAG,"LOADED CASCADE CLASSIFIER FROM " + mCascadeFile.getAbsolutePath());

            cascadeDir.delete();
        }catch (Exception e){
            Log.e("OP", "Error", e);
        }

        capture = (Button) findViewById(R.id.button_capture);
        switchCamera = (Button) findViewById(R.id.button_ChangeCamera);
        back = (Button) findViewById(R.id.button_back);
        cameraPreview = (LinearLayout) findViewById(R.id.camera_preview);
        mPreview = new CameraPreview(myContext, mCamera);
        cameraPreview.addView(mPreview);

        //Change the camera
        switchCamera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                releaseCamera();
                chooseCamera();
            }
        });

        //Take picture and open the next activity
        capture.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mCamera.takePicture(null, null, mPicture);
            }
        });

        //End activity
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                releaseCamera();
                finish();
            }
        });
    }

    //Find what number the front facing camera is
    private int findFrontFacingCamera() {
        int cameraId = -1;
        // Search for the front facing camera
        int numberOfCameras = Camera.getNumberOfCameras();
        for (int i = 0; i < numberOfCameras; i++) {
            CameraInfo info = new CameraInfo();
            Camera.getCameraInfo(i, info);
            if (info.facing == CameraInfo.CAMERA_FACING_FRONT) {
                cameraId = i;
                cameraFront = true;
                break;
            }
        }
        return cameraId;
    }

    //Finds what number the back facing camera is
    private int findBackFacingCamera() {
        int cameraId = -1;
        //Search for the back facing camera
        //get the number of cameras
        int numberOfCameras = Camera.getNumberOfCameras();
        //for every camera check
        for (int i = 0; i < numberOfCameras; i++) {
            CameraInfo info = new CameraInfo();
            Camera.getCameraInfo(i, info);
            if (info.facing == CameraInfo.CAMERA_FACING_BACK) {
                cameraId = i;
                cameraFront = false;
                break;
            }
        }
        return cameraId;
    }

    public void onResume() {
        super.onResume();
        initialize();
        if (!hasCamera(myContext)) {
            Toast toast = Toast.makeText(myContext, "Sorry, your phone does not have a camera!", Toast.LENGTH_LONG);
            toast.show();
            finish();
        }
        if (mCamera == null) {
            //if the front facing camera does exist
            if (findFrontFacingCamera() == 1) {
                //release the old camera instance
                //switch camera, from the front and the back and vice versa
                cameraFront = false;
                releaseCamera();
                chooseCamera();
            //if the front facing camera doesn't exist
            } else {
                Toast toast = Toast.makeText(myContext, "Sorry, your phone has only one camera!", Toast.LENGTH_LONG);
                toast.show();
                switchCamera.setEnabled(false);
                releaseCamera();
                chooseCamera();
            }

        }

        //Camera.Parameters parameters = mCamera.getParameters();
        //parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
        //mCamera.setParameters(parameters);
    };

    public void chooseCamera() {
        //if the camera preview is the front then change to back
        if (cameraFront) {
            int cameraId = findBackFacingCamera();
            if (cameraId >= 0) {
                //open the backFacingCamera
                //set a picture callback
                //refresh the preview
                mCamera = Camera.open(cameraId);
                mPicture = getPictureCallback();
                mPreview.refreshCamera(mCamera);


            }
        }
        //if the camera preview is the back then change to front
        else {
            int cameraId = findFrontFacingCamera();
            if (cameraId >= 0) {
                //open the backFacingCamera
                //set a picture callback
                //refresh the preview
                mCamera = Camera.open(cameraId);
                mPicture = getPictureCallback();
                mPreview.refreshCamera(mCamera);


            }
        }
        //Get Camera to rotate to the correct screen orientation
        Display display = ((WindowManager) getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
        if (display.getRotation() == Surface.ROTATION_0) {
            mCamera.setDisplayOrientation(90);
        }
        if (display.getRotation() == Surface.ROTATION_90) {
            mCamera.setDisplayOrientation(0);
        }
        if (display.getRotation() == Surface.ROTATION_270) {
            mCamera.setDisplayOrientation(180);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        //when on Pause, release camera in order to be used from other applications
        releaseCamera();
        finish();
    }

    private boolean hasCamera(Context context) {
        //check if the device has a  camera
        if (context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA)) {
            return true;
        } else {
            return false;
        }
    }

    //Saves a picture and opens the next activity
    private PictureCallback getPictureCallback() {
        PictureCallback picture = new PictureCallback() {

            @Override
            public void onPictureTaken(byte[] data, Camera camera) {
                //make a new picture file
                File pictureFile = getOutputMediaFile();

                if (pictureFile == null) {
                    return;
                }
                try {
                    FileOutputStream fos = new FileOutputStream(pictureFile);
                    fos.write(data);
                    fos.close();

                    //write the file
                    String photopath = pictureFile.getPath();
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inScaled = false;
                    Bitmap bmp = BitmapFactory.decodeFile(photopath, options);
                    //CREATE FACE BITMAP
                    Bitmap faceBmp = BitmapFactory.decodeFile(photopath, options);

                    //Make sure the saved picture is correctly rotated
                    Matrix matrix = new Matrix();
                    Display display = ((WindowManager) getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
                    if (display.getRotation() == Surface.ROTATION_0) {
                        if (cameraFront) {
                            matrix.setScale(-1, 1);
                            matrix.postTranslate(bmp.getWidth(), 0);
                            matrix.postRotate(90);
                        } else {
                            matrix.postRotate(90);
                        }
                    }
                    if (display.getRotation() == Surface.ROTATION_90) {
                        if (cameraFront) {
                            matrix.setScale(-1, 1);
                            matrix.postTranslate(bmp.getWidth(), 0);
                        }
                    }
                    if (display.getRotation() == Surface.ROTATION_270) {
                        if (cameraFront) {
                            matrix.setScale(-1, 1);
                            matrix.postTranslate(bmp.getWidth(), 0);
                            matrix.postRotate(180);
                        } else {
                            matrix.postRotate(180);
                        }
                    }



                    bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), matrix, true);
                    Mat imageMat = new Mat(bmp.getHeight(), bmp.getWidth(), CvType.CV_8U);
                    //TRANSFORM FROM BMP TO MAT
                    Utils.bitmapToMat(bmp, imageMat);
                    //EXTRACT THE FACE FROM THE IMAGE
                    faceMat = ViolaJonesFaceExtractor(imageMat);
                    if (faceMat != null) {

                        faceBmp = Bitmap.createBitmap(faceBmp, 0, 0, faceMat.width(), faceMat.height());

                        Utils.matToBitmap(faceMat, faceBmp);

                        FileOutputStream fOut;
                        try {
                            fOut = new FileOutputStream(pictureFile);
                            faceBmp.compress(Bitmap.CompressFormat.PNG, 100, fOut);
                            fOut.flush();
                            fOut.close();

                        } catch (FileNotFoundException e1) {
                            // TODO Auto-generated catch block
                            e1.printStackTrace();
                        } catch (IOException e) {
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                        }

                        //Only tell the user its been saved if they are adding a new person as otherwise it gets deleted
                        if (getIntent().getBooleanExtra("New Person", true) == true) {
                            Toast toast = Toast.makeText(myContext, "Picture saved: " + pictureFile.getName(), Toast.LENGTH_LONG);
                            toast.show();
                        }
                    }
                    }catch(FileNotFoundException e){
                    }catch(IOException e){
                    }

                    //refresh camera to continue preview
                    mPreview.refreshCamera(mCamera);

                    //Open the next activity depending on whether the user wants to add or recognise a person
                    openNextActivity(pictureFile.getPath().toString(), faceMat);

                }
             };
        return picture;
    }

    //Open the next activity depending on whether the user wants to add or recognise a person
    private void openNextActivity(String photopath, Mat faceMat) {
        releaseCamera();
        //IF NO FACE HAS BEEN DETECTED, RETURN TO MAIN MENU
        if (faceMat == null){
            Toast toast1 = Toast.makeText(myContext, "NO FACE DETECTED", Toast.LENGTH_LONG);
            toast1.show();
            releaseCamera();
            Intent intent5 = new Intent(TakePicture.this, MainActivity.class);
            intent5.putExtra("training",false);
            startActivity(intent5);

        }else if (getIntent().getBooleanExtra("New Person", true) == true) {
            faceCountNow = getIntent().getIntExtra("faceCount",-1);
            if(faceCountNow==-1){
                Toast toast1 = Toast.makeText(myContext, "Error! Please try again!", Toast.LENGTH_LONG);
                toast1.show();
                releaseCamera();
                Intent intent5 = new Intent(TakePicture.this, MainActivity.class);
                intent5.putExtra("training",false);
                startActivity(intent5);
            }else if(faceCountNow==4){
                Intent intent = new Intent(TakePicture.this, NewPerson.class);
                intent.putExtra("path", getIntent().getStringExtra("path")+" "+photopath);
                startActivity(intent);

            }else{
                Intent intent = new Intent(TakePicture.this, ImgShow.class);
                intent.putExtra("New Person", true);
                intent.putExtra("faceCount",faceCountNow);
                intent.putExtra("path", getIntent().getStringExtra("path"));
                intent.putExtra("new path", photopath);
                startActivity(intent);
                finish();
            }
        }
        else {
            Intent intent = new Intent(TakePicture.this, Scan.class);
            intent.putExtra("path", photopath);
            boolean fisher = getIntent().getBooleanExtra("Fisher", true);
            intent.putExtra("Fisher", fisher);
            startActivity(intent);

        }

        finish();
    }

    //Set up the filename and folder of the picture being taken
    private File getOutputMediaFile() {
        //make a new file directory inside the local directory folder
        File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).getPath(), "Selfie_Secure");

        //if the selfie secure folder does not exist
        if (!mediaStorageDir.exists()) {
            //if you cannot make this folder return
            if (!mediaStorageDir.mkdirs()) {
                return null;
            }
        }

        //take the current timeStamp
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        File mediaFile;
        //and make a media file:
        mediaFile = new File(mediaStorageDir.getPath() + File.separator + "IMG_" + timeStamp + ".png");

        return mediaFile;
    }

    //Camera must be released before a new one can be opened
    private void releaseCamera() {
        // stop and release camera
        if (mCamera != null) {
            mCamera.release();
            mCamera = null;
        }
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

    @Override
    protected void onStop() {
        super.onStop();
        releaseCamera();
    }

    //Go back to the main menu when back is pressed
    public void onBackPressed() {
        releaseCamera();
        Intent intent = new Intent(TakePicture.this, MainActivity.class);
        intent.putExtra("training",false);
        startActivity(intent);
    }

    //INPUT IS THE IMAGE. RETURNS THE FACE IMAGE
    private Mat ViolaJonesFaceExtractor(Mat matrix){
        //VARIABLE TO STORE ALL THE FACES POSITION
        MatOfRect faces = new MatOfRect();
        //FACE MAT
        Mat returningMat;
        // IF THE DETECTOR HAS BEEN INITIALISED
        if (myFaceDetector != null){
            //DETECT ALL THE FACES
            myFaceDetector.detectMultiScale(matrix,faces,1.1,2,2,
                    new Size((int) 0.4*matrix.height(), (int) 0.4*matrix.height()), new Size());

        }
        //GET THE POSITIONS OF THE FACES
        Rect[] facesArray = faces.toArray();
        double currentSize = 0;
        Rect face = null;
        //DETECT THE BIGGER FACE IN THE IMAGE
        for (int i = 0; i < facesArray.length; i++){
            if(facesArray[i].size().area() > currentSize){
                currentSize = facesArray[i].size().area();
                face = facesArray[i];
            }
        }

        //RETURN THE FACE IMAGE
        if(face!= null){
            returningMat = new Mat(matrix,face);
            return returningMat;
        }else{
            return null;
        }

    }


}

package com.universityofsouthampton.enriquemarquez.attempt2opencv;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfKeyPoint;
import org.opencv.core.MatOfRect;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.core.TermCriteria;
import org.opencv.features2d.DescriptorExtractor;
import org.opencv.features2d.DescriptorMatcher;
import org.opencv.features2d.FeatureDetector;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;
import org.opencv.objdetect.Objdetect;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.opencv.android.NativeCameraView.TAG;
import static org.opencv.core.CvType.CV_8U;
import static org.opencv.core.CvType.CV_8UC1;


public class MainActivity extends ActionBarActivity{

    Button newPersonButton, scanButton, deleteButton,trainButton;
    int selectedActivity;
    private Context myContext;
    private int faceCount = 0;
    private ImageView waitImgFrame;
    private Bitmap waitImg;
    File mCascadeFile1;
    CascadeClassifier myFaceDetector1;
    File mCascadeFile2;
    CascadeClassifier myFaceDetector2;
    //NUMBER OF CLUSTERS TO USE IN FISHERS ALGORITHM
    private final static int NUM_CLUSTERS = 1000;
    //CENTRES AND COVARIANCES OF GMM VARIABLE
    private Mat centres = new Mat();

    //FACES IN THE DATABASE
    private Object[] faces;
    private HashMap<Bitmap, String> facesMap = new HashMap<Bitmap, String>();

    //FEATURE DETECTOR VARIABLE
    FeatureDetector featureDetector = FeatureDetector.create(FeatureDetector.DENSE);
    //DESCRIPTOR EXTRACTOR VARIABLE
    DescriptorExtractor descriptorExtractor = DescriptorExtractor.create(DescriptorExtractor.ORB);
    //DESCRIPTOR MATCHER VARIABLE
    DescriptorMatcher descriptorMatcher = DescriptorMatcher.create(DescriptorMatcher.BRUTEFORCE);

    static {

        if(!OpenCVLoader.initDebug()){
            Log.i("openCV","OpenCV was not initialized correctly!!");
        }else{
            Log.i("openCV","OpenCV was initialized correctly!!");
        }

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if(getIntent().getBooleanExtra("training", false) == true){
            tellUser("Training Successfully");
        }
        myContext = this;
        newPersonButton = (Button) findViewById(R.id.insert_Button);
        scanButton = (Button) findViewById(R.id.scan_Button);
        trainButton = (Button) findViewById(R.id.train_Button);
        deleteButton = (Button) findViewById(R.id.delete_Button);
        waitImgFrame = (ImageView) findViewById(R.id.waitImgFrame);
        selectedActivity = 0;

        newPersonButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, TakePicture.class);
                intent.putExtra("New Person", true);
                intent.putExtra("faceCount",faceCount);
                startActivity(intent);
                finish();
            }
        });
        scanButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                orborfisher();
            }
        });
        trainButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showWaitImage();
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        doTraining();
                        Intent intent = new Intent(MainActivity.this, MainActivity.class);
                        intent.putExtra("training",true);
                        startActivity(intent);
                        finish();
                    }
                }).start();
            }
        });
        deleteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                checkDeleteDatabase();
            }
        });

    }

    private void orborfisher() {
        Intent intent = new Intent(MainActivity.this, TakePicture.class);
        intent.putExtra("New Person", false);
        intent.putExtra("Fisher", true);
        startActivity(intent);
        finish();
    }

    private void checkDeleteDatabase() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Delete Database Confirmation");
        builder.setMessage("Are you sure you want to delete the database");
        builder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                //User clicked yes so delete the database
                DatabaseOperations DB = new DatabaseOperations(myContext);
                DB.removeAll(DB);
            }
        });
        builder.setNegativeButton("No", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                //User clicked no so return to main screen
                dialog.cancel();
            }
        });
        AlertDialog dialog = builder.create();
        dialog.setCanceledOnTouchOutside(false);
        //Put the dialog at the top of the screen
        Window window = dialog.getWindow();
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        WindowManager.LayoutParams wlp = window.getAttributes();
        wlp.gravity = Gravity.TOP;
        window.setAttributes(wlp);
        //Show the dialog
        dialog.show();
    }

    public void doTraining(){
        //GET FACES FROM DATABASE
        faces = getFacesArrayFromDatabase();

        //INITIALIZE TYPE OF FEATURE DETECTOR
        featureDetector = FeatureDetector.create(FeatureDetector.DENSE);
        //INITIALIZE TYPE OF FEATURE EXTRACTOR
        descriptorExtractor = DescriptorExtractor.create(DescriptorExtractor.ORB);
        //INITIALIZE TYPE OF MATCHES
        descriptorMatcher = DescriptorMatcher.create(DescriptorMatcher.BRUTEFORCE);

        //GET ALL THE MATS FROM EACH IMAGE IN THE DATABASE
        List<Mat> facesMat = fromBitmapToMat();
        List<Mat> databaseDescriptors = new ArrayList<>();
        //GET ALL THE DESCRIPOTRS FROM THE IMAGES IN THE DATABASE
        databaseDescriptors.addAll(generateDescriptors(facesMat));
        Mat fullDescriptors = new Mat();
        //CONCATENATE VERTICALLY ALL THE DESCRIPTORS IN A SINGLE MATRIX
        Core.vconcat(databaseDescriptors, fullDescriptors);
        //CONVERTO TO 32 FLOAT TO PERFORM GMM OR KMEANS
        fullDescriptors.convertTo(fullDescriptors, CvType.CV_32F);
        Mat labels = new Mat();
        //ITERATION ALGORITHM PARAMETERS
        TermCriteria criteria = new TermCriteria(TermCriteria.EPS + TermCriteria.MAX_ITER, 100, 0.1);
        //COMPUTE CENTROIDS
        Core.kmeans(fullDescriptors, NUM_CLUSTERS, labels, criteria, 3, Core.KMEANS_PP_CENTERS, centres);

        saveCentresToFile(centres);
    }

    //Generate an array of bitmaps containing the faces from Database
    private Object[] getFacesArrayFromDatabase() {
        DatabaseOperations dop = new DatabaseOperations(this);
        Cursor CR = dop.getInformation(dop);
        CR.moveToFirst();
        //Add all the image paths from the database to the array
        if (CR.getString(1) == null) {
            return null;
        }
        do {
            //Add the values from the second column, which is where the paths are stored
            String imagePath = CR.getString(1);
            Bitmap bmp = BitmapFactory.decodeFile(imagePath);//Decode a file path into a bitmap
            facesMap.put(bmp, imagePath);
        } while (CR.moveToNext());
        Object[] keys = facesMap.keySet().toArray();
        return keys;
    }
    //CONVERTS THE FACES ARRAY IN DATABASE INTO A MAT ARRAY
    private List<Mat> fromBitmapToMat() {
        //CREATE VARIABLE
        List<Mat> facesMat = new ArrayList<>();
        //LOOP THROUGH ALL THE FACES
        for (int i = 0; i < faces.length; i++) {
            //TEMPORAL MAT
            Mat tmpMat = new Mat();
            Mat tmpMat1 = new Mat();
            Mat tmpMat2 = new Mat();
            Mat tmpMat3 = new Mat();
            Mat tmpEye;
            tmpMat1.convertTo(tmpMat1, CvType.CV_8UC1);
            //FROM BITMAP TO MAT
            Utils.bitmapToMat((Bitmap) faces[i], tmpMat);
            Imgproc.cvtColor(tmpMat, tmpMat2, Imgproc.COLOR_BGRA2GRAY, 1);
            tmpEye=eyeDetectAndCrop(tmpMat2);
            Imgproc.equalizeHist(tmpEye, tmpMat3);
            Imgproc.bilateralFilter(tmpMat3, tmpMat1, 0, 20.0, 2.0);
            if(i==0){
                File f = new File("/storage/emulated/0/Pictures/Selfie_Secure/","bm.png");
                File f1 = new File("/storage/emulated/0/Pictures/Selfie_Secure/","bm1.png");
                File f2 = new File("/storage/emulated/0/Pictures/Selfie_Secure/","bm2.png");
                File f3 = new File("/storage/emulated/0/Pictures/Selfie_Secure/","bm3.png");
                Bitmap bm = Bitmap.createBitmap(
                        tmpMat.cols(), tmpMat.rows(), Bitmap.Config.RGB_565);
                Bitmap bm1 = Bitmap.createBitmap(
                        tmpMat1.cols(), tmpMat1.rows(), Bitmap.Config.RGB_565);
                Bitmap bm2 = Bitmap.createBitmap(
                        tmpMat2.cols(), tmpMat2.rows(), Bitmap.Config.RGB_565);
                Bitmap bm3 = Bitmap.createBitmap(
                        tmpMat3.cols(), tmpMat3.rows(), Bitmap.Config.RGB_565);

                Utils.matToBitmap(tmpMat,bm);
                Utils.matToBitmap(tmpMat1,bm1);
                Utils.matToBitmap(tmpMat2,bm2);
                Utils.matToBitmap(tmpMat3,bm3);
                try {
                    FileOutputStream out = new FileOutputStream(f);
                    bm.compress(Bitmap.CompressFormat.PNG, 90, out);
                    out.flush();
                    out.close();
                } catch (FileNotFoundException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }

                try {
                    FileOutputStream out = new FileOutputStream(f1);
                    bm1.compress(Bitmap.CompressFormat.PNG, 90, out);
                    out.flush();
                    out.close();
                } catch (FileNotFoundException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }

                try {
                    FileOutputStream out = new FileOutputStream(f2);
                    bm2.compress(Bitmap.CompressFormat.PNG, 90, out);
                    out.flush();
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }

                try {
                    FileOutputStream out = new FileOutputStream(f3);
                    bm3.compress(Bitmap.CompressFormat.PNG, 90, out);
                    out.flush();
                    out.close();
                } catch (FileNotFoundException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
            //ADD CURRENT MAT TO ARRAY
            facesMat.add(tmpMat1);
        }
        return facesMat;
    }

    //DETECT EYES IN THE IMAGE AND CROP THE FACE IMAGE ACCORDINGLY
    private Mat eyeDetectAndCrop(Mat img) {

        try {
            InputStream is = getResources().openRawResource(R.raw.haarcascade_eye_tree_eyeglasses);
            File cascadeDir1 = getDir("cascade", Context.MODE_PRIVATE);
            //LOAD CASCADE CLASSIFIER
            mCascadeFile1 = new File(cascadeDir1, "haarcascade_eye_tree_eyeglasses.xml");
            FileOutputStream os = new FileOutputStream(mCascadeFile1);
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
            }
            is.close();
            os.close();
            myFaceDetector1 = new CascadeClassifier(mCascadeFile1.getAbsolutePath());
            if (myFaceDetector1.empty()) {
                Log.e(TAG, "FAILED TO LOAD CASCADE CLASSIFIER");
            } else
                Log.i(TAG, "LOADED CASCADE CLASSIFIER FROM " + mCascadeFile1.getAbsolutePath());

            cascadeDir1.delete();
        } catch (Exception e) {
            Log.e("OP", "Error", e);
        }

        try {
            InputStream is1 = getResources().openRawResource(R.raw.haarcascade_eye);
            File cascadeDir2 = getDir("cascade", Context.MODE_PRIVATE);
            //LOAD CASCADE CLASSIFIER
            mCascadeFile2 = new File(cascadeDir2, "haarcascade_eye.xml");
            FileOutputStream os1 = new FileOutputStream(mCascadeFile2);
            byte[] buffer1 = new byte[4096];
            int bytesRead1;
            while ((bytesRead1 = is1.read(buffer1)) != -1) {
                os1.write(buffer1, 0, bytesRead1);
            }
            is1.close();
            os1.close();
            myFaceDetector2 = new CascadeClassifier(mCascadeFile2.getAbsolutePath());
            if (myFaceDetector2.empty()) {
                Log.e(TAG, "FAILED TO LOAD CASCADE CLASSIFIER");
            } else
                Log.i(TAG, "LOADED CASCADE CLASSIFIER FROM " + mCascadeFile2.getAbsolutePath());

            cascadeDir2.delete();
        } catch (Exception e) {
            Log.e("OP", "Error", e);
        }

        MatOfRect eyes = new MatOfRect();
        MatOfRect eyes1 = new MatOfRect();
        //FACE MAT
        Mat returningMat;
        Rect[] eyesArray;
        Rect[] eyesArray1;
        int count = 0;

        while(true){
            if (myFaceDetector2 != null) {
                myFaceDetector2.detectMultiScale(img, eyes, 1.1, 2,
                        0|2,
                        new Size(70, 70),
                        new Size());
                eyesArray = eyes.toArray();
                if(eyesArray.length==2){

                    float eyesCenterX = (eyesArray[0].x+eyesArray[1].x)/2+50;
                    float eyesCenterY = (eyesArray[0].y+eyesArray[1].y)/2+200;
                    Point eyesCenter = new Point(eyesCenterX,eyesCenterY);
                    // Get the angle between the 2 eyes.
                    double dy = Math.abs(eyesArray[0].y - eyesArray[1].y);
                    double dx = Math.abs(eyesArray[0].x - eyesArray[1].x);
                    double len = Math.sqrt(dx*dx + dy*dy);
                    double angle = Math.atan2(dy, dx) * 180.0/Math.PI; // Convert from radians to degrees.

                    double DESIRED_LEFT_EYE_X = 0.20;     // Controls how much of the face is visible after preprocessing.
                    double DESIRED_LEFT_EYE_Y = 0.14;
                    double DESIRED_RIGHT_EYE_X = (1.0f - DESIRED_LEFT_EYE_X);
                    // Get the amount we need to scale the image to be the desired fixed size we want.
                    double desiredLen = (DESIRED_RIGHT_EYE_X - DESIRED_LEFT_EYE_X) * img.width();
                    double scale = desiredLen / len;
                    // Get the transformation matrix for rotating and scaling the face to the desired angle & size.
                    returningMat = Imgproc.getRotationMatrix2D(eyesCenter,angle,scale);

                    // Shift the center of the eyes to be the desired center between the eyes.
                    //returningMat.put(0,2,returningMat.get(0,2)[0]+(img.width()*0.5f - eyesCenter.x));
                    //returningMat.put(1,2,returningMat.get(1,2)[0]+(img.width()* DESIRED_LEFT_EYE_Y - eyesCenter.y));
                    // Rotate and scale and translate the image to the desired angle & size & position!
                    // Note that we use 'w' for the height instead of 'h',
                    // because the input face has 1:1 aspect ratio.
                    Mat warped = new Mat(img.width(), img.width(), CV_8U, new Scalar(128));
                    // Clear the output image to a default grey.
                    //warpAffine(gray, warped, returningMat, warped.size());
                    Imgproc.warpAffine(img, warped, returningMat, warped.size());
                    return warped;
                }
            }
            if (myFaceDetector1 != null) {
                myFaceDetector1.detectMultiScale(img, eyes1, 1.1, 2,
                        2 | Objdetect.CASCADE_SCALE_IMAGE,
                        new Size(70, 70),
                        new Size());
                eyesArray1 = eyes1.toArray();
                if(eyesArray1.length==2){

                    float eyesCenterX = (eyesArray1[0].x+eyesArray1[1].x)/2+50;
                    float eyesCenterY = (eyesArray1[0].y+eyesArray1[1].y)/2+200;
                    Point eyesCenter = new Point(eyesCenterX,eyesCenterY);
                    // Get the angle between the 2 eyes.
                    double dy = Math.abs(eyesArray1[0].y - eyesArray1[1].y);
                    double dx = Math.abs(eyesArray1[0].x - eyesArray1[1].x);
                    double len = Math.sqrt(dx*dx + dy*dy);
                    double angle = Math.atan2(dy, dx) * 180.0/Math.PI; // Convert from radians to degrees.

                    double DESIRED_LEFT_EYE_X = 0.20;     // Controls how much of the face is visible after preprocessing.
                    double DESIRED_LEFT_EYE_Y = 0.14;
                    double DESIRED_RIGHT_EYE_X = (1.0f - DESIRED_LEFT_EYE_X);
                    // Get the amount we need to scale the image to be the desired fixed size we want.
                    double desiredLen = (DESIRED_RIGHT_EYE_X - DESIRED_LEFT_EYE_X) * img.width();
                    double scale = desiredLen / len;

                    // Get the transformation matrix for rotating and scaling the face to the desired angle & size.
                    returningMat = Imgproc.getRotationMatrix2D(eyesCenter,angle,scale);

                    // Shift the center of the eyes to be the desired center between the eyes.
                    //returningMat.put(0,2,returningMat.get(0,2)[0]+(img.width()*0.5f - eyesCenter.x));
                    //returningMat.put(1,2,returningMat.get(1,2)[0]+(img.width()* DESIRED_LEFT_EYE_Y - eyesCenter.y));
                    // Rotate and scale and translate the image to the desired angle & size & position!
                    // Note that we use 'w' for the height instead of 'h',
                    // because the input face has 1:1 aspect ratio.
                    Mat warped = new Mat(img.width(), img.width(), CV_8U, new Scalar(128));
                    // Clear the output image to a default grey.
                    //warpAffine(gray, warped, returningMat, warped.size());
                    Imgproc.warpAffine(img, warped, returningMat, warped.size());
                    return warped;
                    //break;
                }
            }
            count++;
            if(count==10){
                return img;
            }
        }
        //return img;
    }

    //RETURNS THE DESCRIPTORS GIVEN THE MAT FACES ARRAY
    private List<Mat> generateDescriptors(List<Mat> facesMat) {
        List<Mat> descriptorsDatabase = new ArrayList<>();
        //LOOP THROUGH ALL THE FACES
        for (int i = 0; i < facesMat.size(); i++) {
            //MAT OF INTEREST POINTS
            MatOfKeyPoint currentFaceKeyPoints = new MatOfKeyPoint();
            Mat descriptorsCurrentFace = new Mat();
            //EXTRACT FEATURES FORM CURRENT FACE
            featureDetector.detect(facesMat.get(i), currentFaceKeyPoints);
            //COMPUTE DESCRIPTORS OF CURRENT FACE
            descriptorExtractor.compute(facesMat.get(i), currentFaceKeyPoints, descriptorsCurrentFace);
            //ADD CURRENT DESCRIPTORS TO ARRAY
            descriptorsDatabase.add(descriptorsCurrentFace);
        }
        return descriptorsDatabase;
    }

    private void saveCentresToFile(Mat centres) {
        Bitmap bmp = Bitmap.createBitmap(centres.width(), centres.height(), Bitmap.Config.ARGB_8888);
        centres.convertTo(centres, CV_8UC1);
        Utils.matToBitmap(centres, bmp);
        File pictureFile = getOutputMediaFile();
        try {
            FileOutputStream fOut = new FileOutputStream(pictureFile);
            bmp.compress(Bitmap.CompressFormat.PNG, 100, fOut);
            fOut.flush();
            fOut.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    //Set up the filename and folder of the picture being taken
    private File getOutputMediaFile() {
        //make a new file directory inside the local directory folder
        File mediaStorageDir = new File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                        .getPath(), "Selfie_Secure");


        if (!(mediaStorageDir.exists())) {
            //mediaStorageDir.delete();
            if (!mediaStorageDir.mkdirs()) {
                //if you cannot make this folder return
                return null;
            }
        }


        File mediaFile;
        //and make a media file:
        String s = mediaStorageDir.getPath() + File.separator + "Centroids.png";
        mediaFile = new File(s);
        clearFolder(mediaFile);

        return mediaFile;
    }

    private void clearFolder(File file) {

        if (file.isDirectory()) {
            File[] childFiles = file.listFiles();
            if (childFiles == null || childFiles.length == 0) {
                return;
            }

            for (int i = 0; i < childFiles.length; i++) {
                childFiles[i].delete();
            }
            return;
        }
    }

    //show the image taken
    private void showWaitImage() {
        Resources res = getResources();
        int id = res.getIdentifier("waiting","drawable",myContext.getPackageName());
        //waitImagePath = "/drawable/iknowyou.jpg";
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inScaled = false;
        waitImg = BitmapFactory.decodeResource(res, id);
        waitImgFrame.setImageBitmap(waitImg);
        setFlickerAnimation(waitImgFrame);
    }

    private void tellUser(String message) {
        Toast toast = Toast.makeText(this, message, Toast.LENGTH_LONG);
        toast.show();
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
        finish();

    }

    private void setFlickerAnimation(ImageView imgBeShow) {
        final Animation animation = new AlphaAnimation(1, 0); // Change alpha from fully visible to invisible
        animation.setDuration(500); // duration - half a second
        animation.setInterpolator(new LinearInterpolator()); // do not alter animation rate
        animation.setRepeatCount(Animation.INFINITE); // Repeat animation infinitely
        animation.setRepeatMode(Animation.REVERSE); //
        imgBeShow.setAnimation(animation);
    }

}

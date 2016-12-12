package com.universityofsouthampton.enriquemarquez.attempt2opencv;


import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import org.opencv.android.Utils;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDMatch;
import org.opencv.core.MatOfKeyPoint;
import org.opencv.core.MatOfRect;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.features2d.DMatch;
import org.opencv.features2d.DescriptorExtractor;
import org.opencv.features2d.DescriptorMatcher;
import org.opencv.features2d.FeatureDetector;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;
import org.opencv.objdetect.Objdetect;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.opencv.android.NativeCameraView.TAG;
import static org.opencv.core.CvType.CV_8U;

public class Scan extends Activity  {

    private Button back, rescan;
    private TextView nickname;
    private String selectedImagePath;
    private ImageView img;
    private Context myContext;
    private Bitmap faceToRecognise;
    private int columnofHis =0;
    private HashMap<Bitmap, String> facesMap = new HashMap<Bitmap, String>();
    private Map<String, ?> allEntries;
    private final static int NUM_CLUSTERS = 1000;
    Mat centres1 = new Mat();
    Object [] faces;
    List<int[]> histograms = new ArrayList<>();
    File mCascadeFile1;
    CascadeClassifier myFaceDetector1;
    File mCascadeFile2;
    CascadeClassifier myFaceDetector2;


    FeatureDetector featureDetector;
    DescriptorExtractor descriptorExtractor;
    DescriptorMatcher descriptorMatcher;
    MatOfKeyPoint matOfKeyPointFaceToRecognize = new MatOfKeyPoint();
    Mat descriptorsFaceToRecognize = new Mat();
    Mat matFaceToRecognize;
    Mat matFaceToRecognize1;
    Mat matFaceToRecognize2;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_scan);
        myContext = this;

        nickname = (TextView) findViewById(R.id.nickname);
        back = (Button) findViewById(R.id.button_back);
        rescan = (Button) findViewById(R.id.button_rescan);
        img = (ImageView) findViewById(R.id.imageview);

        //Go back to the main screen
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onBackPressed();
            }
        });

        //Allow them to take a picture again
        rescan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Scan.this, TakePicture.class);
                intent.putExtra("New Person", false);
                intent.putExtra("Fisher",getIntent().getBooleanExtra("Fisher", true));
                startActivity(intent);

            }
        });
        //GET FACES FROM DATABASE
        faces = getFacesArrayFromDatabase();
        if(faces==null){
            Toast toast1 = Toast.makeText(myContext, "Please add at least one face", Toast.LENGTH_LONG);
            toast1.show();
            Intent intent5 = new Intent(Scan.this, MainActivity.class);
            intent5.putExtra("training",false);
            startActivity(intent5);
            finish();
        }
        //INITIALIZE TYPE OF FEATURE DETECTOR
        featureDetector = FeatureDetector.create(FeatureDetector.DENSE);
        //INITIALIZE TYPE OF FEATURE EXTRACTOR
        descriptorExtractor = DescriptorExtractor.create(DescriptorExtractor.ORB);
        //INITIALIZE TYPE OF MATCHES
        descriptorMatcher = DescriptorMatcher.create(DescriptorMatcher.BRUTEFORCE);

        //IF FISHER IS GOING TO BE PREFORMED, CALCULATE THE NECESSARY PARAMETERS
        //GET ALL THE MATS FROM EACH IMAGE IN THE DATABASE
        List<Mat> facesMat = fromBitmapToMat();
        List<Mat> databaseDescriptors = new ArrayList<>();
        //GET ALL THE DESCRIPOTRS FROM THE IMAGES IN THE DATABASE
        databaseDescriptors.addAll(generateDescriptors(facesMat));

        centres1 = getCentresFromFile();
        centres1.convertTo(centres1, CvType.CV_32F);

        //GO THORUGH ALL THE FACES
        for (int k = 0; k < databaseDescriptors.size(); k++) {
            MatOfDMatch matchesData = new MatOfDMatch();
            List<DMatch> matchesLData;
            Mat currentDescriptor = databaseDescriptors.get(k);
            currentDescriptor.convertTo(currentDescriptor, CvType.CV_32FC1);
            //MATCH ALL THE DESCRIPTORS WITH THE CENTRES
            //Finds the best match for each descriptor from a query set.
            descriptorMatcher.match(currentDescriptor, centres1, matchesData);
            matchesLData = matchesData.toList();
            int[] matchesIndexesTmp = new int[matchesLData.size()];
            columnofHis= matchesLData.size();
            for (int j = 0; j < matchesLData.size(); j++) {
                //COMPUTE VECTOR OF MATCHES
                DMatch currentMatch = matchesLData.get(j);
                matchesIndexesTmp[j] = currentMatch.trainIdx;
            }
            //GENERATE HISTOGRAMS OF ALL THE IMAGES
            histograms.add(generateHistogram(matchesIndexesTmp, NUM_CLUSTERS));
        }
        initialise();
        checkFaceIsCorrect();
    }

    //show the image taken
    private void initialise() {
        selectedImagePath = getIntent().getStringExtra("path");
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inScaled = false;
        faceToRecognise = BitmapFactory.decodeFile(selectedImagePath, options);
        img.setImageBitmap(faceToRecognise);
    }

    private void checkFaceIsCorrect() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Face Detection Confirmation");
        builder.setMessage("Has your face been detected correctly?");
        builder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                //User clicked yes so close the dialog
                dialog.cancel();
                recognise();
            }
        });
        builder.setNegativeButton("No", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                //User clicked no so return to main screen
                tellUser("Please try again! We recommend keeping your face straight and removing your glasses.");
                //end the activity
                onBackPressed();
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


    private void tellUser(String message) {
        Toast toast = Toast.makeText(this, message, Toast.LENGTH_LONG);
        toast.show();
    }

    //Recognise the face from the database and then display the appropriate nickname
    private void recognise() {
        //Object[] faces = getFacesArrayFromPreferences();

        Bitmap recognisedFace;
        //Do facial recognition
        recognisedFace  = recogniseUsingBOV();
        if (recognisedFace != null) {
           //DisplayNicknameFromPreferences(recognisedFace);
            DisplayNicknameFromDatabase(recognisedFace);
        }
        //If the facial recognition didn't return a face because nothing matched
        else {
           //Display that the face is unrecognised
           nickname.setText("Face not recognised");
        }

    }

    private Mat getCentresFromFile(){

        Mat centres2 =  new Mat(new Size(32, 1000), CvType.CV_8UC1);
        File pictureFile = getInputMediaFile();
        try {
            FileInputStream fIn = new FileInputStream(pictureFile);
            Bitmap bitmap = BitmapFactory.decodeStream(fIn);
            Utils.matToBitmap(centres2,bitmap);
            fIn.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }


        return centres2;
    }

    //Set up the filename and folder of the picture being taken
    private File getInputMediaFile() {
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

        return mediaFile;
    }

    //Generate an array of bitmaps containing the faces from Database
    private Object[] getFacesArrayFromDatabase() {
        DatabaseOperations dop = new DatabaseOperations(this);
        Cursor CR = dop.getInformation(dop);
        CR.moveToFirst();
        //Add all the image paths from the database to the array
        if(CR.getString(1)==null){
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

    //Match the bitmap found with the nickname stored in the database
    private void DisplayNicknameFromDatabase(Bitmap recognisedFace) {
        //Get the path from the recognised face
        String imagePath = facesMap.get(recognisedFace);
        DatabaseOperations dop = new DatabaseOperations(this);
        Cursor CR = dop.getNickname(dop, imagePath);
        CR.moveToFirst();
        String name = CR.getString(0);
        //Display the nickname
        nickname.setText(nickname.getText().toString() + " " + name);
    }

    //THIS METHOD RECOGNIZES USING FISHER
    private Bitmap recogniseUsingBOV(){
        //VARIABLE WITH MATCHES INFORMATION
        MatOfDMatch matches = new MatOfDMatch();
        //ARRAY OF MATCHES
        List<DMatch> matchesL;
        //faceToRecognise=convertGreyImg(faceToRecognise);
        //MAT OF FACE TO RECOGNIZE
        matFaceToRecognize = new Mat(faceToRecognise.getHeight(),faceToRecognise.getWidth(), CvType.CV_8UC1);
        matFaceToRecognize2 = new Mat(faceToRecognise.getHeight(),faceToRecognise.getWidth(), CvType.CV_8UC1);
        matFaceToRecognize1 = new Mat(faceToRecognise.getHeight(),faceToRecognise.getWidth(), CvType.CV_8UC1);
        //FROM BITMAP TO MAT
        Utils.bitmapToMat(faceToRecognise, matFaceToRecognize);
        //FROM RGB TO GRAY
        Imgproc.cvtColor(matFaceToRecognize, matFaceToRecognize, Imgproc.COLOR_BGR2GRAY);
        Mat matEyeToRecognize = eyeDetectAndCrop(matFaceToRecognize);
        Imgproc.equalizeHist(matEyeToRecognize, matFaceToRecognize2);
        //tmpMat.convertTo(tmpMat,CvType.CV_8UC(1));
        Imgproc.bilateralFilter(matFaceToRecognize2, matFaceToRecognize1, 0, 20.0, 2.0);
        //DETECT FEATURES IN FACE TO RECOGNIZE
        featureDetector.detect(matFaceToRecognize1, matOfKeyPointFaceToRecognize);
        //COMPUTE DESCRIPTORS IN THE FEATURES PREVIOUSLY DETECTED
        descriptorExtractor.compute(matFaceToRecognize1, matOfKeyPointFaceToRecognize, descriptorsFaceToRecognize);
        //HISTOGRAM VARIABLE
        int [] histogramFaceTestFace;
        //CONVERT FROM UNSIGNED INT TO 32 FLOAT
        descriptorsFaceToRecognize.convertTo(descriptorsFaceToRecognize, CvType.CV_32FC1);
        //CALCULATE THE DISTANCES OF THE DESCRIPTORS TO THE CENTRES BETWEEN CENTRES AND THE DESCRIPTORS OF CURRENT FACE
        descriptorMatcher.match(descriptorsFaceToRecognize, centres1, matches);
        //TRANSFORM MATCHES INTO A LIST
        matchesL = matches.toList();
        int[] matchesIndexes = new int[matchesL.size()];
        for(int k = 0; k < matchesL.size(); k ++){
            //SAVE ALL THE DISTANCES OF THE DESCRIPTORS TO THE CENTRES AS A SINGLE VECTOR
            DMatch currentMatch = matchesL.get(k);
            matchesIndexes[k] = currentMatch.trainIdx;
        }
        //GENERATE THE HISTOGRAM BASED ON THE DISTANCES TO THE CENTRES
        histogramFaceTestFace = generateHistogram(matchesIndexes,NUM_CLUSTERS);
        //MATCH THE TEST IMAGE WITH THE HISTOGRAMS OF ALL THE IMAGES IN THE DATABASE
        int testImageIndex = matchTestWithTraining(histograms,histogramFaceTestFace);
        //IF THERE IS NO GOOD MATCH, RETURN NULL
        if (testImageIndex == -1) return null;
        //OTHERWISE RETURN THE MATCHED FACE
        else return (Bitmap) faces[testImageIndex];
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
        // IF THE DETECTOR HAS BEEN INITIALISED
        if (myFaceDetector2 != null) {
            //DETECT ALL THE FACES
            myFaceDetector2.detectMultiScale(img, eyes, 1.05, 2,
                    0|2,
                    new Size((int) 0.4*img.height(), (int) 0.4*img.height()),
                    new Size());

        }
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
                myFaceDetector1.detectMultiScale(img, eyes1, 1.05, 2,
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


    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public void onResume()
    {
        super.onResume();
    }

    public void onDestroy() {
        //Delete the picture as its no longer needed
        File file = new File(selectedImagePath);
        file.delete();
        super.onDestroy();
    }

    public void onBackPressed() {
        Intent intent = new Intent(Scan.this, MainActivity.class);
        intent.putExtra("training",false);
        startActivity(intent);
        finish();
    }

    //RETURNS THE DESCRIPTORS GIVEN THE MAT FACES ARRAY
    private List<Mat> generateDescriptors(List<Mat> facesMat){
        List<Mat> descriptorsDatabase = new ArrayList<>();
        //LOOP THROUGH ALL THE FACES
        for(int i=0;i < facesMat.size();i++) {
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
    //CONVERTS THE FACES ARRAY IN DATABASE INTO A MAT ARRAY
    private List<Mat> fromBitmapToMat(){
        //CREATE VARIABLE
        List<Mat> facesMat = new ArrayList<>();
        //LOOP THROUGH ALL THE FACES
        for (int i = 0; i < faces.length; i++) {
            //TEMPORAL MAT
            Mat tmpMat = new Mat();
            Mat tmpMat1 = new Mat();
            Mat tmpMat2 = new Mat();
            Mat tmpMat3 = new Mat();
            tmpMat1.convertTo(tmpMat1, CvType.CV_8UC1);
            //FROM BITMAP TO MAT
            Utils.bitmapToMat((Bitmap) faces[i], tmpMat);
            Imgproc.cvtColor(tmpMat, tmpMat2, Imgproc.COLOR_BGRA2GRAY, 1);
            Imgproc.equalizeHist(tmpMat2, tmpMat3);
            Imgproc.bilateralFilter(tmpMat3, tmpMat1, 0, 20.0, 2.0);
            //ADD CURRENT MAT TO ARRAY
            facesMat.add(tmpMat1);
        }
        return facesMat;
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

    }
    //THIS METHOD GENERATES A HISTOGRAM GIVEN THE DISTANCES VECTOR
    private int[] generateHistogram(int[] hist,int k){
        //CREATE HISTOGRAM
        int[] histogram = new int[k];
        //LOOP THROUGH ALL THE VECTOR
        for(int i=1; i < hist.length;i++){
            //ADD ONE TO THE CURRENT VALUE OF HISTOGRAM
            histogram[hist[i]] ++;

        }

        return histogram;
    }

    //THIS METHOD MATCHES A LIST OF HISTOGRAMS WITH A SINGLE HISTOGRAM APPLYING KNN (K=1)
    private int matchTestWithTraining(List<int[]> training,int[] test){
        //INITIALISING THE DISTANCE (INF)
        double currentDistance = Integer.MAX_VALUE;
        //CURRENT INDEX (NONE)
        int currentIndex = -1;
        //LOOP THROUGH ALL THE HISTOGRAMS
        for(int i =0 ; i <training.size(); i++){
            double accummulator = 0;
            //CALCULATE EACH DISTANCE AND ADD THEM TO THE ACCUMULATORS
            for(int j =0;j < test.length; j++)
                accummulator = accummulator + (training.get(i)[j] - test[j])*(training.get(i)[j] - test[j]);
            //SQRT THE SUM OF ALL THE DISTANCES
            Math.sqrt(accummulator);
            //IF CURRENT FACE ITS CLOSER, SAVE INDEX
            if(accummulator < currentDistance){
                currentDistance = accummulator;
                currentIndex = i;
            }
        }

        return currentIndex;
    }

}
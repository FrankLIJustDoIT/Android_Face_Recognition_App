package com.universityofsouthampton.enriquemarquez.attempt2opencv;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

public class ImgShow extends ActionBarActivity {

    private int faceCountNow;
    private String selectedImagePath;
    private String Pathnow;
    private Bitmap faceToAdd;
    private ImageView img;
    Button goodButton,badButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_picture_show);

        img = (ImageView) findViewById(R.id.imageview);
        goodButton = (Button) findViewById(R.id.button_good);
        badButton = (Button) findViewById(R.id.button_bad);

        faceCountNow=getIntent().getIntExtra("faceCount",-1);

        selectedImagePath = getIntent().getStringExtra("new path");
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inScaled = false;
        faceToAdd = BitmapFactory.decodeFile(selectedImagePath, options);
        img.setImageBitmap(faceToAdd);

        goodButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Pathnow=getIntent().getStringExtra("path")+" "+selectedImagePath;
                back();
            }
        });
        badButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tellUser("Please try again! We recommend keeping your face straight and removing your glasses.");
                //end the activity
                Pathnow=getIntent().getStringExtra("path");
                faceCountNow=faceCountNow-1;
                back();
            }
        });

    }

    private void back(){
        Intent intent = new Intent(ImgShow.this, TakePicture.class);
        intent.putExtra("New Person", true);
        intent.putExtra("faceCount",faceCountNow+1);
        intent.putExtra("path", Pathnow);
        startActivity(intent);
        finish();
    }

    private void tellUser(String message) {
        Toast toast = Toast.makeText(this, message, Toast.LENGTH_LONG);
        toast.show();
    }
}

package com.universityofsouthampton.enriquemarquez.attempt2opencv;


import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import com.google.android.gms.appindexing.Action;
import com.google.android.gms.appindexing.AppIndex;
import com.google.android.gms.appindexing.Thing;
import com.google.android.gms.common.api.GoogleApiClient;

public class NewPerson extends Activity {

    private Button add;
    private EditText nickname;
    private String[] selectedImagePath;
    private ImageView img;
    private GoogleApiClient client;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_new_person);

        add = (Button) findViewById(R.id.button_add);
        nickname = (EditText) findViewById(R.id.textfield_nickname);
        img = (ImageView) findViewById(R.id.imageview);

        //add the nickname and picture to database
        add.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addToDatabase();
                tellUser(nickname.getText().toString() + " has been added");
                //end the activity
                onBackPressed();
            }
        });

        initialise();
        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        client = new GoogleApiClient.Builder(this).addApi(AppIndex.API).build();
    }

    //show the image taken
    private void initialise() {
        selectedImagePath = getIntent().getStringExtra("path").split(" ");
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inScaled = false;
        Bitmap[] bmp = new Bitmap[selectedImagePath.length];
        for (int i = 0; i < selectedImagePath.length; i++) {
            bmp[i] = BitmapFactory.decodeFile(selectedImagePath[i], options);
            img.setImageBitmap(bmp[i]);
        }
        //Bitmap bmp = BitmapFactory.decodeFile(selectedImagePath, options);

        checkFaceIsCorrect();
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



    private void addToDatabase() {
        String name = nickname.getText().toString();
        DatabaseOperations dop = new DatabaseOperations(this);
        for (int i = 1; i < selectedImagePath.length; i++) {
            dop.putInformation(dop, name, selectedImagePath[i], null, null);
        }
    }



    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    public void onDestroy() {
        super.onDestroy();
    }

    public void onBackPressed() {
        Intent intent = new Intent(NewPerson.this, MainActivity.class);
        intent.putExtra("training",false);
        startActivity(intent);
        finish();
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
        super.onStop();// ATTENTION: This was auto-generated to implement the App Indexing API.
// See https://g.co/AppIndexing/AndroidStudio for more information.
        AppIndex.AppIndexApi.end(client, getIndexApiAction());

        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        client.disconnect();
    }

    /**
     * ATTENTION: This was auto-generated to implement the App Indexing API.
     * See https://g.co/AppIndexing/AndroidStudio for more information.
     */
    public Action getIndexApiAction() {
        Thing object = new Thing.Builder()
                .setName("NewPerson Page") // TODO: Define a title for the content shown.
                // TODO: Make sure this auto-generated URL is correct.
                .setUrl(Uri.parse("http://[ENTER-YOUR-URL-HERE]"))
                .build();
        return new Action.Builder(Action.TYPE_VIEW)
                .setObject(object)
                .setActionStatus(Action.STATUS_TYPE_COMPLETED)
                .build();
    }

    @Override
    public void onStart() {
        super.onStart();

        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        client.connect();
        AppIndex.AppIndexApi.start(client, getIndexApiAction());
    }
}

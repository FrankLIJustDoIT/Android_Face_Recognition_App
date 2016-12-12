package com.universityofsouthampton.enriquemarquez.attempt2opencv;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Bitmap;
import android.util.Log;

import com.universityofsouthampton.enriquemarquez.attempt2opencv.Table.TableInfo;

import org.opencv.core.Mat;

import java.io.ByteArrayOutputStream;

/**
 * Created by Adam on 30/04/2015.
 */
public class DatabaseOperations extends SQLiteOpenHelper {

    public static final int database_version = 1;
    public String CREATE_QUERY = "CREATE TABLE " + TableInfo.TABLE_NAME + "(" + TableInfo.NICKNAME
            + " TEXT, " + TableInfo.FILE_PATH + " TEXT, " + TableInfo.HISTOGRAM + " TEXT, "
            + TableInfo.CENTRES + " BLOB );";

    public DatabaseOperations(Context context) {
        super(context, TableInfo.DATABASE_NAME, null, database_version);
        Log.e("Database operations", "Database created");
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_QUERY);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

    }

    public void putInformation(DatabaseOperations dop, String nickname, String filepath,
                               int[] histogram, Mat centres) {
        SQLiteDatabase SQ = dop.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(TableInfo.NICKNAME, nickname);
        cv.put(TableInfo.FILE_PATH, filepath);

        if(centres != null) {
            byte[] blobCentres = serializeMat(centres);
            cv.put(TableInfo.CENTRES, blobCentres);
        }
        SQ.insert(TableInfo.TABLE_NAME, null, cv);
    }

    public Cursor getInformation(DatabaseOperations dop) {
        SQLiteDatabase SQ = dop.getReadableDatabase();
        String[] columns = {TableInfo.NICKNAME, TableInfo.FILE_PATH, TableInfo.HISTOGRAM, TableInfo.CENTRES};
        Cursor CR = SQ.query(TableInfo.TABLE_NAME, columns, null, null, null, null, null);
        return CR;
    }

    public Cursor getNickname(DatabaseOperations dop, String filepath) {
        SQLiteDatabase SQ = dop.getReadableDatabase();
        String selection = TableInfo.FILE_PATH + " LIKE ?";
        String[] columns = {TableInfo.NICKNAME};
        String[] args = {filepath};
        Cursor CR = SQ.query(TableInfo.TABLE_NAME, columns, selection, args, null, null, null);
        return CR;
    }



    public void removeAll(DatabaseOperations dop) {
        SQLiteDatabase SQ = dop.getWritableDatabase();
        SQ.delete(TableInfo.TABLE_NAME, null, null);
    }


    public static byte[] serializeMat(Mat m) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        Bitmap bmp = Bitmap.createBitmap(m.width(),m.height(),Bitmap.Config.ARGB_8888);
        bmp.compress(Bitmap.CompressFormat.PNG, 100, bos);
        byte[] byteArray = bos.toByteArray();

        return  byteArray;
    }


}

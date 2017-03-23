package com.universityofsouthampton.enriquemarquez.attempt2opencv;

import android.provider.BaseColumns;

public class Table {

    public Table() {

    }

    public static abstract class TableInfo implements BaseColumns {
        public static final String NICKNAME = "nickname";
        public static final String FILE_PATH = "file_path";
        public static final String HISTOGRAM = "histogram";
        public static final String CENTRES = "centres";

        public static final String DATABASE_NAME = "faces";
        public static final String TABLE_NAME = "face_info";
    }
}

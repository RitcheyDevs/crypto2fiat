package com.example.solanto.cryptofiat.db;

import android.provider.BaseColumns;

/**
 * Created by Solanto on 05/08/2017.
 */

public class TaskContract {
    public static final String DB_NAME = "com.example.solanto.cryptofiat.db";
    public static final int DB_VERSION = 1;
    public class TaskEntry implements BaseColumns {
        public static final String TABLE = "pairs";
        public static final String COL_PAIR_TITLE = "pair_ticker";
    }
}

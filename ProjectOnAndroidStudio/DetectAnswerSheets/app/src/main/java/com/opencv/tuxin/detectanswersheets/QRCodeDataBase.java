package com.opencv.tuxin.detectanswersheets;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.widget.Toast;

public class QRCodeDataBase extends SQLiteOpenHelper {

    private Context mContext;
    private String CREATE_QR_CODE_INFO = "create table qr_code_info("
            + "id integer primary key autoincrement,"
            + "user_name text, "            /// 用户名
            + "test_name text, "            /// 考试名字
            + "question_count integer, "      /// 问题数量
            + "correct_answers text)";       /// 正确的答案
    public QRCodeDataBase(Context context, String name, SQLiteDatabase.CursorFactory factory, int version) {
        super(context, name, factory, version);
        mContext = context;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_QR_CODE_INFO);
        Toast.makeText(mContext, "Create succeeded", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("drop table if exists qr_code_info");
    }
}

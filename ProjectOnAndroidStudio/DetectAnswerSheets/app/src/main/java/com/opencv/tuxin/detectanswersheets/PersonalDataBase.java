package com.opencv.tuxin.detectanswersheets;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.widget.Toast;

public class PersonalDataBase extends SQLiteOpenHelper {
    private Context mContext;
    private String CREATE_INFO_STUDENT = "create table info_student("
            + "id integer primary key autoincrement,"
            + "test_name text, "           /// 考试的名字
            + "student_number text, " /// 学生的学号
            + "image_path text, "     /// 图片的储存地址
            + "error_index text, "    /// 错题的序号
            + "student_answers text, "    /// 对应错题序号下，学生的选择
            + "correct_answers text)";   /// 对应错题序号下，正确的答案

    public PersonalDataBase(Context context, String name, SQLiteDatabase.CursorFactory factory, int version) {
        super(context, name, factory, version);
        mContext = context;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_INFO_STUDENT);
        Toast.makeText(mContext, "Create succeeded", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("drop table if exists info_student");
    }
}


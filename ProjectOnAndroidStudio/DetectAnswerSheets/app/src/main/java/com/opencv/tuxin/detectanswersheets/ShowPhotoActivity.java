package com.opencv.tuxin.detectanswersheets;

import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TabHost;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class ShowPhotoActivity extends AppCompatActivity {
    private ImageView showWarpPicture;
    private ImageView showErrorAnswersPicture;
    private ImageView showCorrectAnswersPicture;
    private Bitmap errorAnswersPicture;
    private Bitmap correctAnswersPicture;
    private Bitmap warpPicture;

    private Button btnPopSave;

    private ProgressBar progressBar;
    private TabHost tabHost;
    private String[] tabHostTabSpec = {"showWarpPicture","showErrorAnswersPicture","showCorrectAnswersPicture"};

    protected static String imgPath;
    private int[] studentNumbers = new int[8];
    protected int[] correctAnswers = new int[AnswerSheetBase.SUM_OF_QUESTIONS];
    private int[] studentAnswers = new int[AnswerSheetBase.SUM_OF_QUESTIONS];
    private AnswerSheetBase answerSheetBase = new AnswerSheetBase();
    private PersonalDataBase  db;

    private String txStudentNumber = "can't found";

    public static final int MENU_CHECK_STU_NUM = Menu.FIRST;
    public static final int MENU_SAVE_DATA = Menu.FIRST + 1;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_show_photo);

        showWarpPicture = (ImageView) findViewById(R.id.showDetectPicture);
        progressBar = (ProgressBar)findViewById(R.id.progressBar);
        /// 设置 tabHost
        tabHost = (TabHost)findViewById(R.id.myTabHost);
        tabHost.setup();
        /// 添加监听器
        tabHost.addTab(tabHost.newTabSpec(tabHostTabSpec[0]).setIndicator("显示分数").setContent(R.id.showDetectPicture));
        tabHost.addTab(tabHost.newTabSpec(tabHostTabSpec[1]).setIndicator("显示错题").setContent(R.id.showErrorAnswersPicture));
        tabHost.addTab(tabHost.newTabSpec(tabHostTabSpec[2]).setIndicator("显示答案").setContent(R.id.showCorrectAnswersPicture));

        db = new PersonalDataBase(this,"infoPersonal.db",null,1);

    }

    /// 创建 menu 按键
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(Menu.NONE,MENU_CHECK_STU_NUM,Menu.NONE,R.string.checkStudentNumber);
        menu.add(Menu.NONE,MENU_SAVE_DATA,Menu.NONE,R.string.saveData);
        return super.onCreateOptionsMenu(menu);
    }

    /// 给 menu 键添加监听器
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        txStudentNumber = "";
        for (int i = 0; i < studentNumbers.length; i++){
            if (studentNumbers[i] == -1 || studentNumbers[i] == -2){
                txStudentNumber += "x";
            } else
                txStudentNumber += studentNumbers[i];
        }

        switch (item.getItemId()){
            /// 查看检测到的学号
            case MENU_CHECK_STU_NUM :
                Toast toast = Toast.makeText(getApplicationContext(), txStudentNumber, Toast.LENGTH_SHORT);
                toast.show();
                break;
            /// 储存数据
            case MENU_SAVE_DATA :
                //    通过LayoutInflater来加载一个xml的布局文件作为一个View对象
                //    设置我们自己定义的布局文件作为弹出框的Content
                final AlertDialog.Builder builder = new AlertDialog.Builder(ShowPhotoActivity.this);
                final LayoutInflater inflater = ShowPhotoActivity.this.getLayoutInflater();
                View view = inflater.inflate(R.layout.dialog_save_data, null);

                builder.setTitle("数据储存");
                builder.setView(view);


                final AlertDialog alertDialog = builder.create();
                alertDialog.setCanceledOnTouchOutside(false);
                alertDialog.show();

                final EditText etExamName = (EditText) view.findViewById(R.id.etExamName);
                final EditText etStudentNumber = (EditText)view.findViewById(R.id.etStudentNumber);
                final CheckBox cbSaveCorrPic = (CheckBox)view.findViewById(R.id.cbSaveCorrPic);
                final CheckBox cbSaveErrPic = (CheckBox)view.findViewById(R.id.cbSaveErrPic);
                Button btnSave = (Button)view.findViewById(R.id.btnSave);
                Button btnCancel = (Button)view.findViewById(R.id.btnCancel);

                etExamName.setText("test");
                etExamName.setSelection("test".length());
                etStudentNumber.setText(txStudentNumber.toCharArray(), 0, txStudentNumber.length());
                cbSaveCorrPic.setChecked(true);
                /// 设置保存按钮的监听器。我们先读取 edit 和 checkbox 的信息，
                /// 然后判断这些信息是否已经保存，如果已经保存，toast 一个信息，
                /// 如果没有保存，我们就把数据保存在 infoStudent.db 的
                /// info_student 的表格中。
                btnSave.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        /// 声明 PersonalDataBase 的 info_student 表格里的所有变量
                        String exam = null;
                        String student_number = null;
                        String image_path = null;
                        String error_index = "";
                        String student_answers = "";
                        String correct_answers = "";

                        /// 为变量赋值
                        /// 从 edit 中得到 exam 和 student_number
                        exam = String.valueOf(etExamName.getText());
                        student_number = String.valueOf(etStudentNumber.getText());
                        image_path = imgPath;

                        for (int i = 0; i < studentAnswers.length; i++) {
                            if (studentAnswers[i] != correctAnswers[i]) {
                                error_index += (i + 1) + ",";
                                student_answers += studentAnswers[i] + ",";
                                correct_answers += correctAnswers[i] + ",";
                            }
                        }

                        if (error_index.length() > 2) {
                            error_index = error_index.substring(0, error_index.length() - 1);
                            student_answers = student_answers.substring(0, student_answers.length() - 1);
                            correct_answers = correct_answers.substring(0, correct_answers.length() - 1);
                        } else if (error_index.length() == 0) {
                            error_index = "No error";
                            student_answers = "No error";
                            correct_answers = "No error";
                        }
                        /*Log.e(AnswerSheetBase.TAG, "exam = " + exam);
                        Log.e(AnswerSheetBase.TAG, "student_number = " + student_number);
                        Log.e(AnswerSheetBase.TAG, "image_path = " + image_path);
                        Log.e(AnswerSheetBase.TAG, "error_index = " + error_index);
                        Log.e(AnswerSheetBase.TAG, "student_answers = " + student_answers);
                        Log.e(AnswerSheetBase.TAG, "correct_answers = " + correct_answers);*/

                        ///******数据库操作*******
                        /// 查看数表格 info_student 中 exam 列，是否已经存在同样的 exam
                        SQLiteDatabase database = db.getWritableDatabase();
                        Cursor cursor = database.query("info_student", null,
                                "exam like ?", new String[]{exam}, null, null, null);
                        /// 如果已经存在 exam，查找 student_number
                        /// 如果 student_number 也已经存在，我们判定该条数据已经储存过了。
                        if (cursor.moveToFirst()) {///找得到游标，说明 exam 的数据已经存在
                            boolean isFound = false;
                            String db_student_number;
                            /// 查找包含 exam 所有数据里的 student_numbers
                            do {
                                db_student_number = cursor.getString(cursor.getColumnIndex("student_number"));
                                /// 如果 student_number 已经存在，toast，并跳出循环
                                if (db_student_number.equals(student_number)) {
                                    isFound = true;
                                    Toast toast = Toast.makeText(getApplicationContext(),
                                            "数据已经存在", Toast.LENGTH_SHORT);
                                    toast.show();
                                    break;
                                }
                            } while (cursor.moveToNext());
                            /// 之前的 do..while 如果没有找到一样的 student_number，说明
                            /// 数据还没有存在，我们可以新建一条。如果找到一样的 student_number，
                            /// isFound = true，此时 student_number 是等于 db_student_number 的，所以
                            /// 不会执行接下来的操作，即插入一条数据。
                            if (!isFound){
                                ContentValues values = new ContentValues();
                                values.put("exam", exam);
                                values.put("student_number", student_number);
                                values.put("image_path", image_path);
                                values.put("error_index", error_index);
                                values.put("student_answers", student_answers);
                                values.put("correct_answers", correct_answers);
                                database.insert("info_student", null, values);
                                Toast toast = Toast.makeText(getApplicationContext(),
                                        "储存成功", Toast.LENGTH_SHORT);
                                toast.show();
                                //alertDialog.dismiss();
                            }
                        } else { /// cursor 无法找到 exam 这个字符，说明第一次建立关于该 exam 的数据
                            ContentValues values = new ContentValues();
                            values.put("exam", exam);
                            values.put("student_number", student_number);
                            values.put("image_path", image_path);
                            values.put("error_index", error_index);
                            values.put("student_answers", student_answers);
                            values.put("correct_answers", correct_answers);
                            database.insert("info_student", null, values);
                            Toast toast = Toast.makeText(getApplicationContext(),
                                            "储存成功", Toast.LENGTH_SHORT);
                            toast.show();
                            //alertDialog.dismiss();
                        }
                        cursor.close();
                        ///****** 结束数据库操作 ******
/*
                        ///****** checkbox 判定 *****
                        /// 存储 correctPicture
                        if (cbSaveCorrPic.isChecked()){
                            File file = new File(Environment.getExternalStorageDirectory() +
                                    "/DetectAnswerSheet/Image/student_" + student_number + "_corr.png");
                            try {

                                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                                correctAnswersPicture.compress(Bitmap.CompressFormat.PNG,100,bytes);

                                file.createNewFile();
                                FileOutputStream fo = new FileOutputStream(file);
                                fo.write(bytes.toByteArray());
                                fo.close();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }

                        /// 存储 correctPicture
                        if (cbSaveErrPic.isChecked()){
                            File file = new File(Environment.getExternalStorageDirectory() +
                                    "/DetectAnswerSheet/Image/student_" + student_number + "_err.png");

                            try {

                                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                                errorAnswersPicture.compress(Bitmap.CompressFormat.PNG, 100, bytes);

                                file.createNewFile();
                                FileOutputStream fo = new FileOutputStream(file);
                                fo.write(bytes.toByteArray());
                                fo.close();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }

                        }
*/

                        ///****** 结束 checkbox 判定 *****
                    }
                });/// end OnclickListener of btnSave
                btnCancel.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        alertDialog.dismiss();
                    }
                });

                break;
            default:
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
   protected void onResume() {
       super.onResume();
       /// 得到 warpPicture 需要一定的时间，我们在这里开进程，
       /// 就不用阻塞 UI 进程，可以让 progressBar 转起来
       MyTask myTask = new MyTask();
       myTask.execute();

       tabHost.setOnTabChangedListener(new TabHost.OnTabChangeListener() {
           public void onTabChanged(String tabId) {
               if (tabId.equals(tabHostTabSpec[0])) {
                   Toast toast = Toast.makeText(getApplicationContext(), "现在是" + tabId + "标签", Toast.LENGTH_SHORT);
                   toast.show();
               } else if (tabId.equals(tabHostTabSpec[1])) {
                   showErrorAnswersPicture = (ImageView) findViewById(R.id.showErrorAnswersPicture);
                   showErrorAnswersPicture.setImageBitmap(errorAnswersPicture);
               } else{
                   showCorrectAnswersPicture = (ImageView)findViewById(R.id.showCorrectAnswersPicture);
                   showCorrectAnswersPicture.setImageBitmap(correctAnswersPicture);
               }
           }
       });
   }
    private class MyTask extends AsyncTask<Void, Void, Void>{
        @Override
        protected Void doInBackground(Void... params) {
            /// 从 Intent 中得到 imgPath
            Intent intent = getIntent();
            imgPath = intent.getStringExtra("imgPath");
            /// 给 AnswerSheetBase 设置 imgPath （必须）
            answerSheetBase.setImgPath(imgPath);

            /// 得到检测后的图片
            warpPicture = answerSheetBase.getWarpPicture();

            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            progressBar.setVisibility(View.INVISIBLE);
            showWarpPicture.setImageBitmap(warpPicture);
            /// 再开一个进程，避免几个比较耗时的调用放在一起，虽然
            /// 之后的 OnTabChangeListener 里用到了 studentAnswers 相关的东西，
            /// 但是通常来说，没等我们开始按按钮，进程就能完成了，所以这样设置没问题。
            TaskGetAnswers taskGetAnswers = new TaskGetAnswers();
            taskGetAnswers.execute();

        }
    }

    private class TaskGetAnswers extends AsyncTask<Void,Void,Void>{
        @Override
        protected Void doInBackground(Void... params) {
            /// 设置正确答案
            for (int i = 0; i < AnswerSheetBase.SUM_OF_QUESTIONS; i++)
                correctAnswers[i] = i % 4 + 1;
            answerSheetBase.setCorrectAnswers(correctAnswers);
            /// 得到错题的图片
            errorAnswersPicture = answerSheetBase.getErrorAnswersPicture();
            /// 得到正确选项的图片
            correctAnswersPicture = answerSheetBase.getCorrectAnswersPicture();
            /// 得到学生的学号
            studentNumbers = answerSheetBase.getStudentNumbers();
            /// 得到学生的答案
            studentAnswers = answerSheetBase.getStudentAnswers();
            //for (int i = 0; i < studentNumbers.length; i++)
            //    Log.e(AnswerSheetBase.TAG,"studentNumber" + i + " = " + studentNumbers[i]);


            return null;
        }
    }
}

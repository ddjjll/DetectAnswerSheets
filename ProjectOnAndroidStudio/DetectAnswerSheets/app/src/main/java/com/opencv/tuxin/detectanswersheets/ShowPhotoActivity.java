package com.opencv.tuxin.detectanswersheets;

import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ProgressBar;
import android.widget.TabHost;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.sql.Array;
import java.util.Arrays;

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

    private String txStudentNumber = "can't find";
    boolean find = false;

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

        db = new PersonalDataBase(this,"a.db",null,1);
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

        for (int i = 0; i < studentNumbers.length; i++){
            if (studentNumbers[i] != -1){
                find = true;
            }
        }
        if (find){
            txStudentNumber = "";
            for (int i = 0; i < studentNumbers.length; i++)
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
                AlertDialog.Builder alertDialog = new AlertDialog.Builder(ShowPhotoActivity.this);
                final LayoutInflater inflater = ShowPhotoActivity.this.getLayoutInflater();
                View view = inflater.inflate(R.layout.dialog_save_data,null);

                alertDialog.setView(view);

                final EditText etExamName = (EditText) view.findViewById(R.id.etExamName);
                final EditText etStudentNumber = (EditText)view.findViewById(R.id.etStudentNumber);
                CheckBox cbSaveCorrPic = (CheckBox)view.findViewById(R.id.cbSaveCorrPic);
                CheckBox cbSaveErrPic = (CheckBox)view.findViewById(R.id.cbSaveErrPic);
                Button btnSave = (Button)view.findViewById(R.id.btnSave);
                Button btnCancel = (Button)view.findViewById(R.id.btnCancel);

                etStudentNumber.setText(txStudentNumber.toCharArray(),0,txStudentNumber.length());
                cbSaveCorrPic.setChecked(true);
                btnSave.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        /// 声明 PersonalDataBase 的 info_student 表格里的所有变量
                        String exam = null;
                        String student_number = null;
                        String image_path = null;
                        String error_index = null;
                        String student_answers = null;
                        String correct_answers = null;

                        /// 为变量赋值
                        /// 从 edit 中得到 exam 和 student_number
                        exam = String.valueOf(etExamName.getText());
                        student_number =  String.valueOf(etStudentNumber.getText());
                        image_path = imgPath;
                        /// 把学生的答案、正确答案等，转为 String
                        for (int i = 0; i < studentAnswers.length; i++) {
                            student_answers += studentAnswers[i] + "";
                            Log.e(AnswerSheetBase.TAG,"studentAnswer" + i + " = " + studentAnswers[i]);
                        }
                        for (int i = 0; i < correctAnswers.length; i++) {
                            correct_answers += correctAnswers[i] + "";
                            Log.e(AnswerSheetBase.TAG,"correctAnswer" + i + " = " + correctAnswers[i]);
                        }


                        //db.getReadableDatabase();
                        //ContentValues values = new ContentValues();
                    }
                });
                alertDialog.show();

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

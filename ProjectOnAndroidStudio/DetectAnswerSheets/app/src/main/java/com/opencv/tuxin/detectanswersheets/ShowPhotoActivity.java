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
import android.text.Html;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TabHost;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;


public class ShowPhotoActivity extends AppCompatActivity {
    private ImageView[] showWarpPicture = new ImageView[AnswerSheetBase.MAX_PICTURE];
    private ImageView[] showErrAnsPic = new ImageView[AnswerSheetBase.MAX_PICTURE];
    private ImageView[] showCorrAnsPic = new ImageView[AnswerSheetBase.MAX_PICTURE];
    LinearLayout.LayoutParams layoutParams;
    private Bitmap[] errorAnswersPicture = new Bitmap[AnswerSheetBase.MAX_PICTURE];
    private Bitmap[] correctAnswersPicture = new Bitmap[AnswerSheetBase.MAX_PICTURE];
    private Bitmap[] warpPicture = new Bitmap[AnswerSheetBase.MAX_PICTURE];
    private Bitmap qrCodePicture;

    private Button btnPopSave;
    private ProgressBar progressBar;
    private TabHost tabHost;
    private EditText[] etTestName = new EditText[AnswerSheetBase.MAX_PICTURE];
    private EditText[] etStudentNumber = new EditText[AnswerSheetBase.MAX_PICTURE];
    private CheckBox[] cbSaveCorrPic = new CheckBox[AnswerSheetBase.MAX_PICTURE];
    private CheckBox[] cbSaveErrPic = new CheckBox[AnswerSheetBase.MAX_PICTURE];
    private TextView[] tvSaveCorrPic = new TextView[AnswerSheetBase.MAX_PICTURE];
    private TextView[] tvSaveErrPic = new TextView[AnswerSheetBase.MAX_PICTURE];
    private TextView[] tvTestName = new TextView[AnswerSheetBase.MAX_PICTURE];
    private TextView[] tvStudentNumber = new TextView[AnswerSheetBase.MAX_PICTURE];

    private String[] tabHostTabSpec = {"showWarpPicture", "showErrAnsPic", "showCorrAnsPic"};
    private static String imgPath;
    private int[][] studentNumbers = new int[AnswerSheetBase.MAX_PICTURE][8];
    protected int[][] correctAnswers = new int[AnswerSheetBase.MAX_PICTURE][];
    private int[][] studentAnswers = new int[AnswerSheetBase.MAX_PICTURE][];
    private String[] testName = new String[AnswerSheetBase.MAX_PICTURE];
    private String[] userName = new String[AnswerSheetBase.MAX_PICTURE];
    private AnswerSheetBase answerSheetBase = new AnswerSheetBase();
    private PersonalDataBase personalDataBase;
    static boolean lastShow = true;
    public static final int MENU_CHECK_STU_NUM = Menu.FIRST;
    public static final int MENU_SAVE_DATA = Menu.FIRST + 1;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_show_photo);
        this.setTitle("寻找中");
        progressBar = (ProgressBar) findViewById(R.id.progressBar);
        /// 设置 tabHost
        tabHost = (TabHost) findViewById(R.id.myTabHost);
        tabHost.setup();
        /// 添加监听器
        tabHost.addTab(tabHost.newTabSpec(tabHostTabSpec[0]).setIndicator("显示图片").setContent(R.id.showDetectPicture));
        tabHost.addTab(tabHost.newTabSpec(tabHostTabSpec[1]).setIndicator("显示错题").setContent(R.id.showErrAnsPic));
        tabHost.addTab(tabHost.newTabSpec(tabHostTabSpec[2]).setIndicator("显示答案").setContent(R.id.showCorrAnsPic));
        
        personalDataBase = new PersonalDataBase(this, "infoPersonal.db", null, 1);
        layoutParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT);
        layoutParams.weight = 1;

        showCorrAnsPic[0] = (ImageView) findViewById(R.id.showCorrAnsPic);
        showCorrAnsPic[1] = (ImageView) findViewById(R.id.showSecCorrAnsPic);
        showErrAnsPic[0] = (ImageView) findViewById(R.id.showErrAnsPic);
        showErrAnsPic[1] = (ImageView) findViewById(R.id.showSecErrAnsPic);
        showWarpPicture[0] = (ImageView) findViewById(R.id.showDetectPicture);
        showWarpPicture[1] = (ImageView) findViewById(R.id.showSecDetPic);
    }

    protected void onResume() {
        super.onResume();
        /// 得到 warpPicture 需要一定的时间，我们在这里开进程，
        /// 就不用阻塞 UI 进程，可以让 progressBar 转起来
        TaskGetWarpPicture taskGetWarpPicture = new TaskGetWarpPicture();
        taskGetWarpPicture.execute();

        tabHost.setOnTabChangedListener(new TabHost.OnTabChangeListener() {
            public void onTabChanged(String tabId) {
                if (tabId.equals(tabHostTabSpec[0])) {
                    showWarpPicture[0].setVisibility(View.VISIBLE);
                    showWarpPicture[1].setVisibility(View.VISIBLE);

                    showErrAnsPic[0].setVisibility(View.INVISIBLE);
                    showErrAnsPic[1].setVisibility(View.INVISIBLE);

                    showCorrAnsPic[0].setVisibility(View.INVISIBLE);
                    showCorrAnsPic[1].setVisibility(View.INVISIBLE);
                } else if (tabId.equals(tabHostTabSpec[1])) { /// 选择显示错题标签
                    showWarpPicture[0].setVisibility(View.INVISIBLE);
                    showWarpPicture[1].setVisibility(View.INVISIBLE);

                    showCorrAnsPic[0].setVisibility(View.INVISIBLE);
                    showCorrAnsPic[1].setVisibility(View.INVISIBLE);
                    /// 判断找到的答题卡张数
                    switch (answerSheetBase.answerSheetCount) {
                        case 1:
                            /// 如果只有一张，就直接 setImageBitmap
                            //showErrAnsPic[0] = (ImageView) findViewById(R.id.showErrAnsPic);
                            showErrAnsPic[0].setImageBitmap(errorAnswersPicture[0]);
                            break;
                        case 2:
                            /// 如果有两张，要先把 layout 的 weight 属性设置为一，这样就可以显示两张图片
                            //showErrAnsPic[0] = (ImageView) findViewById(R.id.showErrAnsPic);
                            //showErrAnsPic[1] = (ImageView) findViewById(R.id.showSecErrAnsPic);

                            showErrAnsPic[0].setLayoutParams(layoutParams);
                            showErrAnsPic[1].setLayoutParams(layoutParams);

                            showErrAnsPic[0].setVisibility(View.VISIBLE);
                            showErrAnsPic[1].setVisibility(View.VISIBLE);
                            showErrAnsPic[0].setImageBitmap(errorAnswersPicture[0]);
                            showErrAnsPic[1].setImageBitmap(errorAnswersPicture[1]);
                            break;
                        default:
                    }
                } else {/// 选择显示正确答案标签
                    showWarpPicture[0].setVisibility(View.INVISIBLE);
                    showWarpPicture[1].setVisibility(View.INVISIBLE);

                    showErrAnsPic[0].setVisibility(View.INVISIBLE);
                    showErrAnsPic[1].setVisibility(View.INVISIBLE);

                    switch (answerSheetBase.answerSheetCount) {
                        case 1:
                            /// 如果只有一张，就直接 setImageBitmap
                            //showCorrAnsPic[0] = (ImageView)findViewById(R.id.showCorrAnsPic);
                            showCorrAnsPic[0].setImageBitmap(correctAnswersPicture[0]);
                            break;
                        case 2:
                            /// 如果有两张，要先把 layout 的 weight 属性设置为一，这样就可以显示两张图片
                            //showCorrAnsPic[0] = (ImageView)findViewById(R.id.showCorrAnsPic);
                            //showCorrAnsPic[1] = (ImageView) findViewById(R.id.showSecCorrAnsPic);

                            showCorrAnsPic[0].setLayoutParams(layoutParams);
                            showCorrAnsPic[1].setLayoutParams(layoutParams);

                            showCorrAnsPic[0].setVisibility(View.VISIBLE);
                            showCorrAnsPic[1].setVisibility(View.VISIBLE);

                            showCorrAnsPic[0].setImageBitmap(correctAnswersPicture[0]);
                            showCorrAnsPic[1].setImageBitmap(correctAnswersPicture[1]);
                            break;
                        default:
                    }
                }
            }
        });
    }

    /* 得到 warpPicture 需要一定时间，所以开一个进程
     * 进行处理，处理完成之后，把 Progressbar 设为不可见。
     * 然后可以显示 warpPicture。
     * 得到 warpPicture，就可以接着用得到的 warpPicture 进行
     * 错误答案检测，和显示正确答案等操作。
     */
    private class TaskGetWarpPicture extends AsyncTask<Void, Void, Void>{
        @Override
        protected Void doInBackground(Void... params) {

            /// 从 Intent 中得到 imgPath
            Intent intent = getIntent();
            imgPath = intent.getStringExtra("imgPath");
            /// 给 AnswerSheetBase 设置 imgPath （必须）
            answerSheetBase.setImgPath(imgPath);
            /// 查看检测到的图片的结果是否正确。
            /// 如果正确，检查二维码解码的内容: 查看 separated 的
            /// 第一个参数（user_name）和第二个参数（test_name），对比已有的
            /// 数据库（createAnswerSheet 时创建），看看是否存在，如果存在，
            /// 直接调用结果，如果不存在，说明出错。
            if (answerSheetBase.TYPE_ERROR == AnswerSheetBase.NO_ERROR) {
                String resultDecode;
                switch (answerSheetBase.answerSheetCount){
                    case 1:
                        /// 得到 warpPicture
                        warpPicture[0] = answerSheetBase.getWarpPicture(0);
                        /// 解码
                        resultDecode = answerSheetBase.getDecodeResult(0);
                        Log.e(AnswerSheetBase.TAG, "resultDecode = " + resultDecode);
                        /// 解码的结果来查找 correctAnswers
                        correctAnswers[0] = getCorrectAnswer(resultDecode, 0);
                        /// 设置正确答案
                        answerSheetBase.setCorrectAnswers(correctAnswers[0],null);
                        //qrCodePicture = answerSheetBase.getQRCodePicture(0);
                        break;
                    case 2:
                        /// 得到 warpPicture
                        warpPicture[0] = answerSheetBase.getWarpPicture(0);
                        warpPicture[1] = answerSheetBase.getWarpPicture(1);
                        /// 解码、得到 correctAnswers
                        resultDecode = answerSheetBase.getDecodeResult(0);
                        Log.e(AnswerSheetBase.TAG, "resultDecode = " + resultDecode);
                        correctAnswers[0] = getCorrectAnswer(resultDecode, 0);

                        resultDecode = answerSheetBase.getDecodeResult(1);
                        Log.e(AnswerSheetBase.TAG, "resultDecode = " + resultDecode);
                        correctAnswers[1] = getCorrectAnswer(resultDecode, 1);
                        /// 设置正确答案
                        answerSheetBase.setCorrectAnswers(correctAnswers[0],correctAnswers[1]);
                        //qrCodePicture = answerSheetBase.getQRCodePicture(1);*/
                        break;
                    default:
                }
            }
            return null;
        }
        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            progressBar.setVisibility(View.INVISIBLE);
            /// 确定 answerSheetBase 类检测到图片的错误类型，
            /// 根据不同的错误，放不一样的图片
            /// 未完成，需要用不同的图片区分不同的错误类型
            switch (answerSheetBase.TYPE_ERROR){
                case AnswerSheetBase.NO_ERROR:
                    switch (answerSheetBase.answerSheetCount) {
                        case 1:
                            ShowPhotoActivity.this.setTitle(testName[0]);
                            showWarpPicture[0].setImageBitmap(warpPicture[0]);
                            //showWarpPicture[0].setImageBitmap(qrCodePicture);

                            /// 得到学生的学号
                            studentNumbers[0] = answerSheetBase.getStudentNumbers(0);
                            Log.e(AnswerSheetBase.TAG,"student length = " + studentAnswers[0]);
                            /// 得到学生的答案
                            studentAnswers[0] = answerSheetBase.getStudentAnswers(0);
                            /// 得到错题的图片
                            errorAnswersPicture[0] = answerSheetBase.getErrorAnswersPicture(0);
                            /// 得到正确选项的图片
                            correctAnswersPicture[0] = answerSheetBase.getCorrectAnswersPicture(0);
                            break;
                        case 2:
                            ShowPhotoActivity.this.setTitle(Html.fromHtml("<small>"
                                    +testName[0] + " + " + testName[1] + "</small>"));
                            /// layoutParams.weight = 1 => 垂直平分
                            showWarpPicture[0].setLayoutParams(layoutParams);
                            showWarpPicture[1].setLayoutParams(layoutParams);
                            showWarpPicture[0].setImageBitmap(warpPicture[0]);
                            showWarpPicture[1].setImageBitmap(warpPicture[1]);
                            /// 得到第一张的相关图片和信息
                            studentNumbers[0] = answerSheetBase.getStudentNumbers(0);
                            studentAnswers[0] = answerSheetBase.getStudentAnswers(0);
                            errorAnswersPicture[0] = answerSheetBase.getErrorAnswersPicture(0);
                            correctAnswersPicture[0] = answerSheetBase.getCorrectAnswersPicture(0);

                            /// 得到第二张的相关图片和信息
                            studentNumbers[1] = answerSheetBase.getStudentNumbers(1);
                            studentAnswers[1] = answerSheetBase.getStudentAnswers(1);
                            errorAnswersPicture[1] = answerSheetBase.getErrorAnswersPicture(1);
                            correctAnswersPicture[1] = answerSheetBase.getCorrectAnswersPicture(1);
                            break;
                        default:
                    }
                    break;
                default:
                    showWarpPicture[0].setImageResource(R.drawable.sorry);
                    Log.e(AnswerSheetBase.TAG, "Type error = " + AnswerSheetBase.TYPE_ERROR);
                    break;
            }
        }
    }

    private int[] getCorrectAnswer(String resultDecode, int numberOfResult){
        ArrayList<Integer> correctAnswers = new ArrayList<Integer>();
        //int[] correctAnswers = new int[];
        int questionCount = 0;
        /// @correctAnswersDB => 对应数据库里的正确答案
        String correctAnswersDB;
        /// @separated => 根据“:”分离解码的内容
        String[] separated;
        if (!resultDecode.equals("null")) {
            separated = resultDecode.split(":");
            userName[numberOfResult] = separated[0];
            testName[numberOfResult] = separated[1];
            /**************数据库操作**************/
            QRCodeDataBase qrCodeDataBase = new QRCodeDataBase(ShowPhotoActivity.this, "QRCodeInfo.db", null, 1);
            SQLiteDatabase database = qrCodeDataBase.getWritableDatabase();
            Cursor cursor = database.query("qr_code_info", null, "test_name like ?",
                    new String[]{testName[numberOfResult]}, null, null, null);
            if (cursor.moveToFirst()) {///找得到游标，说明 testName 的数据已经存在
                /// 找到了同样的 testName, 但是 user_name 可能不一样，
                /// 所以再查看 user_name。
                /// database 中的 userName
                Log.e(AnswerSheetBase.TAG, "testName = " + testName[numberOfResult]);
                String userNameDB;
                boolean hasSameUserName = false;
                do {
                    /// 这里虽然用不到 correctAnswerDB，但是我们也同样读取，
                    /// 这样如果之后查找 userName 也一样的话，我们就直接读取
                    /// correctAnswerDB，避免再重复一次 do{}while();
                    userNameDB = cursor.getString(cursor.getColumnIndex("user_name"));
                    correctAnswersDB = cursor.getString(cursor.getColumnIndex("correct_answers"));
                    questionCount = cursor.getInt(cursor.getColumnIndex("question_count"));

                    if (userName[numberOfResult].equals(userNameDB)) {
                        hasSameUserName = true;
                        break;
                    }
                } while (cursor.moveToNext());
                /// 如果 testName 一样，但是 userName 不一样，
                /// 可能是在同一手机内，存在多个用户。我们默认，只有创建
                /// 过该二维码的用户，能够读取创建时的数据。
                if (!hasSameUserName) {
                    answerSheetBase.TYPE_ERROR = AnswerSheetBase.ERR_NO_DATA;
                    correctAnswers.add(0);
                } else {/// 如果testName 也一样，我们可以读取该正确答案
                    Log.e(AnswerSheetBase.TAG, "correctAnswerDB= " + correctAnswersDB);
                    Log.e(AnswerSheetBase.TAG, "questionCount = " + questionCount);
                    for (int i = 0; i < questionCount; i++) {
                        /// charAt(i) 得到的是 char 类型，查看 ascii 表格，
                        /// 把 char 转为 int，我们需要减 48。
                        correctAnswers.add(i, correctAnswersDB.charAt(i) - 48);
                        //Log.e(AnswerSheetBase.TAG,"correctAnswer[" + i + "] = " + correctAnswers[i]);
                    }
                }/// 结束用户名检测
            }/// 结束游标寻找
            /**************结束数据库操作**************/
        } else { /// 与已有数据库对比，发现没有该数据
            answerSheetBase.TYPE_ERROR = AnswerSheetBase.ERR_NO_DATA;
        }

        int[] answerReturn = new int[correctAnswers.size()];
        for (int i = 0; i < correctAnswers.size(); i++){
            answerReturn[i] = correctAnswers.get(i);
        }
        correctAnswers.clear();
        return answerReturn;
    }

    /// 创建 menu 按键
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(Menu.NONE, MENU_SAVE_DATA, Menu.NONE, R.string.saveData);
        return super.onCreateOptionsMenu(menu);
    }

    /// 给 menu 键添加监听器
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        String[] txStudentNumber = new String[]{"", ""};
        for (int i = 0; i < studentNumbers[0].length; i++) {
            if (studentNumbers[0][i] == -1 || studentNumbers[0][i] == -2) {
                txStudentNumber[0] += "x";
            } else
                txStudentNumber[0] += studentNumbers[0][i];

            if (studentNumbers[1][i] == -1 || studentNumbers[1][i] == -2) {
                txStudentNumber[1] += "x";
            } else
                txStudentNumber[1] += studentNumbers[1][i];
        }
        switch (item.getItemId()) {
            /// 储存数据
            case MENU_SAVE_DATA:
                /// 通过LayoutInflater来加载一个xml的布局文件作为一个View对象
                /// 设置我们自己定义的布局文件作为弹出框的 Content
                final AlertDialog.Builder builder = new AlertDialog.Builder(ShowPhotoActivity.this);
                final LayoutInflater inflater = ShowPhotoActivity.this.getLayoutInflater();
                View view = inflater.inflate(R.layout.dialog_save_data, null);
                builder.setTitle("数据储存");
                builder.setView(view);

                final AlertDialog alertDialog = builder.create();
                alertDialog.setCanceledOnTouchOutside(false);
                alertDialog.show();
                Button btnSave = (Button) view.findViewById(R.id.btnSave);
                Button btnCancel = (Button) view.findViewById(R.id.btnCancel);

                etTestName[0] = (EditText) view.findViewById(R.id.etTestName1);
                etStudentNumber[0] = (EditText) view.findViewById(R.id.etStudentNumber1);
                cbSaveCorrPic[0] = (CheckBox) view.findViewById(R.id.cbSaveCorrPic1);
                cbSaveErrPic[0] = (CheckBox) view.findViewById(R.id.cbSaveErrPic1);

                etTestName[1] = (EditText) view.findViewById(R.id.etTestName2);
                etStudentNumber[1] = (EditText) view.findViewById(R.id.etStudentNumber2);
                cbSaveCorrPic[1] = (CheckBox) view.findViewById(R.id.cbSaveCorrPic2);
                cbSaveErrPic[1] = (CheckBox) view.findViewById(R.id.cbSaveErrPic2);
                tvTestName[1] = (TextView) view.findViewById(R.id.tvTestName2);
                tvStudentNumber[1] = (TextView) view.findViewById(R.id.tvStudentNumber2);

                tvSaveCorrPic[0] = (TextView) view.findViewById(R.id.tvSaveCorrPic1);
                tvSaveErrPic[0] = (TextView) view.findViewById(R.id.tvSaveErrPic1);
                tvSaveErrPic[0].setText("保存 " + testName[0] + " 的错题图片");
                tvSaveCorrPic[0].setText("保存 " + testName[0] + " 的正确答案图片");

                tvSaveCorrPic[1] = (TextView) view.findViewById(R.id.tvSaveCorrPic2);
                tvSaveErrPic[1] = (TextView) view.findViewById(R.id.tvSaveErrPic2);
                tvSaveErrPic[1].setText("保存 " + testName[1] + " 的错题图片");
                tvSaveCorrPic[1].setText("保存 " + testName[1] + " 的正确答案图片");

                switch (answerSheetBase.answerSheetCount) {
                    case 1:
                        etTestName[0].setText(testName[0]);
                        etTestName[0].setSelection(testName[0].length());
                        etStudentNumber[0].setText(txStudentNumber[0].toCharArray(), 0, txStudentNumber[0].length());
                        break;
                    case 2:
                        etTestName[0].setText(testName[0]);
                        etTestName[0].setSelection(testName[0].length());
                        etStudentNumber[0].setText(txStudentNumber[0].toCharArray(), 0, txStudentNumber[0].length());

                        etTestName[1].setText(testName[1]);
                        etTestName[1].setSelection(testName[1].length());
                        etStudentNumber[1].setText(txStudentNumber[1].toCharArray(), 0, txStudentNumber[1].length());

                        /// 这些 view 的 visibility 都是 gone，所以要设置为 visible
                        etTestName[1].setVisibility(View.VISIBLE);
                        etStudentNumber[1].setVisibility(View.VISIBLE);
                        cbSaveCorrPic[1].setVisibility(View.VISIBLE);
                        cbSaveErrPic[1].setVisibility(View.VISIBLE);
                        tvSaveCorrPic[1].setVisibility(View.VISIBLE);
                        tvSaveErrPic[1].setVisibility(View.VISIBLE);
                        tvTestName[1].setVisibility(View.VISIBLE);
                        tvStudentNumber[1].setVisibility(View.VISIBLE);
                        break;
                }

                /// 设置保存按钮的监听器。我们先读取 edit 和 checkbox 的信息，
                /// 然后判断这些信息是否已经保存，如果已经保存，toast 一个信息，
                /// 如果没有保存，我们就把数据保存在 infoStudent.personalDataBase 的
                /// info_student 的表格中。
                btnSave.setOnClickListener(btnSaveOnClickListener);/// end OnclickListener of btnSave
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

    View.OnClickListener btnSaveOnClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            /// 声明 PersonalDataBase 的 info_student 表格里的所有变量
            String[] test_name = new String[]{"", ""};
            String[] student_number = new String[]{"", ""};
            String image_path;
            String[] error_index = new String[]{"", ""};
            String[] student_answers = new String[]{"", ""};
            String[] correct_answers = new String[]{"", ""};
            image_path = imgPath;
            /// 从 edit 中得到 exam 和 student_number
            test_name[0] = String.valueOf(etTestName[0].getText());
            student_number[0] = String.valueOf(etStudentNumber[0].getText());
            switch (answerSheetBase.answerSheetCount) {
                case 1:
                    /// 用学生的答案和正确的答案做比较，得到错题的序号，和对应题号的学生的选项和正确的选项。
                    /// 然后在利用逗号来分开每一个序号/选项。
                    for (int i = 0; i < correctAnswers[0].length; i++) {
                        if (studentAnswers[0][i] != correctAnswers[0][i]) {
                            error_index[0] += (i + 1) + ",";
                            student_answers[0] += studentAnswers[0][i] + ",";
                            correct_answers[0] += correctAnswers[0][i] + ",";
                        }
                    }
                    /// 如果有一个错题，就至少会有 2 个长度的 string（序号和逗号），
                    /// 如果有错题，我们把得到的 string 去掉最后的逗号，
                    /// 如果没有错题，写上 No error
                    if (error_index[0].length() > 2) {
                        error_index[0] = error_index[0].substring(0, error_index[0].length() - 1);
                        student_answers[0] = student_answers[0].substring(0, student_answers[0].length() - 1);
                        correct_answers[0] = correct_answers[0].substring(0, correct_answers[0].length() - 1);
                    } else if (error_index[0].length() == 0) {
                        error_index[0] = "No error";
                        student_answers[0] = "No error";
                        correct_answers[0] = "No error";
                    }
                    /// 数据库操作
                    optionMenuSaveData(test_name[0], student_number[0], image_path, error_index[0],
                            student_answers[0], correct_answers[0]);
                    break;
                case 2:
                    /// 从 edit 中得到 exam 和 student_number
                    test_name[1] = String.valueOf(etTestName[1].getText());
                    student_number[1] = String.valueOf(etStudentNumber[1].getText());
                    /// 用学生的答案和正确的答案做比较，得到错题的序号，和对应题号的学生的选项和正确的选项。
                    /// 然后在利用逗号来分开每一个序号/选项。
                    for (int i = 0; i < correctAnswers[0].length; i++) {
                        if (studentAnswers[0][i] != correctAnswers[0][i]) {

                            error_index[0] += (i + 1) + ",";
                            student_answers[0] += studentAnswers[0][i] + ",";
                            correct_answers[0] += correctAnswers[0][i] + ",";
                        }
                    }
                    if (error_index[0].length() > 2) {
                        error_index[0] = error_index[0].substring(0, error_index[0].length() - 1);
                        student_answers[0] = student_answers[0].substring(0, student_answers[0].length() - 1);
                        correct_answers[0] = correct_answers[0].substring(0, correct_answers[0].length() - 1);
                    } else if (error_index[0].length() == 0) {
                        error_index[0] = "No error";
                        student_answers[0] = "No error";
                        correct_answers[0] = "No error";
                    }

                    Log.e(AnswerSheetBase.TAG, "exam = " + test_name[0]);
                    Log.e(AnswerSheetBase.TAG, "student_number = " + student_number[0]);
                    Log.e(AnswerSheetBase.TAG, "image_path = " + image_path);
                    Log.e(AnswerSheetBase.TAG, "error_index = " + error_index[0]);
                    Log.e(AnswerSheetBase.TAG, "student_answers = " + student_answers[0]);
                    Log.e(AnswerSheetBase.TAG, "correct_answers = " + correct_answers[0]);

                    for (int i = 0; i < correctAnswers[0].length; i++) {
                        if (studentAnswers[1][i] != correctAnswers[1][i]) {
                            error_index[1] += (i + 1) + ",";
                            student_answers[1] += studentAnswers[1][i] + ",";
                            correct_answers[1] += correctAnswers[1][i] + ",";
                        }
                    }
                    if (error_index[1].length() > 2) {
                        error_index[1] = error_index[1].substring(0, error_index[1].length() - 1);
                        student_answers[1] = student_answers[1].substring(0, student_answers[1].length() - 1);
                        correct_answers[1] = correct_answers[1].substring(0, correct_answers[1].length() - 1);
                    } else if (error_index[1].length() == 0) {
                        error_index[1] = "No error";
                        student_answers[1] = "No error";
                        correct_answers[1] = "No error";
                    }
                    optionMenuSaveData(test_name[0], student_number[0], image_path, error_index[0],
                            student_answers[0], correct_answers[0]);
                    optionMenuSaveData(test_name[1], student_number[1], image_path, error_index[1],
                            student_answers[1], correct_answers[1]);
                    break;
                default:
            }
            /*Log.e(AnswerSheetBase.TAG, "exam = " + test_name[0]);
            Log.e(AnswerSheetBase.TAG, "student_number = " + student_number[0]);
            Log.e(AnswerSheetBase.TAG, "image_path = " + image_path);
            Log.e(AnswerSheetBase.TAG, "error_index = " + error_index[0]);
            Log.e(AnswerSheetBase.TAG, "student_answers = " + student_answers[0]);
            Log.e(AnswerSheetBase.TAG, "correct_answers = " + correct_answers[0]);*/
            /// 开一个进程来储存图片。未完成，虽然开了进程，但是要检测
            /// checkBox，这个操作来自 UI 线程，所以只能把储存过程放到
            /// onPostExecute 里面，这样还是会阻塞 UI 线程。
            /// 是否可以通过 handler 的方法来实现？？？？？？？？
            TaskSavePictures taskSavePictures = new TaskSavePictures();
            //taskSavePictures.onPostExecute(student_number);
        }
    };

    private void optionMenuSaveData(String test_name, String student_number, String image_path,
                                    String error_index, String student_answers, String correct_answers) {
        /**************数据库操作**************/
        /// 查看数表格 info_student 中 exam 列，是否已经存在同样的 exam
        SQLiteDatabase database = personalDataBase.getWritableDatabase();
        Cursor cursor = database.query("info_student", null,
                "test_name like ?", new String[]{test_name}, null, null, null);
        /// 如果已经存在 exam，查找 student_number
        /// 如果 student_number 也已经存在，我们判定该条数据已经储存过了。
        if (cursor.moveToFirst()) {///找得到游标，说明 exam 的数据已经存在
            boolean isFoundStudentNumber = false;
            String db_student_number;
            /// 查找包含 exam 所有数据里的 student_numbers
            do {
                db_student_number = cursor.getString(cursor.getColumnIndex("student_number"));
                /// 如果 student_number 已经存在，toast，并跳出循环
                if (db_student_number.equals(student_number)) {
                    isFoundStudentNumber = true;
                    Toast toast = Toast.makeText(getApplicationContext(),
                            test_name + " 数据已经存在", Toast.LENGTH_SHORT);
                    toast.show();
                    break;
                }
            } while (cursor.moveToNext());
            /// 之前的 do..while 如果没有找到一样的 student_number，说明
            /// 数据还没有存在，我们可以新建一条。如果找到一样的 student_number，
            /// isFoundStudentNumber = true，此时 student_number 是等于
            /// db_student_number 的，所以不会执行接下来的操作，即插入一条数据。
            if (!isFoundStudentNumber) {
                ContentValues values = new ContentValues();
                values.put("exam", test_name);
                values.put("student_number", student_number);
                values.put("image_path", image_path);
                values.put("error_index", error_index);
                values.put("student_answers", student_answers);
                values.put("correct_answers", correct_answers);
                database.insert("info_student", null, values);
                Toast toast = Toast.makeText(getApplicationContext(),
                        test_name + " 数据储存成功", Toast.LENGTH_SHORT);
                toast.show();
                //alertDialog.dismiss();
            }
        } else { /// cursor 无法找到 exam 这个字符，说明第一次建立关于该 exam 的数据
            ContentValues values = new ContentValues();
            values.put("test_name", test_name);
            values.put("student_number", student_number);
            values.put("image_path", image_path);
            values.put("error_index", error_index);
            values.put("student_answers", student_answers);
            values.put("correct_answers", correct_answers);
            database.insert("info_student", null, values);
            Toast toast = Toast.makeText(getApplicationContext(),
                    test_name + " 数据储存成功", Toast.LENGTH_SHORT);
            toast.show();
            //alertDialog.dismiss();
        }
        cursor.close();
        /**************结束数据库操作**************/
    }
    private class TaskSavePictures extends AsyncTask<Void, Void, String[]> {

        @Override
        protected String[] doInBackground(Void... params) {
            return new String[0];
        }

        @Override
        protected void onPostExecute(String[] student_number) {
            super.onPostExecute(student_number);

            /// 存储 correctPicture
            if (cbSaveCorrPic[0].isChecked()){
                /// 未完成，如果传进来的学生号是一样的，但是考试名字不一样，将会覆盖图片
                File file = new File(Environment.getExternalStorageDirectory() +
                        "/DetectAnswerSheet/Image/student_" + student_number[0] + "_corr.png");
                try {
                    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                    correctAnswersPicture[0].compress(Bitmap.CompressFormat.PNG,100,bytes);

                    file.createNewFile();
                    FileOutputStream fo = new FileOutputStream(file);
                    fo.write(bytes.toByteArray());
                    fo.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                Toast.makeText(ShowPhotoActivity.this,"第一个学生的 corrPic 储存成功",Toast.LENGTH_SHORT).show();
            }

            /// 存储 errorPicture
            if (cbSaveErrPic[0].isChecked()){
                /// 未完成，如果传进来的学生号是一样的，但是考试名字不一样，将会覆盖图片
                File file = new File(Environment.getExternalStorageDirectory() +
                        "/DetectAnswerSheet/Image/student_" + student_number + "_err.png");
                try {
                    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                    errorAnswersPicture[0].compress(Bitmap.CompressFormat.PNG, 100, bytes);

                    file.createNewFile();
                    FileOutputStream fo = new FileOutputStream(file);
                    fo.write(bytes.toByteArray());
                    fo.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                Toast.makeText(ShowPhotoActivity.this,"第一个学生的 errPic 储存成功",Toast.LENGTH_SHORT).show();
            }

            /// 存储 correctPicture
            if (cbSaveCorrPic[1].isChecked()){
                /// 未完成，如果传进来的学生号是一样的，但是考试名字不一样，将会覆盖图片
                File file = new File(Environment.getExternalStorageDirectory() +
                        "/DetectAnswerSheet/Image/student_" + student_number[0] + "_corr.png");
                try {
                    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                    correctAnswersPicture[1].compress(Bitmap.CompressFormat.PNG,100,bytes);

                    file.createNewFile();
                    FileOutputStream fo = new FileOutputStream(file);
                    fo.write(bytes.toByteArray());
                    fo.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            /// 存储 errorPicture
            if (cbSaveErrPic[1].isChecked()){
                /// 未完成，如果传进来的学生号是一样的，但是考试名字不一样，将会覆盖图片
                File file = new File(Environment.getExternalStorageDirectory() +
                        "/DetectAnswerSheet/Image/student_" + student_number + "_err.png");
                try {
                    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                    errorAnswersPicture[1].compress(Bitmap.CompressFormat.PNG, 100, bytes);

                    file.createNewFile();
                    FileOutputStream fo = new FileOutputStream(file);
                    fo.write(bytes.toByteArray());
                    fo.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }

            }
        }
    }
}
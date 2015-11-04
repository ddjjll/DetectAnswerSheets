package com.opencv.tuxin.detectanswersheets;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TabHost;
import android.widget.Toast;

public class ShowPhotoActivity extends AppCompatActivity {

    private ImageView showWarpPicture;
    private ImageView showErrorAnswersPicture;
    private ImageView showCorrectAnswersPicture;
    private static String imgPath;
    private Bitmap errorAnswersPicture;
    private Bitmap correctAnswersPicture;
    private Bitmap warpPicture;
    private TabHost tabHost;
    private ProgressBar progressBar;
    private String[] tabHostTabSpec = {"showWarpPicture","showErrorAnswersPicture","showCorrectAnswersPicture"};
    private int[] studentNumber = new int[8];
    protected int[] correctAnswers = new int[AnswerSheetBase.SUM_OF_QUESTIONS];
    private AnswerSheetBase answerSheetBase = new AnswerSheetBase();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_show_photo);

        showWarpPicture = (ImageView) findViewById(R.id.showDetectPicture);
        progressBar = (ProgressBar)findViewById(R.id.progressBar);
        /// 设置 tabHost
        tabHost = (TabHost)findViewById(R.id.myTabHost);
        tabHost.setup();

        tabHost.addTab(tabHost.newTabSpec(tabHostTabSpec[0]).setIndicator("显示分数").setContent(R.id.showDetectPicture));
        tabHost.addTab(tabHost.newTabSpec(tabHostTabSpec[1]).setIndicator("显示错题").setContent(R.id.showErrorAnswersPicture));
        tabHost.addTab(tabHost.newTabSpec(tabHostTabSpec[2]).setIndicator("显示答案").setContent(R.id.showCorrectAnswersPicture));
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

            studentNumber = answerSheetBase.getStudentNumber();


            return null;
        }
    }
}

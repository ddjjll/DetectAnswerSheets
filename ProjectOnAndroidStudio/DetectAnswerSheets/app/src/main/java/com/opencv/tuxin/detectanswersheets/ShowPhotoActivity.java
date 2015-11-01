package com.opencv.tuxin.detectanswersheets;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TabHost;
import android.widget.Toast;

public class ShowPhotoActivity extends Activity {


    private ImageView showDetectPicture;
    private ImageView errorAnswersPicture;
    private Bitmap warpImage;
    private static String imgPath;
    private TabHost tabHost;
    private String[] tabHostTabSpec = {"showDetectPicture","showErrorAnswersPicture","showCorrectAnswersPicture"};
    int[] studentAnswers = new int[AnswerSheetBase.SUM_OF_QUESTIONS];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_show_photo);

        showDetectPicture = (ImageView) findViewById(R.id.showDetectPicture);

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
        /// 从 Intent 中得到 imgPath
        Intent intent = getIntent();
        imgPath = intent.getStringExtra("imgPath");
        AnswerSheetBase answerSheetBase = new AnswerSheetBase();

        answerSheetBase.setImgPath(imgPath);
        warpImage = answerSheetBase.getResultImage();
        showDetectPicture.setImageBitmap(warpImage);

        studentAnswers = answerSheetBase.getStudentAnswers();
        for (int i = 0; i < 54 ; i++)
            Log.e(AnswerSheetBase.TAG, "studentAnswers"+ (i + 1) + " = " + studentAnswers[i]);

        tabHost.setOnTabChangedListener(new TabHost.OnTabChangeListener() {
            public void onTabChanged(String tabId) {
                if (tabId.equals(tabHostTabSpec[0])) {
                    Toast toast = Toast.makeText(getApplicationContext(), "现在是" + tabId + "标签", Toast.LENGTH_SHORT);
                    toast.show();
                } else if (tabId.equals(tabHostTabSpec[1])) {
                    errorAnswersPicture = (ImageView) findViewById(R.id.showErrorAnswersPicture);
                    errorAnswersPicture.setImageBitmap(warpImage);
                }
            }
        });
 /*       //圆
        Paint paint=new Paint();
        paint.setColor(Color.BLACK);
        paint.setStyle(Paint.Style.FILL_AND_STROKE);
        Canvas canvas=new Canvas(bitmap);
        int cx=100;
        int cy=100;
        int radius=20;
        canvas.drawCircle(cx, cy, radius, paint);
        chooseView.setImageBitmap(bitmap);

/*        //绘制字体
        Paint paint=new Paint();
        paint.setColor(Color.YELLOW);
        paint.setTextSize(40);
        paint.setTypeface(Typeface.DEFAULT_BOLD);//设置字体
        //引用外部字体
        //Typeface typeface=Typeface.createFromAsset(getAssets(), "newFont.ttf");
        //paint.setTypeface(typeface);

        Canvas canvas=new Canvas(warpImage);
        canvas.drawText("98 分", 50, 100, paint);
        showDetectPicture.setImageBitmap(warpImage);*/
    }






}

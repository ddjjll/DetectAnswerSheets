package com.opencv.tuxin.detectanswersheets;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;
import android.widget.Toast;

import com.google.zxing.WriterException;

import java.io.IOException;

public class ShowCreateAnswerSheetActivity extends AppCompatActivity {
    private ImageView showCreateAnswerSheet;
    CreateAnswerSheet answerSheet;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_show_create_answer_sheet);
        showCreateAnswerSheet = (ImageView)findViewById(R.id.showCreateAnswerSheet);

        /**************二维码操作**************/
        /// 添加二维码
        ZxingQRCode qrCode = new ZxingQRCode();
        ZxingQRCode.Builder qrCodeBuilder = new ZxingQRCode.Builder();
        Bitmap qrCodeBitmap = null;
        /// 创建二维码的信息
        String testName = "test";
        String userName = "TuXin";

        int questionCount = 54;

        qrCodeBuilder.setQuestionCount(questionCount);
        qrCodeBuilder.setTestName(testName);
        qrCodeBuilder.setUserName(userName);

        /*********数据库操作*********/
        QRCodeDataBase qrCodeDataBase = new QRCodeDataBase(this,"QRCodeInfo.db",null,1);
        SQLiteDatabase dataBase = qrCodeDataBase.getWritableDatabase();
        Cursor cursor = dataBase.query("qr_code_info", null, "test_name like ?",
                new String[]{testName}, null, null, null);

        /// 正确答案不会 encode 在二维码中，但是我们需要把正确答案
        /// 添加如对应的 database 之中。Decode 的时候，我们通过
        /// 比较 decode 得到的 test_name，在 database 中查找
        /// 对应的正确答案
        String correctAnswers = "";
        for (int i = 0; i < AnswerSheetBase.SUM_OF_QUESTIONS; i++)
            correctAnswers += (i % 4 + 1) + "";
            //correctAnswers += 1 + "" ;
        Log.e(AnswerSheetBase.TAG,"correctAnswers = " + correctAnswers);

        if (cursor.moveToFirst()) {///找得到游标，说明 testName 的数据已经存在
            /// 找到了同样的 testName, 但是 user_name 可能不一样，
            /// 所以再查看 user_name。
            /// database 中的 userName
            Toast.makeText(this, userName + " exist",Toast.LENGTH_SHORT).show();
            String userNameDB;
            boolean hasSameUserName = false;
            do{
                userNameDB = cursor.getString(cursor.getColumnIndex("user_name"));
                if (userName.equals(userNameDB)){
                    Toast.makeText(this, testName + " exist",Toast.LENGTH_SHORT).show();
                    hasSameUserName = true;
                    break;
                }
            }while (cursor.moveToNext());
            /// 如果 userName 不一样，我们新建一条数据
            if (!hasSameUserName){
                ContentValues values = new ContentValues();
                values.put("user_name",userName);
                values.put("test_name", testName);
                values.put("question_count", questionCount);
                values.put("correct_answers", correctAnswers);
                dataBase.insert("qr_code_info", null, values);
                Toast.makeText(this,testName + " has created",Toast.LENGTH_SHORT).show();
            }
        } else {///找不到游标，说明 testName 的数据不存在，直接新建一条数据
            ContentValues values = new ContentValues();
            values.put("user_name",userName);
            values.put("test_name",testName);
            values.put("question_count",questionCount);
            values.put("correct_answers",correctAnswers);
            dataBase.insert("qr_code_info", null, values);
            Toast.makeText(this,testName + " 存储成功",Toast.LENGTH_SHORT).show();
        }
        cursor.close();
            //Toast.makeText(this,"Can't found",Toast.LENGTH_SHORT).show();
        /*********结束数据库操作*********/
        try {
            qrCodeBitmap = qrCode.encode();
        } catch (WriterException e) {
            e.printStackTrace();
        }
        /**************结束二维码操作**************/

        /// 新建 AnswerSheet
        answerSheet = new CreateAnswerSheet();
        CreateAnswerSheet.Builder answerSheetBuilder = new CreateAnswerSheet.Builder();
        /// 在新建的二维码中添加 qrCode
        answerSheetBuilder.addQRCode(qrCodeBitmap);
        answerSheetBuilder.setOptionCount(questionCount);


        showCreateAnswerSheet.setImageBitmap(answerSheet.getAnswerSheet());

        /// 开一个进程，把图片存储在 SD 卡中
        MyTask myTask = new MyTask();
        myTask.execute();
    }
    private class MyTask extends AsyncTask<Void,Void,Void>{

        @Override
        protected Void doInBackground(Void... params) {
            try {
                answerSheet.saveAnswerSheetOnSDCard();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }
    }

}

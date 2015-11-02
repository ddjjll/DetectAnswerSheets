package com.opencv.tuxin.detectanswersheets;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

public class MainActivity extends Activity {
    private static final int CHOOSE_PHOTO = 1;
    private Button btnChoosePhoto;
    private Button btnCreateAnswerSheet;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        /// 创建 choosePhoto 按钮，并设置监听器
        btnChoosePhoto = (Button) findViewById(R.id.btnChoosePhoto);
        btnChoosePhoto.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent();
                intent.setType("image/*");
                intent.setAction(Intent.ACTION_GET_CONTENT);
                startActivityForResult(Intent.createChooser(intent,"Select Picture"),CHOOSE_PHOTO);
            }
        });

        /// 创建 createAnswerSheet 按钮，并设置监听器
        btnCreateAnswerSheet = (Button) findViewById(R.id.btnCreateAnswerSheet);
        btnCreateAnswerSheet.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this,ShowCreateAnswerSheetActivity.class);
                MainActivity.this.startActivity(intent);
            }
        });


    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if ((requestCode == CHOOSE_PHOTO) && (resultCode == RESULT_OK) && (data != null)){
            Uri choosePhoto = data.getData();

            String[] filePath = {MediaStore.Images.Media.DATA};
            Cursor cursor = getContentResolver().query(choosePhoto,filePath,null,null,null);
            cursor.moveToFirst();
            String imgPath = cursor.getString(cursor.getColumnIndex(filePath[0]));

            Intent intent = new Intent(MainActivity.this,ShowPhotoActivity.class);
            intent.putExtra("imgPath",imgPath);
            startActivity(intent);
        }
    }
}

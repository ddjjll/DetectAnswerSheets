package com.opencv.tuxin.detectanswersheets;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;


import java.io.File;
import java.io.IOException;

public class MainActivity extends Activity {
    private static final int CHOOSE_PHOTO = 1;
    private static final int TAKE_PHOTO = 2;
    private Button btnChoosePhoto;
    private Button btnCreateAnswerSheet;
    private Button btnSystemCamera;
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
                startActivityForResult(Intent.createChooser(intent, "Select Picture"), CHOOSE_PHOTO);
            }
        });

        /// 创建 drawAnswerSheet 按钮，并设置监听器
        btnCreateAnswerSheet = (Button) findViewById(R.id.btnCreateAnswerSheet);
        btnCreateAnswerSheet.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, ShowCreateAnswerSheetActivity.class);
                MainActivity.this.startActivity(intent);
            }
        });

        

        /// 创建 systemCamera 按钮，并设置监听器
        btnSystemCamera = (Button) findViewById(R.id.btnSystemCamera);
        btnSystemCamera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                File file = new File(Environment.getExternalStorageDirectory() +
                        "/DetectAnswerSheet/Image/tempImage1.png");
                try {
                    if (file.exists()) {
                        file.delete();
                    }
                    file.createNewFile();

                } catch (IOException e) {
                    e.printStackTrace();
                }

                Uri imageUri = Uri.fromFile(file);
                Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                intent.putExtra(MediaStore.EXTRA_OUTPUT,imageUri);
                startActivityForResult(intent, TAKE_PHOTO);
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case CHOOSE_PHOTO:
                if ((resultCode == RESULT_OK) && (data != null)) {
                Uri choosePhoto = data.getData();

                String[] filePath = {MediaStore.Images.Media.DATA};
                Cursor cursor = getContentResolver().query(choosePhoto, filePath, null, null, null);
                cursor.moveToFirst();
                String imgPath = cursor.getString(cursor.getColumnIndex(filePath[0]));

                Intent intent = new Intent(MainActivity.this, ShowPhotoActivity.class);
                intent.putExtra("imgPath", imgPath);
                startActivity(intent);
            }
                break;
            case TAKE_PHOTO:
                String imgPath = Environment.getExternalStorageDirectory() +
                        "/DetectAnswerSheet/Image/tempImage1.png";
                Intent intent = new Intent(MainActivity.this, ShowPhotoActivity.class);
                intent.putExtra("imgPath", imgPath);
                startActivity(intent);
        }
    }

}

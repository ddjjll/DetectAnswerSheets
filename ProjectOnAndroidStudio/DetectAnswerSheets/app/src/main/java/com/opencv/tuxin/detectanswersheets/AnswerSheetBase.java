package com.opencv.tuxin.detectanswersheets;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Environment;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;


public class AnswerSheetBase{
    public static final String TAG = "tuxin";
    public static final int SUM_OF_QUESTIONS = 54;

    private String imgPath;
    private GetDataFromNative dataFromNative;
    private int[] studentAnswers = new int[SUM_OF_QUESTIONS];
    private int[] resultInt;
    private int[] correctAnswers;
    protected int[] studentNumbers = null;
    private Bitmap warpPicture;
    private Bitmap correctAnswersPicture;
    private Bitmap errorAnswersPicture;
    private boolean isGetAnswer = false;

    private int wResize;
    private int hResize;

    AnswerSheetBase(){
    }
    /*  提供检测图片的 path，该函数必须第一个调用，否则将出错，
     *  有了 path 后，立即调用 getDataFromNative() 为了得
     *  到 resultInt，这是检测结果的像素值，有了它，就可以调用
     *  getWarpPicture 得到 resultBitmap、可以调用
     *  getStudentAnswers() 得到 studentAnswers。                  */
    public void setImgPath(String imgPath){
        this.imgPath = imgPath;
        getResize();
        getDataFromNative();
    }

    private void getResize(){
        /// 得到 options 属性
        BitmapFactory.Options options = new BitmapFactory.Options();
        /// 设置 inJustDecodeBounds = true 后，解析图片只解析边界大小
        options.inJustDecodeBounds = true;
        Bitmap bitmap = (Bitmap) BitmapFactory.decodeFile(imgPath,options);
        float scale;
        scale = (float) ((1600.0/options.outWidth <=  1130.0/options.outHeight)? 1600.0/options.outWidth : 1130.0/options.outHeight);
        this.wResize = (int) (options.outWidth * scale * 1.2) ;
        this.hResize = (int) (options.outHeight * scale * 1.2) ;
    }

    /*  调用 native 方法，得到 resultInt，有了该数组，就可以调用
     *  getStudentAnswers() 和 getStudentNumbers() 等函数。
     *  第一步：得到尺寸合适（1080，1920）的原图片
     *  第二步：把得到的图片的像素值转换为 int 数组。
     *  第三步：调用 native 方法，得到 GetDataFromNative 类。
     *  第四步：把 GetDataFromNative 类的像素值复制给 resultInt。*/
    private void getDataFromNative(){
        ///　先把原图　resize
        Bitmap photoResize = getResizeBitmap(wResize, hResize);
        /// 创建一个 （wResize，hResize）的数组
        int[] pix = new int[wResize * hResize];
        /// 得到 resize 图片内的像素值，结果存在 pix 内
        photoResize.getPixels(pix, 0, wResize, 0, 0, wResize, hResize);

        /// 调用 native 方法，得到 GetDataFromNative 类
        dataFromNative = getAnswerSheetInfo(pix, wResize, hResize);
        /// resultInt 是转置后的图片的像素值，利用他，可以调用 getStudentAnswers()
        /// 等方法。
        resultInt = dataFromNative.imageDataWarp;
        /// 调用 native 后，图片已经没有用，而且占了大量的内存，应该立即销毁
        photoResize.recycle();
    }

    /*  把像素值转换为 warpPicture。 未完成，需要在 res 里放入图片，说明检测失败。*/
    protected Bitmap getWarpPicture(){
        if (imgPath != null && !isImgFormPathTooSmall() && dataFromNative.isRectangle) {
            warpPicture = Bitmap.createBitmap(wResize, hResize, Bitmap.Config.ARGB_8888);
            if (dataFromNative.isRectangle == true) {
                warpPicture.setPixels(resultInt, 0, wResize, 0, 0, wResize, hResize);
            } else {
                /// 让 resultBitmap 从 res 里面载入一张图片，说明没有找到 rectangle
            }
        } else {
            /// 未完成.... 需要在 res 文件中添加一张图片说明没有设置 imgPath 或者选择的图片太小， 或者图片无法找到 rectangle
        }
        return warpPicture;
    }

    /*  重新设置图片的尺寸，因为得到的图片一般都很大，我们需要在这里
     *  先进行一次压缩，之后传到 native 的时候只需要传输较小尺寸的
     *  图片，可以节省内存。                                      */
    private Bitmap getResizeBitmap(int newWidth, int newHeight){
        /// 先简单的压缩尺寸
        Bitmap bm = getCompressImageBySize();
        /// 得到压缩后的 bitmap 的尺寸
        int width = bm.getWidth();
        int height = bm.getHeight();
        /// 计算压缩的系数
        float scaleWidth = ((float)newWidth) / width;
        float scaleHeight = ((float)newHeight) / height;
        /// 生成一个相关系数的 matrix
        Matrix matrix = new Matrix();
        matrix.postScale(scaleWidth, scaleHeight);
        /// resize 图片
        Bitmap resizeBitmap = Bitmap.createBitmap(bm,0,0,width,height,matrix,false);
        /// 释放原压缩的图片
        return resizeBitmap;
    }

    /*  按尺寸简单的压缩，执行此方法前需要调用 setImgPath(String imgPath)，
     *  以便得到原图片的地址，否则将会出错。                                  */
    private Bitmap getCompressImageBySize(){
        /// 得到 options 属性
        BitmapFactory.Options options = new BitmapFactory.Options();
        /// 设置 inJustDecodeBounds = true 后，解析图片只解析边界大小
        options.inJustDecodeBounds = true;
        Bitmap bitmap = (Bitmap) BitmapFactory.decodeFile(imgPath,options);
        /// 得到原图片的尺寸
        int w = options.outWidth;
        int h = options.outHeight;
        //Log.e(TAG, "width = " + w);
        //Log.e(TAG, "height = " + h);

        options.inSampleSize = 1;

        if (w > wResize && h > hResize){
            int widthRatio = w / wResize;
            int heightRatio = h / hResize;
            options.inSampleSize = heightRatio > widthRatio ? heightRatio : widthRatio;
        }
        /// 设为 false 再 decodeFile 就能得到新的图片
        options.inJustDecodeBounds =false;
        bitmap = (Bitmap) BitmapFactory.decodeFile(imgPath,options);
        return bitmap;
    }

    /*  设置正确答案，有了正确答案，就可以圈出哪题做错了或者圈出正确答案    */
    protected void setCorrectAnswers(int[] correctAnswers){
        this.correctAnswers = correctAnswers;
        /*for (int i = 0; i < studentAnswers.length; i++){
            Log.e(TAG,"correctAnswers" + i + " = " + correctAnswers[i]);
        }*/
    }
    /*  写一个 getter，为了外部调用方便，但是内部也需要知道 studentAnswers，
     *  所以先调用 setStudentAnswers() 为了给内部的 studentAnswers 赋值，
     *  为了防止调用两次 native 方法得到 studentAnswers，我们设置一个
     *  bool isGetAnswer，如果它为真，我们就不需要再次调用 native，如果为
     *  false，就先调用 setStudentAnswers()                          */
    protected int[] getStudentAnswers(){
        if (!isGetAnswer)
            setStudentAnswers();
        return studentAnswers;
    }
    /*  调用 native 方法，得到 studentAnswers                          */
    private void setStudentAnswers(){
        if (imgPath != null && !isImgFormPathTooSmall() && dataFromNative.isRectangle) {
            studentAnswers = getStudentAnswers(dataFromNative.imageDataWarp, wResize, hResize);
        } else { /// 如果没有检测成功
            for (int i = 0; i < SUM_OF_QUESTIONS; i++)
                studentAnswers[i] = 0;
        }
        isGetAnswer = true;
    }

    /*  得到显示错题的图片，用 studentAnswers 和 correctAnswers
     *  做比较，然后圈出不一样的题号，即错题的题号                       */
    protected Bitmap getErrorAnswersPicture(){
        //for (int i = 0; i < studentAnswers.length; i++) {
          //  Log.e(TAG, "studentAnswers" + (i + 1) + " = " + studentAnswers[i]);
        //}
        if (imgPath != null && !isImgFormPathTooSmall() && dataFromNative.isRectangle) {
            setStudentAnswers();
            /// 按质量压缩图片避免同时存在三张很大的图片
            errorAnswersPicture = compressByQuality(warpPicture).copy(Bitmap.Config.RGB_565, true);
            Canvas canvas = new Canvas(errorAnswersPicture);
            Paint paint = new Paint();
            paint.setColor(Color.RED);
            paint.setStrokeWidth(4.5f);
            paint.setStyle(Paint.Style.STROKE);
            for (int i = 0; i < correctAnswers.length; i++) {
               // Log.e(TAG,"studentAnswers" + i + " = " + studentAnswers[i] );
                if (studentAnswers[i] != correctAnswers[i]) {
                    int cols = i % 3;
                    int rows = i / 3;
                    int cx = (int) (0.0425 * wResize + 0.345 * cols * wResize);
                    int cy = (int) (0.3 * hResize + 0.0395 * rows * hResize);
                    int radius = 20;
                    canvas.drawCircle(cx, cy, radius, paint);
                }
            }
        } else { /// 如果没有检测成功
        }
        return errorAnswersPicture;
    }
    /*  得到显示正确答案的图片，每一个正确的答案，都用一个红色的矩形框起来   */
    protected Bitmap getCorrectAnswersPicture(){
        if (imgPath != null && !isImgFormPathTooSmall() && dataFromNative.isRectangle) {
            correctAnswersPicture = compressByQuality(warpPicture).copy(Bitmap.Config.RGB_565, true);
            Canvas canvas = new Canvas(correctAnswersPicture);
            Paint paint = new Paint();
            paint.setColor(Color.RED);
            paint.setStrokeWidth(4.5f);
            paint.setStyle(Paint.Style.STROKE);
            /// 在每一个正确答案上画一个矩形
            for (int i = 0; i < correctAnswers.length; i++) {
                //if (studentAnswers[i] != correctAnswers[i] ){
                int cols = i % 3;
                int rows = i / 3;
                /// 字母 a 的位置的 x 值、 y 值
                int px_letter_a = (int) (0.025 * wResize + 0.345 * cols * wResize + 0.06 * wResize);
                int py_letter_a = (int) (0.2915 * hResize + 0.0394 * rows * hResize);

                int w_letter = (int) (0.0425 * wResize);
                int h_letter = (int) (0.025 * hResize);
                int px_letter = (int) (0.055 * wResize);

                for (int j = 1; j < 5; j++) {
                    //px_letter_a += j * px_letter;
                    if (correctAnswers[i] == j)
                        canvas.drawRect(new Rect(px_letter_a + (j - 1) * px_letter,
                                py_letter_a,
                                px_letter_a + w_letter + (j - 1) * px_letter,
                                py_letter_a + h_letter), paint);

                }
               /* //查看所图取的选项
                for (int j = 1; j < 5; j++) {
                    //px_letter_a += j * px_letter;
                    if (studentAnswers[i] == j)
                        canvas.drawRect(new Rect(px_letter_a + (j - 1) * px_letter,
                                py_letter_a,
                                px_letter_a + w_letter + (j - 1) * px_letter,
                                py_letter_a + h_letter), paint);

                }*/
            }


            /// 查看学号部分的读取情况
            /*int widthNumber = (int) (wResize * 0.9 * 0.08 * 0.4);
            int heightNumber = (int) (hResize * 0.95 * 0.08 * 0.265);

            int wNumNum = (int) (wResize * 0.124 * 0.4);
            int hNumNum = (int) (hResize * 0.097 * 0.265);

            int wNumBord = (int) (wResize * 0.05 + wResize * 0.375 * 0.9);
            int hNumBord = (int) (hResize * 0.022 + hResize * 0.007 * 0.95);

            for (int i = 0; i < 80; i++) {
                int rows = i % 10;
                int cols = i / 10;
                canvas.drawRect(new Rect(wNumBord + wNumNum * cols,
                        hNumBord + hNumNum * rows,
                        wNumBord + wNumNum * cols + widthNumber,
                        hNumBord + hNumNum * rows + heightNumber), paint);
            }*/
        }else {
        ///添加说明图片
        }
        return correctAnswersPicture;
    }
    private boolean isImgFormPathTooSmall(){
        /// 得到 options 属性
        BitmapFactory.Options options = new BitmapFactory.Options();
        /// 设置 inJustDecodeBounds = true 后，解析图片只解析边界大小
        options.inJustDecodeBounds = true;
        Bitmap bitmap = (Bitmap) BitmapFactory.decodeFile(imgPath,options);

        return (options.outWidth < wResize || options.outHeight < hResize );
    }

    /*  通过压缩质量来压缩图片大小，该函数可以被外部函数直接调用。
     *  注意，调用的原图片不会被销毁，为了避免 OMM 注意不要创建过多的新图。    */
    protected Bitmap compressByQuality(Bitmap bitmap){
        Bitmap compressedBitmap = null;
        if (bitmap != null) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            int quality = 70;
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos);
            //Log.e(TAG, "质量压缩到原来的" + quality + "%时大小为：" + baos.toByteArray().length + "byte");
            compressedBitmap = BitmapFactory.decodeByteArray(baos.toByteArray(), 0, baos.toByteArray().length);
        } else {
            /// 载入图片，说明失败
            compressedBitmap = Bitmap.createBitmap(wResize, hResize, Bitmap.Config.RGB_565);
        }
        return compressedBitmap;
    }

    /*  建立一张新的答题卡，现在每次建立都是一样的，而且没有设置答题卡的
     *  名字、答题卡等信息。
     *  之后的目标：用户可以根据自己的需求调整答题卡的大小，所以我们需要
     *  输入参数，表明题目数量、答题卡名字，如果设置了二维码，还可以设置
     *  读取二维码，直接得出这套答题卡的正确答案。*/
    public static Bitmap createAnswerSheet(){
        int wResize = (int) (841);
        int hResize = (int) (1190);
        int wBorder = (int) (0.9 * wResize);
        int hBorder = (int) (0.95 * hResize);
        Bitmap bitmap = Bitmap.createBitmap(wResize,hResize, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint();
        paint.setColor(Color.BLACK);
        paint.setStrokeWidth(3.5f);
        paint.setStyle(Paint.Style.STROKE);
        canvas.drawRGB(255, 255, 255);

        /// 创建边框
        canvas.drawRect(new Rect((int)(wResize * 0.05),
                                (int)(hResize * 0.025),
                                (int)(wResize * 0.05 + wBorder),
                                (int)(hResize * 0.025  + hBorder)), paint);

        paint.setStrokeWidth(1.5f);
        /// 创建名字边框
        canvas.drawRect(new Rect((int)(wResize * 0.05 + wBorder * 0.025),
                                (int)(hResize * 0.025 + hBorder * 0.015),
                                (int)(wResize * 0.05 + wBorder * 0.325),
                                (int)(hResize * 0.025 + hBorder * 0.115)), paint);
        /// 创建二维码边框
        canvas.drawRect(new Rect((int)(wResize * 0.05 + wBorder * 0.025),
                                (int)(hResize * 0.025 + hBorder * 0.125),
                                (int)(wResize * 0.05 + wBorder * 0.325),
                                (int)(hResize * 0.025 + hBorder * 0.275)), paint);
        /// 创建学号边框
        canvas.drawRect(new Rect((int)(wResize * 0.05 + wBorder * 0.375),
                                (int)(hResize * 0.025 + hBorder * 0.015),
                                (int)(wResize * 0.05 + wBorder * 0.775),
                                (int)(hResize * 0.025 + hBorder * 0.275)), paint);
        /// 画上分割线
        for (int i = 0; i < 7; i++){
            canvas.drawLine((float)(wResize * 0.05 + wBorder * 0.425 + i * wBorder * 0.05),
                            (float)(hResize * 0.025 + hBorder * 0.015),
                            (float)(wResize * 0.05 + wBorder * 0.425 + i * wBorder * 0.05),
                            (float)(hResize * 0.025 + hBorder * 0.275),paint);
        }
        /// 创建学科边框
        canvas.drawRect(new Rect((int)(wResize * 0.05 + wBorder * 0.825),
                                (int)(hResize * 0.025 + hBorder * 0.015),
                                (int)(wResize * 0.05 + wBorder * 0.975),
                                (int)(hResize * 0.025 + hBorder * 0.275)), paint);
        ///***********写上选项和数字************

        /// 设置字母和数字的大小
        /// 先随便设置一个大小来，测出当前大小时，"[A]"字符的宽度
        final float testTextSize = 20f;
        paint.setTextSize(testTextSize);
        Rect bound = new Rect();
        String text = "[A]";
        paint.getTextBounds(text,0,text.length(),bound);
        /// 设置我们的单个选项的大小（每一行大约可容纳35个字符）/// 打印出来后感觉不太合适，之后要换一下
        float finalSize = wBorder / 35;
        /// 得到该大小的字体尺寸
        finalSize = testTextSize * finalSize/bound.width();
        paint.setTextSize(finalSize);

        paint.setStyle(Paint.Style.FILL);
        /// 去除锯齿
        paint.setAntiAlias(true);
        paint.setTypeface(Typeface.SERIF);

        /// 字母的间距
        int disLetter = (int) (wBorder * 0.055);
        /// 边框距离每行的第一个数字的长度和宽度
        int wBordNum = (int) (wResize * 0.075);
        int hBordNum = (int) (hResize * 0.055 + 0.275 * hBorder);
        /// 两个数字见的行列距离
        int wNumNum = (int) (wBorder * 0.35);
        int hNumNum = (int) (hBorder * 0.055 * 0.725);
        /// 画上选项
        for (int i = 0; i < 54; i++){
            int rows = i / 3 ;
            int cols = i % 3 ;
            canvas.drawText((i+1)+".",wBordNum + wNumNum * cols,hBordNum + hNumNum * rows,paint);
            canvas.drawText("[A]",wBordNum + wNumNum * cols + disLetter ,hBordNum + hNumNum * rows,paint);
            canvas.drawText("[B]",wBordNum + wNumNum * cols + disLetter * 2, hBordNum + hNumNum * rows,paint);
            canvas.drawText("[C]",wBordNum + wNumNum * cols + disLetter * 3, hBordNum + hNumNum * rows,paint);
            canvas.drawText("[D]",wBordNum + wNumNum * cols + disLetter * 4, hBordNum + hNumNum * rows,paint);
        }

        /// 画上学号
        for (int i = 0; i < 80; i ++){
            int rows = i / 10;
            int cols = i % 10;
            canvas.drawText("["+(i%10)+"]", (float) (wResize * 0.05 + wBorder * 0.385 + rows * wBorder * 0.05),
                                    (float) (hResize * 0.025 + hBorder * 0.04 + cols * hBorder * 0.025),
                                    paint);
        }

        return bitmap;
    }

    /*  调用 native 方法，得到学生的学号，如果某个数字检测错误，该
     *  数字会等于 -1，我们可以检测每一个数字是否大于 -1，来得到正确
     *  的结果。                                                */
    protected int[] getStudentNumbers(){
        if (this.studentNumbers == null && imgPath != null && !isImgFormPathTooSmall() && dataFromNative.isRectangle) {
            this.studentNumbers = getStudentNumbers(resultInt, wResize, hResize);
        }
        return this.studentNumbers;
    }

    /*  把创建好的答题卡写入 SD 卡中，存好之后，就可以在电脑中打开，
     *  然后用 A4 纸打印出来。
     *  目前为止，我们的程序不支持自定义答题卡，所以只存储一张固定的答题卡。
     *  如果以后支持自定义，记得定义存储的地址和不同图片的名字。*/
    protected static void saveAnswerSheetOnSDCard() throws IOException {
        Bitmap bitmap = createAnswerSheet();
        /// 之前创建的 AnswerSheet 只有 A4 纸大小的一半，所以创建一张符合 A4 纸大小的图片
        Bitmap newBitmap = Bitmap.createBitmap(bitmap.getWidth()*2,bitmap.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvasForPrint = new Canvas(newBitmap);
        canvasForPrint.drawRGB(255, 255, 255);
        /// 把半边的图像复制给另外半边
        canvasForPrint.drawBitmap(bitmap,0,0,null);
        canvasForPrint.drawBitmap(bitmap, bitmap.getWidth(), 0, null);

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        newBitmap.compress(Bitmap.CompressFormat.PNG,100,bytes);


        File file = new File(Environment.getExternalStorageDirectory() +
                                    "/DetectAnswerSheet/Image/AnswerSheet.png");
        if (file.exists()){
            file.delete();
        }
        file.createNewFile();
        FileOutputStream fo = new FileOutputStream(file);
        fo.write(bytes.toByteArray());
        fo.close();
        bitmap.recycle();
    }

    private native GetDataFromNative getAnswerSheetInfo(int[] buf, int w, int h);
    private native int[] getStudentAnswers(int[] buf,int w, int h);
    private native int[] getStudentNumbers(int[] buf,int w, int h);
    static {
        System.loadLibrary("opencvDetect");
    }
}

package com.opencv.tuxin.detectanswersheets;

import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.Log;
import android.widget.LinearLayout;

import java.io.ByteArrayOutputStream;


public class AnswerSheetBase extends Application{
    public static final String TAG = "tuxin";
    public static final int SUM_OF_QUESTIONS = 54;
    protected static final int MAX_PICTURE = 2;

    private String imgPath = null;
    private GetDataFromNative dataFromNative;
    protected int answerSheetCount;
    private float[] scaleNewHeight = new float[MAX_PICTURE];

    private int[][] resultWarpInt = new int[MAX_PICTURE][];
    private int[][] resultQRCodeInt = new int[MAX_PICTURE][];
    private int[][] studentAnswers = new int[MAX_PICTURE][];
    private int[][] correctAnswers = new int[MAX_PICTURE][];
    protected int[][] studentNumbers = new int[MAX_PICTURE][];

    private Bitmap[] warpPicture = new Bitmap[MAX_PICTURE];
    private Bitmap[] qrCodePicture = new Bitmap[MAX_PICTURE];
    private Bitmap[] correctAnswersPicture = new Bitmap[MAX_PICTURE];
    private Bitmap[] errorAnswersPicture = new Bitmap[MAX_PICTURE];

    private boolean isGetStudentAnswer = false;

    private int wResize;
    private int hResize;
    private int qrCodeWidth = (int) (ZxingQRCode.width * 0.9);
    private int qrCodeHeight = (int) (ZxingQRCode.height * 1.3);

    protected static final int ERR_NO_PATH = 1;
    protected static final int ERR_IMAGE_TOO_SMALL = 2;
    protected static final int ERR_NO_RECTANGLE = 3;
    protected static final int ERR_NO_QR_CODE = 4;
    protected static final int ERR_NO_DATA = 5;
    protected static final int ERR_UN_KNOW_ERROR = 6;
    protected static final int ERR_MORE_THEN_MAX = 6;
    protected static final int NO_ERROR = 0;

    /// 一开始新建该类的时候，默认是没有提供 imgPath 的，
    /// 所以，我们让 TYPE_ERROR 初始化为 ERR_NO_PATH,
    /// 当我们设置了 imgPath 后，马上让 TYPE_ERROR = NO_ERROR
    protected static int TYPE_ERROR = ERR_NO_PATH;

    AnswerSheetBase(){}
    /*  提供检测图片的 path，该函数必须第一个调用，否则将出错，
     *  有了 path 后，立即调用 getDataFromNative() 为了得
     *  到 resultWarpInt，这是检测结果的像素值，有了它，就可以调用
     *  getWarpPicture 得到 resultBitmap、可以调用
     *  getStudentAnswers() 得到 studentAnswers。
     */
    public void setImgPath(String imgPath){
        this.imgPath = imgPath;
        TYPE_ERROR = NO_ERROR;
        setResize();
        getDataFromNative();
        setWarpPicture();
        setQRCodePicture();
    }
    /* 设置 wResize 和 hResize 的具体值
     * */
    private void setResize(){
        /// 得到 options 属性
        BitmapFactory.Options options = new BitmapFactory.Options();
        /// 设置 inJustDecodeBounds = true 后，解析图片只解析边界大小
        options.inJustDecodeBounds = true;
        Bitmap bitmap = (Bitmap) BitmapFactory.decodeFile(imgPath,options);
        float scale;
        scale = (float) ((1600.0/options.outWidth <=  1130.0/options.outHeight)?
                1600.0/options.outWidth : 1130.0/options.outHeight);
        scale *= 1.2;
        this.wResize = (int) (options.outWidth * scale) ;
        this.hResize = (int) (options.outHeight * scale) ;
    }

    /*  调用 native 方法，得到 resultWarpInt，有了该数组，就可以调用
     *  getStudentAnswers() 和 getStudentNumbers() 等函数。
     *  第一步：得到尺寸合适（1080，1920）的原图片
     *  第二步：把得到的图片的像素值转换为 int 数组。
     *  第三步：调用 native 方法，得到 GetDataFromNative 类。
     *  第四步：把 GetDataFromNative 类的像素值复制给 resultWarpInt。*/
    private void getDataFromNative(){
        ///　先把原图　resize
        Bitmap photoResize = getResizeBitmap(wResize, hResize);
        /// 创建一个 （wResize，hResize）的数组
        int[] pix = new int[wResize * hResize];
        /// 得到 resize 图片内的像素值，结果存在 pix 内
        photoResize.getPixels(pix, 0, wResize, 0, 0, wResize, hResize);

        /// 调用 native 方法，得到 GetDataFromNative 类
        dataFromNative = getAnswerSheetInfo(pix, wResize, hResize, qrCodeWidth, qrCodeHeight);

        /// resultWarpInt 是转置后的图片的像素值，利用他，可以调用 getStudentAnswers()
        /// 等方法。
        answerSheetCount = dataFromNative.answerSheetCount;
        Log.e(TAG,"count = " + answerSheetCount);

        switch (answerSheetCount){
            case 1:
                resultWarpInt[0] = dataFromNative.imageDataWarp;
                resultQRCodeInt[0] = dataFromNative.imageDataQRCode;
                Log.e(TAG,"hasRectangle = " + dataFromNative.hasFirstRectangle);
                if (!dataFromNative.isGetFirstQRCode)
                    TYPE_ERROR = ERR_NO_QR_CODE;
                break;
            case 2:
                resultWarpInt[0] = dataFromNative.imageDataWarp;
                resultQRCodeInt[0] = dataFromNative.imageDataQRCode;

                resultWarpInt[1] = dataFromNative.secondImageDataWarp;
                resultQRCodeInt[1] = dataFromNative.secondImageDataQRCode;

                Log.e(TAG,"hasSecondRectangle = " + dataFromNative.hasSecondRectangle);
                Log.e(TAG,"isGetSecondQRCode = " + dataFromNative.isGetSecondQRCode);
                Log.e(TAG,"second warp length = " + resultQRCodeInt[0].length);
                if (!dataFromNative.isGetFirstQRCode && !dataFromNative.isGetSecondQRCode)
                    TYPE_ERROR = ERR_NO_QR_CODE;
                break;
            default:
                TYPE_ERROR = ERR_UN_KNOW_ERROR;
        }
        if (dataFromNative.answerSheetCount == 0)
            TYPE_ERROR = ERR_NO_RECTANGLE;

        /// 调用 native 后，图片已经没有用，而且占了大量的内存，应该立即销毁
        photoResize.recycle();
    }

    /* 先进行错误类型检测，如果检测到没有错误，
     * 就把像素值转换为 warpPicture。
     */
    protected Bitmap getWarpPicture(int numberOfPicture) throws RuntimeException{
        if (numberOfPicture > MAX_PICTURE)
            TYPE_ERROR = ERR_MORE_THEN_MAX;

        if ((TYPE_ERROR == NO_ERROR)) {
            return warpPicture[numberOfPicture];
        }
        else
            return null;
    }
    /*
     */
    private void setWarpPicture() throws RuntimeException{
        if ((TYPE_ERROR == NO_ERROR)) {
            switch (answerSheetCount){
                case 1:
                    warpPicture[0] = Bitmap.createBitmap(wResize, hResize, Bitmap.Config.ARGB_8888);
                    warpPicture[0].setPixels(resultWarpInt[0],
                            0, wResize, 0, 0, wResize, hResize);
                    break;
                case 2:
                    warpPicture[0] = Bitmap.createBitmap(wResize, hResize/2, Bitmap.Config.ARGB_8888);
                    warpPicture[0].setPixels(resultWarpInt[0],
                            0, wResize, 0, 0, wResize, hResize/2);

                    warpPicture[1] = Bitmap.createBitmap(wResize, hResize/2, Bitmap.Config.ARGB_8888);
                    warpPicture[1].setPixels(resultWarpInt[1],
                            0, wResize, 0, 0, wResize, hResize/2);
                    break;
                default:
            }
        }
        else
            warpPicture = null;
    }

    protected Bitmap getQRCodePicture(int numberOfPicture) {
        return qrCodePicture[numberOfPicture];
    }
    /* 先进行错误类型检测，如果检测到没有错误，
     * 就把像素值转换为 QRCodePicture。
     */
    protected void setQRCodePicture() {
        if (TYPE_ERROR == NO_ERROR) {
            switch (answerSheetCount){
                case 1:
                    qrCodePicture[0] = Bitmap.createBitmap(qrCodeWidth, qrCodeHeight, Bitmap.Config.ARGB_8888);
                    qrCodePicture[0].setPixels(resultQRCodeInt[0], 0, qrCodeWidth, 0, 0, qrCodeWidth, qrCodeHeight);
                    break;
                case 2:
                    qrCodePicture[0] = Bitmap.createBitmap(qrCodeWidth, qrCodeHeight, Bitmap.Config.ARGB_8888);
                    qrCodePicture[0].setPixels(resultQRCodeInt[0], 0, qrCodeWidth, 0, 0, qrCodeWidth, qrCodeHeight);

                    qrCodePicture[1] = Bitmap.createBitmap(qrCodeWidth, qrCodeHeight, Bitmap.Config.ARGB_8888);
                    qrCodePicture[1].setPixels(resultQRCodeInt[1], 0, qrCodeWidth, 0, 0, qrCodeWidth, qrCodeHeight);
                    break;
                default:
            }
        }
        else
            qrCodePicture = null;
    }

    protected String getDecodeResult(int numberOfQRCode){
        if (numberOfQRCode > MAX_PICTURE)
            TYPE_ERROR = ERR_MORE_THEN_MAX;
        String result = "null";
        if (TYPE_ERROR == NO_ERROR) {
            result = ZxingQRCode.decode(getQRCodePicture(numberOfQRCode));
        }
        return result;
    }

    /* 设计的答题卡时，以题目总数为 54 的答题卡为标准，在检测到第二张答题卡时，
     * 我们重新计算了答题卡的高。到目前为止，如果检测到两张答题卡，我们简单的把
     * 答题卡的高设置为 hResize/2，但是实际上我们现在的答题卡的高并不是标准答题卡
     * 的高 1/2，所以我们要计算出实际的比例。
     * */
    private void setScaleNewHeight(){
        if (TYPE_ERROR == NO_ERROR){
            switch (answerSheetCount){
                case 1:
                    /// 如果只找到一张答题卡，我们就不用重新设置 scale 了
                    scaleNewHeight[0] = 1;
                    break;
                case 2:
                    setScaleNewHeight(0);
                    setScaleNewHeight(1);
                    break;
                default:
            }
        }
    }
    private void setScaleNewHeight(int numberOfPicture){
        int questionCount = correctAnswers[numberOfPicture].length;
        int rawCount = (int) Math.ceil(questionCount / 3.0);
        /// 计算原来的比例（答题卡创建时的比例），为什么这么算，可以参考 CreateAnswerSheet
        float oldScale = (float) ((0.275 + 0.04 * rawCount) / (0.275 + 0.04 * 18));
        /// 0.5 是因为我们直接让 hResize/2,之后我们再减掉 0.01 是因为我们测试的时候，发现有一点误差
        if (answerSheetCount == 2)
            scaleNewHeight[numberOfPicture] = (float) (0.5 / oldScale - 0.01);
    }

    protected Bitmap getErrorAnswersPicture(int numberOfPicture){
        return errorAnswersPicture[numberOfPicture];
    }
    protected void setErrorAnswersPicture(){
        if (TYPE_ERROR == NO_ERROR){
            switch (answerSheetCount){
                case 1 :
                    setErrorAnswersPicture(0);
                    break;
                case 2 :
                    setErrorAnswersPicture(0);
                    setErrorAnswersPicture(1);
                    break;
                default:
            }
        }
    }
    /* 先进行错误检测，如果没有错误，进行错误比较。
     * 用 studentAnswers 和 correctAnswers
     * 做比较，然后圈出不一样的题号，即错题的题号。
     */
    protected void setErrorAnswersPicture(int numberOfPicture){
        if (numberOfPicture > MAX_PICTURE)
            TYPE_ERROR = ERR_MORE_THEN_MAX;

        if (TYPE_ERROR == NO_ERROR){
            if (!isGetStudentAnswer)
                setStudentAnswers();
            /// 按质量压缩图片避免同时存在三张很大的图片
            errorAnswersPicture[numberOfPicture] = compressByQuality(warpPicture[numberOfPicture])
                    .copy(Bitmap.Config.RGB_565, true);

            Canvas canvas = new Canvas(errorAnswersPicture[numberOfPicture]);
            Paint paint = new Paint();
            paint.setColor(Color.RED);
            paint.setStrokeWidth(4.5f);
            paint.setStyle(Paint.Style.STROKE);
            for (int i = 0; i < correctAnswers[numberOfPicture].length; i++) {
                if (studentAnswers[numberOfPicture][i] != correctAnswers[numberOfPicture][i]) {
                    int cols = i % 3;
                    int rows = i / 3;
                    int cx = (int) (0.0425 * wResize + 0.345 * cols * wResize);
                    int cy = (int) ((int) (0.3 * hResize + 0.0395 * rows * hResize) * scaleNewHeight[numberOfPicture]);
                    int radius = 20;
                    canvas.drawCircle(cx, cy, radius, paint);
                }
            }
        } else { /// 如果没有检测成功
            errorAnswersPicture[numberOfPicture] = null;
        }
    }

    /* 先进行错误检测，如果没有错误，画出正确答案。*/
    protected Bitmap getCorrectAnswersPicture(int numberOfPicture){
        return correctAnswersPicture[numberOfPicture];
    }
    /* 先进行错误检测，如果没有错误，画出正确答案。*/
    protected void setCorrectAnswersPicture(){
        switch (answerSheetCount){
            case 1 :
                setCorrectAnswersPicture(0);
                break;
            case 2 :
                setCorrectAnswersPicture(0);
                setCorrectAnswersPicture(1);
                break;
            default:
        }
    }
    /* 先进行错误检测，如果没有错误，画出正确答案。*/
    protected void setCorrectAnswersPicture(int numberOfPicture){
        if (numberOfPicture > MAX_PICTURE)
            TYPE_ERROR = ERR_MORE_THEN_MAX;

        if (TYPE_ERROR == NO_ERROR){
            correctAnswersPicture[numberOfPicture] = compressByQuality(warpPicture[numberOfPicture])
                    .copy(Bitmap.Config.RGB_565, true);
            Canvas canvas = new Canvas(correctAnswersPicture[numberOfPicture]);
            Paint paint = new Paint();
            paint.setColor(Color.RED);
            paint.setStrokeWidth(4.5f);
            paint.setStyle(Paint.Style.STROKE);
            /// 在每一个正确答案上画一个矩形
            for (int i = 0; i < correctAnswers[numberOfPicture].length; i++) {
                int cols = i % 3;
                int rows = i / 3;
                /// 字母 a 的位置的 x 值、 y 值
                int px_letter_a = (int) (0.025 * wResize + 0.345 * cols * wResize + 0.06 * wResize);
                int py_letter_a = (int) ((0.2915 * hResize + 0.0394 * rows * hResize) * scaleNewHeight[numberOfPicture]);

                int w_letter = (int) (0.0425 * wResize);
                int h_letter = (int) (0.025 * hResize * scaleNewHeight[numberOfPicture]);
                int px_letter = (int) (0.055 * wResize);

                for (int j = 1; j < 5; j++) {
                    //px_letter_a += j * px_letter;
                    if (correctAnswers[numberOfPicture][i] == j)
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
            correctAnswersPicture[numberOfPicture] = null;
        }
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
    /* 设置正确答案，有了正确答案，就可以圈出哪题做错了或者圈出正确答案,    */
    protected void setCorrectAnswers(int[] correctAnswers0, int[] correctAnswers1){
        this.correctAnswers[0] = correctAnswers0;
        this.correctAnswers[1] = correctAnswers1;
        for (int i = 0; i < correctAnswers.length; i++){
            Log.e(TAG,"correctAnswers" + i + " = " + this.correctAnswers[0][i]);
        }

        /// 提供了所有的正确答案后，就开始计算 scaleNewHeight
        setScaleNewHeight();
        /// 有了 scaleNewHeight，就开始画正确答案的图片
        setCorrectAnswersPicture();
        /// 有了 scaleNewHeight，就开始检测学生学号
        setStudentNumbers();
    }
    /*  写一个 getter，为了外部调用方便，但是前提是内部的 studentAnswer 已经设置好，
     *  所以先调用 setStudentAnswers() 为了给内部的 studentAnswers 赋值，
     *  为了防止调用两次 native 方法得到 studentAnswers，我们设置一个
     *  bool isGetStudentAnswer，如果它为真，我们就不需要再次调用 native，如果为
     *  false，就先调用 setStudentAnswers()                          */
    protected int[] getStudentAnswers(int numberOfAnswer){
        if (numberOfAnswer > MAX_PICTURE)
            TYPE_ERROR = ERR_MORE_THEN_MAX;
        if (!isGetStudentAnswer)
            setStudentAnswers();
        return studentAnswers[numberOfAnswer];
    }
    /* 调用 native 方法，得到 studentAnswers。
     */
    private void setStudentAnswers(){
        if (TYPE_ERROR == NO_ERROR) {
            switch (answerSheetCount){
                case 1 :
                    this.studentAnswers[0] = getStudentAnswers(resultWarpInt[0],
                                                    wResize, hResize, scaleNewHeight[0], correctAnswers[0].length);
                    isGetStudentAnswer = true;
                    break;
                case 2 :
                    this.studentAnswers[0] = getStudentAnswers(resultWarpInt[0],
                                                    wResize, hResize/2, scaleNewHeight[0], correctAnswers[0].length);
                    this.studentAnswers[1] = getStudentAnswers(resultWarpInt[1],
                                                    wResize, hResize/2,scaleNewHeight[1], correctAnswers[1].length);
                    isGetStudentAnswer = true;
                    break;
                default:
            }
        } else { /// 如果没有检测成功
            for (int i = 0; i < SUM_OF_QUESTIONS; i++) {
                this.studentAnswers[0][i] = 0;
                this.studentAnswers[1][i] = 0;
            }
        }
        /// 得到了学生的答案后，就可以开始画错误答案的图片了
        setErrorAnswersPicture();
    }

    private void isImgFormPathTooSmall(){
        /// 得到 options 属性
        BitmapFactory.Options options = new BitmapFactory.Options();
        /// 设置 inJustDecodeBounds = true 后，解析图片只解析边界大小
        options.inJustDecodeBounds = true;
        Bitmap bitmap = (Bitmap) BitmapFactory.decodeFile(imgPath,options);
        if (options.outWidth < wResize || options.outHeight < hResize)
            TYPE_ERROR = ERR_IMAGE_TOO_SMALL;
    }

    /*  通过压缩质量来压缩图片大小，该函数可以被外部函数直接调用。
     *  注意，调用的原图片不会被销毁，为了避免 OMM 注意不要创建过多的新图。    */
    protected Bitmap compressByQuality(Bitmap bitmap){
        Bitmap compressedBitmap = null;
        if (bitmap != null) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            int quality = 60;
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos);
            //Log.e(TAG, "质量压缩到原来的" + quality + "%时大小为：" + baos.toByteArray().length + "byte");
            compressedBitmap = BitmapFactory.decodeByteArray(baos.toByteArray(), 0, baos.toByteArray().length);
        } else {
            /// 载入图片，说明失败
            compressedBitmap = Bitmap.createBitmap(wResize, hResize, Bitmap.Config.RGB_565);
        }
        return compressedBitmap;
    }
    protected int[] getStudentNumbers(int numberOfStudent){
        return studentNumbers[numberOfStudent];
    }
    /*  调用 native 方法，得到学生的学号，如果某个数字检测错误，该
     *  数字会等于 -1，我们可以检测每一个数字是否大于 -1，来得到正确
     *  的结果。                                                */
    protected void setStudentNumbers(){
        if (TYPE_ERROR == NO_ERROR) {
            switch (answerSheetCount){
                case 1:
                    studentNumbers[0] = getStudentNumbers(resultWarpInt[0], wResize, hResize,scaleNewHeight[0]);
                    Log.e(TAG,"scale = " + scaleNewHeight[0]);
                    break;
                case 2:
                    studentNumbers[0] = getStudentNumbers(resultWarpInt[0], wResize, hResize/2,scaleNewHeight[0]);
                    studentNumbers[1] = getStudentNumbers(resultWarpInt[1], wResize, hResize/2,scaleNewHeight[0]);
                    break;
            }
        }
    }
    private native GetDataFromNative getAnswerSheetInfo(int[] buf, int w, int h, int w_qr_code, int h_qr_code);
    private native int[] getStudentAnswers(int[] buf,int w, int h, float scale, int questionCount);
    private native int[] getStudentNumbers(int[] buf,int w, int h, float scale);
    static {
        System.loadLibrary("opencvDetect");
    }
}

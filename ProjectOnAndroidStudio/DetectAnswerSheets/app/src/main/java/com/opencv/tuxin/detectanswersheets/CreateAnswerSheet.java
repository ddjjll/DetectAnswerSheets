package com.opencv.tuxin.detectanswersheets;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Environment;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
/*  建立一张新的答题卡，现在每次建立都是一样的，而且没有设置答题卡的
 *  名字、答题卡等信息。
 *  之后的目标：用户可以根据自己的需求调整答题卡的大小，所以我们需要
 *  输入参数，表明题目数量、答题卡名字，如果设置了二维码，还可以设置
 *  读取二维码，直接得出这套答题卡的正确答案。
 */
public class CreateAnswerSheet{
    public final static int widthA4Paper = 841;
    public final static int heightA4Paper = 1190;
    private static Builder mBuilder;
    CreateAnswerSheet(){
        mBuilder = new Builder();
    }
    private Bitmap getAnswerSheet(Builder builder){
        return builder.getResultBitmap();
    }
    protected Bitmap getAnswerSheet(){
        /// 每次创建的时候，都建好模版。未完成，因为现在的
        /// 二维码很固定，固定题目数量，固定大小，所以模版也很固定。
        /// 正常来说，大小、数量这些参数，需要通过设置 Builder 来确定。
        //mBuilder.drawBase();
        return getAnswerSheet(mBuilder);
    }

    /*  把创建好的答题卡写入 SD 卡中，存好之后，就可以在电脑中打开，
     *  然后用 A4 纸打印出来。
     *  目前为止，我们的程序不支持自定义答题卡，所以只存储一张固定的答题卡。
     *  如果以后支持自定义，记得定义存储的地址和不同图片的名字。*/
    protected void saveAnswerSheetOnSDCard() throws IOException {
        Bitmap bitmap = getAnswerSheet();
        /// 之前创建的 AnswerSheet 只有 A4 纸大小的一半，所以创建一张符合 A4 纸大小的图片
        Bitmap newBitmap = Bitmap.createBitmap(bitmap.getWidth()*2,bitmap.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvasForPrint = new Canvas(newBitmap);
        canvasForPrint.drawRGB(255, 255, 255);
        /// 把半边的图像复制给另外半边
        canvasForPrint.drawBitmap(bitmap, 0, 0, null);
        canvasForPrint.drawBitmap(bitmap, bitmap.getWidth(), 0, null);

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        newBitmap.compress(Bitmap.CompressFormat.PNG, 100, bytes);

        File file = new File(Environment.getExternalStorageDirectory() +
                "/DetectAnswerSheet/Image/AnswerSheet1.png");
        if (file.exists()){
            file.delete();
        }
        file.createNewFile();
        FileOutputStream fo = new FileOutputStream(file);
        fo.write(bytes.toByteArray());
        fo.close();
        bitmap.recycle();
    }

    /* 通过 Builder 可以设置 AnswerSheet 的大小、题目数量，
     * 也进行二维码的添加等操作。
     * 未完成，现在只能够添加二维码。
     * */
    public static class Builder {
        private static Bitmap resultBitmap ;

        private int heightBlocHead = (int) (heightA4Paper * 0.95 * 0.275);
        private static int heightBlocOption = (int) (heightA4Paper * 0.95 * 0.725);

        private int widthBorder = (int) (widthA4Paper * 0.9 );
        private static int heightBorder = (int) (0.95 * heightA4Paper);


        private static int optionCount = 54;
        private static int optionRawCount = 18;

        Builder(){
            drawWhitePaper();
        }

        public Builder setOptionCount(int count){
            optionCount = count;
            optionRawCount = (int) Math.ceil(optionCount / 3.0);

            heightBlocOption = (int) (optionRawCount * 0.04 * heightA4Paper * 0.95);
            heightBorder = heightBlocHead + heightBlocOption;
            Log.e(AnswerSheetBase.TAG,"optionRawCount = " + optionRawCount);

            drawBase(resultBitmap);
            return this;
        }

        private void drawWhitePaper(){
            resultBitmap = Bitmap.createBitmap(widthA4Paper,heightA4Paper, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(resultBitmap);
            canvas.drawRGB(255, 255, 255);
        }

        /* 为 static resultBitmap 画上模版。未完成，因为现在的二维码很固定，
         * 固定题目数量，固定大小，所以模版也很固定。正常来说，大小、数量这些参数，
         * 需要通过设置 Builder 来确定。
         * @return ：返回该类，方便链式调用。
         */
        public Builder drawBase(){
            resultBitmap = drawBase(resultBitmap);
            return this;
        }
        /* 通过 builder 在 static resultBitmap 上添加二维码。
         * @qrCodeBitmap : 用户创建的二维码
         * @return : 返回该类，方便链式调用。
         */
        public Builder addQRCode(Bitmap qrCodeBitmap){
            Canvas canvas = new Canvas(resultBitmap);
            /// 添加二维码
            canvas.drawBitmap(qrCodeBitmap, (int) (widthA4Paper * 0.05 + widthBorder * 0.015),
                    (int) (heightA4Paper * 0.025 + heightBlocHead * 0.425), null);
            Paint paint = new Paint();
            paint.setColor(Color.BLACK);
            paint.setStrokeWidth(1.5f);
            paint.setStyle(Paint.Style.STROKE);

            /// 创建二维码边框
            canvas.drawRect(new Rect((int) (widthA4Paper * 0.05 + widthBorder * 0.025),
                    (int) (heightA4Paper * 0.025 + heightBlocHead * 0.425),
                    (int) (widthA4Paper * 0.05 + widthBorder * 0.325),
                    (int) (heightA4Paper * 0.025 + heightBlocHead)), paint);

            return this;
        }
        /* 得到画好的 static resultBitmap
         * */
        protected static Bitmap getResultBitmap(){
            return resultBitmap;
        }

        /* 画上模版。未完成，因为现在的二维码很固定，固定题目数量，
         * 固定大小，所以模版也很固定。正常来说，大小、数量这些参数，
         * 需要通过设置 Builder 来确定。
         * @bitmap : 通常传入一张白纸。
         * @return : 返回模版。
         */
        private Bitmap drawBase(Bitmap bitmap){
            Canvas canvas = new Canvas(bitmap);
            Paint paint = new Paint();
            paint.setColor(Color.BLACK);
            paint.setStrokeWidth(3.5f);
            paint.setStyle(Paint.Style.STROKE);


            /// 创建边框
            canvas.drawRect(new Rect((int)(widthA4Paper * 0.05),
                    (int)(heightA4Paper * 0.025),
                    (int)(widthA4Paper * 0.05 + widthBorder),
                    (int)(heightA4Paper * 0.025  + heightBorder)), paint);

            paint.setStrokeWidth(1.5f);
            /// 创建名字边框
            canvas.drawRect(new Rect((int)(widthA4Paper * 0.05 + widthBorder * 0.025),
                    (int)(heightA4Paper * 0.025 + heightBlocHead * 0.05),
                    (int)(widthA4Paper * 0.05 + widthBorder * 0.325),
                    (int)(heightA4Paper * 0.025 + heightBlocHead * 0.4)), paint);

            /// 创建学号边框
            canvas.drawRect(new Rect((int)(widthA4Paper * 0.05 + widthBorder * 0.375),
                    (int)(heightA4Paper * 0.025 + heightBlocHead * 0.05),
                    (int)(widthA4Paper * 0.05 + widthBorder * 0.775),
                    (int)(heightA4Paper * 0.025 + heightBlocHead)), paint);
            /// 画上分割线
            for (int i = 0; i < 7; i++){
                canvas.drawLine((float)(widthA4Paper * 0.05 + widthBorder * 0.425 + i * widthBorder * 0.05),
                        (float)(heightA4Paper * 0.025 + heightBlocHead * 0.05),
                        (float)(widthA4Paper * 0.05 + widthBorder * 0.425 + i * widthBorder * 0.05),
                        (float)(heightA4Paper * 0.025 + heightBlocHead),paint);
            }
            /// 创建学科边框
            canvas.drawRect(new Rect((int)(widthA4Paper * 0.05 + widthBorder * 0.825),
                    (int)(heightA4Paper * 0.025 + heightBlocHead * 0.05),
                    (int)(widthA4Paper * 0.05 + widthBorder * 0.975),
                    (int)(heightA4Paper * 0.025 + heightBlocHead)), paint);
            ///***********写上选项和数字************

            /// 设置字母和数字的大小
            /// 先随便设置一个大小来，测出当前大小时，"[A]"字符的宽度
            final float testTextSize = 20f;
            paint.setTextSize(testTextSize);
            Rect bound = new Rect();
            String text = "[A]";
            paint.getTextBounds(text,0,text.length(),bound);
            /// 设置我们的单个选项的大小（每一行大约可容纳35个字符）/// 打印出来后感觉不太合适，之后要换一下
            float finalSize = widthBorder / 35;
            /// 得到该大小的字体尺寸
            finalSize = testTextSize * finalSize/bound.width();
            paint.setTextSize(finalSize);

            paint.setStyle(Paint.Style.FILL);
            /// 去除锯齿
            paint.setAntiAlias(true);
            paint.setTypeface(Typeface.SERIF);


            /// 字母的间距
            int disLetter = (int) (widthBorder * 0.055);
            /// 边框距离每行的第一个数字的长度和宽度
            int wBordNum = (int) (widthA4Paper * 0.075);
            int hBordNum = (int) (heightA4Paper * 0.05 + heightBlocHead);
            /// 两个数字间的行列距离
            int wNumNum = (int) (widthBorder * 0.35);
            int hNumNum = (int) (heightA4Paper * 0.0375);
            /// 画上选项
            for (int i = 0; i < optionCount; i++){
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
                canvas.drawText("["+(i%10)+"]", (float) (widthA4Paper * 0.05 + widthBorder * 0.385 + rows * widthBorder * 0.05),
                        (float) (heightA4Paper * 0.025 + heightBlocHead * 0.125 + cols * heightBlocHead * 0.0925),
                        paint);
            }
            return bitmap;
        }
    }
}

package com.opencv.tuxin.detectanswersheets;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.util.Log;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.NotFoundException;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.Result;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;



public class ZxingQRCode {
    public static final int width = (int) (CreateAnswerSheet.widthA4Paper * 0.3);
    public static final int height = (int) (CreateAnswerSheet.heightA4Paper * 0.15);

    private QRCodeDataBase qrCodeDataBase;
    private static Builder mBuilder;

    ZxingQRCode(){
        mBuilder = new Builder();
    }

    private static Bitmap encode(String data) throws WriterException {
        BitMatrix result;
        Map<EncodeHintType,Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.MARGIN, 0);
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");

        try {
            result = new MultiFormatWriter().encode(data,BarcodeFormat.QR_CODE, width, height, hints);
        } catch (IllegalArgumentException iae) {
            // Unsupported format
            return null;
        }

        int[] pixels = new int[width * height];
        for (int y = 0; y < height; y++) {
            int offset = y * width;
            for (int x = 0; x < width; x++) {
                pixels[offset + x] = result.get(x, y) ? Color.BLACK : Color.WHITE;
            }
        }
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
        return bitmap;
    }
    /* 用户直接调用，调用之前需要（可以）提前调用 ZxingCode.QRCodeInfo，来
     * 设置 QRCode 的信息。
     */
    protected Bitmap encode()throws WriterException{
        return encode(mBuilder);
    }
    private Bitmap encode(Builder builder) throws WriterException {
        String data;
        Bitmap bitmap;

        data = builder.getInfo();
        bitmap = encode(data);
        return bitmap;
    }

    protected static String decode(Bitmap image){
        String resultDecode = null;
        MultiFormatReader reader = new MultiFormatReader();

        Collection<BarcodeFormat> formats = new ArrayList<>();
        formats.add(BarcodeFormat.QR_CODE);

        Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
        hints.put(DecodeHintType.POSSIBLE_FORMATS, formats);
        hints.put(DecodeHintType.CHARACTER_SET, "UTF-8");

        reader.setHints(hints);

        int width = image.getWidth();
        int height = image.getHeight();
        int[] pixel = new int[width * height];
        image.getPixels(pixel, 0, width, 0, 0, width, height);

        RGBLuminanceSource source = new RGBLuminanceSource(width, height, pixel);
        BinaryBitmap binaryBitmap = new BinaryBitmap(new HybridBinarizer(source));

        try {
            Result qrCodeResult = reader.decodeWithState(binaryBitmap);
            resultDecode = qrCodeResult.getText();
        } catch (NotFoundException e) {
            Log.e(AnswerSheetBase.TAG,"Decode error : " + e);
            resultDecode = "null";
        } finally {
            reader.reset();
        }
        return resultDecode;
    }

    /* 这个函数提供正确的答案给二维码。此函数只是测试用，
     * 正确答案应该通过用户输入，然后通过 Intent 传过来
     */
    private int[] testSetCorrectAnswer(){
        int[] correctAnswer = new int[AnswerSheetBase.SUM_OF_QUESTIONS];
        for (int i = 0; i < AnswerSheetBase.SUM_OF_QUESTIONS; i++)
            correctAnswer[i] = i % 4 + 1;
        return correctAnswer;
    }


    public static class Builder {

        private static String testName = "test";
        private static int questionCount = 54;
        private static String userName = "TuXin";


        public Builder setTestName(String test) {
            testName = test;
            return this;
        }

        public Builder setQuestionCount(int Count) {
            questionCount = Count;
            return this;
        }

        public Builder setUserName(String user) {
            userName = user;
            return this;
        }

        public String getInfo() {
            String info = "";
            info += this.userName;
            info += ":" + this.testName;
            info += ":" + this.questionCount;

            Log.e(AnswerSheetBase.TAG, "QRCode info = " + info);
            return info;
        }
    }
}

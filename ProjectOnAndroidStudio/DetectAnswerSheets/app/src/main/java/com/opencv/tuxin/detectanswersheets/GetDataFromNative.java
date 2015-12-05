package com.opencv.tuxin.detectanswersheets;

import android.graphics.Bitmap;

/**
 * Created by Administrator on 2015/10/30.
 */
public class GetDataFromNative {
    protected int[] imageDataWarp;
    protected int[] imageDataQRCode;

    /// 正常应该把 imageDataWarp 和 imageDataQRCode 设为
    /// 二维数组，这里为了快速测试，就重新再创建两个数组。
    protected int[] secondImageDataWarp;
    protected int[] secondImageDataQRCode;

    protected int answerSheetCount;
    protected boolean hasFirstRectangle;
    protected boolean hasSecondRectangle;
    protected boolean isGetFirstQRCode;
    protected boolean isGetSecondQRCode;
    public GetDataFromNative(){}

}


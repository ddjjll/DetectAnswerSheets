#include "com_opencv_tuxin_detectanswersheets_AnswerSheetBase.h"
#include <stdio.h>
#include <Math.h>
#include <opencv2/opencv.hpp>
#include <opencv/highgui.h>
using namespace cv;
using namespace std;

#define PERCENT_OP_B 0.35
#define PERCENT_OP 0.35

Mat detect_again(Point top_left, Point top_right, Point bottom_left,
                 Point bottom_right,Mat img_reset, int answer_sheet_count);
Mat get_qr_code_image(Mat img_warp, int w_qr_code, int h_qr_code,
                      bool & is_get_qr_code, int answer_sheet_count);


JNIEXPORT jobject JNICALL Java_com_opencv_tuxin_detectanswersheets_AnswerSheetBase_getAnswerSheetInfo
        (JNIEnv *env, jobject obj,jintArray buf,jint w, jint h, jint w_qr_code, jint h_qr_code){
    jclass m_cls = env->FindClass("com/opencv/tuxin/detectanswersheets/GetDataFromNative");
    jmethodID m_mid = env->GetMethodID(m_cls,"<init>","()V");

    jobject m_obj = env->NewObject(m_cls,m_mid);

    jfieldID fid_first_result = env->GetFieldID(m_cls,"imageDataWarp","[I");
    jfieldID fid_first_qr_code = env->GetFieldID(m_cls,"imageDataQRCode","[I");
    jfieldID fid_second_result = env->GetFieldID(m_cls,"secondImageDataWarp","[I");
    jfieldID fid_second_qr_code = env->GetFieldID(m_cls,"secondImageDataQRCode","[I");

    jfieldID fid_answer_sheet_count = env->GetFieldID(m_cls,"answerSheetCount","I");
    jfieldID fid_hasFirstRectangle = env->GetFieldID(m_cls,"hasFirstRectangle","Z");
    jfieldID fid_hasSecondRectangle = env->GetFieldID(m_cls,"hasSecondRectangle","Z");

    jfieldID fid_isGetFirstQRCode = env->GetFieldID(m_cls,"isGetFirstQRCode","Z");
    jfieldID fid_isGetSecondQRCode = env->GetFieldID(m_cls,"isGetSecondQRCode","Z");

    env->SetBooleanField(m_obj, fid_isGetFirstQRCode, false);
    bool isFoundFirstQRCode = false;
    jint *cbuf;
    cbuf = env->GetIntArrayElements(buf, 0);
    if (cbuf == NULL) {
        return 0;
    }

    Mat img_reset(h,w,CV_8UC4,(unsigned char *) cbuf);
    /// 创建灰度图
    Mat img_gray;
    cvtColor(img_reset, img_gray, CV_BGR2GRAY);

    ///***********获取包含高水平梯度和低垂着梯度的灰度图***********
    int ddepth = CV_32F;

    Mat grad_x, grad_y;
    Mat gradient,abs_gradient;
    /// Gradient X
    Sobel( img_gray, grad_x, ddepth, 1, 0, -1);
    /// Gradient Y
    Sobel( img_gray, grad_y, ddepth, 0, 1, -1);
    /// 相减
    subtract(grad_x, grad_y, gradient);
    convertScaleAbs( gradient, abs_gradient);

    /// 释放 grad_x 和 grad_y

    img_gray = Mat();
    ///***********结束 获取包含高水平梯度和低垂着梯度的灰度图***********

    ///***********寻找合适的轮廓***********
    /// 平均模糊，二值化，得到清晰的黑白图
    blur(abs_gradient, abs_gradient, Size(3,3));
    threshold(abs_gradient, abs_gradient, 120, 255,  CV_THRESH_BINARY);
    /// 膨胀、腐蚀得到更好的轮廓
    /*dilate(abs_gradient, abs_gradient, Mat(), Point(-1, -1), 3);
    erode(abs_gradient, abs_gradient, Mat(), Point(-1, -1), 5);
    dilate(abs_gradient, abs_gradient, Mat(), Point(-1, -1), 3);*/
    erode(abs_gradient, abs_gradient, Mat(), Point(-1, -1), 1);
    dilate(abs_gradient, abs_gradient, Mat(), Point(-1, -1), 2);
    erode(abs_gradient, abs_gradient, Mat(), Point(-1, -1), 1);
    dilate(abs_gradient, abs_gradient, Mat(), Point(-1, -1), 2);

    /// 寻找轮廓并剔除过小的轮廓
    vector<vector<Point> > contours;
    vector<Vec4i> hierarchy;
    Canny(abs_gradient, abs_gradient, 50, 200, 3);

    /// 这里只寻找外表轮廓，（cv_chain_approx_none表示储存找到轮廓的四个角点）
    findContours(abs_gradient, contours, hierarchy, CV_RETR_EXTERNAL, CV_CHAIN_APPROX_NONE);
    int largest_contour = 0;
    int largest_index = 0;
    int second_large_contour = 0;
    int second_large_index = 0;

    /// 过滤后的轮廓，找出最大的两个轮廓
    for(int i = 0; i < contours.size(); i++){
        if (contourArea(contours[i]) >= largest_contour) {
            second_large_contour = largest_contour;
            second_large_index = largest_index;

            largest_contour = int (contourArea(contours[i]));
            largest_index = i;
        } else if ((contourArea(contours[i]) < largest_contour) &&
                   (contourArea(contours[i]) > second_large_contour)){
            second_large_contour = int (contourArea(contours[i]));
            second_large_index = i;
        }
    }
    /// 根据轮廓周长，判断找到的是一个还是两个矩形
    int largest_perimeter = (int) arcLength(contours[largest_index], true);
    int second_perimeter = (int) arcLength(contours[second_large_index], true);
    int img_reset_perimeter = (h + w) * 2;
    int answer_sheet_count = 0;

    if ((largest_perimeter > 0.35 * img_reset_perimeter) && (second_perimeter > 0.35 * img_reset_perimeter)){
        answer_sheet_count = 2;
        env->SetIntField(m_obj, fid_answer_sheet_count,2);
    } else if (largest_perimeter > 0.5 * img_reset_perimeter){
        answer_sheet_count = 1;
        env->SetIntField(m_obj, fid_answer_sheet_count, 1);
    } else {
        answer_sheet_count = 0;
        env->SetIntField(m_obj, fid_answer_sheet_count, 0);
        return m_obj;
    }
    ///***********结束 寻找合适的轮廓***********

    ///***********检测第一个矩形的四个角点***********
    /// 多边形估计
    vector<Point> poly;
    Point top_left, top_right, bottom_left, bottom_right;

    /// 参数为：输入图像的最大轮廓，输出结果，估计精度，是否闭合
    approxPolyDP(Mat(contours[largest_index]), poly, arcLength(Mat(contours[largest_index]), true) * 0.1, true);
    /// 如果找到的轮廓为四边形，取出他的角点值
    if (poly.size() == 4) {
        env->SetBooleanField(m_obj,fid_hasFirstRectangle, true);
        int center_x = 0;
        int center_y = 0;
        /// 计算出四边形的中心点的 x 值和 y 值
        for (vector<Point>::const_iterator itp = poly.begin(); itp != poly.end(); itp++){
            center_x += itp->x;
            center_y += itp->y;
        }
        center_x /= 4;
        center_y /= 4;

        /// 各点与中心点做比较，找出对应的四个角点
        for (vector<Point>::const_iterator itp = poly.begin(); itp != poly.end(); itp++){
            if ((itp->x < center_x) && (itp->y < center_y))
                top_left = Point(itp->x, itp->y);

            if ((itp->x < center_x) && (itp->y > center_y))
                bottom_left = Point(itp->x, itp->y);

            if ((itp->x > center_x) && (itp->y < center_y))
                top_right = Point(itp->x, itp->y);

            if ((itp->x > center_x) && (itp->y > center_y))
                bottom_right = Point(itp->x, itp->y);
        }
    } else {/// 如果这个轮廓不是四边形
        env->SetBooleanField(m_obj,fid_hasFirstRectangle, false);
    }

    ///***********结束 检测第一个矩形的四个角点***********


    ///防止检测到的四个角点是答题卡白纸的四个角点
    ///***********再次检测***********
    Mat img_warp = detect_again(top_left, top_right, bottom_left, bottom_right, img_reset,answer_sheet_count);
    ///***********结束再次检测***********

    ///***********得到 QRCode image ***********
    /// first_img_qr_code => 存储投影后的 qrCode 图片
    Mat first_img_qr_code = Mat::zeros(h_qr_code, w_qr_code, CV_8UC3);
    bool is_get_qr_code = false;
    first_img_qr_code = get_qr_code_image(img_warp, w_qr_code, h_qr_code, is_get_qr_code,answer_sheet_count);
    if (is_get_qr_code)
        env->SetBooleanField(m_obj, fid_isGetFirstQRCode, true);
    ///***********结束得到 QRCode image ***********

    ///*********** 得到 AnswerSheetBase 类的 imageQRCode ***********
    int *out_qr_code = new int[w_qr_code * h_qr_code];
    int *p1 = out_qr_code;
    for (int i = 0; i < h_qr_code; ++i) {
        memcpy(p1, first_img_qr_code.ptr(i), first_img_qr_code.cols* sizeof(int));
        p1 += first_img_qr_code.cols;
    }

    /// 把数据转为 jintArray result
    int size = first_img_qr_code.rows * first_img_qr_code.cols;
    jintArray result = env->NewIntArray(size);
    env->SetIntArrayRegion(result, 0, size, out_qr_code);
    /// 把 jintArray result 赋值给 m_obj 对象
    env->SetObjectField(m_obj, fid_first_qr_code,result);
    /// 释放 jnitArray result
    env->ReleaseIntArrayElements(result,out_qr_code,0);
    ///*********** 结束得到 AnswerSheetBase 类的 imageQRCode ***********

    /***********这个部分的代码是为了返回一些灰度图，测试检测结果用的*************/

    Mat img_warp_gray_filter;
    Mat img_warp_gray;
    vector<Mat> channels;
    Mat g = Mat::zeros(Size(img_warp.cols,img_warp.rows),CV_8UC1);

    /// 转为灰度图
    /// 图像二值化，亮度超过100的（白）使其转为黑色（0），低于150的转为白色（255）
    cvtColor(img_warp,img_warp_gray,CV_BGR2GRAY);
    threshold(img_warp_gray, img_warp_gray, 100, 255, CV_THRESH_BINARY_INV);

    erode(img_warp_gray, img_warp_gray, Mat(), Point(-1, -1), 2);
    dilate(img_warp_gray, img_warp_gray, Mat(), Point(-1, -1), 2);
/*
    int width_num = w * 0.9 * 0.07 * 0.4;
    int height_num = (h * 0.95 * 0.07 * 0.265);

    /// 两个数字间隔的 x，y 值
    int x_num = w * 0.124 * 0.4;
    int y_num = (h * 0.097 * 0.265);

    int cpt_pixel = 0;
    int cpt_choose = 0;
    /// 每一行的第一个数字的位置 x，y 的值
    int px_first_num = w * 0.05 + w * 0.375  * 0.9;
    int py_first_num = (h * 0.022 + h * 0.007 * 0.95);
    for (int cols = 0; cols < 10; cols++) {
        for (int rows = 0; rows < 8; rows++) {
            Mat roi = img_warp_gray(Rect(px_first_num + x_num * rows, /// x 的位置
                                         py_first_num + y_num * cols, /// y 的位置
                                         width_num,  /// 所选取框的宽
                                         height_num));///        高
            line(img_warp_gray,Point(px_first_num + x_num * rows,py_first_num + y_num * cols),
                 Point(px_first_num + x_num * rows + width_num, py_first_num + y_num * cols + width_num),
                Scalar(255),2);
            line(img_warp_gray,Point(px_first_num + x_num * rows + width_num,py_first_num + y_num * cols),
                 Point(px_first_num + x_num * rows , py_first_num + y_num * cols + width_num),
                 Scalar(255),2);
        }
    }


    channels.push_back(g);
    channels.push_back(g);
    channels.push_back(g);
    channels.push_back(img_warp_gray);
    Mat res;
    merge(channels,res);
    int *outImage = new int[img_warp.rows* img_warp.cols];
    int *p2 = outImage;
    for (int i = 0; i < img_warp.rows; ++i) {
        memcpy(p2,res.ptr(i),res.cols* sizeof(int));
        p2 += res.cols;
    }
*/
    /******************************************************************/

    ///*********** 得到 AnswerSheetBase 类的 imageDataWarp ***********
    int *outImage = new int[img_warp.rows* img_warp.cols];
    int *p2 = outImage;
    for (int i = 0; i < img_warp.rows; ++i) {
        memcpy(p2,img_warp.ptr(i),img_warp.cols* sizeof(int));
        p2 += img_warp.cols;
    }

    /// 把数据转为 jintArray result
    size = img_warp.rows * img_warp.cols;
    result = env->NewIntArray(size);
    env->SetIntArrayRegion(result, 0, size, outImage);
    /// 把 jintArray result 赋值给 m_obj 对象
    env->SetObjectField(m_obj, fid_first_result,result);
    /// 释放 jnitArray result
    env->ReleaseIntArrayElements(result,out_qr_code,0);
    ///*********** 结束得到 AnswerSheetBase 类的 imageDataWarp ***********

    if (answer_sheet_count == 2) {
        ///***********检测第二个矩形的四个角点***********
        /// 参数为：输入图像的最大轮廓，输出结果，估计精度，是否闭合
        approxPolyDP(Mat(contours[second_large_index]), poly,
                     arcLength(Mat(contours[second_large_index]), true) * 0.1, true);
        /// 如果找到的轮廓为四边形，取出他的角点值
        if (poly.size() == 4) {
            env->SetBooleanField(m_obj, fid_hasSecondRectangle, true);
            int center_x = 0;
            int center_y = 0;
            /// 计算出四边形的中心点的 x 值和 y 值
            for (vector<Point>::const_iterator itp = poly.begin(); itp != poly.end(); itp++) {
                center_x += itp->x;
                center_y += itp->y;
            }
            center_x /= 4;
            center_y /= 4;

            /// 各点与中心点做比较，找出对应的四个角点
            for (vector<Point>::const_iterator itp = poly.begin(); itp != poly.end(); itp++) {
                if ((itp->x < center_x) && (itp->y < center_y))
                    top_left = Point(itp->x, itp->y);

                if ((itp->x < center_x) && (itp->y > center_y))
                    bottom_left = Point(itp->x, itp->y);

                if ((itp->x > center_x) && (itp->y < center_y))
                    top_right = Point(itp->x, itp->y);

                if ((itp->x > center_x) && (itp->y > center_y))
                    bottom_right = Point(itp->x, itp->y);
            }
        } else {/// 如果这个轮廓不是四边形
            env->SetBooleanField(m_obj, fid_hasSecondRectangle, false);
        }

        ///***********结束 检测第二个矩形的四个角点***********

        ///防止检测到的四个角点是答题卡白纸的四个角点
        ///***********再次检测***********
        Mat second_img_warp = detect_again(top_left, top_right, bottom_left,
                                           bottom_right, img_reset, answer_sheet_count);
        ///***********结束再次检测***********

        ///***********得到 QRCode image ***********
        /// second_img_qr_code => 存储投影后的 qrCode 图片
        Mat second_img_qr_code = Mat::zeros(h_qr_code, w_qr_code, CV_8UC3);
        bool is_get_qr_code = false;
        second_img_qr_code = get_qr_code_image(second_img_warp, w_qr_code, h_qr_code, is_get_qr_code,answer_sheet_count);
        if (is_get_qr_code)
            env->SetBooleanField(m_obj, fid_isGetSecondQRCode, true);
        ///***********结束得到 QRCode image ***********

        ///*********** 得到 AnswerSheetBase 类的 imageQRCode ***********
        int *out_qr_code = new int[w_qr_code * h_qr_code];
        int *p1 = out_qr_code;
        for (int i = 0; i < h_qr_code; ++i) {
            memcpy(p1, second_img_qr_code.ptr(i), second_img_qr_code.cols* sizeof(int));
            p1 += second_img_qr_code.cols;
        }

        /// 把数据转为 jintArray result
        int size = second_img_qr_code.rows * second_img_qr_code.cols;
        jintArray result = env->NewIntArray(size);
        env->SetIntArrayRegion(result, 0, size, out_qr_code);
        /// 把 jintArray result 赋值给 m_obj 对象
        env->SetObjectField(m_obj, fid_second_qr_code,result);
        /// 释放 jnitArray result
        env->ReleaseIntArrayElements(result,out_qr_code,0);
        ///*********** 结束得到 AnswerSheetBase 类的 imageQRCode ***********

        /***********这个部分的代码是为了返回一些灰度图，测试检测结果用的*************/
/*        Mat img_warp_gray_filter;
        Mat img_warp_gray;
        vector<Mat> channels;
        Mat g = Mat::zeros(Size(second_img_warp.cols,second_img_warp.rows),CV_8UC1);

        /// 转为灰度图
        /// 图像二值化，亮度超过100的（白）使其转为黑色（0），低于150的转为白色（255）
        cvtColor(second_img_warp,img_warp_gray,CV_BGR2GRAY);
        threshold(img_warp_gray, img_warp_gray, 100, 255, CV_THRESH_BINARY_INV);

        erode(img_warp_gray, img_warp_gray, Mat(), Point(-1, -1), 2);
        dilate(img_warp_gray, img_warp_gray, Mat(), Point(-1, -1), 2);

        int width_num = w * 0.9 * 0.07 * 0.4;
        int height_num = (h * 0.95 * 0.07 * 0.265) * 0.886;

        /// 两个数字间隔的 x，y 值
        int x_num = w * 0.124 * 0.4;
        int y_num = (h * 0.097 * 0.265) * 0.886;

        int cpt_pixel = 0;
        int cpt_choose = 0;
        /// 每一行的第一个数字的位置 x，y 的值
        int px_first_num = w * 0.05 + w * 0.375  * 0.9;
        int py_first_num = (h * 0.022 + h * 0.007 * 0.95) * 0.886;
        for (int cols = 0; cols < 10; cols++) {
            for (int rows = 0; rows < 8; rows++) {
                Mat roi = img_warp_gray(Rect(px_first_num + x_num * rows, /// x 的位置
                                             py_first_num + y_num * cols, /// y 的位置
                                             width_num,  /// 所选取框的宽
                                             height_num));///        高
                line(img_warp_gray,Point(px_first_num + x_num * rows,py_first_num + y_num * cols),
                     Point(px_first_num + x_num * rows + width_num, py_first_num + y_num * cols + width_num),
                     Scalar(255),2);
                line(img_warp_gray,Point(px_first_num + x_num * rows + width_num,py_first_num + y_num * cols),
                     Point(px_first_num + x_num * rows , py_first_num + y_num * cols + width_num),
                     Scalar(255),2);
            }
        }


        channels.push_back(g);
        channels.push_back(g);
        channels.push_back(g);
        channels.push_back(img_warp_gray);
        Mat res;
        merge(channels,res);
        int *outImage = new int[img_warp.rows* img_warp.cols];
        int *p2 = outImage;
        for (int i = 0; i < img_warp.rows; ++i) {
            memcpy(p2,res.ptr(i),res.cols* sizeof(int));
            p2 += res.cols;
        }*/
        /******************************************************************/
        ///*********** 得到 AnswerSheetBase 类的 imageDataWarp ***********
        int *outImage = new int[second_img_warp.rows* second_img_warp.cols];
        int *p2 = outImage;
        for (int i = 0; i < second_img_warp.rows; ++i) {
            memcpy(p2,second_img_warp.ptr(i),second_img_warp.cols* sizeof(int));
            p2 += second_img_warp.cols;
        }

        /// 把数据转为 jintArray result
        size = second_img_warp.rows * second_img_warp.cols;
        result = env->NewIntArray(size);
        env->SetIntArrayRegion(result, 0, size, outImage);
        /// 把 jintArray result 赋值给 m_obj 对象
        env->SetObjectField(m_obj, fid_second_result,result);
        /// 释放 jnitArray result
        env->ReleaseIntArrayElements(result,out_qr_code,0);
        ///*********** 结束得到 AnswerSheetBase 类的 imageDataWarp ***********
    }

    env->ReleaseIntArrayElements(buf,cbuf,0);
    return m_obj;
};

Mat detect_again(Point top_left, Point top_right, Point bottom_left, Point bottom_right,
                 Mat img_reset, int answer_sheet_count){
    ///***********再次检测，防止检测到的四个角点是答题卡白纸的四个角点***********
    bool need_detection_again = false;

    int min_x = (top_left.x < bottom_left.x) ? top_left.x : bottom_left.x;
    int min_y = (top_left.y < top_right.y) ? top_left.y : top_right.y;
    int max_x = (top_right.x > bottom_right.x) ? top_right.x : bottom_right.x;
    int max_y = (bottom_left.y > bottom_right.y) ? bottom_left.y : bottom_right.y;

    Mat roi_detection_again;
    if (min_x > 0 && max_x > 0 && min_y > 0 && max_y > 0){

        roi_detection_again = img_reset(Rect((min_x + 10 ), /// 左上角点的 x 值（+10 是为了的到的新区域内不能再包含原来的四个角点）
                                             (min_y + 10), /// 左上角点的 y 值
                                             (max_x - min_x - 10),/// 矩形的宽
                                             (max_y - min_y - 10))); /// 矩形的高
        Mat roi_detection_again_contours;
        cvtColor(roi_detection_again,roi_detection_again_contours,CV_BGR2GRAY);

        Mat grad_x, grad_y;
        Mat gradient,abs_gradient;
        /// Gradient X
        Sobel( roi_detection_again_contours, grad_x, CV_32F, 1, 0, -1);
        /// Gradient Y
        Sobel( roi_detection_again_contours, grad_y, CV_32F, 0, 1, -1);

        /// 相减
        subtract(grad_x, grad_y, gradient);
        convertScaleAbs( gradient, abs_gradient);

        blur(abs_gradient, abs_gradient, Size(3,3));
        threshold(abs_gradient, abs_gradient, 100, 255,  CV_THRESH_BINARY);
        /// 膨胀、腐蚀得到更好的轮廓
        dilate(abs_gradient, abs_gradient, Mat(), Point(-1, -1), 1);
        erode(abs_gradient, abs_gradient, Mat(), Point(-1, -1), 3);
        dilate(abs_gradient, abs_gradient, Mat(), Point(-1, -1), 2);

        Canny(abs_gradient, abs_gradient, 50, 200, 3);

        vector<vector<Point> > contours;
        vector<Vec4i> hierarchy;
        /// 这里只寻找外表轮廓，（cv_chain_approx_none表示储存找到轮廓的四个角点）
        findContours(abs_gradient, contours, hierarchy, CV_RETR_EXTERNAL, CV_CHAIN_APPROX_NONE);

        grad_x = Mat();
        grad_y = Mat();
        abs_gradient = Mat();
        /// 寻找最大的轮廓
        int largest_contour = 0;
        int largest_index = 0;
        for(int i = 0; i < contours.size(); i++){
            if (contourArea(contours[i]) > largest_contour) {
                largest_contour = int (contourArea(contours[i]));
                largest_index = i;
            }
        }
        /// 判断最大的轮廓所占的面积是否达到 roi_detection_again 的七分之一以上
        if (largest_contour > roi_detection_again.cols * roi_detection_again.rows * 0.7){
            need_detection_again = true;
            vector<Point> poly;
            /// 查看最大轮廓是否为四边形
            /// 参数为：输入图像的最大轮廓，输出结果，估计精度，是否闭合
            approxPolyDP(Mat(contours[largest_index]), poly, arcLength(Mat(contours[largest_index]), true) * 0.1, true);
            /// 如果找到的轮廓为四边形，取出他的角点值
            if (poly.size() == 4) {
                int center_x = 0;
                int center_y = 0;
                /// 计算出四边形的中心点的 x 值和 y 值
                for (vector<Point>::const_iterator itp = poly.begin(); itp != poly.end(); itp++) {
                    center_x += itp->x;
                    center_y += itp->y;
                }
                center_x /= 4;
                center_y /= 4;

                /// 各点与中心点做比较，找出对应的四个角点
                for (vector<Point>::const_iterator itp = poly.begin(); itp != poly.end(); itp++) {
                    if ((itp->x < center_x) && (itp->y < center_y))
                        top_left = Point(itp->x, itp->y);

                    if ((itp->x < center_x) && (itp->y > center_y))
                        bottom_left = Point(itp->x, itp->y);

                    if ((itp->x > center_x) && (itp->y < center_y))
                        top_right = Point(itp->x, itp->y);

                    if ((itp->x > center_x) && (itp->y > center_y))
                        bottom_right = Point(itp->x, itp->y);
                }
            }
        }
        roi_detection_again_contours = Mat();
    }
    ///***********结束再次检测***********

    ///***********使用 warp 方法，得到投影后的图像***********
    /// push四个角点，使得其符合新的图像的四个点
    vector<Point2f> corners;
    corners.push_back(top_left);
    corners.push_back(top_right);
    corners.push_back(bottom_right);
    corners.push_back(bottom_left);

    /// 声明 mat 用于存储投影后的图片
    Mat img_warp = Mat::zeros(img_reset.rows/answer_sheet_count, img_reset.cols, CV_8UC3);
    /* Mat img_warp;
     if (answer_sheet_count == 1)
         img_warp = Mat::zeros(img_reset.rows, img_reset.cols, CV_8UC3);
     else if (answer_sheet_count == 2)
         img_warp = Mat::zeros(img_reset.rows/2, img_reset.cols, CV_8UC3);
 */
    /// 投影后的图片的 4 个角点
    vector<Point2f> quad_pts;
    quad_pts.push_back(Point2f(0, 0));
    quad_pts.push_back(Point2f(img_warp.cols, 0));
    quad_pts.push_back(Point2f(img_warp.cols, img_warp.rows));
    quad_pts.push_back(Point2f(0, img_warp.rows));

    /// 得到转换矩阵
    Mat transmtx = getPerspectiveTransform(corners, quad_pts);
    /// Apply perspective transformation
    if (need_detection_again){
        warpPerspective(roi_detection_again, img_warp, transmtx, img_warp.size());
    } else {
        warpPerspective(img_reset, img_warp, transmtx, img_warp.size());
    }

    /// 释放矩阵
    transmtx = Mat();
    roi_detection_again = Mat();
    ///***********结束 warp 方法***********

    //return img_warp;
    return img_warp;
}
Mat get_qr_code_image(Mat img_warp, int w_qr_code, int h_qr_code,
                      bool & is_get_qr_code, int answer_sheet_count){
    ///***********得到 QRCode image ***********
    Mat img_warp_gray;
    cvtColor(img_warp, img_warp_gray, CV_BGR2GRAY);
    Mat img_warp_gray_filter;

    threshold(img_warp_gray, img_warp_gray_filter, 110, 255, CV_THRESH_BINARY_INV);
    if (answer_sheet_count == 1) {
        erode(img_warp_gray_filter, img_warp_gray_filter, Mat(), Point(-1, -1), 3);
        dilate(img_warp_gray_filter, img_warp_gray_filter, Mat(), Point(-1, -1), 4);
        erode(img_warp_gray_filter, img_warp_gray_filter, Mat(), Point(-1, -1), 2);
        dilate(img_warp_gray_filter, img_warp_gray_filter, Mat(), Point(-1, -1), 5);

    } else if (answer_sheet_count == 2){
        erode(img_warp_gray_filter, img_warp_gray_filter, Mat(), Point(-1, -1), 3);
        dilate(img_warp_gray_filter, img_warp_gray_filter, Mat(), Point(-1, -1), 1);
        erode(img_warp_gray_filter, img_warp_gray_filter, Mat(), Point(-1, -1), 1);
        dilate(img_warp_gray_filter, img_warp_gray_filter, Mat(), Point(-1, -1), 7);
        //dilate(img_warp_gray_filter, img_warp_gray_filter, Mat(), Point(-1, -1), 5);

    }

    vector<vector<Point> > contours;
    vector<Vec4i> hierarchy;
    findContours(img_warp_gray_filter, contours, hierarchy, CV_RETR_EXTERNAL, CV_CHAIN_APPROX_SIMPLE);

    int largest_contour = 0;
    int largest_index = 0;
    for(int i = 0; i < contours.size(); i++){
        if (contourArea(contours[i]) > largest_contour) {
            largest_contour = int (contourArea(contours[i]));
            largest_index = i;
        }
    }

    Rect bound_rect;
    bound_rect = boundingRect(Mat(contours[largest_index]));

    Point top_left, top_right, bottom_left, bottom_right;
    top_left = bound_rect.tl();
    top_right = Point(bound_rect.br().x, bound_rect.tl().y);
    bottom_right = bound_rect.br();
    bottom_left = Point(bound_rect.tl().x, bound_rect.br().y);
    /// img_qr_code => 存储投影后的 qrCode 图片
    Mat img_qr_code = Mat::zeros(h_qr_code, w_qr_code, CV_8UC3);
    if (((bottom_right.x - top_left.x) > 0) && ((bottom_right.y - top_left.y) > 0)) {
        vector <Point2f> qr_code_corners;
        qr_code_corners.push_back(top_left);
        qr_code_corners.push_back(top_right);
        qr_code_corners.push_back(bottom_right);
        qr_code_corners.push_back(bottom_left);

        vector <Point2f> qr_code_pts;
        qr_code_pts.push_back(Point2f(0, 0));
        qr_code_pts.push_back(Point2f(img_qr_code.cols, 0));
        qr_code_pts.push_back(Point2f(img_qr_code.cols, img_qr_code.rows));
        qr_code_pts.push_back(Point2f(0, img_qr_code.rows));

        /// 得到转换的矩阵
        Mat transmtx_qr_code = getPerspectiveTransform(qr_code_corners, qr_code_pts);
        warpPerspective(img_warp, img_qr_code, transmtx_qr_code, img_qr_code.size());
        //env->SetBooleanField(m_obj, m_fid_isGetQRCode, true);
        is_get_qr_code = true;
    }
    ///***********结束 QRCode image 查找***********
    return img_qr_code;
}

JNIEXPORT jintArray JNICALL Java_com_opencv_tuxin_detectanswersheets_AnswerSheetBase_getStudentAnswers
        (JNIEnv * env, jobject obj, jintArray buf, jint w, jint h, jfloat scale,jint questionCount){
    jint *cbuf;
    cbuf = env->GetIntArrayElements(buf, 0);
    if (cbuf == NULL) {
        return 0;
    }

    /// 把数据转为图片
    Mat img_src(h,w,CV_8UC4,(unsigned char *) cbuf);
    ///***********处理投影后的图像的亮度***********
    /// 转为灰度图
    Mat img_warp_gray;
    cvtColor(img_src,img_warp_gray,CV_BGR2GRAY);


    /// 把答题卡的选项分为18个部分，然后调整每一个部分的亮度，防止部分区域过暗或过亮，影响检测结果
    Mat roi;
    for (int y = 0; y < 6; y++){
        for (int x = 0; x < 3; x++){
            /// 选择区域
            roi = img_warp_gray(
                    Rect(img_warp_gray.cols * 0.03 + x * img_warp_gray.cols / 3,
                         0.275 * img_warp_gray.rows + y * 0.725 * img_warp_gray.rows / 6,
                         2  * img_warp_gray.cols / 7,
                         0.725 * img_warp_gray.rows / 6
                    )
            );
            /// 在计算选定区域的平均亮度
            Scalar mean_lum = mean(roi);
            for (int i = 0; i < roi.rows; i++){
                for (int j = 0; j < roi.cols; j++){
                    /// 把平均亮度调至120
                    roi.at<uchar>(i, j) = saturate_cast<uchar>(
                            roi.at<uchar>(i, j) + (120 - mean_lum[0]));
                }
            }
        }
    }
    roi = Mat();
    ///***********结束 处理投影后的图像的亮度***********

    ///***********选项检测，检测图了哪些选项***********
    /// 图像二值化，亮度超过100的（白）使其转为黑色（0），低于150的转为白色（255）
    threshold(img_warp_gray, img_warp_gray, 90, 255, CV_THRESH_BINARY_INV);

    if (scale == 1) {
        erode(img_warp_gray, img_warp_gray, Mat(), Point(-1, -1), 2);
        dilate(img_warp_gray, img_warp_gray, Mat(), Point(-1, -1), 4);
        erode(img_warp_gray, img_warp_gray, Mat(), Point(-1, -1), 2);
        dilate(img_warp_gray, img_warp_gray, Mat(), Point(-1, -1), 2);
    } else {
        erode(img_warp_gray, img_warp_gray, Mat(), Point(-1, -1), 2);
        dilate(img_warp_gray, img_warp_gray, Mat(), Point(-1, -1), 2);
    }
    /// answers_number => 题号（1-54）
    /// answers_option => 每一题的选项（A->1, B->2, C->3, D->4）
    /// cpt_choose => 每一题已选的选项计数，如果每题选项超过1，判定该题为没有选取
    int answers_number = 0;
    int answers_option = 0;
    int cpt_choose = 0;
    /// studentAnswers => 学生选择的答案
    jint studentAnswers[54] = { 0 };

    /// w_letter, h_letter 表示字母的大小
    int w_letter = 0.0425 * img_warp_gray.cols;
    int h_letter;
    if (scale == 1)
        h_letter = 0.0225 * img_warp_gray.rows;
    else
        h_letter = 0.0225 * img_warp_gray.rows * scale * 2;
    /// distance_letter 表示两个字母的间距
    int distance_letter = 0.055 * img_warp_gray.cols;

    /// img_roi_rect 表示框取每一个选项(A,B,C,D)的矩形
    Mat img_roi_rect;
    /// 选取框内的像素为 255 的像素计数
    int cpt_pixel = 0;
    /// 总共 54 题，循环 54 次
    for (int i = 0; i < questionCount; i++){
        answers_number++;
        int cols = i % 3;
        int rows = i / 3;

        /// 字母 a 的位置的 x 值、 y 值
        /// 字母 a 的位置的 x 值、 y 值
        int px_letter_a = 0.085 * img_warp_gray.cols + 0.3425 * cols * img_warp_gray.cols;

        int py_letter_a;
        if (scale == 1)
            py_letter_a = (0.2915 * img_warp_gray.rows + 0.0395 * rows * img_warp_gray.rows);
        else
            py_letter_a = (0.2915 * img_warp_gray.rows + 0.039 * rows * img_warp_gray.rows) * scale * 2;
        /// 开始检测每一个选项
        for (int j = 1; j < 5; j++) {
            /// 选项计数加一
            answers_option++;
            img_roi_rect = img_warp_gray(Rect(px_letter_a + distance_letter * (j - 1), /// x 的位置
                                              py_letter_a , /// y 的位置
                                              w_letter,  /// 所选取框的宽
                                              h_letter));///         高

            for (int x = 0; x < img_roi_rect.rows; x++) {
                for (int y = 0; y < img_roi_rect.cols; y++) {
                    //检测每一点的像素，如果等于255，像素计数加一
                    if(img_roi_rect.at<uchar>(x,y) == 255){
                        cpt_pixel++;
                    }
                }
            }
            /// 判断该选项像素计数，如果达到一定的百分比，判定为选取了该选项
            /// 如果检测到 B 和 D 时，把像判定选取的阀值稍微提高一点
            if(answers_option == 2){
                if (cpt_pixel > (w_letter * h_letter) * PERCENT_OP_B ) {
                    studentAnswers[i] = answers_option;
                    cpt_choose++;
                }
            } else {
                if (cpt_pixel > (w_letter * h_letter) * PERCENT_OP) {
                    studentAnswers[i] = answers_option;
                    cpt_choose++;
                }
            }
            /// 像素计数清零，为读取下一个选项做准备
            cpt_pixel = 0;
            /// 如果一题选择了两个选项，判定为没选
            if (cpt_choose > 1) {
                studentAnswers[i] = 0;
            }
        }
        /// 选项计数、图取计数清零，为读取下一题做准备
        cpt_choose = 0;
        answers_option = 0;
    }
    roi = Mat();

    /// 把 studentAnswers[54] 转为 AnswerSheetBase 类的 studentAnswers
    jintArray jint_student_answers = env->NewIntArray(54);
    env->SetIntArrayRegion(jint_student_answers,0,54,studentAnswers);
    env->ReleaseIntArrayElements(buf,cbuf,0);

    return jint_student_answers;
}

JNIEXPORT jintArray JNICALL Java_com_opencv_tuxin_detectanswersheets_AnswerSheetBase_getStudentNumbers
        (JNIEnv * env, jobject obj, jintArray buf, jint w, jint h, jfloat scale){
    jint *cbuf;
    cbuf = env->GetIntArrayElements(buf, 0);
    if (cbuf == NULL) {
        return 0;
    }
    /// 把数据转为图片
    Mat img_src(h,w,CV_8UC4,(unsigned char *) cbuf);
    /// 转为灰度图
    Mat img_warp_gray;

    jint student_number[8] = { -1,-1,-1,-1,-1,-1,-1,-1 };/// 初始值设为 -1，表示没有图取
    ///***********学号检测，检测图了哪些学号***********
    /// 图像二值化，亮度超过100的（白）使其转为黑色（0），低于150的转为白色（255）
    cvtColor(img_src,img_warp_gray,CV_BGR2GRAY);
    threshold(img_warp_gray, img_warp_gray, 100, 255, CV_THRESH_BINARY_INV);

    erode(img_warp_gray, img_warp_gray, Mat(), Point(-1, -1), 2);
    dilate(img_warp_gray, img_warp_gray, Mat(), Point(-1, -1), 2);

    /// 数字的宽和高
    int width_num = w * 0.9 * 0.07 * 0.4;
    int height_num;
    if (scale == 1)
        height_num = h * 0.95 * 0.07 * 0.265;
    else
        height_num = (h * 0.95 * 0.07 * 0.265) * scale * 2;

    /// 两个数字间隔的 x，y 值
    int x_num = w * 0.124 * 0.4;
    int y_num;

    if (scale == 1)
        y_num = h * 0.097 * 0.265;
    else
        y_num = (h * 0.097 * 0.265) * scale * 2;

    int cpt_pixel = 0;
    int cpt_choose = 0;
    /// 每一行的第一个数字的位置 x，y 的值
    int px_first_num = w * 0.05 + w * 0.375  * 0.9;
    int py_first_num;
    if (scale == 1)
        py_first_num = h * 0.022 + h * 0.007 * 0.95;
    else
        py_first_num = (h * 0.022 + h * 0.007 * 0.95) * scale * 2;

    for (int cols = 0; cols < 10; cols++) {
        for (int rows = 0; rows < 8; rows++) {
            Mat roi = img_warp_gray(Rect(px_first_num + x_num * rows, /// x 的位置
                                         py_first_num + y_num * cols, /// y 的位置
                                         width_num,  /// 所选取框的宽
                                         height_num));///        高
            for (int x = 0; x < roi.rows; x++) {
                for (int y = 0; y < roi.cols; y++) {
                    //检测每一点的像素，如果等于255，像素计数加一
                    if(roi.at<uchar>(x,y) == 255){
                        cpt_pixel++;
                    }
                }
            }
            /// 累计达到 255 的像素值的总数如果大于所选框的0.3，认为图取了该数字
            if (cpt_pixel > roi.rows * roi.cols * 0.35) {
                student_number[rows] = cols;
                cpt_choose++;
            }
            cpt_pixel = 0;
        }
        /// 检测到图取了两次，让这个号码为 -1，方便之后检查是否正确
        /*if (cpt_choose > 1){
            student_number[cols] = -2;
        }
        if (cpt_choose == 0){
            student_number[cols] = -1;
        }
        cpt_choose = 0;*/
    }
    img_warp_gray = Mat();
    jintArray jint_student_number = env->NewIntArray(8);
    env->SetIntArrayRegion(jint_student_number,0,8,student_number);

    env->ReleaseIntArrayElements(buf,cbuf,0);
    return jint_student_number;
}
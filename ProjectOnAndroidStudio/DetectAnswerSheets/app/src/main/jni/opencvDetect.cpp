#include "com_opencv_tuxin_detectanswersheets_AnswerSheetBase.h"
#include <stdio.h>
#include <stdlib.h>
#include <opencv2/opencv.hpp>
#include <opencv/highgui.h>
using namespace cv;
using namespace std;

#define PERCENT_OP_B 0.45
#define PERCENT_OP 0.4

JNIEXPORT jobject JNICALL Java_com_opencv_tuxin_detectanswersheets_AnswerSheetBase_getAnswerSheetInfo
        (JNIEnv *env, jobject obj,jintArray buf,jint w, jint h){
    jclass m_cls = env->FindClass("com/opencv/tuxin/detectanswersheets/GetDataFromNative");
    jmethodID m_mid = env->GetMethodID(m_cls,"<init>","()V");

    jobject m_obj = env->NewObject(m_cls,m_mid);
    /// m_fid_result 是投影后的数据的 fieldID
    jfieldID  m_fid_result = env->GetFieldID(m_cls,"imageDataWarp","[I");
    /// m_fid_isRectangle 是 AnswerSheetBase 中的 isRectangle 的fieldID
    jfieldID  m_fid_isRectangle = env->GetFieldID(m_cls,"isRectangle","Z");

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
    grad_x = Mat();
    grad_y = Mat();
    img_gray = Mat();
    ///***********结束 获取包含高水平梯度和低垂着梯度的灰度图***********

    ///***********寻找合适的轮廓***********
    /// 平均模糊，二值化，得到清晰的黑白图
    blur(abs_gradient, abs_gradient, Size(3,3));
    threshold(abs_gradient, abs_gradient, 100, 255,  CV_THRESH_BINARY);
    /// 膨胀、腐蚀得到更好的轮廓
    dilate(abs_gradient, abs_gradient, Mat(), Point(-1, -1), 2);
    erode(abs_gradient, abs_gradient, Mat(), Point(-1, -1), 4);
    dilate(abs_gradient, abs_gradient, Mat(), Point(-1, -1), 3);

    /// 寻找轮廓并剔除过小的轮廓
    vector<vector<Point> > contours;
    vector<Vec4i> hierarchy;
    Canny(abs_gradient, abs_gradient, 50, 200, 3);

    /// 这里只寻找外表轮廓，（cv_chain_approx_none表示储存找到轮廓的四个角点）
    findContours(abs_gradient, contours, hierarchy, CV_RETR_EXTERNAL, CV_CHAIN_APPROX_NONE);

    int largest_contour = 0;
    int largest_index = 0;
    ///过滤后的轮廓，面积最大的为我们的矩形边框
    for(int i = 0; i < contours.size(); i++){
        if (contourArea(contours[i]) > largest_contour) {
            largest_contour = int (contourArea(contours[i]));
            largest_index = i;
        }
    }

    /*   Mat draw_contours(img_reset.rows, img_reset.cols,CV_8UC4,Scalar(0));
       for (int i = 0; i < contours.size(); ++i)
           //drawContours(draw_contours,contours,largest_index,Scalar(255,255,255));
           drawContours(draw_contours,contours,i,Scalar(255,255,255));
   */
    ///***********结束 寻找合适的轮廓***********

    ///***********得到合适的四个角点***********
    /// 多边形估计
    vector<Point> poly;
    Point top_left, top_right, bottom_left, bottom_right;
    /// 参数为：输入图像的最大轮廓，输出结果，估计精度，是否闭合
    approxPolyDP(Mat(contours[largest_index]), poly, arcLength(Mat(contours[largest_index]), true) * 0.01, true);
    /// 如果找到的轮廓为四边形，取出他的角点值
    if (poly.size() == 4) {
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
        /// 如果是四边形，isRectangle 为 true
        env->SetBooleanField(m_obj, m_fid_isRectangle, true);
    } else {
        /// 如果不是四边形，isRectangle 为 false
        env->SetBooleanField(m_obj, m_fid_isRectangle, false);
        return m_obj;
    }
    ///***********结束 得到合适的四个角点***********

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
                                                (max_x - min_x -10),/// 矩形的宽
                                                (max_y - min_y - 10))); /// 矩形的高
        Mat roi_detection_again_contours;
        cvtColor(roi_detection_again,roi_detection_again_contours,CV_BGR2GRAY);
        blur(roi_detection_again_contours, roi_detection_again_contours, Size(3,3));
        Canny(roi_detection_again_contours, roi_detection_again_contours, 50, 200, 3);

        /// 膨胀得到更好的轮廓
        dilate(roi_detection_again_contours, roi_detection_again_contours, Mat(), Point(-1, -1), 1);
        //erode(roi_detection_again_contours, roi_detection_again_contours, Mat(), Point(-1, -1), 1);



        /// 这里只寻找外表轮廓，（cv_chain_approx_none表示储存找到轮廓的四个角点）
        findContours(roi_detection_again_contours, contours, hierarchy, CV_RETR_EXTERNAL, CV_CHAIN_APPROX_NONE);

        /// 寻找最大的轮廓
        largest_contour = 0;
        for(int i = 0; i < contours.size(); i++){
            if (contourArea(contours[i]) > largest_contour) {
                largest_contour = int (contourArea(contours[i]));
                largest_index = i;
            }
        }
        /// 判断最大的轮廓所占的面积是否达到 roi_detection_again 的七分之一以上
        if (largest_contour > roi_detection_again.cols * roi_detection_again.rows * 0.7){
            need_detection_again = true;
            /// 查看最大轮廓是否为四边形
            /// 参数为：输入图像的最大轮廓，输出结果，估计精度，是否闭合
            approxPolyDP(Mat(contours[largest_index]), poly, arcLength(Mat(contours[largest_index]), true) * 0.01, true);
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
    Mat img_warp = Mat::zeros(img_reset.rows, img_reset.cols, CV_8UC3);
    /// 投影后的图片的 4 个角点
    vector<Point2f> quad_pts;
    quad_pts.push_back(Point2f(0, 0));
    quad_pts.push_back(Point2f(img_reset.cols, 0));
    quad_pts.push_back(Point2f(img_reset.cols, img_reset.rows));
    quad_pts.push_back(Point2f(0, img_reset.rows));

    /// 得到转换矩阵
    Mat transmtx = getPerspectiveTransform(corners, quad_pts);
    /// Apply perspective transformation
    if (need_detection_again){
        warpPerspective(roi_detection_again, img_warp, transmtx, img_reset.size());
    } else {
        warpPerspective(img_reset, img_warp, transmtx, img_reset.size());
    }


    /// 释放矩阵
    transmtx = Mat();
    roi_detection_again = Mat();
    ///***********结束 warp 方法***********
/*
    ///***********处理投影后的图像的亮度***********
    /// 转为灰度图
    Mat img_warp_gray;
    cvtColor(img_warp, img_warp_gray, CV_BGR2GRAY);
    /// 把答题卡的选项分为18个部分，然后调整每一个部分的亮度，防止部分区域过暗或过亮，影响检测结果
    Mat roi;
    for (int y = 0; y < 6; y++){
        for (int x = 0; x < 3; x++){
            /// 选择区域
            roi = img_warp_gray(
                    Rect(img_warp_gray.cols * 0.03 + x * img_warp_gray.cols / 3,
                         0.275 * img_warp_gray.rows + y * 0.725 * img_warp_gray.rows / 6,
                         2 * img_warp_gray.cols / 7,
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
    threshold(img_warp_gray, img_warp_gray, 100, 255, CV_THRESH_BINARY_INV);

    /// answers_number => 题号（1-54）
    /// answers_option => 每一题的选项（A->1, B->2, C->3, D->4）
    /// cpt_choose => 每一题已选的选项计数，如果每题选项超过1，判定该题为没有选取
    int answers_number = 0;
    int answers_option = 0;
    int cpt_choose = 0;
    /// studentAnswers => 学生选择的答案
    jint studentAnswers[54] = { 0 };

    /// x_num, y_num 表示数字的位置
    int x_num, y_num;
    /// dis_letter_row 表示各字母的行距离
    int dis_letter_row = 0.055 * img_warp.cols;
    /// x_num_bord, y_num_bord 表示数字距离边界的行、列间距
    int x_num_bord = 0.0275 * img_warp.cols;
    int y_num_bord = 0.0185 * img_warp.rows + 0.27 * img_warp.rows;
    /// x_num_num, y_num_num 表示数字距离数字的行、列间距
    int x_num_num = 0.348 * img_warp.cols;
    int y_num_num = 0.04 * img_warp.rows;
    /// large_letter表示数字和字母的大小
    int width_letter = 0.039 * img_warp.cols;
    int height_letter = 0.0235 * img_warp.rows;

    /// img_roi_rect 表示框取每一个选项(A,B,C,D)的矩形
    Mat img_roi_rect;
    Mat img_roi_rect_color;
    /// 选取框内的像素为 255 的像素计数
    int cpt_pixel = 0;

    /// 读取每一题的选项
    for (int cols = 0; cols < 18; cols++) {
        for (int rows = 0; rows < 3; rows++){
            /// 题号计数加一
            answers_number++;
            /// Point(x_num,y_num) 表示数字的位置，
            /// 每一次循环 x_num 增加 x_num_num * rows 的距离
            /// 每三次循环 y_num 增加 y_num_num * cols 的距离
            x_num = x_num_bord + x_num_num * rows;
            y_num = y_num_bord + y_num_num * cols;

            /// 读取一题的每一个选项，总共四个
            for (int i = 1; i < 5; i++){
                /// 选项计数加一
                answers_option++;

                /// 每一次循环选取的矩形的距离增加一个 dis_letter_row，每一个矩形长为width_letter，宽为height_letter
                img_roi_rect = img_warp_gray(Rect(x_num + dis_letter_row*i, y_num, width_letter, height_letter));
                img_roi_rect_color = img_warp(Rect(x_num + dis_letter_row*i, y_num, width_letter, height_letter));
                /// 选项检测：选取读取框，历遍该框，读取每一个像素，如果等于255，
                /// 像素计数加一，如果像素计数达到读取框面积的特定值，我们认为用户图取了该选项
                for (int x = 0; x < img_roi_rect.rows; x++) {
                    for (int y = 0; y < img_roi_rect.cols; y++) {
                        //检测每一点的像素，如果等于255，像素计数加一
                        if (img_roi_rect.at<uchar>(x, y) == 255){
                            cpt_pixel++;
                        }
                        /// 画上一个叉,检测读取框是否正确
                        //line(img_roi_rect, Point(0,0), Point(img_roi_rect.cols,img_roi_rect.rows), Scalar(255),2);
                        //line(img_roi_rect, Point(img_roi_rect.cols,0), Point(0,img_roi_rect.rows), Scalar(255),2);
                        line(img_roi_rect_color, Point(0,0), Point(img_roi_rect.cols,img_roi_rect.rows), Scalar(255),2);
                        line(img_roi_rect_color, Point(img_roi_rect.cols,0), Point(0,img_roi_rect.rows), Scalar(255),2);
                    }
                }
                /// 选项检测：如果像素计数达到指定值，输出该选项
                /// 选项为 B 时，改变指定值的大小
                if (answers_option == 2){
                    /// 达到每一个框的百分之四十
                    if (cpt_pixel > (width_letter*height_letter) * PERCENT_OP_B) {
                        //line(img_roi_rect, Point(0, 0), Point(width_letter, height_letter), Scalar(255));
                        //line(img_roi_rect, Point(width_letter, 0), Point(0, height_letter), Scalar(255));
                        /// 该题的学生的选项 = answers_option
                        studentAnswers[answers_number - 1] = answers_option;
                        /// 已选选项加一
                        cpt_choose++;
                    }
                }
                else{
                    if (cpt_pixel > (width_letter*height_letter)*PERCENT_OP) {

                        //line(img_roi_rect, Point(0, 0), Point(width_letter, height_letter), Scalar(255));
                        //line(img_roi_rect, Point(width_letter, 0), Point(0, height_letter), Scalar(255));
                        /// 该题的学生的选项 = answers_option
                        studentAnswers[answers_number - 1] = answers_option;
                        /// 已选选项加一
                        cpt_choose++;
                    }
                }/// 结束该选项检测，开始准备进行下一个选项检测
                /// 像素计数归零
                cpt_pixel = 0;
            }/// 结束 answer_number 的四个选项，准备开始 answer_number + 1 的检测
            /// 选项检测归零，下次重新从选项A开始检测
            answers_option = 0;
            /// 如果一题选取了两个以上选项，判定该题没有选择
            if (cpt_choose > 1) {
                studentAnswers[answers_number - 1] = 0;
            }
            /// 选项计数归零
            cpt_choose = 0;
        }
    }

    /// 把 studentAnswers[54] 转为 AnswerSheetBase 类的 studentAnswers
    jfieldID  m_fid_student_answers = env->GetFieldID(m_cls,"studentAnswers","[I");
    jintArray jint_student_answers = env->NewIntArray(54);
    env->SetIntArrayRegion(jint_student_answers,0,54,studentAnswers);
    env->SetObjectField(m_obj,m_fid_student_answers, jint_student_answers);
    env->ReleaseIntArrayElements(jint_student_answers,studentAnswers,0);
*/

    /// 把投影后的图片转为 AnswerSheetBase 类的 imgDataWarp
    int *outImage = new int[img_warp.rows* img_warp.cols];
    int *p = outImage;
    for (int i = 0; i < img_warp.rows; ++i) {
        memcpy(p,img_warp.ptr(i),img_warp.cols* sizeof(int));
        p += img_warp.cols;
    }

    /// 把数据转为 jintArray result
    int size = img_warp.rows * img_warp.cols;
    jintArray result = env->NewIntArray(size);
    env->SetIntArrayRegion(result, 0, size, outImage);
    /// 把 jintArray result 赋值给 m_obj 对象
    env->SetObjectField(m_obj,m_fid_result,result);
    /// 释放 jnitArray result
    env->ReleaseIntArrayElements(result,outImage,0);
    env->ReleaseIntArrayElements(buf,cbuf,0);


    //env->SetObjectField(m_obj,m_fid_result,result);
    return m_obj;
};

JNIEXPORT jintArray JNICALL Java_com_opencv_tuxin_detectanswersheets_AnswerSheetBase_getStudentAnswers
        (JNIEnv * env, jobject obj, jintArray buf, jint w, jint h){
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
                         2 * img_warp_gray.cols / 7,
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
    threshold(img_warp_gray, img_warp_gray, 100, 255, CV_THRESH_BINARY_INV);

    /// answers_number => 题号（1-54）
    /// answers_option => 每一题的选项（A->1, B->2, C->3, D->4）
    /// cpt_choose => 每一题已选的选项计数，如果每题选项超过1，判定该题为没有选取
    int answers_number = 0;
    int answers_option = 0;
    int cpt_choose = 0;
    /// studentAnswers => 学生选择的答案
    jint studentAnswers[54] = { 0 };

    /// x_num, y_num 表示数字的位置
    int x_num, y_num;
    /// dis_letter_row 表示各字母的行距离
    int dis_letter_row = 0.055 * img_warp_gray.cols;
    /// x_num_bord, y_num_bord 表示数字距离边界的行、列间距
    int x_num_bord = 0.027 * img_warp_gray.cols;
    int y_num_bord = 0.0185 * img_warp_gray.rows + 0.27 * img_warp_gray.rows;
    /// x_num_num, y_num_num 表示数字距离数字的行、列间距
    int x_num_num = 0.348 * img_warp_gray.cols;
    int y_num_num = 0.04 * img_warp_gray.rows;
    /// large_letter表示数字和字母的大小
    int width_letter = 0.039 * img_warp_gray.cols;
    int height_letter = 0.0235 * img_warp_gray.rows;

    /// img_roi_rect 表示框取每一个选项(A,B,C,D)的矩形
    Mat img_roi_rect;
    /// 选取框内的像素为 255 的像素计数
    int cpt_pixel = 0;

    /// 读取每一题的选项
    for (int cols = 0; cols < 18; cols++) {
        for (int rows = 0; rows < 3; rows++){
            /// 题号计数加一
            answers_number++;
            /// Point(x_num,y_num) 表示数字的位置，
            /// 每一次循环 x_num 增加 x_num_num * rows 的距离
            /// 每三次循环 y_num 增加 y_num_num * cols 的距离
            x_num = x_num_bord + x_num_num * rows;
            y_num = y_num_bord + y_num_num * cols;

            /// 读取一题的每一个选项，总共四个
            for (int i = 1; i < 5; i++){
                /// 选项计数加一
                answers_option++;

                /// 每一次循环选取的矩形的距离增加一个 dis_letter_row，每一个矩形长为width_letter，宽为height_letter
                img_roi_rect = img_warp_gray(Rect(x_num + dis_letter_row*i, y_num, width_letter, height_letter));
                /// 选项检测：选取读取框，历遍该框，读取每一个像素，如果等于255，
                /// 像素计数加一，如果像素计数达到读取框面积的特定值，我们认为用户图取了该选项
                for (int x = 0; x < img_roi_rect.rows; x++) {
                    for (int y = 0; y < img_roi_rect.cols; y++) {
                        //检测每一点的像素，如果等于255，像素计数加一
                        if (img_roi_rect.at<uchar>(x, y) == 255){
                            cpt_pixel++;
                        }
                    }
                }
                /// 选项检测：如果像素计数达到指定值，输出该选项
                /// 选项为 B 时，改变指定值的大小
                if (answers_option == 2){
                    /// 达到每一个框的百分之四十
                    if (cpt_pixel > (width_letter*height_letter) * PERCENT_OP_B) {
                        //line(img_roi_rect, Point(0, 0), Point(width_letter, height_letter), Scalar(255));
                        //line(img_roi_rect, Point(width_letter, 0), Point(0, height_letter), Scalar(255));
                        /// 该题的学生的选项 = answers_option
                        studentAnswers[answers_number - 1] = answers_option;
                        /// 已选选项加一
                        cpt_choose++;
                    }
                }
                else{
                    if (cpt_pixel > (width_letter*height_letter)*PERCENT_OP) {

                        //line(img_roi_rect, Point(0, 0), Point(width_letter, height_letter), Scalar(255));
                        //line(img_roi_rect, Point(width_letter, 0), Point(0, height_letter), Scalar(255));
                        /// 该题的学生的选项 = answers_option
                        studentAnswers[answers_number - 1] = answers_option;
                        /// 已选选项加一
                        cpt_choose++;
                    }
                }/// 结束该选项检测，开始准备进行下一个选项检测
                /// 像素计数归零
                cpt_pixel = 0;
            }/// 结束 answer_number 的四个选项，准备开始 answer_number + 1 的检测
            /// 选项检测归零，下次重新从选项A开始检测
            answers_option = 0;
            /// 如果一题选取了两个以上选项，判定该题没有选择
            if (cpt_choose > 1) {
                studentAnswers[answers_number - 1] = 0;
            }
            /// 选项计数归零
            cpt_choose = 0;
        }
    }
    roi = Mat();

    /// 把 studentAnswers[54] 转为 AnswerSheetBase 类的 studentAnswers

    jintArray jint_student_answers = env->NewIntArray(54);
    env->SetIntArrayRegion(jint_student_answers,0,54,studentAnswers);

    env->ReleaseIntArrayElements(buf,cbuf,0);
    return jint_student_answers;
}
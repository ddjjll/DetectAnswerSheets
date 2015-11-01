LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)
OPENCV_LIB_TYPE:=STATIC
ifeq ("$(wildcard $(OPENCV_MK_PATH))","")

include E:\OpenCV-android-sdk\sdk\native\jni\OpenCV.mk
else
include $(OPENCV_MK_PATH)
endif

LOCAL_MODULE := opencvDetect
LOCAL_SRC_FILES := opencvDetect.cpp
LOCAL_LDLIBS += -lm -llog
include $(BUILD_SHARED_LIBRARY)
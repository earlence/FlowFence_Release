LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE    := parcelhelper
LOCAL_SRC_FILES += parcelhelper.cpp
LOCAL_CFLAGS	+= -fpermissive
LOCAL_LDLIBS	+= -llog -ldl -L$(LOCAL_PATH) -lbinder
include $(BUILD_SHARED_LIBRARY)

 

#include "Parcel.h"

#include <jni.h>
#include <android/log.h>
#include <dlfcn.h>

#include "IServiceManager.h"
#include "IBinder.h"
//#include "android_util_Binder.h"

#define	TAG		"OASIS.ParcelHelper"

//keep this in sync with ParcelHelper.java
#define	OASIS_BINDER_TYPE_FAIL			-1
#define	OASIS_BINDER_TYPE_BINDER		0
#define	OASIS_BINDER_TYPE_WEAK_BINDER	1
#define	OASIS_BINDER_TYPE_HANDLE		2
#define	OASIS_BINDER_TYPE_WEAK_HANDLE	3
#define	OASIS_BINDER_TYPE_FD			4

#define	LOGI(TAG, MSG)	__android_log_print(ANDROID_LOG_INFO, TAG, MSG)

//Earlence - copied from kernel header binder.h
#define B_PACK_CHARS(c1, c2, c3, c4)   ((((c1)<<24)) | (((c2)<<16)) | (((c3)<<8)) | (c4))
#define B_TYPE_LARGE 0x85
enum {
BINDER_TYPE_BINDER = B_PACK_CHARS('s', 'b', '*', B_TYPE_LARGE),
BINDER_TYPE_WEAK_BINDER = B_PACK_CHARS('w', 'b', '*', B_TYPE_LARGE),
BINDER_TYPE_HANDLE = B_PACK_CHARS('s', 'h', '*', B_TYPE_LARGE),
BINDER_TYPE_WEAK_HANDLE = B_PACK_CHARS('w', 'h', '*', B_TYPE_LARGE),
BINDER_TYPE_FD = B_PACK_CHARS('f', 'd', '*', B_TYPE_LARGE),
};

namespace android {
//extern sp<IServiceManager> gDefaultServiceManager;

extern "C" JNIEXPORT void JNICALL Java_edu_umich_oasis_api_ParcelHelper_rewriteGDefaultServiceManager(JNIEnv *env, jclass clazz, jobject obj)
	{
		/*void *convHandle = dlopen("/system/lib/libandroid_runtime.so", RTLD_NOW);
		sp<IBinder> (*toIBinder)(JNIEnv*, jobject);

		if(convHandle != NULL)
		{
			toIBinder = dlsym(convHandle, "ibinderForJavaObject");
			if(toIBinder != NULL)
			{
				sp<IBinder> oasisBinder = toIBinder(env, obj);
				gDefaultServiceManager =  interface_cast<IServiceManager>(oasisBinder);
				LOGI(TAG, "gDefaultServiceManager reset to Oasis");
			}
		}
		else
			LOGI(TAG, "could not load libandroid_runtime.so");*/

//		sp<IBinder> oasisBinder = ibinderForJavaObject(env, obj);
//		gDefaultServiceManager = interface_cast<IServiceManager>(oasisBinder);
//		LOGI(TAG, "gDefaultServiceManager reset to Oasis");
	}
}

android::Parcel *parcelFromJavaObj(JNIEnv* env, jlong obj)
{
    if (obj) {
        //android::Parcel* p = (android::Parcel*)env->GetIntField(obj, gParcelOffsets.mNativePtr);
    	android::Parcel* p = (android::Parcel*) obj;
        if (p != NULL) {

            return p;
        }
        //jniThrowException(env, "java/lang/IllegalStateException", "Parcel has been finalized!");
    }
    return NULL;
}

jint JNI_OnLoad(JavaVM* vm, void* reserved)
{
	LOGI("EARLENCE", "jni onload");

    return JNI_VERSION_1_6;
}

extern "C" JNIEXPORT jint JNICALL Java_edu_umich_oasis_api_ParcelHelper_getNumObjects(JNIEnv *env, jclass clazz, jlong nativePtr)
{
	android::Parcel *p = parcelFromJavaObj(env, nativePtr);
	if(p != NULL)
		return p->objectsCount();
	else
		return -1;
}

extern "C" JNIEXPORT jint JNICALL Java_edu_umich_oasis_api_ParcelHelper_getDataPos(JNIEnv *env, jclass clazz, jlong nativePtr)
{
	android::Parcel *p = parcelFromJavaObj(env, nativePtr);
	if(p != NULL)
		return p->dataPosition();
	else
		return -1;
}

extern "C" JNIEXPORT jboolean JNICALL Java_edu_umich_oasis_api_ParcelHelper_setDataPos(JNIEnv *env, jclass clazz, jlong nativePtr, jint pos)
{
	android::Parcel *p = parcelFromJavaObj(env, nativePtr);
	if(p != NULL)
	{
		p->setDataPosition(pos);
		return true;
	}
	else
		return false;
}


//seeks to an offset of a flat_binder_object, reads it, computes the type, seeks back and returns type
extern "C" JNIEXPORT jint JNICALL Java_edu_umich_oasis_api_ParcelHelper_seekToBinder(JNIEnv *env, jclass clazz, jlong obj, jint objectPos)
{
	android::Parcel *p = parcelFromJavaObj(env, obj);
	int binder_type = OASIS_BINDER_TYPE_FAIL;

	if(p != NULL)
	{
		__android_log_print(ANDROID_LOG_INFO, TAG, "parcel obtained: %d, hasFDs: %d", p->dataSize(), p->hasFileDescriptors());
		const size_t *mObjects = p->objects();

		size_t offset = mObjects[objectPos];

		p->setDataPosition(offset);
		const android::flat_binder_object *binder_obj = p->readObject(false);

		if(binder_obj)
		{
			switch(binder_obj->type)
			{
			case BINDER_TYPE_BINDER:
				binder_type = OASIS_BINDER_TYPE_BINDER;
				break;
			case BINDER_TYPE_WEAK_BINDER:
				binder_type = OASIS_BINDER_TYPE_WEAK_BINDER;
				break;
			case BINDER_TYPE_HANDLE:
				binder_type = OASIS_BINDER_TYPE_HANDLE;
				break;
			case BINDER_TYPE_WEAK_HANDLE:
				binder_type = OASIS_BINDER_TYPE_WEAK_HANDLE;
				break;
			case BINDER_TYPE_FD:
				binder_type = OASIS_BINDER_TYPE_FD;
				break;
			}

			if(binder_type != OASIS_BINDER_TYPE_FAIL)
				p->setDataPosition(offset); //reset out seek so that the Java code can read correctly
		}
	}

	return binder_type;
}



/* jSSC (Java Simple Serial Connector) - serial port communication library.
 * © Alexey Sokolov (scream3r), 2010-2014.
 *
 * This file is part of jSSC.
 *
 * jSSC is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * jSSC is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with jSSC.  If not, see <http://www.gnu.org/licenses/>.
 *
 * If you use jSSC in public project you can inform me about this by e-mail,
 * of course if you want it.
 *
 * e-mail: scream3r.org@gmail.com
 * web-site: http://scream3r.org | http://code.google.com/p/java-simple-serial-connector/
 */
#include <jni.h>
#include <stdlib.h>
#include <windows.h>
#include <setupapi.h>
#include <iostream>
#if __cplusplus <= 199711L
    #include <tr1/regex>
    namespace std {
        using namespace std::tr1;
    }
#endif
#if __cplusplus > 199711L
    #include <regex>
#endif
#include <string>
#include "../jssc_SerialNativeInterface.h"

//#include <iostream>

/*
 * Get native library version
 */
JNIEXPORT jstring JNICALL Java_jssc_SerialNativeInterface_getNativeLibraryVersion(JNIEnv *env, jobject object) {
    return env->NewStringUTF(jSSC_NATIVE_LIB_VERSION);
}

/*
 * Port opening.
 *
 * In 2.2.0 added useTIOCEXCL (not used in Windows, only for compatibility with _nix version)
 */
JNIEXPORT jlong JNICALL Java_jssc_SerialNativeInterface_openPort(JNIEnv *env, jobject object, jstring portName, jboolean useTIOCEXCL){
    char prefix[] = "\\\\.\\";
    const char* port = env->GetStringUTFChars(portName, JNI_FALSE);

    //since 2.1.0 -> string concat fix
    char portFullName[strlen(prefix) + strlen(port) + 1];
    strcpy(portFullName, prefix);
    strcat(portFullName, port);
    //<- since 2.1.0

    HANDLE hComm = CreateFile(portFullName,
                       	   	  GENERIC_READ | GENERIC_WRITE,
                       	   	  0,
                       	   	  0,
                       	   	  OPEN_EXISTING,
                       	   	  FILE_FLAG_OVERLAPPED,
                       	   	  0);
    env->ReleaseStringUTFChars(portName, port);

    //since 2.3.0 ->
    if(hComm != INVALID_HANDLE_VALUE){
    	DCB *dcb = new DCB();
    	if(!GetCommState(hComm, dcb)){
    		CloseHandle(hComm);//since 2.7.0
    		hComm = (HANDLE)jssc_SerialNativeInterface_ERR_INCORRECT_SERIAL_PORT;//(-4)Incorrect serial port
    	}
    	delete dcb;
    }
    else {
    	DWORD errorValue = GetLastError();
    	if(errorValue == ERROR_ACCESS_DENIED){
    		hComm = (HANDLE)jssc_SerialNativeInterface_ERR_PORT_BUSY;//(-1)Port busy
    	}
    	else if(errorValue == ERROR_FILE_NOT_FOUND){
    		hComm = (HANDLE)jssc_SerialNativeInterface_ERR_PORT_NOT_FOUND;//(-2)Port not found
    	}
    }
    //<- since 2.3.0
    return (jlong)hComm;//since 2.4.0 changed to jlong
};

/*
 * Setting serial port params.
 *
 * In 2.6.0 added flags (not used in Windows, only for compatibility with _nix version)
 */
JNIEXPORT jboolean JNICALL Java_jssc_SerialNativeInterface_setParams
  (JNIEnv *env, jobject object, jlong portHandle, jint baudRate, jint byteSize, jint stopBits, jint parity, jboolean setRTS, jboolean setDTR, jint flags){
    HANDLE hComm = (HANDLE)portHandle;
    DCB *dcb = new DCB();
    jboolean returnValue = JNI_FALSE;
    if(GetCommState(hComm, dcb)){
        dcb->BaudRate = baudRate;
        dcb->ByteSize = byteSize;
        dcb->StopBits = stopBits;
        dcb->Parity = parity;

        //since 0.8 ->
        if(setRTS == JNI_TRUE){
        	dcb->fRtsControl = RTS_CONTROL_ENABLE;
        }
        else {
        	dcb->fRtsControl = RTS_CONTROL_DISABLE;
        }
        if(setDTR == JNI_TRUE){
        	dcb->fDtrControl = DTR_CONTROL_ENABLE;
        }
        else {
           	dcb->fDtrControl = DTR_CONTROL_DISABLE;
        }
        dcb->fOutxCtsFlow = FALSE;
        dcb->fOutxDsrFlow = FALSE;
        dcb->fDsrSensitivity = FALSE;
        dcb->fTXContinueOnXoff = TRUE;
        dcb->fOutX = FALSE;
        dcb->fInX = FALSE;
        dcb->fErrorChar = FALSE;
        dcb->fNull = FALSE;
        dcb->fAbortOnError = FALSE;
        dcb->XonLim = 2048;
        dcb->XoffLim = 512;
        dcb->XonChar = (char)17; //DC1
        dcb->XoffChar = (char)19; //DC3
        //<- since 0.8

        if(SetCommState(hComm, dcb)){

        	//since 2.1.0 -> previously setted timeouts by another application should be cleared
        	COMMTIMEOUTS *lpCommTimeouts = new COMMTIMEOUTS();
        	lpCommTimeouts->ReadIntervalTimeout = 0;
        	lpCommTimeouts->ReadTotalTimeoutConstant = 0;
        	lpCommTimeouts->ReadTotalTimeoutMultiplier = 0;
        	lpCommTimeouts->WriteTotalTimeoutConstant = 0;
        	lpCommTimeouts->WriteTotalTimeoutMultiplier = 0;
        	if(SetCommTimeouts(hComm, lpCommTimeouts)){
        		returnValue = JNI_TRUE;
        	}
        	delete lpCommTimeouts;
        	//<- since 2.1.0
        }
    }
    delete dcb;
    return returnValue;
}

/*
 * PurgeComm
 */
JNIEXPORT jboolean JNICALL Java_jssc_SerialNativeInterface_purgePort
  (JNIEnv *env, jobject object, jlong portHandle, jint flags){
    HANDLE hComm = (HANDLE)portHandle;
    DWORD dwFlags = (DWORD)flags;
    return (PurgeComm(hComm, dwFlags) ? JNI_TRUE : JNI_FALSE);
}

/*
 * Port closing
 */
JNIEXPORT jboolean JNICALL Java_jssc_SerialNativeInterface_closePort
  (JNIEnv *env, jobject object, jlong portHandle){
    HANDLE hComm = (HANDLE)portHandle;
    return (CloseHandle(hComm) ? JNI_TRUE : JNI_FALSE);
}

/*
 * Set events mask
 */
JNIEXPORT jboolean JNICALL Java_jssc_SerialNativeInterface_setEventsMask
  (JNIEnv *env, jobject object, jlong portHandle, jint mask){
    HANDLE hComm = (HANDLE)portHandle;
    DWORD dwEvtMask = (DWORD)mask;
    return (SetCommMask(hComm, dwEvtMask) ? JNI_TRUE : JNI_FALSE);
}

/*
 * Get events mask
 */
JNIEXPORT jint JNICALL Java_jssc_SerialNativeInterface_getEventsMask
  (JNIEnv *env, jobject object, jlong portHandle){
    HANDLE hComm = (HANDLE)portHandle;
    DWORD lpEvtMask;
    if(GetCommMask(hComm, &lpEvtMask)){
        return (jint)lpEvtMask;
    }
    else {
        return -1;
    }
}

/*
 * Change RTS line state (ON || OFF)
 */
JNIEXPORT jboolean JNICALL Java_jssc_SerialNativeInterface_setRTS
  (JNIEnv *env, jobject object, jlong portHandle, jboolean enabled){
    HANDLE hComm = (HANDLE)portHandle;
    if(enabled == JNI_TRUE){
        return (EscapeCommFunction(hComm, SETRTS) ? JNI_TRUE : JNI_FALSE);
    }
    else {
        return (EscapeCommFunction(hComm, CLRRTS) ? JNI_TRUE : JNI_FALSE);
    }
}

/*
 * Change DTR line state (ON || OFF)
 */
JNIEXPORT jboolean JNICALL Java_jssc_SerialNativeInterface_setDTR
  (JNIEnv *env, jobject object, jlong portHandle, jboolean enabled){
    HANDLE hComm = (HANDLE)portHandle;
    if(enabled == JNI_TRUE){
        return (EscapeCommFunction(hComm, SETDTR) ? JNI_TRUE : JNI_FALSE);
    }
    else {
        return (EscapeCommFunction(hComm, CLRDTR) ? JNI_TRUE : JNI_FALSE);
    }
}

/*
 * Write data to port
 * portHandle - port handle
 * buffer - byte array for sending
 */
JNIEXPORT jboolean JNICALL Java_jssc_SerialNativeInterface_writeBytes
  (JNIEnv *env, jobject object, jlong portHandle, jbyteArray buffer){
    HANDLE hComm = (HANDLE)portHandle;
    DWORD lpNumberOfBytesTransferred;
    DWORD lpNumberOfBytesWritten;
    OVERLAPPED *overlapped = new OVERLAPPED();
    jboolean returnValue = JNI_FALSE;
    jbyte* jBuffer = env->GetByteArrayElements(buffer, JNI_FALSE);
    overlapped->hEvent = CreateEventA(NULL, true, false, NULL);
    if(WriteFile(hComm, jBuffer, (DWORD)env->GetArrayLength(buffer), &lpNumberOfBytesWritten, overlapped)){
        returnValue = JNI_TRUE;
    }
    else if(GetLastError() == ERROR_IO_PENDING){
        if(WaitForSingleObject(overlapped->hEvent, INFINITE) == WAIT_OBJECT_0){
            if(GetOverlappedResult(hComm, overlapped, &lpNumberOfBytesTransferred, false)){
                returnValue = JNI_TRUE;
            }
        }
    }
    env->ReleaseByteArrayElements(buffer, jBuffer, 0);
    CloseHandle(overlapped->hEvent);
    delete overlapped;
    return returnValue;
}

/*
 * Read data from port
 * portHandle - port handle
 * byteCount - count of bytes for reading
 */
JNIEXPORT jbyteArray JNICALL Java_jssc_SerialNativeInterface_readBytes
  (JNIEnv *env, jobject object, jlong portHandle, jint byteCount){
    HANDLE hComm = (HANDLE)portHandle;
    DWORD lpNumberOfBytesTransferred;
    DWORD lpNumberOfBytesRead;
    OVERLAPPED *overlapped = new OVERLAPPED();
    jbyte lpBuffer[byteCount];
    jbyteArray returnArray = env->NewByteArray(byteCount);
    overlapped->hEvent = CreateEventA(NULL, true, false, NULL);
    if(ReadFile(hComm, lpBuffer, (DWORD)byteCount, &lpNumberOfBytesRead, overlapped)){
        env->SetByteArrayRegion(returnArray, 0, byteCount, lpBuffer);
    }
    else if(GetLastError() == ERROR_IO_PENDING){
        if(WaitForSingleObject(overlapped->hEvent, INFINITE) == WAIT_OBJECT_0){
            if(GetOverlappedResult(hComm, overlapped, &lpNumberOfBytesTransferred, false)){
                env->SetByteArrayRegion(returnArray, 0, byteCount, lpBuffer);
            }
        }
    }
    CloseHandle(overlapped->hEvent);
    delete overlapped;
    return returnArray;
}

/*
 * Get bytes count in serial port buffers (Input and Output)
 */
JNIEXPORT jintArray JNICALL Java_jssc_SerialNativeInterface_getBuffersBytesCount
  (JNIEnv *env, jobject object, jlong portHandle){
	HANDLE hComm = (HANDLE)portHandle;
	jint returnValues[2];
	returnValues[0] = -1;
	returnValues[1] = -1;
	jintArray returnArray = env->NewIntArray(2);
	DWORD lpErrors;
	COMSTAT *comstat = new COMSTAT();
	if(ClearCommError(hComm, &lpErrors, comstat)){
		returnValues[0] = (jint)comstat->cbInQue;
		returnValues[1] = (jint)comstat->cbOutQue;
	}
	else {
		returnValues[0] = -1;
		returnValues[1] = -1;
	}
	delete comstat;
	env->SetIntArrayRegion(returnArray, 0, 2, returnValues);
	return returnArray;
}

//since 0.8 ->
const jint FLOWCONTROL_NONE = 0;
const jint FLOWCONTROL_RTSCTS_IN = 1;
const jint FLOWCONTROL_RTSCTS_OUT = 2;
const jint FLOWCONTROL_XONXOFF_IN = 4;
const jint FLOWCONTROL_XONXOFF_OUT = 8;
//<- since 0.8

/*
 * Setting flow control mode
 *
 * since 0.8
 */
JNIEXPORT jboolean JNICALL Java_jssc_SerialNativeInterface_setFlowControlMode
  (JNIEnv *env, jobject object, jlong portHandle, jint mask){
	HANDLE hComm = (HANDLE)portHandle;
	jboolean returnValue = JNI_FALSE;
	DCB *dcb = new DCB();
	if(GetCommState(hComm, dcb)){
		dcb->fRtsControl = RTS_CONTROL_ENABLE;
		dcb->fOutxCtsFlow = FALSE;
		dcb->fOutX = FALSE;
		dcb->fInX = FALSE;
		if(mask != FLOWCONTROL_NONE){
			if((mask & FLOWCONTROL_RTSCTS_IN) == FLOWCONTROL_RTSCTS_IN){
				dcb->fRtsControl = RTS_CONTROL_HANDSHAKE;
			}
			if((mask & FLOWCONTROL_RTSCTS_OUT) == FLOWCONTROL_RTSCTS_OUT){
				dcb->fOutxCtsFlow = TRUE;
			}
			if((mask & FLOWCONTROL_XONXOFF_IN) == FLOWCONTROL_XONXOFF_IN){
				dcb->fInX = TRUE;
			}
			if((mask & FLOWCONTROL_XONXOFF_OUT) == FLOWCONTROL_XONXOFF_OUT){
				dcb->fOutX = TRUE;
			}
		}
		if(SetCommState(hComm, dcb)){
			returnValue = JNI_TRUE;
		}
	}
	delete dcb;
	return returnValue;
}

/*
 * Getting flow control mode
 *
 * since 0.8
 */
JNIEXPORT jint JNICALL Java_jssc_SerialNativeInterface_getFlowControlMode
  (JNIEnv *env, jobject object, jlong portHandle){
	HANDLE hComm = (HANDLE)portHandle;
	jint returnValue = 0;
	DCB *dcb = new DCB();
	if(GetCommState(hComm, dcb)){
		if(dcb->fRtsControl == RTS_CONTROL_HANDSHAKE){
			returnValue |= FLOWCONTROL_RTSCTS_IN;
		}
		if(dcb->fOutxCtsFlow == TRUE){
			returnValue |= FLOWCONTROL_RTSCTS_OUT;
		}
		if(dcb->fInX == TRUE){
			returnValue |= FLOWCONTROL_XONXOFF_IN;
		}
		if(dcb->fOutX == TRUE){
			returnValue |= FLOWCONTROL_XONXOFF_OUT;
		}
	}
	delete dcb;
	return returnValue;
}

/*
 * Send break for setted duration
 *
 * since 0.8
 */
JNIEXPORT jboolean JNICALL Java_jssc_SerialNativeInterface_sendBreak
  (JNIEnv *env, jobject object, jlong portHandle, jint duration){
	HANDLE hComm = (HANDLE)portHandle;
	jboolean returnValue = JNI_FALSE;
	if(duration > 0){
		if(SetCommBreak(hComm) > 0){
			Sleep(duration);
			if(ClearCommBreak(hComm) > 0){
				returnValue = JNI_TRUE;
			}
		}
	}
	return returnValue;
}

/*
 * Wait event
 * portHandle - port handle
 */
JNIEXPORT jobjectArray JNICALL Java_jssc_SerialNativeInterface_waitEvents
  (JNIEnv *env, jobject object, jlong portHandle) {
    HANDLE hComm = (HANDLE)portHandle;
    DWORD lpEvtMask = 0;
    DWORD lpNumberOfBytesTransferred = 0;
    OVERLAPPED *overlapped = new OVERLAPPED();
    jclass intClass = env->FindClass("[I");
    jobjectArray returnArray;
    boolean functionSuccessful = false;
    overlapped->hEvent = CreateEventA(NULL, true, false, NULL);
    if(WaitCommEvent(hComm, &lpEvtMask, overlapped)){
        functionSuccessful = true;
    }
    else if(GetLastError() == ERROR_IO_PENDING){
        if(WaitForSingleObject(overlapped->hEvent, INFINITE) == WAIT_OBJECT_0){
            if(GetOverlappedResult(hComm, overlapped, &lpNumberOfBytesTransferred, false)){
                functionSuccessful = true;
            }
        }
    }
    if(functionSuccessful){
        boolean executeGetCommModemStatus = false;
        boolean executeClearCommError = false;
        DWORD events[9];//fixed since 0.8 (old value is 8)
        jint eventsCount = 0;
        if((EV_BREAK & lpEvtMask) == EV_BREAK){
            events[eventsCount] = EV_BREAK;
            eventsCount++;
        }
        if((EV_CTS & lpEvtMask) == EV_CTS){
            events[eventsCount] = EV_CTS;
            eventsCount++;
            executeGetCommModemStatus = true;
        }
        if((EV_DSR & lpEvtMask) == EV_DSR){
            events[eventsCount] = EV_DSR;
            eventsCount++;
            executeGetCommModemStatus = true;
        }
        if((EV_ERR & lpEvtMask) == EV_ERR){
            events[eventsCount] = EV_ERR;
            eventsCount++;
            executeClearCommError = true;
        }
        if((EV_RING & lpEvtMask) == EV_RING){
            events[eventsCount] = EV_RING;
            eventsCount++;
            executeGetCommModemStatus = true;
        }
        if((EV_RLSD & lpEvtMask) == EV_RLSD){
            events[eventsCount] = EV_RLSD;
            eventsCount++;
            executeGetCommModemStatus = true;
        }
        if((EV_RXCHAR & lpEvtMask) == EV_RXCHAR){
            events[eventsCount] = EV_RXCHAR;
            eventsCount++;
            executeClearCommError = true;
        }
        if((EV_RXFLAG & lpEvtMask) == EV_RXFLAG){
            events[eventsCount] = EV_RXFLAG;
            eventsCount++;
            executeClearCommError = true;
        }
        if((EV_TXEMPTY & lpEvtMask) == EV_TXEMPTY){
            events[eventsCount] = EV_TXEMPTY;
            eventsCount++;
            executeClearCommError = true;
        }
        /*
         * Execute GetCommModemStatus function if it's needed (get lines status)
         */
        jint statusCTS = 0;
        jint statusDSR = 0;
        jint statusRING = 0;
        jint statusRLSD = 0;
        boolean successGetCommModemStatus = false;
        if(executeGetCommModemStatus){
            DWORD lpModemStat;
            if(GetCommModemStatus(hComm, &lpModemStat)){
                successGetCommModemStatus = true;
                if((MS_CTS_ON & lpModemStat) == MS_CTS_ON){
                    statusCTS = 1;
                }
                if((MS_DSR_ON & lpModemStat) == MS_DSR_ON){
                    statusDSR = 1;
                }
                if((MS_RING_ON & lpModemStat) == MS_RING_ON){
                    statusRING = 1;
                }
                if((MS_RLSD_ON & lpModemStat) == MS_RLSD_ON){
                    statusRLSD = 1;
                }
            }
            else {
                jint lastError = (jint)GetLastError();
                statusCTS = lastError;
                statusDSR = lastError;
                statusRING = lastError;
                statusRLSD = lastError;
            }
        }
        /*
         * Execute ClearCommError function if it's needed (get count of bytes in buffers and errors)
         */
        jint bytesCountIn = 0;
        jint bytesCountOut = 0;
        jint communicationsErrors = 0;
        boolean successClearCommError = false;
        if(executeClearCommError){
            DWORD lpErrors;
            COMSTAT *comstat = new COMSTAT();
            if(ClearCommError(hComm, &lpErrors, comstat)){
                successClearCommError = true;
                bytesCountIn = (jint)comstat->cbInQue;
                bytesCountOut = (jint)comstat->cbOutQue;
                communicationsErrors = (jint)lpErrors;
            }
            else {
                jint lastError = (jint)GetLastError();
                bytesCountIn = lastError;
                bytesCountOut = lastError;
                communicationsErrors = lastError;
            }
            delete comstat;
        }
        /*
         * Create int[][] for events values
         */
        returnArray = env->NewObjectArray(eventsCount, intClass, NULL);
        /*
         * Set events values
         */
        for(jint i = 0; i < eventsCount; i++){
            jint returnValues[2];
            switch(events[i]){
                case EV_BREAK:
                    returnValues[0] = (jint)events[i];
                    returnValues[1] = 0;
                    goto forEnd;
                case EV_CTS:
                    returnValues[1] = statusCTS;
                    goto modemStatus;
                case EV_DSR:
                    returnValues[1] = statusDSR;
                    goto modemStatus;
                case EV_ERR:
                    returnValues[1] = communicationsErrors;
                    goto bytesAndErrors;
                case EV_RING:
                    returnValues[1] = statusRING;
                    goto modemStatus;
                case EV_RLSD:
                    returnValues[1] = statusRLSD;
                    goto modemStatus;
                case EV_RXCHAR:
                    returnValues[1] = bytesCountIn;
                    goto bytesAndErrors;
                case EV_RXFLAG:
                    returnValues[1] = bytesCountIn;
                    goto bytesAndErrors;
                case EV_TXEMPTY:
                    returnValues[1] = bytesCountOut;
                    goto bytesAndErrors;
                default:
                    returnValues[0] = (jint)events[i];
                    returnValues[1] = 0;
                    goto forEnd;
            };
            modemStatus: {
                if(successGetCommModemStatus){
                    returnValues[0] = (jint)events[i];
                }
                else {
                    returnValues[0] = -1;
                }
                goto forEnd;
            }
            bytesAndErrors: {
                if(successClearCommError){
                    returnValues[0] = (jint)events[i];
                }
                else {
                    returnValues[0] = -1;
                }
                goto forEnd;
            }
            forEnd: {
                jintArray singleResultArray = env->NewIntArray(2);
                env->SetIntArrayRegion(singleResultArray, 0, 2, returnValues);
                env->SetObjectArrayElement(returnArray, i, singleResultArray);
            };
        }
    }
    else {
        returnArray = env->NewObjectArray(1, intClass, NULL);
        jint returnValues[2];
        returnValues[0] = -1;
        returnValues[1] = (jint)GetLastError();
        jintArray singleResultArray = env->NewIntArray(2);
        env->SetIntArrayRegion(singleResultArray, 0, 2, returnValues);
        env->SetObjectArrayElement(returnArray, 0, singleResultArray);
    };
    CloseHandle(overlapped->hEvent);
    delete overlapped;
    return returnArray;
}

/*
 * Get serial port names
 */
JNIEXPORT jobjectArray JNICALL Java_jssc_SerialNativeInterface_getSerialPortNames
  (JNIEnv *env, jobject object){
    HKEY phkResult;
    LPCSTR lpSubKey = "HARDWARE\\DEVICEMAP\\SERIALCOMM\\";
    jobjectArray returnArray = NULL;
    if(RegOpenKeyExA(HKEY_LOCAL_MACHINE, lpSubKey, 0, KEY_READ, &phkResult) == ERROR_SUCCESS){
        boolean hasMoreElements = true;
        DWORD keysCount = 0;
        char valueName[256];
        DWORD valueNameSize;
        DWORD enumResult;
        while(hasMoreElements){
            valueNameSize = 256;
            enumResult = RegEnumValueA(phkResult, keysCount, valueName, &valueNameSize, NULL, NULL, NULL, NULL);
            if(enumResult == ERROR_SUCCESS){
                keysCount++;
            }
            else if(enumResult == ERROR_NO_MORE_ITEMS){
                hasMoreElements = false;
            }
            else {
                hasMoreElements = false;
            }
        }
        if(keysCount > 0){
            jclass stringClass = env->FindClass("java/lang/String");
            returnArray = env->NewObjectArray((jsize)keysCount, stringClass, NULL);
            char lpValueName[256];
            DWORD lpcchValueName;
            byte lpData[256];
            DWORD lpcbData;
            DWORD result;
            for(DWORD i = 0; i < keysCount; i++){
                lpcchValueName = 256;
                lpcbData = 256;
                result = RegEnumValueA(phkResult, i, lpValueName, &lpcchValueName, NULL, NULL, lpData, &lpcbData);
                if(result == ERROR_SUCCESS){
                    env->SetObjectArrayElement(returnArray, i, env->NewStringUTF((char*)lpData));
                }
            }
        }
        CloseHandle(phkResult);
    }
    return returnArray;
}

/*
 * Get lines status
 *
 * returnValues[0] - CTS
 * returnValues[1] - DSR
 * returnValues[2] - RING
 * returnValues[3] - RLSD
 *
 */
JNIEXPORT jintArray JNICALL Java_jssc_SerialNativeInterface_getLinesStatus
  (JNIEnv *env, jobject object, jlong portHandle){
    HANDLE hComm = (HANDLE)portHandle;
    DWORD lpModemStat;
    jint returnValues[4];
    for(jint i = 0; i < 4; i++){
        returnValues[i] = 0;
    }
    jintArray returnArray = env->NewIntArray(4);
    if(GetCommModemStatus(hComm, &lpModemStat)){
        if((MS_CTS_ON & lpModemStat) == MS_CTS_ON){
            returnValues[0] = 1;
        }
        if((MS_DSR_ON & lpModemStat) == MS_DSR_ON){
            returnValues[1] = 1;
        }
        if((MS_RING_ON & lpModemStat) == MS_RING_ON){
            returnValues[2] = 1;
        }
        if((MS_RLSD_ON & lpModemStat) == MS_RLSD_ON){
            returnValues[3] = 1;
        }
    }
    env->SetIntArrayRegion(returnArray, 0, 4, returnValues);
    return returnArray;
}

/*
 * Get the properties of the port.
 * Note: Could be refactored but breaks the seemingly one-method convention used
 * throughout
 */
JNIEXPORT jobjectArray JNICALL Java_jssc_SerialNativeInterface_getPortProperties
        (JNIEnv * env, jobject cls, jstring portName) {
    std::cout << "Attempting to find the devices" << std::endl;

    // Convert to C++ friendly name
    const char* name = (const char*) env->GetStringUTFChars(portName, NULL);
    const size_t nameSize = strlen(name);

    // Create the Java types we will be returning
    jclass stringClass = env->FindClass("Ljava/lang/String;");
    jobjectArray ret = env->NewObjectArray(5, stringClass, NULL);

    // Retrieve the IDs associated with the name Ports for serial ports
    // Too much work will go into hard coding it
    DWORD size = 1;
    DWORD required_size;
    GUID * list = new GUID[1];
    if (!SetupDiClassGuidsFromName("Ports", list, size, &required_size)) {
        delete list;
        env->ReleaseStringUTFChars(portName, name);
        return ret;
    }

    if (required_size > 1) {
        delete list;
        list = new GUID[required_size];
        size = required_size;
        if (!SetupDiClassGuidsFromName("Ports", list, size, &required_size)) {
            delete list;
            env->ReleaseStringUTFChars(portName, name);
            return ret;
        }
    }

    for (int i = 0; i < size; i++) {
        GUID guid = list[i];
        // Get device info from the GUID
        HDEVINFO informationSet = SetupDiGetClassDevs(&guid, NULL, NULL, DIGCF_PRESENT);
        SP_DEVINFO_DATA device;
        device.cbSize = sizeof(SP_DEVINFO_DATA);

        // Enumerate over the devices
        for (int j = 0; SetupDiEnumDeviceInfo(informationSet, j, &device); j++) {

            // Get the registry key
            HKEY key = SetupDiOpenDevRegKey(informationSet, &device, DICS_FLAG_GLOBAL, 0, DIREG_DEV, KEY_READ);

            // Legacy support for Windows versions older than XP for some reason
            // Can go to RegGetValue if only want Windows XP and higher
            // It is safer and cleaner to use

            // Create the buffer with a proper null terminating character
            unsigned char buffer[nameSize + 1];
            buffer[nameSize] = '\0';
            ULONG bufferSize = (ULONG) nameSize;

            // Check if the PortName is a property of this device
            if (RegQueryValueEx(key, "PortName", NULL, NULL, buffer, &bufferSize) != ERROR_SUCCESS)
                continue;

            RegCloseKey(key);

            // Compare whether or not the key found was the one we wanted
            if (strncmp((char *) buffer, name, sizeof(name)) != 0)
                continue;

            // Initialize properties that are desired
            // 100 characters will be largest but should be able to be expanded
            // Contains the vid and pid of the device
            // Will attempt to get everything
            char* hardwareId = new char[51];
            BYTE* hardwareIdFallback = new BYTE[51];
            size_t hardwareIdSize = 50;
            size_t hardwareIdFallbackSize = 50;

            hardwareId[50] = '\0';
            hardwareIdFallback[50] = '\0';

            bool hardwareIdFetched = false;
            bool fetchFromRegistry = true;


            // Get the hardware ID and pid and vid
            // Done with registry fallbacks and buffer checking
            // Maybe add a go-to?
            if (!SetupDiGetDeviceInstanceId(
                informationSet, 
                &device, 
                hardwareId,
                hardwareIdSize,
                &required_size)) {

                // Getting it failed but was the buffer the problem
                if (GetLastError() == ERROR_INSUFFICIENT_BUFFER) {

                    // Buffer was the problem
                    hardwareId = new char[required_size];
                    fetchFromRegistry = false;

                    if (!SetupDiGetDeviceInstanceId(
                        informationSet,
                        &device,
                        hardwareId,
                        required_size,
                        NULL)) {
                        // Attempt to do fallback as the lookup failed again
                        hardwareIdFetched = false;
                        fetchFromRegistry = true;
                    }
                    else {
                        hardwareIdFetched = true;
                        fetchFromRegistry = false;
                    }
                }

                // If we need to get to registry, fallback on this method
                if (fetchFromRegistry) {
                    if (!SetupDiGetDeviceRegistryProperty(
                            informationSet,
                            &device,
                            SPDRP_HARDWAREID,
                            NULL,
                            hardwareIdFallback,
                            hardwareIdFallbackSize,
                            &required_size)) {
                        if (GetLastError() == ERROR_INSUFFICIENT_BUFFER) {
                            // Buffer was the problem, resize
                            hardwareIdFallback = new BYTE[required_size];

                            if (!SetupDiGetDeviceRegistryProperty(
                                informationSet,
                                &device,
                                SPDRP_HARDWAREID,
                                NULL,
                                hardwareIdFallback,
                                required_size,
                                NULL)) {
                                hardwareIdFetched = false;
                            }
                            else {
                                hardwareIdFetched = true;
                            }
                        }
                        else {
                            // Buffer was not the issue and we have no fallback
                            hardwareIdFetched = false;
                        }
                    }
                    else {
                        // We successfully fetched the required ID.
                        hardwareIdFetched = true;
                    }
                }
            } 
            else {
                fetchFromRegistry = false;
                hardwareIdFetched = true;
            }

            if (hardwareIdFetched && fetchFromRegistry)
                hardwareId = (char*) hardwareIdFallback;

            const char * vid;
            const char * pid;
            BYTE * manufacturer;
            BYTE * product;
            BYTE * serial;

            // Matches VID PID AND REV, but unsure if we need REV for now
            std::regex usb("USB\\(?:(VID_[0-9A-Za-z]{1,4}))?(?:&(PID_[0-9A-Za-z]{1,4}))?(?:&(REV_[0-9A-Za-z]{1,4}))?");
            std::regex generic("(?:(VID_[0-9A-Za-z]{1,4})).+?(?:(PID_[0-9A-Za-z]{1,4}))");
            if (hardwareIdFetched) {
                std::cmatch m;
                if (std::regex_search(hardwareId, m, usb)) {
                    for (std::cmatch::iterator it = m.begin(); it != m.end(); it++) {
                        std::string match = std::string(it->str());
                        if (match.compare(0, 4, "VID_")) {
                            vid = match.substr(4).c_str();
                        }
                        else if (match.compare(0, 4, "PID_")) {
                            pid = match.substr(4).c_str();
                        }
                    }
                }
                else if (std::regex_search(hardwareId, m, generic)) {
                    for (std::cmatch::iterator it = m.begin(); it != m.end(); it++) {
                        std::string match = std::string(it->str());
                        if (match.compare(0, 4, "VID_")) {
                            vid = match.substr(4).c_str();
                        }
                        else if (match.compare(0, 4, "PID_")) {
                            pid = match.substr(4).c_str();
                        }
                    }
                }
                else {
                    vid = new char[1] { '\0' };
                    pid = new char[1] { '\0' };

                }
            }

            // Find the manufacturer
            if (!SetupDiGetDeviceRegistryProperty(
                informationSet,
                &device,
                SPDRP_MFG,
                NULL,
                NULL,
                0,
                &required_size)) {
                if (GetLastError() == ERROR_INSUFFICIENT_BUFFER) {
                    manufacturer = new BYTE[required_size];
                    if (!SetupDiGetDeviceRegistryProperty(
                        informationSet,
                        &device,
                        SPDRP_MFG,
                        NULL,
                        manufacturer,
                        required_size,
                        NULL))
                        manufacturer = new BYTE[1] { '\0' };
                }
                else {
                    manufacturer = new BYTE[1] { '\0' };
                }
            }

            // Get the name of the product
            if (!SetupDiGetDeviceRegistryProperty(
                informationSet,
                &device,
                SPDRP_FRIENDLYNAME,
                NULL,
                NULL,
                0,
                &required_size)) {
                if (GetLastError() == ERROR_INSUFFICIENT_BUFFER) {
                    product = new BYTE[required_size];
                    if (!SetupDiGetDeviceRegistryProperty(
                        informationSet,
                        &device,
                        SPDRP_FRIENDLYNAME,
                        NULL,
                        product,
                        required_size,
                        NULL))
                        product = new BYTE[1] { '\0' };
                }
                else {
                    product = new BYTE[1] { '\0' };
                }
            }


            // Unsure how to get serial at this point
            serial = new BYTE[1] { '\0' };

            // Cleanup and return
            delete list;
            SetupDiDestroyDeviceInfoList(informationSet);
            delete hardwareId;
            delete hardwareIdFallback;
            env->ReleaseStringUTFChars(portName, name);

            jstring pidString = env->NewStringUTF(pid);
            env->SetObjectArrayElement(ret, 0, pidString);
            env->DeleteLocalRef(pidString);
            delete pid;

            jstring vidString = env->NewStringUTF(vid);
            env->SetObjectArrayElement(ret, 1, vidString);
            env->DeleteLocalRef(vidString);
            delete vid;

            jstring mfgString = env->NewStringUTF((char*) manufacturer);
            env->SetObjectArrayElement(ret, 2, mfgString);
            env->DeleteLocalRef(mfgString);
            delete manufacturer;

            jstring productString = env->NewStringUTF((char*) product);
            env->SetObjectArrayElement(ret, 3, productString);
            env->DeleteLocalRef(productString);
            delete product;

            jstring serialString = env->NewStringUTF((char*) serial);
            env->SetObjectArrayElement(ret, 4, serialString);
            env->DeleteLocalRef(serialString);
            delete serial;

            return ret;
        }

        // Destroy the list of devices
        SetupDiDestroyDeviceInfoList(informationSet);
    }

    // A bit of cleanup
    delete list;
    env->ReleaseStringUTFChars(portName, name);
    return ret;
}
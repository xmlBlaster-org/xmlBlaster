/*----------------------------------------------------------------------------
Name:      xmlBlaster/src/c/util/msgUtil.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Generic helper code, used by Queue implementation and xmlBlaster client code
           Don't add any queue specific or xmlBlaster client specific code!
Author:    "Marcel Ruff" <xmlBlaster@marcelruff.info>
-----------------------------------------------------------------------------*/
#ifndef _XMLBLASTER_HELPER_H
#define _XMLBLASTER_HELPER_H

#include <util/basicDefs.h> /* for int64_t (C99), Dll_Export, inline, bool etc. */

#if defined(_WINDOWS)
#  include <pthreads/pthread.h> /* Our pthreads.h: For timespec, for logging output of thread ID, for Windows and WinCE downloaded from http://sources.redhat.com/pthreads-win32 */
#else
# include <pthread.h>
# define XB_USE_PTHREADS 1
#endif

#ifdef __cplusplus
#ifndef XMLBLASTER_C_COMPILE_AS_CPP /* 'g++ -DXMLBLASTER_C_COMPILE_AS_CPP ...' allows to compile the lib as C++ code */
extern "C" {
#endif
#endif

/**
 * Holds arbitrary raw data and its length
 */
typedef struct {
   size_t dataLen;
   char *data;
} BlobHolder;

/**
 * Holds error text
 */
#define EXCEPTIONSTRUCT_ERRORCODE_LEN 56
#define EXCEPTIONSTRUCT_MESSAGE_LEN 1024
typedef struct ExceptionStruct {    /* This name is need for C++ forward declaration 'struct ExceptionStruct; */
   int remote; /**< true if exception is from remote (changed from bool to int to be C/C++ alignment compatible) */
   char errorCode[EXCEPTIONSTRUCT_ERRORCODE_LEN];
   char message[EXCEPTIONSTRUCT_MESSAGE_LEN];
   /* ExceptionStruct *embedded;  who allocates/frees it? */
} ExceptionStruct;
Dll_Export extern void initializeExceptionStruct(ExceptionStruct *exception);
Dll_Export extern void embedException(ExceptionStruct *exception, const char *newErrorCode, const char *newMessage, const ExceptionStruct *embed);
Dll_Export extern const char *getExceptionStr(char *out, int outSize, const ExceptionStruct *exception);

typedef enum XMLBLASTER_LOG_LEVEL_ENUM {
   /*LOG_NOLOG=0,  don't use */
   LOG_ERROR=1,  /**< supported, use for programming errors */
   LOG_WARN=2,   /**< supported, use for user errors and wrong configurations */
   LOG_INFO=3,   /**< supported, use for success information only */
   /*LOG_CALL=4,  don't use */
   /*LOG_TIME=5,  don't use */
   LOG_TRACE=6,  /**< supported, use for debugging purposes */
   LOG_DUMP=7    /**< supported, use for debugging purposes */
   /*LOG_PLAIN=8  don't use */
} XMLBLASTER_LOG_LEVEL;
typedef void  ( * XmlBlasterLogging)(void *logUserP, XMLBLASTER_LOG_LEVEL currLevel, XMLBLASTER_LOG_LEVEL level, const char *location, const char *fmt, ...);
Dll_Export extern void xmlBlasterDefaultLogging(void *logUserP, 
                              XMLBLASTER_LOG_LEVEL currLevel,
                              XMLBLASTER_LOG_LEVEL level,
                              const char *location, const char *fmt, ...);
Dll_Export extern XMLBLASTER_LOG_LEVEL parseLogLevel(const char *logLevelStr);
Dll_Export extern const char *getLogLevelStr(XMLBLASTER_LOG_LEVEL logLevel);
Dll_Export extern bool doLog(XMLBLASTER_LOG_LEVEL currLevel, XMLBLASTER_LOG_LEVEL level);

Dll_Export extern char *getStackTrace(int maxNumOfLines);
Dll_Export extern void sleepMillis(long millis);
Dll_Export extern int64_t getTimestamp(void);   /* if no 'int64_t=long long' support we need a workaround */
Dll_Export extern bool getAbsoluteTime(long relativeTimeFromNow, struct timespec *abstime); /* timespec forces pthread */
Dll_Export extern char *strFromBlobAlloc(const char *blob, const size_t len);
Dll_Export extern char *strcpyAlloc(const char *src);
Dll_Export extern char *strcpyRealloc(char **dest, const char *src);
Dll_Export extern char *strcatAlloc(char **dest, const char *src);
Dll_Export extern char *strncpy0(char * const to, const char * const from, const size_t maxLen);
Dll_Export extern void trim(char *s);
Dll_Export extern void trimStart(char *s);
Dll_Export extern void trimEnd(char *s);
Dll_Export extern char *toReadableDump(char *data, size_t len);
Dll_Export extern const char* int64ToStr(char * const buf, int64_t val);
Dll_Export extern bool strToInt64(int64_t *val, const char * const str);
Dll_Export extern BlobHolder *blobcpyAlloc(BlobHolder *blob, const char *data, size_t dataLen);
Dll_Export extern BlobHolder *freeBlobHolderContent(BlobHolder *blob);
Dll_Export extern char *blobDump(BlobHolder *blob);
Dll_Export extern void freeBlobDump(char *blobDump); /* deprecated: use xmlBlasterFree() */

#ifdef __cplusplus
#ifndef XMLBLASTER_C_COMPILE_AS_CPP
}
#endif
#endif

#endif /* _XMLBLASTER_HELPER_H */


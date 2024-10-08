/*----------------------------------------------------------------------------
Name:      xmlBlaster/src/c/util/helper.h
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
#  if defined(XB_USE_PTHREADS)
#     include <pthreads/pthread.h> /* Our pthreads.h: For timespec, for logging output of thread ID, for Windows and WinCE downloaded from http://sources.redhat.com/pthreads-win32 */
#  else
struct timespec {
        long tv_sec;
        long tv_nsec;
};
#  endif
#else
# include <pthread.h>
# define XB_USE_PTHREADS 1
#endif

#ifdef __cplusplus
#ifndef XMLBLASTER_C_COMPILE_AS_CPP /* 'g++ -DXMLBLASTER_C_COMPILE_AS_CPP ...' allows to compile the lib as C++ code */
extern "C" {
#endif
#endif

/** Declare function pointer to write to socket */
typedef ssize_t ( * XmlBlasterWriteToSocketFunc)(void *xb, const int fd, const char *ptr, const size_t nbytes);
/** Declare function pointer to read from socket */
typedef ssize_t ( * XmlBlasterReadFromSocketFunc)(void *xb, const int fd, char *ptr, const size_t nbytes, XmlBlasterNumReadFunc fpNumRead, void *userP2);

/**
 * Holds a callback function pointer and its user pointer (the 'this' pointer).
 */
typedef struct {
   XmlBlasterReadFromSocketFunc readFromSocketFuncP;
   void *userP;
   /** Register listener for socket read progress, can be NULL if nobody is interested */
   /**
    * You can register a function pointer listener to be informed about the socket read progress.
    * The function will be called than and again during reading the socket with the currently read bytes.
    * Example:
    * <pre>
    * static void progress(void *numReadUserP, const size_t currBytesRead, const size_t nbytes) {
    *     printf("currBytesRead=%ld nbytes=%ld\n", (long)currBytesRead, (long)nbytes);
    * }
    * </pre>
    * The pointer may remain NULL.
    */
   XmlBlasterNumReadFunc numReadFuncP;
   /**
    * This will be looped through to numReadFuncP.
    */
   void *numReadUserP;
} XmlBlasterReadFromSocketFuncHolder;

/**
 * Holds a callback function pointer and its user pointer (the 'this' pointer, first argument).
 */
typedef struct {
   XmlBlasterWriteToSocketFunc writeToSocketFuncP;
   void *userP;
} XmlBlasterWriteToSocketFuncHolder;

/**
 * Holds arbitrary raw data and its length
 */
typedef struct {
   size_t dataLen;
   char *data;
} BlobHolder;

#define EXCEPTIONSTRUCT_ERRORCODE_LEN 56
#define EXCEPTIONSTRUCT_MESSAGE_LEN 1024
/**
 * Holds error text
 */
typedef struct ExceptionStruct {    /* This name is need for C++ forward declaration 'struct ExceptionStruct; */
   int remote; /**< true if exception is from remote (changed from bool to int to be C/C++ alignment compatible) */
   char errorCode[EXCEPTIONSTRUCT_ERRORCODE_LEN];
   char message[EXCEPTIONSTRUCT_MESSAGE_LEN];
   /* ExceptionStruct *embedded;  who allocates/frees it? */
} ExceptionStruct;
Dll_Export extern void initializeExceptionStruct(ExceptionStruct *exception);
Dll_Export extern void embedException(ExceptionStruct *exception, const char *newErrorCode, const char *newMessage, const ExceptionStruct *embed);
Dll_Export extern const char *getExceptionStr(char *out, int outSize, const ExceptionStruct *exception);

/* Must match XmlBlasterAccess.cs C# LogLevel */
typedef enum XMLBLASTER_LOG_LEVEL_ENUM {
   /*XMLBLASTER_LOG_NOLOG=0,  don't use */
   XMLBLASTER_LOG_ERROR=1,  /**< supported, use for programming errors */
   XMLBLASTER_LOG_WARN=2,   /**< supported, use for user errors and wrong configurations */
   XMLBLASTER_LOG_INFO=3,   /**< supported, use for success information only */
   /*XMLBLASTER_LOG_CALL=4,  don't use */
   /*XMLBLASTER_LOG_TIME=5,  don't use */
   XMLBLASTER_LOG_TRACE=6,  /**< supported, use for debugging purposes */
   XMLBLASTER_LOG_DUMP=7    /**< supported, use for debugging purposes */
   /*XMLBLASTER_LOG_PLAIN=8  don't use */
} XMLBLASTER_LOG_LEVEL;
typedef void  ( * XmlBlasterLogging)(void *logUserP, XMLBLASTER_LOG_LEVEL currLevel, XMLBLASTER_LOG_LEVEL level, const char *location, const char *fmt, ...);
Dll_Export extern void xmlBlasterDefaultLogging(void *logUserP,
                              XMLBLASTER_LOG_LEVEL currLevel,
                              XMLBLASTER_LOG_LEVEL level,
                              const char *location, const char *fmt, ...);
Dll_Export extern XMLBLASTER_LOG_LEVEL parseLogLevel(const char *logLevelStr);
Dll_Export extern const char *getLogLevelStr(XMLBLASTER_LOG_LEVEL logLevel);
Dll_Export extern bool doLog(XMLBLASTER_LOG_LEVEL currLevel, XMLBLASTER_LOG_LEVEL level);

Dll_Export extern char getInputKey(const char *str);
Dll_Export extern char *getStackTrace(int maxNumOfLines);
Dll_Export extern void sleepMillis(long millis);

#if defined(__FreeBSD__) || defined(__MacOSX__) || defined(__IPhoneOS__) || defined(__hpux__) || defined(__linux__)
#include <wchar.h>
#endif
Dll_Export extern char **convertWcsArgv(wchar_t **argv_wcs, int argc);

Dll_Export extern void freeArgv(char **argv, int argc);
Dll_Export extern char *strFromBlobAlloc(const char *blob, const size_t len);
/**
 * Extract token from string.
 * Thread save variant of strtok which returns empty string for two following delimiters.
 * <pre>
  char *p, *savePtr, *str = strcpyAlloc("\"H,ello\",joe,,");
  int count = 0;
  for (p=str;; count++, p = 0) {
	if ((token = strtok_r2(p, ",", &savePtr, '"')) == 0)
		break;
	printf("%d: %s\n", count, token);
  }
  xmlBlasterFree(str);
 * </pre>
 * returns
 * <pre>
0: H,ello
1: joe
2:
3:
 * </pre>
 * @param src Will be spoiled after this call, must be NULL after first call
 * @param delim Separator like ","
 * @param saveptr Holding current position
 * @param quotechar Typically '"', can be 0 to be ignored
 * @return the next token
 * @see strsep
 */
Dll_Export char *strtok_r2(char *src, const char *delim, char **saveptr, const char quotechar);
Dll_Export extern char *strcpyAlloc(const char *src);
Dll_Export extern char *strcpyAlloc0(const char *src, const size_t maxLen);
Dll_Export extern char *strcpyRealloc(char **dest, const char *src);
Dll_Export extern char *strcatAlloc(char **dest, const char *src);
Dll_Export extern char *strcatAlloc0(char **dest, const char *src, const size_t maxLen);
Dll_Export extern char *strncpy0(char * const to, const char * const from, const size_t maxLen);
Dll_Export extern char *strncat0(char * const to, const char * const from, const size_t max);
Dll_Export extern int snprintf0(char *buffer, size_t sizeOfBuffer, const char *format, ...);
Dll_Export extern char *trim(char *s);
Dll_Export extern char *trimStart(char *s);
Dll_Export extern char *trimEnd(char *s);
Dll_Export extern bool startsWith(const char * const str, const char * const token);
Dll_Export extern bool endsWith(const char * const str, const char * const token);
Dll_Export extern void xb_strerror(char *errnoStr, size_t sizeInBytes, int errnum);
Dll_Export extern char *toReadableDump(char *data, size_t len);
Dll_Export extern const char* int64ToStr(char * const buf, int64_t val);
Dll_Export extern bool strToInt64(int64_t *val, const char * const str);
Dll_Export extern bool strToLong(long *val, const char * const str);
Dll_Export extern bool strToULong(unsigned long *val, const char * const str);
Dll_Export extern bool strToInt(int *val, const char * const str);
Dll_Export extern BlobHolder *blobcpyAlloc(BlobHolder *blob, const char *data, size_t dataLen);
Dll_Export extern void freeBlobHolder(BlobHolder *blob);
Dll_Export extern BlobHolder *freeBlobHolderContent(BlobHolder *blob);
Dll_Export extern char *blobDump(BlobHolder *blob);
Dll_Export extern void freeBlobDump(char *blobDumpP); /* deprecated: use xmlBlasterFree() */
#if defined(XB_USE_PTHREADS)
Dll_Export extern unsigned long get_pthread_id(pthread_t t);
#endif

#ifdef __cplusplus
#ifndef XMLBLASTER_C_COMPILE_AS_CPP
}
#endif
#endif

#endif /* _XMLBLASTER_HELPER_H */


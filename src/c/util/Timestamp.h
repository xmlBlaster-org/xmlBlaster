/*
 * Timestamp.h
 */
#ifndef XMLBLASTER_UTIL_TIMESTAMP_H_
#define XMLBLASTER_UTIL_TIMESTAMP_H_

#include <util/basicDefs.h> /* for int64_t (C99), Dll_Export, inline, bool etc. */

#if defined(_WINDOWS)
#  if defined(XB_USE_PTHREADS)
#     include <pthreads/pthread.h> /* Our pthreads.h: For timespec, for logging output of thread ID, for Windows and WinCE downloaded from http://sources.redhat.com/pthreads-win32 */
#  else
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

/**
 * Unique for each call.
 * <p>
 * Is thread safe
 * @return unique for each call, with nano counter
 */
Dll_Export extern int64_t getTimestamp(void);   /* if no 'int64_t=long long' support we need a workaround */

/**
 * Unique for each call.
 * <p>
 * Is thread safe
 * @param buf Used to write the timestamp into
 * @param buflen The size of above
 * @return buf
 */
Dll_Export char *getTimestampStr(char *buf, size_t buflen);

/**
 * Fills the given abstime with absolute time, using the given timeout relativeTimeFromNow in milliseconds
 * On Linux < 2.5.64 does not support high resolution timers clock_gettime(),
 * but patches are available at http://sourceforge.net/projects/high-res-timers
 * @param relativeTimeFromNow the relative time from now in milliseconds
 * @param abstime
 * @return true If implemented
 */
Dll_Export extern bool getAbsoluteTime(long relativeTimeFromNow, struct timespec *abstime); /* timespec forces pthread */
/**
 * Get a human readable time string for logging.
 * @param timeStr out parameter, e.g. "12:34:46" or "2006-11-14 12:34:46"
 * @param bufSize The size of timeStr
 * @return timeStr Your parameter given (for easy usage in printf())
 */
Dll_Export extern const char *getCurrentTimeStr(char *timeStr, int bufSize);
/**
 * Get current timestamp string in ISO 8601 notation.
 * @param bufSize at least 26
 * @param timeStr out parameter, filled with e.g. "1997-07-16T19:20:30.45-02:00"
 * @return Your param timeStr for easy usage in printf() and such
 * @see http://en.wikipedia.org/wiki/ISO_8601
 */
Dll_Export extern const char *getCurrentLocalIsoTimestampStr(char *timeStr, int bufSize);


#ifdef __cplusplus
#ifndef XMLBLASTER_C_COMPILE_AS_CPP
}
#endif
#endif


#endif /* XMLBLASTER_UTIL_TIMESTAMP_H_ */

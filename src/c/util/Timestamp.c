/*----------------------------------------------------------------------------
Name:      Timestamp.c
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Time handling and unique counter
Author:    "Marcel Ruff" <xmlBlaster@marcelruff.info>
See:       http://www.xmlblaster.org/xmlBlaster/doc/requirements/protocol.socket.html
-----------------------------------------------------------------------------*/
#include <stdio.h>  /* snprintf */
#include <string.h> /* memset */
#include "basicDefs.h"
#include "helper.h"
#include "Timestamp.h"

#ifdef _WINDOWS
#  if defined(WINCE)
     /* time between jan 1, 1601 and jan 1, 1970 in units of 100 nanoseconds */
#    if !defined(PTW32_TIMESPEC_TO_FILETIME_OFFSET)
#       define PTW32_TIMESPEC_TO_FILETIME_OFFSET \
          ( ((LONGLONG) 27111902 << 32) + (LONGLONG) 3577643008 )
#    endif
#  else
#    include <sys/timeb.h>
#  endif
#  if XB_USE_PTHREADS
#    include <pthreads/pthread.h> /* Our pthreads.h: For logging output of thread ID, for Windows and WinCE downloaded from http://sources.redhat.com/pthreads-win32 */
#  endif
#else
#  include <unistd.h>         /* sleep(), only used in main */
#  include <errno.h>          /* errno */
#  include <sys/time.h>       /* sleep with select(), gettimeofday() */
#  if XB_USE_PTHREADS
#    include <pthread.h>      /* The original pthreads.h from the OS */
#  endif
#  include <inttypes.h>       /* PRId64 %lld format specifier */
#endif

#define  MICRO_SECS_PER_SECOND 1000000
#define  NANO_SECS_PER_SECOND MICRO_SECS_PER_SECOND * 1000


#if XB_USE_PTHREADS
static pthread_mutex_t tsMutex = PTHREAD_MUTEX_INITIALIZER;
/*pthread_mutex_init(&tsMutex, NULL); */
/*pthread_mutex_destroy(&tsMutex);*/
#endif

/**
 * Create a timestamp in nano seconds elapsed since 1970-01-01.
 * The timestamp is guaranteed to be ascending and unique.
 * <p>
 * The call is thread safe
 */
Dll_Export int64_t getTimestamp() {
   struct timespec abstime;
   int64_t timestamp;
   static int64_t lastNanos=0;

   getAbsoluteTime(0L, &abstime);

   timestamp = (int64_t)abstime.tv_sec * NANO_SECS_PER_SECOND;
   timestamp += abstime.tv_nsec;
#  if XB_USE_PTHREADS
      pthread_mutex_lock(&tsMutex);
#  endif
   if (timestamp <= lastNanos) {
      timestamp = lastNanos + 1;
   }
   lastNanos = timestamp;
#  if XB_USE_PTHREADS
      pthread_mutex_unlock(&tsMutex);
#  endif
   return timestamp;
}

Dll_Export char *getTimestampStr(char *buf, size_t buflen) {
   int64_t ts = getTimestamp(); /* INT_LEAST64_MAX=9223372036854775807 */
   SNPRINTF(buf, buflen, "%"PRId64, ts);
   return buf;
}

/**
 * Fills the given abstime with absolute time, using the given timeout relativeTimeFromNow in milliseconds
 * On Linux < 2.5.64 does not support high resolution timers clock_gettime(),
 * but patches are available at http://sourceforge.net/projects/high-res-timers
 * @param relativeTimeFromNow the relative time from now in milliseconds
 * @param abstime
 * @return true If implemented
 */
Dll_Export bool getAbsoluteTime(long relativeTimeFromNow, struct timespec *abstime)
{
# if defined(WINCE)
   /* Copied from pthreads_win32: thank you! */
   FILETIME ft;
   SYSTEMTIME st;
   GetSystemTime(&st);
   SystemTimeToFileTime(&st, &ft);
   /*
    * GetSystemTimeAsFileTime(&ft); would be faster,
    * but it does not exist on WinCE
    */
     /*
      * -------------------------------------------------------------------
      * converts FILETIME (as set by GetSystemTimeAsFileTime), where the time is
      * expressed in 100 nanoseconds from Jan 1, 1601,
      * into struct timespec
      * where the time is expressed in seconds and nanoseconds from Jan 1, 1970.
      * -------------------------------------------------------------------
      */
   abstime->tv_sec =
    (int) ((*(LONGLONG *) &ft - PTW32_TIMESPEC_TO_FILETIME_OFFSET) / 10000000);
   abstime->tv_nsec =
    (int) ((*(LONGLONG *) &ft - PTW32_TIMESPEC_TO_FILETIME_OFFSET -
            ((LONGLONG) abstime->tv_sec * (LONGLONG) 10000000)) * 100);

   if (relativeTimeFromNow > 0) {
      abstime->tv_sec += relativeTimeFromNow / 1000;
      abstime->tv_nsec += (relativeTimeFromNow % 1000) * 1000 * 1000;
   }
   if (abstime->tv_nsec >= NANO_SECS_PER_SECOND) {
      abstime->tv_nsec -= NANO_SECS_PER_SECOND;
      abstime->tv_sec += 1;
   }
   return true;

# elif defined(_WINDOWS)
   struct _timeb tm;
#  if _MSC_VER >= 1400  /* _WINDOWS: 1200->VC++6.0, 1310->VC++7.1 (2003), 1400->VC++8.0 (2005) */
        errno_t err = _ftime_s(&tm);
      if (err) return false;
#  else
        (void) _ftime(&tm);
#  endif

   abstime->tv_sec = (long)tm.time;
   abstime->tv_nsec = tm.millitm * 1000 * 1000; /* TODO !!! How to get the more precise current time on Win? */

   if (relativeTimeFromNow > 0) {
      abstime->tv_sec += relativeTimeFromNow / 1000;
      abstime->tv_nsec += (relativeTimeFromNow % 1000) * 1000 * 1000;
   }
   if (abstime->tv_nsec >= NANO_SECS_PER_SECOND) {
      abstime->tv_nsec -= NANO_SECS_PER_SECOND;
      abstime->tv_sec += 1;
   }
   return true;
# else /* LINUX, __sun */
   struct timeval tv;

   memset(abstime, 0, sizeof(struct timespec));

   /* Better?
        if (clock_gettime(CLOCK_REALTIME, &abstime) == -1) {
                printf("Timeout.c clock_gettime failed%d\n", errno);
        }
   */

   gettimeofday(&tv, 0);
   abstime->tv_sec = tv.tv_sec;
   abstime->tv_nsec = tv.tv_usec * 1000;  /* microseconds to nanoseconds */

   if (relativeTimeFromNow > 0) {
      abstime->tv_sec += relativeTimeFromNow / 1000;
      abstime->tv_nsec += (relativeTimeFromNow % 1000) * 1000 * 1000;
   }
   if (abstime->tv_nsec >= NANO_SECS_PER_SECOND) {
      abstime->tv_nsec -= NANO_SECS_PER_SECOND;
      abstime->tv_sec += 1;
   }
   return true;
# endif
# ifdef MORE_REALTIME
   clock_gettime(CLOCK_REALTIME, abstime);

   if (relativeTimeFromNow > 0) {
      abstime->tv_sec += relativeTimeFromNow / 1000;
      abstime->tv_nsec += (relativeTimeFromNow % 1000) * 1000 * 1000;
   }
   if (abstime->tv_nsec >= NANO_SECS_PER_SECOND) {
      abstime->tv_nsec -= NANO_SECS_PER_SECOND;
      abstime->tv_sec += 1;
   }
   return true;
# endif
}

/**
 * Get current timestamp string in ISO 8601 notation.
 * @param bufSize at least 26
 * @param timeStr out parameter, filled with e.g. "1997-07-16T19:20:30.45-02:00"
 * @return Your param timeStr for easy usage in printf() and such
 * @see http://en.wikipedia.org/wiki/ISO_8601
 */
Dll_Export const char *getCurrentLocalIsoTimestampStr(char *timeStr, int bufSize) {
#  if defined(WINCE)
        /*http://msdn.microsoft.com/library/default.asp?url=/library/en-us/wcekernl/html/_wcesdk_win32_systemtime_str.asp*/
        SYSTEMTIME st;
        GetSystemTime(&st);
        snprintf0(timeStr, bufSize, "%hd-%hd-%hdT%hd:%hd:%hd.%hd\n", st.wYear, st.wMonth, st.wDay, st.wHour, st.wMinute, st.wSecond, st.wMilliseconds);
#  elif _MSC_VER >= 1400
        /* TODO */
        /*__time64_t timer;
         _time64(&timer);*/
        time_t t1; /* unsigned long */
        (void) time(&t1); /* in seconds since the Epoch. 1970 */
        ctime_s(timeStr, bufSize-1, &t1);
#  elif defined(_WINDOWS)
        /* TODO */
        time_t t1; /* unsigned long */
        (void) time(&t1);
        strncpy0(timeStr, ctime(&t1), bufSize);
#  elif defined(__sun)
        /* TODO */
        time_t t1; /* unsigned long */
        (void) time(&t1);
        ctime_r(&t1, (char *)timeStr, bufSize-1);
#  else
        time_t t1; /* unsigned long */
        struct tm st;
        (void) time(&t1);
        /*ctime_r(&t1, (char *)timeStr);*/
        gmtime_r(&t1, &st); /* TODO: localtime_r() with zone offset*/
        snprintf0(timeStr, bufSize,
                        "20%0.2hd-%0.2hd-%0.2hdT%0.2hd:%0.2hd:%0.2hdZ\n", st.tm_year - 100,
                        st.tm_mon + 1, st.tm_mday, st.tm_hour, st.tm_min, st.tm_sec);
#  endif
        *(timeStr + strlen(timeStr) - 1) = '\0'; /* strip \n */
        return timeStr;
}

/**
 * Get a human readable time string for logging.
 * @param timeStr out parameter, e.g. "12:34:46" or "2006-11-14 12:34:46"
 * @param bufSize The size of timeStr
 * @return timeStr Your parameter given (for easy usage in printf())
 */
Dll_Export const char *getCurrentTimeStr(char *timeStr, int bufSize) {
#  if defined(WINCE)
      /*http://msdn.microsoft.com/library/default.asp?url=/library/en-us/wcekernl/html/_wcesdk_win32_systemtime_str.asp*/
      SYSTEMTIME st;
      GetSystemTime(&st);
      snprintf0(timeStr, bufSize, "%hd:%hd:%hd\n", st.wHour, st.wMinute, st.wSecond);
      /*wDay, wMilliseconds etc. */
#  elif _MSC_VER >= 1400
      /*__time64_t timer;
      _time64(&timer);*/
      time_t t1; /* unsigned long */
      (void) time(&t1); /* in seconds since the Epoch. 1970 */
      ctime_s(timeStr, bufSize-1, &t1);
#  elif defined(_WINDOWS)
      time_t t1; /* unsigned long */
      (void) time(&t1);
      strncpy0(timeStr, ctime(&t1), bufSize);
#  elif defined(__sun)
      time_t t1; /* unsigned long */
      (void) time(&t1);
      ctime_r(&t1, (char *)timeStr, bufSize-1);
#  else
      time_t t1; /* unsigned long */
      (void) time(&t1);
      ctime_r(&t1, (char *)timeStr);
      if (bufSize > 0) bufSize = 0; /* to avoid compiler warning */
#  endif
   *(timeStr + strlen(timeStr) - 1) = '\0'; /* strip \n */
   return timeStr;
}






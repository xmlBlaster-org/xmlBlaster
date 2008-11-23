/*----------------------------------------------------------------------------
Name:      xmlBlaster/src/c/util/helper.c
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Contains helper functions for string and message manipulation
           Generic helper code, used by Queue implementation and xmlBlaster client code
           Don't add any queue specific or xmlBlaster client specific code!
Compile:   gcc -Wall -g -o helper helper.c -DHELPER_UTIL_MAIN -I..
Testsuite: xmlBlaster/testsuite/src/c/TestUtil.c
Author:    "Marcel Ruff" <xmlBlaster@marcelruff.info>
-----------------------------------------------------------------------------*/

#include <stdio.h>
#include <stdlib.h>
#include <stdarg.h>
#include <string.h>
#include <ctype.h>
#include <assert.h>
#include <time.h>
#include "helper.h"

#ifdef _ENABLE_STACK_TRACE_
# include <execinfo.h>
#endif

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
#  include <Winsock2.h>       /* Sleep() */
#  if XB_USE_PTHREADS
#    include <pthreads/pthread.h> /* Our pthreads.h: For logging output of thread ID, for Windows and WinCE downloaded from http://sources.redhat.com/pthreads-win32 */
#  endif
#else
#  include <unistd.h>         /* sleep(), only used in main */
#  include <errno.h>          /* errno */
#  include <sys/time.h>       /* sleep with select(), gettimeofday() */
#  include <sys/types.h>      /* sleep with select() */
#  if XB_USE_PTHREADS
#    include <pthread.h>      /* The original pthreads.h from the OS */
#  endif
#endif

#include <inttypes.h> /* PRId64 %lld format specifier */


#define  MICRO_SECS_PER_SECOND 1000000
#define  NANO_SECS_PER_SECOND MICRO_SECS_PER_SECOND * 1000

static const char *LOG_TEXT[] = { "NOLOG", "ERROR", "WARN", "INFO", "CALL", "TIME", "TRACE", "DUMP", "PLAIN" };
static const int numLOG_TEXT = 9; /* sizeof(LOG_TEXT) returns 36 which is not what we want */

#define ESC "\033[0m"; /* Reset color to original values */
#define BOLD "\033[1m"

#define RED_BLACK "\033[31;40m"
#define GREEN_BLACK "\033[32;40m"
#define YELLOW_BLACK "\033[33;40m"
#define BLUE_BLACK "\033[34;40m"
#define PINK_BLACK "\033[35;40m"
#define LTGREEN_BLACK "\033[36;40m"
#define WHITE_BLACK "\033[37;40m"

#define WHITE_RED "\033[37;41m"
#define BLACK_RED "\033[30;41m"
#define BLACK_GREEN "\033[40;42m"
#define BLACK_PINK "\033[40;45m"
#define BLACK_LTGREEN "\033[40;46m"

/* To support colored logging output in xterminals */
static const char *LOG_TEXT_ESCAPE[] = {
       "NOLOG",
        "\033[31;40mERROR\033[0m",
        "\033[33;40mWARN\033[0m",
        "\033[32;40mINFO\033[0m",
        "\033[34;40mCALL\033[0m",
        "\033[36;40mTIME\033[0m",
        "\033[37;40mTRACE\033[0m",
        "\033[35;40mDUMP\033[0m",
        "\033[37;40mPLAIN\033[0m"
        };

static int vsnprintf0(char *s, size_t size, const char *format, va_list ap);

/**
 * Add for GCC compilation: "-rdynamic -export-dynamic -D_ENABLE_STACK_TRACE_"
 * @return The stack trace, you need to free() it.
 *         Returns NULL if out of memory.
 */
Dll_Export char *getStackTrace(int maxNumOfLines)
{
#ifdef _ENABLE_STACK_TRACE_
   int i;
   void** arr = (void **)calloc(maxNumOfLines, sizeof(void *));
   if (arr == 0) return (char *)0;
   {
      /*
      > +Currently, the function name and offset can only be obtained on systems
      > +that use the ELF binary format for programs and libraries.
      Perhaps a reference to the addr2line program can be added here.  It
      can be used to retrieve symbols even if the -rdynamic flag wasn't
      passed to the linker, and it should work on non-ELF targets as well.
      o  Under linux, gcc interprets it by setting the
         "-export-dynamic" option for ld, which has that effect, according
         to the linux ld manpage.

      o Under IRIX it's ignored, and the program's happy as a clam.

      o Under SunOS-4.1, gcc interprets it by setting the -dc -dp
         options for ld, which again forces the allocation of the symbol
         table in the code produced (see ld(1) on a Sun).
      */
      int bt = backtrace(arr, maxNumOfLines);
      char** list = (char **)backtrace_symbols(arr, bt); /* malloc the return pointer, the entries don't need to be freed */
      char *ret = strcpyAlloc("");
      for (i=0; i<bt; i++) {
         if (list[i] != NULL) {
            strcatAlloc(&ret, list[i]);
            strcatAlloc(&ret, "\n");
         }
      }
      free(list);
      free(arr);
      if (strlen(ret) < 1) {
         strcatAlloc(&ret, ""); /* Creation of stackTrace failed */
      }
      return ret;
   }
#else
   if (maxNumOfLines > 0)      /* to make the compiler happy */
           return strcpyAlloc("");
   return strcpyAlloc(""); /* No stack trace provided in this system */
#endif
}

#ifndef XMLBLASTER_SLEEP_FALLBACK
#  define  XMLBLASTER_SLEEP_FALLBACK 0 /* Initialize to make icc happy */
#endif
#ifndef XMLBLASTER_SLEEP_NANO
#  define XMLBLASTER_SLEEP_NANO 0
#endif

/**
 * Sleep for given milliseconds, on none real time systems expect ~ 10 millisecs tolerance.
 */
Dll_Export void sleepMillis(long millisecs)
{
#ifdef _WINDOWS
   Sleep(millisecs);
#elif __IPhoneOS__
   usleep(millisecs*1000);
#elif XMLBLASTER_SLEEP_FALLBACK /* rounded to seconds */
   if (millisecs < 1000)
      millisecs = 1000;
   sleep(millisecs/1000);
#elif XMLBLASTER_SLEEP_NANO
   TODO:
   int nanosleep(const struct timespec *rqtp,  struct  timespec *rmtp);
   struct timespec
   {
            time_t  tv_sec;         /* seconds */
            long    tv_nsec;        /* nanoseconds */
   };
   /*
   usleep()  deprecated
   */
   /*
   #include <time.h>
   void Sleep(clock_t wait)
   {
          clock_t goal;
          goal = wait * (CLOCKS_PER_SEC / 1000);
          while( goal >=  clock())
                  ;
   }
   */
#else
   fd_set dummy;
   struct timeval toWait;
   int ret;

   FD_ZERO(&dummy);
   toWait.tv_sec = millisecs / 1000;
   toWait.tv_usec = (millisecs % 1000) * 1000;

   ret = select(0, &dummy, NULL, NULL, &toWait);
   if (ret == -1) {
      printf("[helper.c] ERROR: sleepMillis(%ld) returned errnor %d", millisecs, errno);
   }
#endif
}

/**
 * Create a timestamp in nano seconds elapsed since 1970-01-01.
 * The timestamp is guaranteed to be ascending and unique.
 */
Dll_Export int64_t getTimestamp() {
   struct timespec abstime;
   int64_t timestamp;
   static int64_t lastNanos=0;

   getAbsoluteTime(0L, &abstime);

   timestamp = (int64_t)abstime.tv_sec * NANO_SECS_PER_SECOND;
   timestamp += abstime.tv_nsec;
   if (timestamp <= lastNanos) {
      timestamp = lastNanos + 1;
   }
   lastNanos = timestamp;
   return timestamp;
}


#include <wchar.h>
/**
 * Converts the given wide char pwcs to multibyte argv.
 * <p>Call freeWcsArgv() to free the memory again.</p>
 * @param pwcs In parameter: Wide char command line arguments
 * @param argc The number of strings in pwcs
 * @return argv Is allocated with malloc and holds all given pwcs strings
 */
Dll_Export char **convertWcsArgv(wchar_t **argv_wcs, int argc) {
   int i;
   char **argv = (char **)malloc(argc*sizeof(char*));
   for (i=0; i<argc; i++) {
      int sizeInBytes = 4*(int)wcslen(argv_wcs[i]);
      argv[i] = (char *)malloc(sizeInBytes*sizeof(char));
#     if _MSC_VER >= 1400 && !defined(WINCE)
       {
         size_t pReturnValue;
         /*errno_t err = */
         wcstombs_s(&pReturnValue, argv[i], sizeInBytes, argv_wcs[i], _TRUNCATE);
       }
#     else
         wcstombs(argv[i], argv_wcs[i], sizeInBytes);
#     endif
          /*printf("%s ", argv[i]);*/
   }
   return argv;
}

/**
 * Frees the allocated argv from convertWcsArgv().
 * @param argv The main(argv)
 * @param argc The number of strings in argv
 */
Dll_Export void freeArgv(char **argv, int argc) {
   int i;
   if (argv == 0) return;
   for (i=0; i<argc; i++) {
      free(argv[i]);
   }
   free(argv);
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

Dll_Export char *strtok_r2(char *src, const char *delim, char **saveptr, const char quotechar) {
    bool inQuotes = false;
    int ii, len;
    char *ptr;
        if (src != 0)
                *saveptr = src;
        ptr = *saveptr;
        if (ptr == 0)
                return 0;
        len = strlen(ptr);
   for (ii = 0; ii < len; ii++) {
          char c = ptr[ii];
          if (quotechar != 0 && c == quotechar) {
                 inQuotes = !inQuotes;
                 if (inQuotes)
                         ptr++; /* strip leading quotechar */
                 else
                         ptr[ii] = 0; /* Remove trailing quotechar */
          }
          else if (strchr(delim, c) != 0 && !inQuotes) {
                  ptr[ii] = 0;
                  (*saveptr) = ptr+ii+1;
                  return ptr;
          }
   }
   (*saveptr) = 0;
   return ptr;
}

/**
 * Allocates the string with malloc for you.
 * You need to free it with free()
 * @param src The text to copy
 * @return The allocated string or NULL if out of memory
 */
Dll_Export char *strcpyAlloc(const char *src)
{
   char *dest;
   size_t len;
   if (src == 0) return (char *)0;
   len = strlen(src) + 1;
   dest = (char *)malloc(len*sizeof(char));
   if (dest == 0) return 0;
   strncpy0(dest, src, len);
   return dest;
}

Dll_Export char *strcpyAlloc0(const char *src, const size_t maxLen)
{
   char *dest;
   size_t len;
   if (src == 0) return (char *)0;
   len = strlen(src) + 1;
   if (len > maxLen) len = maxLen;
   dest = (char *)malloc(len*sizeof(char));
   if (dest == 0) return 0;
   strncpy0(dest, src, len);
   return dest;
}

/**
 * Same as strcat but reallocs the 'dest' string
 * @return The allocated string (*dest) or NULL if out of memory
 */
Dll_Export char *strcatAlloc(char **dest, const char *src)
{
   size_t lenSrc;
   size_t len;
   assert(dest != 0);
   if (src == 0) return (char *)0;
   lenSrc = strlen(src);
   len = lenSrc+strlen(*dest)+1;
   (*dest) = (char *)realloc(*dest, len*sizeof(char));
   if ((*dest) == 0) return 0;
   strncat((*dest), src, lenSrc);
   *((*dest)+len-1) = '\0';
   return (*dest);
}

/**
 * Same as strcat but reallocs the 'dest' string
 * @return The allocated string (*dest) or NULL if out of memory
 */
Dll_Export char *strcatAlloc0(char **dest, const char *src, const size_t maxLen)
{
   size_t lenSrc;
   size_t len;
   assert(dest != 0);
   if (src == 0) return (char *)0;
   lenSrc = strlen(src);
   len = lenSrc+strlen(*dest)+1;
   (*dest) = (char *)realloc(*dest, len*sizeof(char));
   if ((*dest) == 0) return 0;
   strncat((*dest), src, lenSrc);
   if (len > maxLen) {
           len = maxLen; /* TODO: proper handling: not allocate too much */
   }
   *((*dest)+len-1) = '\0';
   return (*dest);
}

/**
 * Same as strcpyAlloc but if the given *dest != NULL this old allocation is freed first
 * @return *dest The allocated string filled with 'src',
 *         you need to free() it when not needed anymore.
 */
Dll_Export char *strcpyRealloc(char **dest, const char *src)
{
   if (*dest != 0)
      free(*dest);
   *dest = strcpyAlloc(src);
   return *dest;
}

/**
 * Allocates the string with malloc for you, it is always ended with 0.
 * NOTE: If your given blob or len is 0 an empty string of size 1 is returned
 * @return The string, never null.
 *         You need to free it with free()
 */
Dll_Export char *strFromBlobAlloc(const char *blob, const size_t len)
{
   char *dest;
   size_t i;
   if (blob == 0 || len < 1) {
      dest = (char *)malloc(1*sizeof(char));
      if (dest == 0) return 0;
      *dest = 0;
      return dest;
   }

   dest = (char *)malloc((len+1)*sizeof(char));
   if (dest == 0) return 0;
   for (i=0; i<len; i++) {
      dest[i] = (char)blob[i];
   }
   dest[len] = '\0';
   return dest;
}


/**
 * Convert the errnum to a human readable errnoStr.
 * @param errnoStr Out parameter holding the string
 * @param sizeInBytes Size of the buffer
 * @param errnum The error number (errno)
 */
Dll_Export void xb_strerror(char *errnoStr, size_t sizeInBytes, int errnum) {
   snprintf0(errnoStr, sizeInBytes, "%d", errnum); /* default if string lookup fails */
#  if defined(WINCE)
#  elif _MSC_VER >= 1400
      strerror_s(errnoStr, sizeInBytes, errnum);
#  elif defined(_LINUX)
      strerror_r(errnum, errnoStr, sizeInBytes-1); /* glibc > 2. returns a char*, but should return an int */
#  else
      {
         char *p = strerror(errnum);
         strncpy0(errnoStr, p, sizeInBytes);
      }
#  endif
}


/**
 * Guarantees a 0 terminated string
 * @param to The destination string must be big enough
 * @param from The source to be copied
 * @param maxLen (maxLen-1) of 'to' will be filled with a 0,
 *        so effectively only maxLen-1 from 'from' are copied.
 * @return The destination string 'to'
 */
Dll_Export char *strncpy0(char * const to, const char * const from, const size_t maxLen)
{
#  if defined(WINCE)
      char *ret=strncpy(to, from, maxLen-1);
      *(to+maxLen-1) = '\0';
      return ret;
#  elif _MSC_VER >= 1400
/*      errno_t strncpy_s(
   char *strDest,
   size_t sizeInBytes,
   const char *strSource,
   size_t count
); */
      errno_t ee = strncpy_s(to, maxLen, from, _TRUNCATE); /*maxLen);*/
      return to;
#  else /* MAC OSX calls it strlcpy() */
      char *ret=strncpy(to, from, maxLen-1);
      *(to+maxLen-1) = '\0';
      return ret;
#  endif
}


/**
 * Guarantees a 0 terminated string
 * @param to The destination string must be big enough
 * @param from The source to be appended
 * @param max Number of characters to append, max-1 will be ended by 0
 * @return The destination string 'to'
 */
Dll_Export char *strncat0(char * const to, const char * const from, const size_t max)
{
#  if _MSC_VER >= 1400 && !defined(WINCE)
      /* buffersize of 'to' in bytes */
      size_t bufferSizeInBytes = strlen(to) + max;
      errno_t ee = strncat_s(to, bufferSizeInBytes, from, _TRUNCATE);
      return to;
#  else /* MAC OSX calls it strlcat() */
      int oldLen = strlen(to);
      char *ret=strncat(to, from, max-1);
      *(to+oldLen+max-1) = '\0';
      return ret;
#  endif
}

int vsnprintf0(char *s, size_t size, const char *format, va_list ap) {
#  if _MSC_VER >= 1400 && !defined(WINCE)
      errno_t err = vsnprintf_s(s, size, _TRUNCATE, format, ap);
      if ( err == STRUNCATE ) {
         printf("truncation occurred %s!\n", format);
         return 0;
      }
      return err;
#  elif defined(_WINDOWS)
      return _vsnprintf(s, size, format, ap);
#  else
      return vsnprintf(s, size, format, ap);
#  endif
}

/**
 * Microsoft introduces the vsnprintf_s()
 */
Dll_Export int snprintf0(char *buffer, size_t sizeOfBuffer, const char *format, ...) {
   int ret;
   va_list ap;
   va_start (ap, format);
   ret = vsnprintf0(
         buffer,
         sizeOfBuffer,
         format,
         ap);
   va_end (ap);
   return ret;
}

/**
 * strip leading and trailing spaces of the given string
 */
Dll_Export void trim(char *s)
{
   size_t first=0;
   size_t len;
   int i;

   if (s == (char *)0) return;

   len = strlen((char *) s);

   {  /* find beginning of text */
      while (first<len) {
         if (!isspace((unsigned char)s[first]))
            break;
         first++;
      }
   }

   if (first>=len) {
      *s = '\0';
      return;
   }
   else
      memmove((char *) s, (char *) s+first, strlen(s+first)+1); /* including '\0' */

   for (i=(int)strlen((char *) s)-1; i >= 0; i--)
      if (!isspace((unsigned char)s[i])) {
         s[i+1] = '\0';
         return;
      }
   if (i<0) *s = '\0';
}

/**
 * strip leading spaces of the given string
 */
Dll_Export void trimStart(char *s)
{
   size_t first=0;
   size_t len;

   if (s == (char *)0) return;

   len = strlen((char *) s);

   {  /* find beginning of text */
      while (first<len) {
         if (!isspace((unsigned char)s[first]))
            break;
         first++;
      }
   }

   if (first>=len) {
      *s = '\0';
      return;
   }
   else
      memmove((char *) s, (char *) s+first, strlen(s+first)+1); /* including '\0' */
}

/**
 * strip trailing spaces of the given string
 */
Dll_Export void trimEnd(char *s)
{
   int i;
   for (i=(int)strlen((char *) s)-1; i >= 0; i--)
      if (!isspace((unsigned char)s[i])) {
         s[i+1] = '\0';
         return;
      }
   if (i<0) *s = '\0';
}

Dll_Export
bool startsWith(const char * const str, const char * const token) {
        int i;
        if (str == 0 || token == 0)
                return false;
        for (i = 0; ; i++) {
                if (token[i] == 0)
                        return true;
                if (str[i] != token[i])
                        return false;
        }
}

Dll_Export
bool endsWith(const char * const str, const char * const token) {
        int i, count=0, lenStr, len;
        if (str == 0 || token == 0)
                return false;
        lenStr = strlen(str);
        len = strlen(token);
        if (lenStr < len)
                return false;
        for (i = lenStr - len; i < lenStr; i++, count++) {
                if (str[i] != token[count])
                        return false;
        }
        return true;
}

/**
 * Converts the given binary data to a more readable string,
 * the zero bytes are replaced by '*'
 * @param data The data to convert
 * @param len The length of the binary data
 * @return readable is returned, it must be free()'d.
 *         If allocation fails NULL is returned
 */
Dll_Export char *toReadableDump(char *data, size_t len)
{
   char *readable;
   size_t i;
   if (data == 0) {
      return (char *)0;
   }
   readable = (char *)malloc((len+1) * sizeof(char));
   if (readable == (char *)0) return (char *)0;
   for (i=0; i<len; i++) {
      if (data[i] == '\0')
         readable[i] = '*';
      else
         readable[i] = data[i];
   }
   readable[len] = '\0';
   return readable;
}

#if defined(XB_USE_PTHREADS)
/**
 * Cast the thread identifier to an long value.
 * @param t The pthread_t type
 * @return A unique long, usually the pointer address
 */
unsigned long get_pthread_id(pthread_t t)
{
#  ifdef _WINDOWS
   return (unsigned long)t.p; /* typedef ptw32_handle_t pthread_t; with struct {void*p; unsigned int x;} */
#  else
   /* TODO: Mac OS X: in sys/_types.h: struct _opaque_pthread_t { long __sig; struct __darwin_pthread_handler_rec  *__cleanup_stack; char __opaque[__PTHREAD_SIZE__]; }; */
   int64_t val64 = 0;
   /*printf("xmlBlaster helper.c pthread_t size=%ud\n", sizeof(pthread_t));*/
   {
      val64 = (int64_t)t; /* INT_LEAST64_MAX=9223372036854775807 */
      if (val64 <= 4294967295U) {
         /*printf("xmlBlaster helper.c OK\n");*/
         return (unsigned long)t;
      }
   }
   {  /* Intels icc 10.x had problems which i couldn't resolve (2008/11 marcel) */
      char *p;
      char buf[56];
      long val32;
      /* 2147483647 */
      /* 3081234112 */
      printf("xmlBlaster helper.c Warning: stripping pthread_id %"PRId64"\n", val64);
      SNPRINTF(buf, 55, "%"PRId64, val64); /* PRId64 As 32bit system need "%lld" and 64 bit systems need "%ld" */
      /*printf("xmlBlaster helper.c Warning: stripping pthread_id string %s\n", buf);*/
      p = buf + strlen(buf) - 9;
      sscanf(p, "%ld", &val32);
      printf("xmlBlaster helper.c Warning: stripping pthread_id from %"PRId64" to %ld\n", val64, val32);
      return val32;
      /*return (long)(val64/(1+int64_t(val64 / INT_LEAST32_MAX)));*/
   }
#  endif
}
#endif

/**
 * Console helper to get key hit.
 * New lines are ignored
 * @param str The text displayed on the console
 * @return the key hit
 */
Dll_Export char getInputKey(const char *str) {
        char c = 0;
        printf("%s >\n", str);
        do {
                c = getchar();
        }
        while (c == '\n'); /* Ignore enter hits */
        return c;
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
      bufSize = 0; /* to avoid compiler warning */
#  endif
   *(timeStr + strlen(timeStr) - 1) = '\0'; /* strip \n */
   return timeStr;
}

/**
 * Default logging output is handled by this method:
 * All logging is appended a time, the loglevel and the location string.
 * The logging output is to console.
 * <p>
 * If you have your own logging device you need to implement this method
 * yourself and register it with
 * </p>
 * <pre>
 * xa->log = myXmlBlasterLoggingHandler;
 * </pre>
 * @param logUserP User specific location bounced back
 * @param currLevel The actual log level of the client
 * @param level The level of this log entry
 * @param location A string describing the code place
 * @param fmt The formatting string
 * @param ... Other variables to log, corresponds to 'fmt'
 */
Dll_Export void xmlBlasterDefaultLogging(void *logUserP, XMLBLASTER_LOG_LEVEL currLevel,
                              XMLBLASTER_LOG_LEVEL level,
                              const char *location, const char *fmt, ...)
{
   /* Guess, we need no more than 200 bytes. */
   int n, size = 200;
   char *p = 0;
   va_list ap;
   char *stackTrace = 0;
#  ifdef _WINDOWS
   const char * const * logText = LOG_TEXT;
#  else
   const char * const * logText = LOG_TEXT_ESCAPE;
#  endif
   if (logUserP) {}  /* To avoid "logUserP was never referenced" compiler warning */

   if (level > currLevel) {
      return;
   }
   if ((p = (char *)malloc (size)) == NULL)
      return;

   if (level <= XMLBLASTER_LOG_ERROR) {
      stackTrace = getStackTrace(10);
   }

   for (;;) {
      /* Try to print in the allocated space. */
      va_start(ap, fmt);
      n = vsnprintf0(p, size, fmt, ap);
      va_end(ap);
      /* If that worked, print the string to console. */
      if (n > -1 && n < size) {
                 enum { SIZE=128 };
         char timeStr[SIZE];
         getCurrentTimeStr(timeStr, SIZE);
#        if XB_USE_PTHREADS
            printf("[%s %s %s thread0x%lx] %s %s\n", timeStr, logText[level], location,
                                    get_pthread_id(pthread_self()), p,
                                    (stackTrace != 0) ? stackTrace : "");
#        else
            printf("[%s %s %s] %s %s\n", timeStr, logText[level], location, p,
                                    (stackTrace != 0) ? stackTrace : "");
#        endif
         free(p);
         free(stackTrace);
         return;
      }
      /* Else try again with more space. */
      if (n > -1)    /* glibc 2.1 */
         size = n+1; /* precisely what is needed */
      else           /* glibc 2.0 */
         size *= 2;  /* twice the old size */
      if ((p = (char *)realloc (p, size)) == NULL) {
         free(stackTrace);
         return;
      }
   }
}

/**
 * Parses the given string and returns the enum for it.
 * If logLevelStr is NULL or empty or unknown we return the default log level.
 * @param logLevelStr The level e.g. "WARN" or "warn" or "2"
 * @return The enum, e.g. XMLBLASTER_LOG_WARN
 */
Dll_Export XMLBLASTER_LOG_LEVEL parseLogLevel(const char *logLevelStr)
{
   int i;
   int len = numLOG_TEXT;
   if (logLevelStr == 0 || *logLevelStr == '\0' ) {
      return XMLBLASTER_LOG_WARN;
   }
   for (i=0; i<len; i++) {
#     ifdef _WINDOWS
      if (!strcmp(LOG_TEXT[i], logLevelStr)) {
#     else
      if (!strcasecmp(LOG_TEXT[i], logLevelStr)) {
#     endif
         return (XMLBLASTER_LOG_LEVEL)i;
      }
   }
   if (strToInt(&i, logLevelStr) == 1)
      return (XMLBLASTER_LOG_LEVEL)i;
   return XMLBLASTER_LOG_WARN;
}

/**
 * @return A human readable log level, e.g. "ERROR"
 */
Dll_Export const char *getLogLevelStr(XMLBLASTER_LOG_LEVEL logLevel)
{
   return LOG_TEXT[logLevel];
}

/**
 * Check if logging is necessary.
 * @param currLevel The actual log level of the client
 * @param level The level of this log entry
 * @return true If logging is desired
 */
Dll_Export _INLINE_FUNC bool doLog(XMLBLASTER_LOG_LEVEL currLevel, XMLBLASTER_LOG_LEVEL level)
{
   return (currLevel <= level) ? true : false;
}

/**
 * Embed the given 'embed' into exception->message.
 * <code>embed</code> and <code>exception</code> may point on the same instance
 * @param embed An original exception to embed, can be empty in which case it is ignored
 * @param exception Contains the new exception with embedded old exception errorCode/message
 */
Dll_Export void embedException(ExceptionStruct *exception, const char *newErrorCode, const char *newMessage, const ExceptionStruct *embed)
{
   char embedStr[EXCEPTIONSTRUCT_MESSAGE_LEN];
   char newErrorCodeTmp[EXCEPTIONSTRUCT_ERRORCODE_LEN];
   char message[EXCEPTIONSTRUCT_MESSAGE_LEN];

   strncpy0(newErrorCodeTmp, newErrorCode, EXCEPTIONSTRUCT_ERRORCODE_LEN); /* Make temporary copy in case the memory overlaps */
   if (*embed->errorCode != 0) {
      SNPRINTF(message, EXCEPTIONSTRUCT_MESSAGE_LEN, "%s {Root cause: %s}", newMessage, getExceptionStr(embedStr, EXCEPTIONSTRUCT_MESSAGE_LEN, embed));
   }
   else {
      SNPRINTF(message, EXCEPTIONSTRUCT_MESSAGE_LEN, "%s", newMessage);
   }
   strncpy0(exception->message, message, EXCEPTIONSTRUCT_MESSAGE_LEN);
   strncpy0(exception->errorCode, newErrorCodeTmp, EXCEPTIONSTRUCT_ERRORCODE_LEN);
}

/**
 * Should be called on any ExceptionStruct before using it.
 * Nulls all fields
 */
Dll_Export void initializeExceptionStruct(ExceptionStruct *exception)
{
   exception->remote = false;
   *exception->errorCode = (char)0;
   *exception->message = (char)0;
}

/**
 * Convenience function which returns a formatted exception string.
 * <pre>
 * </pre>
 * @param out The string where the exception is written into, you should allocate at least
 *            EXCEPTIONSTRUCT_ERRORCODE_LEN + EXCEPTIONSTRUCT_MESSAGE_LEN + 64
 *            bytes for it
 * @param outSize The max size of 'out'
 * @param exception The exception to dump
 * @return out
 */
Dll_Export const char *getExceptionStr(char *out, int outSize, const ExceptionStruct *exception)
{
   SNPRINTF(out, outSize, "[%s] %s", exception->errorCode, exception->message);
   return out;
}

/**
 * Convert a 64 bit integer to a string.
 * This helper concentrates this conversion to one place to
 * simplify porting to compilers with no <code>int64_t = long long</code> support
 * @param buf You need to pass this buffer with at least INT64_STRLEN_MAX=22 bytes of size
 * @return buf
 */
Dll_Export const char* int64ToStr(char * const buf, int64_t val)
{
   if (buf == 0) return 0;
   *buf = 0;
   /* SNPRINTF(buf, INT64_STRLEN_MAX, "%lld", val);  The normal sprintf should be safe enough */
   snprintf0(buf, INT64_STRLEN_MAX, PRINTF_PREFIX_INT64_T, val);  /* Returns number of written chars */
   return buf;
}

/**
 * Convert a string to a 64 bit integer.
 * This helper concentrates this conversion to one place to
 * simplify porting to compilers with no <code>long long</code> support
 * @param val Your <code>long long</code> which is filled from <code>str</code>
 * @param str The string to convert, for example "123450000LL"
 * @return true on success
 */
Dll_Export bool strToInt64(int64_t *val, const char * const str)
{
   if (str == 0 || val == 0) return false;
   /*str[INT64_STRLEN_MAX-1] = 0; sscanf should be safe enough to handle overflow */
        /* %lld on UNIX, %I64d on Windows */
#  if _MSC_VER >= 1400 && !defined(WINCE)
   return (sscanf_s(str, PRINTF_PREFIX_INT64_T, val) == 1) ? true : false;
#  else
   return (sscanf(str, PRINTF_PREFIX_INT64_T, val) == 1) ? true : false;
#  endif
}

Dll_Export bool strToLong(long *val, const char * const str)
{
   if (str == 0 || val == 0) return false;
   {
      int64_t tmp;
      bool ret = strToInt64(&tmp, str);
      if (ret == false) return false;
      *val = (long)tmp;
      return true;
   }
}

Dll_Export bool strToInt(int *val, const char * const str)
{
   if (str == 0 || val == 0) return false;
   {
      int64_t tmp;
      bool ret = strToInt64(&tmp, str);
      if (ret == false) return false;
      *val = (int)tmp;
      return true;
   }
}

Dll_Export bool strToULong(unsigned long *val, const char * const str)
{
   if (str == 0 || val == 0) return false;
#  if _MSC_VER >= 1400 && !defined(WINCE)
   return (sscanf_s(str, "%lu", val) == 1) ? true : false;
#  else
   return (sscanf(str, "%lu", val) == 1) ? true : false;
#  endif
}


/**
 * Allocates the string with malloc for you.
 * You need to free it with free()
 * @param blob If null it is malloc()'d for you, else the given blob is used to be filled.
 * @return The given blob (or a new malloc()'d if blob was NULL), the data is 0 terminated.
 *         We return NULL on out of memory.
 */
Dll_Export BlobHolder *blobcpyAlloc(BlobHolder *blob, const char *data, size_t dataLen)
{
   if (blob == 0) {
      blob = (BlobHolder *)calloc(1, sizeof(BlobHolder));
      if (blob == 0) return blob;
   }
   blob->dataLen = dataLen;
   blob->data = (char *)malloc((dataLen+1)*sizeof(char));
   if (blob->data == 0) {
      free(blob);
      return (BlobHolder *)0;
   }
   *(blob->data + dataLen) = 0;
   memcpy(blob->data, data, dataLen);
   return blob;
}

/**
 * free()'s the data in the given blob, does not free the blob itself.
 * @param blob if NULL we return NULL
 * @return The given blob
 */
Dll_Export BlobHolder *freeBlobHolderContent(BlobHolder *blob)
{
   if (blob == 0) return 0;
   if (blob->data != 0) {
      free(blob->data);
      blob->data = 0;
      blob->dataLen = 0;
   }
   return blob;
}

/**
 * Converts the given binary data to a more readable string,
 * the zero bytes are replaced by '*'
 * @param blob The binary data
 * @return readable is returned, it must be free()'d
 */
Dll_Export char *blobDump(BlobHolder *blob)
{
   return toReadableDump(blob->data, blob->dataLen);
}

Dll_Export void freeBlobDump(char *blobDump)
{
   free(blobDump);
}

# ifdef HELPER_UTIL_MAIN
/*
 * gcc -g -Wall -DHELPER_UTIL_MAIN=1 -I../../ -o helper helper.c -I../
 */
int main()
{
   const long millisecs = 500;
   const int currLevel = 3;
   const char *location = __FILE__;
   const char *p = "OOOO";
   int i = 3;
   xmlBlasterDefaultLogging(0, currLevel, XMLBLASTER_LOG_WARN, location, "%s i=%d\n", p, i);

   printf("Sleeping now for %ld millis\n", millisecs);
   sleepMillis(millisecs);
   printf("Waiking up after %ld millis\n", millisecs);

   {
      const char *ptr = "     28316";
      char tr[20];
      strncpy0(tr, ptr, 20);
      trim(tr);
      printf("Before '%s' after '%s'\n", ptr, tr);
   }
   {
      const char *ptr = "     28316  ";
      char tr[20];
      strncpy0(tr, ptr, 20);
      trim(tr);
      printf("Before '%s' after '%s'\n", ptr, tr);
   }
   {
      ExceptionStruct ex;
      strncpy0(ex.errorCode, "Original.cause", EXCEPTIONSTRUCT_ERRORCODE_LEN);
      strncpy0(ex.message, "Original message", EXCEPTIONSTRUCT_MESSAGE_LEN);
      embedException(&ex, "new.cause", "new message", &ex);
      printf("errorCode=%s message=%s\n", ex.errorCode, ex.message);
   }
   return 0;
}
# endif

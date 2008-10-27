/*----------------------------------------------------------------------------
 Name:      xmlBlaster/src/c/util/Timeout.h
 Project:   xmlBlaster.org
 Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
 Comment:   Generic Timeout code with POSIX threads
 Author:    "Marcel Ruff" <xmlBlaster@marcelruff.info>
 -----------------------------------------------------------------------------*/
#ifndef _XMLBLASTER_TIMEOUT_H
#define _XMLBLASTER_TIMEOUT_H

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
#include <stdio.h>
#include <stdlib.h>


struct TimeoutStruct;
typedef Dll_Export struct TimeoutStruct Timeout;

/* Declare function pointers to use in struct to simulate object oriented access */
typedef void (*TimeoutCbFp)(Timeout *timeout, void *userData, void *userData2);

typedef int (* XmlBlasterTimeoutSetTimeoutListener)(Timeout *xb,
		TimeoutCbFp timeoutCbFp, const long int delay, void *userData, void *userData2);

/**
 * Callback specific data is hold here.
 * So we can in future support many listeners (having a map of TimeoutContainers)
 */
typedef struct Dll_Export TimeoutContainerStruct {
	long int delay;
	void *userData;
	void *userData2;
	TimeoutCbFp timeoutCbFp;
} TimeoutContainer;

/**
 * All client access to Timeout goes over this struct and its function pointers.
 */
struct Dll_Export TimeoutStruct {
	const char *name; /**< The timer/thread name */
	pthread_t threadId;
	bool running;
	bool ready; /**< On creation wait until thread started */
	TimeoutContainer timeoutContainer;
	/**
	 * Add listener and span timer.
	 * @param timeout The this pointer
	 * @param timeoutCbFp The function pointer to call back on timeout
	 * @param delay Repeated call of timeoutCbFp of given delay in millisconds
	 *        If < 1 the timer is reset (set inactive)
	 * @param userData is passed back to your timeoutCbFp
	 * @param userData2 is passed back to your timeoutCbFp
	 */
	XmlBlasterTimeoutSetTimeoutListener setTimeoutListener;
	pthread_mutex_t condition_mutex; /*= PTHREAD_MUTEX_INITIALIZER; */
	pthread_cond_t  condition_cond; /*  = PTHREAD_COND_INITIALIZER;*/
};

/**
 * Get an instance of this Timeout struct.
 * NOTE: Every call creates a new and independent instance
 * @param name The name of the thread
 * @return NULL if bootstrapping failed. If not NULL you need to free it when you are done
 * usually by calling freeTimeout().
 */
Dll_Export extern Timeout *createTimeout(const char* const name);

/**
 * Free your instance after accessing xmlBlaster.
 */
Dll_Export extern void freeTimeout(Timeout *timeout);

#ifdef __cplusplus
#ifndef XMLBLASTER_C_COMPILE_AS_CPP
}
#endif
#endif

#endif /* _XMLBLASTER_TIMEOUT_H */


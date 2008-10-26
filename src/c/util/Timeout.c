/*----------------------------------------------------------------------------
Name:      xmlBlaster/src/c/util/Timeout.c
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   C Timer with POSIX threads
Compile:   gcc -Wall -g -o Timeout Timeout.c -DTIMEOUT_UTIL_MAIN -I..
Testsuite: xmlBlaster/testsuite/src/c/TestUtil.c
Author:    "Marcel Ruff" <xmlBlaster@marcelruff.info>
-----------------------------------------------------------------------------*/

#include <stdio.h>
#include <stdlib.h>
#include <stdarg.h>
#include <string.h>
#include <ctype.h>
#include <time.h>
#include "helper.h"
#include "Timeout.h"

#ifdef _ENABLE_STACK_TRACE_
# include <execinfo.h>
#endif

#ifdef _WINDOWS
#  if XB_USE_PTHREADS
#    include <pthreads/pthread.h> /* Our pthreads.h: For logging output of thread ID, for Windows and WinCE downloaded from http://sources.redhat.com/pthreads-win32 */
#  endif
#else
#  if XB_USE_PTHREADS
#    include <pthread.h>      /* The original pthreads.h from the OS */
#  endif
#endif

static void *timeoutMainLoop(void *ptr);
static int setTimeoutListener(Timeout *timeout, TimeoutCbFp timeoutCbFp, const long int delay, void * const userData);

/**
 * Create an instance of a property struct.
 * @param name Thread name, a clone is kept
 */
Timeout *createTimeout(const char* const name) {
   Timeout *timeout = (Timeout *)calloc(1, sizeof(Timeout));
   timeout->ready = false;
   timeout->running = true;
   timeout->name = (name != 0) ? strcpyAlloc(name) : 0;
   timeout->setTimeoutListener = setTimeoutListener;

   pthread_mutex_init(&timeout->condition_mutex, NULL); /* int rc return is always 0 */
   pthread_cond_init(&timeout->condition_cond, NULL);

   return timeout;
}


void freeTimeout(Timeout *timeout)
{
   if (timeout == 0) return;
   pthread_mutex_lock(&timeout->condition_mutex);
   timeout->running = false;
   pthread_cond_broadcast(&timeout->condition_cond);
   pthread_mutex_unlock(&timeout->condition_mutex );
   pthread_join(timeout->thread, NULL);
   free((char *)timeout->name);
   free(timeout);
}

/**
 * See header Timeout.h for documentation
 */
static int setTimeoutListener(Timeout * const timeout, TimeoutCbFp timeoutCbFp, const long int delay, void * const userData)
{
    int  iret;

    if (timeout == 0)
    	return -1;

    timeout->timeoutContainer.timeoutCbFp = timeoutCbFp;
    timeout->timeoutContainer.delay = delay;
    timeout->timeoutContainer.userData = userData;

    if (timeout->thread != 0) {
    	printf("Timeout.c Calling setTimeoutListener twice is not tested\n");
    	return -1;
    }

    /* pthread_attr.name before calling pthread_create() ? pthread_setname(timeout->name) pthread_attr_setname() */
    iret = pthread_create(&timeout->thread, NULL, timeoutMainLoop, (void*)timeout);

    return iret; /* 0 == success */
}

/**
 * Run by the new created thread, calls the clients update method.
 * Leaving this pthread start routine does an implicit pthread_exit().
 * @param ptr Is Timeout * holding all necessary informations
 * @return 0 on success, 1 on error. The return value is the exit value returned by pthread_join()
 */
static void *timeoutMainLoop(void *ptr)
{
    Timeout *timeout = (Timeout *)ptr;
    while (timeout->running) {
    	int64_t startNanos = getTimestamp();
    	int ret = 0;
    	struct timespec abstime;

    	pthread_mutex_lock(&timeout->condition_mutex);

    	/* calculate absolute time from relaive delay millis */
    	getAbsoluteTime(timeout->timeoutContainer.delay, &abstime);

    	/* protect against spurious wake ups */
        while (timeout->running) {
        	bool timeElapsed = false;
        	ret = pthread_cond_timedwait(&timeout->condition_cond, &timeout->condition_mutex, &abstime);
        	/* check if delay reached */
        	timeElapsed = (getTimestamp()-startNanos) >= timeout->timeoutContainer.delay*1000000L;
        	/*if (ret == ETIMEDOUT) { Not found on my Linux box?! */
        		if (timeElapsed)
        			break;
        	/*}
        	else {
        		printf("Timeout.c signaled");
        		break;
        	}*/
        }

        pthread_mutex_unlock(&timeout->condition_mutex);

        if (!timeout->running)
        	break;

    	/*sleepMillis(timeout->timeoutContainer.delay);*/

        if (timeout->timeoutContainer.timeoutCbFp != 0) { /* Client has registered to receive callback */
        	timeout->timeoutContainer.timeoutCbFp(timeout, timeout->timeoutContainer.userData);
        }
    }
    return 0;
}


# ifdef TIMEOUT_UTIL_MAIN
/*
 * gcc -g -Wall -pedantic -DTIMEOUT_UTIL_MAIN=1 -lpthread -I../../ -o Timeout Timeout.c helper.c -I../
 */
static void onTimeout(Timeout *timeout, void *userData) {
	const char *data = (char *)userData;
	printf("Timeout occurred, timer=%s delay=%ld userData=%s\n",
			timeout->name, timeout->timeoutContainer.delay, data);
}
int main()
{
   const long millisecs = 500;
   printf("millisec=%ld\n", millisecs);
   {
	   Timeout *timeout = createTimeout("TestTimer");
	   timeout->setTimeoutListener(timeout, onTimeout, 4000, "dummyData");
	   printf("Sleeping for 30sec\n");
	   sleepMillis(30000);
	   freeTimeout(timeout);
	   printf("Bye\n");
   }
   return 0;
}
# endif

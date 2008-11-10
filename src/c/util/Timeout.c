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
static int setTimeoutListener(Timeout * const timeout, TimeoutCbFp timeoutCbFp, const long int delay,
      void * const userData, void * const userData2);

/* Local helper function */
static bool _isMyThread(Timeout *timeout) {
	/** see  long get_pthread_id(pthread_t t) */
#if defined(_WINDOWS)
	if (timeout == 0 ||  timeout->threadId.p == 0)
	   return false;
	{
   	pthread_t callingThreadId = pthread_self();
      if (callingThreadId.p == timeout->threadId.p) {
			return true;
		}
		return false;
	}
#else
	if (timeout == 0 ||  timeout->threadId == 0)
	   return false;
	{
   	pthread_t callingThreadId = pthread_self();
      if (callingThreadId == timeout->threadId) {
			return true;
		}
		return false;
	}
#endif
}

/* Local helper function */
static bool _isNull(pthread_t *threadId) {
	if (threadId == 0) {
		return true;
	}
#if defined(_WINDOWS)
   if (threadId->p == 0)
		return true;
#else
   if (*threadId == 0)
		return true;
#endif
	return false;
}

static void initTimeout(Timeout *timeout) {
   if (timeout == 0)
      return;
   memset(&timeout->threadId, 0, sizeof(pthread_t));
   timeout->ready = false;
   timeout->running = true;
   timeout->selfCleanup = false;
   pthread_mutex_init(&timeout->condition_mutex, NULL); /* int rc return is always 0 */
   pthread_cond_init(&timeout->condition_cond, NULL);
}

/**
 * Create an instance of a property struct.
 * @param name Thread name, a clone is kept
 */
Timeout *createTimeout(const char* const name) {
   Timeout *timeout = (Timeout *) calloc(1, sizeof(Timeout));
   timeout->verbose = false;
   timeout->name = (name != 0) ? strcpyAlloc(name) : 0;
   timeout->setTimeoutListener = setTimeoutListener;
   initTimeout(timeout);
   return timeout;
}

static void stopThread(Timeout *timeout) {
   pthread_t threadId;
   if (timeout == 0 || _isNull(&timeout->threadId))
      return;
   if (_isMyThread(timeout)) {
      /* to avoid memory leak on needs to call pthread_join() or pthread_detach() */
      pthread_detach(threadId);
      timeout->running = false;
      return;
   }
   threadId = timeout->threadId;
   pthread_mutex_lock(&timeout->condition_mutex);
   timeout->running = false;
   pthread_cond_broadcast(&timeout->condition_cond);
   pthread_mutex_unlock(&timeout->condition_mutex);
   if (!_isNull(&threadId))
      pthread_join(threadId, NULL);
   if (timeout->verbose) printf("Timeout.c Joined threadId=%ld\n", get_pthread_id(threadId));
   initTimeout(timeout);
}

void freeTimeout(Timeout *timeout) {
   if (timeout == 0)
      return;
   stopThread(timeout);
   free((char *) timeout->name);
   free(timeout);
}

/**
 * See header Timeout.h for documentation
 * May not be call from within a timeout as this would destroy the thread during this call
 */
static int setTimeoutListener(Timeout * const timeout, TimeoutCbFp timeoutCbFp,
      const long int delay, void * const userData, void * const userData2) {
   int iret;
   int i;

   if (timeout == 0)
      return -1;

   timeout->timeoutContainer.timeoutCbFp = timeoutCbFp;
   timeout->timeoutContainer.delay = delay;
   timeout->timeoutContainer.userData = userData;
   timeout->timeoutContainer.userData2 = userData2;

   /* delay==0: cancel timer */
   if (delay < 1) {
      if (_isMyThread(timeout)) {
         if (timeout->verbose)
            printf("Timeout.c Calling setTimeoutListener from timer thread callback\n");
         /*
          The timeoutMainLoop called us here: it is at the end of the while loop
          and like this the thread will die as soon as the user cb returns
         */
         timeout->selfCleanup = true;
         timeout->running = false;
      }
      else {
         /* Another thread called us, so clean up immediately */
         if (timeout->verbose)
            printf("Timeout.c Stopping timer %s threadId=%ld, callingThreadId=%ld\n", timeout->name, get_pthread_id(timeout->threadId), get_pthread_id(pthread_self()));
         stopThread(timeout);
      }
      return 0;
   }

   if (!_isNull(&timeout->threadId)) {
      if (timeout->verbose)
         printf("Timeout.c Warning: Calling setTimeoutListener twice is not reinitializing immediately the timer timeout time\n");
      return -1;
   }

   if (timeout->timeoutContainer.timeoutCbFp == 0) {
      printf("Timeout.c Warning: calling setTimeoutListener with 0 callback pointer\n");
   }

   /* pthread_attr.name before calling pthread_create() ? pthread_setname(timeout->name) pthread_attr_setname() */
   iret = pthread_create(&timeout->threadId, NULL, timeoutMainLoop, (void*) timeout);

   /* Block until timer thread is ready */
   for (i=0; i<50; i++) {
      if (timeout->ready)
         break;
      sleepMillis(1);
   }
   if (i >= 50)
      printf("Timeout.c Warning: calling setTimeoutListener is not getting ready\n");

   return iret; /* 0 == success */
}

/**
 * Run by the new created thread, calls the clients update method.
 * Leaving this pthread start routine does an implicit pthread_exit().
 * @param ptr Is Timeout * holding all necessary informations
 * @return 0 on success, 1 on error. The return value is the exit value returned by pthread_join()
 */
static void *timeoutMainLoop(void *ptr) {
   Timeout *timeout = (Timeout *) ptr;
   while (timeout->running) {
      int64_t startNanos = getTimestamp();
      int ret = 0;
      struct timespec abstime;

      pthread_mutex_lock(&timeout->condition_mutex);

      timeout->ready = true;

      /* calculate absolute time from relative delay millis */
      getAbsoluteTime(timeout->timeoutContainer.delay, &abstime);

      /* protect against spurious wake ups */
      while (timeout->running) {
         bool timeElapsed = false;
         ret
               = pthread_cond_timedwait(&timeout->condition_cond, &timeout->condition_mutex,
                     &abstime);
         /* check if delay reached */
         timeElapsed = (getTimestamp() - startNanos) >= timeout->timeoutContainer.delay * 1000000L;
         /* ret == 110 for timed wake up on Linux */
         /* ret == 0 for signal wake up on Linux */
         /*if (ret == ETIMEDOUT) { Not found on my Linux box?! */
         if (timeElapsed)
            break;
      }

      pthread_mutex_unlock(&timeout->condition_mutex);

      if (!timeout->running)
         break;

      /*sleepMillis(timeout->timeoutContainer.delay);*/

      if (timeout->timeoutContainer.timeoutCbFp != 0) {
         /* Callback client, has registered to receive callback */
         timeout->timeoutContainer.timeoutCbFp(timeout, timeout->timeoutContainer.userData,
               timeout->timeoutContainer.userData2);
      }
   }

   if (timeout->selfCleanup) {
      /* to avoid memory leak one needs to call pthread_join() or pthread_detach() */
      pthread_detach(timeout->threadId);
      initTimeout(timeout); /* Thread dies, reset timeout struct */
   }
   return 0;
}

# ifdef TIMEOUT_UTIL_MAIN
/*
 * gcc -g -Wall -pedantic -DTIMEOUT_UTIL_MAIN=1 -lpthread -I.. -o Timeout Timeout.c helper.c
 */
static void onTimeout(Timeout *timeout, void *userData, void *userData2) {
   const char *data = (char *) userData;
   char timeStr[64];
   printf("%s Timeout occurred, timer=%s delay=%ld userData=%s\n",
         getCurrentTimeStr(timeStr, 64), timeout->name,
         timeout->timeoutContainer.delay, data);
}
int main()
{
   const long millisecs = 1000;
   printf("millisec=%ld\n", millisecs);
   {
      Timeout *timeout = createTimeout("TestTimer");
      timeout->setTimeoutListener(timeout, onTimeout, millisecs, "dummyData", 0);
      while (getInputKey("Hit 'q' to quit") != 'q');
      freeTimeout(timeout);
      printf("Bye\n");
   }
   return 0;
}
# endif

/*----------------------------------------------------------------------------
 Name:      xmlBlaster/testsuite/src/c/TestTimeout.c
 Project:   xmlBlaster.org
 Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
 Comment:   Test C client library
 Author:    "Marcel Ruff" <xmlBlaster@marcelruff.info>
 Compile:   cd xmlBlaster; build c
 Invoke:    'TestTimeout'
 See:       http://www.xmlblaster.org/xmlBlaster/doc/requirements/c.client.socket.html
 -----------------------------------------------------------------------------*/
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <util/helper.h>
#include <util/Timeout.h>
#include "test.h"

static int countTimeouts = 0;

/**
 * Callback function on timeout.
 */
static void onTimeout(Timeout *timeout, void *userData) {
	const char *data = (char *) userData;
	char timeStr[64];
	printf("%s Timeout occurred, timer=%s delay=%ld userData=%s\n",
			getCurrentTimeStr(timeStr, 64), timeout->name,
			timeout->timeoutContainer.delay, data);
	/*mu_assert("onTimeout()", !strcmp("dummyData", data));*/
	countTimeouts++;
}

static const char * test_timeout() {
	const long millisecs = 2000;
	countTimeouts = 0;
	printf("millisec=%ld\n", millisecs);
	{
		Timeout *timeout = createTimeout("TestTimer");
		timeout->setTimeoutListener(timeout, onTimeout, millisecs, "dummyData");
		sleepMillis(5000);
		mu_assert("test_timeout()", 2==countTimeouts);
		freeTimeout(timeout);
		printf("SUCCESS test_timeout\n");
	}
	return 0;
}

static const char * test_timeoutNoCb() {
	const long millisecs = 2000;
	countTimeouts = 0;
	printf("millisec=%ld\n", millisecs);
	{
		Timeout *timeout = createTimeout("TestTimerNoCb");
		timeout->setTimeoutListener(timeout, 0, millisecs, "dummyData");
		sleepMillis(3000);
		mu_assert("test_timeout()", 0==countTimeouts);
		freeTimeout(timeout);
		printf("SUCCESS test_timeoutNoCb\n");
	}
	return 0;
}

static const char * test_timeoutErr() {
	const long millisecs = 2000;
	countTimeouts = 0;
	printf("millisec=%ld\n", millisecs);
	{
		Timeout *timeout = createTimeout("TestTimerErr");
		timeout->setTimeoutListener(timeout, 0, 200, 0);
		sleepMillis(1000);
		timeout->setTimeoutListener(timeout, onTimeout, 200, 0);
		sleepMillis(1000);
		timeout->setTimeoutListener(timeout, onTimeout, millisecs, "dummyData");
		countTimeouts = 0;
		sleepMillis(5000);
		mu_assert("test_timeout()", 2==countTimeouts);
		freeTimeout(timeout);
		printf("SUCCESS test_timeoutErr\n");
	}
	return 0;
}

/**
 * Callback function on timeout.
 */
static void onTimeoutTwice(Timeout *timeout, void *userData) {
	int *data = (int *) userData;
	char timeStr[64];
	*data += 1;
	printf("%s Timeout occurred, timer=%s delay=%ld count=%d\n",
			getCurrentTimeStr(timeStr, 64), timeout->name,
			timeout->timeoutContainer.delay, *data);
}

static const char * test_timeoutTwice() {
	const long millisecs = 2000;
	Timeout *timeout1 = 0;
	int count1 = 0;
	Timeout *timeout2 = 0;
	int count2 = 0;
	countTimeouts = 0;

	timeout1 = createTimeout("TestTimer1");
	timeout1->setTimeoutListener(timeout1, onTimeoutTwice, millisecs, &count1);

	timeout2 = createTimeout("TestTimer2");
	timeout2->setTimeoutListener(timeout2, onTimeoutTwice, millisecs, &count2);

	sleepMillis(5000);

	mu_assert("test_timeoutTwice()", 2==count1);
	mu_assert("test_timeoutTwice()", 2==count2);

	freeTimeout(timeout1);
	freeTimeout(timeout2);
	printf("SUCCESS test_timeoutTwice\n");
	return 0;
}

static const char *all_tests() {
	mu_run_test(test_timeoutErr);
	mu_run_test(test_timeoutNoCb);
	mu_run_test(test_timeout);
	mu_run_test(test_timeoutTwice);
	return 0;
}

/**
 * gcc -g -Wall -pedantic -lpthread -I../../../src/c -o TestTimeout TestTimeout.c ../../../src/c/util/Timeout.c ../../../src/c/util/helper.c
 * exit(0) if OK
 */
int main(int argc_, char **argv_) {
	const char *result;
	result = all_tests();
	if (result != 0) {
		printf("[TEST FAIL] %s\n", result);
	} else {
		printf("ALL %d TESTS PASSED\n", tests_run);
	}
	printf("Tests run: %d\n", tests_run);
	return result != 0;
}

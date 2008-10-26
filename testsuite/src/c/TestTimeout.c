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

static void onTimeout(Timeout *timeout, void *userData) {
	const char *data = (char *) userData;
	char timeStr[64];
	printf("%s Timeout occurred, timer=%s delay=%ld userData=%s\n",
			getCurrentTimeStr(timeStr, 64), timeout->name,
			timeout->timeoutContainer.delay, data);
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

static const char *all_tests() {
	mu_run_test(test_timeout);
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

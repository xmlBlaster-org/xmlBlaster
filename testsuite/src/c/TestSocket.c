/*----------------------------------------------------------------------------
Name:      xmlBlaster/testsuite/src/c/TestSocket.c
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Test C client library
Author:    "Marcel Ruff" <xmlBlaster@marcelruff.info>
Compile:   cd xmlBlaster; build c
Invoke:    'TestSocket'
See:       http://www.xmlblaster.org/xmlBlaster/doc/requirements/c.client.socket.html
-----------------------------------------------------------------------------*/
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <util/helper.h>
#include <socket/xmlBlasterSocket.h>
#include "test.h"

static const char * test_exceptionEncode()
{
   XmlBlasterException exception, exceptionAfter;
   XmlBlasterBlob blob;

   {
      initializeXmlBlasterException(&exception);

      encodeXmlBlasterException(&blob, &exception, true);

      convertToXmlBlasterException(&blob, &exceptionAfter, true);

      freeBlobHolderContent(&blob);

      mu_assertEqualsString("Exception encoding failed", exception.errorCode, exceptionAfter.errorCode);
      mu_assertEqualsString("Exception encoding failed", exception.message, exceptionAfter.message);
      mu_assertEqualsBool("Exception encoding failed", true, exceptionAfter.remote);
   }
   {
      initializeXmlBlasterException(&exception);
      strncpy0(exception.errorCode, "communication.noConnection", XMLBLASTEREXCEPTION_ERRORCODE_LEN);
      SNPRINTF(exception.message, XMLBLASTEREXCEPTION_MESSAGE_LEN,
                     "[%.100s:%d] Lost connection to xmlBlaster with server side EOF", __FILE__, __LINE__);

      encodeXmlBlasterException(&blob, &exception, true);

      convertToXmlBlasterException(&blob, &exceptionAfter, true);

      freeBlobHolderContent(&blob);

      mu_assertEqualsString("Exception encoding failed", exception.errorCode, exceptionAfter.errorCode);
      mu_assertEqualsString("Exception encoding failed", exception.message, exceptionAfter.message);
      mu_assertEqualsBool("Exception encoding failed", true, exceptionAfter.remote);
   }
   return 0;
}

static const char *all_tests()
{
   mu_run_test(test_exceptionEncode);
   return 0;
}

/**
 * exit(0) if OK
 */
int main(int argc_, char **argv_)
{
   const char *result;

   result = all_tests();

   if (result != 0) {
      printf("[TEST FAIL] %s\n", result);
   }
   else {
      printf("ALL %d TESTS PASSED\n", tests_run);
   }
   printf("Tests run: %d\n", tests_run);

   return result != 0;
}

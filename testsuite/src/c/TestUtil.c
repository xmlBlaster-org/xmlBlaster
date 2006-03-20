/*----------------------------------------------------------------------------
Name:      xmlBlaster/testsuite/src/c/TestUtil.c
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Test C client library
Author:    "Marcel Ruff" <xmlBlaster@marcelruff.info>
Compile:   cd xmlBlaster; build c
Invoke:    'TestUtil'
See:       http://www.xmlblaster.org/xmlBlaster/doc/requirements/c.client.socket.html
-----------------------------------------------------------------------------*/
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <util/helper.h>
#include <util/msgUtil.h>
#include "test.h"

static const char * test_trim(const char *expected, const char *source)
{
   char tr[256];
   if (expected == 0 && source == 0) {
      trim(0);  /* SIGSEG ? */
      return 0;
   }
   strcpy(tr, source);
   trim(tr);
   mu_assert2("trim()", expected, tr, strlen(tr) == strlen(expected));
   mu_assert2("trim()", expected, tr, strlen(tr) == strlen(expected));
   mu_assert2("trim()", expected, tr, strcmp(tr, expected)==0);
   return 0;
}

static const char * test_trimStart(const char *expected, const char *source)
{
   char tr[256];
   if (expected == 0 && source == 0) {
      trim(0);  /* SIGSEG ? */
      return 0;
   }
   strcpy(tr, source);
   trimStart(tr);
   mu_assert2("trimStart()", expected, tr, strlen(tr) == strlen(expected));
   mu_assert2("trimStart()", expected, tr, strlen(tr) == strlen(expected));
   mu_assert2("trimStart()", expected, tr, strcmp(tr, expected)==0);
   return 0;
}

static const char * test_trimEnd(const char *expected, const char *source)
{
   char tr[256];
   if (expected == 0 && source == 0) {
      trim(0);  /* SIGSEG ? */
      return 0;
   }
   strcpy(tr, source);
   trimEnd(tr);
   mu_assert2("trimEnd()", expected, tr, strlen(tr) == strlen(expected));
   mu_assert2("trimEnd()", expected, tr, strlen(tr) == strlen(expected));
   mu_assert2("trimEnd()", expected, tr, strcmp(tr, expected)==0);
   return 0;
}

static const char *all_tests()
{
   mu_run_test2(test_trim, "28316", "28316");
   mu_run_test2(test_trim, "28316", "     28316");
   mu_run_test2(test_trim, "28316", "  \t   28316   ");
   mu_run_test2(test_trim, "", " \t\n  ");
   mu_run_test2(test_trim, "", "");
   mu_run_test2(test_trim, "2", " 2 ");
   mu_run_test2(test_trim, "2 3 4", " 2 3 4\t\n");
   mu_run_test2(test_trim, (char *)0, (char *)0);

   mu_run_test2(test_trimStart, "28316", "28316");
   mu_run_test2(test_trimStart, "28316", "     28316");
   mu_run_test2(test_trimStart, "28316 \t ", "  \t   28316 \t ");
   mu_run_test2(test_trimStart, "", " \t\n  ");
   mu_run_test2(test_trimStart, "", "");
   mu_run_test2(test_trimStart, "2 ", " 2 ");
   mu_run_test2(test_trimStart, "2 3 4\t\n", " 2 3 4\t\n");
   mu_run_test2(test_trimStart, (char *)0, (char *)0);

   mu_run_test2(test_trimEnd, "28316", "28316");
   mu_run_test2(test_trimEnd, "28316", "28316   ");
   mu_run_test2(test_trimEnd, "  \t   28316", "  \t   28316 \t ");
   mu_run_test2(test_trimEnd, "", " \t\n  ");
   mu_run_test2(test_trimEnd, "", "");
   mu_run_test2(test_trimEnd, " 2", " 2 ");
   mu_run_test2(test_trimEnd, "\t 2 3 4", "\t 2 3 4\t\n");
   mu_run_test2(test_trimEnd, (char *)0, (char *)0);
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

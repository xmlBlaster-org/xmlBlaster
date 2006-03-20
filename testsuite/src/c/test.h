/*
 * file: test.h (originally minunit.h)
 * Comment: No multi threading safety
 * Copyright: http://www.jera.com/techinfo/jtns/jtn002.html
 * See: http://www.eskimo.com/~scs/C-faq/q10.4.html
 */
static char MU_ASSERT_TEXT[2048];
static int tests_run = 0;

#define mu_assertException(message, exceptionStr) \
   do { sprintf(MU_ASSERT_TEXT, "[TEST FAIL]%s:%d %.500s: Caught exception: '%.500s'", __FILE__, __LINE__, message, exceptionStr); return MU_ASSERT_TEXT; } while (0)

#define mu_assertEqualsBool(message, expected, was) \
   do { if (expected != was) { sprintf(MU_ASSERT_TEXT, "[TEST FAIL]%s:%d %.500s: expected='%.500s' was='%.500s'", __FILE__, __LINE__, message, expected?"true":"false", was?"true":"false"); return MU_ASSERT_TEXT; }} while (0)

#define mu_assertEqualsInt(message, expected, was) \
   do { if (expected != was) { sprintf(MU_ASSERT_TEXT, "[TEST FAIL]%s:%d %.500s: expected='%d' was='%d'", __FILE__, __LINE__, message, (int)expected, (int)was); return MU_ASSERT_TEXT; }} while (0)

#define mu_assertEqualsShort(message, expected, was) \
   do { if (expected != was) { sprintf(MU_ASSERT_TEXT, "[TEST FAIL]%s:%d %.500s: expected='%hd' was='%hd'", __FILE__, __LINE__, message, expected, was); return MU_ASSERT_TEXT; }} while (0)

#define mu_assertEqualsLong(message, expected, was) \
   do { if (expected != was) { sprintf(MU_ASSERT_TEXT, "[TEST FAIL]%s:%d %.500s: expected='%ld' was='%ld'", __FILE__, __LINE__, message, expected, was); return MU_ASSERT_TEXT; }} while (0)

#define mu_assertEqualsString(message, expected, was) \
   do { if (strcmp(expected,was) != 0) { sprintf(MU_ASSERT_TEXT, "[TEST FAIL]%s:%d %.500s: expected='%.500s' was='%.500s'", __FILE__, __LINE__, message, expected, was); return MU_ASSERT_TEXT; }} while (0)

#define mu_assert2(message, expected, was, test) \
   do { if (!(test)) { sprintf(MU_ASSERT_TEXT, "[TEST FAIL]%s:%d %.500s: expected='%.500s' was='%.500s'", __FILE__, __LINE__, message, expected, was); return MU_ASSERT_TEXT; }} while (0)

#define mu_assert(message, test) \
   do { if (!(test)) { sprintf(MU_ASSERT_TEXT, "[TEST FAIL]%s:%d %.500s", __FILE__, __LINE__, message); return MU_ASSERT_TEXT; }} while (0)

#define mu_fail(message) \
   do { sprintf(MU_ASSERT_TEXT, "[TEST FAIL]%s:%d %.500s", __FILE__, __LINE__, message); return MU_ASSERT_TEXT; } while (0)

#define mu_run_test(test) \
   do { const char *message = test(); tests_run++; \
                              if (message) return message; } while (0)
#define mu_run_test2(test, a, b) \
   do { const char *message = test(a,b); tests_run++; \
                              if (message) return message; } while (0)


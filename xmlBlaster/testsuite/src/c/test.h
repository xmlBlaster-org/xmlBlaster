/*
 * file: test.h (originally minunit.h)
 * Copyright: http://www.jera.com/techinfo/jtns/jtn002.html
 * See: http://www.eskimo.com/~scs/C-faq/q10.4.html
 */
extern char MU_ASSERT_TEXT[1024];
extern int tests_run;

#define mu_assert2(message, expected, was, test) do { if (!(test)) { sprintf(MU_ASSERT_TEXT, "%s: expected='%s' was='%s'", message, expected, was); return MU_ASSERT_TEXT; }} while (0)
#define mu_assert(message, test) do { if (!(test)) return message; } while (0)
#define mu_fail(message) do { return message; } while (0)
#define mu_run_test(test) do { const char *message = test(); tests_run++; \
                              if (message) return message; } while (0)
#define mu_run_test2(test, a, b) do { const char *message = test(a,b); tests_run++; \
                              if (message) return message; } while (0)


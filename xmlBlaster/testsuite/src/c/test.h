/*
 * file: test.h (originally minunit.h)
 * Copyright: http://www.jera.com/techinfo/jtns/jtn002.html
 * See: http://www.eskimo.com/~scs/C-faq/q10.4.html
 */
#define mu_assert(message, test) do { if (!(test)) return message; } while (0)
#define mu_run_test(test) do { const char *message = test(); tests_run++; \
                              if (message) return message; } while (0)
extern int tests_run;

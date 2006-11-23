/*----------------------------------------------------------------------------
Name:      Properties.c
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   A tiny helper to encapsulate command line and environment properties
Author:    "Marcel Ruff" <xmlBlaster@marcelruff.info>
Compile:   gcc -DPropertiesMain -D_ENABLE_STACK_TRACE_ -rdynamic -export-dynamic -Wall -pedantic -g -D_REENTRANT -I.. -o PropertiesMain Properties.c
See:       http://www.xmlblaster.org/xmlBlaster/doc/requirements/client.c.socket.html
-----------------------------------------------------------------------------*/
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
/*#include <errno.h>*/
/*#include <sys/types.h>*/
#include "helper.h"
#include "Properties.h"

static const char *getString(Properties *props, const char *key, const char *defaultValue);
static bool getBool(Properties *props, const char *key, bool defaultValue);
static int getInt(Properties *props, const char *key, int defaultValue);
static long getLong(Properties *props, const char *key, long defaultValue);
static int64_t getInt64(Properties *props, const char *key, int64_t defaultValue);

/**
 * Create an instance of a property struct. 
 * NOTE: Our properties point on the passed argv memory, so you should
 * not free the original argv memory.
 * @param argc The number of command line args
 * @param argv The command line arguments, argv[0] is expected to be the
 *             name of the process, argv[1] should start with '-' and 
 *             argv[2] is the value of the argv[1] key ...
 *             argv can be NULL if argc == 0
 */
Properties *createProperties(int argc, const char* const* argv) {
   int iarg;
   Properties *props = (Properties *)calloc(1, sizeof(Properties));
   props->argc = 0;
   props->argv = 0;
   props->execName = (argv != 0) ? argv[0] : __FILE__;
   props->getString = getString;
   props->getBool = getBool;
   props->getInt = getInt;
   props->getLong = getLong;
   props->getInt64 = getInt64;

   if (argc > 1) {
      /* strip the executable name and the dash '-' */
      props->argc = argc-1;
      props->argv = (char **)calloc(props->argc, sizeof(char *));
      for (iarg=1; iarg < argc; iarg++) {
         if (argv[iarg] == 0 || strlen(argv[iarg]) == 0)
            props->argv[iarg-1] = (char *)argv[iarg];
         else if ((iarg % 2) == 1 && *argv[iarg] == '-')
            props->argv[iarg-1] = (char *)argv[iarg]+1;
         else
            props->argv[iarg-1] = (char *)argv[iarg];
      }
   }
   return props;
}

void freeProperties(Properties *props)
{
   if (props == 0) return;

   if (props->argc > 0) {
      free(props->argv);
      props->argc = 0;
      props->argv = 0;
   }
   free(props);
}

/**
 * See header Properties.h for documentation
 */
void dumpProperties(Properties *props)
{
   int iarg;
   if (props == 0) return;

   for (iarg=0; iarg < props->argc-1; iarg++) {
      printf("#%d, %s=%s\n", iarg, props->argv[iarg], props->argv[iarg+1]);
      iarg++;
   }
}

/**
 * See header Properties.h for documentation
 */
static const char *getString(Properties *props, const char *key, const char *defaultValue)
{
   int iarg;
   const char *p;

   if (key == 0) return defaultValue;

   for (iarg=0; iarg < props->argc-1; iarg++) {
      if (strcmp(props->argv[iarg], key) == 0)
         return props->argv[++iarg];
   }
   
/*
WIN32
   char *pValue;
   size_t len;
   errno_t err = _dupenv_s( &pValue, &len, "pathext" );
   // returns 0 if not found!
   if ( err ) return -1;
   printf( "pathext = %s\n", pValue );
   free( pValue );
   err = _dupenv_s( &pValue, &len, "nonexistentvariable" );
   if ( err ) return -1;
   printf( "nonexistentvariable = %s\n", pValue );
   free( pValue ); // It's OK to call free with NULL

   // not thread save as putenv could change
   errno_t getenv_s(size_t *pReturnValue, char* buffer, size_t sizeInBytes, const char *varname);


UNIX: int getenv_r(const char *name, char *buf, size_t len);
   returns -1 if not found
*/
   p = getenv(key);

   if (p != 0) return p;
   return defaultValue;
}

static bool getBool(Properties *props, const char *key, bool defaultValue)
{
   const char *valP = getString(props, key, 0);
   if (valP != 0) {
      if (!strcmp(valP, "false") || !strcmp(valP, "FALSE") || !strcmp(valP, "0"))
         return false;
      else
         return true;
   }
   return defaultValue;
}

static int getInt(Properties *props, const char *key, int defaultValue)
{
   return (int)getLong(props, key, defaultValue);
}

static long getLong(Properties *props, const char *key, long defaultValue)
{
   const char *valP = getString(props, key, 0);
   if (valP != 0) {
      long val;
      if (strToLong(&val, valP) == true)
         return val;
   }
   return defaultValue;
}

static int64_t getInt64(Properties *props, const char *key, int64_t defaultValue)
{
   const char *valP = getString(props, key, 0);
   if (valP != 0) {
      int64_t val;
      if (strToInt64(&val, valP) == true)
         return val;
   }
   return defaultValue;
}

#ifdef PropertiesMain /* compile a standalone test program */

Dll_Export bool strToInt64(int64_t *val, const char * const str) {
   if (str == 0 || val == 0) return false;
   return (SSCANF(str, PRINTF_PREFIX_INT64_T, val) == 1) ? true : false;
}

/**
 * icc -DPropertiesMain -Wall -g -I.. -o PropertiesMain Properties.c
 * Invoke:
 * export MY_SETTING="Hello World"
 * PropertiesMain -logLevel TRACE  -numTests 10  -timeout -999 -isPersistent true -isDurable false
 */
int main(int argc, char** argv)
{
   Properties *props = createProperties(argc, (const char* const*)argv);
   printf("MY_SETTING=%s\n", props->getString(props, "MY_SETTING", "DUMMY"));
   printf("logLevel=%s\n", props->getString(props, "logLevel", "DUMMY"));
   printf("isPersistent=%d\n", props->getBool(props, "isPersistent", false));
   printf("isDurable=%d\n", props->getBool(props, "isDurable", true));
   printf("numTests=%d\n", props->getInt(props, "numTests", -1));
   printf("timeout=%ld\n", props->getLong(props, "timeout", -1l));
   printf("timeout="PRINTF_PREFIX_INT64_T"\n", props->getInt64(props, "lonLong", -1LL)); /* "%I64d", "%ld", "%lld" */
   freeProperties(props);
   return 0;
}
#endif /* #ifdef PropertiesMain */


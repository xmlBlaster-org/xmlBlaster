/*----------------------------------------------------------------------------
Name:      Properties.c
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Wraps raw socket connection to xmlBlaster
           Implements sync connection and async callback
           Needs pthread to compile (multi threading).
Author:    "Marcel Ruff" <xmlBlaster@marcelruff.info>
Compile:   gcc -DPropertiesMain -D_ENABLE_STACK_TRACE_ -rdynamic -export-dynamic -Wall -pedantic -g -D_REENTRANT -I.. -o PropertiesMain Properties.c
See:       http://www.xmlblaster.org/xmlBlaster/doc/requirements/client.c.socket.html
-----------------------------------------------------------------------------*/
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <ctype.h>
#include <errno.h>
#include <sys/types.h>
#include "Properties.h"

static const char *getString(Properties *props, const char *key, const char *defaultValue);
static bool getBool(Properties *props, const char *key, bool defaultValue);
static int getInt(Properties *props, const char *key, int defaultValue);
static long getLong(Properties *props, const char *key, long defaultValue);

/**
 * Create an instance of a property struct. 
 */
Properties *createProperties(int argc, char** argv) {
   int iarg;
   Properties *props = (Properties *)calloc(1, sizeof(Properties));
   props->argc = 0;
   props->argv = 0;
   props->execName = (argv != 0) ? argv[0] : __FILE__;
   props->getString = getString;
   props->getBool = getBool;
   props->getInt = getInt;
   props->getLong = getLong;

   if (argc > 1) {
      /* strip the executable name and the dash '-' */
      props->argc = argc-1;
      props->argv = (char **)calloc(props->argc, sizeof(char *));
      for (iarg=1; iarg < argc; iarg++) {
         if (argv[iarg] == 0 || strlen(argv[iarg]) == 0)
            props->argv[iarg-1] = argv[iarg];
         else if ((iarg % 2) == 1 && *argv[iarg] == '-')
            props->argv[iarg-1] = argv[iarg]+1;
         else
            props->argv[iarg-1] = argv[iarg];
      }
   }
   /*
   for (iarg=0; iarg < props->argc-1; iarg++) {
      printf("#%d, %s=%s\n", iarg, props->argv[iarg], props->argv[iarg+1]);
      iarg++;
   }
   */
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
static const char *getString(Properties *props, const char *key, const char *defaultValue)
{
   int iarg;
   const char *p;

   if (key == 0) return defaultValue;

   for (iarg=0; iarg < props->argc-1; iarg++) {
      if (strcmp(props->argv[iarg], key) == 0)
         return props->argv[++iarg];
   }
   
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
      if (sscanf(valP, "%ld", &val) == 1)
         return val;
   }
   return defaultValue;
}

#ifdef PropertiesMain /* compile a standalone test program */

/**
 * Invoke:
 * export MY_SETTING="Hello World"
 * PropertiesMain -logLevel TRACE  -numTests 10  -timeout -999 -isPersistent true -isDurable false
 */
int main(int argc, char** argv)
{
   Properties *props = createProperties(argc, argv);
   printf("MY_SETTING=%s\n", props->getString(props, "MY_SETTING", "DUMMY"));
   printf("logLevel=%s\n", props->getString(props, "logLevel", "DUMMY"));
   printf("isPersistent=%d\n", props->getBool(props, "isPersistent", false));
   printf("isDurable=%d\n", props->getBool(props, "isDurable", true));
   printf("numTests=%d\n", props->getInt(props, "numTests", -1));
   printf("timeout=%ld\n", props->getLong(props, "timeout", -1l));
   freeProperties(props);
   return 0;
}
#endif /* #ifdef PropertiesMain */


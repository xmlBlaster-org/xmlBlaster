/*----------------------------------------------------------------------------
Name:      Properties.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Hold environment and command line properties.
Author:    "Marcel Ruff" <xmlBlaster@marcelruff.info>
Date:      06/2003
See:       http://www.xmlblaster.org/xmlBlaster/doc/requirements/client.c.socket.html
-----------------------------------------------------------------------------*/
#ifndef XMLBLASTER_Properties_H
#define XMLBLASTER_Properties_H

#include <util/basicDefs.h>

#ifdef __cplusplus
#ifndef XMLBLASTER_C_COMPILE_AS_CPP /* 'g++ -DXMLBLASTER_C_COMPILE_AS_CPP ...' allows to compile the lib as C++ code */
extern "C" {
#endif
#endif

struct PropertiesStruct;
typedef struct PropertiesStruct Properties;

/* Declare function pointers to use in struct to simulate object oriented access */
typedef const char *( * XmlBlasterPropertiesGetString)(Properties *xb, const char * key, const char *defaultValue);
typedef bool ( * XmlBlasterPropertiesGetBool)(Properties *xb, const char * key, bool defaultValue);
typedef int ( * XmlBlasterPropertiesGetInt)(Properties *xb, const char * key, int defaultValue);
typedef long ( * XmlBlasterPropertiesGetLong)(Properties *xb, const char * key, long defaultValue);
typedef int64_t ( * XmlBlasterPropertiesGetInt64)(Properties *xb, const char * key, int64_t defaultValue);
typedef double ( * XmlBlasterPropertiesGetDouble)(Properties *xb, const char * key, double defaultValue);

/**
 * Parses argv and has accessor methods for different data types.
 * All client access to Properties goes over this struct and its function pointers.
 */
struct PropertiesStruct {
   int argc;             /**< Number of #argv */
   char **argv;          /**< Pointer on the memory of the passed arguments */
   const char *execName; /**< The executable name */
   /**
    * Access command line settings of for "myExec -logLevel TRACE".
    * If the key is not found the environment is checked, if this
    * does not contain the key the defaultValue is returned.
    * @param xb The 'this' pointer
    * @param key e.g. "logLevel"
    * @param defaultValue e.g. "WARN"
    */
   XmlBlasterPropertiesGetString getString; /**< Access a property as a string */
   XmlBlasterPropertiesGetBool getBool;     /**< Access a property converted to bool */
   XmlBlasterPropertiesGetInt getInt;       /**< Access a property converted to int */
   XmlBlasterPropertiesGetLong getLong;     /**< Access a property converted to long */
   XmlBlasterPropertiesGetInt64 getInt64;   /**< Access a property converted to a 64 bit long */
   XmlBlasterPropertiesGetDouble getDouble; /**< Access a property converted to double */
};

/**
 * Get an instance of this Properties struct.
 * NOTE: Every call creates a new and independent instance
 * @param argc Number of argv entries
 * @param argv The first entry is expected to be the executable name, the others are tuples of form "-logLevel" "TRACE"
 * @return NULL if bootstrapping failed. If not NULL you need to free() it when you are done
 * usually by calling freeProperties().
 */
Dll_Export extern Properties *createProperties(int argc, const char* const* argv);

/**
 * Free your instance after accessing xmlBlaster.
 */
Dll_Export extern void freeProperties(Properties *props);

/**
 * Dump properties to console, for debugging only.
 */
Dll_Export extern void dumpProperties(Properties *props);

#ifdef __cplusplus
#ifndef XMLBLASTER_C_COMPILE_AS_CPP
}
#endif
#endif

#endif /* XMLBLASTER_Properties_H */


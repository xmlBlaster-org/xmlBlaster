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

/**
 * All client access to Properties goes over this struct and its function pointers. 
 */
struct PropertiesStruct {
   int argc;
   char **argv;
   const char *execName;
   /**
    * Access command line settings of for "myExec -logLevel TRACE".
    * If the key is not found the envrionment is checked, if this
    * does not contain the key the defaultValue is returned.
    * @param key e.g. "logLevel"
    * @param defaultValue e.g. "WARN"
    */
   XmlBlasterPropertiesGetString getString;
   XmlBlasterPropertiesGetBool getBool;
   XmlBlasterPropertiesGetInt getInt;
   XmlBlasterPropertiesGetLong getLong;
};

/**
 * Get an instance of this Properties struct. 
 * NOTE: Every call creates a new and independent instance
 * @param argc Number of argv entries
 * @param argv The first entry is expected to be the executable name, the others are tuples of form "-logLevel" "TRACE"
 * @return NULL if bootstrapping failed. If not NULL you need to free() it when you are done
 * usually by calling freeProperties().
 */
extern Properties *createProperties(int argc, const char* const* argv);

/**
 * Free your instance after accessing xmlBlaster. 
 */
extern void freeProperties(Properties *props);

#ifdef __cplusplus
#ifndef XMLBLASTER_C_COMPILE_AS_CPP
}
#endif
#endif

#endif /* XMLBLASTER_Properties_H */


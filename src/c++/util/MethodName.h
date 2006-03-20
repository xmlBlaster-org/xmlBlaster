/*-----------------------------------------------------------------------------
Name:      MethodName.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Method name constants
-----------------------------------------------------------------------------*/
#ifndef _UTIL_METHODNAME_H
#define _UTIL_METHODNAME_H

#include <util/xmlBlasterDef.h>
#include <string>

namespace org { namespace xmlBlaster {
  namespace util { namespace MethodName {

/**
 * Holding the constant names of the supported remote methods. 
 * <p />
 * @see xmlBlaster/src/java/org/xmlBlaster/util/def/MethodName.java
 */
//class Dll_Export MethodName { // Using namespace instead of class here

extern Dll_Export const std::string CONNECT;
extern Dll_Export const std::string DISCONNECT;
extern Dll_Export const std::string GET;
extern Dll_Export const std::string ERASE;
extern Dll_Export const std::string PUBLISH;
extern Dll_Export const std::string PUBLISH_ARR;
extern Dll_Export const std::string PUBLISH_ONEWAY;
extern Dll_Export const std::string SUBSCRIBE;
extern Dll_Export const std::string UNSUBSCRIBE;
extern Dll_Export const std::string UPDATE;
extern Dll_Export const std::string UPDATE_ONEWAY;
extern Dll_Export const std::string PING;
extern Dll_Export const std::string DUMMY;
extern Dll_Export const std::string UNKNOWN;

//}; // class MethodName


}}}} // namespace 

#endif



/*-----------------------------------------------------------------------------
Name:      Constants.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Holding some constants, see Constants.h for a description
-----------------------------------------------------------------------------*/
#if defined(_WINDOWS)
#pragma warning(disable:4786)
#endif


#include <util/Constants.h>


namespace org { namespace xmlBlaster {
  namespace util { namespace Constants {

Dll_Export const char * DEFAULT_SECURITYPLUGIN_TYPE = "htpasswd";
Dll_Export const char * DEFAULT_SECURITYPLUGIN_VERSION = "1.0";

Dll_Export long XMLBLASTER_OID_ROOT[] = { 1, 3, 6, 1, 4, 1, XMLBLASTER_SNMP }; // 11662

Dll_Export const char * STATE_OK = "OK";
Dll_Export const char * RET_OK = "<qos><state id='OK'/></qos>";

Dll_Export const char * STATE_TIMEOUT = "TIMEOUT";
Dll_Export const char * STATE_ERASED = "ERASED";
Dll_Export const char * STATE_FORWARD_ERROR = "FORWARD_ERROR";

Dll_Export const char * INFO_QUEUED = "QUEUED";

Dll_Export const char * RELATING_SESSION = "session";
Dll_Export const char * RELATING_SUBJECT = "subject";
Dll_Export const char * RELATING_UNRELATED = "unrelated";

Dll_Export const char * ONOVERFLOW_BLOCK = "block";
Dll_Export const char * ONOVERFLOW_DEADLETTER = "deadLetter";
Dll_Export const char * ONOVERFLOW_DISCARD = "discard";
Dll_Export const char * ONOVERFLOW_DISCARDOLDEST = "discardOldest";
Dll_Export const char * ONOVERFLOW_EXCEPTION = "exception";

Dll_Export const char * ONEXHAUST_KILL_SESSION = "killSession";

Dll_Export const char * SESSIONID_PRAEFIX = "sessionId:";
Dll_Export const char * SUBSCRIPTIONID_PRAEFIX = "__subId:";

Dll_Export const char * INTERNAL_OID_PRAEFIX = "__sys__";
Dll_Export const char * INTERNAL_OID_CLUSTER_PRAEFIX = "__sys__cluster";

Dll_Export const char * JDBC_OID = "__sys__jdbc";

Dll_Export const char * OID_DEAD_LETTER = "__sys__deadLetter";

Dll_Export const char * XPATH = "XPATH";
Dll_Export const char * EXACT = "EXACT";
//const char * const Constants::DOMAIN = "DOMAIN"; // doesn't compile with g++ 3.1.1
Dll_Export const char * REGEX = "REGEX";

Dll_Export const char * IOR     = "IOR";
Dll_Export const char * EMAIL   = "EMAIL";
Dll_Export const char * XML_RPC = "XML-RPC";

/*
const int getPriority(string prio, int defaultPriority)
{
  if (prio != null) {
     prio = prio.trim();
     try {
        return new Integer(prio).intValue();
     } catch (NumberFormatException e) {
        prio = prio.toUpperCase();
        if (prio.startsWith("MIN"))
           return Constants.MIN_PRIORITY;
        else if (prio.startsWith("LOW"))
           return Constants.LOW_PRIORITY;
        else if (prio.startsWith("NORM"))
           return Constants.NORM_PRIORITY;
        else if (prio.startsWith("HIGH"))
           return Constants.HIGH_PRIORITY;
        else if (prio.startsWith("MAX"))
           return Constants.MAX_PRIORITY;
        else
           Global.instance().getLog("core").warn(ME, "Wrong format of <priority>" + prio +
                "</priority>, expected a number between (inclusiv) 0 - 9, setting to message priority to "
                + defaultPriority);
     }
  }
  if (defaultPriority < Constants.MIN_PRIORITY || defaultPriority > Constants.MAX_PRIORITY) {
      Global.instance().getLog("core").warn(ME, "Wrong message defaultPriority=" + defaultPriority + " given, setting to NORM_PRIORITY");
      return Constants.NORM_PRIORITY;
  }
  return defaultPriority;
}
*/

}}}}; // namespace 



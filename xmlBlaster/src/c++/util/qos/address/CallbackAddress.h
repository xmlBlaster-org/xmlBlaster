/*------------------------------------------------------------------------------
Name:      CallbackAddress.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Holding callback address std::string and protocol std::string
Version:   $Id$
------------------------------------------------------------------------------*/

/**
 * Helper class holding callback address std::string and protocol std::string.
 * <p />
 * <pre>
 * &lt;callback type='XMLRPC' sessionId='4e56890ghdFzj0'
 *           pingInterval='60000' retries='5' delay='10000'
 *           oneway='false' useForSubjectQueue='true'
 *           dispatchPlugin='Priority,1.0'>
 *    http://server:8080/cb
 *    &lt;compress type='gzip' minSize='1000'/>
 *    &lt;burstMode collectTime='400'/>
 * &lt;/callback>
 * </pre>
 */
#ifndef _UTIL_CFG_CALLBACKADDRESS_H
#define _UTIL_CFG_CALLBACKADDRESS_H

#include <util/xmlBlasterDef.h>
#include <util/qos/address/AddressBase.h>
#include <string>

namespace org { namespace xmlBlaster { namespace util { namespace qos { namespace address {

class Dll_Export CallbackAddress : public org::xmlBlaster::util::qos::address::AddressBase
{
private:

   /**
    * Configure property settings
    */
   inline void initialize();

public:

   /**
    * @param type    The protocol type, e.g. "IOR", "SOCKET", "XMLRPC"
    * @param nodeId  A unique std::string (typically the cluster node id we connect to).<br />
    *   This is used for extended env-variable support, e.g. for a given
    *    <code>nodeId="heron"</ code>
    *   the command line argument (or xmlBlaster.property entry)
    *    <code>-cb.retries[heron] 20</code>
    *   is precedence over
    *    <code>-cb.retries 10</code>
    */
   CallbackAddress(org::xmlBlaster::util::Global& global, const std::string& type="", const std::string nodeId="");

   /**
    * copy constructor
    */
   CallbackAddress(const org::xmlBlaster::util::qos::address::AddressBase& addr);

   /**
    * Assignment operator
    */
   CallbackAddress& operator =(const org::xmlBlaster::util::qos::address::AddressBase& addr);

   /**
    * Shall this address be used for subject queue messages?
    * @return false if address is for session queue only
    */
   bool useForSubjectQueue();

   /**
    * Shall this address be used for subject queue messages?
    * @param useForSubjectQueue false if address is for session queue only
    */
   void useForSubjectQueue(bool useForSubjectQueue);

   /** @return The literal address as given by getRawAddress() */
   std::string toString();

   /**
    * Get a usage std::string for the server side supported callback connection parameters
    */
   std::string usage();
};

}}}}} // namespace

#endif

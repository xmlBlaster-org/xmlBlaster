/*------------------------------------------------------------------------------
Name:      Address.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Holding address std::string and protocol std::string
Version:   $Id$
------------------------------------------------------------------------------*/

/**
 * Helper class holding address std::string, protocol std::string and client side connection properties.
 * <p />
 * <pre>
 * &lt;address type='XMLRPC' sessionId='4e56890ghdFzj0'
 *           pingInterval='60000' retries='5' delay='10000'
 *           oneway='false'>
 *    http://server:8080/cb
 *    &lt;compress type='gzip' minSize='1000'/>
 *    &lt;burstMode collectTime='400'/> <!-- for publishOneway() calls -->
 * &lt;/address>
 * </pre>
 */
#ifndef _UTIL_CFG_ADDRESS_H
#define _UTIL_CFG_ADDRESS_H

#include <util/qos/address/AddressBase.h>

namespace org { namespace xmlBlaster { namespace util { namespace qos { namespace address {

class Dll_Export Address : public AddressBase
{
private:

   /**
    * Configure property settings. 
    * "-delay[heron] 20" has precedence over "-delay 10"
    * @see #Address(String, String)
    */
   inline void initialize();

public:

   /**
    * @param type    The protocol type, e.g. "IOR", "SOCKET", "XMLRPC"
    * @param nodeId  A unique std::string (typically the cluster node id we connect to).<br />
    *   This is used for extended env-variable support, e.g. for a given
    *    <code>nodeId="heron"</ code>
    *   the command line argument (or xmlBlaster.property entry)
    *    <code>-retries[heron] 20</code>
    *   is precedence over
    *    <code>-retries 10</code>
    */
   Address(org::xmlBlaster::util::Global& global, const std::string& type="", const std::string& nodeId="");

   /**
    * copy constructor
    */
   Address(const AddressBase& addr);

   /**
    * Assignment operator. Note that both assignment operator and copy
    * constructor are taking an AddressBase as argument. This because Address
    * is only a decorator to AddressBase, i.e. it does not provide any new
    * member, it only has different accessors.
    */
   Address& operator =(const AddressBase& addr);

   void setMaxEntries(long maxEntries);

   long getMaxEntries() const;

   /** For logging only */
   std::string getSettings();

   /** @return The literal address as given by getRawAddress() */
   std::string toString();

   /**
    * Get a usage std::string for the connection parameters
    */
   std::string usage();

};

}}}}} // namespace

#endif

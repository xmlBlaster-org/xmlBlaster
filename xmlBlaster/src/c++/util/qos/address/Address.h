/*------------------------------------------------------------------------------
Name:      Address.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Holding address string and protocol string
Version:   $Id: Address.h,v 1.1 2002/12/20 19:43:27 laghi Exp $
------------------------------------------------------------------------------*/

/**
 * Helper class holding address string, protocol string and client side connection properties.
 * <p />
 * <pre>
 * &lt;address type='XML-RPC' sessionId='4e56890ghdFzj0'
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
    * @param type    The protocol type, e.g. "IOR", "EMAIL", "XML-RPC"
    * @param nodeId  A unique string (typically the cluster node id we connect to).<br />
    *   This is used for extended env-variable support, e.g. for a given
    *    <code>nodeId="heron"</ code>
    *   the command line argument (or xmlBlaster.property entry)
    *    <code>-retries[heron] 20</code>
    *   is precedence over
    *    <code>-retries 10</code>
    */
   Address(Global& global, const string& type="", const string& nodeId="");

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

   void setMaxMsg(long maxMsg);

   long getMaxMsg() const;

   /** How often to retry if connection fails: defaults to -1 (retry forever) */
   int getDefaultRetries();

   /** Delay between connection retries in milliseconds (5000 is a good value): defaults to 0, a value bigger 0 switches fails save mode on */
   long getDefaultDelay();

   // /* Delay between connection retries in milliseconds: defaults to 5000 (5 sec), a value of 0 switches fails save mode off */
   // public long getDefaultDelay() { return 5 * 1000L; };

   /** Ping interval: pinging every given milliseconds, defaults to 10 seconds */
   long getDefaultPingInterval();

   /** For logging only */
   string getSettings();

   /** @return The literal address as given by getAddress() */
   string toString();

   /**
    * Get a usage string for the connection parameters
    */
   string usage();

};

}}}}} // namespace

#endif

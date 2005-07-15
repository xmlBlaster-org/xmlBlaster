/*------------------------------------------------------------------------------
Name:      ClientQueueProperty.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Holding callback queue properties
------------------------------------------------------------------------------*/

/**
 * Helper class holding callback queue properties.
 * <p />
 * See org::xmlBlaster::util::qos::ConnectQos for XML sysntax.
 * @see org.xmlBlaster.client.qos.ConnectQos
 */

#ifndef _UTIL_QUEUE_QUEUEPROPERTY_H
#define _UTIL_QUEUE_QUEUEPROPERTY_H


#include <util/xmlBlasterDef.h>
#include <util/Constants.h>
#include <util/qos/storage/QueuePropertyBase.h>
#include <util/qos/address/Address.h>
#include <string>

namespace org { namespace xmlBlaster { namespace util { namespace qos { namespace storage {

class Dll_Export ClientQueueProperty : public QueuePropertyBase
{
protected:

public:
   /**
    * @param nodeId    If not null, the command line properties will look for prop[nodeId] as well,
    * e.g. -queue/maxEntries and -queue/maxEntries[heron] will be searched
    */
   ClientQueueProperty(org::xmlBlaster::util::Global& global, const std::string& nodeId);

   ClientQueueProperty(const QueuePropertyBase& prop);

   ClientQueueProperty& operator =(const QueuePropertyBase& prop);

   /**
    * Show some important settings for logging
    */
   std::string getSettings();

   /**
    */
   void setAddress(const org::xmlBlaster::util::qos::address::AddressBaseRef& address);

   /**
    * clears up all addresses and allocates new ones.
    */
   void setAddresses(const AddressVector& addresses);

   /**
    * @return null if none available
    */
   org::xmlBlaster::util::qos::address::AddressBaseRef getCurrentAddress();

   /**
    * Get a usage std::string for the connection parameters
    */
   static std::string usage();

};

}}}}} // namespace

#endif

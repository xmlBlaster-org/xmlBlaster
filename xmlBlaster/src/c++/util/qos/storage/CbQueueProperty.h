/*------------------------------------------------------------------------------
Name:      CbQueueProperty.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Holding callback queue properties
Version:   $Id$
------------------------------------------------------------------------------*/

/**
 * Helper class holding callback queue properties.
 * <p />
 * See org::xmlBlaster::util::qos::ConnectQos for XML sysntax.
 * @see org.xmlBlaster.client.qos.ConnectQos
 */

#ifndef _UTIL_QUEUE_CBQUEUEPROPERTY_H
#define _UTIL_QUEUE_CBQUEUEPROPERTY_H

#include <util/xmlBlasterDef.h>
#include <util/Constants.h>
#include <util/qos/storage/QueuePropertyBase.h>
#include <util/qos/address/CallbackAddress.h>

#include <string>




namespace org { namespace xmlBlaster { namespace util { namespace qos { namespace storage {

class Dll_Export CbQueueProperty : public QueuePropertyBase
{
protected:

   /**
    * @param relating  To what is this queue related: Constants.RELATING_CALLBACK | Constants.RELATING_SUBJECT
    * @param nodeId    If not null, the command line properties will look for prop[nodeId] as well,
    * e.g. -queue/maxEntries and -queue/maxEntries[heron] will be searched
    */
public:
   CbQueueProperty(org::xmlBlaster::util::Global& global, const std::string& relating, const std::string& nodeId);

   CbQueueProperty(const QueuePropertyBase& prop);

   CbQueueProperty& operator =(const QueuePropertyBase& prop);

   /**
    * Show some important settings for logging
    */
   std::string getSettings();

   /**
    * This method converts a std::string to lowercase. Note that the input std::string is
    * modified and a reference to it is returned.
    */
   static std::string& toLowerCase(std::string& ref);

   /**
    * @param relating    To what is this queue related: Constants.RELATING_CALLBACK | Constants.RELATING_SUBJECT
    */
   void setRelating(const std::string& relating);

   bool isSubjectRelated();

   bool isSessionRelated();

   bool onOverflowDeadMessage();

   /**
    * Currently only one address is allowed, failover addresses will be implemented in a future version
    */
   void setCallbackAddress(const org::xmlBlaster::util::qos::address::AddressBaseRef& address);

   /**
    */
   void setCallbackAddresses(const AddressVector& addresses);

   /**
    * @return array with size 0 if none available
    */
   AddressVector getCallbackAddresses();

   /**
    * @return null if none available
    */
   org::xmlBlaster::util::qos::address::AddressBaseRef getCurrentCallbackAddress();

   /**
    * Get a usage std::string for the connection parameters
    */
   static std::string usage();
};

}}}}} // namespace

#endif

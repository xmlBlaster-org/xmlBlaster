/*------------------------------------------------------------------------------
Name:      CbQueueProperty.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Holding callback queue properties
Version:   $Id: CbQueueProperty.h,v 1.4 2003/01/16 18:03:56 laghi Exp $
------------------------------------------------------------------------------*/

/**
 * Helper class holding callback queue properties.
 * <p />
 * See ConnectQos for XML sysntax.
 * @see org.xmlBlaster.client.qos.ConnectQos
 */

#ifndef _UTIL_QUEUE_CBQUEUEPROPERTY_H
#define _UTIL_QUEUE_CBQUEUEPROPERTY_H

#include <util/xmlBlasterDef.h>
#include <util/Log.h>
#include <util/Constants.h>
#include <util/qos/storage/QueuePropertyBase.h>
#include <util/qos/address/CallbackAddress.h>

#include <string>
#include <vector>

using namespace org::xmlBlaster::util;
using namespace org::xmlBlaster::util::qos::address;

namespace org { namespace xmlBlaster { namespace util { namespace qos { namespace storage {

class Dll_Export CbQueueProperty : public QueuePropertyBase
{
protected:

   /**
    * Configure property settings
    */
   inline void initialize();

   /**
    * @param relating  To what is this queue related: Constants.RELATING_SESSION | Constants.RELATING_SUBJECT
    * @param nodeId    If not null, the command line properties will look for prop[nodeId] as well,
    * e.g. -queue.maxMsg and -queue.maxMsg[heron] will be searched
    */
public:
   CbQueueProperty(Global& global, const string& relating, const string& nodeId);

   CbQueueProperty(const QueuePropertyBase& prop);

   CbQueueProperty& operator =(const QueuePropertyBase& prop);

   /**
    * Show some important settings for logging
    */
   string getSettings();

   /**
    * This method converts a string to lowercase. Note that the input string is
    * modified and a reference to it is returned.
    */
   static string& toLowerCase(string& ref);

   /**
    * @param relating    To what is this queue related: Constants.RELATING_SESSION | Constants.RELATING_SUBJECT
    */
   void setRelating(const string& relating);

   bool isSubjectRelated();

   bool isSessionRelated();

   bool onOverflowDeadMessage();

   /**
    * Currently only one address is allowed, failover addresses will be implemented in a future version
    */
   void setCallbackAddress(const AddressBase& address);

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
   AddressBase getCurrentCallbackAddress();

   /**
    * Get a usage string for the connection parameters
    */
   string usage();
};

}}}}} // namespace

#endif

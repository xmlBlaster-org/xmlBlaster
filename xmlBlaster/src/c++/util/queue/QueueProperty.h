/*------------------------------------------------------------------------------
Name:      QueueProperty.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Holding callback queue properties
------------------------------------------------------------------------------*/

/**
 * Helper class holding callback queue properties.
 * <p />
 * See ConnectQos for XML sysntax.
 * @see org.xmlBlaster.util.ConnectQos
 */

#ifndef _UTIL_QUEUE_QUEUEPROPERTY_H
#define _UTIL_QUEUE_QUEUEPROPERTY_H


#include <util/xmlBlasterDef.h>
#include <util/Log.h>
#include <util/Constants.h>
#include <util/queue/QueuePropertyBase.h>
#include <util/cfg/Address.h>

#include <string>
#include <vector>

using namespace org::xmlBlaster::util;
using namespace org::xmlBlaster::util::cfg;

namespace org { namespace xmlBlaster { namespace util { namespace queue {

class Dll_Export QueueProperty : public QueuePropertyBase
{
protected:

   /**
    * Configure property settings
    */
   inline void initialize();

public:
   /**
    * @param nodeId    If not null, the command line properties will look for prop[nodeId] as well,
    * e.g. -queue.maxMsg and -queue.maxMsg[heron] will be searched
    */
   QueueProperty(Global& global, const string& nodeId);

   QueueProperty(const QueuePropertyBase& prop);

   QueueProperty& operator =(const QueuePropertyBase& prop);

   /**
    * Show some important settings for logging
    */
   string getSettings();

   /**
    */
   void setAddress(const AddressBase& address);

   /**
    * clears up all addresses and allocates new ones.
    */
   void setAddresses(const AddressVector& addresses);

   /**
    * @return null if none available
    */
   AddressBase getCurrentAddress();

   /**
    * Get a usage string for the connection parameters
    */
   string usage();

};

}}}} // namespace

#endif

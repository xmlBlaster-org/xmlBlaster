/*------------------------------------------------------------------------------
Name:      org::xmlBlaster::util::qos::storage::MsgUnitStoreProperty.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

/**
 * Helper class holding properties of the MsgUnit storage. 
 * <p />
 * See org::xmlBlaster::util::qos::ConnectQos for XML sysntax.
 * @see org.xmlBlaster.client.qos.ConnectQos
 */

#ifndef _UTIL_MSGUNITSTORE_PROPERTY_H
#define _UTIL_MSGUNITSTORE_PROPERTY_H

#include <util/xmlBlasterDef.h>
#include <util/qos/storage/QueuePropertyBase.h>

// 


namespace org { namespace xmlBlaster { namespace util { namespace qos { namespace storage {

class Dll_Export MsgUnitStoreProperty : public QueuePropertyBase
{
public:

   /**
    * @param nodeId_ If not "" (empty), the command line properties will look
    *                for prop[nodeId] as well,
    * e.g. -persistence/maxEntries and -persistence/maxEntries[heron] will be searched
    */
   MsgUnitStoreProperty(org::xmlBlaster::util::Global& global, const std::string& nodeId);

   MsgUnitStoreProperty(const QueuePropertyBase& prop);

   MsgUnitStoreProperty& operator =(const QueuePropertyBase& prop);

   bool onOverflowDeadMessage();

   /**
    * Get a usage std::string for the connection parameters
    */
   static std::string usage();

   std::string getRootTagName() const;
};

}}}}}

#endif


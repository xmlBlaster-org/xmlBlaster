/*------------------------------------------------------------------------------
Name:      MsgUnitStoreProperty.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

/**
 * Helper class holding properties of the MsgUnit storage. 
 * <p />
 * See ConnectQos for XML sysntax.
 * @see org.xmlBlaster.client.qos.ConnectQos
 */

#ifndef _UTIL_MSGUNITSTORE_PROPERTY_H
#define _UTIL_MSGUNITSTORE_PROPERTY_H

#include <util/xmlBlasterDef.h>
#include <util/qos/storage/QueuePropertyBase.h>

// using namespace org::xmlBlaster::util;
using namespace std;

namespace org { namespace xmlBlaster { namespace util { namespace qos { namespace storage {

class Dll_Export MsgUnitStoreProperty : public QueuePropertyBase
{
public:

   /**
    * @param nodeId_ If not "" (empty), the command line properties will look
    *                for prop[nodeId] as well,
    * e.g. -queue.maxMsg and -queue.maxMsg[heron] will be searched
    */
   MsgUnitStoreProperty(Global& global, const string& nodeId);

   MsgUnitStoreProperty(const QueuePropertyBase& prop);

   MsgUnitStoreProperty& operator =(const QueuePropertyBase& prop);

    /**
    * Configure property settings
    */
   void initialize();

   bool onOverflowDeadMessage();

   /**
    * Get a usage string for the connection parameters
    */
   string usage();

   string getRootTagName() const;
};

}}}}}

#endif


/*------------------------------------------------------------------------------
Name:      TopicCacheProperty.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

/**
 * Helper class holding properties of the MsgUnit storage. 
 * <p />
 * See ConnectQos for XML sysntax.
 * @see org.xmlBlaster.util.ConnectQos
 */

#ifndef _UTIL_TOPICCACHEPROPERTY_H
#define _UTIL_TOPICCACHEPROPERTY_H

#include <util/xmlBlasterDef.h>
#include <util/qos/storage/QueuePropertyBase.h>

using namespace org::xmlBlaster::util;
using namespace std;

namespace org { namespace xmlBlaster { namespace util { namespace qos { namespace storage {

class Dll_Export TopicCacheProperty : public QueuePropertyBase
{
public:

   /**
    * @param nodeId_ If not "" (empty), the command line properties will look
    *                for prop[nodeId] as well,
    * e.g. -queue.maxMsg and -queue.maxMsg[heron] will be searched
    */
   TopicCacheProperty(Global& global, const string& nodeId);

   /**
    * Configure property settings
    */
   void initialize();

   bool onOverflowDeadMessage();

   /**
    * Get a usage string for the connection parameters
    */
   string usage();
};

}}}}}

#endif


/*------------------------------------------------------------------------------
Name:      HistoryQueueProperty.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

/**
 * Helper class holding history queue properties.
 * <p />
 * See ConnectQos for XML sysntax.
 * @see org.xmlBlaster.client.qos.ConnectQos
 */

#ifndef _UTIL_HISTORYQUEUEPROPERTY_H
#define _UTIL_HISTORYQUEUEPROPERTY_H

#include <util/xmlBlasterDef.h>
#include <util/qos/storage/QueuePropertyBase.h>

using namespace org::xmlBlaster::util;
using namespace std;

namespace org { namespace xmlBlaster { namespace util { namespace qos { namespace storage {

class Dll_Export HistoryQueueProperty : public QueuePropertyBase
{
public:

   /**
    * @param nodeId    If not null, the command line properties will look for prop[nodeId] as well,
    * e.g. -queue.maxMsg and -queue.maxMsg[heron] will be searched
    */
   HistoryQueueProperty(Global& global, const string& nodeId);

   HistoryQueueProperty(const QueuePropertyBase& prop);

   HistoryQueueProperty& operator =(const QueuePropertyBase& prop);

   /**
    * Show some important settings for logging
    */
   string getSettings();

   /**
    * Configure property settings
    */
   void initialize();

   bool onOverflowDeadMessage();
};

}}}}} // namespace

#endif

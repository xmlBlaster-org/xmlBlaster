/*------------------------------------------------------------------------------
Name:      org::xmlBlaster::util::qos::storage::HistoryQueueProperty.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

/**
 * Helper class holding history queue properties.
 * <p />
 * See org::xmlBlaster::util::qos::ConnectQos for XML sysntax.
 * @see org.xmlBlaster.client.qos.ConnectQos
 */

#ifndef _UTIL_HISTORYQUEUEPROPERTY_H
#define _UTIL_HISTORYQUEUEPROPERTY_H

#include <util/xmlBlasterDef.h>
#include <util/qos/storage/QueuePropertyBase.h>

namespace org { namespace xmlBlaster { namespace util { namespace qos { namespace storage {

class Dll_Export HistoryQueueProperty : public QueuePropertyBase
{
public:

   /**
    * @param nodeId    If not "", the command line properties will look for prop[nodeId] as well,
    * e.g. -queue/history/maxEntries and -queue/history/maxEntries[heron] will be searched
    */
   HistoryQueueProperty(org::xmlBlaster::util::Global& global, const std::string& nodeId);

   HistoryQueueProperty(const QueuePropertyBase& prop);

   HistoryQueueProperty& operator =(const QueuePropertyBase& prop);

   /**
    * Show some important settings for logging
    */
   std::string getSettings();

   bool onOverflowDeadMessage();
};

}}}}} // namespace

#endif

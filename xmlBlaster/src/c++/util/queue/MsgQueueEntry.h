/*------------------------------------------------------------------------------
Name:      MsgQueueEntry.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

#ifndef _UTIL_QUEUE_MSGQUEUEENRY_H
#define _UTIL_QUEUE_MSGQUEUEENRY_H

#include <util/xmlBlasterDef.h>
#include <util/Timestamp.h>
#include <util/MessageUnit.h>
#include <util/qos/ConnectQos.h>
#include <stddef.h>

using org::xmlBlaster::util::qos::ConnectQos;
using org::xmlBlaster::util::MessageUnit;

/**
 * Class embedding messages or information to be stored on the client queues
 * Note that all content is copied when passed to the constructors.
 * This way this queue entry is the owner of the content (and therefore will
 * delete it when its destructor is called).
 *
 * @author <a href='mailto:laghi@swissinfo.org'>Michele Laghi</a>
 */
namespace org { namespace xmlBlaster { namespace util { namespace queue {

class MsgQueueEntry
{
private:
   const string ME;
   int          priority_;
   bool         durable_;
   Timestamp    uniqueId_;
   void*        embeddedObject_;
   string       embeddedType_;
   string       logId_;
   long         sizeInBytes_;

   // specific for c++ clients:
   MessageUnit* msgUnit_;
   ConnectQos*  connectQos_;

public:

    /**
     * Constructor suited for operations like publishes
     */
    MsgQueueEntry(const MessageUnit& msgUnit, const string& type="publish", int priority=5, bool durable=false);

    /**
     * Constructor suited for operations like connect
     */
    MsgQueueEntry(const ConnectQos& connectQos, const string& type="connect", int priority=9, bool durable=false);

    ~MsgQueueEntry();

    inline void copy(const MsgQueueEntry& entry)
    {
       if (connectQos_ != NULL) delete connectQos_;
       if (entry.connectQos_ != NULL)
          connectQos_ = new ConnectQos(*entry.connectQos_);

       if (msgUnit_ != NULL) delete msgUnit_;
       if (entry.msgUnit_ != NULL)
          msgUnit_ = new MessageUnit(*entry.msgUnit_);

       uniqueId_     = entry.uniqueId_;
       embeddedType_ = entry.embeddedType_;
       priority_     = entry.priority_;
       durable_      = entry.durable_;
       logId_        = logId_;
    }

    /**
     * copy constructor
     */
    MsgQueueEntry(const MsgQueueEntry& entry);

    MsgQueueEntry& operator =(const MsgQueueEntry& entry);

    inline bool operator == (const MsgQueueEntry& entry)
    {
       if (priority_ != entry.priority_) return false;
       return (uniqueId_ == entry.uniqueId_);
    }

    /**
     * returns true if the current object is lower than the entry passed as
     * an argument.
     */
    inline bool operator < (const MsgQueueEntry& entry)
    {
       if (priority_ < entry.priority_) return true;
       if (priority_ > entry.priority_) return false;
       return (uniqueId_ > entry.uniqueId_);
    }

   /**
    * Allows to query the priority of this entry.
    * This is the highest order precedence in the sorted queue
    * @return The priority
    */
   int getPriority() const;

   /**
    * Returns true if the entry is durable (persistent), false otherwise.
    */
   bool isDurable() const;

   /**
    * This is the second order criteria in the queue
    * @return The unique Id of this entry.
    */
   long getUniqueId() const;

   /**
    * gets the content of this queue entry (the embedded object). In
    * persistent queues this is the data which is stored as a blob.
    */
   void* getEmbeddedObject();

   /**
    * gets the type of the object embedded in this entry.
    * @return String the identifier which tells the I_EntryFactory how to
    *         deserialize this entry.
    */
   string getEmbeddedType() const;

   /**
    * Return a human readable identifier for logging output.
    * <p>
    * See the derived class for a syntax description.
    * </p>
    */
   string getLogId();

   /**
    * returns the size in bytes of this entry.
    */
   size_t getSizeInBytes() const;
};

}}}} // namespace

#endif


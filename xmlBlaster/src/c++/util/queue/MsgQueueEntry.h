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
#include <client/qos/PublishQos.h>
#include <client/qos/PublishReturnQos.h>
#include <client/protocol/I_XmlBlasterConnection.h>
#include <util/ReferenceCounterBase.h>
#include <util/qos/StatusQosData.h>
#include <util/qos/QueryQosData.h>
#include <util/key/QueryKeyData.h>
#include <util/Log.h>
#include <stddef.h>

using namespace org::xmlBlaster::util;
using namespace org::xmlBlaster::util::qos;
using namespace org::xmlBlaster::util::key;
using namespace org::xmlBlaster::client::qos;
using namespace org::xmlBlaster::client::protocol;

/**
 * Class embedding messages or information to be stored on the client queues
 * Note that all content is copied when passed to the constructors.
 * This way this queue entry is the owner of the content (and therefore will
 * delete it when its destructor is called).
 *
 * @author <a href='mailto:laghi@swissinfo.org'>Michele Laghi</a>
 */
namespace org { namespace xmlBlaster { namespace util { namespace queue {

class Dll_Export MsgQueueEntry : public ReferenceCounterBase
{
protected:
   string        ME;
   Global&       global_;
   Log&          log_;
   int           priority_;
   bool          persistent_;
   Timestamp     uniqueId_;
   string        embeddedType_;
   string        logId_;
   MessageUnit*  msgUnit_;
   ConnectQos*   connectQos_;
   QueryQosData* queryQosData_;
   QueryKeyData* queryKeyData_;

   // specific return values
   ConnectReturnQos* connectReturnQos_;
   PublishReturnQos* publishReturnQos_;
   StatusQosData*    statusQosData_;

public:

    /**
     * Constructor suited for operations like publishes
     */
    MsgQueueEntry(Global& global, const MessageUnit& msgUnit, const string& type="publish", int priority=5, bool persistent=false);

    /**
     * Constructor suited for operations like connect
     */
    MsgQueueEntry(Global& global, const ConnectQos& connectQos, const string& type="connect", int priority=9, bool persistent=false);


    /**
     * Constructor suited for operations like subscribe and unSubscribe
     */
    MsgQueueEntry(Global& global, const QueryKeyData& queryKeyData, const QueryQosData& queryQosData, const string& type="subscribe", int priority=9, bool persistent=false);


    virtual ~MsgQueueEntry();

    inline void copy(const MsgQueueEntry& entry)
    {
       if (connectQos_ != NULL) {
          delete connectQos_;
          connectQos_ = NULL;
       }
       if (entry.connectQos_ != NULL) connectQos_ = new ConnectQos(*entry.connectQos_);

       if (msgUnit_ != NULL) {
          delete msgUnit_;
          msgUnit_ = NULL;
       }
       if (entry.msgUnit_ != NULL) msgUnit_ = new MessageUnit(*entry.msgUnit_);

       if (connectReturnQos_ != NULL) {
          delete connectReturnQos_;
          connectReturnQos_ = NULL; 
       }
       if (entry.connectReturnQos_ != NULL) 
          connectReturnQos_ = new ConnectReturnQos(*entry.connectReturnQos_);

       if (publishReturnQos_ != NULL) {
          delete publishReturnQos_;
          publishReturnQos_ = NULL; 
       }
       if (entry.publishReturnQos_ != NULL) 
          publishReturnQos_ = new PublishReturnQos(*entry.publishReturnQos_);

       if (queryQosData_ != NULL) {
          delete queryQosData_;
          queryQosData_ = NULL; 
       }
       if (entry.queryQosData_ != NULL)
          queryQosData_ = new QueryQosData(*entry.queryQosData_);

       if (queryKeyData_ != NULL) {
          delete queryKeyData_;
          queryKeyData_ = NULL; 
       }
       if (entry.queryKeyData_ != NULL) 
          queryKeyData_ = new QueryKeyData(*entry.queryKeyData_);

       if (statusQosData_ != NULL) {
          delete statusQosData_;
          statusQosData_ = NULL; 
       }
       if (entry.statusQosData_ != NULL) 
          statusQosData_ = new StatusQosData(*entry.statusQosData_);

       uniqueId_     = entry.uniqueId_;
       embeddedType_ = entry.embeddedType_;
       priority_     = entry.priority_;
       persistent_      = entry.persistent_;
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
    * Returns true if the entry is persistent (persistent), false otherwise.
    */
   bool isPersistent() const;

   /**
    * This is the second order criteria in the queue
    * @return The unique Id of this entry.
    */
   long getUniqueId() const;

   /**
    * gets the content of this queue entry (the embedded object). In
    * persistent queues this is the data which is stored as a blob.
    */
   virtual void* getEmbeddedObject() = 0;

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


   // this should actually be in another interface but since it is an only method we put it here.
   virtual MsgQueueEntry& send(I_XmlBlasterConnection& connection); // = 0;

   virtual string toXml(const string& indent=""); // const = 0;

};

}}}} // namespace

#endif


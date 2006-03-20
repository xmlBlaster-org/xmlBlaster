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
#include <util/ReferenceCounterBase.h>
#include <util/qos/StatusQosData.h>
#include <util/qos/QueryQosData.h>
#include <util/key/QueryKeyData.h>
//#include <util/msgUtil.h> // from xmlBlaster C library
//#include <socket/xmlBlasterSocket.h> // from xmlBlaster C library ::encodeMsgUnit(&msgUnit, debug);

// circular dependency I_ConnectionsHandler -> Queue -> MsgQueueEntry
#ifndef _UTIL_DISPATCH_ICONNECTIONSHANDLER_H
namespace org { namespace xmlBlaster { namespace util { namespace dispatch {
class I_ConnectionsHandler;
}}}}
#endif



/**
 * Class embedding messages or information to be stored on the client queues
 * Note that all content is copied when passed to the constructors.
 * This way this queue entry is the owner of the content (and therefore will
 * delete it when its destructor is called).
 *
 * @author <a href='mailto:laghi@swissinfo.org'>Michele Laghi</a>
 */
namespace org { namespace xmlBlaster { namespace util { namespace queue {

/**
 * Holds arbitrary raw data and its length
 */
typedef struct {
   size_t dataLen; // size_t is the unsigned integer size result of a sizeof operator. Change to uint32_t ?
   char *data;
} BlobHolder;

class Dll_Export MsgQueueEntry : public ReferenceCounterBase
{
protected:
   std::string ME;
   mutable org::xmlBlaster::util::Global& global_;
   org::xmlBlaster::util::I_Log& log_;
   int priority_;
   bool persistent_;
   org::xmlBlaster::util::Timestamp uniqueId_;
   std::string embeddedType_;
   std::string logId_;
   org::xmlBlaster::util::MessageUnit* msgUnit_;
   /* TODO: Change that connectQos, queryQos all derive from QosData and are transported inside msgUnit */
   org::xmlBlaster::util::qos::ConnectQosRef connectQos_;

   /**
    * Specific return value for connect(). 
    */
   mutable org::xmlBlaster::util::qos::ConnectReturnQosRef connectReturnQos_;
   /**
    * Specific return value for publish(). 
    */
   mutable org::xmlBlaster::client::qos::PublishReturnQos* publishReturnQos_;
   /**
    * Return status for subscribe() etc. 
    */
   mutable org::xmlBlaster::util::qos::StatusQosData* statusQosData_;

   /**
    * Holds the serialized information which is returned by getEmbeddedObject(),
    * encoded according to embeddedType
    */
   mutable BlobHolder blobHolder_;

public:

    /**
     * Constructor suited for operations like publishes
     * @param msgUnit We take a clone of it
     * @param embeddedType Describes the type of serialization of the embedded object to be able
     *        to restore it later, something like "MSG_RAW|publish"
     */
    MsgQueueEntry(org::xmlBlaster::util::Global& global,
                  const org::xmlBlaster::util::MessageUnit& msgUnit,
                  const std::string& embeddedType,
                  int priority,
                  bool persistent,
                  org::xmlBlaster::util::Timestamp uniqueId = TimestampFactory::getInstance().getTimestamp());

    /**
     * Constructor suited for operations like connect
     * @param connectQos We take a clone of it
     */
    MsgQueueEntry(org::xmlBlaster::util::Global& global,
                  const org::xmlBlaster::util::qos::ConnectQosRef& connectQos,
                  const std::string& embeddedType,
                  int priority,
                  bool persistent,
                  org::xmlBlaster::util::Timestamp uniqueId = TimestampFactory::getInstance().getTimestamp());


    /**
     * Constructor suited for operations like subscribe and unSubscribe
     * @param queryKeyData We take a clone of it
     * @param queryQosData We take a clone of it
     */
    MsgQueueEntry(org::xmlBlaster::util::Global& global,
                  const org::xmlBlaster::util::key::QueryKeyData& queryKeyData,
                  const org::xmlBlaster::util::qos::QueryQosData& queryQosData,
                  const std::string& embeddedType,
                  int priority,
                  bool persistent,
                  org::xmlBlaster::util::Timestamp uniqueId = TimestampFactory::getInstance().getTimestamp());


    virtual ~MsgQueueEntry();

    Global& getGlobal() const { return global_; }

    void copy(const MsgQueueEntry& entry);

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
     * Create a new entry of myself. 
     * @return The cloned entry, is is allocated with new and it is your responsibility to delete it
     */
    virtual MsgQueueEntry *getClone() const = 0;

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
    * Set the sender name into the QoS. 
    */
   void setSender(org::xmlBlaster::util::SessionNameRef sender);

   /**
    * This is the second order criteria in the queue
    * @return The unique Id of this entry.
    */
   org::xmlBlaster::util::Timestamp getUniqueId() const;

   /**
    * The serialized data with MSG_RAW (identical to the SOCKET protocol serialization). 
    * @return The content of this queue entry (the embedded object). In
    * persistent queues this is the data which is stored as a blob.
    * Returns a BlobHolder instance containing the serialized message (identical serialization as in SOCKET protocol)
    * May return 0.
    */
   virtual const void* getEmbeddedObject() const;

   /**
    * gets the embeddedType of the object embedded in this entry.
    * @return String in namespace MethodName, the identifier which tells the I_EntryFactory how to
    *         deserialize this entry.
    */
   virtual std::string getEmbeddedType() const;

   /**
    * Return a human readable identifier for logging output.
    * <p>
    * See the derived class for a syntax description.
    * </p>
    */
   std::string getLogId() const;

   /**
    * returns the size in bytes of this entry.
    */
   virtual size_t getSizeInBytes() const;

   /**
    * Access the MessageUnit in case it is a Publish.
    * @return NULL if not publish
    */
   org::xmlBlaster::util::MessageUnit& getMsgUnit() const;

   //org::xmlBlaster::util::qos::QueryQosData& getQueryQosData() const;
   //org::xmlBlaster::util::key::QueryKeyData& getQueryKeyData() const;



   // this should actually be in another interface but since it is an only method we put it here.
   virtual const MsgQueueEntry& send(org::xmlBlaster::util::dispatch::I_ConnectionsHandler&) const; // = 0;

   virtual std::string toXml(const std::string& indent="") const; // = 0;

};

}}}} // namespace

#endif


/*----------------------------------------------------------------------------
Name:      I_Queue.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Author:    "Marcel Ruff" <xmlBlaster@marcelruff.info>
Comment:   Implements a persistent queue with priority and time stamp sorting
Note:      The gcc and icc (>=8) both define __GNUC__
-----------------------------------------------------------------------------*/
#ifndef I_QUEUE_I_Queue_h
#define I_QUEUE_I_Queue_h

#include "util/helper.h" /* BlobHolder. basicDefs.h: for int64_t (C99), Dll_Export, bool etc. */

#ifdef __cplusplus
#ifndef XMLBLASTER_C_COMPILE_AS_CPP /* 'g++ -DXMLBLASTER_C_COMPILE_AS_CPP ...' allows to compile the lib as C++ code */
extern "C" {
#endif
#endif

/*const int QUEUE_ENTRY_EMBEDDEDTYPE_LEN = 26;*/
#define QUEUE_ENTRY_EMBEDDEDTYPE_LEN 28

#define QUEUE_PREFIX_MAX 20
#define QUEUE_DBNAME_MAX 256
#define QUEUE_ID_MAX 256
typedef struct {
   char dbName[QUEUE_DBNAME_MAX];      /** "xmlBlaster.db" */
   char nodeId[QUEUE_ID_MAX];          /** "/node/heron/client/joe" */
   char queueName[QUEUE_ID_MAX];       /** "connection_client_joe" */
   char tablePrefix[QUEUE_PREFIX_MAX]; /** "XB_" */
   int32_t maxNumOfEntries;            /** 10000 */
   int64_t maxNumOfBytes;              /** 10000000LL */
} QueueProperties;

/**
 * A stuct holding the necessary queue entry informations used by I_Queue. 
 */
typedef struct {
   int64_t uniqueId;        /** The unique key, used for sorting, usually a time stamp [nano sec]. Is assumed to be ascending over time. */
   int16_t priority;        /** The priority of the queue entry, has higher sorting order than than the time stamp */
   bool isPersistent;       /** Mark an entry to be persistent, needed for cache implementations, 'T' is true, 'F' is false. 'F' in persistent queue is a swapped transient entry */
   char embeddedType[QUEUE_ENTRY_EMBEDDEDTYPE_LEN]; /** A string describing this entry, for example the format of the blob. */
   BlobHolder embeddedBlob; /** blob.data is allocated with malloc, you need to free() it yourself, is compressed if marked as such */
} QueueEntry;

/**
 * Holds an array of Messages
 */
typedef struct QueueEntryStructArr {
   size_t len;
   QueueEntry *queueEntryArr;
} QueueEntryArr;

struct I_QueueStruct;
typedef struct I_QueueStruct I_Queue;

/** Declare function pointers to use in struct to simulate object oriented access */
typedef bool  ( * I_QueueInitialize)(I_Queue *queueP, const QueueProperties *queueProperties, ExceptionStruct *exception);
typedef void  ( * I_QueueShutdown)(I_Queue *queueP, ExceptionStruct *exception);
typedef const QueueProperties *( * I_QueueGetProperties)(I_Queue *queueP);
typedef void  ( * I_QueuePut)(I_Queue *queueP, QueueEntry *queueEntry, ExceptionStruct *exception);
typedef QueueEntryArr *( * I_QueuePeekWithSamePriority)(I_Queue *queueP, int32_t maxNumOfEntries, int64_t maxNumOfBytes, ExceptionStruct *exception);
typedef int32_t ( * I_QueueRandomRemove)(I_Queue *queueP, QueueEntryArr *queueEntryArr, ExceptionStruct *exception);
typedef bool  ( * I_QueueClear)(I_Queue *queueP, ExceptionStruct *exception);
typedef bool  ( * I_QueueEmpty)(I_Queue *queueP);
typedef int32_t ( * I_QueueNumOfEntries)(I_Queue *queueP);
typedef int32_t ( * I_QueueMaxNumOfEntries)(I_Queue *queueP);
typedef int64_t ( * I_QueueNumOfBytes)(I_Queue *queueP);
typedef int64_t ( * I_QueueMaxNumOfBytes)(I_Queue *queueP);

/**
 * Interface for a queue implementation. 
 * See SQLiteQueue.c for a DB based persistent queue implementation
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/client.c.queue.html">The client.c.queue requirement</a>
 */
struct I_QueueStruct {
  /* public: */
   void *userObject; /* A client can use this pointer to point to any client specific information */

   /**
    * Access the configuration properties (readonly). 
    */
   I_QueueGetProperties getProperties;

   /**
    * Shutdown the queue, no entries are destroyed. 
    */
   I_QueueShutdown shutdown;

   /**
    * puts a new entry into the queue. 
    * Note that this method takes the entry pointed to by the argument 
    * and puts a reference to it into the queue. This means that you can not destroy the entry before the
    * reference to it has been processed. 
    * @exception Check *exception.errorCode!=0 for errors:
    *            "resource.db.unavailable", "resource.overflow.queue.entries", "resource.overflow.queue.bytes"
    */
   I_QueuePut put;

   /**
    * Returns maximum the first num element in the queue of highest priority
    * but does not remove it from that queue (leaves it untouched).
    * @param queueP The 'this' pointer (similar to the hidden C++ 'this' pointer)
    * @param maxNumOfEntries Access num entries, if -1 access all entries currently found
    * @param maxNumOfBytes so many entries are returned as not to exceed the amount specified. If the first
    *        entry is bigger than this amount, it is returned anyway. -1 is unlimited.
    * @param exception *exception.errorCode!=0 if the underlying implementation gets an exception
    * @return list with QueueEntry, the least elements with respect to the given time ordering
    *         or QueueEntryArr.len==0.
    *         Returned pointer is only NULL on exception
    */
   I_QueuePeekWithSamePriority peekWithSamePriority;

   /**
    * Removes the given entries from persistence. 
    * @return The number of removed entries
    */
   I_QueueRandomRemove randomRemove;

   /**
    * Clears (removes all entries) this queue
    * @return true on success, if false *exception.errorCode is not 0
    */
   I_QueueClear clear;

   /**
    * Access the current number of entries. 
    * @return The number of entries in the queue, returns -1 on error
    */                                  
   I_QueueNumOfEntries getNumOfEntries;

   /**
    * Access the configured maximum number of elements for this queue. 
    * @return The maximum number of elements in the queue, returns -1 when passing a NULL pointer
    */
   I_QueueMaxNumOfEntries getMaxNumOfEntries;

   /**
    * Returns the amount of bytes currently in the queue
    * If the implementation of this interface is not able to return the correct
    * number of entries (for example if the implementation must make a remote
    * call to a DB which is temporarly not available) it will return -1.
    * @return The amount of bytes currently in the queue, returns -1 on error
    */
   I_QueueNumOfBytes getNumOfBytes;

   /**
    * Access the configured capacity (maximum bytes) for this queue
    * @return The maximum capacity for the queue in bytes, returns -1 when passing a NULL pointer
    */
   I_QueueMaxNumOfBytes getMaxNumOfBytes;

   /**
    * returns true if the queue is empty, false otherwise
    */                                  
   I_QueueEmpty empty;

   /**
    * Set the logLevel to LOG_TRACE to get logging output
    */
   XMLBLASTER_LOG_LEVEL logLevel;

   /**
    * Assign your logging function pointer to receive logging output
    */
   XmlBlasterLogging log;

  /* private: */

   /**
    * For internal use only. 
    * @return false on error
    */
   I_QueueInitialize initialize;
   bool isInitialized;  /** Hold current state of I_QueueStruct */
   void *privateObject; /** Usually holds a pointer on the internal data structure (like a DB handle or a hashtable) */
};

/**
 * Get an instance of a persistent queue and initialize it. 
 * NOTE: Every call creates a new and independent instance which shall
 * be destroyed by a call to freeQueue() when you are done
 * @param queueProperties
 *        dbName The database name, for SQLite it is the file name on HD, "xmlBlasterClient.db"
 *        nodeId The name space of this queue, "clientJoe1081594557415"
 *        queueName The name of the queue, "connection_clientJoe"
 *        maxNumOfEntries The max. accepted entries, 10000000l
 *        maxNumOfBytes The max. accepted bytes, 1000000000ll
 * @param exception
 * @return queueP The 'this' pointer
 */
Dll_Export extern I_Queue *createQueue(const QueueProperties *queueProperties,
                                      XmlBlasterLogging logFp,
                                      XMLBLASTER_LOG_LEVEL logLevel,
                                      ExceptionStruct *exception);
/*Dll_Export extern I_Queue *createQueue(int argc, const char* const* argv, I_QueueLogging logFp);*/

/**
 * Free your instance after using the persistent queue. 
 */
Dll_Export extern void freeQueue(I_Queue *queueP);

extern Dll_Export void freeQueueEntryArr(QueueEntryArr *queueEntryArr);
extern Dll_Export void freeQueueEntryArrInternal(QueueEntryArr *queueEntryArr);
extern Dll_Export void freeQueueEntryData(QueueEntry *queueEntry);
extern Dll_Export void freeQueueEntry(QueueEntry *queueEntry);
extern Dll_Export char *queueEntryToXmlLimited(QueueEntry *queueEntry, int maxContentDumpLen);
extern Dll_Export char *queueEntryToXml(QueueEntry *queueEntry);

#ifdef __cplusplus
#ifndef XMLBLASTER_C_COMPILE_AS_CPP
}
#endif
#endif

#endif /* I_QUEUE_I_Queue_h */


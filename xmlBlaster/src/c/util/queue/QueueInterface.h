/*----------------------------------------------------------------------------
Name:      QueueInterface.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Author:    "Marcel Ruff" <xmlBlaster@marcelruff.info>
Comment:   Interface for transient or persistent queue implementations
           with priority and time stamp sorting
Note:      The gcc and icc (>=8) both define __GNUC__
See:       http://www.xmlblaster.org/xmlBlaster/doc/requirements/client.c.queue.html
See:       http://www.xmlblaster.org/xmlBlaster/doc/requirements/queue.html
-----------------------------------------------------------------------------*/
#ifndef I_QUEUE_QueueInterface_h
#define I_QUEUE_QueueInterface_h

#include "util/helper.h" /* BlobHolder. basicDefs.h: for int64_t (C99), Dll_Export, bool etc. */

#ifdef __cplusplus
#ifndef XMLBLASTER_C_COMPILE_AS_CPP /* 'g++ -DXMLBLASTER_C_COMPILE_AS_CPP ...' allows to compile the lib as C++ code */
extern "C" {
#endif
#endif

/*const int QUEUE_ENTRY_EMBEDDEDTYPE_LEN = 28; -> not supported with C90 to be used for array sizes */
#define QUEUE_ENTRY_EMBEDDEDTYPE_LEN 28

#define QUEUE_PREFIX_MAX 20
#define QUEUE_DBNAME_MAX 256
#define QUEUE_ID_MAX 256
/**
 * The QueueProperty struct holds all configuration parameters of the queue to create. 
 * It is passed by the client code to create a queue.
 */
typedef struct {
   char dbName[QUEUE_DBNAME_MAX];      /**< The database name, for SQLite it is the file name on HD, "xmlBlaster.db" */
   char queueName[QUEUE_ID_MAX];       /**< The name of the queue, "connection_client_joe" */
   char tablePrefix[QUEUE_PREFIX_MAX]; /**< The table prefix to use, "XB_" */
   int32_t maxNumOfEntries;            /**< The max. accepted entries, 10000 */
   int64_t maxNumOfBytes;              /**< The max. capacity of the queue in bytes, 10000000LL */
   XmlBlasterLogging logFp;            /**< Your logging implementation or NULL if no logging callbacks are desired */
   XMLBLASTER_LOG_LEVEL logLevel;      /**< Set to LOG_TRACE to receive any logging */
   void *userObject;                   /**< A pointer of your choice, is passed back when calling logFp in queueP->userObject */
} QueueProperties;

/**
 * A struct holding the necessary queue entry informations used by I_Queue. 
 */
typedef struct {
   int64_t uniqueId;        /**< The unique key, used for sorting, usually a time stamp [nano sec]. Is assumed to be ascending over time. */
   int16_t priority;        /**< The priority of the queue entry, has higher sorting order than than the time stamp */
   bool isPersistent;       /**< Mark an entry to be persistent, needed for cache implementations, 'T' is true, 'F' is false. 'F' in persistent queue is a swapped transient entry */
   int64_t sizeInBytes;     /**< The size of this entry which is given by the client and used to sum up queue->numOfBytes, if 0 we use the size of the blob */
   char embeddedType[QUEUE_ENTRY_EMBEDDEDTYPE_LEN]; /**< A string describing this entry, for example the format of the blob. */
   BlobHolder embeddedBlob; /**< blob.data is allocated with malloc, you need to free() it yourself, is compressed if marked as such */
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
typedef void  ( * I_QueueShutdown)(I_Queue **queuePP, ExceptionStruct *exception);
typedef bool  ( * I_QueueDestroy)(I_Queue **queuePP, ExceptionStruct *exception);
typedef const QueueProperties *( * I_QueueGetProperties)(I_Queue *queueP);
typedef void  ( * I_QueuePut)(I_Queue *queueP, const QueueEntry *queueEntry, ExceptionStruct *exception);
typedef QueueEntryArr *( * I_QueuePeekWithSamePriority)(I_Queue *queueP, int32_t maxNumOfEntries, int64_t maxNumOfBytes, ExceptionStruct *exception);
typedef int32_t ( * I_QueueRandomRemove)(I_Queue *queueP, const QueueEntryArr *queueEntryArr, ExceptionStruct *exception);
typedef bool  ( * I_QueueClear)(I_Queue *queueP, ExceptionStruct *exception);
typedef bool  ( * I_QueueEmpty)(I_Queue *queueP);
typedef int32_t ( * I_QueueNumOfEntries)(I_Queue *queueP);
typedef int32_t ( * I_QueueMaxNumOfEntries)(I_Queue *queueP);
typedef int64_t ( * I_QueueNumOfBytes)(I_Queue *queueP);
typedef int64_t ( * I_QueueMaxNumOfBytes)(I_Queue *queueP);

/**
 * Interface for a queue implementation. 
 * See SQLiteQueue.c for a DB based persistent queue implementation.
 * The 'I_' stands for 'interface'.
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/client.c.queue.html">The client.c.queue requirement</a>
 */
struct I_QueueStruct {
  /* public: */
   void *userObject; /* A client can use this pointer to point to any client specific information */

   /**
    * Access the configuration properties (readonly). 
    * @param queueP The 'this' pointer (similar to the hidden C++ 'this' pointer)
    * @return The queue configuration
    */
   I_QueueGetProperties getProperties;

   /**
    * Puts a new entry into the queue. 
    * Note that this method takes the entry pointed to by the argument 
    * and puts a reference to it into the queue. This means that you can not destroy the entry before the
    * reference to it has been processed. 
    * @param queueP The 'this' pointer (similar to the hidden C++ 'this' pointer)
    * @param queueEntry The data of type #QueueEntry to put into the queue
    *        Please initialize it with <code>memset(&queueEntry, 0, sizeof(QueueEntry));</code> before setting
    *        your values so we can add new fields without breaking your code
    * @param exception Check *exception.errorCode!=0 for errors:
    *            "user.illegalArgument",
    *            "resource.db.unavailable", "resource.db.block", "resource.db.unknown"
    *            "resource.overflow.queue.entries", "resource.overflow.queue.bytes"
    */
   I_QueuePut put;

   /**
    * Returns maximum the first num element in the queue of highest priority
    * but does not remove it from that queue (leaves it untouched).
    * @param queueP The 'this' pointer (similar to the hidden C++ 'this' pointer)
    * @param maxNumOfEntries Access num entries, if -1 access all entries currently found
    * @param maxNumOfBytes so many entries are returned as not to exceed the amount specified. If the first
    *        entry is bigger than this amount, it is returned anyway. -1 is unlimited.
    * @param exception *exception.errorCode!=0 if the underlying implementation gets an exception:
    *            "user.illegalArgument",
    *            "resource.db.unavailable", "resource.db.block", "resource.db.unknown"
    * @return list with QueueEntry, the least elements with respect to the given time ordering
    *         or QueueEntryArr.len==0.
    *         Returned pointer is only NULL on exception
    */
   I_QueuePeekWithSamePriority peekWithSamePriority;

   /**
    * Removes the given entries from persistence. 
    * @param queueP The 'this' pointer (similar to the hidden C++ 'this' pointer)
    * @param queueEntryArr The entries to remove
    *        Please initialize each entry with <code>memset(&queueEntry, 0, sizeof(QueueEntry));</code>
    *        (or use <code>calloc()</code>) before setting
    *        your values so we can add new fields without breaking your code
    * @param exception Check *exception.errorCode!=0 for errors:
    *            "user.illegalArgument",
    *            "resource.db.unavailable", "resource.db.block", "resource.db.unknown"
    *            "resource.overflow.queue.entries", "resource.overflow.queue.bytes"
    * @return The number of removed entries
    */
   I_QueueRandomRemove randomRemove;

   /**
    * Clears (removes all entries) this queue
    * @param queueP The 'this' pointer (similar to the hidden C++ 'this' pointer)
    * @param exception Check *exception.errorCode!=0 for errors:
    *            "user.illegalArgument",
    *            "resource.db.unavailable", "resource.db.block", "resource.db.unknown"
    * @return true on success, if false *exception.errorCode is not 0
    */
   I_QueueClear clear;

   /**
    * Shutdown the queue and free memory resources, no persistent entries are destroyed. 
    * The backend store is closed and all memory allocation are freed and the queueP is set to NULL<br />
    * NOTE: Your queueP is not usable anymore after this call.
    * @param queuePP The pointer to your queue pointer, after freeing it is set to *queuePP=0
    * @param exception *exception.errorCode!=0 if the underlying implementation gets an exception
    */
   I_QueueShutdown shutdown;

   /**
    * An administrative command to remove the backend store (e.g. clear all entries and the database files). 
    * @param queueP The 'this' pointer (similar to the hidden C++ 'this' pointer)
    * @return true on success, if false *exception.errorCode is not 0
    */
   I_QueueDestroy destroy;

   /**
    * Access the current number of entries. 
    * @param queueP The 'this' pointer (similar to the hidden C++ 'this' pointer)
    * @return The number of entries in the queue, returns -1 on error
    */                                  
   I_QueueNumOfEntries getNumOfEntries;

   /**
    * Access the configured maximum number of elements for this queue. 
    * @param queueP The 'this' pointer (similar to the hidden C++ 'this' pointer)
    * @return The maximum number of elements in the queue, returns -1 when passing a NULL pointer
    */
   I_QueueMaxNumOfEntries getMaxNumOfEntries;

   /**
    * Returns the amount of bytes currently in the queue
    * If the implementation of this interface is not able to return the correct
    * number of entries (for example if the implementation must make a remote
    * call to a DB which is temporarly not available) it will return -1.
    * @param queueP The 'this' pointer (similar to the hidden C++ 'this' pointer)
    * @return The amount of bytes currently in the queue, returns -1 on error
    */
   I_QueueNumOfBytes getNumOfBytes;

   /**
    * Access the configured capacity (maximum bytes) for this queue. 
    * @param queueP The 'this' pointer (similar to the hidden C++ 'this' pointer)
    * @return The maximum capacity for the queue in bytes, returns -1 when passing a NULL pointer
    */
   I_QueueMaxNumOfBytes getMaxNumOfBytes;

   /**
    * Check if queue is empty. 
    * @param queueP The 'this' pointer (similar to the hidden C++ 'this' pointer)
    * returns true if the queue is empty, false otherwise
    */                                  
   I_QueueEmpty empty;

   /**
    * Set the logLevel to LOG_TRACE to get logging output. 
    * Other levels are not supported
    */
   XMLBLASTER_LOG_LEVEL logLevel;

   /**
    * Assign your logging function pointer to receive logging output. 
    * @see xmlBlaster/demo/c/socket/LogRedirect.c for an example
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
 *
 * Every call creates a new and independent instance which shall
 * be destroyed by a call to freeQueue() when you are done
 *
 * @param queueProperties
 *        Configuration properties of the queue, always do a first
 *        <code>memset(&queueProperties, 0, sizeof(QueueProperties));</code>
 *        to initialize new, future members.<br />
 * <pre>
 *        dbName The database name, for SQLite it is the file name on HD, "xmlBlasterClient.db"
 *        queueName The name of the queue, "connection_clientJoe"
 *        maxNumOfEntries The max. accepted entries, 10000000l
 *        maxNumOfBytes The max. accepted bytes, 1000000000ll
 *        logFp Your logging implementation or NULL if no logging callbacks are desired
 *        logLevel Set to LOG_TRACE to receive any logging
 *        userObject A pointer of your choice, is passed back when calling logFp in queueP->userObject
 * </pre>
 * @param exception
 * @return queueP The 'this' pointer
 */
Dll_Export extern I_Queue *createQueue(const QueueProperties *queueProperties, ExceptionStruct *exception);

/**
 * Frees everything inside QueueEntryArr and the struct QueueEntryArr itself. 
 * @param queueEntryArr The struct to free, it is not usable anymore after this call.
 *                      Passing NULL is OK
 */
extern Dll_Export void freeQueueEntryArr(QueueEntryArr *queueEntryArr);

/**
 * Frees everything inside QueueEntryArr but NOT the struct QueueEntryArr itself. 
 * @param queueEntryArr The struct internals to free.
 *                      Passing NULL is OK
 */
extern Dll_Export void freeQueueEntryArrInternal(QueueEntryArr *queueEntryArr);

/**
 * Frees the internal blob and the queueEntry itself. 
 * @param queueEntry Its memory is freed, it is not usable anymore after this call.
 *                   Passing NULL is OK
 */
extern Dll_Export void freeQueueEntry(QueueEntry *queueEntry);

/**
 * NOTE: You need to free the returned pointer with freeEntryDump() (which calls free())!
 *
 * @param maxContentDumpLen for -1 get the complete content, else limit the
 *        content to the given number of bytes
 * @return A ASCII XML formatted entry or NULL if out of memory
 */
extern Dll_Export char *queueEntryToXml(QueueEntry *queueEntry, int maxContentDumpLen);

/**
 * Free the memory allocated by queueEntryToXml()
 * @param queueDump NULL is OK
 */
extern Dll_Export void freeEntryDump(char *queueDump);

#ifdef __cplusplus
#ifndef XMLBLASTER_C_COMPILE_AS_CPP
}
#endif
#endif

#endif /* I_QUEUE_QueueInterface_h */


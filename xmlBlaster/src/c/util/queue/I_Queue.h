/*----------------------------------------------------------------------------
Name:      I_Queue.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Author:    "Marcel Ruff" <xmlBlaster@marcelruff.info>
Comment:   Implements a persistent queue with priorize and timestamp sorting
Note:      The gcc and icc (>=8) both define __GNUC__
-----------------------------------------------------------------------------*/
#ifndef I_QUEUE_I_Queue_h
#define I_QUEUE_I_Queue_h

#include<stdint.h>  /*-> C99:  uint64_t etc. */
/*typedef long long int64_t */

/**
 * Standard defines, copied from basicDefs.h
 * to avoid #include <basicDefs.h> with dependency on xmlBlaster
 */
#define Dll_Export 

#ifndef __cplusplus
#  if !defined(__sun) && !defined(_WINDOWS)
#    include <stdbool.h>
#  endif
#  ifndef __bool_true_false_are_defined
#    define bool int
#    define true 1
#    define false 0
#  endif
#endif

/**
 * Holds arbitrary raw data and its length
 */
typedef struct BlobStruct {
   size_t dataLen;
   char *data;
} BlobStruct;

/**
 * Holds error text
 */
#define I_QUEUE_EXCEPTION_ERRORCODE_LEN 56
#define I_QUEUE_EXCEPTION_MESSAGE_LEN 1024
typedef struct QueueException {
   bool remote; /* true if exception is from remote */
   char errorCode[I_QUEUE_EXCEPTION_ERRORCODE_LEN];
   char message[I_QUEUE_EXCEPTION_MESSAGE_LEN];
} QueueException;


/*const int QUEUE_ENTRY_EMBEDDEDTYPE_LEN = 26;*/
#define QUEUE_ENTRY_EMBEDDEDTYPE_LEN 28

/**
 * A stuct holding the necessary queue entry informations used by I_Queue. 
 */
typedef struct QueueEntry {
   int64_t uniqueId;        /** The unique key, used for sorting, usually a time stamp [nano sec]. Is assumed to be ascending over time. */
   int16_t priority;        /** The priority of the queue entry, has higher sorting order than than the time stamp */
   bool isPersistent;       /** Mark an entry to be persistent, needed for cache implementations, 'T' is true, 'F' is false. 'F' in persistent queue is a swapped transient entry */
   char embeddedType[QUEUE_ENTRY_EMBEDDEDTYPE_LEN]; /** A string describing this entry, for example the format of the blob. */
   BlobStruct embeddedBlob; /** blob.data is allocated with malloc, you need to free() it yourself, is compressed if marked as such */
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
typedef void  ( * I_QueueInitialize)(I_Queue *queueP, QueueException *exception);
typedef void  ( * I_QueueShutdown)(I_Queue *queueP, QueueException *exception);
typedef void  ( * I_QueuePut)(I_Queue *queueP, QueueEntry *queueEntry, QueueException *exception);
typedef QueueEntryArr *( * I_QueuePeekWithSamePriority)(I_Queue *queueP, int32_t maxNumOfEntries, int64_t maxNumOfBytes, QueueException *exception);
typedef int32_t ( * I_QueueRandomRemove)(I_Queue *queueP, QueueEntryArr *queueEntryArr, QueueException *exception);
typedef bool  ( * I_QueueClear)(I_Queue *queueP, QueueException *exception);
typedef bool  ( * I_QueueEmpty)(I_Queue *queueP, QueueException *exception);
typedef void  ( * I_QueueLogging)(const char *location, const char *fmt, ...);

/**
 * Interface for a queue implementation. 
 * See SQLiteQueue.c for a DB based persistent queue implementation
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/client.c.queue.html">The client.c.queue requirement</a>
 */
struct I_QueueStruct {
  /* public: */
   int argc;
   const char * const *argv;
   void *userObject; /* A client can use this pointer to point to any client specific information */

   /**
    * Initialize the queue. 
    */
   I_QueueInitialize initialize;

   /**
    * Shutdown the queue, no entries are destroyed. 
    */
   I_QueueShutdown shutdown;

   /**
    * puts a new entry into the queue. 
    * Note that this method takes the entry pointed to by the argument 
    * and puts a reference to it into the queue. This means that you can not destroy the entry before the
    * reference to it has been processed. 
    */
   I_QueuePut put;

   I_QueuePeekWithSamePriority peekWithSamePriority;

   I_QueueRandomRemove randomRemove;

   /**
    * Clears (removes all entries) this queue
    */
   I_QueueClear clear;

   /**
    * returns true if the queue is empty, false otherwise
    */                                  
   I_QueueEmpty empty;

   I_QueueLogging log;

  /* private: */
   bool isInitialized;
   void *privateObject; /* Usually holds a pointer on the internal data structure (like a DB handle or a hashtable) */
};

/**
 * Get an instance of a persistent queue. 
 * NOTE: Every call creates a new and independent instance which shall
 * be destroyed by a call to freeQueue() when you are done
 */
Dll_Export extern I_Queue *createQueue(int argc, const char* const* argv, I_QueueLogging logFp);

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
#endif /* I_QUEUE_I_Queue_h */


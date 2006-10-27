/*------------------------------------------------------------------------------
Name:      StreamingCallback.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.client;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.client.queuemsg.MsgQueuePublishEntry;
import org.xmlBlaster.jms.XBConnectionMetaData;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.I_Timeout;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.Timeout;
import org.xmlBlaster.util.Timestamp;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.dispatch.ConnectionStateEnum;
import org.xmlBlaster.util.key.MsgKeyData;
import org.xmlBlaster.util.qos.ClientProperty;
import org.xmlBlaster.util.qos.MsgQosData;
import org.xmlBlaster.util.qos.storage.ClientQueueProperty;
import org.xmlBlaster.util.queue.I_Queue;
import org.xmlBlaster.util.queue.StorageId;

import EDU.oswego.cs.dl.util.concurrent.LinkedQueue;
import EDU.oswego.cs.dl.util.concurrent.Mutex;

/**
 * StreamingCallback
 * @author <a href="mailto:michele@laghi.eu">Michele Laghi</a>
 */
public class StreamingCallback implements I_Callback, I_Timeout, I_ConnectionStateListener {

   /**
    * 
    * Writer needed since the out stream must be written from a thread which does not
    * die before the thread which reads the in counterpart. For some "strange" reason
    * the implementation of the Pipe streams makes a check if the thread which has 
    * made the last write operation on the out stream still is valid. If not, a Dead
    * End IO Exception is thrown when reading.
    * 
    * @author <a href="mailto:michele@laghi.eu">Michele Laghi</a>
    */
   class Writer extends Thread {

      class WriterData extends Mutex {
         private OutputStream out;
         private byte[] data;
         private Throwable ex;
         
         public WriterData(OutputStream out, byte[] data) {
            super();
            this.out = out;
            this.data = data;
         }
      }

      private LinkedQueue channel;
      
      public Writer(String name) {
         super(name);
         this.channel = new LinkedQueue();
         setDaemon(true);
         start();
      }

      public Writer() {
         super();
         this.channel = new LinkedQueue();
         setDaemon(true);
         start();
      }
      
      public synchronized void write(OutputStream out, byte[] buf) throws InterruptedException, XmlBlasterException {
         WriterData data = new WriterData(out, buf);
         try {
            data.acquire();
            this.channel.put(data);
            data.acquire(); // waits until the other thread is finished
            if (data.ex != null)
               throw new XmlBlasterException(global, ErrorCode.USER_UPDATE_HOLDBACK, "write: a throwable occured", "", data.ex);
         }
         finally {
            data.release();
         }
      }

      public synchronized void close(OutputStream out) throws InterruptedException, XmlBlasterException {
         WriterData data = new WriterData(out, null);
         try {
            data.acquire();
            this.channel.put(data);
            data.acquire(); // waits until the other thread is finished
            if (data.ex != null)
               throw new XmlBlasterException(global, ErrorCode.USER_UPDATE_HOLDBACK, "close: a throwable occured", "", data.ex);
         }
         finally {
            data.release();
         }
      }

      /**
       * @see java.lang.Thread#run()
       */
      public void run() {
         while (true) {
            try {
               WriterData writerData = (WriterData)this.channel.take();
               try {
                  if (writerData.out != null) {
                     if (writerData.data != null) {

                        int bytesLeft = writerData.data.length;
                        int bytesRead = 0;
                        final int MAX_CHUNK_SIZE = 4096;
                        while (bytesLeft > 0) {
                           int toRead = bytesLeft > MAX_CHUNK_SIZE ? MAX_CHUNK_SIZE : bytesLeft;
                           writerData.out.write(writerData.data, bytesRead, toRead);
                           writerData.out.flush();
                           bytesRead += toRead;
                           bytesLeft -= toRead;
                        }
                        // writerData.out.write(0);
                        // writerData.out.flush();
                        
                        // these would block (probably the pipes are not the best in the world
                        // writerData.out.write(writerData.data);
                        // writerData.out.flush();
                     }
                     else
                        writerData.out.close();
                  }
               }
               catch (Throwable ex) {
                  writerData.ex = ex;
               }
               finally {
                  writerData.release();
               }
            }
            catch (Throwable ex) {
               if (ex.getMessage().indexOf("Pipe closed") < 0) {
                  log.warning("An exception occured when writing to the stream: ' " + ex.getMessage());
                  ex.printStackTrace();
               }
               else if (log.isLoggable(Level.FINE)) {
                  log.fine("The pipe was closed, which resulted in an IO Exception. It can happen when the client has returned before reading the complete message");
                  ex.printStackTrace();
               }
            }
         }
      }
   }
   
   
   class ExecutionThread extends Thread {
      
      public ExecutionThread() {
      }
      
      public void run() {
         try {
            ret = updateNewMessage(cbSessionId, updateKey, content, updateQos);
            clearQueue();
         }
         catch (Throwable e) {
            setException(e);
            e.printStackTrace();
         }
         finally {
            try {
               if (in != null)
                  in.close();
            }
            catch (IOException ex) {
               ex.printStackTrace();
            }
            mutex.release();
         }
      }
   };

   private static Logger log = Logger.getLogger(StreamingCallback.class.getName());
   public final static String ENTRY_CB_SESSION_ID = "__entryCbSessionId";
   
   private I_StreamingCallback callback;

   private Global global;
   private PipedOutputStream out;
   private PipedInputStream in;
   private XmlBlasterException ex;
   private String ret;
   private String cbSessionId;
   private UpdateKey updateKey;
   private UpdateQos updateQos;
   private byte[] content;
   private Writer writer;
   /** The time to wait in ms until returning when waiting (if zero or negative inifinite) */
   private long waitForChunksTimeout;
   // private long waitForClientReturnTimeout;
   private Timeout timer;
   private Timestamp timestamp; // the key for the timeout timer (can be null)
   private I_Queue queue; // optional client side queue
   private boolean useQueue;
   private boolean initialized;
   private boolean lastMessageCompleted = true;
   private Mutex mutex;
   
   private void reset() throws XmlBlasterException {
      this.out = null;
      this.in = null;
      this.ret = null;
      this.cbSessionId = null;
      this.updateKey = null;
      this.updateQos = null;
      this.content = null;
   }
   
   public StreamingCallback(Global global, I_StreamingCallback callback) throws XmlBlasterException {
      this(global, callback, 0L, 0L, false);
   }
   
   /**
    * 
    * @param callback
    */
   public StreamingCallback(Global global, I_StreamingCallback callback, long waitForChunksTimeout, long waitForClientReturnTimeout, boolean useQueue) 
      throws XmlBlasterException {
      this.callback = callback;
      this.global = global;
      this.mutex = new Mutex();
      String writerName = StreamingCallback.class.getName() + "-writer";
      synchronized(this.global) {
         this.writer = (Writer)this.global.getObjectEntry(writerName);
         if (this.writer == null) {
            this.writer = new Writer();
            this.global.addObjectEntry(writerName, this.writer);
         }
      }
      this.waitForChunksTimeout = waitForChunksTimeout;
      // this.waitForClientReturnTimeout = waitForClientReturnTimeout;
      if (this.waitForChunksTimeout > 0L) {
         String timerName = StreamingCallback.class.getName() + "-timer";
         synchronized(this.global) {
            this.timer = (Timeout)this.global.getObjectEntry(timerName);
            if (this.timer == null) {
               this.timer = new Timeout(timerName);
               this.global.addObjectEntry(timerName, this.timer);
            }
         }
      }
      this.useQueue = useQueue;
      // TODO latch until connected to avoit early updates
   }
   
   /**
    * 
    * @return the number of delivered entries from local client update queue.
    */
   public final int sendInitialQueueEntries() throws XmlBlasterException {
      if (this.queue == null)
         return 0;
      ArrayList list = this.queue.peek(-1, -1L);
      for (int i=0; i < list.size(); i++) {
         MsgQueuePublishEntry entry = (MsgQueuePublishEntry)list.get(i);
         MsgKeyData key = entry.getMsgKeyData();
         MsgQosData qos =(MsgQosData)entry.getMsgUnit().getQosData();
         byte[] content = entry.getMsgUnit().getContent();
         String entryCbSessionId = qos.getClientProperty(ENTRY_CB_SESSION_ID, (String)null);
         qos.getClientProperties().remove(ENTRY_CB_SESSION_ID);
         final boolean isExternal = false; // we don't want to store these entries since already here
         updateInternal(entryCbSessionId, new UpdateKey(key), content, new UpdateQos(this.global, qos), isExternal);
      }
      this.queue.clear();
      return list.size();
   }
   
   private final void storeEntry(String cbSessionId, UpdateKey key, byte[] content, UpdateQos qos) throws XmlBlasterException {
      if (this.queue == null)
         return;
      final boolean ignorePutInterceptor = false;
      if (cbSessionId != null) {
         String oldCbSessionId = qos.getClientProperty(ENTRY_CB_SESSION_ID, (String)null);
         if (oldCbSessionId != null && !oldCbSessionId.equals(cbSessionId)) {
            log.warning("the client property '" + ENTRY_CB_SESSION_ID + "' is a reserved word, we will overwrite its value='" + oldCbSessionId + "' to be '" + cbSessionId + "'");
            ClientProperty prop = new ClientProperty(ENTRY_CB_SESSION_ID, null, null, cbSessionId);
            qos.getClientProperties().put(prop.getName(), prop);
         }
      }
      MsgUnit msgUnit = new MsgUnit(key.getData(), content, qos.getData());
      MsgQueuePublishEntry entry = new MsgQueuePublishEntry(this.global, msgUnit, this.queue.getStorageId());
      this.queue.put(entry, ignorePutInterceptor);
   }
   
   /**
    * @see org.xmlBlaster.client.I_Callback#update(java.lang.String, org.xmlBlaster.client.key.UpdateKey, byte[], org.xmlBlaster.client.qos.UpdateQos)
    */
   public String updateStraight(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) throws XmlBlasterException, IOException {
      log.fine("cbSessionId='" + cbSessionId + "'");
      ByteArrayInputStream bais = new ByteArrayInputStream(content);
      return this.callback.update(cbSessionId, updateKey, bais, updateQos);
   }
   
   /**
    * @see org.xmlBlaster.client.I_Callback#update(java.lang.String, org.xmlBlaster.client.key.UpdateKey, byte[], org.xmlBlaster.client.qos.UpdateQos)
    */
   public String updateNewMessage(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) throws XmlBlasterException, IOException {
      log.fine("cbSessionId='" + cbSessionId + "'");
      return this.callback.update(cbSessionId, updateKey, in, updateQos);
   }

   private final boolean isFirstChunk(UpdateQos qos) {
      int seq = qos.getClientProperty(Constants.addJmsPrefix(XBConnectionMetaData.JMSX_GROUP_SEQ, log), 0);
      return seq == 0;
   }
   
   private final boolean isLastChunk(UpdateQos qos) {
      boolean hasGroupId = qos.getClientProperty(Constants.addJmsPrefix(XBConnectionMetaData.JMSX_GROUP_ID, log), (String)null) != null;
      if (!hasGroupId)
         return true;
      return qos.getClientProperty(Constants.addJmsPrefix(XBConnectionMetaData.JMSX_GROUP_EOF, log), false);
   }
   
   private final ClientProperty getProp(String key, UpdateQos qos) {
      return qos.getClientProperty(Constants.addJmsPrefix(key, log));
   }
   
   /**
    * @see org.xmlBlaster.client.I_Callback#update(java.lang.String, org.xmlBlaster.client.key.UpdateKey, byte[], org.xmlBlaster.client.qos.UpdateQos)
    */
   public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) throws XmlBlasterException {
      boolean sendInitial = this.queue != null && this.lastMessageCompleted && this.queue.getNumOfEntries() > 0; 
      if (sendInitial)
         sendInitialQueueEntries();
      
      final boolean isExternal = true;
      log.fine("cbSessionId='" + cbSessionId + "'");
      return updateInternal(cbSessionId, updateKey, content, updateQos, isExternal);
   }
   
   /**
    * @see org.xmlBlaster.client.I_Callback#update(java.lang.String, org.xmlBlaster.client.key.UpdateKey, byte[], org.xmlBlaster.client.qos.UpdateQos)
    */
   private final String updateInternal(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos, boolean isExternal) throws XmlBlasterException {
      this.lastMessageCompleted = false;
      boolean doStore = isExternal;
      boolean isLastChunk = false;
      try {
         log.fine("entering with cbSessionId='" + cbSessionId + "'");
         if (this.timer != null && this.timestamp != null) { // no need to be threadsafe since update is single thread
            this.timer.removeTimeoutListener(this.timestamp);
            this.timestamp = null;
         }
         ClientProperty exProp = getProp(XBConnectionMetaData.JMSX_GROUP_EX, updateQos);
         if (exProp != null)
            throw new XmlBlasterException(this.global, ErrorCode.USER_UPDATE_INTERNALERROR, "update", "An exception occured on a chunk when updating. " + updateQos.toXml());
         isLastChunk = isLastChunk(updateQos);
         
         synchronized(this) {
            consumeExceptionIfNotNull();
            if (this.ret != null) {
               clearQueue();
               return ret;
            }
         }
         
         if (isLastChunk) { // no need to store the last message since sync return
            if (isFirstChunk(updateQos)) {
               // TODO a sync to wait until cleared (the updateStraight after the sync, not inside).
               try {
                  return updateStraight(cbSessionId, updateKey, content, updateQos);
               }
               catch (IOException ex) {
                  throw new XmlBlasterException(this.global, ErrorCode.INTERNAL, "StreamingCallback", "update: exception occured.", ex);
               }
               
            }
            
            try {
               if (content != null && content.length > 0) {
                  this.writer.write(this.out, content);
               }
               
               this.writer.close(this.out);
               // wait until the client has returned his method.
               try {
                  mutex.acquire();
                  consumeExceptionIfNotNull();
                  clearQueue();
                  return this.ret;
               }
               finally {
                  mutex.release();
               }
            }
            catch (InterruptedException e) {
               throw new XmlBlasterException(this.global, ErrorCode.INTERNAL, "StreamingCallback", "update", e);
            }
            finally {
               reset();
            }
         }
         else { // it is not the last message
            if (this.timer != null)
               this.timestamp = this.timer.addTimeoutListener(this, this.waitForChunksTimeout, null);
            try {
               if (isFirstChunk(updateQos)) {
                  this.mutex.acquire();
                  this.cbSessionId = cbSessionId;
                  this.updateKey = updateKey;
                  this.updateQos = updateQos;
                  this.content = content;
                  this.out = new PipedOutputStream();
                  this.in = new PipedInputStream(this.out);
                  ExecutionThread thread = new ExecutionThread();
                  thread.start();
               }
               else { // check if the message is complete
                  /*
                  if (this.oldGroupId == null) {
                     try {
                        mutex.acquire();
                        throw new XmlBlasterException(this.global, ErrorCode.INTERNAL, "StreamingCallback", "update: The message is not the first of a group but the previous one was already completed.");
                     }
                     finally {
                        mutex.release();
                     }
                  }
                  */
               }
               this.writer.write(this.out, content);
            }
            catch (InterruptedException ex) {
               throw new XmlBlasterException(this.global, ErrorCode.INTERNAL, "StreamingCallback", "update", ex);
            }
            catch (IOException ex) {
               throw new XmlBlasterException(this.global, ErrorCode.INTERNAL, "StreamingCallback", "update", ex);
            }
            if (doStore)
               storeEntry(cbSessionId, updateKey, content, updateQos);
            // and return a fake positive response.
            return Constants.RET_OK;
         }
         
      }
      catch (XmlBlasterException ex) {
         try {
            this.writer.close(this.out);
         }
         catch (InterruptedException e) {
            e.printStackTrace();
         }
         this.lastMessageCompleted = true;
         throw ex;
      }
      catch (Throwable ex) {
         ex.printStackTrace();
         throw new XmlBlasterException(this.global, ErrorCode.USER_UPDATE_HOLDBACK, "throwable in updateInternal", "", ex);
      }
      finally {
         if (isLastChunk) {
            this.lastMessageCompleted = true;
         }
         log.fine("Leaving method");
      }
   }

   /**
    * It is used here to inform the user update method that a timeout occured, it will throw
    * an IOException when reading the in stream of the update method.
    * @see org.xmlBlaster.util.I_Timeout#timeout(java.lang.Object)
    */
   public void timeout(Object userData) {
      try {
         this.writer.close(this.out);
      }
      catch (Throwable ex) {
         // we can not make it threadsafe so we must protect against possible NPE Exceptions
         ex.printStackTrace();
      }
      
   }

   private final void clearQueue() {
      if (queue != null) {
         log.fine("Clear the queue " + this.queue.getStorageId());
         queue.clear();
      }
   }

   /**
    * Always makes a USER_UPDATE_HOLDBACK Exception out of it, no matter what the original exception 
    * was.
    * @param ex
    */
   private synchronized void setException(Throwable ex) {
      if (ex instanceof XmlBlasterException) {
         XmlBlasterException tmp = (XmlBlasterException)ex;
         if (tmp.getErrorCode().equals(ErrorCode.USER_UPDATE_HOLDBACK))
            this.ex = tmp;
         else
            this.ex = new XmlBlasterException(global, ErrorCode.USER_UPDATE_HOLDBACK, "StreamingCallback", "update: exception occured.", ex);
      }
      else {
         this.ex = new XmlBlasterException(global, ErrorCode.USER_UPDATE_HOLDBACK, "StreamingCallback", "update: exception occured.", ex);
      }
   }
   
   /**
    * returns the exception (if any) and resets it.
    * @return
    */
   private synchronized void consumeExceptionIfNotNull() throws XmlBlasterException {
      XmlBlasterException ex = this.ex;
      if (ex != null) {
         this.ex = null;
         throw ex;
      }
   }
   
   // implementation of interface I_ConnectionStateListener
   
   /**
    * @see org.xmlBlaster.client.I_ConnectionStateListener#reachedAlive(org.xmlBlaster.util.dispatch.ConnectionStateEnum, org.xmlBlaster.client.I_XmlBlasterAccess)
    */
   public synchronized void reachedAlive(ConnectionStateEnum oldState, I_XmlBlasterAccess connection) {
      log.fine("I am alive now");
      // only used on first connect after it is ignored.
      if (this.initialized)
         return;
     
      if (this.useQueue) {
         log.info("going to instance the queue");
         ConnectQos connectQos = connection.getConnectQos();
         ClientQueueProperty prop = connectQos.getClientQueueProperty();
         // The storageId must remain the same after a client restart
         String storageIdStr = connection.getId();
         if (((XmlBlasterAccess)connection).getPublicSessionId() == 0 ) {
            // having no public sessionId we need to generate a unique queue name
            storageIdStr += System.currentTimeMillis()+Global.getCounter();
         }
         StorageId queueId = new StorageId(Constants.RELATING_CLIENT_UPDATE, storageIdStr);
         try {
            this.queue = this.global.getQueuePluginManager().getPlugin(prop.getType(), prop.getVersion(), queueId,
                  connectQos.getClientQueueProperty());
            if (((XmlBlasterAccess)connection).isCallbackDispatcherActive())
               sendInitialQueueEntries();
         }
         catch (XmlBlasterException ex) {
            log.severe("An exception occured when trying to initialize the callback client queue: " + ex.getMessage());
            ex.printStackTrace();
         }
      }
      
      this.initialized = true;
   }

   /* (non-Javadoc)
    * @see org.xmlBlaster.client.I_ConnectionStateListener#reachedDead(org.xmlBlaster.util.dispatch.ConnectionStateEnum, org.xmlBlaster.client.I_XmlBlasterAccess)
    */
   public void reachedDead(ConnectionStateEnum oldState, I_XmlBlasterAccess connection) {
      // TODO Auto-generated method stub
      
   }

   /* (non-Javadoc)
    * @see org.xmlBlaster.client.I_ConnectionStateListener#reachedPolling(org.xmlBlaster.util.dispatch.ConnectionStateEnum, org.xmlBlaster.client.I_XmlBlasterAccess)
    */
   public void reachedPolling(ConnectionStateEnum oldState, I_XmlBlasterAccess connection) {
      // TODO Auto-generated method stub
      
   }
   
}

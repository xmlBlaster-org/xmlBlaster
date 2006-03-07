/*------------------------------------------------------------------------------
Name:      FileRecorder.java
Project:   xmlBlaster.org
Comment:   Records requests of the client into a file, if the client is
           not able to connect to the xmlBlaster.
Author:    astelzl@avitech.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.recorder.file;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.Timestamp;
import org.xmlBlaster.util.plugin.I_Plugin;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.def.MethodName;
import org.xmlBlaster.util.recorder.I_InvocationRecorder;
import org.xmlBlaster.util.qos.StatusQosData;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.client.qos.SubscribeReturnQos;
import org.xmlBlaster.client.qos.UnSubscribeReturnQos;
import org.xmlBlaster.client.qos.EraseReturnQos;
import org.xmlBlaster.client.qos.PublishReturnQos;
import org.xmlBlaster.client.protocol.I_XmlBlaster;

import java.io.IOException;

/**
 * Records requests of the client into a file, if the client is
 * not able to connect to the xmlBlaster. By implementing the
 * I_InvocationRecorder it can be plugged into the xmlBlaster.
 * This implementation is very similar to the implementation
 * of RamRecorder for the xmlBlaster ($XMLBLASTER_HOME/src/java/org/xmlBlaster/util/recorder/ram).
 * <p />
 * Configuration:
 * <pre>
 * RecorderPlugin[FileRecorder][1.0]=org.xmlBlaster.util.recorder.file.FileRecorder
 * </pre>
 * See the <a href="http://www.xmlblaster.org/xmlBlaster/doc/requirements/util.recorder.html">util.recorder</a> requirement.
 * @author astelzl@avitech.de
 * @author pavol.hrnciarik@pixelpark.com
 * @author xmlBlaster@marcelruff.info
 * @see org.xmlBlaster.test.qos.TestFailSave
 * @see org.xmlBlaster.test.classtest.InvocationRecorderTest
 */
public class FileRecorder implements I_Plugin, I_InvocationRecorder//, I_CallbackRaw
{
   private final String ME = "FileRecorder";
   private Global glob;
   private static Logger log = Logger.getLogger(FileRecorder.class.getName());

   private FileIO rb;

   private String fileName;

   private I_XmlBlaster serverCallback = null;
   //private I_CallbackRaw clientCallback = null;

   private final MsgUnit[] dummyMArr = new MsgUnit[0];
   private final PublishReturnQos[] dummyPubRetQosArr = new PublishReturnQos[0];
   private PublishReturnQos dummyPubRet;
   private SubscribeReturnQos dummySubRet;
   private UnSubscribeReturnQos[] dummyUnSubRet = new UnSubscribeReturnQos[0];
   private final EraseReturnQos[] dummyEraseReturnQosArr = new EraseReturnQos[0];

   /** Automatically write curr pos to file? */
   private boolean autoCommit = true; // only true is supported
  

  /**
   * Setup the file name. 
   * <p />
   * File name is:
   * <pre>
   *   Persistence.Path=${user.home}${file.separator}tmp
   *   recorder.path=${Persistence.Path}${file.separator}fileRecorder
   *
   *   The file name is
   *       tailback-[clientClusterNodeId]-[serverClusterNodeId].frc
   *
   *   For example:
   *      /home/michelle/tmp/fileRecorder/tailback-heron-to-avalon.frc
   * </pre>
   * @see <a href="http://www.xmlblaster.org/xmlBlaster/doc/requirements/util.recorder.html">util.recorder requirement</a>
   */
   public void initialize(Global glob, String fn, long maxEntries, I_XmlBlaster serverCallback) throws XmlBlasterException
   //, I_CallbackRaw clientCallback) throws XmlBlasterException
   {
      this.glob = glob;
      this.serverCallback = serverCallback;
      //this.clientCallback = clientCallback;

      StatusQosData statRetQos = new StatusQosData(glob, MethodName.PUBLISH);
      statRetQos.setStateInfo(Constants.INFO_QUEUED);
      this.dummyPubRet = new PublishReturnQos(glob, statRetQos);
      StatusQosData subRetQos = new StatusQosData(glob, MethodName.SUBSCRIBE);
      subRetQos.setStateInfo(Constants.INFO_QUEUED);
      this.dummySubRet = new SubscribeReturnQos(glob, subRetQos);

      fileName = createPathString(fn);

      boolean useSync = glob.getProperty().get("recorder.useSync", false);

      try {
         this.rb = new FileIO(glob, fileName, new MsgDataHandler(glob), maxEntries, useSync);
         if (rb.getNumUnread() > 0) {
            boolean destroyOld = glob.getProperty().get("recorder.destroyOld", false);
            if (destroyOld) {
               log.warning("Destroyed " + rb.getNumUnread() + " unprocessed tail back messages in '" + fileName + "' as requested with option 'recorder.destroyOld=true'.");
               rb.destroy();
               rb.initialize();
            }
            else {
               log.info("Found " + rb.getNumUnread() + " unprocessed tail back messages in '" + fileName + "'.");
            }
         }
         else {
            if (log.isLoggable(Level.FINE)) log.fine("Using persistence file '" + fileName + "' for tail back messages.");
         }
      }
      catch(IOException ex) {
         log.severe("Error at creation of RecordBuffer. It is not possible to buffer any messages: " + ex.toString());
         throw new XmlBlasterException(glob, ErrorCode.INTERNAL_UNKNOWN, ME, "Initializing FileRecorder failed: Error at creation of RecordBuffer. It is not possible to buffer any messages: " + ex.toString());
      }
      if (maxEntries < 0)
         log.info("FileRecorder is ready, unlimited tail back messages are stored in '" + fileName + "'");
      else
         log.info("FileRecorder is ready, max=" + maxEntries + " tail back messages are stored in '" + fileName + "'");
   }

   /**
    * Returns the name of the file to store tail back messages. 
    * <p />
    * Example:
    * <pre>
    *  For an ordinary client 'joe' with public session id 9
    *
    *   /home/xmlblast/tmp/fileRecorder/tailback-joe9-to-frodo.frc
    *
    *  In a cluster environment (which only logs in once (exactly one session):
    *
    *   /home/xmlblast/tmp/fileRecorder/tailback-bilbo-to-heron.frc  (in cluster environment)
    * </pre>
    * See the <a href="http://www.xmlblaster.org/xmlBlaster/doc/requirements/util.recorder.html">util.recorder</a> requirement
    * on how to configure.
    * @param fn The file name, without any path information or null
    * @return The complete path with filename
    */
   private String createPathString(String fn) {
      String fullName = glob.getProperty().get("recorder.path", (String)null);
      fullName = glob.getProperty().get("recorder.path["+glob.getId()+"]", fullName);
      if (fullName == null) {
         fullName = glob.getProperty().get("Persistence.Path", System.getProperty("user.home") + System.getProperty("file.separator") + "tmp");
         fullName += System.getProperty("file.separator") + "fileRecorder";
      }
      if (fn == null) {
         fn = glob.getProperty().get("recorder.fn", (String)null);
         fn = glob.getProperty().get("recorder.fn["+glob.getId()+"]", fn);
         if (fn == null)
            fn = "tailback-" + glob.getStrippedId() + ".frc";
      }
      return fullName + System.getProperty("file.separator") + fn;
   }

   /** Returns the name of the database file or null if RAM based */
   public String getFullFileName() {
      return fileName;
   }

   /**
    * This method is called by the PluginManager (enforced by I_Plugin). 
    * @see org.xmlBlaster.util.plugin.I_Plugin#init(org.xmlBlaster.util.Global,org.xmlBlaster.util.plugin.PluginInfo)
    */
   public void init(org.xmlBlaster.util.Global glob, org.xmlBlaster.util.plugin.PluginInfo pluginInfo) {
   }

   /**
    * Return plugin type for Plugin loader
    * @return "FileRecorder"
    */
   public String getType() {
      return "FileRecorder";
   }

   /**
    * Return plugin version for Plugin loader
    * @return "1.0"
    */
   public String getVersion() {
      return "1.0";
   }

   /**
    * @param mode
    *        ONOVERFLOW_DEADMESSAGE = "deadMessage",
    *        ONOVERFLOW_DISCARD = "discard", ONOVERFLOW_DISCARDOLDEST = "discardOldest",
    *        ONOVERFLOW_EXCEPTION = "exception"
    */
   public void setMode(String mode)
   {
      if (mode == null) return;

      if (mode.equals(Constants.ONOVERFLOW_DISCARDOLDEST))
         this.rb.setModeDiscardOldest();
      else if (mode.equals(Constants.ONOVERFLOW_DISCARD))
         this.rb.setModeDiscard();
      else if (mode.equals(Constants.ONOVERFLOW_EXCEPTION)) {
         this.rb.setModeException();
         log.fine("Setting onOverflow mode to exception"); // default
      }
      else
         log.warning("Ignoring unknown onOverflow mode '" + mode + "', using default mode 'exception'."); // default
   }

  /**
   * Dummy method. Just provided as other recorders also provide it. 
   * @return always false
   */
  public final boolean isFull() throws XmlBlasterException
  { return false;
  }


  /**
   * Number of requests buffered
   */
  public final long getNumUnread()
  { return rb.getNumUnread();
  }

   /**
   * This method reads out stored requests from the file where 
   * they were buffered. Depending on the "request-method" 
   * the appropriate client operation will be invoked.
   */ 
   public void pullback(long startDate, long endDate, double motionFactor) throws XmlBlasterException
   {
      log.info("Invoking pullback(startDate=" + startDate + ", endDate=" + endDate + ", motionFactor=" + motionFactor + ") numUnread=" + getNumUnread());

      RequestContainer cont = null;
      long startOfPullback = System.currentTimeMillis();
      long numAtBeginning = getNumUnread();

      while(rb.getNumUnread() > 0) { // find the start node ...
         try {
           cont = (RequestContainer)rb.readNext(autoCommit);
         }
         catch(IOException ex){}
         if (cont == null)
            break;
         if (startDate == 0L || cont.timestamp >= startDate)
            break;
      }
 
      if (cont == null) {
         log.warning("Sorry, no invocations found, queue is empty or your start date is to late");
         return;
      }

      long startTime = cont.timestamp;
      long playbackStart = System.currentTimeMillis();

      while(cont != null) {
         if (endDate != 0 && cont.timestamp > endDate) // break if the end date is reached
            break;

         if (motionFactor == 0.)
            callback(cont);
         else {
            long actualElapsed = (long)((System.currentTimeMillis() - playbackStart) * motionFactor);
            long originalElapsed = cont.timestamp - startTime;
            if (originalElapsed > actualElapsed) 
            { try 
               { Thread.sleep(originalElapsed - actualElapsed);                } 
               catch(InterruptedException e) 
               { log.warning("Thread sleep got interrupted, this invocation is not in sync");
               }
            }
            callback(cont);
         }
         try {
            cont = (RequestContainer)rb.readNext(autoCommit);
         }
         catch(IOException ex){}
      }

      long elaps = System.currentTimeMillis()-startOfPullback;
      if (elaps > 0) {
         log.info("Pullback of " + (numAtBeginning-getNumUnread()) + " messages done - elapsed " +
               Timestamp.millisToNice(elaps) +
               " average rate was " + (numAtBeginning*1000L/elaps) + 
               " msg/sec, numUnread=" + getNumUnread());
      }
      else
         log.info("Pullback of " + (numAtBeginning-getNumUnread()) + " messages done very fast");
      // we are done, everything played back
   }

   /**
    * Playback the stored messages, the are removed from the recorder after the callback. 
    * <p />
    * The messages are retrieved with the given rate per second
    * <p />
    * This method is thread save, even for undo on exceptions.
    * @param msgPerSec 20. is 20 msg/sec, 0.1 is one message every 10 seconds<br />
    *                  With -1.0 we deliver as fast as possible
    */
   public void pullback(float msgPerSec) throws XmlBlasterException {

      log.info("Invoking pullback(msgPerSec=" + msgPerSec + ") numUnread=" + getNumUnread());

      RequestContainer cont = null;

      long startOfPullback = System.currentTimeMillis();
      long numAtBeginning = getNumUnread();

      while(true) {
         // woke up after sleeping, sending the next bulk ...

         long startTime = System.currentTimeMillis();

         int numSent = 0;
         while (true) {
            // Send for one second messages until msgPerSec is reached ...

            int localCount = 0; // for logging output only

            // to protect undo (if multi thread access) we could pass the currPos with rb
            // and pass it to undo again if we want to avoid this because of dead lock danger
            synchronized (rb) {
               try {
                  cont = (RequestContainer)rb.readNext(autoCommit);

                  if (cont == null) {
                     long elaps = System.currentTimeMillis()-startOfPullback;
                     if (elaps == 0L) elaps = 1L;
                     log.info("Pullback of " + (numAtBeginning-getNumUnread()) +
                         " messages done - elapsed " +
                         Timestamp.millisToNice(elaps) +
                         " average rate was " + (numAtBeginning*1000L/elaps) + 
                         " msg/sec, numUnread=" + getNumUnread());
                     return;    // we are done, everything played back
                  }

                  // How many messages are sent in a bulk?
                  localCount = (cont.msgUnitArr != null) ? cont.msgUnitArr.length : 1;
                  
                  callback(cont);

                  numSent += localCount;
               }
               catch (Exception e) {
                  String text;
                  if (rb.undo() == true) {
                     text = "Playback of tail back messages failed, " + getNumUnread() + " messages are kept savely in '" + fileName + "': " + e.toString();
                     log.warning(text);
                  }
                  else {
                     text = "Playback of tail back messages failed, " + getNumUnread() + " messages are in queue, " + localCount + " are lost, check '" + fileName + "': " + e.toString();
                     log.severe(text);
                  }
                  throw new XmlBlasterException(glob, ErrorCode.INTERNAL_UNKNOWN, ME, text);
               }
            }

            if (msgPerSec <= 0.)
               continue;

            if (numSent >= msgPerSec)
               break;     // the desired rate per second is reached
         }

         long actualElapsed = System.currentTimeMillis() - startTime;

         // We have the actually sent number of messages and can calculate
         // how long to sleep to fulfill the desired msgPerSec
         long timeToUse = (long)(1000. * numSent / msgPerSec);

         if (actualElapsed < timeToUse) {
            try {
               Thread.sleep(timeToUse - actualElapsed);
            } catch( InterruptedException i) {
               log.warning("Unexpected interrupt when sleeping for pullback");
            }
         }
      }
   }

   /**
    * How many messages are silently lost in 'discard' or 'discardOldest' mode?
    */
   public long getNumLost() {
      return rb.getNumLost();
   }

  /**
   * Not implemented yet
   */
  public void playback(long startDate, long endDate, double motionFactor) throws XmlBlasterException {
      //Has to be implemented. Look at InvocationRecorder for further information
      log.severe("Sorry, playback() is not implemented, use pullback() or implement it");
      throw new XmlBlasterException(glob, ErrorCode.INTERNAL_NOTIMPLEMENTED, "FileRecorder.NoImpl", "Sorry, only pullback is implemented");
  }

  //appropriate client function will be called depending on the request method
  private void callback(RequestContainer cont) throws XmlBlasterException { 
    if (serverCallback != null) 
    {
      // This should be faster then reflection
      if (cont.method == MethodName.PUBLISH_ONEWAY) 
      { serverCallback.publishOneway(cont.msgUnitArr);
        return;
      }
      else if (cont.method == MethodName.PUBLISH) 
      { 
        if (cont.msgUnitArr.length == 1) // simulate single PUBLISH
           serverCallback.publish(cont.msgUnitArr[0]);
        else
           serverCallback.publishArr(cont.msgUnitArr); // PUBLISH_ARR
        return;
      }
      else if (cont.method == MethodName.GET) 
      { serverCallback.get(cont.xmlKey, cont.xmlQos);
        return;
      }
      else if (cont.method == MethodName.SUBSCRIBE) 
      { serverCallback.subscribe(cont.xmlKey, cont.xmlQos);
        return;
      }
      else if (cont.method == MethodName.UNSUBSCRIBE) 
      { serverCallback.unSubscribe(cont.xmlKey, cont.xmlQos);
        return;
      }
      else if (cont.method == MethodName.ERASE) 
      { serverCallback.erase(cont.xmlKey, cont.xmlQos);
        return;
      }
    }

    /*
    if (clientCallback != null) 
    {
      // This should be faster then reflection
      if (cont.method == MethodName.UPDATE) 
      {
        clientCallback.update(cont.cbSessionId, cont.msgUnitArr);
        return;
      }
      else if (cont.method == MethodName.UPDATE_ONEWAY) 
      {
        clientCallback.updateOneway(cont.cbSessionId, cont.msgUnitArr);
        return;
      }
    }
    */

    log.severe("Internal error: Method '" + cont.method + "' is unknown");
    throw new XmlBlasterException(glob, ErrorCode.INTERNAL_UNKNOWN, ME, "Internal error: Method '" + cont.method + "' is unknown");
  }

  /**
   * storing subscribe request
   */
  public SubscribeReturnQos subscribe(String xmlKey, String qos) throws XmlBlasterException
  {
    RequestContainer cont = new RequestContainer();
    cont.method = MethodName.SUBSCRIBE;
    cont.xmlKey = xmlKey;
    cont.xmlQos = qos;
    try
    { rb.writeNext(cont);
    }
    catch(IOException ex) {
       throw new XmlBlasterException(glob, ErrorCode.INTERNAL_UNKNOWN, ME, cont.method + " invocation: " + ex.toString());
    }
    return dummySubRet;
  }

  /**
   * storing unSubscribe request
   */
  public UnSubscribeReturnQos[] unSubscribe(String xmlKey, String qos) throws XmlBlasterException
  {
    RequestContainer cont = new RequestContainer();
    cont.method = MethodName.UNSUBSCRIBE;
    cont.xmlKey = xmlKey;
    cont.xmlQos = qos;
    try
    { rb.writeNext(cont);
    }
    catch(IOException ex) {
       throw new XmlBlasterException(glob, ErrorCode.INTERNAL_UNKNOWN, ME, cont.method + " invocation: " + ex.toString());
    }
    return dummyUnSubRet;
  }

  /**
   * storing publish request
   */
  public PublishReturnQos publish(MsgUnit msgUnit) throws XmlBlasterException
  {
    RequestContainer cont = new RequestContainer();
    cont.method = MethodName.PUBLISH;
    cont.msgUnitArr = new MsgUnit[] { msgUnit };
    try
    { rb.writeNext(cont);
    }
    catch(IOException ex) {
       throw new XmlBlasterException(glob, ErrorCode.INTERNAL_UNKNOWN, ME, cont.method + " invocation: " + ex.toString());
    }
    return dummyPubRet;
  }

  /**
   * storing publishOneway request
   */
  public void publishOneway(MsgUnit[] msgUnitArr) throws XmlBlasterException
  { 
    RequestContainer cont = new RequestContainer();
    cont.method = MethodName.PUBLISH_ONEWAY;
    cont.msgUnitArr = msgUnitArr;
    try
    { rb.writeNext(cont);
    }
    catch(IOException ex) {
       log.severe(cont.method + " invocation failed: " + ex.toString());
    }
  }

  /**
   * storing publishArr request
   */
  public PublishReturnQos[] publishArr(MsgUnit[] msgUnitArr) throws XmlBlasterException
  { 
    RequestContainer cont = new RequestContainer();
    cont.method = MethodName.PUBLISH;
    cont.msgUnitArr = msgUnitArr;
    try
    { rb.writeNext(cont);
    }
    catch(IOException ex) {
       throw new XmlBlasterException(glob, ErrorCode.INTERNAL_UNKNOWN, ME, cont.method + " invocation: " + ex.toString());
    }
    return dummyPubRetQosArr;
  }

  /**
   * storing erase request
   */
  public EraseReturnQos[] erase(String xmlKey, String qos) throws XmlBlasterException
  { 
    RequestContainer cont = new RequestContainer();
    cont.method = MethodName.ERASE;
    cont.xmlKey = xmlKey;
    cont.xmlQos = qos;
    try
    { rb.writeNext(cont);
    }
    catch(IOException ex) {
       throw new XmlBlasterException(glob, ErrorCode.INTERNAL_UNKNOWN, ME, cont.method + " invocation: " + ex.toString());
    }
    return dummyEraseReturnQosArr;
  }

  /**
   * storing get request
   */
  public MsgUnit[] get(String xmlKey, String qos) throws XmlBlasterException
  {
    RequestContainer cont = new RequestContainer();
    cont.method = MethodName.GET;
    cont.xmlKey = xmlKey;
    cont.xmlQos = qos;
    try
    { rb.writeNext(cont);
    }
    catch(IOException ex) {
       throw new XmlBlasterException(glob, ErrorCode.INTERNAL_UNKNOWN, ME, cont.method + " invocation: " + ex.toString());
    }
    return dummyMArr;
  }

  public boolean ping()
  { return false;
  }

   /**
    * storing update request
    */
   public String[] update(String cbSessionId, MsgUnit[] msgUnitArr) throws XmlBlasterException
   {
      RequestContainer cont = new RequestContainer();
      cont.method = MethodName.UPDATE;
      cont.cbSessionId = cbSessionId;
      cont.msgUnitArr = msgUnitArr;
      try {
         rb.writeNext(cont);
      }
      catch(IOException ex) {
         throw new XmlBlasterException(glob, ErrorCode.INTERNAL_UNKNOWN, ME, cont.method + " invocation: " + ex.toString());
      }
      String[] ret=new String[msgUnitArr.length];
      for (int i=0; i<ret.length; i++) ret[i] = "";
      return ret;
   }

  /**
   * storing updateOneway request
   */
  public void updateOneway(String cbSessionId, MsgUnit[] msgUnitArr) // throws XmlBlasterException
  {
    RequestContainer cont = new RequestContainer();
    cont.method = MethodName.UPDATE_ONEWAY;
    cont.cbSessionId = cbSessionId;
    cont.msgUnitArr = msgUnitArr;
    try
    { rb.writeNext(cont);
    }
    catch(IOException ex) {
       log.severe(cont.method + " invocation failed: " + ex.toString());
    }
    catch(XmlBlasterException ex) {
       log.severe(cont.method + " invocation failed: " + ex.toString());
    }
  }

   /**
   * deletes the file
   */
   public void destroy() { 
      rb.destroy();
   }

   public void shutdown() {
      rb.shutdown();
   }

}

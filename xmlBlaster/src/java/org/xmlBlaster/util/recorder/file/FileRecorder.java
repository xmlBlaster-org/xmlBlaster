/*------------------------------------------------------------------------------
Name:      FileRecorder.java
Project:   xmlBlaster.org
Comment:   Records requests of the client into a file, if the client is
           not able to connect to the xmlBlaster.
Author:    astelzl@avitech.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.recorder.file;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.plugin.I_Plugin;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.recorder.I_InvocationRecorder;
import org.xmlBlaster.engine.helper.MessageUnit;
import org.xmlBlaster.engine.helper.Constants;
import org.xmlBlaster.client.I_CallbackRaw;
import org.xmlBlaster.client.SubscribeRetQos;
import org.xmlBlaster.client.EraseRetQos;
import org.xmlBlaster.client.PublishRetQos;
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
 * @author ruff@swand.lake.de
 * @see testsuite.org.xmlBlaster.TestFailSave
 * @see classtest.InvocationRecorderTest
 */
public class FileRecorder implements I_Plugin, I_InvocationRecorder, I_CallbackRaw
{
   private final String ME = "FileRecorder";
   private Global glob;
   private LogChannel log;

   private FileIO rb;

   private String fileName;

   private I_XmlBlaster serverCallback = null;
   private I_CallbackRaw clientCallback = null;

   private final MessageUnit[] dummyMArr = new MessageUnit[0];
   private final String[] dummySArr = new String[0];
   private final String dummyS = "";
   private final PublishRetQos[] dummyPubRetQosArr = new PublishRetQos[0];
   private PublishRetQos dummyPubRet;
   private SubscribeRetQos dummySubRet;
   private final EraseRetQos[] dummyEraseRetQosArr = new EraseRetQos[0];

   private long maxEntries;

   /** Automatically write curr pos to file? */
   private boolean autoCommit = true; // only true is supported
  

   /**
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
   public void initialize(Global glob, String fn, long maxEntries, I_XmlBlaster serverCallback, I_CallbackRaw clientCallback) throws XmlBlasterException
   {
      this.glob = glob;
      this.serverCallback = serverCallback;
      this.clientCallback = clientCallback;
      this.log = glob.getLog("recorder");
      this.dummyPubRet = new PublishRetQos(glob, Constants.STATE_OK, Constants.INFO_QUEUED);
      this.dummySubRet = new SubscribeRetQos(glob, Constants.STATE_OK, Constants.INFO_QUEUED);

      fileName = createPathString(fn);

      boolean useSync = glob.getProperty().get("recorder.useSync", false);

      try {
         this.rb = new FileIO(glob, fileName, new MsgDataHandler(glob), maxEntries, useSync);
         if (rb.getNumUnread() > 0) {
            boolean destroyOld = glob.getProperty().get("recorder.destroyOld", false);
            if (destroyOld) {
               log.warn(ME, "Destroyed " + rb.getNumUnread() + " unprocessed tail back messages in '" + fileName + "' as requested with option 'recorder.destroyOld=true'.");
               rb.destroy();
               rb.initialize();
            }
            else {
               log.info(ME, "Found " + rb.getNumUnread() + " unprocessed tail back messages in '" + fileName + "'.");
            }
         }
         else {
            if (log.TRACE) log.trace(ME, "Using persistence file '" + fileName + "' for tail back messages.");
         }
      }
      catch(IOException ex) {
         log.error(ME,"Error at creation of RecordBuffer. It is not possible to buffer any messages: " + ex.toString());
         throw new XmlBlasterException(ME, "Initializing FileRecorder failed: Error at creation of RecordBuffer. It is not possible to buffer any messages: " + ex.toString());
      }
      if (maxEntries < 0)
         log.info(ME, "FileRecorder is ready, unlimited tail back messages are stored in '" + fileName + "'");
      else
         log.info(ME, "FileRecorder is ready, max=" + maxEntries + " tail back messages are stored in '" + fileName + "'");
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
    * This method is called by the PluginManager.
    * <p />
    * @see org.xmlBlaster.util.plugin.I_Plugin#init(Global,String[])
    */
   public void init(org.xmlBlaster.util.Global glob, String[] options) throws XmlBlasterException {
      // see ../demo/ContentLenFilter.java for an example
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
    *        ONOVERFLOW_BLOCK = "block", ONOVERFLOW_DEADLETTER = "deadLetter",
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
         log.trace(ME, "Setting onOverflow mode to exception"); // default
      }
      else
         log.warn(ME, "Ignoring unknown onOverflow mode '" + mode + "', using default mode 'exception'."); // default
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
      log.info(ME, "Invoking pullback(startDate=" + startDate + ", endDate=" + endDate + ", motionFactor=" + motionFactor + ") numUnread=" + getNumUnread());

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
         log.warn(ME + ".NoInvoc", "Sorry, no invocations found, queue is empty or your start date is to late");
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
               { Thread.currentThread().sleep(originalElapsed - actualElapsed);                } 
               catch(InterruptedException e) 
               { log.warn(ME, "Thread sleep got interrupted, this invocation is not in sync");
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
      log.info(ME, "Pullback of " + (numAtBeginning-getNumUnread()) + " messages done - elapsed " +
            org.jutils.time.TimeHelper.millisToNice(elaps) +
            " average rate was " + (numAtBeginning*1000L/elaps) + 
            " msg/sec, numUnread=" + getNumUnread());
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

      log.info(ME, "Invoking pullback(msgPerSec=" + msgPerSec + ") numUnread=" + getNumUnread());

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
                     log.info(ME, "Pullback of " + (numAtBeginning-getNumUnread()) +
                         " messages done - elapsed " +
                         org.jutils.time.TimeHelper.millisToNice(elaps) +
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
                     log.warn(ME, text);
                  }
                  else {
                     text = "Playback of tail back messages failed, " + getNumUnread() + " messages are in queue, " + localCount + " are lost, check '" + fileName + "': " + e.toString();
                     log.error(ME, text);
                  }
                  throw new XmlBlasterException(ME, text);
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
               Thread.currentThread().sleep(timeToUse - actualElapsed);
            } catch( InterruptedException i) {
               log.warn(ME, "Unexpected interrupt when sleeping for pullback");
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
      log.error(ME + ".NoImpl", "Sorry, playback() is not implemented, use pullback() or implement it");
      throw new XmlBlasterException(ME + ".NoImpl", "Sorry, only pullback is implemented");
  }

  //appropriate client function will be called depending on the request method
  private void callback(RequestContainer cont) throws XmlBlasterException { 
    if (serverCallback != null) 
    {
      // This should be faster then reflection
      if (cont.method.equals("publish")) 
      { serverCallback.publish(cont.msgUnit);
        return;
      }
      else if (cont.method.equals("get")) 
      { serverCallback.get(cont.xmlKey, cont.xmlQos);
        return;
      }
      else if (cont.method.equals("subscribe")) 
      { serverCallback.subscribe(cont.xmlKey, cont.xmlQos);
        return;
      }
      else if (cont.method.equals("unSubscribe")) 
      { serverCallback.unSubscribe(cont.xmlKey, cont.xmlQos);
        return;
      }
      else if (cont.method.equals("publishOneway")) 
      { serverCallback.publishOneway(cont.msgUnitArr);
        return;
      }
      else if (cont.method.equals("publishArr")) 
      { serverCallback.publishArr(cont.msgUnitArr);
        return;
      }
      else if (cont.method.equals("erase")) 
      { serverCallback.erase(cont.xmlKey, cont.xmlQos);
        return;
      }
    }

    if (clientCallback != null) 
    {
      // This should be faster then reflection
      if (cont.method.equals("update")) 
      {
        clientCallback.update(cont.cbSessionId, cont.msgUnitArr);
        return;
      }
      else if (cont.method.equals("updateOneway")) 
      {
        clientCallback.updateOneway(cont.cbSessionId, cont.msgUnitArr);
        return;
      }
    }

    log.error(ME, "Internal error: Method '" + cont.method + "' is unknown");
    throw new XmlBlasterException(ME, "Internal error: Method '" + cont.method + "' is unknown");
  }

  /**
   * storing subscribe request
   */
  public SubscribeRetQos subscribe(String xmlKey, String qos) throws XmlBlasterException
  {
    RequestContainer cont = new RequestContainer();
    cont.method = "subscribe";
    cont.xmlKey = xmlKey;
    cont.xmlQos = qos;
    try
    { rb.writeNext(cont);
    }
    catch(IOException ex) {
       throw new XmlBlasterException(ME, cont.method + " invocation: " + ex.toString());
    }
    return dummySubRet;
  }

  /**
   * storing unSubscribe request
   */
  public void unSubscribe(String xmlKey, String qos) throws XmlBlasterException
  {
    RequestContainer cont = new RequestContainer();
    cont.method = "unSubscribe";
    cont.xmlKey = xmlKey;
    cont.xmlQos = qos;
    try
    { rb.writeNext(cont);
    }
    catch(IOException ex) {
       throw new XmlBlasterException(ME, cont.method + " invocation: " + ex.toString());
    }
  }

  /**
   * storing publish request
   */
  public PublishRetQos publish(MessageUnit msgUnit) throws XmlBlasterException
  {
    RequestContainer cont = new RequestContainer();
    cont.method = "publish";
    cont.msgUnit = msgUnit;
    try
    { rb.writeNext(cont);
    }
    catch(IOException ex) {
       throw new XmlBlasterException(ME, cont.method + " invocation: " + ex.toString());
    }
    return dummyPubRet;
  }

  /**
   * storing publishOneway request
   */
  public void publishOneway(MessageUnit[] msgUnitArr) throws XmlBlasterException
  { 
    RequestContainer cont = new RequestContainer();
    cont.method = "publishOneway";
    cont.msgUnitArr = msgUnitArr;
    try
    { rb.writeNext(cont);
    }
    catch(IOException ex) {
       log.error(ME, cont.method + " invocation failed: " + ex.toString());
    }
  }

  /**
   * storing publishArr request
   */
  public PublishRetQos[] publishArr(MessageUnit[] msgUnitArr) throws XmlBlasterException
  { 
    RequestContainer cont = new RequestContainer();
    cont.method = "publishArr";
    cont.msgUnitArr = msgUnitArr;
    try
    { rb.writeNext(cont);
    }
    catch(IOException ex) {
       throw new XmlBlasterException(ME, cont.method + " invocation: " + ex.toString());
    }
    return dummyPubRetQosArr;
  }

  /**
   * storing erase request
   */
  public EraseRetQos[] erase(String xmlKey, String qos) throws XmlBlasterException
  { 
    RequestContainer cont = new RequestContainer();
    cont.method = "erase";
    cont.xmlKey = xmlKey;
    cont.xmlQos = qos;
    try
    { rb.writeNext(cont);
    }
    catch(IOException ex) {
       throw new XmlBlasterException(ME, cont.method + " invocation: " + ex.toString());
    }
    return dummyEraseRetQosArr;
  }

  /**
   * storing get request
   */
  public MessageUnit[] get(String xmlKey, String qos) throws XmlBlasterException
  {
    RequestContainer cont = new RequestContainer();
    cont.method = "get";
    cont.xmlKey = xmlKey;
    cont.xmlQos = qos;
    try
    { rb.writeNext(cont);
    }
    catch(IOException ex) {
       throw new XmlBlasterException(ME, cont.method + " invocation: " + ex.toString());
    }
    return dummyMArr;
  }

  public boolean ping()
  { return false;
  }

   /**
    * storing update request
    */
   public String[] update(String cbSessionId, MessageUnit[] msgUnitArr) throws XmlBlasterException
   {
      RequestContainer cont = new RequestContainer();
      cont.method = "update";
      cont.cbSessionId = cbSessionId;
      cont.msgUnitArr = msgUnitArr;
      try {
         rb.writeNext(cont);
      }
      catch(IOException ex) {
         throw new XmlBlasterException(ME, cont.method + " invocation: " + ex.toString());
      }
      String[] ret=new String[msgUnitArr.length];
      for (int i=0; i<ret.length; i++) ret[i] = "";
      return ret;
   }

  /**
   * storing updateOneway request
   */
  public void updateOneway(String cbSessionId, MessageUnit[] msgUnitArr) // throws XmlBlasterException
  {
    RequestContainer cont = new RequestContainer();
    cont.method = "updateOneway";
    cont.cbSessionId = cbSessionId;
    cont.msgUnitArr = msgUnitArr;
    try
    { rb.writeNext(cont);
    }
    catch(IOException ex) {
       log.error(ME, cont.method + " invocation failed: " + ex.toString());
    }
    catch(XmlBlasterException ex) {
       log.error(ME, cont.method + " invocation failed: " + ex.toString());
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

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
import org.xmlBlaster.util.I_Plugin;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.recorder.I_InvocationRecorder;
import org.xmlBlaster.engine.helper.MessageUnit;
import org.xmlBlaster.engine.helper.Constants;
import org.xmlBlaster.client.I_CallbackRaw;
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
   private LogChannel log;

   private FileIO rb;

   private String fileName;

   private I_XmlBlaster serverCallback = null;
   private I_CallbackRaw clientCallback = null;

   private MessageUnit[] dummyMArr = new MessageUnit[0];
   private String[] dummySArr = new String[0];
   private String dummyS = "";

   private long maxEntries;

   private boolean autoCommit = true;
  

   /**
   * maxEntries will not be used as the filesize can grow at will
   * File name is:
   * <pre>
   *   Persistence.Path=${user.home}${file.separator}tmp
   *   recorder.path=${Persistence.Path}${file.separator}fileRecorder
   *
   *   The file name is
   *       tailback-${cluster.node.id}.frc
   *
   *   For example:
   *      /home/michelle/tmp/fileRecorder/tailback-heron.frc
   * </pre>
   */
   public void initialize(Global glob, long maxEntries, I_XmlBlaster serverCallback, I_CallbackRaw clientCallback) throws XmlBlasterException
   {
      this.serverCallback = serverCallback;
      this.clientCallback = clientCallback;
      this.log = glob.getLog("recorder");

      createPath();

      boolean useSync = glob.getProperty().get("recorder.useSync", false);

      try {
         this.rb = new FileIO(fileName, new MsgDataHandler(glob), maxEntries, useSync);
         if (rb.getNumUnread() > 0) {
            boolean destroyOld = glob.getProperty().get("recorder.detroyOld", false);
            if (destroyOld) {
               log.warn(ME, "Destroyed " + rb.getNumUnread() + " unprocessed tail back messages in '" + fileName + "'.");
               rb.destroy();
               rb.initialize();
            }
            else {
               log.warn(ME, "Recovering " + rb.getNumUnread() + " unprocessed tail back messages from '" + fileName + "'.");
            }
         }
         else {
            log.info(ME, "Using persistence file '" + fileName + "' for tail back messages.");
         }
      }
      catch(IOException ex) {
         log.error(ME,"Error at creation of RecordBuffer. It is not possible to buffer any messages: " + ex.toString());
         throw new XmlBlasterException(ME, "Initializing FileRecorder failed: Error at creation of RecordBuffer. It is not possible to buffer any messages: " + ex.toString());
      }
      log.info(ME, "FileRecorder is ready, tail back messages are stored in '" + fileName + "'");
   }

   private String createPath() {
      fileName = glob.getProperty().get("recorder.path", (String)null);
      if (fileName == null) {
         fileName = glob.getProperty().get("recorder.path["+glob.getId()+"]", (String)null);
      }
      if (fileName == null) {
         fileName = glob.getProperty().get("Persistence.Path", System.getProperty("user.home") + System.getProperty("file.separator") + "tmp");
         fileName += System.getProperty("file.separator") + "fileRecorder";
      }
      fileName += System.getProperty("file.separator") + "tailback-" + glob.getStrippedId() + ".frc";
      File f = new File(filename);
      f.mkdirs();
   }

   /**
    * This method is called by the PluginManager.
    * <p />
    * @see org.xmlBlaster.util.I_Plugin#init(Global,String[])
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
    log.info(ME, "Invoking pullback(startDate=" + startDate + ", endDate=" + endDate + ", motionFactor=" + motionFactor + ")");

    RequestContainer cont = null;
    while(rb.getNumUnread() > 0) 
    { // find the start node ...
      try
      { cont = (RequestContainer)rb.readNext(autoCommit);
      }
      catch(IOException ex){}
      if (cont == null)
        break;
      if (startDate == 0L || cont.timestamp >= startDate)
        break;
    }
 
    if (cont == null) 
    { log.warn(ME + ".NoInvoc", "Sorry, no invocations found, queue is empty or your start date is to late");
      throw new XmlBlasterException(ME + ".NoInvoc", "Sorry, no invocations found, queue is empty or your start date is to late");
    }

    long startTime = cont.timestamp;
    long playbackStart = System.currentTimeMillis();

    while(cont != null) 
    {
      if (endDate != 0 && cont.timestamp > endDate) // break if the end date is reached
        break;

      if (motionFactor == 0.)
        callback(cont);
      else 
      {
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
      try
      { cont = (RequestContainer)rb.readNext(autoCommit);
      }
      catch(IOException ex){}
    }
  }

   /**
    * How many messages are silently lost in 'discard' or 'discardOldest' mode?
    */
   public long getNumLost()
   {
      return rb.getNumLost();
   }

  /**
   * Not implemented yet
   */
  public void playback(long startDate, long endDate, double motionFactor) throws XmlBlasterException
  { //Has to be implemented. Look at InvocationRecorder for further information
  }

  //appropriate client function will be called depending on the request method
  private void callback(RequestContainer cont) throws XmlBlasterException
  { 
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
  public String subscribe(String xmlKey, String qos) throws XmlBlasterException
  {
    RequestContainer cont = new RequestContainer();
    cont.method = "subscribe";
    cont.xmlKey = xmlKey;
    cont.xmlQos = qos;
    try
    { rb.writeNext(cont);
    }
    catch(IOException ex)
    { throw new XmlBlasterException(ME,ex.toString());
    }
    return dummyS;
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
    catch(IOException ex)
    { throw new XmlBlasterException(ME,ex.toString());
    }
  }

  /**
   * storing publish request
   */
  public String publish(MessageUnit msgUnit) throws XmlBlasterException
  {
    RequestContainer cont = new RequestContainer();
    cont.method = "publish";
    cont.msgUnit = msgUnit;
    try
    { rb.writeNext(cont);
    }
    catch(IOException ex)
    { throw new XmlBlasterException(ME,ex.toString());
    }
    return dummyS;
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
    catch(IOException ex)
    { throw new XmlBlasterException(ME,ex.toString());
    }
  }

  /**
   * storing publishArr request
   */
  public String[] publishArr(MessageUnit[] msgUnitArr) throws XmlBlasterException
  { 
    RequestContainer cont = new RequestContainer();
    cont.method = "publishArr";
    cont.msgUnitArr = msgUnitArr;
    try
    { rb.writeNext(cont);
    }
    catch(IOException ex)
    { throw new XmlBlasterException(ME,ex.toString());
    }
    return dummySArr;
  }

  /**
   * storing erase request
   */
  public String[] erase(String xmlKey, String qos) throws XmlBlasterException
  { 
    RequestContainer cont = new RequestContainer();
    cont.method = "erase";
    cont.xmlKey = xmlKey;
    cont.xmlQos = qos;
    try
    { rb.writeNext(cont);
    }
    catch(IOException ex)
    { throw new XmlBlasterException(ME,ex.toString());
    }
    return dummySArr;
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
    catch(IOException ex)
    { throw new XmlBlasterException(ME,ex.toString());
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
         throw new XmlBlasterException(ME,ex.toString());
      }
      String[] ret=new String[msgUnitArr.length];
      for (int i=0; i<ret.length; i++) ret[i] = "";
      return ret;
   }

  /**
   * storing updateOneway request
   */
  public void updateOneway(String cbSessionId, MessageUnit[] msgUnitArr)
  {
    RequestContainer cont = new RequestContainer();
    cont.method = "updateOneway";
    cont.cbSessionId = cbSessionId;
    cont.msgUnitArr = msgUnitArr;
    try
    { rb.writeNext(cont);
    }
    catch(Exception ex)
    { log.error(ME,"Can't push updateOneway(): "+ex.toString());
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

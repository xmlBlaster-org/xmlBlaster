/*------------------------------------------------------------------------------
Name:      SessionInfo.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling the Client data
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.authentication;

import org.xmlBlaster.engine.Global;
import org.xmlBlaster.engine.queue.MsgQueue;
import org.xmlBlaster.engine.queue.SessionMsgQueue;
import org.xmlBlaster.engine.queue.MsgQueueEntry;
import org.xmlBlaster.engine.helper.MessageUnit;
import org.xmlBlaster.authentication.SubjectInfo;
import org.xmlBlaster.authentication.plugins.I_Session;
import org.xmlBlaster.util.Timestamp;
import org.xmlBlaster.util.Timeout;
import org.xmlBlaster.util.I_Timeout;
import org.xmlBlaster.util.ConnectQos;
import org.xmlBlaster.util.DisconnectQos;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.Log;



/**
 * SessionInfo stores all known session data about a client.
 * <p />
 * The driver supporting the desired Callback protocol (CORBA/EMAIL/HTTP)
 * is instantiated here.<br />
 * Note that only CORBA is supported in this version.<br />
 * To add a new driver protocol, you only need to implement the empty
 * CallbackEmailDriver.java or any other protocol.
 * <p />
 * It also contains a message queue, where messages are stored
 * until they are delivered at the next login of this client.
 *
 * @author $Author: ruff $
 */
public class SessionInfo implements I_Timeout
{
   public static long sentMessages = 0L;
   private String ME = "SessionInfo";
   private SubjectInfo subjectInfo = null; // all client informations
   private I_Session securityCtx = null;
   private static long instanceCounter = 0L;
   private long instanceId = 0L;
   private ConnectQos connectQos;
   private Timeout expiryTimer;
   private Timestamp timerKey = null;
   private Global glob;

   /**
    * All MessageUnit which shall be delivered to the current session of the client
    * are queued here to be ready to deliver. 
    * <p />
    * Node objects = MsgQueueEntry
    */
   private MsgQueue sessionQueue;


   /**
    * Create this instance when a client did a login.
    * <p />
    * @param subjectInfo the SubjectInfo with the login informations for this client
    */
   public SessionInfo(SubjectInfo subjectInfo, I_Session securityCtx, ConnectQos connectQos, Global glob)
          throws XmlBlasterException
   {
      if (securityCtx==null) {
         String tmp = "SessionInfo(securityCtx==null); A correct security manager must be set.";
         Log.error(ME+".illegalArgument", tmp);
         throw new XmlBlasterException(ME+".illegalArgument", tmp);
      }
      
      subjectInfo.checkNumberOfSessions(connectQos);

      synchronized (SessionInfo.class) {
         instanceId = instanceCounter;
         instanceCounter++;
      }
      if (Log.CALL) Log.call(ME, "Creating new SessionInfo " + instanceId + ": " + subjectInfo.toString());
      this.glob = glob;
      this.subjectInfo = subjectInfo;
      this.securityCtx = securityCtx;
      this.connectQos = connectQos;
      this.sessionQueue = new SessionMsgQueue("session:"+securityCtx.getSessionId(), this, connectQos.getSessionCbQueueProperty(), glob);
      this.expiryTimer = glob.getSessionTimer();
      if (connectQos.getSessionTimeout() > 0L) {
         Log.info(ME, "Setting expiry timer for " + getLoginName() + " to " + connectQos.getSessionTimeout() + " msec");
         timerKey = this.expiryTimer.addTimeoutListener(this, connectQos.getSessionTimeout(), null);
      }
      else
         Log.info(ME, "Session lasts forever, requested expiry timer was 0");
   }

   public void finalize()
   {
      if (timerKey != null) {
         this.expiryTimer.removeTimeoutListener(timerKey);
         timerKey = null;
      }

      if (Log.TRACE) Log.trace(ME, "finalize - garbage collected " + getSessionId());
   }

   public void shutdown()
   {
      if (Log.CALL) Log.call(ME, "shutdown() of session " + getSessionId());
      if (timerKey != null) {
         this.expiryTimer.removeTimeoutListener(timerKey);
         timerKey = null;
      }
      this.sessionQueue.shutdown();
      this.sessionQueue = null;
      this.subjectInfo = null;
      // this.securityCtx = null; We need it in finalize() getSessionId()
      this.connectQos = null;
      this.expiryTimer = null;
   }

   /**
    * Call this to reactivate the session expiry to full value
    */
   public final void refreshSession() throws XmlBlasterException
   {
      if (connectQos.getSessionTimeout() > 0L) {
         if (timerKey == null) {
            timerKey = this.expiryTimer.addTimeoutListener(this, connectQos.getSessionTimeout(), null);
         }
         else {
            //Log.info(ME, "Refreshing expiry timer for " + getLoginName() + " to " + connectQos.getSessionTimeout() + " msec");
            timerKey = this.expiryTimer.refreshTimeoutListener(timerKey, connectQos.getSessionTimeout());
         }
      }
   }

   /**
    * We are notified when this session expires. 
    * @param userData You get bounced back your userData which you passed
    *                 with Timeout.addTimeoutListener()
    */
   public final void timeout(Object userData)
   {
      synchronized (this) {
         timerKey = null;
         Log.warn(ME, "Session timeout for " + getLoginName() + " occurred, session '" + getSessionId() + "' is expired, autologout");
         DisconnectQos qos = new DisconnectQos();
         qos.deleteSubjectQueue(true);
         try {
            glob.getAuthenticate().disconnect(getSessionId(), qos.toXml());
         } catch (XmlBlasterException e) {
            Log.error(ME, "Internal problem with disconnect: " + e.toString());
         }
      }
   }

   /**
    */
   public final void queueMessage(MsgQueueEntry entry) throws XmlBlasterException
   {
      if (Log.CALL) Log.call(ME, "Session of client [" + getLoginName() + "] queing message");
      if (entry == null) {
         Log.error(ME+".Internal", "Can't queue null message");
         throw new XmlBlasterException(ME+".Internal", "Can't queue null message");
      }

      sessionQueue.putMsg(entry);
   }


   /**
    * This is the unique identifier of the session
    * <p />
    * @return sessionId
    */
   public final String getUniqueKey()
   {
      return getSessionId();
   }

   public ConnectQos getConnectQos()
   {
      return this.connectQos;
   }


   /**
    * Access the unique login name of a client.
    * <br />
    * @return loginName
    */
   public final String getLoginName()
   {
      return subjectInfo.getLoginName();
   }


   /**
    * Accessing the SubjectInfo object
    * <p />
    * @return SubjectInfo
    */
   public final SubjectInfo getSubjectInfo()
   {
      return subjectInfo;
   }

   public String getSessionId()
   {
      return this.securityCtx.getSessionId();
   }

   public I_Session getSecuritySession() {
      return securityCtx;
   }

   public void setSecuritySession(I_Session ctx) {
      this.securityCtx = ctx;
   }

   /**
    * This queue holds all messages which where addressed to this session
    * @return never null
    */
   public MsgQueue getSessionQueue() {
      return sessionQueue;
   }

   /**
    * The unique login name.
    * <p />
    * @return the loginName
    */
   public final String toString()
   {
      return getLoginName();
   }


   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * @return internal state of SessionInfo as a XML ASCII string
    */
   public final String toXml() throws XmlBlasterException
   {
      return toXml((String)null);
   }


   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * @param extraOffset indenting of tags for nice output
    * @return internal state of SessionInfo as a XML ASCII string
    */
   public final String toXml(String extraOffset) throws XmlBlasterException
   {
      StringBuffer sb = new StringBuffer(256);
      String offset = "\n   ";
      if (extraOffset == null) extraOffset = "";
      offset += extraOffset;

      sb.append(offset).append("<SessionInfo id='").append(instanceId).append("' sessionId='").append(getUniqueKey());
      if (connectQos != null) {
         sb.append("'>");
         sb.append(connectQos.toXml(extraOffset+"   "));
         sb.append(offset).append("</SessionInfo>");
      }
      else
         sb.append(offset).append("'/>");

      return sb.toString();
   }
}

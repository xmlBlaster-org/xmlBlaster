/*------------------------------------------------------------------------------
Name:      AvailabilityChecker.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling the Client data
Author:    xmlBlaster@marcelruff.info
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.def.MethodName;
import org.xmlBlaster.engine.runlevel.I_RunlevelListener;
import org.xmlBlaster.engine.runlevel.RunlevelManager;
import org.xmlBlaster.util.SessionName;


/**
 * This checks depending on the run level to accept or deny messages from outside. 
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>
 */
public final class AvailabilityChecker implements I_RunlevelListener
{
   private String ME = "AvailabilityChecker";
   private final Global glob;
   private final LogChannel log;
   private int currRunlevel = 0;
   private boolean startup = true;

   /**
    * Ctor
    * @param glob
    */
   public AvailabilityChecker(Global glob) {
      this.glob = glob;
      this.log = glob.getLog("core");
      glob.getRunlevelManager().addRunlevelListener(this);
   }

   public void shutdown() {
      glob.getRunlevelManager().removeRunlevelListener(this);
   }

   /**
    * The extended check when the message is imported/decrypted. 
    * @param sessionName The client, null is OK
    * @param msgUnit The decrypted (readable) message received, null is OK
    * @param action The method name for logging, never null!
    */
   public void checkServerIsReady(SessionName sessionName, MsgUnit msgUnit, MethodName action) throws XmlBlasterException {

      if (this.currRunlevel > RunlevelManager.RUNLEVEL_CLEANUP) // 7 to 9
         return;

      boolean isInternalUser = (sessionName != null && sessionName.isInternalLoginName()) ? true : false;
      if (isInternalUser)
         return;

      if (!this.startup && action == MethodName.DISCONNECT)
         return; // to allow internal services to disconnect on shutdown

      if (this.startup && this.currRunlevel < RunlevelManager.RUNLEVEL_STANDBY) // 3
         return; // Accept internal calls and startup (for example from persistence recovery)
      
      // <= 6
      String post = (sessionName != null) ? " from '" + sessionName.getAbsoluteName() + "'" : "";
      String str = "The server is in run level " + RunlevelManager.toRunlevelStr(this.currRunlevel) +
                     " and not ready for " + action.toString() + post;

      if (this.log.TRACE) this.log.trace(ME, str);
      throw new XmlBlasterException(this.glob, ErrorCode.COMMUNICATION_NOCONNECTION_SERVERDENY, ME, str);
   }

   /**
    * Checks the given exception and depending on the current run level it is converted
    * into a communication exception (with the original embedded). 
    * This case usually happens if a long publishArr() is processed and the server is
    * simultaneously shutdown. The publishArr() is failing and will be reported to
    * the client as a communication exception.
    * @param action The method name for logging
    * @param origEx The internal cause during shutdown
    * @return XmlBlasterException With the probably corrected exception errorCode or the origEx
    */
   public XmlBlasterException checkException(MethodName action, Throwable origEx) {

      if (this.currRunlevel <= RunlevelManager.RUNLEVEL_CLEANUP) { // 6

         if (origEx instanceof XmlBlasterException) {
            XmlBlasterException e = (XmlBlasterException)origEx;
            if (e.isCommunication()) return e; // Is already how we want it
         }

         this.log.warn(ME, "DEBUG ONLY: The server is in run level " + RunlevelManager.toRunlevelStr(this.currRunlevel) + " and not ready for " + action.toString() + 
            "(): " + origEx.toString());

         return new XmlBlasterException(this.glob, ErrorCode.COMMUNICATION_NOCONNECTION_SERVERDENY, ME,
               "The server is in run level " + RunlevelManager.toRunlevelStr(this.currRunlevel) + " and not ready for " + action.toString() + "()",
               origEx);
      }

      if (origEx instanceof XmlBlasterException) {
         XmlBlasterException e = (XmlBlasterException)origEx;
         if (e.isInternal()) log.error(ME, action.toString() + "() failed: " + e.getMessage());
         return e;
      }

      // Transform a Throwable to an XmlBlasterException ...
      log.error(ME, "Internal problem in " + action.toString() + "(): " + origEx.getMessage());
      ErrorCode code = ErrorCode.INTERNAL_UNKNOWN;
      if (action == MethodName.PUBLISH) code = ErrorCode.INTERNAL_PUBLISH;
      else if (action == MethodName.PUBLISH_ARR) code = ErrorCode.INTERNAL_PUBLISH_ARR;
      else if (action == MethodName.SUBSCRIBE) code = ErrorCode.INTERNAL_SUBSCRIBE;
      else if (action == MethodName.UNSUBSCRIBE) code = ErrorCode.INTERNAL_UNSUBSCRIBE;
      else if (action == MethodName.ERASE) code = ErrorCode.INTERNAL_ERASE;         
      origEx.printStackTrace();
      return XmlBlasterException.convert(glob, code, ME, action.toString(), origEx);
   }

   /**
    * A human readable name of the listener for logging.
    * <p />
    * Enforced by I_RunlevelListener
    */
   public String getName() {
      return ME;
   }

   /**
    * Invoked on run level change, see RunlevelManager.RUNLEVEL_HALTED and RunlevelManager.RUNLEVEL_RUNNING
    * <p />
    * Enforced by I_RunlevelListener
    */
   public void runlevelChange(int from, int to, boolean force) throws org.xmlBlaster.util.XmlBlasterException {
      this.currRunlevel = to;
      this.startup = to > from; 
   }
}

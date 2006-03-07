/*------------------------------------------------------------------------------
Name:      StatusConfiguration.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.dispatch.plugins.prio;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.StringPairTokenizer;
import org.xmlBlaster.util.def.PriorityEnum;
import org.xmlBlaster.util.dispatch.ConnectionStateEnum;

/**
 * Holding the configuration of a specific status. 
 * <p />
 * For example we hold such a subset of the configuration:<p />
 * <pre>
 *   &lt;onStatus oid='_bandwidth.status' content='64k' connectionState='polling' defaultAction='destroy'>
 *     &lt;action do='send'  ifPriority='7-9'/>
 *     &lt;action do='queue'  ifPriority='2-6'/>
 *   &lt;/onStatus>
 */
public final class StatusConfiguration
{
   private String ME = "StatusConfiguration";
   private static Logger log = Logger.getLogger(StatusConfiguration.class.getName());
   private final DispatchAction[] dispatchActionArr = new DispatchAction[PriorityEnum.MAX_PRIORITY.getInt()+1];
   private DispatchAction defaultDispatchAction;
   private String oid;
   private String content;
   private ConnectionStateEnum connectionState;

   /**
    * @exception IllegalArgumentException For invalid configuration
    */
   public StatusConfiguration(Global glob, String oid, String content, ConnectionStateEnum connectionState, DispatchAction defaultAction) {
      if (defaultAction == null) {
         throw new IllegalArgumentException("Missing defaultAction for connectionState=" + connectionState);
      }

      setOid(oid);
      setContent(content);
      setConnectionState(connectionState);
      this.defaultDispatchAction = defaultAction;
      check(connectionState, this.defaultDispatchAction);
   }

   private void check(ConnectionStateEnum connectionState, DispatchAction action) throws IllegalArgumentException {
      if (action.doSend() && connectionState != null &&
          connectionState != ConnectionStateEnum.UNDEF &&
          connectionState != ConnectionStateEnum.ALIVE ) {

         throw new IllegalArgumentException("Connection states which are not ALIVE may not have a SEND action, connectionState=" +
               connectionState.toString() + " action=" + this.defaultDispatchAction.toString());
      }
   }

   private void setOid(String oid) {
      this.oid = (oid == null) ? null : oid.trim();
   }

   /**
    * The message oid containing the status information (white spaces are trimmed). 
    */
   public String getOid() {
      return this.oid;
   }

   private void setContent(String content) {
      this.content = (content == null) ? null : content.trim();
   }

   /**
    * The content of the status message (white spaces are trimmed). 
    * This is the status of the connection.
    */
   public String getContent() {
      return this.content;
   }

   public void setConnectionState(ConnectionStateEnum connectionState) {
      this.connectionState = connectionState; 
   }

   public ConnectionStateEnum getConnectionState() {
      return this.connectionState;
   }

   public DispatchAction getDefaultDispatchAction() {
      return this.defaultDispatchAction;
   }

   /**
    * Checks if only "send" is configured (for performance tuning)
    */
   public boolean defaultActionOnly() {
      for (int i=0; i<dispatchActionArr.length; i++) {
         if (dispatchActionArr[i] != null) {
            if (!dispatchActionArr[i].defaultActionOnly()) {
               return false;
            }
         }
      }
      return true;
   }

   /**
    * @param priority If null, the whole range 0-9 is assumed
    */
   public void addDispatchAction(String priorityRange, DispatchAction action) {
      if (priorityRange == null || priorityRange.length() < 1) {
         priorityRange = "0-9";
         if (log.isLoggable(Level.FINE)) log.fine("Given priorityRange is empty, setting it to '" + priorityRange + "'");
      }
      priorityRange = priorityRange.trim();
      String[] lowerUpper = StringPairTokenizer.toArray(priorityRange, "- ");
      if (lowerUpper.length == 0) {
         throw new IllegalArgumentException(ME + ": Given priorityRange is empty, ignoring action=" + action.getAction());
      }

      check(getConnectionState(), action);

      PriorityEnum lower = PriorityEnum.parsePriority(lowerUpper[0]);
      PriorityEnum upper = lower;
      if (lowerUpper.length > 1) {
         upper = PriorityEnum.parsePriority(lowerUpper[1]);
      }

      if (lower.getInt() > upper.getInt()) { // swap if necessary
         PriorityEnum tmp = lower;
         lower = upper;
         upper = tmp;
      }

      for (int i=lower.getInt(); i<=upper.getInt(); i++) {
         if (dispatchActionArr[i] != null) {
            log.warning("Overwriting dispatch action=" + action.getAction() + " for priority=" +
                         PriorityEnum.toPriorityEnum(i).toString());
         }
         dispatchActionArr[i] = action;
      }
   }

   /**
    * @return The desired action for the given priority, is never null
    */
   public DispatchAction getDispatchAction(PriorityEnum priority) {
      DispatchAction action = dispatchActionArr[priority.getInt()];
      if (action == null) {
         return this.defaultDispatchAction;
      }
      return action;
   }

   public String toXml(String extraOffset) {
      StringBuffer sb = new StringBuffer();
      String offset = "\n ";
      if (extraOffset == null) extraOffset = "";
      offset += extraOffset;
      sb.append(offset).append("<onStatus");
      if (getOid() != null) {
         sb.append(" oid='").append(getOid()).append("'");
         sb.append(" content='").append(getContent()).append("'");
      }
      if (getConnectionState() != null) {
         sb.append(" connectionState='").append(getConnectionState()).append("'");
      }
      sb.append(" defaultAction='").append(defaultDispatchAction.getAction()).append("'>");
      for (int i=0; i<dispatchActionArr.length; i++) {
         if (dispatchActionArr[i] == null) {
            continue;
         }
         if (dispatchActionArr[i].equals(defaultDispatchAction)) {
            continue;
         }
         sb.append(offset).append(" <action do='");
         sb.append(dispatchActionArr[i].getAction());
         sb.append("' ifPriority='").append(i).append("'/>");
      }
      sb.append(offset).append("</onStatus>");
      return sb.toString();
   }
}

/*------------------------------------------------------------------------------
Name:      DispatchAction.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.dispatch.plugins.prio;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.StringPairTokenizer;

/**
 * This class holds all actions of one specific status. 
 * <p>
 * The StatusConfiguration instance holds zero to n instances of this class.
 * </p>
 * @see StatusConfiguration
 * @author xmlBlaster@marcelruff.info
 */
public final class DispatchAction
{
   private String action;
   public static final String SEND = "send";
   private boolean doSend = false;
   public static final String QUEUE = "queue";
   private boolean doQueue = false;
   public static final String DESTROY = "destroy";
   private boolean doDestroy = false;
   public static final String NOTIFY_SENDER = "notifySender";
   private boolean doNotifySender = false;

   /**
    * Create an immutable instance. 
    * @param action e.g. "send|queue"
    */
   public DispatchAction(Global glob, String action) {

      if (action == null) {
         throw new IllegalArgumentException("The given dispatch action is null");
      }
      action = action.trim();
      String[] arr = StringPairTokenizer.toArray(action, "|, ");
      if (arr.length < 1) {
         throw new IllegalArgumentException("The given dispatch action '" + action + "' is invalid");
      }                                                                                                                                           
      this.action = action;
      for (int i=0; i<arr.length; i++) {
         if (SEND.equals(arr[i])) {
            doSend = true;
         }
         else if (QUEUE.equals(arr[i])) {
            doQueue = true;
         }
         else if (DESTROY.equals(arr[i])) {
            doDestroy = true;
         }
         else if (NOTIFY_SENDER.equals(arr[i])) {
            doNotifySender = true;
         }
         else {
            throw new IllegalArgumentException("The given dispatcher action '" + arr[i] + "' is not supported.");
         }
      }

      // check
      int count = 0;
      if (doQueue) count++;
      if (doSend) count++;
      if (doDestroy) count++;
      if (count > 1) {
         throw new IllegalArgumentException("Only one of the actions 'queue|send|destroy' is possible, combinations make no sense");
      }
   }

   /**
    * Enforced by I_Plugin
    * @return The action string, e.g. "send|queue"
    */
   public final String getAction() { return this.action; }

   /**
    * @return Shall message be sent?
    */
   public boolean doSend() { return doSend; }

   /**
    * @return Shall message be queued?
    */
   public boolean doQueue() { return doQueue; }

   /**
    * @return Shall message be destroyed?
    */
   public boolean doDestroy() { return doDestroy; }

   /**
    * @return Shall message be destroyed?
    */
   public boolean doNotifySender() { return doNotifySender; }

   /**
    * Check if only default action is wanted (for performance)
    */
   public boolean defaultActionOnly() {
      if (doSend() && !doNotifySender()) {
         return true;
      }
      return false;
   }

   public boolean equals(DispatchAction other) {
      return action.equals(other.getAction());
   }

   public String toString() {
      return action;
   }

   public String toXml(String extraOffset) {
      StringBuffer sb = new StringBuffer();
      String offset = "\n  ";
      if (extraOffset == null) extraOffset = "";
      offset += extraOffset;
      sb.append(offset).append("<action do='").append(action).append("'/>");
      return sb.toString();
   }
}

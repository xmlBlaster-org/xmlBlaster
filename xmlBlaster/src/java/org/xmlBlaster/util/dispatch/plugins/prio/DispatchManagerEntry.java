/*------------------------------------------------------------------------------
Name:      DispatchManagerEntry.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.dispatch.plugins.prio;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.dispatch.DispatchManager;
import org.xmlBlaster.util.dispatch.ConnectionStateEnum;
import org.xmlBlaster.util.queue.I_Queue;

/**
 * Holds a dispatchManager and a plugin holdback queue. 
 * <p>
 * The plugin is used for many DispatchManager instances. If
 * the plugin needs to hold back a message it is put into the holdback queue.
 * When the connection is fine again, the holdback queue is flushed to the
 * real queue of the DispatchManager.
 * </p>
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/dispatch.control.plugin.html" target="others">the dispatch.control.plugin requirement</a>
 * @author xmlBlaster@marcelruff.info
 */
public final class DispatchManagerEntry
{
   private String ME = "DispatchManagerEntry";
   private final DispatchManager dispatchManager;
   private I_Queue holdbackQueue;
   /** This is the configuration for the current connection state of the dispatcher framework or null: */
   private StatusConfiguration currConnectionStateConfiguration;
   private ConnectionStateEnum currConnectionState;


   public DispatchManagerEntry(DispatchManager dispatchManager) {
      this.dispatchManager = dispatchManager;
   }

   public DispatchManager getDispatchManager() {
      return this.dispatchManager;
   }

   public void setCurrConnectionStateConfiguration(StatusConfiguration conf) {
      this.currConnectionStateConfiguration = conf;
   }

   /**
    * This is the configuration for the current connection state of the dispatcher framework or null:
    */ 
   public StatusConfiguration getCurrConnectionStateConfiguration() {
      return this.currConnectionStateConfiguration;
   }

   /**
    * The current state of the dispatcher connection
    */
   public ConnectionStateEnum getCurrConnectionState() {
      return this.currConnectionState;
   }

   public void setCurrConnectionState(ConnectionStateEnum stateEnum) {
      this.currConnectionState = stateEnum;
   }

   /**
    * @return null if no queue was allocated yet (lazy instantiation)
    */
   public I_Queue getHoldbackQueue() {
      return this.holdbackQueue;
   }

   public void setHoldbackQueue(I_Queue holdbackQueue) {
      this.holdbackQueue = holdbackQueue;
   }
}

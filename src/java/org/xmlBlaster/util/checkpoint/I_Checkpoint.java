/*------------------------------------------------------------------------------
Name:      I_Checkpoint.java
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.checkpoint;

import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.SessionName;
import org.xmlBlaster.util.plugin.I_Plugin;

/**
 * Interface for plugins to handle messages passing checkpoints.
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>
 */
public interface I_Checkpoint extends I_Plugin, CheckpointMBean {
   //public static final int CP_PUBLISH_IN = ; // on arriving
   //public static final int CP_PUBLISH_REJECT = ; // on exception

   /**
    * A published messages is successfully processed by the core (publish or publishOneway)
    * For none oneway the ACK is now returned to the publisher.
    * Note: Internal and administrative messages are not reported
    */
   public static final int CP_PUBLISH_ACK = 0;
   /**
    * A messages is put to a clients callback queue
    */
   public static final int CP_UPDATE_QUEUE_ADD = 1;
   /**
    * A message was delivered to a client (update or updateOneway).
    * For none oneway messages after the client has returned its ACK.
    */
   public static final int CP_UPDATE_ACK = 2;
   public static final String[] CP_NAMES = { "publish.ack", "update.queue.add", "update.ack"};

   /**
    * A message is passing a checkpoint.
    * Note: This method may never throw any exceptions
    * @param checkpoint The checkpoint, e.g. CP_PUBLISH_ACK
    * @param destination Can be null
    * @param msgUnit The message processed
    * @param context Additional key values like { "subscriptionId", "__suvbId:2349", "comment", "blabla" }
    */
   void passingBy(int checkpoint, MsgUnit msgUnit, SessionName destination, String[] context);
}

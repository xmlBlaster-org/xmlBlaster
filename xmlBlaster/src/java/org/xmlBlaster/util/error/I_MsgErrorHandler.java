/*------------------------------------------------------------------------------
Name:      I_MsgErrorHandler.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.error;

import org.xmlBlaster.util.XmlBlasterException;

/**
 * You need to implement this interface to be notified on unrecoverable errors. 
 * <p>
 * For example called by dispatchManager if a message is lost or the queue overflows
 *
 * @author xmlBlaster@marcelruff.info
 */
public interface I_MsgErrorHandler
{
   /**
    * The final recovery, all informations necessary are transported in msgErrorInfo. 
    * <p>
    * This handler is called for example from the 'put' side of a queue if the queue is full
    * or from the 'take' side from the queue e.g. if DispatchManager exhausted to reconnect.
    * </p>
    * <p>
    * This method never throws an exception but handles the error itself.
    * </p>
    */
   public void handleError(I_MsgErrorInfo msgErrorInfo);

   /**
    * The final recovery, all informations necessary are transported in msgErrorInfo. 
    * <p>
    * This handler is called for example from the 'put' side of a queue if the queue is full
    * or from the 'take' side from the queue e.g. if DispatchManager exhausted to reconnect.
    * </p>
    * <p>
    * This method can throw an exception, the caller usually passes this back
    * the client code. 
    * </p>
    * @exception XmlBlasterException To throw an XmlBlasterException makes sense
    * if we are in sync mode and want to pass the exception back to the caller.
    */
   public void handleErrorSync(I_MsgErrorInfo msgErrorInfo) throws XmlBlasterException;
   
   public void shutdown();
}


/*------------------------------------------------------------------------------
Name:      I_InvocationRecorder.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Interface for storing tail back messages
Version:   $Id: I_InvocationRecorder.java,v 1.3 2002/06/01 12:22:58 ruff Exp $
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.recorder;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.client.I_CallbackRaw;
import org.xmlBlaster.client.protocol.I_XmlBlaster;
import org.xmlBlaster.engine.helper.MessageUnit;

/**
 * Interface for InvocationRecorder, forces the supported methods.
 * <p />
 * @see org.xmlBlaster.util.recorder.ram.RamRecorder
 * @see org.xmlBlaster.util.recorder.file.FileRecorder
 */
public interface I_InvocationRecorder extends I_XmlBlaster, I_CallbackRaw
{
   /**
    * Called by plugin manager. 
    * @param maxEntries The maximum number of invocations to store
    * @param serverCallback You need to implement I_XmlBlaster to receive the invocations on playback
    *                       null if you are not interested in those
    * @param clientCallback You need to implement I_CallbackRaw to receive the invocations on playback
    *                       null if you are not interested in those
    */
   public void initialize(Global glob, long maxEntries, I_XmlBlaster serverCallback,
                             I_CallbackRaw clientCallback) throws XmlBlasterException;

   /**
    * @param ONOVERFLOW_BLOCK = "block", ONOVERFLOW_DEADLETTER = "deadLetter",
    *        ONOVERFLOW_DISCARD = "discard", ONOVERFLOW_DISCARDOLDEST = "discardOldest",
    *        ONOVERFLOW_EXCEPTION = "exception"
    */
   public void setMode(String mode);

   /**
    * @return true of queue overflow
    */
   public boolean isFull() throws XmlBlasterException;

   /**
    * How many objects are in the queue. 
    * @return     The number of objects in this queue.
    */
   public long getNumUnread();

   /**
    * How many messages are silently lost in 'discard' or 'discardOldest' mode?
    */
   public long getNumLost();

   /**
    * Playback the stored messages, without removing them form the recorder. 
    * <p />
    * This you can use multiple times again. 
    * @param startDate Start date for playback, 0 means from the very start
    * @param endDate End date to stop playback, pass 0 to go to the very end
    * @param motionFactor for fast motion choose for example 4.0
    *        so four reals seconds are elapsing in one second.<br />
    *        For slow motion choose for example 0.5
    */
   public void playback(long startDate, long endDate, double motionFactor) throws XmlBlasterException;
   
   /**
    * Playback the stored messages, the are removed from the recorder after the callback. 
    * <p />
    * Every message is chronologically sent through the interface to the client.
    * @param startDate Start date for playback, 0 means from the very start
    * @param endDate End date to stop playback, pass 0 to go to the very end
    * @param motionFactor for fast motion choose for example 4.0
    *        so four reals seconds are elapsing in one second.<br />
    *        For slow motion choose for example 0.5
    *        0. does everything instantly.
    */
   public void pullback(long startDate, long endDate, double motionFactor) throws XmlBlasterException;

   /**
    * Reset the queue, throw all entries to garbage. 
    */
   public void destroy();

   /**
    * Close the queue, entries are still available (e.g. close the file handle).
    */
   public void shutdown();
}

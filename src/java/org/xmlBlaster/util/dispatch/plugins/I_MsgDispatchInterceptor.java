/*------------------------------------------------------------------------------
Name:      I_MsgDispatchInterceptor.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.dispatch.plugins;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.dispatch.DispatchManager;
import org.xmlBlaster.util.dispatch.I_ConnectionStatusListener;

import java.util.ArrayList;

/**
 * The Interface allows to control how messages are sent to the remote side. 
 * <p>
 * Plugins of this interface have only one instance per plugin-typeVersion for each Global scope
 * so you can look at it like a singleton.
 * </p>
 * @author xmlBlaster@marcelruff.info
 */
public interface I_MsgDispatchInterceptor extends I_ConnectionStatusListener
{
   //public void initClientAccess(Global glob, I_XmlBlasterAccess con, I_CallbackDriver driver) throws XmlBlasterException;

   /**
    * Is called once only after the instance is created. 
    * @param The configuration name under which we are loaded e.g. "Priority,1.0"
    */
   public void initialize(Global glob, String typeVersion) throws XmlBlasterException;

   /**
    * This is called once for each dispatch manager using this plugin. 
    */
   public void addDispatchManager(DispatchManager dispatchManager);

   /**
    * If there are new messages available in the queue, you get invoked
    * here and are asked if you want to start a worker thread (from the thread pool)
    * to start taking messages from the queue and send them over the remote connection.
    * <p/> 
    * Note: If the session cb is in polling and the callback queue contains entries and you
    * return true, the dispatchWorker thread will loop! In such a case check with
    * <pre>
    * if (dispatchManager.getDispatchConnectionsHandler().isPolling()) {
    *     return false;
    *  }
    * </pre>
    * @return true: create a worker thread to process messages from queue (it will call our getNextMessages() method
    *               where we can decide which messages it will process<br />
    *         false: abort, don't start worker thread
    */
   public boolean doActivate(DispatchManager dispatchManager);

   /**
    * If you returned true from doActivate() the worker thread will
    * ask us to retrieve the next messages from the queue (dispatchManager.getQueue()). 
    * <p>
    * This is where this plugin comes in action. The plugin may
    * filter the queue entries and for example only return high priority messages
    * </p>
    * <p>
    * Usually you take the message out of the queue and then invoke prepareMsgsFromQueue()
    * to filter expired messages away and do a shallow copy of the messages to avoid
    * that changes in the messages have impact on the original messages. See the following
    * example:
    * </p>
    * <pre>
    *  // take messages from queue (none blocking)
    *  // we take all messages with same priority as a bulk ...
    *  ArrayList entryList = dispatchManager.getQueue().peekSamePriority(-1);
    *
    *  // filter expired entries etc. ...
    *  // you should always call this method after taking messages from queue
    *  entryList = dispatchManager.prepareMsgsFromQueue(entryList);
    *
    *  // ... do plugin specific work ...
    *
    *  return entryList;
    * </pre>
    * @param pushEntries null: Take messages yourself from queue (async mode) <br />
    *                not null: Use messages pushed (sync mode) or messages from ErrorCode.COMMUNICATION*
    * @return An ArrayList containing the I_QueueEntry to send.<br />
    *         If list.size() == 0 the worker thread stops and does nothing<br />
    *         If list.size() > 0 the given messages are sent.
    *            In case of pushEntries>0 and ErrorCode.COMMUNICATION* if you return them they are send to error handler.
    * @exception If XmlBlasterException is thrown, dispatch of messages is stopped.
    *            Other exceptions will lead to giving up sending messages as configured with I_MsgErrorHandler,
    *            usually shutdown queue and sending dead messages.
    */
   public ArrayList handleNextMessages(DispatchManager dispatchManager, ArrayList pushEntries) throws XmlBlasterException;

   /**
    * Deregister the given dispatchManager
    */
   public void shutdown(DispatchManager dispatchManager) throws XmlBlasterException;

   /**
    * Shutdown the implementation, sync with data store
    * @param true: force shutdown, don't flush everything
    */ 
   public void shutdown() throws XmlBlasterException;

   /**
    * @return true if shutdown
    */
   public boolean isShutdown();

   /**
    * @return a human readable usage help string
    */
   public String usage();

   /**
    * @param extraOffset Indent the dump with given ASCII blanks
    * @return An xml encoded dump
    */
   public String toXml(String extraOffset);
}

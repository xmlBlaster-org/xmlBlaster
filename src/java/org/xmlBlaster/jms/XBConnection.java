/*------------------------------------------------------------------------------
Name:      XBConnection.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.jms;

import java.util.HashMap;
import java.util.Map;

import javax.jms.ConnectionConsumer;
import javax.jms.ConnectionMetaData;
import javax.jms.Destination;
import javax.jms.ExceptionListener;
import javax.jms.IllegalStateException;
import javax.jms.JMSException;
import javax.jms.Queue;
import javax.jms.QueueConnection;
import javax.jms.QueueSession;
import javax.jms.ServerSessionPool;
import javax.jms.Session;
import javax.jms.Topic;
import javax.jms.TopicConnection;
import javax.jms.TopicSession;

import java.util.logging.Logger;
import java.util.logging.Level;

import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;

/**
 * XBConnection holds the connections to xmlBlaster.Since this class serves as a 
 * factory for jms sessions, and since the mapping between sessions and connections
 * is: &lt;br/>
 * &lt;ul>
 *   &lt;li>&lt;b>xmlBlaster&lt;/b>: 1 connection -> 1 session&lt;/li>
 *   &lt;li>&lt;b>jms&lt;/b>: 1 connection -> n session&lt;/li>
 * &lt;/ul>
 * we need to map one jms connection (XBConnection) to multiple (n) xmlBlaster
 * connections (one for each session). Then again, each jms session would map to
 * one xmlBlaster session.
 *
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 * 
 */
public class XBConnection implements QueueConnection, TopicConnection, I_StatusChangeListener {

   private final static String ME = "XBConnection";
   private Global global;
   private static Logger log = Logger.getLogger(XBConnection.class.getName());
   private ExceptionListener exceptionListener;
   private ConnectionMetaData metaData;
   private ConnectQos connectQos;
   private Map sessionMap;
   private boolean running;
   /** if true, no invocation on this connection has been done. Used for setClientId check */
   private boolean stillVirgin = true;
   
   private boolean forQueues;
   private boolean closed;
   
   Object closeSync = new Object();
   
   /**
    * 
    * @param connectQos
    * @param metaData
    * @param forQueues true if the connection is used as a QueueConnection, false otherwise
    * @throws XmlBlasterException
    */
   XBConnection(ConnectQos connectQos, ConnectionMetaData metaData, boolean forQueues) throws XmlBlasterException {
      this.forQueues = forQueues;
      if (connectQos != null) {
         this.global = connectQos.getData().getGlobal();
         this.connectQos = connectQos;
      }
      else {
         this.global = new Global();
         this.connectQos = new ConnectQos(this.global);
      }

      if (this.connectQos.getData().getCurrentCallbackAddress().isDispatcherActive()) {
         log.warning("The dispatcher in the ConnectQos is active, it will be now disactivated");
         this.connectQos.getData().getCurrentCallbackAddress().setDispatcherActive(false);
      }
      
      this.metaData = metaData;
      this.sessionMap = new HashMap();
      if (log.isLoggable(Level.FINER)) 
         log.finer("constructor");
   }
   
   final void checkClosed() throws JMSException {
      if (this.closed)
         throw new IllegalStateException("No operation is permitted on the Connection since in state 'closed'");
   }
   
   /*
   private ConnectQos cloneConnectQos(ConnectQos qos) throws XmlBlasterException {
      Global global = qos.getData().getGlobal().getClone(null);
      ConnectQosSaxFactory factory = new ConnectQosSaxFactory(global);
      ConnectQos connQos = new ConnectQos(global, factory.readObject(qos.toXml()));
      return connQos;
   }
   */
   
   private synchronized void initSession(String methodName, XBSession session, boolean transacted, int ackMode) 
      throws JMSException {
      this.stillVirgin = false;
      if (log.isLoggable(Level.FINER)) 
         log.finer(methodName + " transacted='" + transacted + "' ackMode='" + ackMode + "'");
      if (transacted) 
         throw new XBException(ME, " '" + methodName + "' in transacted mode not implemented yet");
      try {
         session.setStatusChangeListener(this);
         String sessionName = session.connect();
         if (this.running) {
            session.activateDispatcher(true);
         }
         this.sessionMap.put(sessionName, session);
      }
      catch (XmlBlasterException ex) {
         throw new XBException(ex, ME + ".createSession");
      }
   }

   /**
    * It creates a session. The session is responsible of registering itself 
    * into this connection in the constructor and to deregister itself when
    * close is invoked. Registering is necessary since the connection is
    * stateful (running/stopped) and all sessions belonging to it must be
    * notified of such state changes. 
    */
   public Session createSession(boolean transacted, int ackMode)
      throws JMSException {
      checkClosed();
      // XBSession session = new XBSession(cloneConnectQos(this.connectQos), ackMode, transacted);
      XBSession session = new XBSession(this, ackMode, transacted);
      initSession("createSession", session, transacted, ackMode); 
      return session;
      /*
      try {
      }
      catch (XmlBlasterException ex) {
         throw new JMSException(ex.getMessage());
      }
      */
   }
      
   public TopicSession createTopicSession(boolean transacted, int ackMode)
      throws JMSException {
      checkClosed();
      if (this.forQueues) 
         throw new IllegalStateException(ME + ".createTopicSession", "this is a QueueConnection: use TopicConnection to invoke this method");
      // XBTopicSession session = new XBTopicSession(cloneConnectQos(this.connectQos), ackMode, transacted);
      XBTopicSession session = new XBTopicSession(this, ackMode, transacted);
      initSession("createTopicSession", session, transacted, ackMode); 
      return session;
      /*
      try {
      }
      catch (XmlBlasterException ex) {
         throw new JMSException(ex.getMessage());
      }
      */
   }

   public QueueSession createQueueSession(boolean transacted, int ackMode)
      throws JMSException {
      checkClosed();
      if (!this.forQueues) 
         throw new IllegalStateException(ME + ".createQueueSession", "this is a TopicConnection: use QueueConnection to invoke this method");
      // XBQueueSession session = new XBQueueSession(cloneConnectQos(this.connectQos), ackMode, transacted);
      XBQueueSession session = new XBQueueSession(this, ackMode, transacted);
      initSession("createQueueSession", session, transacted, ackMode); 
      return session;
      /*
      try {
      }
      catch (XmlBlasterException ex) {
         throw new JMSException(ex.getMessage());
      }
      */
   }

   /**
    * Disconnect all sessions administered by this connection.
    * @see javax.jms.Connection#close()
    */
   public synchronized void close() throws JMSException {
      if (this.closed)
         return;
      if (log.isLoggable(Level.FINER)) 
         log.finer("close");
      this.closed = true;
      try {
         if (this.global.getXmlBlasterAccess().isConnected())
            this.global.getXmlBlasterAccess().setCallbackDispatcherActive(false);
      }
      catch (XmlBlasterException ex) {
         throw new XBException(ex, "exception occured when trying to close the connection");
      }
      
      synchronized(this.closeSync) { // Note: this does not protect in case a newMessage
         // calls close itself (since in same thread sync will not do)
         JMSException ex = null;
         Object[] keys = this.sessionMap.keySet().toArray();
         for (int i=0; i < keys.length; i++) {
            // first deregister listener to avoid recursive deletion of entries in this map
            XBSession session = (XBSession)this.sessionMap.get(keys[i]);
            if (session != null) {
               try {
                  session.close();
               }
               catch (JMSException e) {
                  ex = e;
                  if (this.exceptionListener != null) this.exceptionListener.onException(e);
                  if (log.isLoggable(Level.FINE)) {
                     ex.printStackTrace();
                  }
               }
            }
         }
         this.sessionMap.clear();
         if (ex != null) {
            log.warning("close: exception occured when closing some of the sessions associated to this connection");
            throw ex;
         }
      }
   }

   public synchronized String getClientID() throws JMSException {
      checkClosed();
      return this.connectQos.getUserId();
   }

   public ExceptionListener getExceptionListener() throws JMSException {
      checkClosed();
      return this.exceptionListener;
   }

   public ConnectionMetaData getMetaData() throws JMSException {
      checkClosed();
      return this.metaData;
   }

   public synchronized void setClientID(String loginName) throws JMSException {
      if (log.isLoggable(Level.FINER)) 
         log.finer("setClientID '" + loginName + "'");
      if (!this.stillVirgin) {
         throw new IllegalStateException(ME + ".setClientID: the clientId cannot be set since you made already invocations on this connection");
      }
      String oldId = getClientID();
      if (oldId != null && oldId.length() > 0) {
         throw new IllegalStateException(ME + ".setClientID: the clientId cannot be set since the administrator has already set an id to this connection via the Connection Factory");
      }
      // TODO check if the userId is already connected. If yes, then an IllegalStateException must be thrown
      this.connectQos.getSecurityQos().setUserId(loginName);
   }

   public synchronized void setExceptionListener(ExceptionListener exeptionListener) throws JMSException {
      if (log.isLoggable(Level.FINER)) 
         log.finer("setExceptionListener");
      checkClosed();
      this.exceptionListener = exeptionListener;
   }

   /**
    * Activates the dispatcher of all sessions administered by this
    * connection.
    * @see javax.jms.Connection#start()
    */
   public synchronized void start() throws JMSException {
      checkClosed();
      startStop("start", true);
   }       
    
   private synchronized void startStop(String txt, boolean isStart) throws JMSException {
      if (log.isLoggable(Level.FINER)) 
         log.finer(txt);
      synchronized (this.closeSync) {
         this.stillVirgin = false;

         JMSException ex = null;
         Object[] keys = this.sessionMap.keySet().toArray();
         for (int i=0; i < keys.length; i++) {
            XBSession session = (XBSession)this.sessionMap.get(keys[i]);
            if (session != null) {
               try {
                  session.activateDispatcher(isStart);
               }
               catch (XmlBlasterException e) {
                  ex = new XBException(e, ME + "." + txt);
                  if (this.exceptionListener != null) 
                     this.exceptionListener.onException(ex);
                  if (log.isLoggable(Level.FINE)) {
                     e.printStackTrace();
                  }
               }
            }
         }
         this.running = isStart;
         if (ex != null) {
            log.warning(txt + ": exception occured when invoking activateDispatcher");
            throw ex;
         }
      }
   }

   /**
    * Disactivates the dispatchers of all sessions administered by this connection.
    * @see javax.jms.Connection#stop()
    */
   public synchronized void stop() throws JMSException {
      checkClosed();
      startStop("start", true);
   }

   // optional server side stuff not implemented here ...

   public void statusPostChanged(String id, int oldStatus, int newStatus) {
      if (log.isLoggable(Level.FINER)) 
         log.finer("statusPostChanged");
   }

   /**
    * Removes the session from the map to avoid future notification. This event comes
    * when the session closes.
    */
   public void statusPreChanged(String id, int oldStatus, int newStatus) {
      if (log.isLoggable(Level.FINER)) 
         log.finer("statusPreChanged");
      synchronized(this) {
         if (oldStatus == I_StatusChangeListener.RUNNING && newStatus == I_StatusChangeListener.CLOSED)
            this.sessionMap.remove(id);
      }
   }


   /**
    * @see javax.jms.QueueConnection#createConnectionConsumer(javax.jms.Queue, java.lang.String, javax.jms.ServerSessionPool, int)
    */
   public synchronized ConnectionConsumer createConnectionConsumer(
      Destination destination,
      String msgSelector,
      ServerSessionPool serverSessionPool,
      int maxMessages)
      throws JMSException {
      checkClosed();
      this.stillVirgin = false;
      return new XBConnectionConsumer(this);
      // throw new JMSException(ME + " 'createConnectionConsumer' not implemented yet");
   }

   /**
    * @see javax.jms.QueueConnection#createConnectionConsumer(javax.jms.Queue, java.lang.String, javax.jms.ServerSessionPool, int)
    */
   public ConnectionConsumer createConnectionConsumer(Topic topic, String msgSelector, ServerSessionPool serverSessionPool, int maxMessages)
      throws JMSException {
      return createConnectionConsumer((Destination)topic, msgSelector, serverSessionPool, maxMessages);
   }


   /**
    * @see javax.jms.QueueConnection#createConnectionConsumer(javax.jms.Queue, java.lang.String, javax.jms.ServerSessionPool, int)
    */
   public synchronized ConnectionConsumer createConnectionConsumer(Queue queue, String msgSelector, ServerSessionPool serverSessionPool, int maxMessages)
      throws JMSException {
      return createConnectionConsumer((Destination)queue, msgSelector, serverSessionPool, maxMessages);
   }

   /**
    * @see javax.jms.TopicConnection#createDurableConnectionConsumer(javax.jms.Topic, java.lang.String, java.lang.String, javax.jms.ServerSessionPool, int)
    */
   public synchronized ConnectionConsumer createDurableConnectionConsumer(
      Topic topic,
      String subscriptionName,
      String msgSelector,
      ServerSessionPool serverSessionPool,
      int maxMessages)
      throws JMSException {
      this.stillVirgin = false;
      throw new JMSException(ME + " 'createDurableConnectionConsumer' not implemented yet");
   }

   ConnectQos getConnectQos() {
      return this.connectQos;
   }

}

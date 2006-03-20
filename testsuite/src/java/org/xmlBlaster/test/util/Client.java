/*------------------------------------------------------------------------------
Name:      Client.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.test.util;

import java.util.ArrayList;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.client.key.EraseKey;
import org.xmlBlaster.client.key.PublishKey;
import org.xmlBlaster.client.key.SubscribeKey;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.qos.DisconnectQos;
import org.xmlBlaster.client.qos.EraseQos;
import org.xmlBlaster.client.qos.PublishQos;
import org.xmlBlaster.client.qos.SubscribeQos;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.SessionName;
import org.xmlBlaster.util.qos.TopicProperty;

public class Client implements I_Callback {
   private String ME = "Client-";
   private Global global;
   private static Logger log = Logger.getLogger(Client.class.getName());
   private I_XmlBlasterAccess accessor;
   private String name;
   private String publishOid;
   private String subscribeOid;
   private boolean consumable;
   private ArrayList responses; // object to notify about updates 
   private XmlBlasterException updateException;

   /**
    * A helper client to be used when testing 
    * @param global
    * @param name the name to be given to this instance
    * @param responses the ArrayList object to which to add an entry (the name) when an update 
    * happens. You can pass 'null' here and nobody will be notified.
    */   
   public Client(Global global, String name, ArrayList responses) {
      this.global = global.getClone(null);

      this.accessor = this.global.getXmlBlasterAccess();
      this.name = name;
      this.ME += this.name;
      this.responses = responses;
      if (log.isLoggable(Level.FINER)) log.finer("constructor");
   }

   /**
    * Initializes this client which either can be a publisher, a subscriber or both depending
    * on the parameters passed here.   
    * @param publishOid The oid to which to publish messages. If you pass 'null', then no message 
    *        will be published by this instance.
    * @param subscribeOid the oid to which to subscribe. If you pass null here no 
    *        subscription will be node. 
    * @param consumable if this flag is set to true, then the publishing oid will be
    *        consumable, otherwise it is a normal (non consumable) topic.
    * @param session the session of this instance as an integer (either positive or negative).
    * @throws XmlBlasterException
    */
   public void init(String publishOid, String subscribeOid, boolean consumable, int session) throws XmlBlasterException {
      if (log.isLoggable(Level.FINER)) log.finer("init");
      this.consumable = consumable;
      ConnectQos connectQos = new ConnectQos(this.global, name, "secret");
      if (session > 0) {
         SessionName sessionName = new SessionName(this.global, name + "/" + session);
         connectQos.setSessionName(sessionName);
      } 
      this.accessor.connect(connectQos, this);
      this.publishOid = publishOid;
      this.subscribeOid = subscribeOid;
      if (this.subscribeOid != null) {
         SubscribeQos subQos = new SubscribeQos(this.global);
         this.accessor.subscribe(new SubscribeKey(this.global, this.subscribeOid), subQos);
      }
   }

   /**
    * Publishes one entry on the oid specified by the init method. If you passed
    * null there, then you will get an exceptio here. This client just sends strings
    * @param content the content of the message to send (a string).
    * @throws XmlBlasterException
    */
   public void publish(String content) throws XmlBlasterException {
      if (log.isLoggable(Level.FINER)) log.finer("publish");
      if (this.publishOid == null)
         throw new XmlBlasterException(this.global, ErrorCode.USER_CLIENTCODE, ME, "no oid configured for publishing");
      if (content == null)
         throw new XmlBlasterException(this.global, ErrorCode.USER_CLIENTCODE, ME, "no content passed");
      
      PublishQos pubQos = new PublishQos(this.global);
      TopicProperty topicProp = new TopicProperty(this.global);
      topicProp.setMsgDistributor("ConsumableQueue,1.0");
      if (this.consumable) pubQos.setTopicProperty(topicProp);
      MsgUnit msgUnit = new MsgUnit(new PublishKey(this.global, this.publishOid), content, pubQos);
      this.accessor.publish(msgUnit);
   }

   /**
    * Disconnect the client from xmlBlaster with an optional previous deletion of the publishing topic.
    * @param doEraseTopic if true, the topic on which this client publishes is erased. If this
    *        client is not a publisher, then erase will not be done.
    * @throws XmlBlasterException
    */
   public void shutdown(boolean doEraseTopic) throws XmlBlasterException {
      if (log.isLoggable(Level.FINER)) log.finer("shutdown");
      if (this.publishOid != null && doEraseTopic) {
         this.accessor.erase(new EraseKey(this.global, this.publishOid), new EraseQos(this.global));
      }
      this.accessor.disconnect(new DisconnectQos(this.global));
   }

   /**
    * Sets an exception to be thrown the next time an update event comes. After
    * that it is reset to null.
    * @param ex
    */
   public void setUpdateException(XmlBlasterException ex) {
      this.updateException = ex;
   }

   /**
    * Enforced by I_Callback. If you passed a responses array list, then it will notify the
    * thread waiting for it (and will add an entry with this istance's name).
    * If updateException is not null, then an exception is thrown here and 
    * updateException is reset to null. 
    * @param cbSessionId
    * @param updateKey
    * @param content
    * @param updateQos
    * @return
    * @throws XmlBlasterException
    */
   public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos)
      throws XmlBlasterException {
      if (log.isLoggable(Level.FINER)) log.finer("update '" + cbSessionId + "' content='" + new String(content) + "'");
      String clientProp = (String)updateQos.getData().getClientProperties().get("MsgDistributorPlugin");

      if (this.responses != null) {
         synchronized(responses) {
            try {
               this.responses.add(this.name);
               XmlBlasterException ex = this.updateException;
               this.updateException = null;
               if (ex != null) throw ex;
            }
            finally {
               this.responses.notify();
            }
         }
      }
      else {
         XmlBlasterException ex = this.updateException;
         this.updateException = null;
         if (ex != null) throw ex;
      }

      return "OK";
   }
}


/*------------------------------------------------------------------------------
Name:      QueueEntryId.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.queue;

import java.util.logging.Logger;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.SessionName;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.cluster.NodeId;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.queue.jdbc.XBStore;

/**
 * Class encapsulating the unique id of a queue or a cache. 
 * @author michele@laghi.eu
 * @author xmlBlaster@marcelruff.info
 */
public class StorageId implements java.io.Serializable
{
   private static final long serialVersionUID = 485407826616456805L;
   private static Logger log = Logger.getLogger(StorageId.class.getName());
   private Global glob;
   private transient final String postfix; // unstripped, deprecated: unique
                                           // string
   private String strippedId; // deprecated: only for xb_entries
   private final String id; // deprecated: only for xb_entires

   // these are used for the 2008 queues
   private transient XBStore xbStore;

   // Helper to split postfix further
   private transient String postfix1; // e.g. subjectId="joe"
   private transient String postfix2; // e.g. pubSessionId="2"

   public StorageId(Global glob, String xbNode, String xbType, SessionName sessionName) {
      this(glob, xbNode, xbType, null, sessionName);
   }

   public StorageId(Global glob, String xbNode, String xbType, String xbPostfix) {
      this(glob, xbNode, xbType, xbPostfix, null);
   }

   /**
    * New XBstore approach.
    * 
    * @param glob
    * @param xbNode
    *           "heron"
    * @param xbType
    *           Constants.RELATING_TOPIC
    * @param xbPostfix
    *           "Hello"
    */
   private StorageId(Global glob, String xbNode, String xbType, String xbPostfix, SessionName sessionName) {
      this.glob = (glob == null) ? Global.instance() : glob;
      // New queue: (xbPostfix) must be stripped to be backward compatible to
      // xb_entries transfered data
      // xb_entries.queuename uses absolute name
      // xbstore.postfix uses relative name
      String xbpost = "";
      if (sessionName != null) {
         xbpost = sessionName.getRelativeName();
         this.postfix1 = sessionName.getLoginName();
         if (sessionName.getPublicSessionId() != 0)
            this.postfix2 = "" + sessionName.getPublicSessionId();
      }
      else if (xbPostfix != null) {
         xbPostfix = xbPostfix.trim();
         splitPostfixFurther(xbPostfix);
         // xbpost = Global.getStrippedString(xbPostfix); FOR OLD XB_ENTRIES IT
         // MUST BE STRIPPED
         xbpost = xbPostfix;
      }
      String xbnod = (xbNode == null) ? "" : xbNode.trim();
      this.xbStore = new XBStore(xbnod, (xbType == null) ? "" : xbType.trim(),
            xbpost);
      
      // Old xb_entries:
      if (sessionName != null)
         this.postfix = sessionName.getAbsoluteName(); // Global.getStrippedString(sessionName.getAbsoluteName());
      else
         this.postfix = Global.getStrippedString(this.xbStore.getNode()) + xbPostfix; // xbpost;
      this.id = this.xbStore.getType() + ":" + this.postfix;
      this.strippedId = Global.getStrippedString(this.id);
      // session_heronsubPersistence,1_0
   }
   
   /**
    * Create a unique id, e.g. "history:/node/heron/client/joe/-2"
    * 
    * @param glob
    * @param relating
    *           e.g. "history" or Constants.RELATING_SUBJECT
    * @param postfix
    *           unique string e.g. "/node/heron/client/joe/-2" from
    *           sessionName.getAbsoluteName()
    * @deprecated Old xb_entries only
    */
   public StorageId(Global glob, String relating, String postfix) {
      this.glob = (glob == null) ? Global.instance() : glob;
      this.xbStore = new XBStore();
      this.xbStore.setType(relating);
      this.postfix = postfix;
      this.id = this.xbStore.getType() + ":" + this.postfix;
      splitPostfix(this.postfix);
      splitPostfixFurther(this.postfix);
      this.strippedId = Global.getStrippedString(this.id);
   }

   /**
    * Parse and create a unique id.
    * <p>
    * A queueId must be of the kind <i>cb:some/id/or/someother</i> where the
    * important requirement here is that it contains a ':' character. The text
    * on the left side of the separator (in this case 'cb') tells which kind of
    * queue it is: for example a callback queue (cb) or a client queue.
    * </p>
    * 
    * @param id
    *           e.g. "history:/node/heron/client/joe/-2"
    * @exception XmlBlasterException
    *               if no separator ":" was found
    * @deprecated Old xb_entries only
    */
   public StorageId(Global glob, String id) throws XmlBlasterException {
      this.glob = (glob == null) ? Global.instance() : glob;
      this.xbStore = new XBStore();
      this.id = id;
      int pos = this.id.indexOf(":");
      if (pos < 0)
         throw new XmlBlasterException(glob, ErrorCode.INTERNAL_ILLEGALARGUMENT, "StorageId", "Separator ':' not found in the queueId '" + id + "' please change it to a correct id");

      String prefix = this.id.substring(0, pos);
      this.xbStore.setType(prefix);
      this.postfix = this.id.substring(pos+1);
      splitPostfix(this.postfix);
      splitPostfixFurther(this.postfix);
      this.strippedId = Global.getStrippedString(this.id);
   }

   // Parse old xb_entries.queueName
   private final void splitPostfix(String post) {
      NodeId nodeId = this.glob.getNodeId();
      if (nodeId != null)
         this.xbStore.setNode(nodeId.getId());
      String relating = this.xbStore.getType();
      if (Constants.RELATING_CALLBACK.equals(relating) || Constants.RELATING_SUBJECT.equals(relating)) {
         //callback_nodemarcelclientsubscriber1  | UPDATE_REF
         // /node/heron/client/joe/session/1 (from SessionName)
         // subject_nodeheronclientsubscriberDummy
         String token = relating + "_node" + this.xbStore.getNode(); // "callback_nodeheron"
         if (post.startsWith(token)) {
            this.xbStore.setPostfix(post.substring(token.length()));
         } else {
            token = relating + "_" + this.xbStore.getNode(); // "history_heronHello"
            if (post.startsWith(token)) {
               this.xbStore.setPostfix(post.substring(token.length()));
            } else {
               this.xbStore.setPostfix(post);
            }
         }
         return;
      }
      else if (Constants.RELATING_HISTORY.equals(relating)) { // "history"
         // "history_heronHello"
         String token = relating + "_" + this.xbStore.getNode();
         if (post.startsWith(token)) {
            this.xbStore.setPostfix(post.substring(token.length()));
         } else {
            this.xbStore.setPostfix(post);
         }
         return;
      }
      else if (Constants.RELATING_MSGUNITSTORE.equals(relating) || Constants.RELATING_SESSION.equals(relating)
            || Constants.RELATING_SUBSCRIBE.equals(relating) || Constants.RELATING_TOPICSTORE.equals(relating)) {
         // msgUnitStore:msgUnitStore_heronHello | MSG_XML
         // session_heronsubPersistence,1_0 | SESSION
         // subscribe_heronsubPersistence,1_0 | SUBSCRIBE
         // topicStore_heron | TOPIC_XML
         String token = relating + "_" + this.xbStore.getNode();
         if (post.length() <= token.length()) {
            this.xbStore.setPostfix("");
            return;
         } else if (post.startsWith(token)) {
            this.xbStore.setPostfix(post.substring(token.length()));
            return;
         }
         token = this.xbStore.getNode() + "/";
         // post="heron/subPersistence,1.0"
         // if coming from SessionPersistencePlugin.java
         if (post.startsWith(token)) {
            // heron | session | subPersistence,1_0
            this.xbStore.setPostfix(post.substring(token.length()));
            return;
         }
         this.xbStore.setPostfix(post);
         return;
      }
      else if (Constants.RELATING_CLIENT.equals(relating)) { // "connection"
         // connection_clientpubisherToHeron2 | "publish"
         // "connection_avalonclientheron1" if in cluster environment (by
         // xmlBlasterAccess.setServerNodeId())
         String token = relating + "_"; // "connection_"
         if (nodeId != null) {
            token = relating + "node" + this.xbStore.getNode(); // "connection_nodeheron"
         }
         if (post.startsWith(token)) {
            this.xbStore.setPostfix(post.substring(token.length()));
         } else {
            token = relating + "_"; // "connection_clientpubisherToHeron2"
            if (post.startsWith(token)) {
               this.xbStore.setPostfix(post.substring(token.length()));
            } else {
               this.xbStore.setPostfix(post);
            }
         }
         return;
      }
      else {
         // 
         log.severe("Can't handle storageId '" + post + "'");
         // throw new IllegalArgumentException("Can't handle storageId '" + post
         // + "'");
      }
      int pos = post.indexOf('/');
      if (pos < 0) {
         this.xbStore.setNode(post);
         this.xbStore.setPostfix("");
      }
      else {
         this.xbStore.setNode(post.substring(0, pos + 1));
         String rest = post.substring(pos+1);
         pos = rest.indexOf('/');
         if (pos < 0)
            this.xbStore.setPostfix(rest);
         else {
            this.xbStore.setNode(this.xbStore.getNode() + rest.substring(0, pos + 1));
            this.xbStore.setPostfix(rest.substring(pos + 1));
         }
      }
   }

   private void splitPostfixFurther(String xbPostfix) {
      if (xbPostfix == null)
         return;
      this.postfix1 = xbPostfix;
      int pos = xbPostfix.lastIndexOf('/');
      if (pos > -1) {
         String tmp = xbPostfix.substring(0, pos);
         pos = tmp.lastIndexOf('/');
         if (pos > -1) {
            this.postfix1 = tmp.substring(pos + 1);
            this.postfix2 = xbPostfix.substring(pos + 1);
         } else {
            this.postfix1 = xbPostfix.substring(pos + 1);
         }
      }
   }

   /**
    * Not functional, we have no guaranteed way to find out the real id from the
    * stripped id.
    * 
    * @param glob
    * @param strippedStorageId
    * @return storageId.getId() is not recovered properly, can be null
    * @deprecated Old xb_entries
    */
   public static StorageId valueOf(Global glob, String strippedStorageId) {
      if (strippedStorageId == null) return null;
      String relating = null;
      if (strippedStorageId.startsWith(Constants.RELATING_CALLBACK))
         relating = Constants.RELATING_CALLBACK;
      else if (strippedStorageId.startsWith(Constants.RELATING_HISTORY))
         relating = Constants.RELATING_HISTORY;
      else if (strippedStorageId.startsWith(Constants.RELATING_MSGUNITSTORE))
         relating = Constants.RELATING_MSGUNITSTORE;
      else if (strippedStorageId.startsWith(Constants.RELATING_CLIENT))
         relating = Constants.RELATING_CLIENT;
      else if (strippedStorageId.startsWith(Constants.RELATING_SESSION))
         relating = Constants.RELATING_SESSION;
      else if (strippedStorageId.startsWith(Constants.RELATING_SUBJECT))
         relating = Constants.RELATING_SUBJECT;
      else if (strippedStorageId.startsWith(Constants.RELATING_SUBSCRIBE))
         relating = Constants.RELATING_SUBSCRIBE;
      else if (strippedStorageId.startsWith(Constants.RELATING_TOPICSTORE))
         relating = Constants.RELATING_TOPICSTORE;
      else
         return null;
      // "callback_nodexmlBlaster_172_23_254_15_10412clientcore900_prod_sc_r1-9"
      // "topicStore_xmlBlaster_172_23_254_15_10412"
      // "msgUnitStore_xmlBlaster_172_23_254_15_10412lidb"
      String postfix = strippedStorageId;
      StorageId tmp = new StorageId(glob, relating, postfix);
      tmp.strippedId = strippedStorageId;
      return tmp;
   }

   /**
    * is XBSTORE.XBTYPE
    * 
    * @return e.g. Constants.RELATING_HISTORY = "history"
    */
   public String getRelatingType() {
      return getXBStore().getType();
   }

   /**
    * Is XBSTORE.XBNODE + XBSTORE.XBPOSTFIX
    * 
    * @return e.g. "/node/heron/client/joe/-2"
    * @deprecated Use getXBStore().getPostfix()
    */
   public String getOldPostfix() {
      return this.postfix;
   }

   /**
    * @return e.g. "history:/node/heron/client/joe/-2"
    */
   public String getId() {
      return this.id;
   }

   /**
    * The id usable for file names and is used for the queue and message-store
    * names.
    * <p>
    * NOTE: This name should never change in future xmlBlaster releases. If it
    * changes the new release would not find old database entries!
    * 
    * @return e.g. "history_nodeheronclientjoe-2"
    * @see Global#getStrippedString(String)
    * @deprecated Old xb_entries
    */
   public String getStrippedId() {
      return this.strippedId;
   }

   public String getStrippedLogId() {
      return Global.getStrippedString(this.xbStore.toString());
   }

   /**
    * @return getId()
    * @deprecated
    */
   public String toString() {
      return this.id;
   }
   
   
   /**
    * returns an XBStore without having filled the id nor the flag1 member variables.
    * @return
    */
   public XBStore getXBStore() {
      return this.xbStore;
   }

   /**
    * 
    * @return never null, can be the subjectId, topicOid etc. depending on
    *         Constants.RELATING_xxx
    */
   public String getPostfix1() {
      return (postfix1 == null) ? "" : postfix1;
   }

   /**
    * 
    * @return never null, typically the publicSessionId or empty string
    */
   public String getPostfix2() {
      return (postfix2 == null) ? "" : postfix2;
   }
}

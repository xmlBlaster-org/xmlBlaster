/*------------------------------------------------------------------------------
Name:      QueueEntryId.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.queue;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.SessionName;
import org.xmlBlaster.util.XmlBlasterException;
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
   private Global glob;
   private transient final String prefix;
   private transient final String postfix;
   private String strippedId;
   private final String id;

   // these are used for the 2008 queues (to build an XBStore object)
   private transient String xbNode;
   private transient String xbPostfix;

   /**
    * Create a unique id, e.g. "history:/node/heron/client/joe/-2"
    * 
    * @param glob
    * @param prefix
    *           e.g. "history" or Constants.RELATING_SUBJECT
    * @param postfix
    *           unique string e.g. "/node/heron/client/joe/-2" from
    *           sessionName.getAbsoluteName()
    */
   public StorageId(Global glob, String prefix, String postfix) {
      this.glob = Global.instance();
      this.prefix = prefix;
      this.postfix = postfix;
      this.id = this.prefix + ":" + this.postfix;
      splitPostfix(this.postfix);
      this.strippedId = Global.getStrippedString(this.id);
   }

   /**
    * Parse and create a unique id. 
    * <p>
    * A queueId must be of the kind <i>cb:some/id/or/someother</i>
    * where the important requirement here is that it contains a ':' character.
    * The text on the left side of the separator (in this case 'cb') tells which
    * kind of queue it is: for example a callback queue (cb) or a client queue.
    * </p>
    * @param e.g. "history:/node/heron/client/joe/-2"
    * @exception XmlBlasterException if no separator ":" was found
    */
   public StorageId(Global glob, String id) throws XmlBlasterException {
      this.glob = (glob == null) ? Global.instance() : glob;
      this.id = id;
      int pos = this.id.indexOf(":");
      if (pos < 0)
         throw new XmlBlasterException(glob, ErrorCode.INTERNAL_ILLEGALARGUMENT, "StorageId", "Separator ':' not found in the queueId '" + id + "' please change it to a correct id");

      this.prefix = this.id.substring(0, pos);
      this.postfix = this.id.substring(pos+1);
      splitPostfix(this.postfix);
      this.strippedId = Global.getStrippedString(this.id);
   }

   
   private final void splitPostfix(String post) {
      if (Constants.RELATING_CALLBACK.equals(this.prefix)) { // "callback"
         //callback_nodemarcelclientsubscriber1  | UPDATE_REF
         SessionName sn = new SessionName(this.glob, post);
         xbNode = sn.getNodeIdStr();
         xbPostfix = sn.getRelativeNameWithoutSessionMarker();
         return;
      }
      else if (Constants.RELATING_SUBJECT.equals(this.prefix)) { // "subject"
         //subject_nodemarcelclientsomebody
         SessionName sn = new SessionName(this.glob, post);
         xbNode = sn.getNodeIdStr();
         xbPostfix = sn.getLoginName();
         return;
      }
      int pos = post.indexOf('/');
      if (pos < 0) {
         xbNode = post;
         xbPostfix = null;
      }
      else {
         xbNode = post.substring(0, pos + 1);
         String rest = post.substring(pos+1);
         pos = rest.indexOf('/');
         if (pos < 0)
            xbPostfix = rest;
         else {
            xbNode += rest.substring(0, pos+1);
            xbPostfix = rest.substring(pos+1);
         }
      }
      if (xbNode == null)
         xbNode = "";
      if (xbPostfix == null)
         xbPostfix = "";
   }

   /**
    * Not functional, we have no guaranteed way to find out the real id from the stripped id. 
    * @param glob
    * @param strippedStorageId
    * @return storageId.getId() is not recovered properly, can be null
    */
   public static StorageId valueOf(Global glob, String strippedStorageId) {
      if (strippedStorageId == null) return null;
      String prefix = null;
      if (strippedStorageId.startsWith(Constants.RELATING_CALLBACK))
         prefix = Constants.RELATING_CALLBACK;
      else if (strippedStorageId.startsWith(Constants.RELATING_HISTORY))
         prefix = Constants.RELATING_HISTORY;
      else if (strippedStorageId.startsWith(Constants.RELATING_MSGUNITSTORE))
         prefix = Constants.RELATING_MSGUNITSTORE;
      else if (strippedStorageId.startsWith(Constants.RELATING_CLIENT))
         prefix = Constants.RELATING_CLIENT;
      else if (strippedStorageId.startsWith(Constants.RELATING_SESSION))
         prefix = Constants.RELATING_SESSION;
      else if (strippedStorageId.startsWith(Constants.RELATING_SUBJECT))
         prefix = Constants.RELATING_SUBJECT;
      else if (strippedStorageId.startsWith(Constants.RELATING_SUBSCRIBE))
         prefix = Constants.RELATING_SUBSCRIBE;
      else if (strippedStorageId.startsWith(Constants.RELATING_TOPICSTORE))
         prefix = Constants.RELATING_TOPICSTORE;
      else
         return null;
      // "callback_nodexmlBlaster_172_23_254_15_10412clientcore900_prod_sc_r1-9"
      // "topicStore_xmlBlaster_172_23_254_15_10412"
      // "msgUnitStore_xmlBlaster_172_23_254_15_10412lidb"
      String postfix = strippedStorageId;
      StorageId tmp = new StorageId(glob, prefix, postfix);
      tmp.strippedId = strippedStorageId;
      return tmp;
   }

   /**
    * @return e.g. "history"
    */
   public String getPrefix() {
      return this.prefix;
   }

   /**
    * @return e.g. "/node/heron/client/joe/-2"
    */
   public String getPostfix() {
      return this.postfix;
   }

   /**
    * @return e.g. "history:/node/heron/client/joe/-2"
    */
   public String getId() {
      return this.id;
   }

   /**
    * The id usable for file names and is used for the queue and message-store names. 
    * <p>
    * NOTE: This name should never change in future xmlBlaster releases.
    * If it changes the new release would not find old database entries!
    * @return e.g. "history_nodeheronclientjoe-2"
    * @see Global#getStrippedString(String)
    */
   public String getStrippedId() {
      return this.strippedId;
   }

   /**
    * @return getId()
    */
   public String toString() {
      return this.id;
   }
   
   
   /**
    * returns an XBStore without having filled the id nor the flag1 member variables.
    * @return
    */
   public XBStore getXBStore() {
      XBStore xbStore = new XBStore();
      xbStore.setNode(Global.getStrippedString(xbNode));
      xbStore.setType(Global.getStrippedString(prefix));
      xbStore.setPostfix(Global.getStrippedString(xbPostfix));
      return xbStore;
   }

}

/*------------------------------------------------------------------------------
Name:      QueueEntryId.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.queue;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.ErrorCode;

/**
 * Class encapsulating the unique id of a queue or a cache. 
 * @author laghi@swissinfo.org
 * @author xmlBlaster@marcelruff.info
 */
public class StorageId implements java.io.Serializable
{
   private transient final String prefix;
   private transient final String postfix;
   private transient String strippedId;
   private final String id;

   /**
    * Create a unique id, e.g. "history:/node/heron/client/joe/-2"
    * @param prefix e.g. "history"
    * param postfix unique string e.g. "/node/heron/client/joe/-2"
    */
   public StorageId(String prefix, String postfix) {
      this.prefix = prefix;
      this.postfix = postfix;
      this.id = this.prefix + ":" + this.postfix;
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
      this.id = id;
      int pos = this.id.indexOf(":");
      if (pos < 0)
         throw new XmlBlasterException(glob, ErrorCode.INTERNAL_ILLEGALARGUMENT, "StorageId", "Separator ':' not found in the queueId '" + id + "' please change it to a correct id");

      this.prefix = this.id.substring(0, pos);
      this.postfix = this.id.substring(pos+1);
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
      if (this.strippedId == null) {
         this.strippedId = Global.getStrippedString(this.id);
      }
      return this.strippedId;
   }

   /**
    * @return getId()
    */
   public String toString() {
      return this.id;
   }
}

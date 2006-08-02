/*------------------------------------------------------------------------------
Name:      MsgKeyData.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.key;

import org.xmlBlaster.util.Global;

import java.util.Hashtable;
import java.util.Iterator;

/**
 * This class encapsulates the Message meta data and unique identifier (key)
 * of a publish()/update() or get()-return message.
 * <p />
 * A typical key could look like this:<br />
 * <pre>
c* </pre>
 * <br />
 * Note that the AGENT and DRIVER tags are application know how, which you have
 * to supply to the setClientTags() method.<br />
 * A well designed xml hierarchy of your problem domain is essential for a proper working xmlBlaster
 * <p />
 * <p>
 * If you haven't specified a key oid, there will be generated one automatically.
 * </p>
 * <p>
 * NOTE: Message oid starting with "__" is reserved for internal usage.
 * </p>
 * <p>
 * NOTE: Message oid starting with "_" is reserved for xmlBlaster plugins.
 * </p>
 * <p>
 * NOTE: The application specific tags and their attributes (like AGENT or DRIVER in the above example)
 * are received as a 'raw' XML ASCII string in the update() method of a client.
 * If a client wants to look at it she needs to parse it herself, usually by using an XML parser (DOM or SAX).
 * Some weird people even use regular expressions to do so.
 * </p>
 * @see org.xmlBlaster.util.key.MsgKeySaxFactory
 */
public final class MsgKeyData extends KeyData implements java.io.Serializable, Cloneable
{
   private final static String ME = "MsgKeyData";
   private transient I_MsgKeyFactory factory;
   private String clientTags;

   /**
    * Minimal constructor.
    */
   public MsgKeyData(Global glob) {
      this(glob, null, null);
   }

   /**
    * Constructor to parse a message. 
    * @param factory If null, the default factory from Global is used.
    */
   public MsgKeyData(Global glob, String serialData) {
      this(glob, null, serialData);
   }

   /**
    * Constructor to parse a message. 
    * @param factory If null, the default factory from Global is used.
    */
   public MsgKeyData(Global glob, I_MsgKeyFactory factory, String serialData) {
      super(glob, serialData);
      this.factory = (factory == null) ? this.glob.getMsgKeyFactory() : factory;
   }

   /**
    * @return never null, an oid is generated if it was null.
    */
   public String getOid() {
      if (super.getOid() == null) {
         setOid(generateOid(glob.getStrippedId()));
      }
      return super.getOid();
   }

   /**
    * Set client specific meta informations. 
    * <p />
    * May be used to integrate your application tags, for example:
    * <p />
    * <pre>
    *&lt;key oid='4711' contentMime='text/xml'>
    *   &lt;AGENT id='192.168.124.20' subId='1' type='generic'>
    *      &lt;DRIVER id='FileProof' pollingFreq='10'>
    *      &lt;/DRIVER>
    *   &lt;/AGENT>
    *&lt;/key>
    * </pre>
    * @param str Your tags in ASCII XML syntax
    */
   public void setClientTags(String tags) {
      this.clientTags = tags;
   }

   /**
    * The XML meta information of the topic. 
    * @return Can be null
    */
   public String getClientTags() {
      return this.clientTags;
   }

   private I_MsgKeyFactory getFactory() {
      if (this.factory == null) {
         this.factory = this.glob.getMsgKeyFactory();
      }
      return this.factory;
   }

   /**
    * Converts the data in XML ASCII string.
    * @return An XML ASCII string
    */
   public String toXml() {
      return getFactory().writeObject(this, null);
   }

   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * @param extraOffset indenting of tags for nice output
    * @return internal state of the query as a XML ASCII string
    */
   public String toXml(String extraOffset) {
      return getFactory().writeObject(this, extraOffset);
   }

   /**
    * Returns a shallow clone, you can change safely all basic or immutable types
    * like boolean, String, int.
    */
   public Object clone() {
      return super.clone();
   }

   /**
    * Dump the key to a flattened JXPath representation. 
    * <p>
    * This is experimental code for the simple Applet client
    * </p>
    * <pre>
    *   /key/@oid                 -> Rugby
    *   /key/@contentMime         -> text/plain
    *   /key/@contentMimeExtended -> Version-1.0
    *   /key/child::node()        -> &lt;myTeam>Munich&lt;numPlayers>6&lt;/numPlayers>&lt;/myTeam>
    * </pre>
    * <p>
    * @see <a href="http://jakarta.apache.org/commons/jxpath/">Apache JXPath</a>
    */
   public Hashtable toJXPath() {
      Hashtable map = new Hashtable();
      if (getOid() != null) map.put("/key/@oid", getOid());
      if (getContentMime() != null) map.put("/key/@contentMime", getContentMime());
      if (getContentMimeExtended() != null) map.put("/key/@contentMimeExtended", getContentMimeExtended());
      if (getClientTags() != null) map.put("/key/child::node()", getClientTags());
      return map;
   }

   /** java org.xmlBlaster.util.key.MsgKeyData */
   public static void main(String[] args) {
      {
         MsgKeyData key = new MsgKeyData(null);
         String clientTags = "<agent xmlns:aa='http://XX'>\n" +
                             "<![CDATA[ Hello\n" +
                             " world\n]]>" +
                             "</agent>";
         key.setClientTags(clientTags);
         System.out.println(key.getClientTags());
      }
      {
         MsgKeyData md = new MsgKeyData(new Global(args));
         Hashtable map = md.toJXPath();
         Iterator it = map.keySet().iterator();
         while (it.hasNext()) {
            String key = (String)it.next();
            System.out.println(key + " -> '" + map.get(key) + "'");
         }
      }
   }
}

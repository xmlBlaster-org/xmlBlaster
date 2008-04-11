/*------------------------------------------------------------------------------
Name:      MsgKeySaxFactory.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.key;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.SaxHandlerBase;
import org.xmlBlaster.util.XmlBuffer;
import org.xmlBlaster.util.def.Constants;

import org.xml.sax.*;

/**
 * Parsing xml Key (quality of service) of publish() and update(). 
 * <p />
 * All XmlKey's have the same XML minimal structure:<p>
 * <pre>
 *    &lt;key oid="12345"/>
 * </pre>
 * or
 * <pre>
 *    &lt;key oid="12345">
 *       &lt;!-- application specific tags -->
 *    &lt;/key>
 * </pre>
 *
 * where oid is a unique key.
 * <p />
 * A typical <b>publish</b> key could look like this:<br />
 * <pre>
 *     &lt;key oid='4711' contentMime='text/xml'>
 *        &lt;AGENT id='192.168.124.20' subId='1' type='generic'>
 *           &lt;DRIVER id='FileProof' pollingFreq='10'>
 *           &lt;/DRIVER>
 *        &lt;/AGENT>
 *     &lt;/key>
 * </pre>
 * <br />
 * Note that the AGENT and DRIVER tags are application know how, which you have to supply.<br />
 * A well designed xml hierarchy of your problem domain is essential for a proper working xmlBlaster
 * <p />
 * <p>
 * NOTE: &lt;![CDATA[ ... ]]> sections in the key are not supported
 * </p>
 * <p>
 * NOTE: Using tags like '&lt;<c/>' will be transformed to '&lt;c>&lt;/c>' on toXml()
 * </p>
 * @see org.xmlBlaster.util.key.MsgKeyData
 * @see org.xmlBlaster.test.classtest.key.MsgKeyFactoryTest
 * @author xmlBlaster@marcelruff.info
 */
public final class MsgKeySaxFactory extends SaxHandlerBase implements I_MsgKeyFactory
{
   private final Global glob;

   private  MsgKeyData msgKeyData;

   /** helper flag for SAX parsing: parsing inside <state> ? */
   private int inKey = 0;

   /**
    * Can be used as singleton. 
    */
   public MsgKeySaxFactory(Global glob) {
      super(glob);
      this.glob = glob;
   }

   /**
    * Parses the given xml Key and returns a MsgKeyData holding the data. 
    * Parsing of update() and publish() key is supported here.
    * @param the XML based ASCII string
    */
   public synchronized MsgKeyData readObject(String xmlKey) throws XmlBlasterException {
      if (xmlKey == null) {
         xmlKey = "<key/>";
      }
      
      this.inKey = 0; // Reset in case last parse had an exception

      this.msgKeyData = new MsgKeyData(glob, this, xmlKey);

      init(xmlKey);  // use SAX parser to parse it (is slow)

      if (this.msgKeyData.getOid() == null || this.msgKeyData.getOid().length() < 1) {
         this.msgKeyData.setOid(this.msgKeyData.generateOid(glob.getStrippedId()));
      }

      MsgKeyData tmp = this.msgKeyData;
      this.msgKeyData = null; // Don't need it anymore
      return tmp;
   }

   /**
    * Start element, event from SAX parser.
    * <p />
    * @param name Tag name
    * @param attrs the attributes of the tag
    */
   public final void startElement(String uri, String localName, String name, Attributes attrs) {
      if (name.equalsIgnoreCase("key")) {
         inKey++;
         if (inKey < 2) {
            if (attrs != null) {
               String tmp = attrs.getValue("oid");
               if (tmp != null) msgKeyData.setOid(tmp.trim());
               tmp = attrs.getValue("queryType");
               // Only for msg keys:
               //if (tmp != null) msgKeyData.setQueryType(tmp.trim());
               tmp = attrs.getValue("contentMime");
               if (tmp != null) msgKeyData.setContentMime(tmp.trim());
               tmp = attrs.getValue("contentMimeExtended");
               if (tmp != null) msgKeyData.setContentMimeExtended(tmp.trim());
               tmp = attrs.getValue("domain");
               if (tmp != null) msgKeyData.setDomain(tmp.trim());
            }
            character.setLength(0);
            return;
         }
      }
      if (inKey > 0) {
         String nameSpaceStr = null;
         if (uri != null && uri.length() > 0) {
            // Process namespace: <database:adapter xmlns:database='http://www.xmlBlaster.org/jdbc'/>
            //  uri=http://www.xmlBlaster.org/jdbc
            //  localName=adapter
            //  name=database:adapter
            String nameSpace = name.substring(0, name.indexOf(":")); // "database"
            nameSpaceStr = " xmlns:" + nameSpace + "='" + uri + "'";
         }

         // Collect everything to pass it later to XmlKey for DOM parsing:
         character.append("<").append(name);
         if (nameSpaceStr != null) {
            character.append(nameSpaceStr);
         }
         if (attrs != null) {
            int len = attrs.getLength();
            for (int ii=0; ii<len; ii++) {
                character.append(" ").append(attrs.getQName(ii)).append("='").append(attrs.getValue(ii)).append("'");
            }
         }
         character.append(">");
      }
   }

   /**
    * End element, event from SAX parser.
    * <p />
    * @param name Tag name
    */
   public void endElement(String uri, String localName, String name) {
      if (name.equalsIgnoreCase("key")) {
         inKey--;
         if (inKey < 1) {
            String tmp = character.toString().trim(); // Child tags
            if (tmp.length() > 0) {
               msgKeyData.setClientTags(tmp);
            }
            character.setLength(0);
         }
      }
      if (inKey > 0) character.append("</"+name+">");
   }

   /* Report the start of a CDATA section. (interface LexicalHandler) */
   //public void startCDATA() {
   //   log.info(ME, "startCDATA(str="+character.toString()+")");
   //   character.append("<![CDATA["); 
   //}

   /* Report the end of a CDATA section. (interface LexicalHandler) */
   //public void endCDATA() {
   //   log.info(ME, "endCDATA()");
   //   character.append("]]>");
   //}

   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * @param extraOffset indenting of tags for nice output
    * @return internal state of the RequestBroker as a XML ASCII string
    */
   public final String writeObject(MsgKeyData msgKeyData, String extraOffset) {
      XmlBuffer sb = new XmlBuffer(256);
      if (extraOffset == null) extraOffset = "";
      String offset = Constants.OFFSET + extraOffset;

      sb.append(offset).append("<key oid='").appendAttributeEscaped(msgKeyData.getOid()).append("'");
      if (msgKeyData.getContentMime() != null && !msgKeyData.getContentMime().equals(KeyData.CONTENTMIME_DEFAULT))
         sb.append(" contentMime='").appendAttributeEscaped(msgKeyData.getContentMime()).append("'");
      if (msgKeyData.getContentMimeExtended() != null && !msgKeyData.getContentMimeExtended().equals(KeyData.CONTENTMIMEEXTENDED_DEFAULT))
         sb.append(" contentMimeExtended='").appendAttributeEscaped(msgKeyData.getContentMimeExtended()).append("'");
      if (msgKeyData.getDomain() != null && msgKeyData.getDomain().length() > 0)
         sb.append(" domain='").appendAttributeEscaped(msgKeyData.getDomain()).append("'");
      if (msgKeyData.getClientTags() != null) {
         sb.append(">");
         sb.append(offset).append(extraOffset).append(Constants.INDENT).append(msgKeyData.getClientTags());
         sb.append(offset).append("</key>");
      }
      else
         sb.append("/>");
     return sb.toString();
   }

   /**
    * A human readable name of this factory
    * @return "MsgKeySaxFactory"
    */
   public String getName() {
      return "MsgKeySaxFactory";
   }

   /** java org.xmlBlaster.util.key.MsgKeySaxFactory */
   public static void main(String args[]) {
      Global glob = new Global(args);
      MsgKeySaxFactory factory = new MsgKeySaxFactory(glob);
      String xml = 
           "<key oid='HELLO' contentMime='image/gif' contentMimeExtended='2.0' domain='RUGBY'>\n" +
           "   Bla1\n" +
           "   <a><b></b></a>\n" +
           "   <![CDATA[Bla2]]>\n" +
           "   <c></c>\n" +
           "   Bla3\n" +
           "</key>\n";
      try {
         MsgKeyData key = factory.readObject(xml);
         System.out.println("RESULT\n" + key.toXml());
      }
      catch (Throwable e) {
         System.out.println("ERROR: " + e.toString());
      }
   }
}

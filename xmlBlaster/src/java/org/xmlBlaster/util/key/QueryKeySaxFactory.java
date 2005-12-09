/*------------------------------------------------------------------------------
Name:      QueryKeySaxFactory.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.key;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.SaxHandlerBase;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.qos.AccessFilterQos;

import java.util.Set;
import java.util.HashSet;

import org.xml.sax.Attributes;


/**
 * Parsing xml Key (quality of service) of subscribe() and update(). 
 * <p />
 * All XmlKey's have the same XML minimal structure:<p>
 * <pre>
 *     &lt;key oid='4711' queryType='EXACT'>
 *     &lt;/key>
 * </pre>
 * or like this:
 * <pre>
 *     &lt;key oid='' queryType='XPATH'>
 *        //AGENT
 *     &lt;/key>
 * </pre>
 * Example of the filter tag in the key (NOT EVALUATED -> use QoS):
 * <pre>
 *  &lt;key queryType='XPATH'>
 *     /xmlBlaster/key/RUGBY
 *     &lt;filter type='ContentLength' version='1.0'>
 *       800
 *     &lt;/filter>
 *  &lt;key>
 * </pre>
 * @see org.xmlBlaster.util.key.QueryKeyData
 * @see org.xmlBlaster.test.classtest.key.QueryKeyFactoryTest
 * @author xmlBlaster@marcelruff.info
 */
public final class QueryKeySaxFactory extends SaxHandlerBase implements I_QueryKeyFactory
{
   private String ME = "QueryKeySaxFactory";
   private final Global glob;
   private final LogChannel log;

   private QueryKeyData queryKeyData;

   /** helper flag for SAX parsing: parsing inside <state> ? */
   private transient int inKey;
   //private transient boolean inFilter;
   private transient AccessFilterQos tmpFilter;

   private StringBuffer innerTags;
   private boolean inCdata;

   private Set nameSpaceSet;

   /**
    * Can be used as singleton. 
    */
   public QueryKeySaxFactory(Global glob) {
      super(glob);
      this.glob = glob;
      this.log = glob.getLog("core");
   }

   /**
    * Parses the given xml Key and returns a QueryKeyData holding the data. 
    * Parsing of update() and subscribe() key is supported here.
    * @param the XML based ASCII string
    */
   public synchronized QueryKeyData readObject(String xmlKey) throws XmlBlasterException {
      if (xmlKey == null) {
         xmlKey = "<key/>";
      }
      
      this.inKey = 0;
      //this.inFilter = false;
      this.tmpFilter = null;
      this.innerTags = null;
      this.inCdata = false;
      this.nameSpaceSet = null;

      this.queryKeyData = new QueryKeyData(glob, this, xmlKey);

      init(xmlKey);  // use SAX parser to parse it (is slow)

      return queryKeyData;
   }

   /**
    * Start element, event from SAX parser.
    * <p />
    * @param name Tag name
    * @param attrs the attributes of the tag
    */
   public final void startElement(String uri, String localName, String name, Attributes attrs) {
      if (inKey == 0 && name.equalsIgnoreCase("key")) { // allow nested key tags
         inKey++;
         if (attrs != null) {
            String tmp = attrs.getValue("oid");
            if (tmp != null) queryKeyData.setOid(tmp.trim());
            tmp = attrs.getValue("contentMime");
            if (tmp != null) queryKeyData.setContentMime(tmp.trim());
            tmp = attrs.getValue("contentMimeExtended");
            if (tmp != null) queryKeyData.setContentMimeExtended(tmp.trim());
            tmp = attrs.getValue("domain");
            if (tmp != null) queryKeyData.setDomain(tmp.trim());
            tmp = attrs.getValue("queryType");
            try {
               if (tmp != null) queryKeyData.setQueryType(tmp.trim());
            }
            catch (XmlBlasterException e) {
               log.error(ME, e.toString());
            }
         }
         character.setLength(0);
         return;
      }

      if (inKey == 1 && name.equalsIgnoreCase("filter")) {
         //this.inFilter = true;

         if (character.length() > 0) {
            if (innerTags == null) innerTags = new StringBuffer();
            innerTags.append(character.toString().trim());
            character.setLength(0);
         }

         tmpFilter = new AccessFilterQos(glob);
         boolean ok = tmpFilter.startElement(uri, localName, name, character, attrs);
         if (ok) {
            queryKeyData.addFilter(tmpFilter);
         }
         else
            tmpFilter = null;
         return;
      }

      if (inKey > 0) {
         //log.info(ME, "uri=" + uri + " localName=" + localName + " name=" + name + " attrs=" + attrs.toString());
         String nameSpaceStr = null;
         if (innerTags == null) innerTags = new StringBuffer();

         if (uri != null && uri.length() > 0) {
            // Process namespace: <database:adapter xmlns:database='http://www.xmlBlaster.org/jdbc'/>
            //  uri=http://www.xmlBlaster.org/jdbc
            //  localName=adapter
            //  name=database:adapter
            if (this.nameSpaceSet == null) {
               this.nameSpaceSet = new HashSet();
            }
            if (!this.nameSpaceSet.contains(uri)) { // declare namespace only on first occurence
               this.nameSpaceSet.add(uri);
               String nameSpace = name.substring(0, name.indexOf(":")); // "database"
               nameSpaceStr = " xmlns:" + nameSpace + "='" + uri + "'";
            }
         }
         
         // Collect all sub tags with their attributes
         innerTags.append("<").append(name);
         if (nameSpaceStr != null) {
            innerTags.append(nameSpaceStr);
         }
         if (attrs != null) {
            int len = attrs.getLength();
            for (int ii=0; ii<len; ii++) {
                innerTags.append(" ").append(attrs.getQName(ii)).append("='").append(attrs.getValue(ii)).append("'");
            }
         }
         innerTags.append(">");

         // Collect text between tags
         if (character.length() > 0) {
            String tmp = character.toString().trim();
            character.setLength(0);
            // try to protect '<' text with CDATA section
            if (tmp.indexOf("<") > -1) {
               inCdata = true;
               innerTags.append("<![CDATA["); 
            }
            innerTags.append(tmp);
         }

         return;
      }
   }

   /**
    * End element, event from SAX parser.
    * <p />
    * @param name Tag name
    */
   public void endElement(String uri, String localName, String name) {
      if (inKey == 1 && name.equalsIgnoreCase("key")) {
         inKey--;
         String tmp = character.toString().trim(); // The xpath query (if after inner tags)
         if (tmp.length() > 0)
            queryKeyData.appendQueryString(tmp);
         if (innerTags != null && innerTags.length() > 0) {
            queryKeyData.appendQueryString(innerTags.toString());
            innerTags.setLength(0);
            innerTags = null; // free memory
         }
         character.setLength(0);
      }

      if (inKey == 1 && name.equalsIgnoreCase("filter")) {
         //this.inFilter = false;
         if (tmpFilter != null)
            tmpFilter.endElement(uri, localName, name, character);
         return;
      }

      if (inKey > 0) {
         if (innerTags == null) {
            innerTags = new StringBuffer();
         }
         String tmp = character.toString();
         character.setLength(0);

         // try to protect '<' text with CDATA section
         if (tmp.indexOf("<") > -1) {
            inCdata = true;
            innerTags.append("<![CDATA["); 
         }

         innerTags.append(tmp);

         if (inCdata) {
            inCdata = false;
            innerTags.append("]]>");
         }
         innerTags.append("</"+name+">");
      }
   }

   /* Report the start of a CDATA section. (interface LexicalHandler) */
   //public void startCDATA() {
   //   inCdata = true;
   //   innerTags.append(character.toString()); // e.g. "<adapter>\n\n<![CDATA[..." -> get the two "\n\n"
   //   character.setLength(0);
   //   innerTags.append("<![CDATA["); 
   //}

   /* Report the end of a CDATA section. (interface LexicalHandler) */
   //public void endCDATA() {
   //   inCdata = false;
   //   innerTags.append(character.toString());
   //   character.setLength(0);
   //   innerTags.append("]]>");
   //}

   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * @param extraOffset indenting of tags for nice output
    * @return internal state of the RequestBroker as a XML ASCII string
    */
   public final String writeObject(QueryKeyData queryKeyData, String extraOffset) {
      StringBuffer sb = new StringBuffer(256);
      if (extraOffset == null) extraOffset = "";
      String offset = Constants.OFFSET + extraOffset;

         sb.append(offset).append("<key");
      if (queryKeyData.getOid() != null)
         sb.append(" oid='").append(queryKeyData.getOid()).append("'");
      if (queryKeyData.getContentMime() != null && !queryKeyData.getContentMime().equals(KeyData.CONTENTMIME_DEFAULT))
         sb.append(" contentMime='").append(queryKeyData.getContentMime()).append("'");
      if (queryKeyData.getContentMimeExtended() != null && !queryKeyData.getContentMimeExtended().equals(KeyData.CONTENTMIMEEXTENDED_DEFAULT))
         sb.append(" contentMimeExtended='").append(queryKeyData.getContentMimeExtended()).append("'");
      if (queryKeyData.getDomain() != null && queryKeyData.getDomain().length() > 0)
         sb.append(" domain='").append(queryKeyData.getDomain()).append("'");
      if (queryKeyData.getQueryType() != null && !Constants.EXACT.equals(queryKeyData.getQueryType()))
         sb.append(" queryType='").append(queryKeyData.getQueryType()).append("'");

      boolean isClosed = false;
      if (queryKeyData.getQueryString() != null) {
         if (!isClosed) { sb.append(">"); isClosed=true; }
         sb.append(offset).append(Constants.INDENT).append(queryKeyData.getQueryString());
      }
      AccessFilterQos[] list = queryKeyData.getAccessFilterArr();
      for (int ii=0; list != null && ii<list.length; ii++) {
         if (!isClosed) { sb.append(">"); isClosed=true; }
         sb.append(list[ii].toXml(extraOffset+Constants.INDENT));
      }

      if (!isClosed) {
         sb.append("/>");
      }
      else {
         sb.append(offset).append("</key>");
      }
      return sb.toString();
   }

   /**
    * A human readable name of this factory
    * @return "QueryKeySaxFactory"
    */
   public String getName() {
      return "QueryKeySaxFactory";
   }
}

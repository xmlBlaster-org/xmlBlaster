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
import org.xmlBlaster.util.enum.Constants;
import org.xmlBlaster.engine.helper.AccessFilterQos;

import java.io.*;
import java.util.ArrayList;

import org.xml.sax.*;
import org.xml.sax.helpers.*;


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
   private transient int inKey = 0;
   private transient boolean inFilter = false;
   private transient AccessFilterQos tmpFilter = null;

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

      queryKeyData = new QueryKeyData(glob, this, xmlKey);

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
      if (name.equalsIgnoreCase("key")) {
         inKey++;
         if (inKey > 1) return; // ignore nested key tags
         if (attrs != null) {
            String tmp = attrs.getValue("oid");
            if (tmp != null) queryKeyData.setOid(tmp.trim());
            tmp = attrs.getValue("queryType");
            // Only for query keys:
            //if (tmp != null) queryKeyData.setQueryType(tmp.trim());
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

      if (inKey == 1) {
         String tmp = character.toString().trim(); // The xpath query (if before inner tags)
         if (tmp.length() > 0) {
            queryKeyData.setQueryString(tmp);
            character.setLength(0);
         }
      }

      if (inKey == 1 && name.equalsIgnoreCase("filter")) {
         inFilter = true;
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
         // Collect all sub tags
         character.append("<").append(name);
         if (attrs != null) {
            int len = attrs.getLength();
            for (int ii=0; ii<len; ii++) {
                character.append(" ").append(attrs.getQName(ii)).append("='").append(attrs.getValue(ii)).append("'");
            }
         }
         character.append(">");
         return;
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
         if (inKey > 0) return; // ignore nested key tags
         String tmp = character.toString().trim(); // The xpath query (if after inner tags)
         if (tmp.length() > 0)
            queryKeyData.setQueryString(tmp);
         character.setLength(0);
      }

      if (inKey == 1 && name.equalsIgnoreCase("filter")) {
         inFilter = false;
         if (tmpFilter != null)
            tmpFilter.endElement(uri, localName, name, character);
         return;
      }

      if (inKey > 0)
         character.append("</"+name+">");
   }

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

      sb.append(offset).append("<key oid='").append(queryKeyData.getOid()).append("'");
      if (queryKeyData.getContentMime() != null)
         sb.append(" contentMime='").append(queryKeyData.getContentMime()).append("'");
      if (queryKeyData.getContentMimeExtended() != null)
         sb.append(" contentMimeExtended='").append(queryKeyData.getContentMimeExtended()).append("'");
      if (queryKeyData.getDomain() != null && queryKeyData.getDomain().length() > 0)
         sb.append(" domain='").append(queryKeyData.getDomain()).append("'");
      if (queryKeyData.getQueryType() != null && !Constants.EXACT.equals(queryKeyData.getQueryType()))
         sb.append(" queryType='").append(queryKeyData.getQueryType()).append("'");
      sb.append(">");
      if (queryKeyData.getQueryString() != null) {
         sb.append(offset).append(Constants.INDENT).append(queryKeyData.getQueryString());
      }
      AccessFilterQos[] list = queryKeyData.getAccessFilterArr();
      for (int ii=0; list != null && ii<list.length; ii++) {
         sb.append(list[ii].toXml(extraOffset+Constants.INDENT));
      }
      sb.append(offset).append("</key>");
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

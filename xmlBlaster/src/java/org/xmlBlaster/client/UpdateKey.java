/*------------------------------------------------------------------------------
Name:      UpdateKey.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling one xmlKey, knows how to parse it with DOM
Version:   $Id: UpdateKey.java,v 1.27 2002/09/13 23:17:54 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.client;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.SaxHandlerBase;
import org.xmlBlaster.util.StopParseException;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.engine.helper.Constants;
import java.io.*;
import org.xml.sax.*;
import org.xml.sax.helpers.*;


/**
 * This class encapsulates the Message meta data and unique identifier of a received message.
 * <p />
 * A typical <b>update</b> key could look like this:<br />
 * <pre>
 * &lt;key oid='4711' contentMime='text/xml' contentMimeExtended='1.2' domain='Administration'>
 *    &lt;AGENT id='192.168.124.20' subId='1' type='generic'>
 *       &lt;DRIVER id='FileProof' pollingFreq='10'>
 *       &lt;/DRIVER>
 *    &lt;/AGENT>
 * &lt;/key>
 * </pre>
 * <br />
 * Note that the AGENT and DRIVER tags are application know how, which you have to supply.<br />
 * A well designed xml hierarchy of your problem domain is essential for a proper working xmlBlaster
 * <p />
 * This is exactly the key how it was published from the data source.
 * <p />
 * Call updateKey.init(xmlKey_literal); to start parsing the received key
 * <p />
 * See xmlBlaster/src/dtd/UpdateKey.xml
 * <p />
 * See http://www.w3.org/TR/xpath
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.update.html" target="others">the interface.update requirement</a>
 */
public class UpdateKey extends SaxHandlerBase
{
   private String ME = "UpdateKey";
   private final Global glob;
   private final LogChannel log;

   protected boolean inKey = false;     // parsing inside <key> ? </key>

   /** value from attribute <key oid="..."> */
   protected String keyOid = null;

   /** value from attribute <key oid="" contentMime="..."> */
   protected String contentMime = "text/plain";

   /** value from attribute <key oid="" contentMimeExtended="..."> */
   protected String contentMimeExtended = "";

   /** value from attribute <key oid="" domain="..."> */
   protected String domain = "";


   /**
    * Constructs an initialized UpdateKey object.
    * @param xmlKey The ASCII XML key to parse
    */
   public UpdateKey(Global glob, String xmlKey) throws XmlBlasterException
   {
      this.glob = glob;
      this.log = glob.getLog("client");
      if (log.CALL) log.trace(ME, "Creating new UpdateKey");
      init(xmlKey); // does the parsing
   }

   /**
    * Constructs an initialized UpdateKey object.
    * You must call init yourself
    */
   public UpdateKey(Global glob) throws XmlBlasterException
   {
      this.glob = glob;
      this.log = glob.getLog("client");
      if (log.CALL) log.trace(ME, "Creating new UpdateKey");
   }

   /**
    * Access the $lt;key oid="...">.
    * @return The unique key oid
    */
   public String getOid()
   {
      return keyOid;
   }


   /**
    * Access the $lt;key oid="...">.
    * @return The unique key oid
    * @see #getOid()
    */
   public String getUniqueKey()
   {
      return keyOid;
   }


   /**
    * Test if oid is '__sys__deadLetter'. 
    * <p />
    * Dead letters are unrecoverable lost messages, usually an administrator
    * should subscribe to those messages.
    */
   public boolean isDeadLetter()
   {
      return getUniqueKey().equals(Constants.OID_DEAD_LETTER);
   }

   public final boolean isInternal() {
      return getUniqueKey().startsWith("__");
   }

   public final boolean isNotInternal() {
      return !getUniqueKey().startsWith("__");
   }

   /**
    * Find out which mime type (syntax) the content of the message has.
    * @return e.g "text/xml" or "image/png"
    *         defaults to "text/plain"
    */
   public String getContentMime()
   {
      return contentMime;
   }


   /**
    * Some further specifying information of the content.
    * <p />
    * For example the application version number the document in the content.<br />
    * You may use this attribute for you own purposes.
    * @return The MIME-extended info, for example<br />
    *         "Version 1.1" in &lt;key oid='' contentMime='text/xml' contentMimeExtended='Version 1.1'><br />
    *         or "" (empty string) if not known
    */
   public String getContentMimeExtended()
   {
      return contentMimeExtended;
   }


   /**
    * The cluster domain. 
    */
   public String getDomain()
   {
      return domain;
   }


   /**
    * Start element callback, does handling of tag &lt;key> and its attributes.
    * <p />
    * You may include this into your derived startElement() method like this:<br />
    * <pre>
    *  if (super.startElementBase(name, attrs) == true)
    *     return;
    * </pre>
    * @return true if the tag is parsed here, the derived class doesn't need to look at this tag anymore
    *         false this tag is not handled by this Base class
    */
   protected final boolean startElementBase(String name, Attributes attrs)
   {
      if (name.equalsIgnoreCase("key")) {
         inKey = true;
         if (attrs != null) {
            int len = attrs.getLength();
            for (int i = 0; i < len; i++) {
               if( attrs.getQName(i).equalsIgnoreCase("oid") ) {
                  keyOid = attrs.getValue(i).trim();
               }
               if( attrs.getQName(i).equalsIgnoreCase("contentMime") ) {
                  contentMime = attrs.getValue(i).trim();
                  if (contentMime == null || contentMime.length() < 1)
                     contentMime = "text/plain";
               }
               if( attrs.getQName(i).equalsIgnoreCase("contentMimeExtended") ) {
                  contentMimeExtended = attrs.getValue(i).trim();
               }
               if( attrs.getQName(i).equalsIgnoreCase("domain") ) {
                  domain = attrs.getValue(i).trim();
               }
            }
            if (keyOid == null)
               log.warn(ME, "The oid of the message is missing");
            if (contentMime == null)
               log.warn(ME, "The contentMime of the message is missing");
         }
         return true;
      }
      return false;
   }


   /**
    * Start element.
    * <p />
    * Default implementation, knows how to parse &lt;key> but knows nothing about the tags inside of key
    */
   public void startElement(String uri, String localName, String name, Attributes attrs) throws StopParseException
   {
      if (startElementBase(name, attrs) == true) {
         // Now i know what i need to know, stop parsing here (i'm not interested in the tags inside)
         throw new StopParseException();
      }
   }


   /**
    * End element callback, does handling of tag &lt;key>.
    * <p />
    * You may include this into your derived endElement() method like this:<br />
    * <pre>
    *  if (super.endElementBase(name) == true)
    *     return;
    * </pre>
    * @return true if the tag is parsed here, the derived class doesn't need to look at this tag anymore
    *         false this tag is not handled by this Base class
    */
   protected final boolean endElementBase(String name)
   {
      if( name.equalsIgnoreCase("key") ) {
         inKey = false;
         character.setLength(0);
         return true;
      }
      return false;
   }


   /**
    * End element callback, does handling of tag &lt;key>.
    * <p />
    * You may include this into your derived endElement() method like this:<br />
    * <pre>
    *  if (super.endElementBase(name) == true)
    *     return;
    * </pre>
    * @return true if the tag is parsed here, the derived class doesn't need to look at this tag anymore
    *         false this tag is not handled by this Base class
    */
   public void endElement(String uri, String localName, String name)
   {
      endElementBase(name);
   }


   /**
    * Dump state of this object into XML.
    * <br>
    * @return The unparsed and literal XML string as delivered from xmlBlaster
    */
   public final String toXml()
   {
      return super.toXml();
   }


   /**
    * Dump state of this object into XML.
    * <br>
    * @param extraOffset indenting of tags
    * @return XML state of UpdateKey
    * @deprecated Use toXml() instead
    */
   public final String toXml(String extraOffset)
   {
      return super.toXml();
      /*
      StringBuffer sb = new StringBuffer();
      String offset = "\n   ";
      if (extraOffset == null) extraOffset = "";
      offset += extraOffset;

      sb.append(offset).append("<key oid='").append(getUniqueKey()).append("'");
      sb.append(" contentMime='").append(getContentMime()).append("'");
      sb.append(" contentMimeExtended='").append(getContentMimeExtended()).append("'");
      sb.append(" domain='").append(getDomain()).append("'");
      sb.append("/>");
      return sb.toString();
      */
   }
}

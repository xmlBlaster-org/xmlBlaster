/*------------------------------------------------------------------------------
Name:      UpdateKey.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling one xmlKey, knows how to parse it with DOM
Version:   $Id: UpdateKey.java,v 1.5 1999/12/16 11:29:51 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.client;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.SaxHandlerBase;
import org.xmlBlaster.util.StopParseException;
import org.xmlBlaster.serverIdl.XmlBlasterException;
import java.io.*;
import org.xml.sax.*;
import org.xml.sax.helpers.*;


/**
 * This class encapsulates the Message meta data and unique identifier.
 * <p />
 * A typical <b>update</b> key could look like this:<br />
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
 * This is exactly the key how it was published from the data source.
 *
 * @see org.xmlBlaster.util.UpdateKeyBase
 * <p />
 * see xmlBlaster/src/dtd/UpdateKey.xml
 * <p />
 * see http://www.w3.org/TR/xpath
 */
public class UpdateKey extends SaxHandlerBase
{
   private String ME = "UpdateKey";

   protected boolean inKey = false;     // parsing inside <key> ? </key>

   /** value from attribute <key oid="..."> */
   protected String keyOid = null;

   /** value from attribute <key oid="" contentMime="..."> */
   protected String contentMime = null;


   /**
    * Constructs an un initialized UpdateKey object.
    * You need to call the init() method to parse the XML string.
    */
   public UpdateKey()
   {
      if (Log.CALLS) Log.trace(ME, "Creating new UpdateKey");
   }


   /**
    * Access the $lt;key oid="...">.
    * @return The unique key oid
    */
   public String getUniqueKey()
   {
      return keyOid;
   }


   /**
    * Find out which mime type (syntax) the content of the message has.
    * @return e.g "text/xml" or "image/png"
    */
   public String getContentMime()
   {
      return contentMime;
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
   protected final boolean startElementBase(String name, AttributeList attrs)
   {
      if (name.equalsIgnoreCase("key")) {
         inKey = true;
         if (attrs != null) {
            int len = attrs.getLength();
            for (int i = 0; i < len; i++) {
               if( attrs.getName(i).equalsIgnoreCase("oid") ) {
                  keyOid = attrs.getValue(i).trim();
               }
               if( attrs.getName(i).equalsIgnoreCase("contentMime") ) {
                  contentMime = attrs.getValue(i).trim();
               }
            }
            if (keyOid == null)
               Log.warning(ME, "The oid of the message is missing");
            if (contentMime == null)
               Log.warning(ME, "The contentMime of the message is missing");
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
   public void startElement(String name, AttributeList attrs) throws StopParseException
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
         character = new StringBuffer();
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
   public void endElement(String name)
   {
      endElementBase(name);
   }


   /**
    * Dump state of this object into XML.
    * <br>
    * @return XML state of MessageUnitHandler
    */
   public final StringBuffer printOn()
   {
      return printOn((String)null);
   }


   /**
    * Dump state of this object into XML.
    * <br>
    * @param extraOffset indenting of tags
    * @return XML state of UpdateKey
    */
   public final StringBuffer printOn(String extraOffset)
   {
      StringBuffer sb = new StringBuffer();
      String offset = "\n   ";
      if (extraOffset == null) extraOffset = "";
      offset += extraOffset;

      sb.append(offset + "<key oid='" + getUniqueKey() + "' contentType='" + getContentMime() + "'>");
      sb.append(offset + "</key>\n");
      return sb;
   }
}

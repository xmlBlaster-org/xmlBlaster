/*------------------------------------------------------------------------------
Name:      UpdateKey.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling one xmlKey, knows how to parse it with DOM
Version:   $Id: UpdateKey.java,v 1.2 1999/12/14 23:18:56 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.client;

import org.xmlBlaster.util.Log;
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
public class UpdateKey extends HandlerBase
{
   private String ME = "UpdateKey";

   // private static final String DEFAULT_PARSER_NAME =  // com.ibm.xml.parsers.SAXParser // com.sun.xml.parser.ValidatingParser
   protected StringBuffer character = new StringBuffer();
   protected boolean inKey = false;     // parsing inside <key> ? </key>

   /** value from attribute <key oid="..."> */
   protected String keyOid = null;

   /** value from attribute <key oid="" mimeType="..."> */
   protected String mimeType = null;

   /**
    * The original key in XML syntax, for example:
    * <code>   &lt;key>&lt;/key>"</code>
    */
   protected String xmlKey_literal;


   /**
    * Constructs an un initialized UpdateKey object.
    * You need to call the init() method to parse the XML string.
    */
   public UpdateKey()
   {
      if (Log.CALLS) Log.trace(ME, "Creating new UpdateKey");
   }


   /**
    * Constructs an un initialized UpdateKey object.
    * @deprecated Use the empty constructor
    */
   public UpdateKey(String xmlKey_literal)
   {
      if (Log.CALLS) Log.trace(ME, "Creating new UpdateKey");
   }


   /*
    * This method parses the given key XML string using the SAX parser.
    * @param xmlKey_literal xmlBlaster key in XML notation
    */
   public void init(String xmlKey_literal) throws XmlBlasterException
   {
      if (xmlKey_literal == null)
         xmlKey_literal = "";

      this.xmlKey_literal = xmlKey_literal;

      if (xmlKey_literal.length() > 0) {
         parse(xmlKey_literal);
      }
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
    * Find out which mime type (syntax) of the XmlKey_literal String.
    * @return "text/xml" only XML is supported
    */
   public String getMimeType()
   {
      return mimeType;
   }


   /**
    * Does the actual parsing
    * @param xmlData key in XML notation
    */
   private void parse(String xmlData) throws XmlBlasterException
   {
      try {
         Parser parser = ParserFactory.makeParser(); // DEFAULT_PARSER_NAME
         parser.setDocumentHandler(this);
         parser.setErrorHandler(this);
         parser.setDTDHandler(this);
         parser.parse(new InputSource(new StringReader(xmlData)));
      }
      catch (Exception e) {
         Log.error(ME, "Error while parsing Key: " + e.toString());
         e.printStackTrace();
         throw new XmlBlasterException(ME, "Error while parsing Key: " + e.toString());
      }
   }


   /**
    * @return returns the literal xml string
    */
   public String toString()
   {
      return xmlKey_literal;
   }


   /** Processing instruction. */
   public void processingInstruction(String target, String data)
   {
   }


   /** Start document. */
   public void startDocument()
   {
   }


   /** Start element. */
   public void startElement(String name, AttributeList attrs)
   {
      if (name.equalsIgnoreCase("key")) {
         inKey = true;
         if (attrs != null) {
            int len = attrs.getLength();
            for (int i = 0; i < len; i++) {
               if( attrs.getName(i).equalsIgnoreCase("oid") ) {
                  keyOid = attrs.getValue(i).trim();
               }
               if( attrs.getName(i).equalsIgnoreCase("mimeType") ) {
                  mimeType = attrs.getValue(i).trim();
               }
            }
            if (keyOid == null)
               Log.warning(ME, "The oid of the message is missing");
            if (mimeType == null)
               Log.warning(ME, "The mimeType of the message is missing");
         }
         return;
      }
   }


   /**
    * Characters.
    * The text between two tags, in the following example 'Hello':
    * <key>Hello</key>
    */
   public void characters(char ch[], int start, int length)
   {
      character.append(new String(ch, start, length));
   }


   /** Ignorable whitespace. */
   public void ignorableWhitespace(char ch[], int start, int length)
   {
   }


   /** End element. */
   public void endElement(String name) {
      if( name.equalsIgnoreCase("key") ) {
         inKey = false;
         character = new StringBuffer();
         return;
      }
   }


   /** End document. */
   public void endDocument() {
   }


   //
   // ErrorHandler methods
   //

   /** Warning. */
   public void warning(SAXParseException ex)
   {
      Log.warning(ME, getLocationString(ex) + ": " + ex.getMessage());
   }


   /** Error. */
   public void error(SAXParseException ex)
   {
      Log.warning(ME, getLocationString(ex) + ": " + ex.getMessage());
   }


   /** Fatal error. */
   public void fatalError(SAXParseException ex) throws SAXException
   {
      if(inKey) {
         Log.warning(ME, getLocationString(ex) + ": " + ex.getMessage());
         throw ex;
      }
   }


   /** Fatal error. */
   public void notationDecl(String name, String publicId, String systemId)
   {
      if (Log.TRACE) Log.trace(ME, "notationDecl(name="+name+", publicId="+publicId+", systemId="+systemId+")");
   }


   /** Fatal error. */
   public void unparsedEntityDecl(String name, String publicId, String systemId, String notationName)
   {
      if (Log.TRACE) Log.trace(ME, "unparsedEntityDecl(name="+name+
                                          ", publicId="+publicId+
                                          ", systemId="+systemId+
                                          ", notationName="+notationName+")");
   }


   /** Returns a string of the location. */
   private String getLocationString(SAXParseException ex)
   {
      StringBuffer str = new StringBuffer();

      String systemId = ex.getSystemId();
      if (systemId != null) {
          int index = systemId.lastIndexOf('/');
          if (index != -1)
              systemId = systemId.substring(index + 1);
          str.append(systemId);
      }
      str.append(':');
      str.append(ex.getLineNumber());
      str.append(':');
      str.append(ex.getColumnNumber());

      return str.toString();

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

      sb.append(offset + "<key oid='" + getUniqueKey() + "' mimeType='" + getMimeType() + "'>");
      sb.append(offset + "</key>\n");
      return sb;
   }
}

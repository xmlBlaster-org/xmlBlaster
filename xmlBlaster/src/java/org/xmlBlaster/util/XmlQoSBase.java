/*------------------------------------------------------------------------------
Name:      XmlQoSBase.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling one QoS (quality of service), knows how to parse it with SAX
Version:   $Id: XmlQoSBase.java,v 1.3 1999/12/02 16:48:06 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.util;

import org.xmlBlaster.serverIdl.XmlBlasterException;
import java.io.*;
import java.util.Vector;
import org.xml.sax.*;
import org.xml.sax.helpers.*;


/**
 * XmlQoSBase
 * In good old C days this would have been named a 'flag' (with bit wise setting)
 * but: this nows some more stuff, namely:
 *
 *  - The stringified IOR of the ClientCallback
 */
public class XmlQoSBase extends HandlerBase
{
   private String ME = "XmlQoSBase";

   // private static final String DEFAULT_PARSER_NAME = 
                                        // com.ibm.xml.parsers.SAXParser
                                        // com.sun.xml.parser.ValidatingParser

   private StringBuffer  character = new StringBuffer();
   protected boolean inQos = false;         // parsing inside <qos> ?
   private boolean inDestination = false; // parsing inside <destination> ?
   private boolean isXPathQuery = false;

   /**
    * Vector for loginQoS, holding all destination addresses
    */
   private Vector destinationVec = new Vector();

   /**
    * The original key in XML syntax, for example:
    *    "<qos></qos>"
    */
   protected String xmlQoS_literal;


   /**
    * This object parses the given quality of service XML string using the SAX parser. 
    * @param xmlQoS_literal Quality of service in XML notation
    */
   public XmlQoSBase(String xmlQoS_literal) throws XmlBlasterException
   {
      if (Log.CALLS) Log.trace(ME, "Creating new XmlQoSBase");

      if (xmlQoS_literal == null)
         xmlQoS_literal = "";

      this.xmlQoS_literal = xmlQoS_literal;

      if (xmlQoS_literal.length() > 0) {
         parse(xmlQoS_literal);
      }
   }


   /**
    * Does the actual parsing
    * @param xmlData Quality of service in XML notation
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
         Log.error(ME, "Error while parsing QoS: " + e.toString());
         e.printStackTrace();
         throw new XmlBlasterException(ME, "Error while parsing QoS: " + e.toString());
      }
   }

   
   /**
    * @return returns the literal xml string
    */
   public String toString()
   {
      return xmlQoS_literal;
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
      if (name.equalsIgnoreCase("qos")) {
         inQos = true;
         return;
      }

      if (name.equalsIgnoreCase("destination")) {
         if (!inQos)
            return;
         inDestination = true;
         if (attrs != null) {
            int len = attrs.getLength();
            for (int i = 0; i < len; i++) {
               if( attrs.getName(i).equalsIgnoreCase("queryType") ) {
                  String queryType = attrs.getValue(i).trim();
                  if (queryType.equalsIgnoreCase("XPATH"))
                     isXPathQuery = true;
               }
            }
         }
         return;
      }
   } // startElement(String,AttributeList)


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
      if( name.equalsIgnoreCase("qos") ) {
         inQos = false;
         character = new StringBuffer();
         return;
      }

      if( name.equalsIgnoreCase("destination") ) {
         inDestination = false;
         if (isXPathQuery)
            Log.error(ME, "Sorry, XPath destinations are not yet supported");
         isXPathQuery = false;
         String destination = character.toString().trim();
         destinationVec.addElement(destination);
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
      if(inQos) {
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
}

/*------------------------------------------------------------------------------
Name:      SaxHandlerBase.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Default handling of Sax callbacks
Version:   $Id: SaxHandlerBase.java,v 1.4 2000/02/20 17:38:54 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.util;

import org.xmlBlaster.protocol.corba.serverIdl.XmlBlasterException;
import java.io.*;
import org.xml.sax.*;
import org.xml.sax.helpers.*;


/**
 * Default xmlBlaster handling of Sax callbacks and errors.
 * <p />
 * You may use this as a base class for your SAX handling.
 */
public class SaxHandlerBase extends HandlerBase
{
   private String ME = "SaxHandlerBase";

   // private static final String DEFAULT_PARSER_NAME =  // com.ibm.xml.parsers.SAXParser // com.sun.xml.parser.ValidatingParser
   protected StringBuffer character = new StringBuffer();


   /**
    * The original XML string in ASCII representation, for example:
    * <code>   &lt;qos>&lt;/qos>"</code>
    */
   protected String xmlLiteral;


   /**
    * Constructs an new object.
    * You need to call the init() method to parse the XML string.
    */
   public SaxHandlerBase()
   {
      if (Log.CALLS) Log.trace(ME, "Creating new SaxHandlerBase");
   }


   /*
    * This method parses the XML string using the SAX parser.
    * @param xmlLiteral The XML string
    */
   public void init(String xmlLiteral) throws XmlBlasterException
   {
      if (xmlLiteral == null)
         xmlLiteral = "";

      this.xmlLiteral = xmlLiteral;

      if (xmlLiteral.length() > 0) {
         parse(xmlLiteral);
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
      catch (StopParseException e) { // Doesn't work, with SUN parser (Exception is wrapped into org.xml.sax.SAXParseException)
         if (Log.TRACE) Log.trace(ME, "StopParseException: Parsing execution stopped half the way");
         return;
      }
      catch (Exception e) {
         if (e.getMessage().indexOf("org.xmlBlaster.util.StopParseException") > -1) { // org.xml.sax.SAXParseException
            if (Log.TRACE) Log.trace(ME, "Parsing execution stopped half the way");
            return;
         }
         Log.error(ME, "Error while SAX parsing: " + e.toString() + "\n" + xmlData);
         e.printStackTrace();
         throw new XmlBlasterException(ME, "Error while SAX parsing: " + e.toString() + "\n" + xmlData);
      }
   }


   /**
    * @return returns the literal xml string
    */
   public String toString()
   {
      return xmlLiteral;
   }


   /**
    * @return returns the literal xml string
    */
   public String toXml()
   {
      return xmlLiteral;
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
      Log.warning(ME, "Please provide your startElement() implementation");
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
   public void endElement(String name)
   {
      Log.warning(ME, "Please provide your startElement() implementation");
   }


   /** End document. */
   public void endDocument()
   {
   }


   //
   // ErrorHandler methods
   //

   /** Warning. */
   public void warning(SAXParseException ex)
   {
      Log.warning(ME, getLocationString(ex) + ": " + ex.getMessage() + "\n" + xmlLiteral);
   }


   /** Error. */
   public void error(SAXParseException ex)
   {
      Log.warning(ME, getLocationString(ex) + ": " + ex.getMessage() + "\n" + xmlLiteral);
   }


   /** Fatal error. */
   public void fatalError(SAXParseException ex) throws SAXException
   {
      Log.warning(ME, getLocationString(ex) + ": " + ex.getMessage() + "\n" + xmlLiteral);
      throw ex;
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

/*------------------------------------------------------------------------------
Name:      SaxHandlerBase.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Default handling of Sax callbacks
Version:   $Id: SaxHandlerBase.java,v 1.15 2002/05/11 08:09:02 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.util;

import org.xmlBlaster.util.Log;

import java.io.*;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXParseException;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.Locator;
import org.xml.sax.ContentHandler;
import org.xml.sax.ErrorHandler;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.parsers.SAXParser;


/**
 * Default xmlBlaster handling of Sax2 callbacks and errors.
 * <p />
 * You may use this as a base class for your SAX2 handling.
 */
public class SaxHandlerBase implements ContentHandler, ErrorHandler
{
   private String ME = "SaxHandlerBase";

   // The current location
   protected Locator locator = null;

   // private static final String DEFAULT_PARSER_NAME =  // com.ibm.xml.parsers.SAXParser // .sun.xml.parser.ValidatingParser
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
      if (Log.CALL) Log.trace(ME, "Creating new SaxHandlerBase");
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
         SAXParserFactory spf = SAXParserFactory.newInstance();
         // TODO: use glob and not Global
         boolean validate = Global.instance().getProperty().get("javax.xml.parsers.validation", false);
         spf.setValidating(validate);
         //if (Log.TRACE) Log.trace(ME, "XML-Validation 'javax.xml.parsers.validation' set to " + validate);

         SAXParser sp = spf.newSAXParser();
         XMLReader parser = sp.getXMLReader();

         parser.setContentHandler(this);
         parser.setErrorHandler(this); // !!! new MyErrorHandler ());
         parser.parse(new InputSource(new StringReader(xmlData)));
      }
      catch (StopParseException e) { // Doesn't work, with SUN parser (Exception is wrapped into org.xml.sax.SAXParseException)
         if (Log.TRACE) Log.trace(ME, "StopParseException: Parsing execution stopped half the way");
         return;
      }
      catch (Throwable e) {
         String location = (locator == null) ? "" : locator.toString();
         if (e instanceof org.xml.sax.SAXParseException)
            location = getLocationString((SAXParseException)e);

         if (e.getMessage().indexOf("org.xmlBlaster.util.StopParseException") > -1) { // org.xml.sax.SAXParseException
            if (Log.TRACE) Log.trace(ME, location + ": Parsing execution stopped half the way");
            return;
         }
         Log.error(ME, "Error while SAX parsing: " + location + ": " + e.toString() + "\n" + xmlData);
         e.printStackTrace();
         throw new XmlBlasterException(ME, "Error while SAX parsing: " + location + ": " + e.toString() + "\n" + xmlData);
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

   //
   // ContentHandler (or DefaultHandler) methods
   //

   /**
    * Characters.
    * The text between two tags, in the following example 'Hello':
    * <key>Hello</key>
    */
   public void characters(char ch[], int start, int length)
   {
      //character.append(new String(ch, start, length));
      character.append(ch, start, length);
   }

   /** End document. */
   public void endDocument()
   {
      //Log.warn(ME, "Entering endDocument() ...");
   }

   public void endElement(java.lang.String namespaceURI, java.lang.String localName, java.lang.String qName)
   {
      Log.warn(ME, "Please provide your endElement() implementation");
   }

   public void endPrefixMapping(java.lang.String prefix)
   {
      Log.warn(ME, "Entering endPrefixMapping() ...");
   }

   /** Ignorable whitespace. */
   public void ignorableWhitespace(char[] ch, int start, int length)
   {
   }

   /** Processing instruction. */
   public void processingInstruction(java.lang.String target, java.lang.String data)
   {
   }

   public void setDocumentLocator(Locator locator)
   {
      this.locator = locator;
   }

   public void skippedEntity(java.lang.String name)
   {
      Log.warn(ME, "Entering skippedEntity() ...");
   }

   /** Start document. */
   public void startDocument()
   {
   }

   /**
    * Receive notification of the beginning of an element.
    * The Parser will invoke this method at the beginning of every element in the XML document;
    * there will be a corresponding endElement event for every startElement event (even when the element is empty).
    * All of the element's content will be reported, in order, before the corresponding endElement event.
    */
   public void startElement(String namespaceURI, String localName, String qName, Attributes atts)
   {
      Log.warn(ME, "Please provide your startElement() implementation");
   }

   public void startPrefixMapping(java.lang.String prefix, java.lang.String uri)
   {
      Log.warn(ME, "Entering startPrefixMapping() ...");
   }



   //
   // ErrorHandler methods
   //

   /** Warning. */
   public void warning(SAXParseException ex)
   {
      Log.warn(ME, "warning: " + getLocationString(ex) + ": " + ex.getMessage() + " PublicId=" + ex.getPublicId() + ", SystemId=" + ex.getSystemId() + "\n" + xmlLiteral);
   }


   /** Error. */
   public void error(SAXParseException ex)
   {
      Log.warn(ME, "error: " + getLocationString(ex) + ": " + ex.getMessage() + "\n" + xmlLiteral);
   }


   /** Fatal error. */
   public void fatalError(SAXParseException ex) throws SAXException
   {
      if (ex.getMessage().indexOf("org.xmlBlaster.util.StopParseException") > -1) { // org.xml.sax.SAXParseException
         // using Picolo SAX2 parser we end up here
         if (Log.TRACE) Log.trace(ME+".fatalError", "Parsing execution stopped half the way");
         return;
      }

      Log.error(ME+".fatalError", getLocationString(ex) + ": " + ex.getMessage() + "\n" + xmlLiteral);
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

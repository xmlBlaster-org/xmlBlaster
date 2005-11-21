/*------------------------------------------------------------------------------
Name:      SaxHandlerBase.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Default handling of Sax callbacks
Version:   $Id$
------------------------------------------------------------------------------*/
package org.xmlBlaster.util;

import org.jutils.log.LogChannel;

import java.io.*;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXParseException;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.Locator;
import org.xml.sax.ContentHandler;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;
import org.xml.sax.ext.LexicalHandler;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.parsers.SAXParser;
import org.xmlBlaster.util.def.ErrorCode;


/**
 * Default xmlBlaster handling of Sax2 callbacks and errors.
 * <p />
 * You may use this as a base class for your SAX2 handling.
 */
public class SaxHandlerBase implements ContentHandler, ErrorHandler, LexicalHandler
{
   private String ME = "SaxHandlerBase";

   protected final Global glob;
   private final LogChannel log;

   // The current location
   protected Locator locator = null;

   // private static final String DEFAULT_PARSER_NAME =  // com.ibm.xml.parsers.SAXParser // .sun.xml.parser.ValidatingParser
   protected StringBuffer character = new StringBuffer();

   /** The xml file read for logging only */
   protected String xmlSource;


   /**
    * The original XML string in ASCII representation, for example:
    * <code>   &lt;qos>&lt;/qos>"</code>
    */
   protected String xmlLiteral;

   private boolean useLexicalHandler = false;

   /**
    * Constructs an new object.
    * You need to call the init() method to parse the XML string.
    */
   public SaxHandlerBase() {
       // TODO: use specific glob and not Global - set to deprecated
      this(null);
   }

   public SaxHandlerBase(Global glob) {
      this.glob = (glob == null) ? Global.instance() : glob;
      this.log = this.glob.getLog(null);
      if (log.CALL) log.trace(ME, "Creating new SaxHandlerBase");
   }

   /*
    * This method parses the XML InputSource using the SAX parser.
    * @param inputSource The XML string
    */
   public void init(InputSource inputSource) throws XmlBlasterException
   {
      parse(inputSource);
   }

   /*
    * This method parses the XML InputSource using the SAX parser.
    * @param inputSource For logging only (e.g. the XML file) or null
    * @param xmlLiteral The XML string
    */
   public void init(String xmlSource, InputSource inputSource) throws XmlBlasterException
   {
      this.xmlSource = xmlSource;
      parse(inputSource);
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
    * activates/deactivates the lexical handler. This can be used to get also the CDATA events
    */
   public void setUseLexicalHandler(boolean useLexicalHandler) {
      this.useLexicalHandler = useLexicalHandler;
   }

   public boolean getUseLexicalHandler() {
      return this.useLexicalHandler;
   }


   private void parse(String xmlData) throws XmlBlasterException {
      parse(new InputSource(new StringReader(xmlData)));
   }


   /**
    * Does the actual parsing
    * @param xmlData Quality of service in XML notation
    */
   private void parse(InputSource xmlData) throws XmlBlasterException {
      try {
         SAXParserFactory spf = glob.getSAXParserFactory();
         boolean validate = glob.getProperty().get("javax.xml.parsers.validation", false);
         spf.setValidating(validate);
         //if (log.TRACE) log.trace(ME, "XML-Validation 'javax.xml.parsers.validation' set to " + validate);

         SAXParser sp = spf.newSAXParser();
         XMLReader parser = sp.getXMLReader();

         parser.setContentHandler(this);
         parser.setErrorHandler(this); // !!! new MyErrorHandler ());

         /*
         final boolean useLexicalHandler = true; // switch on to get CDATA events
         */
         if (this.useLexicalHandler) {
            try {
               parser.setProperty("http://xml.org/sax/properties/lexical-handler", this); // register for startCDATA() etc. events
            }
            catch (SAXNotRecognizedException e) {
               log.warn(ME, "The SAX parser does not support the LexicalHandler interface, CDATA sections can't be restored" + e.toString());
            }
            catch (SAXNotSupportedException e) {
               log.warn(ME, "The SAX parser does not support the LexicalHandler interface, CDATA sections can't be restored" + e.toString());
            }
         }

         parser.parse(xmlData);
      }
      catch (Throwable e) {
         // In startElement(), endElement() you can use directly 
         // throw new org.xml.sax.SAXException("Can't parse it", e);

         if (e instanceof org.xmlBlaster.util.StopParseException) {
            // This inctanceOf / and cast does not seem to work: do we have different classloaders?
            StopParseException stop = (StopParseException)e;
            if (log.TRACE) log.trace(ME, "StopParseException: Parsing execution stopped half the way");
            if (stop.hasError()) {
               throw stop.getXmlBlasterException();
            }
            else {
               log.error(ME, "StopParseException without embedded XmlBlasterException: " + e.toString());
            }
            return;
         }

         if (e instanceof SAXException) { // Try to find an encapsulated XmlBlasterException ...
            SAXException saxE = (SAXException)e;
            if (log.TRACE) log.trace(ME, "SAXException: Parsing execution stopped half the way");
            Exception exc = saxE.getException();
            if (exc instanceof XmlBlasterException) {
               XmlBlasterException stop = (XmlBlasterException)exc;
               String txt = (stop.getMessage() != null && stop.getMessage().length() > 0) ? stop.getMessage() : "Error while SAX parsing";
               throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_CONFIGURATION, ME, txt, e);
            }
            else if (exc instanceof StopParseException) {
               StopParseException stop = (StopParseException)exc;
               if (stop.hasError()) {
                  throw stop.getXmlBlasterException();
               }
            }
         }

         String location = (locator == null) ? "" : locator.toString();
         if (e instanceof org.xml.sax.SAXParseException) {
            location = getLocationString((SAXParseException)e);
         }
         else if (this.xmlSource != null) {
            location = this.xmlSource;
         }

         if (e.getMessage() != null && e.getMessage().indexOf("org.xmlBlaster.util.StopParseException") > -1) { // org.xml.sax.SAXParseException
            if (log.TRACE) log.trace(ME, location + ": Parsing execution stopped half the way: " + e.getMessage() + ": " + e.toString());
            return;
         }
         if (log.TRACE) {
            log.trace(ME, "Error while SAX parsing: " + location + ": " + e.toString() + "\n" + xmlData);
            e.printStackTrace();
         }
         throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_CONFIGURATION, ME + ".parse()",
            "Error while SAX parsing " + location, e);
      }
      finally {
         locator = null;
      }
   }

   /**
    * @return returns the literal xml string
    */
   public String toString() {
      return xmlLiteral;
   }

   /**
    * @return returns the literal xml string
    */
   public String toXml() {
      return xmlLiteral;
   }

   /*
    * trims outer CDATA and spaces
   public String trimAll(String in) {
      String tmp = in.trim();
      if (tmp.startsWith("<![CDATA[")) {
         tmp = tmp.substring("<![CDATA[".length());
         int last = tmp.lastIndexOf("]]>");
         if (last > -1) {
            tmp = tmp.substring(0, last);
            return tmp.trim();
         }
      }
      return in;
   }
    */

   //
   // ContentHandler (or DefaultHandler) methods
   //

   /**
    * Characters.
    * The text between two tags, in the following example 'Hello':
    * <key>Hello</key>
    */
   public void characters(char ch[], int start, int length) {
      // log.info(ME, "Entering characters(str=" + new String(ch, start, length) + ")");
      character.append(ch, start, length);
   }

   /** End document. */
   public void endDocument() {
      //log.warn(ME, "Entering endDocument() ...");
   }

   public void endElement(java.lang.String namespaceURI, java.lang.String localName, java.lang.String qName) throws org.xml.sax.SAXException {
      log.warn(ME, "Please provide your endElement() implementation");
   }

   public void endPrefixMapping(java.lang.String prefix) {
      log.warn(ME, "Entering endPrefixMapping(prefix="+prefix+") ...");
   }

   /** Ignorable whitespace. */
   public void ignorableWhitespace(char[] ch, int start, int length) {
      // log.info(ME, "Entering ignorableWhitespace(str=" + new String(ch, start, length) + ")");
   }

   /** Processing instruction. */
   public void processingInstruction(java.lang.String target, java.lang.String data) {
      // log.info(ME, "Entering processingInstruction(target=" + target + " data=" + data);
   }

   public void setDocumentLocator(Locator locator) {
      this.locator = locator;
   }

   public void skippedEntity(java.lang.String name) {
      log.warn(ME, "Entering skippedEntity() ...");
   }

   /** Start document. */
   public void startDocument() {
      // log.info(ME, "Entering startDocument");
   }

   //public InputSource resolveEntity(java.lang.String publicId, java.lang.String systemId) {
   //   log.warn(ME, "Entering resolveEntity(publicId="+publicId+", systemId="+systemId+")");
   //   return null;
   //}

   /**
    * Receive notification of the beginning of an element.
    * The Parser will invoke this method at the beginning of every element in the XML document;
    * there will be a corresponding endElement event for every startElement event (even when the element is empty).
    * All of the element's content will be reported, in order, before the corresponding endElement event.
    * <p>
    * Example:
    * </p>
    * <p>
    *  With a namespace: &lt;database:adapter xmlns:database='http://www.xmlBlaster.org/jdbc'/>
    * </p>
    * <p>
    *  uri=http://www.xmlBlaster.org/jdbc
    *  localName=adapter
    *  name=database:adapter
    * </p>
    *
    * <p>
    *  Without a namespace: &lt;adapter/>
    * </p>
    * <p>
    *  uri= 
    *  localName=adapter
    *  name=adapter
    * </p>
    */
   public void startElement(String namespaceURI, String localName, String qName, Attributes atts) throws org.xml.sax.SAXException {
      log.warn(ME, "Please provide your startElement() implementation");
   }

   public void startPrefixMapping(java.lang.String prefix, java.lang.String uri) {
      log.warn(ME, "Entering startPrefixMapping() ...");
   }


   //========== ErrorHandler interface methods =============
   /** Warning. */
   public void warning(SAXParseException ex) {
      log.warn(ME, "warning: " + getLocationString(ex) + ": " + ex.getMessage() + " PublicId=" + ex.getPublicId() + ", SystemId=" + ex.getSystemId() + "\n" + xmlLiteral);
   }

   /** Error. */
   public void error(SAXParseException ex) {
      log.warn(ME, "error: " + getLocationString(ex) + ": " + ex.getMessage() + "\n" + xmlLiteral);
   }

   /** Fatal error. */
   public void fatalError(SAXParseException ex) throws SAXException {
      if (ex.getMessage().indexOf("org.xmlBlaster.util.StopParseException") > -1) { // org.xml.sax.SAXParseException
         // using Picolo SAX2 parser we end up here
         if (log.TRACE) log.trace(ME+".fatalError", "Parsing execution stopped half the way");
         return;
      }

      if (log.TRACE) {
         log.trace(ME+".fatalError", getLocationString(ex) + ": " + ex.getMessage() + "\n" + xmlLiteral);
         ex.printStackTrace();
      }
      throw ex;
   }

   /**  */
   public void notationDecl(String name, String publicId, String systemId) {
      if (log.TRACE) log.trace(ME, "notationDecl(name="+name+", publicId="+publicId+", systemId="+systemId+")");
   }

   /**  */
   public void unparsedEntityDecl(String name, String publicId, String systemId, String notationName) {
      if (log.TRACE) log.trace(ME, "unparsedEntityDecl(name="+name+
                                          ", publicId="+publicId+
                                          ", systemId="+systemId+
                                          ", notationName="+notationName+")");
   }


   /** Returns a string of the location. */
   private String getLocationString(SAXParseException ex)
   {
      StringBuffer str = new StringBuffer();

      if (this.xmlSource != null)
         str.append(this.xmlSource).append(":");

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

   //=============== LexicalHandler interface =====================
   /** Report an XML comment anywhere in the document. (interface LexicalHandler) */
   public void comment(char[] ch, int start, int length) {
      //if (log.TRACE) log.trace(ME, "Entering comment(str=" + new String(ch, start, length) + ")");
   }
   /** Report the end of a CDATA section. (interface LexicalHandler) */
   public void endCDATA() {
      //if (log.TRACE) log.trace(ME, "endCDATA()");
   }
   /** Report the end of DTD declarations. (interface LexicalHandler) */
   public void endDTD() {
      //if (log.TRACE) log.trace(ME, "endDTD()");
   }
   /** Report the end of an entity. (interface LexicalHandler) */
   public void endEntity(java.lang.String name) {
      //if (log.TRACE) log.trace(ME, "endEntity(name="+name+")");
   }
   /** Report the start of a CDATA section. (interface LexicalHandler) */
   public void startCDATA() {
      //if (log.TRACE) log.trace(ME, "startCDATA()");
   }
   /** Report the start of DTD declarations, if any. (interface LexicalHandler) */
   public void startDTD(java.lang.String name, java.lang.String publicId, java.lang.String systemId) {
      //if (log.TRACE) log.trace(ME, "startDTD(name="+name+", publicId="+publicId+", systemId="+systemId+")");
   }
   /** Report the beginning of some internal and external XML entities. (interface LexicalHandler) */
   public void startEntity(java.lang.String name) {
      //if (log.TRACE) log.trace(ME, "startEntity(name="+name+")");
   }
}

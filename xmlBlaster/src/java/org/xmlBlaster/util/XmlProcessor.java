/*------------------------------------------------------------------------------
Name:      XmlProcessor.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Accessing the XML processor through a singleton
Version:   $Id$
------------------------------------------------------------------------------*/
package org.xmlBlaster.util;

//import org.xmlBlaster.engine.Global;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.DocumentBuilder;
/**
 * Accessing the XML processor through a singleton glob.getXmlProcessor(). 
 * <p />
 * Only one instance of this XML processor is used in xmlBlaster to save creation time.
 */
public class XmlProcessor
{
   final private static String ME = "XmlProcessor";
   private com.jclark.xsl.dom.XMLProcessorImpl xmlProc;  // One global instance to save instantiation time
   private Global glob;


   /**
    * private Constructor for Singleton Pattern
    */
   public XmlProcessor(Global glob) throws XmlBlasterException
   {
      this.glob = glob;
      //this.xmlProc = new com.jclark.xsl.dom.SunXMLProcessorImpl();    // [ 75 millis ]
      this.xmlProc = new JAXPProcessor();
   }


   /**
    * Accessing the  Xml to DOM parser
    */
   public com.jclark.xsl.dom.XMLProcessorImpl getXmlProcessorImpl()
   {
      return this.xmlProc;
   }
   
   class JAXPProcessor extends com.jclark.xsl.dom.XMLProcessorImpl  {
      DocumentBuilderFactory dbf = null;
      
      public JAXPProcessor() throws XmlBlasterException{
         dbf = glob.getDocumentBuilderFactory();
      }
      
      public org.w3c.dom.Document load(org.xml.sax.InputSource input)
         throws java.io.IOException, org.xml.sax.SAXException {
         DocumentBuilder db = null;
         try {
            db = dbf.newDocumentBuilder ();
         }catch(javax.xml.parsers.ParserConfigurationException ex) {
            throw new org.xml.sax.SAXException("Could not setup builder", ex);
         }
         return db.parse(input);
      }
      
      public org.w3c.dom.Element getElementById(org.w3c.dom.Document doc, String str) {
         // Hope this is safe!
         return null;
      }
   } 

   public void shutdown() {}
}

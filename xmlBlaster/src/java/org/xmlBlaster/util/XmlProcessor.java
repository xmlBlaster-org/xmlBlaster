/*------------------------------------------------------------------------------
Name:      XmlProcessor.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Accessing the XML processor through a singleton
Version:   $Id: XmlProcessor.java,v 1.1 1999/12/09 17:14:53 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.util;


/**
 * Accessing the XML processor through a singleton. 
 * <p />
 * Only one instance of this XML processor is used in xmlBlaster to save creation time.
 */
public class XmlProcessor
{
   final private static String ME = "XmlProcessor";
   private static XmlProcessor theXmlProcesser = null;   // Singleton pattern
   private com.jclark.xsl.dom.XMLProcessorImpl xmlProc;  // One global instance to save instantiation time


   /**
    * Access to XmlProcessor singleton
    */
   public static XmlProcessor getInstance()
   {
      synchronized (XmlProcessor.class) {
         if (theXmlProcesser == null) {
            theXmlProcesser = new XmlProcessor();
         }
      }
      return theXmlProcesser;
   }


   /**
    * private Constructor for Singleton Pattern
    */
   private XmlProcessor()
   {
      this.xmlProc = new com.jclark.xsl.dom.SunXMLProcessorImpl();    // [ 75 millis ]
   }


   /**
    * Accessing the  Xml to DOM parser
    */
   public com.jclark.xsl.dom.XMLProcessorImpl getXmlProcessorImpl()
   {
      return this.xmlProc;
   }
}

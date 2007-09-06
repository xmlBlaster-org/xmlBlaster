/*------------------------------------------------------------------------------
Name:      SvgUtility.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Demo code for a svg client using batik
Version:   $Id$
------------------------------------------------------------------------------*/
package javaclients.svg.batik;

import java.io.IOException;
import java.io.Reader;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.io.StringReader;

import org.apache.batik.dom.svg.SAXSVGDocumentFactory;
import org.apache.batik.dom.svg.SVGOMDocument;


/**
 * @author $Author$ (michele@laghi.eu)
 */

public class SvgUtility
{
   private final static String PARSER_CLASSNAME = "org.apache.crimson.parser.XMLReaderImpl";

   protected SvgUtility ()
   {
   }


   public static SVGOMDocument createDocument (Reader reader, String dummyURI)
      throws IOException
   {
      SAXSVGDocumentFactory factory = new SAXSVGDocumentFactory(PARSER_CLASSNAME);
      return (org.apache.batik.dom.svg.SVGOMDocument)factory.createDocument(dummyURI, reader);
   }


   public static SVGOMDocument createDocument (InputStream inputStream, String dummyURI)
      throws IOException
   {
      SAXSVGDocumentFactory factory = new SAXSVGDocumentFactory(PARSER_CLASSNAME);
      return (org.apache.batik.dom.svg.SVGOMDocument)factory.createDocument(dummyURI, inputStream);
   }


   public static SVGOMDocument createDocument (String xmlString, String dummyURI) throws IOException
   {
      return createDocument(new StringReader(xmlString), dummyURI);
   }


   public static SVGOMDocument createDocument (byte[] byteArray, String dummyURI) throws IOException
   {
      return createDocument(new ByteArrayInputStream(byteArray), dummyURI);
   }

}

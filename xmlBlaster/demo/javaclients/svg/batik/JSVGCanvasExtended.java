/*------------------------------------------------------------------------------
Name:      JSVGCanvasExtended.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   The JSVGCanvas extended to fit the application specific requirements
Version:   $Id$
------------------------------------------------------------------------------*/
package javaclients.svg.batik;

import org.apache.batik.swing.JSVGCanvas;
// import org.apache.batik.dom.svg.SAXSVGDocumentFactory;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;

import java.io.IOException;
import java.io.Reader;
import java.io.InputStream;
import java.io.StringReader;
import java.io.ByteArrayInputStream;
import java.util.Hashtable;

import org.w3c.dom.Element;


/**
 * @author $Author$ (laghi@swissinfo.org)
 */

public class JSVGCanvasExtended extends JSVGCanvas
{
   private final static String ME = "JSVGCanvasExtended";
   private static Logger log = Logger.getLogger(JSVGCanvasExtended.class.getName());
//   private final static String PARSER_CLASSNAME = "org.xml.sax.parser";
//   private final static String PARSER_CLASSNAME = "org.apache.crimson.parser.Parser2";
//   private final static String PARSER_CLASSNAME = "org.apache.crimson.parser.XMLReaderImpl";

   private Interactor  specificInteractor = null;
   // this object takes care of the communication to xmlBlaster
   private Transceiver transceiver        = null;
   private Hashtable   idTable            = null;


   public JSVGCanvasExtended(Global glob)
   {
      super();

      this.specificInteractor = new Interactor();
      /* Initializes this object. It sets all necessary stuff for the special
       * interactor (the one which takes care of the application specific stuff)
       * adds the specific interactor to the list of interactors of this
       * canvas-inherited object. */
      this.specificInteractor.setBridgeContext(this.bridgeContext);
      this.specificInteractor.setCanvas(this);
      this.transceiver = new Transceiver(glob, this);
      this.specificInteractor.setTransceiver(this.transceiver);
   }


   public void loadDocumentFromReader (Reader reader)
      throws IOException
   {
      this.setSVGDocument(SvgUtility.createDocument(reader,"file://dummy.svg"));
   }


   public void loadDocumentFromInputStream (InputStream inputStream)
      throws IOException
   {
      this.setSVGDocument(SvgUtility.createDocument(inputStream, "file://dummy.svg"));
   }


   public void loadDocumentFromXmlString (String xmlString) throws IOException
   {
      loadDocumentFromReader(new StringReader(xmlString));
   }

   public void loadDocumentFromByteArray (byte[] byteArray) throws IOException
   {
      loadDocumentFromInputStream(new ByteArrayInputStream(byteArray));
   }


   /**
    * gets the element with the given elementId. If no table has been created,
    * then it returns null. If the id is not found in the table, null is
    * returned.
    */
   public Element getElement (String elementId)
   {
      if (this.idTable == null) return null;
      Object obj = this.idTable.get(elementId);
      if (obj == null) return null;
      return (Element)obj;
   }


   /**
    * This method should be called each time a new document has been successfully
    * and completelty loaded.
    */
   public void updateDocument ()
   {
      try {
         // a new contextBridge has been created
         // a new grepahicsNode has been created
         this.specificInteractor.setBridgeContext(this.bridgeContext);
         this.specificInteractor.setGraphicsNode();
         this.transceiver.setBridgeContext(this.bridgeContext);
         SvgIdMapper mapper = new SvgIdMapper();
         this.idTable = mapper.createIdTable(this.svgDocument);
         this.transceiver.subscribeElements();
      }
      catch (XmlBlasterException ex) {
         log.severe(".updateDocument: graphicsNode was null");
      }
   }

   public Interactor getSpecificInteractor ()
   {
      return this.specificInteractor;
   }

}

/*------------------------------------------------------------------------------
Name:      XmlNotPortable.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   XmlNotPortable hold none portable xml code
Version:   $Id$
Author:    xmlBlaster@marcelruff.info
------------------------------------------------------------------------------*/
package org.xmlBlaster.util;

import org.xmlBlaster.util.Global;
import org.jutils.log.LogChannel;

import java.io.IOException;
import org.w3c.dom.Document;

/**
 * XmlNotPortable holds static methods for parser dependent code.
 */
public class XmlNotPortable
{
   private static final String ME = "XmlNotPortable";
   private static final LogChannel log = Global.instance().getLog("core");

   public static final java.io.ByteArrayOutputStream write(org.w3c.dom.Document document) throws IOException
   {
      java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
      if (document instanceof org.apache.crimson.tree.XmlDocument) {
         ((org.apache.crimson.tree.XmlDocument)document).write(out/*, encoding*/);
      }
      else {
        /* NEW: xerces 2x (=IBM xml4j 4.0.1)   2002-04-18
        -> samples/dom/GetElementsByTagName.java shows how to dump XML our self
        -> samples/dom/Writer.java shows how to dump XML our self
           or use this Apache specific code:

        else if (document instanceof org.apache.xerces.dom.DocumentImpl) {
            import  org.apache.xml.serialize.OutputFormat;
            import  org.apache.xml.serialize.Serializer;
            import  org.apache.xml.serialize.SerializerFactory;
            import  org.apache.xml.serialize.XMLSerializer;

            ...
            import  org.w3c.dom.Document;
            Document doc= ...
            ...

            OutputFormat    format  = new OutputFormat( doc );   //Serialize DOM
            StringWriter  stringOut = new StringWriter();        //Writer will be a String
            XMLSerializer    serial = new XMLSerializer( stringOut, format );
            serial.asDOMSerializer();                            // As a DOM Serializer

            serial.serialize( doc.getDocumentElement() );

            System.out.println( "STRXML = " + stringOut.toString() ); //Spit out DOM as a String
         */

         /*
         // import java.io.IOException;
         // import java.io.StringWriter;
         // import org.apache.xalan.xpath.xml.FormatterToXML;
         // import org.apache.xml.serialize.OutputFormat;
         try
         {
            StringWriter stringWriter = new StringWriter();
            FormatterToXML serializer = new FormatterToXML(stringWriter);
            OutputFormat xmlOutputFormat = new OutputFormat();
            xmlOutputFormat.setOmitXMLDeclaration(true);
            xmlOutputFormat.setIndent(true);
            xmlOutputFormat.setIndentAmount(2);
            serializer.setOutputFormat(xmlOutputFormat);
            serializer.serialize(xmlKeyDoc);
         }
         catch (IOException ioException)
         {
            ioException.printStackTrace();
            // handle exception
         }
         */
         log.error(ME, "Code to write XML-ASCII is missing for document class=" + document.getClass().getName());
      }
      return out;
   }


   /**
    * Merging a node into another document.
    * <p />
    * The caller must synchronize if necessary
    * @param the destination document
    * @param the node to merge into the DOM tree
    */
   public static final void mergeNode(org.w3c.dom.Document document, org.w3c.dom.Node node)
   {
      if (log.CALL) log.call(ME, "mergeNode()");
      if (log.DUMP) log.dump(ME, "mergeNode=" + node.toString());

      if (document instanceof org.apache.crimson.tree.XmlDocument) {
         ((org.apache.crimson.tree.XmlDocument)document).changeNodeOwner(node); // not DOM portable
      }
      else {
         log.error(ME, "Code to merge XML-documents is missing for document class=" + document.getClass().getName());
      }

      document.getDocumentElement().appendChild(node);

      if (log.CALL) log.call(ME, "Successfully merged tree");
   }


   /**
    * Replacing a node (located within a certain DOM hierachy) with an other
    * one
    * <p />
    * The caller must synchronize if necessary
    * @param oldNode the node to be replaced
    * @param newNode the node to put in place of the old node
    */
   public static final void replaceNode(org.w3c.dom.Node oldNode, org.w3c.dom.Node newNode)
   {
      if (log.TRACE) log.trace(ME, "replaceNode=" + oldNode.toString());

      org.w3c.dom.Document document = oldNode.getOwnerDocument();

      if (document instanceof org.apache.crimson.tree.XmlDocument) {
         ((org.apache.crimson.tree.XmlDocument)document).changeNodeOwner(newNode); // not DOM portable
      }
      else {
         log.error(ME, "Code to replace XML-nodes is missing for document class=" + document.getClass().getName());
      }

      org.w3c.dom.Node parentNode = oldNode.getParentNode();
      if (parentNode == null) {
         document.getDocumentElement().appendChild(newNode);
         document.getDocumentElement().removeChild(oldNode);
      }
      else {
         parentNode.appendChild(newNode);
         parentNode.removeChild(oldNode);
      }

      if (log.TRACE) log.trace(ME, "Successfully replaced node");
   }

}

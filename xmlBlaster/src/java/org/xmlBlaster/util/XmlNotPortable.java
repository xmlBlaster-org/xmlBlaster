/*------------------------------------------------------------------------------
Name:      XmlNotPortable.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   XmlNotPortable hold none portable xml code
Version:   $Id: XmlNotPortable.java,v 1.2 2002/01/02 21:07:56 laghi Exp $
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.util;

import java.io.IOException;
import org.w3c.dom.Document;

/**
 * XmlNotPortable holds static methods for parser dependend code.
 */
public class XmlNotPortable
{
   private static final String ME = "XmlNotPortable";

   public static final java.io.ByteArrayOutputStream write(org.w3c.dom.Document document) throws IOException
   {
      java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
      if (document instanceof org.apache.crimson.tree.XmlDocument) {
         ((org.apache.crimson.tree.XmlDocument)document).write(out/*, encoding*/);
      }
      else {
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
         Log.error(ME, "Code to write XML-ASCII is missing for document class=" + document.getClass().getName());
      }
      return out;
   }


   /**
    * Mergin a node into another document.
    * <p />
    * The caller must synchronize if necessary
    * @param the destination document
    * @param the node to merge into the DOM tree
    */
   public static final void mergeNode(org.w3c.dom.Document document, org.w3c.dom.Node node)
   {
      if (Log.TRACE) Log.trace(ME, "mergeNode=" + node.toString());

      if (document instanceof org.apache.crimson.tree.XmlDocument) {
         ((org.apache.crimson.tree.XmlDocument)document).changeNodeOwner(node); // not DOM portable
      }
      else {
         Log.error(ME, "Code to merge XML-documents is missing for document class=" + document.getClass().getName());
      }

      document.getDocumentElement().appendChild(node);

      if (Log.TRACE) Log.trace(ME, "Successfully merged tree");
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
      if (Log.TRACE) Log.trace(ME, "replaceNode=" + oldNode.toString());

      org.w3c.dom.Document document = oldNode.getOwnerDocument();

      if (document instanceof org.apache.crimson.tree.XmlDocument) {
         ((org.apache.crimson.tree.XmlDocument)document).changeNodeOwner(newNode); // not DOM portable
      }
      else {
         Log.error(ME, "Code to replace XML-nodes is missing for document class=" + document.getClass().getName());
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

      if (Log.TRACE) Log.trace(ME, "Successfully replaced node");
   }

}

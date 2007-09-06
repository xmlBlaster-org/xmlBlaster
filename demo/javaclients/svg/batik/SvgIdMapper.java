/*------------------------------------------------------------------------------
Name:      SvgIdMapper.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Creates an hashtable containing the dynamic svg elements
Version:   $Id$
------------------------------------------------------------------------------*/
package javaclients.svg.batik;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.XmlBlasterException;
import java.util.Hashtable;
import java.util.Enumeration;
import java.io.File;

// It would make sense to use DOM2, but with Crimson this is not possible
/*
import org.w3c.dom.traversal.NodeFilter;
import org.w3c.dom.traversal.DocumentTraversal;
import org.w3c.dom.traversal.NodeIterator;
*/
import org.w3c.dom.NodeList;

import org.w3c.dom.Node;
import org.w3c.dom.Element;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.DocumentBuilder;

/**
 * This class implements the NodeFilter interface to handle only nodes which
 * are of the type "element" whith an attribute "id" which is not empty.
 * @author $Author$ (michele@laghi.eu)
 */
public class SvgIdMapper /*implements NodeFilter*/
{
   private final static String ME = "SvgIdMapper";
   private static Logger log = Logger.getLogger(SvgIdMapper.class.getName());

   /**
    * The table containing the pairs
    */
   private Hashtable idTable = null;

   public SvgIdMapper ()
   {

      idTable = new Hashtable();
   }


   /**
    * does a check to see if the given id string fullfills the requirement
    * needed to be a dynamic element. At this stage it accepts all id which
    * start with the prefix 'xmlBlaster.'. For example 'xmlBlaster.rect1'.
    */
   public static boolean isDynamic (String id)
   {
      if (id == null) return false;
      if (id.startsWith("xmlBlaster.")) return true;
      return false;
   }


   /*
   public short acceptNode(Node node)
   {
      log.info(".acceptNode " + node.toString());
      // this is probably not needed
      if (!(node instanceof Element)) return FILTER_REJECT;
      String idText = ((Element)node).getAttribute("id");
      if ((idText == null) || (idText.length() < 1)) return FILTER_SKIP;
      // add it to the map ...
      this.idTable.put(idText, node);
      return FILTER_ACCEPT;
   }
   */


   /**
    * This method can be used with the crimson parser since this has no
    * transverse implemented.
    */
   protected void scanNode (Node node)
   {
      log.fine(".scanNode started");
      if (node instanceof Document) {
         node = ((Document)node).getDocumentElement();
      }
      if (node instanceof Element) {
         Element el = (Element)node;
         String idText = el.getAttribute("id");
         if (isDynamic(idText)) {
            this.idTable.put(idText, node);
            log.fine(".scanNode: " + idText);
         }
         // scan the child nodes if any
         NodeList nodeList = el.getChildNodes();
         if (nodeList != null) {
            for (int i=0; i < nodeList.getLength(); i++) {
               scanNode(nodeList.item(i));
            }
         }
      }
      log.fine(".scanNode ended");
   }



   public Hashtable createIdTable (Document document)
   {
      log.info("createIdTable");
      this.idTable = new Hashtable();

      this.scanNode(document);
      /*
      NodeIterator nodeIterator = ((DocumentTraversal)document)
         .createNodeIterator(document, SHOW_ELEMENT, this, true);

      while (nodeIterator.nextNode() != null) {}
      */


      return this.idTable;
   }



   public static void main(String[] args)
   {
      SvgIdMapper mapper = new SvgIdMapper();
      try {
         File file = new File("simple.svg");
         DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
         Document doc = builder.parse(file);
         Hashtable idTable = mapper.createIdTable(doc);
         Enumeration keys = idTable.keys();
         while (keys.hasMoreElements()) {
            String key = (String)keys.nextElement();
            System.out.println(key);
         }
      }
      catch (Exception ex) {
         System.err.println(ex.toString());
      }
   }
}

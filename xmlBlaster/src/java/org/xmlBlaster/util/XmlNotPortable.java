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
import org.xmlBlaster.util.def.ErrorCode;
import org.jutils.log.LogChannel;

import java.io.IOException;
import java.util.Enumeration;

/**
 * XmlNotPortable holds static methods for parser dependent code. 
 *
 * Currently JDK 1.2 until JDK 1.5 are explicitly covered. 
 * <p>
 * For JDK 1.5 we use only DOM Level 3 compliant coding, so any
 * such parser should do the job.
 * <p>
 * For JDK <= 1.4 we need the crimson parser.
 * <p>
 * The current xml code is tested with Sun JDK 1.2 - JDK 1.6
 * IBM JDK 1.4 and jrockit JDK 5.
 *
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>
 */
public class XmlNotPortable
{
   private static final String ME = "XmlNotPortable";
   private static final LogChannel log = Global.instance().getLog("core");
   private static java.lang.reflect.Method method_newXPath = null;
   private static Class[] paramCls_StringDocument = null;
   private static Class clazz_XPathFactory = null;
   private static Class clazz_XPath = null;
   private static Class clazz_QName = null;
   private static Object instance_XPathFactory = null;
   private static Object field_NODESET = null;
   /** We only distinguish:
    * 13 for all JDK <= 1.3
    * 14 for JDK 1.4
    * 15 for all JDK >= 1.5
    */
   public static int JVM_VERSION = 14;
   /**
    * xmlBlaster uses generally UTF-8
    */
   public static String ENCODING = "UTF-8";

   static {
      String version = System.getProperty("java.specification.version"); // 1.5, use "java.vm.version=1.5.0_04"?
      version = (version==null)?"1.3":version.trim();

      if (version.startsWith("1.1") || version.startsWith("1.2") || version.startsWith("1.3")) {
         JVM_VERSION = 13;
      }
      else if (version.startsWith("1.4")) {
         JVM_VERSION = 14;
      }
      else {
         JVM_VERSION = 15; // or above
      }
   }

   /**
    * Do XPath query on DOM
    */
   public static Enumeration getNodeSetFromXPath(String expression, org.w3c.dom.Document document) throws XmlBlasterException {
      try {
         if (JVM_VERSION >= 15) {
            //javax.xml.xpath.XPath xpath = javax.xml.xpath.XPathFactory.newInstance().newXPath();
            //final org.w3c.dom.NodeList nodes = (org.w3c.dom.NodeList)xpath.evaluate(expression, document, javax.xml.xpath.XPathConstants.NODESET);
            if (method_newXPath == null) {
               synchronized(XmlNotPortable.class) {
                  if (method_newXPath == null) {
                     clazz_XPathFactory = java.lang.Class.forName("javax.xml.xpath.XPathFactory");
                     Class[] paramCls = new Class[0];
                     Object[] params = new Object[0];
                     java.lang.reflect.Method method = clazz_XPathFactory.getMethod("newInstance", paramCls);
                     instance_XPathFactory = method.invoke(clazz_XPathFactory, params);
                     method_newXPath = clazz_XPathFactory.getMethod("newXPath", paramCls);

                     clazz_XPath = java.lang.Class.forName("javax.xml.xpath.XPath");

                     Class clazz_XPathConstants = java.lang.Class.forName("javax.xml.xpath.XPathConstants");
                     clazz_QName = java.lang.Class.forName("javax.xml.namespace.QName");
                     java.lang.reflect.Field field = clazz_XPathConstants.getDeclaredField("NODESET");
                     field_NODESET = field.get(null);
                     paramCls_StringDocument = new Class[] { 
                                      java.lang.String.class,
                                      java.lang.Object.class, // org.w3c.dom.Document.class,
                                      clazz_QName };
                  }
               }
            }
            
            Object xpath = method_newXPath.invoke(instance_XPathFactory, new Object[0]);
            Object[] params = new Object[] { expression, document, field_NODESET };
            java.lang.reflect.Method method_evaluate = clazz_XPath.getMethod("evaluate", paramCls_StringDocument);
            final org.w3c.dom.NodeList nodes = (org.w3c.dom.NodeList)method_evaluate.invoke(xpath, params);

            final int length = nodes.getLength();
            return new java.util.Enumeration() {
               int i=0;
               public boolean hasMoreElements() {
                  return i<length;
               }
               public Object nextElement() {
                  i++;
                  return nodes.item(i-1);
               }
            };
         }
         else {
            // JDK < 1.5: james clark XSL parser with fujitsu wrapper for XPath:
            // see xmlBlaster/lib/xtdash.jar and xmlBlaster/lib/omquery.jar
            //com.fujitsu.xml.omquery.DomQueryMgr queryMgr =
            //      new com.fujitsu.xml.omquery.DomQueryMgr(document);
            //return queryMgr.getNodesByXPath(document, expression);
            Class clazz = java.lang.Class.forName("com.fujitsu.xml.omquery.DomQueryMgr");
            Class[] paramCls = new Class[] { org.w3c.dom.Document.class };
            Object[] params = new Object[] { document };
            java.lang.reflect.Constructor ctor = clazz.getConstructor(paramCls);
            Object domQueryMgr = ctor.newInstance(params);

            paramCls = new Class[] { org.w3c.dom.Node.class, java.lang.String.class };
            params = new Object[] { document, expression };
            java.lang.reflect.Method method = clazz.getMethod("getNodesByXPath", paramCls);
            return (Enumeration)method.invoke(domQueryMgr, params);
         }
      }
      catch (Exception e) { // javax.xml.xpath.XPathExpressionException or com.jclark.xsl.om.XSLException
         e.printStackTrace();
         throw new XmlBlasterException(Global.instance(), ErrorCode.RESOURCE_CONFIGURATION, ME, "Can't process XPath expression '" + expression + "'", e);
      }
   }

  /**
   * Dumo the DOM nodes to a XML string. 
   *
   * (DOM) Level 1 and DOM Level 2 (The org.w3c.dom API is DOM Level 2 API)
   * specifications do not have a standard method for loading and saving an XML document.
   * Instead, we use vendor (crimson) specific code for input to and output from DOM parsers,
   * which is a disadvantage when portability is a requirement.
   */
   public static final java.io.ByteArrayOutputStream write(org.w3c.dom.Document document) throws IOException
   {
      java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();

      if (JVM_VERSION == 13) {
         if (document.getClass().getName().equals("org.apache.crimson.tree.XmlDocument")) {
            //if (document instanceof org.apache.crimson.tree.XmlDocument) {
            //   ((org.apache.crimson.tree.XmlDocument)document).write(out/*, encoding*/);
            //}
            try {
               Class[] paramCls = new Class[] { java.io.OutputStream.class };
               Object[] params = new Object[] { out };
               java.lang.reflect.Method method = document.getClass().getMethod("write", paramCls);
               method.invoke(document, params);
            }
            catch(Exception e) {
               e.printStackTrace();
               log.error(ME, "Code to write XML-ASCII has failed for document class=" + document.getClass().getName() + ": " + e.toString());
            }
         }
         return out;
      }
      
      if (JVM_VERSION == 14) {
         // see http://java.sun.com/xml/jaxp/dist/1.1/docs/tutorial/xslt/2_write.html
         try {
            if (log.TRACE) log.trace(ME, "write - Using JDK 1.4 DOM implementation");
            javax.xml.transform.TransformerFactory tf = javax.xml.transform.TransformerFactory.newInstance();
            javax.xml.transform.Transformer t = tf.newTransformer();
            // t.setOutputProperty(javax.xml.transform.OutputKeys.DOCTYPE_SYSTEM, PROPS_DTD_URI);
            t.setOutputProperty(javax.xml.transform.OutputKeys.INDENT, "yes");
            t.setOutputProperty(javax.xml.transform.OutputKeys.METHOD, "xml");
            t.setOutputProperty(javax.xml.transform.OutputKeys.ENCODING, ENCODING);
            //t.setOutputProperty(javax.xml.transform.OutputKeys.CDATA_SECTION_ELEMENTS, "");
            t.transform(new javax.xml.transform.dom.DOMSource(document), new javax.xml.transform.stream.StreamResult(out));
         }
         catch(Exception e) {
            e.printStackTrace();
            log.error(ME, "Code to write XML-ASCII has failed for document class=" + document.getClass().getName() + ": " + e.toString());
         }
         return out;
      }

      try {
         if (log.TRACE) log.trace(ME, "write - Using JDK 1.5 DOM implementation");
         // JDK 1.5 DOM Level 3 conforming:
         org.w3c.dom.ls.DOMImplementationLS implls = (org.w3c.dom.ls.DOMImplementationLS)document.getImplementation();
         // The above downcast throws a java.lang.ClassCastException for JDK 1.4, but is fine for JDK 1.5
         org.w3c.dom.ls.LSSerializer domWriter = implls.createLSSerializer();
         //domWriter.setNewLine("\n"); // should be default
         //domWriter.setFilter(new org.w3c.dom.ls.LSSerializerFilter () {
         //   public int getWhatToShow() {
         //      return SHOW_ALL;
         //   }
         //   public short acceptNode(org.w3c.dom.Node n) {
         //      return FILTER_ACCEPT;
         //   }            
         //});
         org.w3c.dom.ls.LSOutput output=implls.createLSOutput();
         output.setByteStream(out);
         output.setEncoding(ENCODING);
         domWriter.write(document, output);
         //String tmp = domWriter.writeToString(document);
         //out.write(tmp.getBytes());
      }
      catch(Exception e) {
         e.printStackTrace();
         log.error(ME, "Code to write XML-ASCII has failed for document class=" + document.getClass().getName() + ": " + e.toString());
      }
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
      return out;
   }


   /**
    * Merging a node into another document.
    * <p />
    * The caller must synchronize if necessary
    * <p />
    * Implementation details:
    * <ol>
    *  <li>adoptNode():
    *  Node org.w3c.dom.Document.adoptNode(Node source) throws DOMException
    *  merges the nodes since JDK 1.5 / DOM Level 3
    *  </li>
    *  <li>importNode():
    *  Node org.w3c.dom.Document.importNode(Node importedNode, boolean deep) throws DOMException
    *  takes a clone, since JDK 1.4 / DOM Level 2
    *  </li>
    *  <li>changeNodeOwner():
    *  Specific for crimson xml parser which was default in JDK 1.4 (not portable)
    *  </li>
    * </ol>
    * @param the destination document
    * @param the node to merge into the DOM tree
    */
   public static final void mergeNode(org.w3c.dom.Document document, org.w3c.dom.Node node)
   {
      if (log.CALL) log.call(ME, "mergeNode()");
      if (log.DUMP) log.dump(ME, "mergeNode=" + node.toString());

      //if (JVM_VERSION == 13) {
      if (JVM_VERSION == 13 || JVM_VERSION == 14) {
         //if (document instanceof org.apache.crimson.tree.XmlDocument) {
         if (document.getClass().getName().equals("org.apache.crimson.tree.XmlDocument")) {
            //((org.apache.crimson.tree.XmlDocument)document).changeNodeOwner(node); // not DOM portable
            try {
               Class[] paramCls = new Class[] { org.w3c.dom.Node.class };
               Object[] params = new Object[] { node };
               java.lang.reflect.Method method = document.getClass().getMethod("changeNodeOwner", paramCls);
               method.invoke(document, params);
               document.getDocumentElement().appendChild(node);
            }
            catch(Exception e) {
               log.error(ME, "Code to merge XML-documents adoptNode() has failed for document class=" + document.getClass().getName() + ": " + e.toString());
            }
         }
         else {
            log.error(ME, "Code to merge XML-documents is missing for document class=" + document.getClass().getName());
         }
         return;
      }
      /* -> This did not produce anything useful marcel 2005-08-22
            and makes no sense at it clones the DOM and is too slow
      if (JVM_VERSION == 14) {
         try {
            if (log.TRACE) log.trace(ME, "mergeNode - Using JDK 1.4 DOM implementation");
            document.importNode(node, true);
            document.getDocumentElement().appendChild(node);
         }
         catch(org.w3c.dom.DOMException e) {
            log.error(ME, "Code to merge XML-documents importNode() has failed for document class=" + document.getClass().getName() + ": " + e.toString());
         }
         return;
      }
      */
      try {
         if (log.TRACE) log.trace(ME, "mergeNode - Using JDK 1.5 DOM implementation");
         //document.adoptNode(node);   -> Use reflection for backward compatibility
         Class[] paramCls = new Class[] { org.w3c.dom.Node.class };
         Object[] params = new Object[] { node };
         java.lang.reflect.Method method = document.getClass().getMethod("adoptNode", paramCls);
         method.invoke(document, params);
         document.getDocumentElement().appendChild(node);
      }
      catch(/*org.w3c.dom.DOMException*/Exception e) {
         log.error(ME, "Code to merge XML-documents adoptNode() has failed for document class=" + document.getClass().getName() + ": " + e.toString());
      }

      if (log.CALL) log.call(ME, "Successfully merged tree");
   }

   /**
    * Access a XML DOM node tree walker. 
    * Caution: Works only with JDK 1.5 or higher
    * @param document The DOM document
    * @param node The root node to walk, if null the document's top node is chosen
    * @throws IllegalArgumentException for JDK <= 1.4
    */
   public static final org.w3c.dom.traversal.TreeWalker getTreeWalker(org.w3c.dom.Document document, org.w3c.dom.Node node)
   {
      if (log.CALL) log.call(ME, "getTreeWalker()");
      if (node == null) {
         node = document.getFirstChild();
      }

      if (JVM_VERSION == 13 || JVM_VERSION == 14) {
         // Not possible as crimson does not implement interface org.w3c.dom.traversal.TreeWalker!
         //org.apache.crimson.tree.TreeWalker tw = new org.apache.crimson.tree.TreeWalker(firstNode);
         //return tw;
         //throw new XmlBlasterException(Global.instance(), ErrorCode.INTERNAL_NOTIMPLEMENTED, "XmlNotPortable",
         //      "XML TreeWalker is only available for JDK 1.5 or above");
         throw new IllegalArgumentException("XML TreeWalker is only available for JDK 1.5 or above");
      }

      // org/w3c/dom/traversal/TreeWalker.java DOM Level 2
      org.w3c.dom.traversal.DocumentTraversal traveller = (org.w3c.dom.traversal.DocumentTraversal)document;
      org.w3c.dom.traversal.TreeWalker tw = traveller.createTreeWalker(node,
            org.w3c.dom.traversal.NodeFilter.SHOW_ALL, null, true);
      return tw;
   }

   /*
    * Replacing a node (located within a certain DOM hierachy) with an other
    * one
    * <p />
    * The caller must synchronize if necessary
    * @param oldNode the node to be replaced
    * @param newNode the node to put in place of the old node
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
    */
}

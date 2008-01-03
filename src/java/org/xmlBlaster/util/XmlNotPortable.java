/*------------------------------------------------------------------------------
Name:      XmlNotPortable.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   XmlNotPortable hold none portable xml code
Version:   $Id$
Author:    xmlBlaster@marcelruff.info
------------------------------------------------------------------------------*/
package org.xmlBlaster.util;

import org.w3c.dom.CDATASection;
import org.w3c.dom.Comment;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;
import org.w3c.dom.Document;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.def.ErrorCode;
import java.util.logging.Logger;
import java.util.logging.Level;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Enumeration;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

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
   private static Logger log = Logger.getLogger(XmlNotPortable.class.getName());
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
   private static int version;
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
    * Checks for forcing crimson even for JDK 15
    * @return 13, 14 or 15
    */
   public static int getJvmXmlVersionToUse() {
      if (version == 0) {
         synchronized (XmlNotPortable.class) {
            if (version == 0) {
               version = JVM_VERSION;
               if ("org.apache.crimson.jaxp.DocumentBuilderFactoryImpl".equals(Global.instance().getProperty().get("javax.xml.parsers.DocumentBuilderFactory",""))) {
                  version = 14;
               }
               // System.out.println("JVM_VERSION used for XML is " +  version);
            }
         }
      }
      return version;
   }

   /**
    * Do XPath query on DOM
    */
   public static Enumeration getNodeSetFromXPath(String expression, org.w3c.dom.Document document) throws XmlBlasterException {
      try {
         if (getJvmXmlVersionToUse() >= 15) {
            //javax.xml.xpath.XPath xpath = javax.xml.xpath.XPathFactory.newInstance().newXPath();
            //final org.w3c.dom.NodeList nodes = (org.w3c.dom.NodeList)xpath.evaluate(expression, document, javax.xml.xpath.XPathConstants.NODESET);
            Object xpath = null;
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
               xpath = method_newXPath.invoke(instance_XPathFactory, new Object[0]);
            }
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

   private static final java.io.ByteArrayOutputStream write(ByteArrayOutputStream out, Node node, String offset) throws IOException  {
      final String off = "\n" + offset;

      if (node instanceof Element) {
         String name = node.getNodeName();
         // String value = node.getNodeValue();
         NamedNodeMap attrs = node.getAttributes();
         StringBuffer buf = new StringBuffer(128);
         StringBuffer buf1 = new StringBuffer(50);

         buf.append(off).append("<").append(name);
         if (attrs != null && attrs.getLength() > 0) {
            for (int i=0; i < attrs.getLength(); i++) {
               Node attr = attrs.item(i);
               buf.append(" ").append(attr.getNodeName()).append("='").append(attr.getNodeValue()).append("'");
            }
         }
         boolean hasChilds = node.hasChildNodes();
         if (!hasChilds) {
            buf.append("/>");
            out.write(buf.toString().getBytes());
         }
         else {
            buf.append(">");
            out.write(buf.toString().getBytes());
            buf = new StringBuffer(50);
            buf1.append("</").append(name).append(">");
            if (hasChilds) {
               NodeList childs = node.getChildNodes();
               for (int i=0; i < childs.getLength(); i++) {
                  Node child = childs.item(i);
                  write(out, child, offset + "  ");
               }
               buf.append(off);
            }
            buf.append(buf1);
            out.write(buf.toString().getBytes());
         }
      }
      else if (node instanceof CDATASection) {
         String value = node.getNodeValue();
         out.write(off.getBytes());
         out.write("<![CDATA[".getBytes());
         if (value != null)
            out.write(value.getBytes());
         out.write("]]>".getBytes());
      }
      else if (node instanceof Comment) {
         String value = node.getNodeValue();
         out.write(off.getBytes());
         out.write("<!-- ".getBytes());
         if (value != null)
            out.write(value.getBytes());
         out.write(" -->".getBytes());
      }
      else if (node instanceof Text) {
         String value = node.getNodeValue();
         if (value != null && value.trim().length() > 0)
            out.write(value.getBytes());
      }

      return out;
   }
   
   public static final ByteArrayOutputStream writeNode(Node node) throws IOException  {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      out = write(out, node, "");
      out.flush();
      return out;
   }
   
   
   
  /**
   * Dumo the DOM nodes to a XML string. 
   *
   * (DOM) Level 1 and DOM Level 2 (The org.w3c.dom API is DOM Level 2 API)
   * specifications do not have a standard method for loading and saving an XML document.
   * Instead, we use vendor (crimson) specific code for input to and output from DOM parsers,
   * which is a disadvantage when portability is a requirement.
   */
   public static final java.io.ByteArrayOutputStream write(org.w3c.dom.Node node) throws IOException
   {
      java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
/*
      if (getJvmXmlVersionToUse() == 13) {
         if (document.getClass().getName().equals("org.apache.crimson.tree.XmlDocument")) {
            //if (document instanceof org.apache.crimson.tree.XmlDocument) {
            //   ((org.apache.crimson.tree.XmlDocument)document).write(out/*, encoding* /);
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
*/      
      if (getJvmXmlVersionToUse() == 14) {
         // see http://java.sun.com/xml/jaxp/dist/1.1/docs/tutorial/xslt/2_write.html
         try {
            if (log.isLoggable(Level.FINE)) log.fine("write - Using JDK 1.4 DOM implementation");
            javax.xml.transform.TransformerFactory tf = javax.xml.transform.TransformerFactory.newInstance();
            javax.xml.transform.Transformer t = tf.newTransformer();
            // t.setOutputProperty(javax.xml.transform.OutputKeys.DOCTYPE_SYSTEM, PROPS_DTD_URI);
            t.setOutputProperty(javax.xml.transform.OutputKeys.INDENT, "yes");
            t.setOutputProperty(javax.xml.transform.OutputKeys.METHOD, "xml");
            t.setOutputProperty(javax.xml.transform.OutputKeys.ENCODING, ENCODING);
            //t.setOutputProperty(javax.xml.transform.OutputKeys.CDATA_SECTION_ELEMENTS, "");
            t.transform(new javax.xml.transform.dom.DOMSource(node), new javax.xml.transform.stream.StreamResult(out));
         }
         catch(Exception e) {
            e.printStackTrace();
            log.severe("Code to write XML-ASCII has failed for document class=" + node.getClass().getName() + ": " + e.toString());
         }
         return out;
      }

      try {
         if (log.isLoggable(Level.FINE)) log.fine("write - Using JDK 1.5 DOM implementation");
         // JDK 1.5 DOM Level 3 conforming:

         /* Works only with lib/ant/xercesImpl.jar in the CLASSPATH
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
         */

         Class clazz_DOMImplementationLS = java.lang.Class.forName("org.w3c.dom.ls.DOMImplementationLS");

         /* org.w3c.dom.ls.DOMImplementationLS */
         Object implls = null;
         if (node instanceof Document)
            implls = ((Document)node).getImplementation();
         else
            implls = node.getOwnerDocument().getImplementation();

         Class[] paramCls = new Class[0];
         Object[] params = new Object[0];
         java.lang.reflect.Method method_createLSSerializer = clazz_DOMImplementationLS.getMethod("createLSSerializer", paramCls);
         java.lang.reflect.Method method_createLSOutput = clazz_DOMImplementationLS.getMethod("createLSOutput", paramCls);

         /* org.w3c.dom.ls.LSSerializer */
         Object domWriter = method_createLSSerializer.invoke(implls, params);

         /* org.w3c.dom.ls.LSOutput */
         Class clazz_LSOutput = java.lang.Class.forName("org.w3c.dom.ls.LSOutput");
         Object output = method_createLSOutput.invoke(implls, params);

         paramCls = new Class[] { java.io.OutputStream.class };
         params = new Object[] { out };
         java.lang.reflect.Method method_setByteStream = clazz_LSOutput.getMethod("setByteStream", paramCls);
         method_setByteStream.invoke(output, params);

         paramCls = new Class[] { java.lang.String.class };
         params = new Object[] { ENCODING };
         java.lang.reflect.Method method_setEncoding = clazz_LSOutput.getMethod("setEncoding", paramCls);
         method_setEncoding.invoke(output, params);

         paramCls = new Class[] { org.w3c.dom.Node.class, clazz_LSOutput };
         params = new Object[] { node, output };
         java.lang.reflect.Method method_write = domWriter.getClass().getMethod("write", paramCls);
         method_write.invoke(domWriter, params);
      }
      catch(Exception e) {
         e.printStackTrace();
         log.severe("Code to write XML-ASCII has failed for document class=" + node.getClass().getName() + ": " + e.toString());
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
    * @param the node to merge into the DOM tree, it is invalid after this call
    * @return The node added or null
    */
   public static final org.w3c.dom.Node mergeNode(org.w3c.dom.Document document, org.w3c.dom.Node node)
   {
      if (log.isLoggable(Level.FINER)) log.finer("mergeNode()");
      if (log.isLoggable(Level.FINEST)) log.finest("mergeNode=" + node.toString());

      if (getJvmXmlVersionToUse() == 13 || getJvmXmlVersionToUse() == 14) {
         //if (document instanceof org.apache.crimson.tree.XmlDocument) {
         if (document.getClass().getName().equals("org.apache.crimson.tree.XmlDocument")) {
            //((org.apache.crimson.tree.XmlDocument)document).changeNodeOwner(node); // not DOM portable
            try {
               Class[] paramCls = new Class[] { org.w3c.dom.Node.class };
               Object[] params = new Object[] { node };
               java.lang.reflect.Method method = document.getClass().getMethod("changeNodeOwner", paramCls);
               method.invoke(document, params);
               return document.getDocumentElement().appendChild(node);
            }
            catch(Exception e) {
               log.severe("Code to merge XML-documents adoptNode() has failed for document class=" + document.getClass().getName() + ": " + e.toString());
            }
         }
         else {
            log.severe("Code to merge XML-documents is missing for document class=" + document.getClass().getName());
         }
         return null;
      }
      try {
         if (log.isLoggable(Level.FINE)) log.fine("mergeNode - Using JDK 1.5 DOM implementation");
         // DOM Level 3 WD - Experimental
         // com/sun/org/apache/xerces/internal/dom/CoreDocumentImpl.adoptNode()
         // Changes the ownerDocument of a node, its children, as well as the attached attribute nodes if there are any.
         // This effectively allows moving a subtree from one document to another (unlike importNode() which create a copy of the source node instead of moving it). 
         // Note: This failed as our attribute value got lost (2006-02-07 marcel):
         //  Befor: <key oid="aTopic"><AA name="HELLO"/></key>
         //  After: <key oid="aTopic"><AA name=""/></key>
         // but never happenend again after changes (did we remerge an invalid DOM?)
         //document.adoptNode(node);   -> Use reflection for backward compatibility
         /*
         Class[] paramCls = new Class[] { org.w3c.dom.Node.class };
         Object[] params = new Object[] { node };
         java.lang.reflect.Method method = document.getClass().getMethod("adoptNode", paramCls);
         method.invoke(document, params);
         return document.getDocumentElement().appendChild(node);
         */
         
         // importNode introduced in DOM Level 2
         // It makes a deep clone and is slower
         // The source node is not altered or removed from the original document;
         // this method creates a new copy of the source node.
         //Class[] paramCls = new Class[] { org.w3c.dom.Node.class, boolean.class };
         //Object[] params = new Object[] { node, Boolean.TRUE };
         //java.lang.reflect.Method method = document.getClass().getMethod("importNode", paramCls);
         //method.invoke(document, params);
         org.w3c.dom.Node newNode = document.importNode(node, true);
         return document.getDocumentElement().appendChild(newNode);
      }
      catch(/*org.w3c.dom.DOMException*/Exception e) {
         log.severe("Code to merge XML-documents adoptNode() has failed for document class=" + document.getClass().getName() + ": " + e.toString());
      }
      return null;
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
      if (log.isLoggable(Level.FINER)) log.finer("getTreeWalker()");
      if (node == null) {
         node = document.getFirstChild();
      }

      if (getJvmXmlVersionToUse() == 13 || getJvmXmlVersionToUse() == 14) {
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

   /**
    * Encapsulate the given string with CDATA, escapes "]]>" tokens in str.
    * Please use for dumps only as it can't handle internally used ]]>
    * (no Base64 encoding is done)
    */
   public static String escape(String str) {
      // TODO: Move to something like a XmlUtil class
      int protect = protectionNeeded(str);
      if (protect == 0) return str;
      if (protect == 2) {
         //System.out.println("Can't handle strings containing a CDATA end"
         //      + " section ']]>', as i won't make Base64");
         //return str;
      }
      str = (str == null) ? "" : str;
      int index;
      while ((index = str.indexOf("]]>")) != -1) {
         String tmp = str;
         str = tmp.substring(0, index + 2);
         str += "&gt;";
         str += tmp.substring(index + 3);
         //Thread.dumpStack();
         //System.out.println("Can't handle strings containing a CDATA end"
         //      + " section ']]>', i'll escape it to: " + str);
      }
      return "<![CDATA[" + str + "]]>";
   }
   
   /**
    * If value contains XML harmful characters it needs to be
    * wrapped by CDATA or encoded to Base64. 
    * @param value The string to verify
    * @return 0 No protection necessary
    *         1 Protection with CDATA is needed
    *         2 Protection with Base64 is needed
    */
   public static int protectionNeeded(String value) {
      // TODO: Move to something like a XmlUtil class
      /* Handle other special characters
     private static final char[] NULL = "&#x0;".toCharArray();
    private static final char[] AMP = "&amp;".toCharArray();
    private static final char[] LT = "&lt;".toCharArray();
    private static final char[] GT = "&gt;".toCharArray();
    private static final char[] SLASH_R = "&#x0D;".toCharArray();
    private static final char[] QUOT = "&quot;".toCharArray();
    private static final char[] APOS = "&apos;".toCharArray();
    private static final char[] CLOSE = "</".toCharArray();
   */
      if (value == null) return 0;
      if (value.indexOf("]]>") >= 0)
         return 2;
      for (int i=0; i<value.length(); i++) {
         int c = value.charAt(i);
         if (c == '<' || c == '&')
            return 1;
      }
      return 0;
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
      if (log.isLoggable(Level.FINE)) log.trace(ME, "replaceNode=" + oldNode.toString());

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

      if (log.isLoggable(Level.FINE)) log.trace(ME, "Successfully replaced node");
   }
    */
   
   // java org.xmlBlaster.util.XmlNotPortable
   public static void main(String[] args) {
      Global glob = new Global(args);
      
      // Instantiate the xmlBlaster DOM tree with <xmlBlaster> root node (DOM portable)
      org.w3c.dom.Document bigDoc = null;
      String xml = "<?xml version='1.0' encoding='ISO-8859-1' ?>\n" +
                   "<xmlBlaster></xmlBlaster>";
      java.io.StringReader reader = new java.io.StringReader(xml);
      org.xml.sax.InputSource input = new org.xml.sax.InputSource(reader);
      try {
         DocumentBuilderFactory dbf = glob.getDocumentBuilderFactory();
         //dbf.setNamespaceAware(true);
         //dbf.setCoalescing(true);
         //dbf.setValidating(false);
         //dbf.setIgnoringComments(true);
         DocumentBuilder db = dbf.newDocumentBuilder ();
         bigDoc = db.parse(input);
      } catch (Exception e) {
         e.printStackTrace();
      }

      
      xml = "<key oid='aTopic'><AA name='HELLO'/></key>";
      try {
         XmlToDom dom = new XmlToDom(glob, xml);
         System.out.println("Before merge: " + dom.domToXml(""));
         
         org.w3c.dom.Node newNode = XmlNotPortable.mergeNode(bigDoc, dom.getRootNode());
         
         //org.w3c.dom.Node newNode = bigDoc.importNode(dom.getRootNode(), true);
         //bigDoc.getDocumentElement().appendChild(newNode);
         
         //bigDoc.adoptNode(dom.getRootNode());

         System.out.println("After merge: " + XmlNotPortable.write(newNode));
         System.out.println("After merge complete: " + XmlNotPortable.write(bigDoc));
      } catch (Exception e) {
         e.printStackTrace();
      }
   }
}

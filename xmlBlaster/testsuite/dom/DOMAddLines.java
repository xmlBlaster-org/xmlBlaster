/*
 * (C) Copyright IBM Corp. 1999  All rights reserved.
 *
 * US Government Users Restricted Rights Use, duplication or
 * disclosure restricted by GSA ADP Schedule Contract with IBM Corp.
 *
 * The program is provided "as is" without any warranty express or
 * implied, including the warranty of non-infringement and the implied
 * warranties of merchantibility and fitness for a particular purpose.
 * IBM will not be liable for any damages suffered by you as a result
 * of using the Program. In no event will IBM be liable for any
 * special, indirect or consequential damages or lost profits even if
 * IBM has been advised of the possibility of their occurrence. IBM
 * will not be liable for any third party claims against you.
 */

package testsuite.dom;                    

import java.io.*;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.w3c.dom.NamedNodeMap;
import org.xml.sax.Locator;
import org.xml.sax.helpers.*;
import com.ibm.xml.dom.NodeImpl;
import com.ibm.xml.parsers.DOMParser;
import com.ibm.xml.parsers.NonValidatingDOMParser;

/**
 * A sample of Adding lines to the DOM Node. This sample program illustrates:
 * - How to override methods from  DocumentHandler ( XMLDocumentHandler) 
 * - How to turn off ignorable white spaces by overriding ignorableWhiteSpace
 * - How to use the SAX Locator to return row position ( line number of DOM element).
 * - How to attach user defined Objects to Nodes using method setUserData
 * This example relies on the following:
 * - Turning off the "fast" DOM so we can use set expansion to FULL 
 * @version 
 */

public class DOMAddLines extends DOMParser {

   /** Print writer. */
   private PrintWriter out;
   static private boolean NotIncludeIgnorableWhiteSpaces = false; 


   public DOMAddLines( String inputName ) {
      this.setNodeExpansion( NonValidatingDOMParser.FULL );
      try {
         this.parse( inputName );
         out = new PrintWriter(new OutputStreamWriter(System.out, "UTF8"));
      } catch ( IOException e ) {
         System.err.println( "except" + e );
      } catch ( org.xml.sax.SAXException e ) {
         System.err.println( "except" + e );
      }
   } // constructor

   /** Prints the specified node, recursively. */
   public void print(Node node) {
      // is there anything to do?
      if ( node == null ) {
         return;
      }

      String lineRowColumn = (String ) ((NodeImpl) node).getUserData();

      int type = node.getNodeType();
      switch ( type ) {
         // print document
         case Node.DOCUMENT_NODE: {
               out.println(  lineRowColumn + ":" + "<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
               print( ((Document)node).getDocumentElement());
               out.flush();
               break;
            }

            // print element with attributes
         case Node.ELEMENT_NODE: {
               out.print( lineRowColumn + ":" + '<');
               out.print(node.getNodeName());
               Attr attrs[] = sortAttributes(node.getAttributes());
               for ( int i = 0; i < attrs.length; i++ ) {
                  Attr attr = attrs[i];
                  out.print(' ');
                  out.print(attr.getNodeName());
                  out.print("=\"");
                  out.print( attr.getNodeValue());
                  out.print('"');
               }
               out.print('>');
               NodeList children = node.getChildNodes();
               if ( children != null ) {
                  int len = children.getLength();
                  for ( int i = 0; i < len; i++ ) {
                     print(children.item(i));
                  }
               }
               break;
            }

            // handle entity reference nodes
         case Node.ENTITY_REFERENCE_NODE: {
               out.print('&');
               out.print(node.getNodeName());
               out.print(';');
               break;
            }

            // print cdata sections
         case Node.CDATA_SECTION_NODE: {
               out.print("<![CDATA[");
               out.print(node.getNodeValue());
               out.print("]]>");
               break;
            }

            // print text
         case Node.TEXT_NODE: {
               out.print(  node.getNodeValue());
               break;
            }

            // print processing instruction
         case Node.PROCESSING_INSTRUCTION_NODE: {
               out.print("<?");
               out.print(node.getNodeName());
               String data = node.getNodeValue();
               if ( data != null && data.length() > 0 ) {
                  out.print(' ');
                  out.print(data);
               }
               out.print("?>");
               break;
            }
      }

      if ( type == Node.ELEMENT_NODE ) {
         out.print("</");
         out.print(node.getNodeName());
         out.print('>');
      }

      out.flush();

   } // print(Node)


   /** Returns a sorted list of attributes. */
   private Attr[] sortAttributes(NamedNodeMap attrs) {

      int len = (attrs != null) ? attrs.getLength() : 0;
      Attr array[] = new Attr[len];
      for ( int i = 0; i < len; i++ ) {
         array[i] = (Attr)attrs.item(i);
      }
      for ( int i = 0; i < len - 1; i++ ) {
         String name  = array[i].getNodeName();
         int    index = i;
         for ( int j = i + 1; j < len; j++ ) {
            String curName = array[j].getNodeName();
            if ( curName.compareTo(name) < 0 ) {
               name  = curName;
               index = j;
            }
         }
         if ( index != i ) {
            Attr temp    = array[i];
            array[i]     = array[index];
            array[index] = temp;
         }
      }

      return (array);

   } // sortAttributes(NamedNodeMap):Attr[]

   /* Methods that we override */

   /*   We override startElement callback  from DocumentHandler */

   public void startElement(int elementNameIndex, int attrListIndex) throws Exception 
   {
      super.startElement(elementNameIndex, attrListIndex);
      NodeImpl node = (NodeImpl)getCurrentNode();       // Get current node
      Locator location = new LocatorImpl(getLocator()); // Get line location

      node.setUserData(  String.valueOf( location.getLineNumber() ) ); // Save location String into node
   } //startElement 

   /* We override startDocument callback from DocumentHandler */

   public void startDocument(int versionIndex, int encodingIndex,
                                   int standAloneIndex)
   {
     super.startDocument( versionIndex, encodingIndex,
                                    standAloneIndex);
     NodeImpl node = (NodeImpl)getCurrentNode();       // Get current node
     Locator location = new LocatorImpl(getLocator()); // Get line location

     node.setUserData(  String.valueOf( location.getLineNumber() ) ); // Save location String into node
  } //startDocument 
   

   public void ignorableWhitespace(int dataIndex, boolean cdataSection) throws Exception
    {
    if(! NotIncludeIgnorableWhiteSpaces )
       super.ignorableWhitespace( dataIndex, cdataSection);
    else
       ;// Ignore ignorable white spaces
    }// ignorableWhitespace
   


   //
   // Main
   //

   /** Main program entry point. */
   public static void main(String argv[]) {
      // is there anything to do?
      if ( argv.length == 0 ) {
         printUsage();
         System.exit(1);
      }
      // check parameters

      for ( int i = 0; i < argv.length; i++ ) {
         String arg = argv[i];

         // options
         if ( arg.startsWith("-") ) {
            if ( arg.equals("-h") ) {
               printUsage();
               System.exit(1);
            }
            if (arg.equals("-i")) {
                   NotIncludeIgnorableWhiteSpaces = true;
                   continue;
               }
            
         }
      // DOMAddLine parse and print

      DOMAddLines domAddExample = new DOMAddLines( arg );
      Document doc             = domAddExample.getDocument();
      domAddExample.print( doc );

     }
   } // main(String[])

   /** Prints the usage. */
   private static void printUsage() {
      System.err.println("usage: jre dom.DOMAddLines uri ...");
      System.err.println();
      System.err.println("  -h       This help screen.");
      System.err.println("  -i       don't print ignorable white spaces");

   } // printUsage()

}

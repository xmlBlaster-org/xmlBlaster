/*------------------------------------------------------------------------------
Name:      XmlUtility.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Demo code for a svg client using batik
Version:   $Id$
------------------------------------------------------------------------------*/
package javaclients.svg.batik;

import java.io.Writer;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.IOException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.Attr;
import org.w3c.dom.NamedNodeMap;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;


 /**
  * Used to retrieve a default Document (of the underling implementation)
  * @author $Author$ (laghi@swissinfo.org)
  */
public class XmlUtility {

   private static final boolean fCanonical = false;

   public static Document getDocument () throws ParserConfigurationException
   {
      return
         DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
   }


   public static Document parse(InputSource inputSource)
   throws ParserConfigurationException, IOException, SAXException
   {
      return
         DocumentBuilderFactory.newInstance().
            newDocumentBuilder().parse(inputSource);
   }


   public static void write (Node node, Writer writer)
   {
      PrintWriter printWriter = new PrintWriter(writer);
      write(node, printWriter);
   }


   public static String write (Node node)
   {
      StringWriter writer = new StringWriter();
      write(node, writer);
      return writer.toString();
   }


   public static void write (Node node, PrintWriter fOut)
   {
        // is there anything to do?
        if (node == null) {
            return;
        }

        short type = node.getNodeType();
        switch (type) {
            case Node.DOCUMENT_NODE: {
                if (!fCanonical) {
                    fOut.println("<?xml version=\"1.0\" encoding=\"iso-8859-1\"?>");
                    fOut.flush();
                }

                Document document = (Document)node;
                write(document.getDocumentElement(), fOut);
                break;
            }

            case Node.ELEMENT_NODE: {
                fOut.print('<');
                fOut.print(node.getNodeName());
                Attr attrs[] = sortAttributes(node.getAttributes());
                for (int i = 0; i < attrs.length; i++) {
                    Attr attr = attrs[i];
                    fOut.print(' ');
                    fOut.print(attr.getNodeName());
                    fOut.print("=\"");
                    normalizeAndPrint(attr.getNodeValue(), fOut);
                    fOut.print('"');
                }
                fOut.print('>');
                fOut.flush();

                Node child = node.getFirstChild();
                while (child != null) {
                    write(child, fOut);
                    child = child.getNextSibling();
                }
                break;
            }

            case Node.ENTITY_REFERENCE_NODE: {
                if (fCanonical) {
                    Node child = node.getFirstChild();
                    while (child != null) {
                        write(child, fOut);
                        child = child.getNextSibling();
                    }
                }
                else {
                    fOut.print('&');
                    fOut.print(node.getNodeName());
                    fOut.print(';');
                    fOut.flush();
                }
                break;
            }

            case Node.CDATA_SECTION_NODE: {
                if (fCanonical) {
                    normalizeAndPrint(node.getNodeValue(), fOut);
                }
                else {
                    fOut.print("<![CDATA[");
                    fOut.print(node.getNodeValue());
                    fOut.print("]]>");
                }
                fOut.flush();
                break;
            }

            case Node.TEXT_NODE: {
                normalizeAndPrint(node.getNodeValue(),fOut);
                fOut.flush();
                break;
            }

            case Node.PROCESSING_INSTRUCTION_NODE: {
                fOut.print("<?");
                fOut.print(node.getNodeName());
                String data = node.getNodeValue();
                if (data != null && data.length() > 0) {
                    fOut.print(' ');
                    fOut.print(data);
                }
                fOut.println("?>");
                fOut.flush();
                break;
            }
        }

        if (type == Node.ELEMENT_NODE) {
            fOut.print("</");
            fOut.print(node.getNodeName());
            fOut.print('>');
            fOut.flush();
        }

    } // write(Node)


    /** Returns a sorted list of attributes. */
    protected static Attr[] sortAttributes(NamedNodeMap attrs) {

        int len = (attrs != null) ? attrs.getLength() : 0;
        Attr array[] = new Attr[len];
        for (int i = 0; i < len; i++) {
            array[i] = (Attr)attrs.item(i);
        }
        for (int i = 0; i < len - 1; i++) {
            String name = array[i].getNodeName();
            int index = i;
            for (int j = i + 1; j < len; j++) {
                String curName = array[j].getNodeName();
                if (curName.compareTo(name) < 0) {
                    name = curName;
                    index = j;
                }
            }
            if (index != i) {
                Attr temp = array[i];
                array[i] = array[index];
                array[index] = temp;
            }
        }

        return array;

    } // sortAttributes(NamedNodeMap):Attr[]

    //
    // Protected methods
    //

    /** Normalizes and prints the given string. */
    protected static void normalizeAndPrint(String s, PrintWriter fOut) {

        int len = (s != null) ? s.length() : 0;
        for (int i = 0; i < len; i++) {
            char c = s.charAt(i);
            normalizeAndPrint(c, fOut);
        }

    } // normalizeAndPrint(String)

    /** Normalizes and print the given character. */
    protected static void normalizeAndPrint(char c, PrintWriter fOut) {

        switch (c) {
            case '<': {
                fOut.print("&lt;");
                break;
            }
            case '>': {
                fOut.print("&gt;");
                break;
            }
            case '&': {
                fOut.print("&amp;");
                break;
            }
            case '"': {
                fOut.print("&quot;");
                break;
            }
            case '\r':
            case '\n': {
                if (fCanonical) {
                    fOut.print("&#");
                    fOut.print(Integer.toString(c));
                    fOut.print(';');
                    break;
                }
                // else, default print char
            }
            default: {
                fOut.print(c);
            }
        }

    } // normalizeAndPrint(char)

}
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

import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;


/**
 * A sample DOM filter. This sample program illustrates how to
 * use the Document#getElementsByTagName() method to quickly
 * and easily locate elements by name.
 *
 * @version Revision: 37 1.1 samples/dom/DOMFilter.java, samples, xml4j2, xml4j2_0_15
 */
public class DOMFilter {

    //
    // Constants
    //

    /** Default parser name. */
    private static final String
        DEFAULT_PARSER_NAME = "dom.wrappers.DOMParser";

    //
    // Public static methods
    //

    /** Prints the specified elements in the given document. */
    public static void print(String parserWrapperName, String uri,
                             String elementName, String attributeName) {

        try {
            // parse document
            DOMParserWrapper parser =
                (DOMParserWrapper)Class.forName(parserWrapperName).newInstance();
            Document document = parser.parse(uri);

            // get elements that match
            NodeList elements = document.getElementsByTagName(elementName);

            // print nodes
            print(elements, attributeName);
        }
        catch (Exception e) {
            e.printStackTrace(System.err);
        }

    } // print(String,String,String,String)

    //
    // Private static methods
    //

    /**
     * Prints the contents of the given element node list. If the given
     * attribute name is non-null, then all of the elements are printed
     * out
     */
    private static void print(NodeList elements, String attributeName) {

        // is there anything to do?
        if (elements == null) {
            return;
        }

        // print all elements
        if (attributeName == null) {
            int elementCount = elements.getLength();
            for (int i = 0; i < elementCount; i++) {
                Element element = (Element)elements.item(i);
                print(element, element.getAttributes());
            }
        }

        // print elements with given attribute name
        else {
            int elementCount = elements.getLength();
            for (int i = 0; i < elementCount; i++) {
                Element      element    = (Element)elements.item(i);
                NamedNodeMap attributes = element.getAttributes();
                if (attributes.getNamedItem(attributeName) != null) {
                    print(element, attributes);
                }
            }
        }

    } // print(NodeList,String)

    /** Prints the specified element. */
    private static void print(Element element, NamedNodeMap attributes) {

        System.out.print('<');
        System.out.print(element.getNodeName());
        if (attributes != null) {
            int attributeCount = attributes.getLength();
            for (int i = 0; i < attributeCount; i++) {
                Attr attribute = (Attr)attributes.item(i);
                System.out.print(' ');
                System.out.print(attribute.getNodeName());
                System.out.print("=\"");
                System.out.print(normalize(attribute.getNodeValue()));
                System.out.print('"');
            }
        }
        System.out.println('>');

    } // print(Element,NamedNodeMap)

    /** Normalizes the given string. */
    private static String normalize(String s) {
        StringBuffer str = new StringBuffer();

        int len = (s != null) ? s.length() : 0;
        for (int i = 0; i < len; i++) {
            char ch = s.charAt(i);
            switch (ch) {
                case '<': {
                    str.append("&lt;");
                    break;
                }
                case '>': {
                    str.append("&gt;");
                    break;
                }
                case '&': {
                    str.append("&amp;");
                    break;
                }
                case '"': {
                    str.append("&quot;");
                    break;
                }
                case '\r':
                case '\n': {
                    str.append("&#");
                    str.append(Integer.toString(ch));
                    str.append(';');
                    break;
                }
                default: {
                    str.append(ch);
                }
            }
        }

        return str.toString();

    } // normalize(String):String

    //
    // Main
    //

    /** Main program entry point. */
    public static void main(String argv[]) {

        // is there anything to do?
        if (argv.length == 0) {
            printUsage();
            System.exit(1);
        }

        // vars
        String parserName    = DEFAULT_PARSER_NAME;
        String elementName   = "*"; // all elements
        String attributeName = null;

        // check parameters
        for (int i = 0; i < argv.length; i++) {
            String arg = argv[i];

            // options
            if (arg.startsWith("-")) {
                if (arg.equals("-p")) {
                    if (i == argv.length - 1) {
                        System.err.println("error: missing parser name");
                        System.exit(1);
                    }
                    parserName = argv[++i];
                    continue;
                }

                if (arg.equals("-e")) {
                    if (i == argv.length - 1) {
                        System.err.println("error: missing element name");
                        System.exit(1);
                    }
                    elementName = argv[++i];
                    continue;
                }

                if (arg.equals("-a")) {
                    if (i == argv.length - 1) {
                        System.err.println("error: missing attribute name");
                        System.exit(1);
                    }
                    attributeName = argv[++i];
                    continue;
                }

                if (arg.equals("-h")) {
                    printUsage();
                    System.exit(1);
                }
            }

            // print uri
            System.err.println(arg+':');
            print(parserName, arg, elementName, attributeName);
        }

    } // main(String[])

    /** Prints the usage. */
    private static void printUsage() {

        System.err.println("usage: java dom.DOMFilter (options) uri ...");
        System.err.println();
        System.err.println("options:");
        System.err.println("  -p name  Specify DOM parser wrapper by name.");
        System.err.println("           Default parser: "+DEFAULT_PARSER_NAME);
        System.err.println("  -e name  Specify element name to search for. Default is \"*\".");
        System.err.println("  -a name  Specify attribute name of specified elements.");
        System.err.println("  -h       This help screen.");

    } // printUsage()

} // class DOMFilter

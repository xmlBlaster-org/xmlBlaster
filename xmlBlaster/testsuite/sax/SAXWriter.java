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

package testsuite.sax;                    
                    
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;

import testsuite.sax.AttributeListImpl;

import org.xml.sax.AttributeList;
import org.xml.sax.HandlerBase;
import org.xml.sax.Parser;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.ParserFactory;

/**
 * A sample SAX writer. This sample program illustrates how to
 * register a SAX DocumentHandler and receive the callbacks in
 * order to print a document that is parsed.
 *
 * @version Revision: 06 1.5 samples/sax/SAXWriter.java, samples, xml4j2, xml4j2_0_15 
 */
public class SAXWriter 
    extends HandlerBase {

    //
    // Constants
    //

    /** Default parser name. */
    private static final String 
        DEFAULT_PARSER_NAME = "com.ibm.xml.parsers.SAXParser";

    //
    // Data
    //

    /** Print writer. */
    protected PrintWriter out;

    /** Canonical output. */
    protected boolean canonical;

    //
    // Constructors
    //

    /** Default constructor. */
    public SAXWriter(boolean canonical) throws UnsupportedEncodingException {
        this(null, canonical);
    }

    protected SAXWriter(String encoding, boolean canonical) throws UnsupportedEncodingException {

        if (encoding == null) {
            encoding = "UTF8";
        }

        out = new PrintWriter(new OutputStreamWriter(System.out, encoding));
        this.canonical = canonical;

    } // <init>(String,boolean)

    //
    // Public static methods
    //

    /** Prints the output from the SAX callbacks. */
    public static void print(String parserName, String uri, boolean canonical) {

        try {
            HandlerBase handler = new SAXWriter(canonical);

            Parser parser = ParserFactory.makeParser(parserName);
            parser.setDocumentHandler(handler);
            parser.setErrorHandler(handler);
            parser.parse(uri);
        }
        catch (Exception e) {
            e.printStackTrace(System.err);
        }

    } // print(String,String,boolean)

    //
    // DocumentHandler methods
    //

    /** Processing instruction. */
    public void processingInstruction(String target, String data) {

        out.print("<?");
        out.print(target);
        if (data != null && data.length() > 0) {
            out.print(' ');
            out.print(data);
        }
        out.print("?>");

    } // processingInstruction(String,String)

    /** Start document. */
    public void startDocument() {

        if (!canonical) {
            out.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        }

    } // startDocument()

    /** Start element. */
    public void startElement(String name, AttributeList attrs) {

        out.print('<');
        out.print(name);
        if (attrs != null) {
            attrs = sortAttributes(attrs);
            int len = attrs.getLength();
            for (int i = 0; i < len; i++) {
                out.print(' ');
                out.print(attrs.getName(i));
                out.print("=\"");
                out.print(normalize(attrs.getValue(i)));
                out.print('"');
            }
        }
        out.print('>');

    } // startElement(String,AttributeList)

    /** Characters. */
    public void characters(char ch[], int start, int length) {

        out.print(normalize(new String(ch, start, length)));

    } // characters(char[],int,int);

    /** Ignorable whitespace. */
    public void ignorableWhitespace(char ch[], int start, int length) {

        characters(ch, start, length);

    } // ignorableWhitespace(char[],int,int);

    /** End element. */
    public void endElement(String name) {

        out.print("</");
        out.print(name);
        out.print('>');

    } // endElement(String)

    /** End document. */
    public void endDocument() {

        out.flush();

    } // endDocument()

    //
    // ErrorHandler methods
    //

    /** Warning. */
    public void warning(SAXParseException ex) {
        System.err.println("[Warning] "+
                           getLocationString(ex)+": "+
                           ex.getMessage());
    }

    /** Error. */
    public void error(SAXParseException ex) {
        System.err.println("[Error] "+
                           getLocationString(ex)+": "+
                           ex.getMessage());
    }

    /** Fatal error. */
    public void fatalError(SAXParseException ex) throws SAXException {
        System.err.println("[Fatal Error] "+
                           getLocationString(ex)+": "+
                           ex.getMessage());
        throw ex;
    }

    /** Returns a string of the location. */
    private String getLocationString(SAXParseException ex) {
        StringBuffer str = new StringBuffer();

        String systemId = ex.getSystemId();
        if (systemId != null) {
            int index = systemId.lastIndexOf('/');
            if (index != -1) 
                systemId = systemId.substring(index + 1);
            str.append(systemId);
        }
        str.append(':');
        str.append(ex.getLineNumber());
        str.append(':');
        str.append(ex.getColumnNumber());

        return str.toString();

    } // getLocationString(SAXParseException):String

    //
    // Protected static methods
    //

    /** Normalizes the given string. */
    protected String normalize(String s) {
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
                    if (canonical) {
                        str.append("&#");
                        str.append(Integer.toString(ch));
                        str.append(';');
                        break;
                    }
                    // else, default append char
                }
                default: {
                    str.append(ch);
                }
            }
        }

        return str.toString();

    } // normalize(String):String

    /** Returns a sorted list of attributes. */
    protected AttributeList sortAttributes(AttributeList attrs) {

        AttributeListImpl attributes = new AttributeListImpl();
        int len = (attrs != null) ? attrs.getLength() : 0;
        for (int i = 0; i < len; i++) {
            String name = attrs.getName(i);
            int count = attributes.getLength();
            int j = 0;
            while (j < count) {
                if (name.compareTo(attributes.getName(j)) < 0) {
                    break;
                }
                j++;
            }
            attributes.insertAttributeAt(j, name, attrs.getType(i), 
                                         attrs.getValue(i));
        }

        return attributes;

    } // sortAttributes(AttributeList):AttributeList

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
        String  parserName = DEFAULT_PARSER_NAME;
        boolean canonical  = false;

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

                if (arg.equals("-c")) {
                    canonical = true;
                    continue;
                }

                if (arg.equals("-h")) {
                    printUsage();
                    System.exit(1);
                }
            }

            // print uri
            System.err.println(arg+':');
            print(parserName, arg, canonical);
            System.out.println();
        }

    } // main(String[])

    /** Prints the usage. */
    private static void printUsage() {

        System.err.println("usage: java sax.SAXWriter (options) uri ...");
        System.err.println();
        System.err.println("options:");
        System.err.println("  -p name  Specify SAX parser by name.");
        System.err.println("           Default parser: "+DEFAULT_PARSER_NAME);
        System.err.println("  -c       Canonical XML output.");
        System.err.println("  -h       This help screen.");

    } // printUsage()

} // class SAXWriter

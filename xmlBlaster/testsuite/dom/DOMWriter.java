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
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import com.ibm.xml.internal.MIME2Java;


/**
 * A sample DOM writer. This sample program illustrates how to
 * traverse a DOM tree in order to print a document that is parsed.
 *
 * @version Revision: 10 1.6 samples/dom/DOMWriter.java, samples, xml4j2
 */
public class DOMWriter {

   //
   // Constants
   //

   /** Default parser name. */
   private static final String
//   DEFAULT_PARSER_NAME = "dom.wrappers.DOMParser";
   DEFAULT_PARSER_NAME = "com.ibm.xml.parsers.DOMParser";
   //
   // Data
   //

   /** Default Encoding */
   private static  String
   PRINTWRITER_ENCODING = "UTF8";

   private static String MIME2JAVA_ENCODINGS[] =
    { "Default", "UTF-8", "US-ASCII", "ISO-8859-1", "ISO-8859-2", "ISO-8859-3", "ISO-8859-4",
      "ISO-8859-5", "ISO-8859-6", "ISO-8859-7", "ISO-8859-8", "ISO-8859-9", "ISO-2022-JP",
      "SHIFT_JIS", "EUC-JP","GB2312", "BIG5", "EUC-KR", "ISO-2022-KR", "KOI8-R", "EBCDIC-CP-US",
      "EBCDIC-CP-CA", "EBCDIC-CP-NL", "EBCDIC-CP-DK", "EBCDIC-CP-NO", "EBCDIC-CP-FI", "EBCDIC-CP-SE",
      "EBCDIC-CP-IT", "EBCDIC-CP-ES", "EBCDIC-CP-GB", "EBCDIC-CP-FR", "EBCDIC-CP-AR1",
      "EBCDIC-CP-HE", "EBCDIC-CP-CH", "EBCDIC-CP-ROECE","EBCDIC-CP-YU",
      "EBCDIC-CP-IS", "EBCDIC-CP-AR2", "UTF-16"
    };


/*
   private static String JAVA_SUPPORTED_ENCODINGS[] =
   { "Default", "8859_1", "8859_2", "8859_3", "8859_4", "8859_5", "8859_6",
      "8859_7", "8859_8", "8859_9", "Cp037", "Cp273", "Cp277", "Cp278",
      "Cp280", "Cp284", "Cp285", "Cp297", "Cp420", "Cp424", "Cp437",
      "Cp500", "Cp737", "Cp775", "Cp838", "Cp850", "Cp852", "Cp855", "Cp856",
      "Cp857", "Cp860", "Cp861",
      "Cp862", "Cp863", "Cp864", "Cp865", "Cp866", "Cp868", "Cp869", "Cp870",
      "Cp871", "Cp874", "Cp875",
      "Cp918", "Cp921", "Cp922", "Cp930", "Cp933", "Cp935", "Cp937", "Cp939",
      "Cp942", "Cp948", "Cp949",
      "Cp950", "Cp964", "Cp970", "Cp1006", "Cp1025", "Cp1026", "Cp1046",
      "Cp1097", "Cp1098", "Cp1112",
      "Cp1122", "Cp1123", "Cp1124", "Cp1250", "Cp1251", "Cp1252", "Cp1253",
      "Cp1254", "Cp1255", "Cp1256",
      "Cp1257", "Cp1258", "Cp1381", "Cp1383", "Cp33722", "MS874",
      "EUCJIS", "GB2312",
       "GBK", "ISO2022CN_CNS", "ISO2022CN_GB",
      "JIS",
      "JIS0208", "KOI8_R", "KSC5601","MS874",
      "SJIS",  "Big5", "CNS11643",
      "MacArabic", "MacCentralEurope", "MacCroatian", "MacCyrillic",
      "MacDingbat", "MacGreek",
      "MacHebrew", "MacIceland", "MacRoman", "MacRomania", "MacSymbol",
      "MacThai", "MacTurkish",
      "MacUkraine", "SJIS", "Unicode", "UnicodeBig", "UnicodeLittle", "UTF8"};
*/

   /** Print writer. */
   protected PrintWriter out;

   /** Canonical output. */
   protected boolean canonical;


   public DOMWriter(String encoding, boolean canonical)
   throws UnsupportedEncodingException {
      out = new PrintWriter(new OutputStreamWriter(System.out, encoding));
      this.canonical = canonical;
   } // <init>(String,boolean)

   //
   // Constructors
   //

   /** Default constructor. */
   public DOMWriter(boolean canonical) throws UnsupportedEncodingException {
      this( getWriterEncoding(), canonical);
   }

   public static String getWriterEncoding( ) {
      return (PRINTWRITER_ENCODING);
   }// getWriterEncoding

   public static void  setWriterEncoding( String encoding ) {
      if( encoding.equalsIgnoreCase( "DEFAULT" ) )
         PRINTWRITER_ENCODING  = "UTF8";
      else if( encoding.equalsIgnoreCase( "UTF-16" ) )
         PRINTWRITER_ENCODING  = "Unicode";
      else
         PRINTWRITER_ENCODING = MIME2Java.convert( encoding );
   }// setWriterEncoding


   public static boolean isValidJavaEncoding( String encoding ) {
      for ( int i = 0; i < MIME2JAVA_ENCODINGS.length; i++ )
         if ( encoding.equals( MIME2JAVA_ENCODINGS[i] ) )
            return (true);

      return (false);
   }// isValidJavaEncoding



   /** Prints the resulting document tree. */
   public static void print(String parserWrapperName, String uri,
                            boolean canonical ) {
      try {
      //System.out.println("Hallo Node");

         DOMParserWrapper parser =
         (DOMParserWrapper)Class.forName(parserWrapperName).newInstance();
         Document document = parser.parse(uri);
         DOMWriter writer = new DOMWriter(canonical);
         System.out.println("Hallo Node");

         writer.print(document);
         System.out.println("Hallo Node");

      } catch ( Exception e ) {
         //e.printStackTrace(System.err);
      }

   } // print(String,String,boolean)


   /** Prints the specified node, recursively. */
   public void print(Node node) {
   System.out.println("Hallo Node");

      // is there anything to do?
      if ( node == null ) {
         return;
      }

      int type = node.getNodeType();
      switch ( type ) {
         // print document
         case Node.DOCUMENT_NODE: {
               if ( !canonical ) {
                  String  Encoding = this.getWriterEncoding();
                  if( Encoding.equalsIgnoreCase( "DEFAULT" ) )
                     Encoding = "UTF-8";
                  else if( Encoding.equalsIgnoreCase( "Unicode" ) )
                     Encoding = "UTF-16";
                  else
                     Encoding = MIME2Java.reverse( Encoding );

                  out.println("<?xml version=\"1.0\" encoding=\""+
                           Encoding + "\"?>");
               }
               print(((Document)node).getDocumentElement());
               out.flush();
               break;
            }

            // print element with attributes
         case Node.ELEMENT_NODE: {
               out.print('<');
               out.print(node.getNodeName());
               Attr attrs[] = sortAttributes(node.getAttributes());
               for ( int i = 0; i < attrs.length; i++ ) {
                  Attr attr = attrs[i];
                  out.print(' ');
                  out.print(attr.getNodeName());
                  out.print("=\"");
                  out.print(normalize(attr.getNodeValue()));
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
               if ( canonical ) {
                  NodeList children = node.getChildNodes();
                  if ( children != null ) {
                     int len = children.getLength();
                     for ( int i = 0; i < len; i++ ) {
                        print(children.item(i));
                     }
                  }
               } else {
                  out.print('&');
                  out.print(node.getNodeName());
                  out.print(';');
               }
               break;
            }

            // print cdata sections
         case Node.CDATA_SECTION_NODE: {
               if ( canonical ) {
                  out.print(normalize(node.getNodeValue()));
               } else {
                  out.print("<![CDATA[");
                  out.print(node.getNodeValue());
                  out.print("]]>");
               }
               break;
            }

            // print text
         case Node.TEXT_NODE: {
               out.print(normalize(node.getNodeValue()));
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
   protected Attr[] sortAttributes(NamedNodeMap attrs) {

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

      // vars
      String  parserName = DEFAULT_PARSER_NAME;
      boolean canonical  = false;
      String  encoding   = "UTF8"; // default encoding

      // check parameters
      for ( int i = 0; i < argv.length; i++ ) {
         String arg = argv[i];

         // options
         if ( arg.startsWith("-") ) {
            if ( arg.equals("-p") ) {
               if ( i == argv.length - 1 ) {
                  System.err.println("error: missing parser name");
                  System.exit(1);
               }
               parserName = argv[++i];
               continue;
            }

            if ( arg.equals("-c") ) {
               canonical = true;
               continue;
            }

            if ( arg.equals("-h") ) {
               printUsage();
               System.exit(1);
            }

            if ( arg.equals("-e") ) {
               if ( i == argv.length - 1 ) {
                  System.err.println("error: missing encoding name");
                  printValidJavaEncoding();
                  System.exit(1);
               } else {
                  encoding = argv[++i];
                  if ( isValidJavaEncoding( encoding ) )
                     setWriterEncoding( encoding );
                  else {
                     printValidJavaEncoding();
                     System.exit( 1 );
                  }
               }
               continue;
            }

         }

         // print uri
         System.err.println(arg+':');
         print(parserName, arg, canonical );
         System.err.println();
      }

   } // main(String[])


   /** Normalizes the given string. */
   protected String normalize(String s) {
      StringBuffer str = new StringBuffer();

      int len = (s != null) ? s.length() : 0;
      for ( int i = 0; i < len; i++ ) {
         char ch = s.charAt(i);
         switch ( ch ) {
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
                  if ( canonical ) {
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

      return (str.toString());

   } // normalize(String):String

   /** Prints the usage. */
   private static void printUsage() {

      System.err.println("usage: java dom.DOMWriter (options) uri ...");
      System.err.println();
      System.err.println("options:");
      System.err.println("  -p name  Specify DOM parser wrapper by name.");
      System.err.println("           Default parser: "+DEFAULT_PARSER_NAME);
      System.err.println("  -c       Canonical XML output.");
      System.err.println("  -h       This help screen.");
      System.err.println("  -e       Output Java Encoding.");
      System.err.println("           Default encoding: UTF-8");

   } // printUsage()

   private static void printValidJavaEncoding() {
      System.err.println( "    ENCODINGS:" );
      System.err.print( "   " );
      for( int i = 0;
                     i < MIME2JAVA_ENCODINGS.length; i++) {
         System.err.print( MIME2JAVA_ENCODINGS[i] + " " );
      if( (i % 7 ) == 0 ){
         System.err.println();
         System.err.print( "   " );
         }
      }

   } // printJavaEncoding()

}

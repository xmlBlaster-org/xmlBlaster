package org.xmlBlaster.engine.xmldb.dom;

import java.io.*;
import java.io.File;
import java.io.IOException;

import java.util.Properties;
import java.util.Enumeration;

import org.xml.sax.InputSource;
import org.xml.sax.SAXException;


import org.w3c.dom.*;

import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public final class DOMUtil
{

    /** Print writer. */
    private static PrintWriter _out;

    /** Canonical output. */
    private static boolean canonical=true;

    /** Default Encoding */
    private static  String PRINTWRITER_ENCODING = "UTF8";

    public static String getMimeEncoding(String s)
    {
        s = s.intern();
        for(int i = 0; i < Java2MIMEStr.length; i += 2)
            if(Java2MIMEStr[i] == s)
                return Java2MIMEStr[i + 1];

        return "UTF-8";
    }

    public static String getJavaEncoding(String s)
    {
        s = s.intern();
        for(int i = 0; i < MIME2Java.length; i += 2)
            if(MIME2Java[i] == s)
                return MIME2Java[i + 1];

        return null;
    }


    public static void parseXML(String s, String s1, Document document, int i)
        throws DOMParseException
    {
        try
        {
            SAXtoDOM.parse(s, s1, document, i);
        }
        catch(Exception exception)
        {
            throw new DOMParseException(exception);
        }
    }
 
    public static Node cloneNode(Document document, Node node, boolean flag)
    {
        Object obj = null;
        switch(node.getNodeType())
        {
        case 1: // '\001'
            Element element = document.createElement(node.getNodeName());
            NamedNodeMap namednodemap = node.getAttributes();
            if(namednodemap != null)
            {
                int i = namednodemap.getLength();
                for(int j = 0; j < i; j++)
                {
                    Attr attr1 = (Attr)namednodemap.item(j);
                    element.setAttribute(attr1.getName(), attr1.getValue());
                }

            }
            obj = element;
            break;

        case 2: // '\002'
            Attr attr = document.createAttribute(((Node) (obj)).getNodeName());
            attr.setValue(((Node) (obj)).getNodeValue());
            obj = attr;
            break;

        case 4: // '\004'
            obj = document.createCDATASection(node.getNodeValue());
            break;

        case 8: // '\b'
            obj = document.createComment(node.getNodeValue());
            break;

        case 3: // '\003'
            obj = document.createTextNode(node.getNodeValue());
            break;

        case 11: // '\013'
            obj = document.createDocumentFragment();
            break;

        case 5: // '\005'
            obj = document.createEntityReference(node.getNodeName());
            break;

        case 7: // '\007'
            ProcessingInstruction processinginstruction = (ProcessingInstruction)node;
            obj = document.createProcessingInstruction(processinginstruction.getTarget(), processinginstruction.getData());
            break;

        case 6: // '\006'
        case 9: // '\t'
        case 10: // '\n'
        default:
            return null;
        }
        if(flag)
        {
            for(Node node1 = node.getFirstChild(); node1 != null; node1 = node1.getNextSibling())
                ((Node) (obj)).appendChild(cloneNode(document, node1, true));

        }
        return ((Node) (obj));
    }

    public static Document createDocument(String s)
    {
        if(s != null)
            try
            {
                return (Document)Class.forName(s).newInstance();
            }
            catch(Exception exception) { }
        return createDocument();
    }

    public static Document createDocument()
    {
        for(int i = 0; i < docimpls.length;)
            try
            {
                return (Document)Class.forName(docimpls[i]).newInstance();
            }
            catch(Exception exception)
            {
                i++;
            }

        throw new NoClassDefFoundError("No supported DOM document implementation found");
    }

    private DOMUtil()
    {
    }

    public static final int SKIP_NO_WHITESPACE        = 0;
    public static final int SKIP_IGNORABLE_WHITESPACE = 1;
    public static final int SKIP_ALL_WHITESPACE       = 2;
    static final String MIME2Java[] = {
        "UTF-8", "UTF8", "US-ASCII", "8859_1", "ISO-8859-1", "8859_1", "ISO-8859-2", "8859_2", "ISO-8859-3", "8859_3", 
        "ISO-8859-4", "8859_4", "ISO-8859-5", "8859_5", "ISO-8859-6", "8859_6", "ISO-8859-7", "8859_7", "ISO-8859-8", "8859_8", 
        "ISO-8859-9", "8859_9", "ISO-2022-JP", "JIS", "SHIFT_JIS", "SJIS", "EUC-JP", "EUCJIS", "GB2312", "GB2312", 
        "BIG5", "Big5", "EUC-KR", "KSC5601", "ISO-2022-KR", "ISO2022KR", "KOI8-R", "KOI8_R", "EBCDIC-CP-US", "CP037", 
        "EBCDIC-CP-CA", "CP037", "EBCDIC-CP-NL", "CP037", "EBCDIC-CP-DK", "CP277", "EBCDIC-CP-NO", "CP277", "EBCDIC-CP-FI", "CP278", 
        "EBCDIC-CP-SE", "CP278", "EBCDIC-CP-IT", "CP280", "EBCDIC-CP-ES", "CP284", "EBCDIC-CP-GB", "CP285", "EBCDIC-CP-FR", "CP297", 
        "EBCDIC-CP-AR1", "CP420", "EBCDIC-CP-HE", "CP424", "EBCDIC-CP-CH", "CP500", "EBCDIC-CP-ROECE", "CP870", "EBCDIC-CP-YU", "CP870", 
        "EBCDIC-CP-IS", "CP871", "EBCDIC-CP-AR2", "CP918"
    };
    static final String Java2MIMEStr[] = {
        "UTF8", "UTF-8", "8859_1", "ISO-8859-1", "8859_2", "ISO-8859-2", "8859_3", "ISO-8859-3", "8859_4", "ISO-8859-4", 
        "8859_5", "ISO-8859-5", "8859_6", "ISO-8859-6", "8859_7", "ISO-8859-7", "8859_8", "ISO-8859-8", "8859_9", "ISO-8859-9", 
        "JIS", "ISO-2022-JP", "SJIS", "Shift_JIS", "EUCJIS", "EUC-JP", "GB2312", "GB2312", "BIG5", "Big5", 
        "KSC5601", "EUC-KR", "ISO2022KR", "ISO-2022-KR", "KOI8_R", "KOI8-R", "CP037", "EBCDIC-CP-US", "CP037", "EBCDIC-CP-CA", 
        "CP037", "EBCDIC-CP-NL", "CP277", "EBCDIC-CP-DK", "CP277", "EBCDIC-CP-NO", "CP278", "EBCDIC-CP-FI", "CP278", "EBCDIC-CP-SE", 
        "CP280", "EBCDIC-CP-IT", "CP284", "EBCDIC-CP-ES", "CP285", "EBCDIC-CP-GB", "CP297", "EBCDIC-CP-FR", "CP420", "EBCDIC-CP-AR1", 
        "CP424", "EBCDIC-CP-HE", "CP500", "EBCDIC-CP-CH", "CP870", "EBCDIC-CP-ROECE", "CP870", "EBCDIC-CP-YU", "CP871", "EBCDIC-CP-IS", 
        "CP918", "EBCDIC-CP-AR2"
    };
    static final String saximpls[] = {
        "com.sun.xml.parser.Parser", "com.jclark.xml.sax.Driver", "oracle.xml.parser.XMLParser", "com.microstar.xml.SAXDriver"
    };
    static final String docimpls[] = {
        "com.sun.xml.tree.XmlDocument", "com.ibm.xml.dom.PDocument", "com.ibm.xml.parser.TXDocument", "com.docuverse.dom.BasicDocument", "oracle.xml.parser.XMLDocument"
    };


   public static String getWriterEncoding( ) {
      return (PRINTWRITER_ENCODING);
   }// getWriterEncoding 

   public static void  setWriterEncoding( String encoding ) {
      if( encoding.equalsIgnoreCase( "DEFAULT" ) )
         PRINTWRITER_ENCODING  = "UTF8";
      else if( encoding.equalsIgnoreCase( "UTF-16" ) )
         PRINTWRITER_ENCODING  = "Unicode";
      else
         PRINTWRITER_ENCODING =  encoding;
   }// setWriterEncoding 

  
   // Prints the Node in XML out to generic Stream
   public static void toXML(Node node, Writer write)
   {
   } 

   // Prints the Node in XML to STDOUT
   public static void toXML(Node node) 
   {
      try{
         _out = new PrintWriter(new OutputStreamWriter(System.out, PRINTWRITER_ENCODING));
      }catch(UnsupportedEncodingException e){
         _out = new PrintWriter(new OutputStreamWriter(System.out));
      }
      print(node);
   }

   
   private static void  print(Node node)
   {
      // is there anything to do?
      if ( node == null ) {
         return;
      }
    
      int type = node.getNodeType();
      switch ( type ) {
         // print document
         case Node.DOCUMENT_NODE: {
               if ( !canonical ) {
                  String  Encoding = getWriterEncoding();
                  if( Encoding.equalsIgnoreCase( "DEFAULT" ) )
                     Encoding = "UTF-8";
                  else if( Encoding.equalsIgnoreCase( "Unicode" ) )
                     Encoding = "UTF-16";
                  else
                     Encoding =  Encoding;

                  _out.println("<?xml version=\"1.0\" encoding=\""+
                           Encoding + "\"?>");
               }
               print(((Document)node).getDocumentElement());
               _out.flush();
               break;
            }

            // print element with attributes
         case Node.ELEMENT_NODE: {
               _out.print('<');
               _out.print(node.getNodeName());
               Attr attrs[] = sortAttributes(node.getAttributes());
               for ( int i = 0; i < attrs.length; i++ ) {
                  Attr attr = attrs[i];
                  _out.print(' ');
                  _out.print(attr.getNodeName());
                  _out.print("=\"");
                  _out.print(normalize(attr.getNodeValue()));
                  _out.print('"');
               }
               _out.print('>');
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
                  _out.print('&');
                  _out.print(node.getNodeName());
                  _out.print(';');
               }
               break;
            }

            // print cdata sections
         case Node.CDATA_SECTION_NODE: {
               if ( canonical ) {
                  _out.print(normalize(node.getNodeValue()));
               } else {
                  _out.print("<![CDATA[");
                  _out.print(node.getNodeValue());
                  _out.print("]]>");
               }
               break;
            }

            // print text
         case Node.TEXT_NODE: {
               _out.print(normalize(node.getNodeValue()));
               break;
            }

            // print processing instruction
         case Node.PROCESSING_INSTRUCTION_NODE: {
               _out.print("<?");
               _out.print(node.getNodeName());
               String data = node.getNodeValue();
               if ( data != null && data.length() > 0 ) {
                  _out.print(' ');
                  _out.print(data);
               }
               _out.print("?>");
               break;
            }
      }
    
      if ( type == Node.ELEMENT_NODE ) {
         _out.print("</");
         _out.print(node.getNodeName());
         _out.print('>');
      }

      _out.flush();
  } 

   /** Returns a sorted list of attributes. */
   private static Attr[] sortAttributes(NamedNodeMap attrs) {

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

   /** Normalizes the given string. */
   private static String normalize(String s) {
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
                  if ( false ) {
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

   // Converts a String in a number
   public static int hashCode(String key)
   {
      return key.hashCode();
   }

   

}

package org.xmlBlaster.engine.xmldb.dom;
import java.io.*;
import org.w3c.dom.*;
import org.xml.sax.*;
import org.xml.sax.helpers.ParserFactory;

class SAXtoDOM
{
    static class SaxCreate implements DocumentHandler
    {
        public void characters(char ac[], int i, int j)
        {
            if(iws == 2 && SAXtoDOM.isWS(ac, i, j))
            {
                return;
            } else
            {
                current.appendChild(doc.createTextNode(new String(ac, i, j)));
                return;
            }
        }

        public void endDocument(){}

        public void endElement(String s)
        {
            current = current.getParentNode();
        }

        public void ignorableWhitespace(char ac[], int i, int j)
        {
            if(iws >= 1)
            {
                return;
            } else
            {
                characters(ac, i, j);
                return;
            }
        }

        public void processingInstruction(String s, String s1)
        {
            current.appendChild(doc.createProcessingInstruction(s, s1));
        }

        public void setDocumentLocator(Locator locator)
        {
        }

        public void startDocument()
        {
        }

        public void startElement(String s, AttributeList attributelist)
        {
            Element element = doc.createElement(s);
            int i = attributelist.getLength();
            for(int j = 0; j < i; j++)
                element.setAttribute(attributelist.getName(j), attributelist.getValue(j));

            current.appendChild(element);
            current = element;
        }

        final Document doc;
        Node current;
        final int iws;

        SaxCreate(Document document, int i)
        {
            current = doc = document;
            iws = i;
        }
    }


    static boolean isWS(char ac[], int i, int j)
    {
        int k = i + j;
        for(int l = i; l < k;)
            switch(ac[l])
            {
            case 11: // '\013'
            case 12: // '\f'
            case 14: // '\016'
            case 15: // '\017'
            case 16: // '\020'
            case 17: // '\021'
            case 18: // '\022'
            case 19: // '\023'
            case 20: // '\024'
            case 21: // '\025'
            case 22: // '\026'
            case 23: // '\027'
            case 24: // '\030'
            case 25: // '\031'
            case 26: // '\032'
            case 27: // '\033'
            case 28: // '\034'
            case 29: // '\035'
            case 30: // '\036'
            case 31: // '\037'
            default:
                return false;

            case 9: // '\t'
            case 10: // '\n'
            case 13: // '\r'
            case 32: // ' '
                l++;
                break;
            }

        return true;
    }

    public static void parse(String s, String xmlInstance, Document document, int i)
        throws IOException, ClassNotFoundException, IllegalAccessException, InstantiationException
    {
        try
        {
            Parser parser = ParserFactory.makeParser(s);
            parser.setDocumentHandler(new SaxCreate(document, i));
            StringReader reader = new StringReader(xmlInstance);
            parser.parse(new InputSource(reader));
        }
        catch(SAXException saxexception)
        {
            throw new RuntimeException("SAXException");
        }
    }

    SAXtoDOM(){}
}

/*------------------------------------------------------------------------------
Name:      XmlToDom.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Parse a xml-instance to DOM.
Version:   $Id: XmlToDom.java,v 1.4 2001/02/12 00:08:39 ruff Exp $
Author:    manuel.kron@gmx.net
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.persistence.mudb.dom;

import java.io.*;
import org.w3c.dom.*;
import org.xml.sax.*;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.parsers.SAXParser;
import org.xml.sax.ContentHandler;
import org.w3c.dom.Document;
import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.XmlBlasterProperty;


class XmlToDom
{
   private final String ME = "XmlToDom";
   private XmlKeyDom _xmlKeyDom;


   XmlToDom(XmlKeyDom xmlKeyDom)
   {
      this._xmlKeyDom = xmlKeyDom;
   }

   XmlKeyDom getXmlKeyDom() {
      return _xmlKeyDom;
   }

   class SaxCreate implements ContentHandler
   {
      private final String ME = "SaxCreate";
      final int iws;
      private final XmlToDom _xmlToDom;
      private org.w3c.dom.Node _rootNode = null;
      private org.w3c.dom.Node _littleRootNode = null;
      private final org.w3c.dom.Document  _document;
      private boolean first = true;

      Node getNode() { return _littleRootNode; }

      public void characters(char ac[], int i, int j)
      {
         if(iws == 2 && _xmlToDom.isWS(ac, i, j))
         {
               return;
         } else
         {
               _rootNode.appendChild(_document.createTextNode(new String(ac, i, j)));
               return;
         }
      }

      public void endDocument(){}

      public void endElement(String uri, String localName, String s)
      {
         // System.out.println("END : "+s);
         _rootNode = (Node)_rootNode.getParentNode();
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
         _rootNode.appendChild(_document.createProcessingInstruction(s, s1));
      }

      public void setDocumentLocator(Locator locator)
      {
      }

      public void startDocument()
      {
      }

      public void startElement(String uri, String localName, String s, Attributes attributelist)
      {
         Log.call(ME, "startElement("+s + ")");
         Element element=null;

         element = _document.createElement(s);
         int i = attributelist.getLength();
         for(int j = 0; j < i; j++)
            element.setAttribute(attributelist.getQName(j), attributelist.getValue(j));

         if(_littleRootNode == null)
            _littleRootNode = _rootNode.appendChild(element);
         else
            _rootNode.appendChild(element);

         _rootNode = (Node)element;

      }

      public void skippedEntity(java.lang.String name)
      {
         Log.warn(ME, "Entering skippedEntity() ..."); 
      }

      public void startPrefixMapping(java.lang.String prefix, java.lang.String uri)
      {
         Log.warn(ME, "Entering startPrefixMapping() ..."); 
      }
      
      public void endPrefixMapping(java.lang.String prefix)
      {
         Log.warn(ME, "Entering endPrefixMapping() ..."); 
      }

      SaxCreate(XmlToDom xmlToDom, Document document, int i)
      {
         Log.call(ME,"Calling SaxCreate....");

         _xmlToDom     = xmlToDom;
         _document     = document;
         _rootNode = (Node)_xmlToDom.getXmlKeyDom().getRootNode();

         iws = i;
      }

   }


   boolean isWS(char ac[], int i, int j)
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


   public Node parse(String xmlInstance, Document bigDocument) throws XmlBlasterException
   {
      SaxCreate sax = new SaxCreate(this, bigDocument, 1);
      try
      {
         SAXParserFactory spf = SAXParserFactory.newInstance();
         spf.setValidating(XmlBlasterProperty.get("javax.xml.parsers.validation", false));

         SAXParser sp = spf.newSAXParser();
         XMLReader parser = sp.getXMLReader();

         parser.setContentHandler(sax);
         //parser.setErrorHandler(sax);

         StringReader reader = new StringReader(xmlInstance);
         parser.parse(new InputSource(reader));
         return sax.getNode();
      }
      catch(Throwable e)
      {
         throw new XmlBlasterException(ME, "Error in parse: " + e.toString());
      }
   }
}

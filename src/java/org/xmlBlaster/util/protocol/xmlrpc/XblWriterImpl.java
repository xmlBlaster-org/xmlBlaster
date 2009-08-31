/*
 * Copyright 2003, 2004  The Apache Software Foundation
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.xmlBlaster.util.protocol.xmlrpc;

import org.apache.ws.commons.serialize.XMLWriter;
import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.Writer;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.XMLConstants;


/** Default implementation of {@link XMLWriter}. Works with Java 1.2 and
 * later.
 */
public class XblWriterImpl implements XMLWriter {
   
   private class FineWriter extends Writer {
    
      private Writer w;
      private StringBuffer buf;
      
      public FineWriter(Writer w) {
         this.w = w;
         buf = new StringBuffer(512);
      }
      private void flushLog() {
         log.fine(buf.toString());
         buf = new StringBuffer(512);
      }
      
      public Writer append(char c) throws IOException {
         buf.append(c);
         return w.append(c);
      }

      public Writer append(CharSequence csq, int start, int end)
            throws IOException {
         buf.append(csq, start, end);
         return w.append(csq, start, end);
      }

      public Writer append(CharSequence csq) throws IOException {
         buf.append(csq);
         return w.append(csq);
      }

      public void close() throws IOException {
         flushLog();
         w.close();
      }

      public boolean equals(Object obj) {
         return w.equals(obj);
      }

      public void flush() throws IOException {
         flushLog();
         w.flush();
      }

      public int hashCode() {
         return w.hashCode();
      }

      public String toString() {
         return w.toString();
      }

      public void write(char[] cbuf, int off, int len) throws IOException {
         buf.append(new String(cbuf, off, len));
         w.write(cbuf, off, len);
      }

      public void write(char[] cbuf) throws IOException {
         buf.append(new String(cbuf));
         w.write(cbuf);
      }

      public void write(int c) throws IOException {
         buf.append((char)c);
         w.write(c);
      }

      public void write(String str, int off, int len) throws IOException {
         buf.append(str, off, len);
         w.write(str, off, len);
      }

      public void write(String str) throws IOException {
         buf.append(str);
         w.write(str);
      }
   }
   
   private final static Logger log = Logger.getLogger(XblWriterImpl.class.getName());
   
	private static final int STATE_OUTSIDE = 0;
	private static final int STATE_IN_START_ELEMENT = 1;
	private static final int STATE_IN_ELEMENT = 2;

	private String encoding, indentString, lineFeed;
	private Writer w;
	private Locator l;
	private Map delayedPrefixes;
	int curIndent = 0;
	private int state;
	private boolean declarating, indenting, flushing;


	public void setEncoding(String pEncoding) { encoding = pEncoding; }
	public String getEncoding() { return encoding; }
	public void setDeclarating(boolean pDeclarating) { declarating = pDeclarating; }
	public boolean isDeclarating() { return declarating; }
	public void setIndenting(boolean pIndenting) { indenting = pIndenting; }
	public boolean isIndenting() { return indenting; }
	public void setIndentString(String pIndentString) { indentString = pIndentString; }
	public String getIndentString() { return indentString; }
	public void setLineFeed(String pLineFeed) { lineFeed = pLineFeed; }
	public String getLineFeed() { return lineFeed; }
	public void setFlushing(boolean pFlushing) { flushing = pFlushing; }
	public boolean isFlushing() { return flushing; }

	private boolean useCDATA;
	
	/** <p>Sets the JaxbXMLSerializers Writer.</p>
	 */
	public void setWriter(Writer pWriter) {
	   if (log.isLoggable(Level.FINE))
	      w = new FineWriter(pWriter);
	   else
	      w = pWriter;
	}

	/** <p>Returns the JaxbXMLSerializers Writer.</p>
	 */
	public Writer getWriter() {
		return w;
	}
	
	public void setUseCData(boolean use) {
	   useCDATA = use;
	}
	
	/** Sets the locator.
	 *
	 * @param pLocator A locator for use in case of errors
	 * @see #getDocumentLocator
	 */
	public void setDocumentLocator(Locator pLocator) { l = pLocator; }
	
	/** Returns the locator
	 * @return A locator previously set with setDocumentLocator or null.
	 * @see #setDocumentLocator
	 */
	public Locator getDocumentLocator() { return l; }
	
	/**
	 * <p>Starts use of a namespace prefix.</p>
	 *
	 * @param namespaceURI The namespace URI
	 * @param prefix The prefix
	 * @throws SAXException Not actually thrown, just for compliance to the interface specification.
	 */
	public void startPrefixMapping(String prefix, String namespaceURI)
	throws SAXException {
		if (delayedPrefixes == null) {
			delayedPrefixes = new java.util.HashMap();
		}
		if ("".equals(prefix)) {
			if (namespaceURI.equals(prefix)) {
				return;
			}
			prefix = XMLConstants.XMLNS_ATTRIBUTE;
		} else {
			prefix = XMLConstants.XMLNS_ATTRIBUTE + ":" + prefix;
		}
		delayedPrefixes.put(prefix, namespaceURI);
	}
	
	/** <p>Terminates use of a namespace prefix.</p>
	 *
	 * @param prefix The prefix being abandoned.
	 * @throws SAXException Not actually thrown, just for compliance to the interface specification.
	 */
	public void endPrefixMapping(String prefix) throws SAXException {
		if (delayedPrefixes != null) {
			if ("".equals(prefix)) {
				prefix = XMLConstants.XMLNS_ATTRIBUTE;
			} else {
				prefix = XMLConstants.XMLNS_ATTRIBUTE + ":" + prefix;
			}
			delayedPrefixes.remove(prefix);
		}
	}
	
	/** <p>Starts a document.</p>
	 * @throws SAXException Not actually thrown, just for compliance to the interface specification.
	 */
	public void startDocument() throws SAXException {
		if (delayedPrefixes != null) {
			delayedPrefixes.clear();
		}
		state = STATE_OUTSIDE;
		curIndent = 0;
		if (isDeclarating()  &&  w != null) {
			try {
				w.write("<?xml version=\"1.0\"");
				String enc = getEncoding();
				if (enc != null) {
					w.write(" encoding=\"");
					w.write(enc);
					w.write("\"");
				}
				w.write("?>");
				if (isIndenting()) {
					String lf = getLineFeed();
					if (lf != null) {
						w.write(lf);
					}
				}
			} catch (IOException e) {
				throw new SAXException("Failed to write XML declaration: " + e.getMessage(), e);
			}
		}
	}
	
	/** <p>This method finishs the handlers action. After calling endDocument you
	 * may start a new action by calling startDocument again.</p>
	 *
	 * @throws SAXException Not actually thrown, just for compliance to the
	 *   interface specification.
	 */  
	public void endDocument() throws SAXException {
		if (isFlushing()  &&  w != null) {
			try {
				w.flush();
			} catch (IOException e) {
				throw new SAXException("Failed to flush target writer: " + e.getMessage(), e);
			}
		}
	}
	
	/** Calls the character method with the same arguments.
	 * @param ch A string of whitespace characters being inserted into the document.
	 * @param start The index of the first character.
	 * @param length The number of characters.
	 * @throws SAXException Thrown in case of an IOException.
	 */  
	public void ignorableWhitespace(char[] ch, int start, int length)
	throws SAXException {
		characters(ch, start, length);
	}
	
	private void stopTerminator() throws java.io.IOException {
		if (state == STATE_IN_START_ELEMENT) {
			if (w != null) {
				w.write('>');
			}
			state = STATE_IN_ELEMENT;
		}
	}
	
   /** Inserts a string of characters into the document.
    * @param ch The characters being inserted. A substring, to be precise.
    * @param start Index of the first character
    * @param length Number of characters being inserted
    * @throws SAXException Thrown in case of an IOException
    */  
   public void charactersOrig(char[] ch, int start, int length) throws SAXException {
      try {
         stopTerminator();
         if (w == null) return;
         int end = start+length;
         for (int i = start;  i < end;  i++) {
            char c = ch[i];
            switch (c) {
            case '&':  w.write("&amp;"); break;
            case '<':  w.write("&lt;");  break;
            case '>':  w.write("&gt;");  break;
            case '\n':
            case '\r':
            case '\t':
               w.write(c); break;
            default:
               if (canEncode(c)) {
                  w.write(c);
               } else {
                  w.write("&#");
                  w.write(Integer.toString(c));
                  w.write(";");
               }
            break;
            }
         }
      } catch (IOException e) {
         throw new SAXException(e);
      }
   }
   
   
   /** Inserts a string of characters into the document.
    * @param ch The characters being inserted. A substring, to be precise.
    * @param start Index of the first character
    * @param length Number of characters being inserted
    * @throws SAXException Thrown in case of an IOException
    */  
   public void characters(char[] ch, int start, int length) throws SAXException {
      if (useCDATA) {
         try {
            stopTerminator();
            if (w == null) 
               return;
            wrapWithCDATAIfNecessary(ch, start, length, w);

            /*
            stopTerminator();
            if (w == null) 
               return;
            int end = start+length;
            for (int i = start;  i < end;  i++) {
               char c = ch[i];
               w.write(c);
            }
            */
         } 
         catch (IOException e) {
            throw new SAXException(e);
         }
      }
      else
         charactersOrig(ch, start, length);
   }
   
	public boolean canEncode(char c) {
		return c == '\n'  ||  (c >= ' '  &&  c < 0x7f);
	}
	
	
	/** <p>Terminates an element.</p>
	 *
	 * @param namespaceURI The namespace URI, if any, or null
	 * @param localName The local name, without prefix, or null
	 * @param qName The qualified name, including a prefix, or null
	 * @throws SAXException Thrown in case of an IOException.
	 */
	public void endElement(String namespaceURI, String localName, String qName)
			throws SAXException {
		if (isIndenting()) {
			--curIndent;
		}
		if (w != null) {
			try {
				if (state == STATE_IN_START_ELEMENT) {
					w.write("/>");
					state = STATE_OUTSIDE;
				} else {
					if (state == STATE_OUTSIDE) {
						indentMe();
					}
					w.write("</");
					w.write(qName);
					w.write('>');
				}
				state = STATE_OUTSIDE;
			} catch (java.io.IOException e) {
				throw new SAXException(e);
			}
		}
	}
	
	private void indentMe() throws java.io.IOException {
		if (w != null) {
			if (isIndenting()) {
				String s = getLineFeed();
				if (s != null) {
					w.write(s);
				}
				s = getIndentString();
				if (s != null) {
					for (int i = 0;  i < curIndent;  i++) {
						w.write(s);
					}
				}
			}
		}
	}
	
   private final boolean needsEncoding(String txt) {
      if (txt == null || txt.trim().length() < 1)
         return false;
      if (txt.indexOf('&') > -1)
         return true;
      if (txt.indexOf('<') > -1)
         return true;
      if (txt.indexOf('>') > -1)
         return true;
      if (txt.indexOf('\'') > -1)
         return true;
      if (txt.indexOf('"') > -1)
         return true;
      return false;
   }
   
   private void wrapWithCDATAIfNecessary(char[] ch, int start, int length, Writer wr) throws IOException, SAXException {
      String txt = new String(ch, start, length);
      int pos = txt.indexOf("<![CDATA[");
      boolean hasCDATA = false;
      if (pos > -1) { // check the content outside of the limits
         String prefix = txt.substring(0, pos);
         if (needsEncoding(prefix)) {
            throw new SAXException("The content " + txt + " contains CDATA and can not be parsed");
         }
         else {
            // get the posfix
            pos = txt.indexOf("]]>");
            if (pos < 0)
               throw new SAXException("The content " + txt + " must be encoded and can not be wrapped by a CDATA since no ending ]]> found");
            String postfix = txt.substring(pos+"]]>".length()+1);
            if (needsEncoding(postfix)) {
               throw new SAXException("The content " + txt + " contains CDATA and can not be parsed");
            }
         }
         hasCDATA = true;
      }
      
      boolean needsEncoding = !hasCDATA && needsEncoding(txt);
      if (needsEncoding)
         wr.write("<![CDATA[");

      int end = start+length;
      for (int i = start;  i < end;  i++) {
         char c = ch[i];
         w.write(c);
      }
      if (needsEncoding)
         wr.write("]]>");
   }

	private void writeCData(String v) throws java.io.IOException {
		int len = v.length();
		for (int j = 0;  j < len;  j++) {
			char c = v.charAt(j);
			switch (c) {
			case '&':  w.write("&amp;");  break;
			case '<':  w.write("&lt;");   break;
			case '>':  w.write("&gt;");   break;
			case '\'': w.write("&apos;"); break;
			case '"':  w.write("&quot;"); break;
			default:
				if (canEncode(c)) {
					w.write(c);
				} else {
					w.write("&#");
					w.write(Integer.toString(c));
					w.write(';');
				}
			break;
			}
		}
	}
	
	/** Starts a new element.
	 *
	 * @param namespaceURI The namespace URI, if any, or null
	 * @param localName The local name, without prefix, or null
	 * @param qName The qualified name, including a prefix, or null
	 * @param attr The element attributes
	 * @throws SAXException Thrown in case of an IOException.
	 */
	public void startElement(String namespaceURI, String localName, String qName,
			Attributes attr) throws SAXException {
		try {
			stopTerminator();
			if (isIndenting()) {
				if (curIndent > 0) {
					indentMe();
				}
				curIndent++;
			}
			
			if (w != null) {
				w.write('<');
				w.write(qName);
				if (attr != null) {
					for (int i = attr.getLength();  i > 0;) {
						w.write(' ');
						String name = attr.getQName(--i);
						w.write(name);
						if (delayedPrefixes != null) {
							delayedPrefixes.remove(name);
						}
						w.write("=\"");
						writeCData(attr.getValue(i));
						w.write('"');
					}
				}
				if (delayedPrefixes != null  &&  delayedPrefixes.size() > 0) {
					for (java.util.Iterator iter = delayedPrefixes.entrySet().iterator();
					iter.hasNext();  ) {
						java.util.Map.Entry entry = (java.util.Map.Entry) iter.next();
						w.write(' ');
						w.write((String) entry.getKey());
						w.write("=\"");
						w.write((String) entry.getValue());
						w.write('"');
					}
					delayedPrefixes.clear();
				}
			}
			state = STATE_IN_START_ELEMENT;
		} catch (java.io.IOException e) {
			throw new SAXException(e);
		}
	}
	
	/** Not actually implemented, because I don't know how to skip entities.
	 *
	 * @param ent The entity being skipped.
	 * @throws SAXException Not actually thrown, just for compliance to the interface specification.
	 */  
	public void skippedEntity(String ent) throws SAXException {
		throw new SAXException("Don't know how to skip entities");
	}
	
	/** Inserts a processing instruction.
	 *
	 * @param target The PI target
	 * @param data The PI data
	 * @throws SAXException Thrown in case of an IOException
	 */  
	public void processingInstruction(String target, String data)
	throws SAXException {
		try {
			stopTerminator();
			if (w != null) {
				w.write("<?");
				w.write(target);
				w.write(' ');
				w.write(data);
				w.write("?>");
			}
		} catch (java.io.IOException e) {
			throw new SAXException(e);
		}
	}
}

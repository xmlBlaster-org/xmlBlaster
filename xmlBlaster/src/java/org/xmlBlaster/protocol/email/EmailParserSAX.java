package org.xmlBlaster.protocol.email;

import org.xmlBlaster.util.XmlProcessor;
import org.xmlBlaster.util.SaxHandlerBase;
import org.xml.sax.Attributes;

import org.xmlBlaster.util.XmlToDom;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Vector;

import org.xmlBlaster.protocol.I_Authenticate;
import org.xmlBlaster.protocol.I_XmlBlaster;
import org.xmlBlaster.util.ConnectQos;
import org.xmlBlaster.util.ConnectReturnQos;
import org.xmlBlaster.engine.helper.MessageUnit;
import org.xmlBlaster.engine.Global;

/**
 * Title:
 * Description:
 * Copyright:    Copyright (c) 2002
 * Company:
 * @author
 * @version 1.0
 */

 /**
  * xmlBlaster
  *
  *  String subscribe(String sessionId, String xmlKey_literal, String subscribeQoS_literal) throws XmlBlasterException;
  *  <subscribe sessionId='xxx'>
  *     <key>....</key>
  *     <qos>...</qos>
  *  </subscribe>
  *
  *  void unSubscribe(String sessionId, String xmlKey_literal, String unSubscribeQoS_literal) throws XmlBlasterException;
  *  <unSubscribe sessionId='xxx'>
  *     <key>
  *     </key>
  *  </unSubscribe>
  *
  *  String publish(String sessionId, MessageUnit msgUnit) throws XmlBlasterException;
  *  <publish sessionId='sss'>
  *     <key></key>
  *     <content link='ff'></content>
  *     <qos>...</qos>  *  </publish>
  *
  *  String[] publishArr(String sessionId, MessageUnit[] msgUnitArr) throws XmlBlasterException;
  *  <publishArr sessionId='sss'>
  *     <key></key>
  *     <content link='ff'></content>
  *     <qos>...</qos>
  *     <key></key>
  *     <content link='ff'></content>
  *     <qos>...</qos>
  *       .......
  *     <key></key>
  *     <content link='ff'></content>
  *     <qos>...</qos>
  *  </publishArr>
  *
  *  String[] erase(String sessionId, String xmlKey_literal, String eraseQoS_literal) throws XmlBlasterException;
  *  <erase sessionId='sss'>
  *     <key></key>
  *     <qos>...</qos>
  *  </erase>
  *
  *  MessageUnit[] get(String sessionId, String xmlKey_literal, String getQoS_literal) throws XmlBlasterException;
  *  <erase sessionId='sss'>
  *     <key></key>
  *     <qos>...</qos>
  *  </erase>
  */

public class EmailParserSAX extends SaxHandlerBase
{
   private I_Authenticate authenticator = null;
   private I_XmlBlaster   xmlBlaster    = null;

   private boolean isInXmlBlaster    = false;
   private HashSet commandsToFire    = new HashSet();
   private StringBuffer qos          = null;
   private StringBuffer key          = null;
   private StringBuffer content      = null;
   private boolean      isInQos      = false;
   private boolean      isInKey      = false;
   private boolean      isInContent  = false;
   private String       tmpSessionId     = null;
   private String       sessionId        = null;
   private String       currentSessionId = null;
   private String       link             = null;
   private Hashtable    attachementContents = null;
   private Vector       messageVector       = null;
   private boolean      doesDisconnect = false;
   private StringBuffer response    = null;
   private Global       glob = null;

   public EmailParserSAX(Global glob)
   {
      super();
      this.glob = glob;
      this.commandsToFire.add("connect");
      this.commandsToFire.add("subscribe");
      this.commandsToFire.add("unsubscribe");
      this.commandsToFire.add("publish");
      this.commandsToFire.add("publishArr");
      this.commandsToFire.add("erase");
      this.commandsToFire.add("disconnect");

      this.response = new StringBuffer("<xmlBlasterResponse>\n");
   }


   protected String writeElementStart(String qName, Attributes attr)
   {
      this.sessionId   = this.currentSessionId;
      this.link        = null;
      StringBuffer buf = new StringBuffer("<");
      buf.append(qName);
      int nmax = attr.getLength();
      for (int i=0; i < nmax; i++) {
         String name = attr.getQName(i);
         String value = attr.getValue(i);
         if (this.commandsToFire.contains(qName))
            if ("SessionId".equalsIgnoreCase(name)) this.tmpSessionId = value;
         if ("content".equals(qName))
            if ("link".equalsIgnoreCase(name)) this.link = value;
         buf.append(' ');
         buf.append(name);
         buf.append("='");
         buf.append(value);
         buf.append('\'');
      }
      buf.append('>');
      return buf.toString();
   }

   public void characters(char[] ch, int start, int length)
   {
      if (this.isInXmlBlaster) super.characters(ch, start, length);
      if (this.isInQos       ) this.qos.append(new String(ch, start, length));
      if (this.isInKey       ) this.key.append(new String(ch, start, length));
      if (this.isInContent   ) this.content.append(new String(ch, start, length));
   }

   public void startElement(String namespaceURI, String localName, String qName, Attributes atts)
   {
      // level 0
      if ("xmlBlaster".equalsIgnoreCase(qName)) {
         this.isInXmlBlaster = true;
         return;
      }
      if (this.isInXmlBlaster == false) return;

      if (this.commandsToFire.contains(qName)) {
         this.character = new StringBuffer();
         this.character.append(this.writeElementStart(qName, atts));
         return;
      }

      if ("key".equals(qName)) {
         this.key = new StringBuffer();
         this.key.append(this.writeElementStart(qName, atts));
         this.isInKey = true;
         return;
      }

      if ("qos".equals(qName)) {
         this.qos = new StringBuffer();
         this.qos.append(this.writeElementStart(qName, atts));
         this.isInQos = true;
         return;
      }

      if ("message".equals(qName)) {
         this.messageVector = new Vector();
         return;
      }

      if ("content".equals(qName)) {
         this.content = new StringBuffer();
         this.content.append(this.writeElementStart(qName, atts));
         this.isInContent = true;
         return;
      }

      if (this.isInXmlBlaster)
         this.character.append(this.writeElementStart(qName, atts));
      if (this.isInQos)
         this.qos.append(this.writeElementStart(qName, atts));
      if (this.isInKey)
         this.key.append(this.writeElementStart(qName, atts));
   }


   private void appendEndOfElement(StringBuffer buf, String qName)
   {
      buf.append("</");
      buf.append(qName);
      buf.append('>');

      try {
         if ("connect".equals(qName)) {
            ConnectQos connectQos = new ConnectQos(this.glob, this.character.toString());
            ConnectReturnQos ret = this.authenticator.connect(connectQos);
            this.currentSessionId = ret.getSessionId();
            this.response.append("<connect>\n");
            this.response.append(ret.toXml());
            this.response.append("</connect>\n");
            return;
         }

         if ("disconnect".equals(qName)) {
            this.doesDisconnect = true;
            this.authenticator.disconnect(this.sessionId, this.character.toString());
            return;
         }

         if ("publish".equals(qName)) {
            MessageUnit msgUnit = buildMessageUnit();
            String ret = this.xmlBlaster.publish(this.sessionId, msgUnit);
            this.response.append("<publish>\n");
            this.response.append("  <messageId>");
            this.response.append(ret);
            this.response.append("  </messageId>\n");
            this.response.append("</publish>\n");
            return;
         }

         if ("publishArr".equals(qName)) {
            int size = this.messageVector.size();
            MessageUnit[] msgs = new MessageUnit[size];
            for (int i=0; i < size; i++)
               msgs[i] = (MessageUnit)this.messageVector.elementAt(i);
            String[] ret = this.xmlBlaster.publishArr(this.sessionId, msgs);
            this.response.append("<publishArr>\n");
            for (int i=0; i < ret.length; i++) {
               this.response.append("  <messageId>");
               this.response.append(ret[i]);
               this.response.append("  </messageId>\n");
            }
            this.response.append("</publishArr>\n");
            return;
         }

         if ("subscribe".equals(qName)) {
            String ret = this.xmlBlaster.subscribe(this.sessionId.toString(),
               this.key.toString(), this.qos.toString());
            this.response.append("<subscribe>\n");
            this.response.append("  <subscribeId>");
            this.response.append(ret);
            this.response.append("  </subscribeId>\n");
            this.response.append("</subscribe>\n");
            return;
         }

         if ("unSubscribe".equals(qName)) {
            this.xmlBlaster.unSubscribe(this.sessionId.toString(),
               this.key.toString(), this.qos.toString());
            return;
         }

         if ("erase".equals(qName)) {
            String[] ret = this.xmlBlaster.erase(this.sessionId.toString(),
               this.key.toString(), this.qos.toString());
            this.response.append("<erase>\n");
            for (int i=0; i < ret.length; i++) {
               this.response.append("  <messageId>");
               this.response.append(ret[i]);
               this.response.append("  </messageId>\n");
            }
            this.response.append("</erase>\n");
            return;
         }

         if ("get".equals(qName)) {
            MessageUnit[] ret = this.xmlBlaster.get(this.sessionId.toString(),
               this.key.toString(), this.qos.toString());
            this.response.append("<get>\n");
            this.response.append("  <message>\n");
            for (int i=0; i < ret.length; i++) {
               this.response.append(ret[i].toXml());
            }
            this.response.append("  </message>\n");
            this.response.append("</get>\n");

            return;
         }
      }
      catch (Exception ex) {  // handle here the exception
         ex.printStackTrace();
      }
   }

   private MessageUnit buildMessageUnit ()
   {
      byte[] currentContent = null;
      if (this.link == null)
         currentContent = this.content.toString().getBytes();
      else {
         if (this.attachementContents.contains(this.link)) {
            currentContent = (byte[])this.attachementContents.get(this.link);
         }
         else {
            // throw exception
         }
      }
      MessageUnit msgUnit = new MessageUnit(this.key.toString(),
         currentContent, this.qos.toString());
      return msgUnit;
   }


   public void endElement(String namespaceURI, String localName, String qName)
   {
      if ("xmlBlaster".equalsIgnoreCase(qName)) {
         this.isInXmlBlaster = false;
         return;
      }

      if (this.isInXmlBlaster) appendEndOfElement(this.character, qName);
      if (this.isInQos)        appendEndOfElement(this.qos      , qName);
      if (this.isInKey)        appendEndOfElement(this.key      , qName);
      if (this.isInContent)    appendEndOfElement(this.content  , qName);


      if ("message".equals(qName)) {
         this.messageVector.add(buildMessageUnit());
         return;
      }
      if ("content".equals(qName)) {
         System.out.println(this.content);
         System.out.println("-----------------------------------------------");
         this.isInContent = false;
         return;
      }
      if ("key".equals(qName)) {
         System.out.println(this.key);
         System.out.println("-----------------------------------------------");
         this.isInKey = false;
         return;
      }
      if ("qos".equals(qName)) {
         System.out.println(this.qos);
         System.out.println("-----------------------------------------------");
         this.isInQos = false;
         return;
      }




      if (commandsToFire.contains(qName)) {
//         System.out.println(this.character);
//         System.out.println("-----------------------------------------------");
         return;
      }
   }


   public static void main(String[] args)
   {
      String request = "<xmlBlaster>\n" +
                       "  <connect>" +
                       "    <securityPlugin type='aaa' version='bbb'>\n" +
                       "      <user>michele</user>\n" +
                       "      <passwd><![CDATA[secret    ]]></passwd>\n" +
                       "    </securityPlugin>\n" +
                       "  </connect>\n" +
                       "  <publish>\n" +
                       "    <key>xxxx</key>\n" +
                       "    <content xlink='sss'/>\n" +
                       "    <qos></qos>\n" +
                       "  </publish>\n" +
                       "  <subscribe/>\n" +
                       "  <disconnect/>\n" +
                       "</xmlBlaster>";

      try {
         EmailParserSAX emailParser = new EmailParserSAX(new Global());
         emailParser.init(request);
      }
      catch (Exception ex) {
         ex.printStackTrace();
      }
   }
}

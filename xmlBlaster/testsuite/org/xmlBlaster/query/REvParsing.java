/*------------------------------------------------------------------------------
Name:      REvParsing.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Comparison between RegExp and XML-Parsing.
Version:   $Id: REvParsing.java,v 1.4 2002/05/11 10:07:55 ruff Exp $
Author:    manuel.kron@gmx.net
------------------------------------------------------------------------------*/
package testsuite.org.xmlBlaster.query;

import org.jutils.io.FileUtil;
import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.Global;
import org.jutils.JUtilsException;
import org.jutils.time.StopWatch;
import org.jutils.init.Args;

import java.util.Vector;
import java.io.*;

import org.xml.sax.*;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import org.xml.sax.helpers.DefaultHandler;

import gnu.regexp.*;


/**
 */
public class REvParsing extends DefaultHandler
{
   static private final String ME ="REvParsing";
   private final Global glob;
   String xmlInstance = "";
   String uri = "";
   Vector oids = new Vector();
   int countOids = 0;

   public REvParsing(Global glob)
   {
      this.glob = glob;
      countOids = 0; // Set Oid-Counter zero;
      try{
         uri = glob.getProperty().get("f",(String)null);
         if(uri==null)
            usage();
         xmlInstance = FileUtil.readAsciiFile(uri);
      }catch(JUtilsException e){
         Log.panic(ME,e.reason);
      }
   }

   /**
    * Recognize xmlkey-oids by PERL5 RegExp-Syntax.
    */
   public void testRE()
   {
      StopWatch stop = new StopWatch();
      RE expression = null;
      try{
         expression = new RE("oid=(\'|\"|\\s)(.*)(\'|\")");
         REMatch match[] = expression.getAllMatches(xmlInstance);

         for(int y=0; y < match.length; y++)
         {
            if(match != null)
            {
                /** matches OID pure */
                RE re = new RE("[^oid=\'\"]");
                REMatch[] matches = re.getAllMatches(match[y].toString());
                StringBuffer sb = new StringBuffer();
                for (int i = 0; i < matches.length; i++) {
                  sb.append(matches[i]);
                }
                oids.add(sb.toString());
                countOids++;
             }else{
                Log.error(ME,"Invalid xmlKey.");
             }
         }
      }catch(REException e){
         Log.error(ME,"Can't create RE."+e.toString());
      }

      Log.info(ME,"Time for parsing "+countOids+ " oids by Regexp: "+stop.toString());
   }

   /**
    * Recognize xmlkey-oids by SAX-Parsing.
    */
   public void testParsing()
   {
      oids.removeAllElements();
      // Use the default (non-validating) parser
      SAXParserFactory factory = SAXParserFactory.newInstance();
      factory.setValidating(false);
      StopWatch stop = new StopWatch();
      try
      {
         SAXParser saxParser = factory.newSAXParser();
         saxParser.parse(new File(uri), new REvParsing(glob) );
      } catch (SAXParseException spe) {
      } catch (SAXException sxe) {
      } catch (ParserConfigurationException pce) {
      } catch (IOException ioe) {
           ioe.printStackTrace();
      }
      Log.info(ME,"Time for parsing "+countOids+" oids by SAX:"+stop.toString());

   }

   public void startElement(String namespaceURI, String localName, String tag, Attributes attr)
   {
      if(tag.equals("key")){
         if(attr.getLength() >0){
            if(attr.getQName(0).equals("oid")){
               oids.add(attr.getValue(0));
            }
         }
      }
   }


   public static void main(String[] arg)
   {
      Global glob = new Global();
      if (glob.init(arg) != 0) {
         usage();
         Log.panic(ME, "Bye");
      }

      REvParsing repa = new REvParsing(glob);
      // RegExp-Test
      repa.testRE();

      // Parsing-Test
      repa.testParsing();
   }


   public static void usage()
   {
      Log.plain(ME, "----------------------------------------------------------");
      Log.plain(ME, "Invoke:");
      Log.plain(ME, "   java testsuite.org.xmlBlaster.query.REvParsing  -f /PATH/$file.xml");
      Log.plain(ME, "");
      System.exit(1);
   }
}


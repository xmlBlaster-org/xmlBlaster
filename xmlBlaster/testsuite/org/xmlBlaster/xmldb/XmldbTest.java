package testsuite.org.xmlBlaster.xmldb;

import org.xmlBlaster.engine.xmldb.dom.*;
import org.xmlBlaster.engine.xmldb.file.*;
import org.xmlBlaster.engine.xmldb.*;
import org.xmlBlaster.util.*;

import com.jclark.xsl.om.*;

import java.io.File;
import java.io.IOException;

import java.util.Properties;
import java.util.Enumeration;

import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import com.jclark.xsl.dom.XMLProcessorImpl;
import com.jclark.xsl.dom.SunXMLProcessorImpl;

import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import org.xmlBlaster.util.*;

import com.fujitsu.xml.omquery.DomQueryMgr;
import com.fujitsu.xml.omquery.JAXP_ProcessorImpl;

import com.sun.xml.tree.*;
import java.io.*;
import java.util.*;

import org.jutils.JUtilsException;
import org.jutils.log.Log;
import org.jutils.io.FileUtil;
import org.jutils.time.StopWatch;
import org.xmlBlaster.engine.helper.MessageUnit;
import org.xmlBlaster.engine.xml2java.PublishQoS;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.engine.PMessageUnit;

class XmldbTest
{

  private static final String ME = "XmldbTest";
  private static final String ARCH = "PII 350, 128MB, JDK 1.3, Linux 2.2.14";

  public static void main(String args[])
  {
      /********** Read property-file **********/
      try {
         XmlBlasterProperty.init(args);
      } catch(org.jutils.JUtilsException e) {
         Log.panic(ME, e.toString());
      }
      Log.setLogLevel(XmlBlasterProperty.getProperty());

      /************* MessageUnits *************/

      /*** Big content ***/
      String contentBig = "";
      try{
         contentBig = FileUtil.readAsciiFile("bigtext.txt");
      }catch(JUtilsException e){
      }

      /*** Small content ***/
      String content = new String("Data for personKey and some other data.");

      /*** qos with durable ***/
      String qos = new String("<qos></qos>");

      /*** qos without durable ***/
      String qosD = new String("<qos><isDurable /></qos>");

      /*****************************************/

      MessageUnit mu;
      String key,oid;

      /** **** Create a xmldb *****/
      PDOM pdom = PDOM.getInstance();


      /******* INSERT-Test *******/
      /** Insert 1000 MessageUnits to xmldb **/
      StopWatch stop = new StopWatch();
      for(int i=0; i<1000; i++)
      {
         oid = String.valueOf(i);
         key = "<?xml version='1.0' ?>\n"+"<key oid='"+oid+"'>\n"+"<person pid='10"+oid+"' gid='200'>\n" +"<name age='31' sex='f'>Lisa</name>\n"+
               "<surname>Schmid</surname>\n"+ "<adress>\n <street>Bakerstreet 2a</street>\n </adress>\n"+"</person>\n"+" </key>\n";

         mu = new MessageUnit(key,content.getBytes(),qos);
         pdom.insert(mu,true);
      }

      showState(pdom);
      protocol(pdom,"insert (0..999)",1000,stop.elapsed(),stop.nice());
      stop.restart();

      /** Insert 500 MessageUnits to xmldb **/
      for(int i=1000; i<1500; i++)
      {
         oid = String.valueOf(i);
         key = "<?xml version='1.0' ?>\n"+"<key oid='"+oid+"'>\n"+"<person pid='10"+oid+"' gid='200'>\n" +"<name age='31' sex='f'>Lisa</name>\n"+
               "<surname>Schmid</surname>\n"+ "<adress>\n <street>Bakerstreet 2a</street>\n </adress>\n"+"</person>\n"+" </key>\n";

         mu = new MessageUnit(key,content.getBytes(),qos);
         pdom.insert(mu,true);
      }
      protocol(pdom,"insert (1000..1499)",500,stop.elapsed(),stop.nice());

      /******** Delete-Test ********/
      stop.restart();
      pdom.delete("1400");
      protocol(pdom,"delete 1400",1,stop.elapsed(),stop.nice());

      


      /** Query by String XPATH */

//      Enumeration iter = null;
      stop.restart();

      String queryKey = XmlBlasterProperty.get("key","//key");


/*      Enumeration iter = pdom.query(queryKey);
      if(iter==null)
      {
         Log.info(ME,"Query resultset was null. goodbye..");
         System.exit(0);
      }

      int hits = 0;
      while(iter.hasMoreElements())
      {
         PMessageUnit pmu = (PMessageUnit)iter.nextElement();
         hits++;
//         Log.info(ME,"OID : "+pmu.oid);
      }
      Log.info(ME,"Query : "+queryKey+" and gets "+String.valueOf(hits)+" hits.");
      Log.time(ME,"Querytime was :"+stop.nice());*/
  }

  private static void printOn(Enumeration nodeIter)
  {
    while(nodeIter.hasMoreElements())
    {
       PMessageUnit pmu = (PMessageUnit)nodeIter.nextElement();
       Log.info(ME,"OID : "+pmu.oid);
//       Object obj = nodeIter.nextElement();
//       org.w3c.dom.Node node = (org.w3c.dom.Node)obj;
//       DOMUtil.toXML(node);
    } 
  }

  private static void showState(PDOM pdom)
  {
     Vector v = pdom.getState();
     Log.info(ME,"--------------");
     Log.info(ME,"| Statistics |");
     Log.info(ME,"--------------");
     Log.info(ME,"Architecture of my host : "+ARCH);
     Log.info(ME,"Max. CacheSize : "+v.elementAt(1));
     Log.info(ME,"Max. Msg-Size  : "+v.elementAt(2));
     Log.info(ME,"---------------------------------------------------------------");
     Log.info(ME,"Operation                  Msg's     Msg/Sec  Time                  CA-Size     PMU-in-CA CA-OV     PMU-in-DB Durable   Big-PMU   DOM-Nodes");
     Log.info(ME,"-------------------------------------------------------------------------------------------------------------------------------------------");
  }

  private static void protocol(PDOM pdom,String operation, int countMsg, long time, String sec)
  {
     String msgSec = "0";
     try{
        msgSec = String.valueOf(countMsg / (time/1000));
     }catch(ArithmeticException e){
        msgSec = "0";
     }

     Vector v = pdom.getState();
     Log.info(ME,format(operation,25)+ format(String.valueOf(countMsg),8)+
                 format(msgSec,6) + format((String)sec,21) + format((String)v.elementAt(0),11)+ 
                 format((String)v.elementAt(3),8)+ format((String)v.elementAt(4),8)+
                 format((String)v.elementAt(5),8)+ format((String)v.elementAt(6),8)+
                 format((String)v.elementAt(7),8)+ format((String)v.elementAt(8),8) );
  }


  private static String format(String str,int len)
  {
     String strFo;
     int length = str.length();
     if(length > len)
     {
        strFo = new String(str.substring(0,len-1));   
     }else{
        for(int i=0;i<(len-length);i++){
           str = str+" ";
        }
        strFo = str;
     }
     return strFo+"  ";
  }

}

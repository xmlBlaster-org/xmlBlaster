/*------------------------------------------------------------------------------
Name:      TestXmlDb.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Testing xmldb
Version:   $Id: TestXmlDb.java,v 1.6 2000/08/26 14:50:16 kron Exp $
------------------------------------------------------------------------------*/
package testsuite.org.xmlBlaster;

import org.xmlBlaster.engine.xmldb.dom.*;
import org.xmlBlaster.engine.xmldb.file.*;
import org.xmlBlaster.engine.xmldb.*;
import org.xmlBlaster.util.*;


import java.io.File;
import java.io.IOException;

import java.util.Properties;
import java.util.Enumeration;

import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;


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

import test.framework.*;

/**
 * This class tests the XmlDb.
 * <p>
 * Invoke examples:<br />
 * <pre>
 *    jaco test.textui.TestRunner testsuite.org.xmlBlaster.TestXmlDb
 *
 *    jaco test.ui.TestRunner testsuite.org.xmlBlaster.TestXmlDb
 * </pre>
 */
public class TestXmlDb extends TestCase
{
   private final static String ME = "TestXmlDb";
   private XmlDb xmldb;
   
   private String content; 
   private String qos;
   private String qosD; 

   public TestXmlDb(String testName)
   {
      super(testName);
      setup();
   }
   
   public void setup()
   {
      xmldb = new XmlDb();
      content = new String("Data for personKey and some other data.");
      qos = new String("<qos></qos>");
      qosD = new String("<qos><isDurable /></qos>");
   }

   // Test insert with durable-messages
   public void insertMsg(String oid, boolean durable)
   {
      MessageUnit mu;
      String key;
      
      key = "<?xml version='1.0' ?>\n"+"<key oid='"+oid+"'>\n"+"<person pid='10"+oid+"' gid='200'>\n" +"<name age='31' sex='f'>Lisa</name>\n"+
               "<surname>Schmid</surname>\n"+ "<adress>\n <street>Bakerstreet 2a</street>\n </adress>\n"+"</person>\n"+" </key>\n";

      if(durable){
         mu = new MessageUnit(key,content.getBytes(),qosD);
      }else{
         mu = new MessageUnit(key,content.getBytes(),qos);
      }
      String result = xmldb.insert(mu);
//      assertNotEquals("Can't insert MessageUnit with oid : "+result+" because Key exists", result, oid); 
   }

   public void testGet()
   {
      Log.calls(ME,"Testcase ...... testGet()");
      // Add a MessageUnit with oid=100
      insertMsg("100",true);

      MessageUnit mu;
      PMessageUnit pmu = xmldb.get("100");

      if(pmu==null)
         assert("Can't get MessageUnit from xmldb with oid : 100",false);

      //invoke a second insert with oid=100
      xmldb.delete("100");
   }

   public void testDelete()
   {
      Log.calls(ME,"Testcase ...... testDelete()");
      xmldb.delete("100");
      PMessageUnit pmu = xmldb.get("100");

      if(pmu!=null)
         assert("Can't delete MessageUnit from xmldb with oid : "+pmu.oid,false);
      //invoke a second delete with oid=100
      xmldb.delete("100");
   }

   public void testQuery()
   {
      Log.calls(ME,"Testcase ...... testQuery()");
      insertMsg("100",true);
      insertMsg("101",true);
      insertMsg("102",true);
      Enumeration msgIter = xmldb.query("//key[@oid=\"101\"]");
      PMessageUnit pmu=null;
      while(msgIter.hasMoreElements())
      {
         pmu = (PMessageUnit)msgIter.nextElement();
      }
      if(pmu==null)
      {
         assert("Can't query with oid 101.",false);
         return;
      }
      assertEquals("Query was not correct for oid 101.",new String("101"),pmu.oid);
   }

   public void testInsertQuery()
   {
      Log.calls(ME,"Testcase ...... testInsertQuery()");
      for(int i=100;i<200;i++){
         insertMsg(String.valueOf(i),true);
      }
      Enumeration msgIter = xmldb.query("//key");
      PMessageUnit pmu=null;
      int countMsg=0;
      while(msgIter.hasMoreElements())
      {
         pmu = (PMessageUnit)msgIter.nextElement();
         countMsg++;
      }
      assertEquals("Insert-Query-Test was failed.",new String("100"),String.valueOf(countMsg));
      //Delete MessageUnits from xmldb
      for(int i=100;i<200;i++){
         xmldb.delete(String.valueOf(i));
      }
   }

   public void testInsertMsgPerSecond()
   {
      Log.calls(ME,"Testcase ...... testInsertMsgPerSecond()");
      StopWatch stop = new StopWatch();
      for(int i=100;i<1100;i++){
         insertMsg(String.valueOf(i),true);
      }

      if(stop.elapsed()<1000)
      {
         assert("Can't insert 1000 MessageUnits",false);
      }else{
         long msgSec = 1000 / (stop.elapsed()/1000L); 
         Log.info(ME,"MessageUnits per Second by INSERT : "+String.valueOf(msgSec)+" Msg/sec.");
      }

      stop.restart();
    
      // Query-time-test
      Enumeration msgIter = xmldb.query("//key");
      Log.info(ME,"Time for a simple query (1000 MessageUnits):"+stop.toString());
      PMessageUnit pmu=null;
      int countMsg=0;
      while(msgIter.hasMoreElements())
      {
         pmu = (PMessageUnit)msgIter.nextElement();
         countMsg++;
      }
      assertEquals("Query-Test was failed.",new String("1000"),String.valueOf(countMsg));

      stop.restart();
      // Delete MessageUnits 
      for(int i=100;i<1100;i++){
        xmldb.delete(String.valueOf(i));
      }      
      Log.info(ME,"1000 MessageUnits deleted in: "+stop.toString());
   }

   /**
   * Check the cache (RAM) with no durable Messages.
   */
   public void testCacheSize()
   {
      Log.calls(ME,"Testcase ...... testCacheSize()");
      StopWatch stop = new StopWatch();
      long varSize[] = {0L, 1000000L, 2000000L, 4000000L, 6000000L};

      for(int r=0;r<5;r++)
      {
         Log.info(ME,"Testing Cachesize................................."+String.valueOf(varSize[r]/1000000)+"mb");
         stop.restart();
         xmldb.setMaxCacheSize(varSize[r]);

         // Insert 1000 MessageUnits
         for(int i=100;i<1100;i++){
            insertMsg(String.valueOf(i),false);
         }
         Log.info(ME,"    Insert 1000 MUs....in..."+stop.toString());
         stop.restart();

         Enumeration msgIter = xmldb.query("//key");
         Log.info(ME,"    Query 1000 MUs.....in..."+stop.toString());

         stop.restart();
         for(int i=100;i<1100;i++){
           xmldb.delete(String.valueOf(i));
         }
         Log.info(ME,"    Delete 1000 MUs....in..."+stop.toString());
      }
   }

   public void testCache()
   {
   }

   /**
    * Method is used by TestRunner to load these tests
    */
   public static Test suite()
   {
       TestSuite suite= new TestSuite();
      suite.addTest(new TestXmlDb("testGet"));
       suite.addTest(new TestXmlDb("testDelete"));
       suite.addTest(new TestXmlDb("testQuery"));
       suite.addTest(new TestXmlDb("testInsertQuery"));
       suite.addTest(new TestXmlDb("testInsertMsgPerSecond"));
//       suite.addTest(new TestXmlDb("testCacheSize"));
       return suite;
   }

   /**
   */
   public static void main(String args[])
   {
      try {
         XmlBlasterProperty.init(args);
      } catch(org.jutils.JUtilsException e) {
         Log.panic(ME, e.toString());
      }
      TestXmlDb testXmldb = new TestXmlDb("TestXmlDb");
/*      testXmldb.testGet();
      testXmldb.testDelete();
      testXmldb.testQuery();
      testXmldb.testInsertQuery();
      testXmldb.testInsertMsgPerSecond();*/
      testXmldb.testCacheSize();
      Log.exit(TestXmlDb.ME, "Good bye");
   } 
   

}

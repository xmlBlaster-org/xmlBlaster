/*------------------------------------------------------------------------------
Name:      TestXmlDb.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Testing xmldb
Version:   $Id $
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
 * This class tests the XmlDb-Features.
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
   public void insertMsg(String oid)
   {
      MessageUnit mu;
      String key;
      
      key = "<?xml version='1.0' ?>\n"+"<key oid='"+oid+"'>\n"+"<person pid='10"+oid+"' gid='200'>\n" +"<name age='31' sex='f'>Lisa</name>\n"+
               "<surname>Schmid</surname>\n"+ "<adress>\n <street>Bakerstreet 2a</street>\n </adress>\n"+"</person>\n"+" </key>\n";

      mu = new MessageUnit(key,content.getBytes(),qosD);
      String result = xmldb.insert(mu,true);
//      assertNotEquals("Can't insert MessageUnit with oid : "+result+" because Key exists", result, oid); 
   }

   public void testGet()
   {
      // Add a MessageUnit with oid=100
      insertMsg("100");

      MessageUnit mu;
      PMessageUnit pmu = xmldb.get("100");

      if(pmu==null)
         assert("Can't get MessageUnit from xmldb with oid : 100",false);
   }

   public void testDelete()
   {
      xmldb.delete("100");
      PMessageUnit pmu = xmldb.get("100");

      if(pmu!=null)
         assert("Can't delete MessageUnit from xmldb with oid : "+pmu.oid,false);
   }

   public void testQuery()
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
       return suite;
   }

   /**
    * Invoke: jaco testsuite.org.xmlBlaster.TestPersistence
    * <p />
   */
   public static void main(String args[])
   {
      try {
         XmlBlasterProperty.init(args);
      } catch(org.jutils.JUtilsException e) {
         Log.panic(ME, e.toString());
      }
      TestXmlDb testXmldb = new TestXmlDb("TestXmlDb");
      testXmldb.testGet();
      testXmldb.testDelete();
      Log.exit(TestXmlDb.ME, "Good bye");
   } 
   

}

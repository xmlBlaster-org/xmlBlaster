/*------------------------------------------------------------------------------
Name:      Sql92SelectorTest.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.test.classtest;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import junit.framework.TestCase;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.lexical.LikeOpWrapper;
import org.xmlBlaster.util.lexical.Sql92Selector;
import org.xmlBlaster.util.qos.ClientProperty;

/**
 * Test ClientProperty. 
 * <p />
 * All methods starting with 'test' and without arguments are invoked automatically
 * <p />
 * Invoke: java -Djava.compiler= junit.textui.TestRunner -noloading org.xmlBlaster.test.classtest.Sql92SelectorTest
 * @see org.xmlBlaster.util.qos.ClientProperty
 * @see <a href="http://www.xmlblaster.org/xmlBlaster/doc/requirements/engine.qos.clientProperty.html">The client.qos.clientProperty requirement</a>
 */
public class Sql92SelectorTest extends TestCase {
   
   private final static String ME = "Sql92SelectorTest"; 
   protected Global glob;
   private static Logger log = Logger.getLogger(Sql92SelectorTest.class.getName());
   private Map[] dataSet;
   private boolean[][] resultSet;
   private String[] querySet;
   
   public Sql92SelectorTest(String name) {
      this(null, name);
   }
   
   public Sql92SelectorTest(Global global, String name) {
      super(name);
      if (global == null) this.glob = Global.instance();
      else this.glob = global;

   }

   protected void setUp() {
      setupDataSets();
   }

   protected void tearDown() {
   }

   /**
    * Change this method if you want to add a new data set to be checked
    *
    */
   private void setupDataSets() {
      ArrayList datas = new ArrayList();
      Map map;
      String encoding = null;
      ClientProperty prop1, prop2, prop3;
      prop1 = new ClientProperty("age"   , "integer", encoding, "23"         );
      prop2 = new ClientProperty("city"  ,      null, encoding, "London"     );
      prop3 = new ClientProperty("amount",  "double", encoding, "100.1234567");
      
      // set : 0  (0:0:0)
      map = new HashMap();
      datas.add(map);
      
      // set : 1  (0:0:1)
      map = new HashMap();
      map.put("amount", prop3);
      datas.add(map);

      // set : 2  (0:1:0)
      map = new HashMap();
      map.put("city"  , prop2);
      datas.add(map);

      // set : 3  (0:1:1)
      map = new HashMap();
      map.put("city"  , prop2);
      map.put("amount", prop3);
      datas.add(map);

      // set : 4  (1:0:0)
      map = new HashMap();
      map.put("age"   , prop1);
      datas.add(map);

      // set : 5  (1:0:1)
      map = new HashMap();
      map.put("age"   , prop1);
      map.put("amount", prop3);
      datas.add(map);

      // set : 6  (1:1:0)
      map = new HashMap();
      map.put("age"   , prop1);
      map.put("city"  , prop2);
      datas.add(map);

      // set : 7  (1:1:1)
      map = new HashMap();
      map.put("age"   , prop1);
      map.put("city"  , prop2);
      map.put("amount", prop3);
      datas.add(map);
      this.dataSet = (Map[])datas.toArray(new Map[datas.size()]);
      
   }

   /**
    * Checks if the provided data sets, the queries and the results are
    * consistent with eachother (this is invoked before starting the real testing)
    */
   private void consistencyCheck() {
      int numData = this.dataSet.length;
      int numQueries = this.querySet.length;
      int numResults = this.resultSet.length;
      assertEquals("The number of queries '" + numQueries + "' differes from the number of results '" + numResults + "'", numQueries, numResults);
      for (int i=0; i < numResults; i++) {
         assertEquals("The number of results for query '" + i + "' is wrong", numData, this.resultSet[i].length);
      }
   }

   private String getDataAsText(int pos) {
      Map map = this.dataSet[pos];
      StringBuffer buffer = new StringBuffer("[");
      Object[] keys = map.keySet().toArray();
      for (int i=0; i < keys.length; i++) {
         if (i != 0) buffer.append(";");
         buffer.append(keys[i]).append("=");
         ClientProperty cp = (ClientProperty)map.get(keys[i]);
         String tmp = "null";
         if (cp != null) tmp = cp.getStringValue();
         buffer.append(tmp);
      }
      buffer.append("]");
      return buffer.toString();
   }
   
   /**
    * This is the fully automatized initial (general) test. Since it is 
    * difficult to predict all possible problems, additional tests should 
    * be added once a bug is encountered. For each such bug an own test
    * method should be added.
    */
   private void selectorPerformTest() {
      // for each data set one selector
      consistencyCheck();
      /*
      Sql92Selector[] selectors = new Sql92Selector[this.dataSet.length];
      for (int i=0; i < this.dataSet.length; i++) {
         if (log.isLoggable(Level.FINE)) log.fine("testSelectorStandard: creating selector nr. " + i);
         selectors[i] = new Sql92Selector(this.glob);
      }
      */
      Sql92Selector selector = new Sql92Selector(this.glob);
      
      for (int i=0; i <  this.querySet.length; i++) {
         String query = this.querySet[i];
         log.info("testSelectorStandard: process query '" + query + "'");
         boolean[] shouldAnswers = this.resultSet[i];
         for (int j=0; j < this.dataSet.length; j++) {
            if (log.isLoggable(Level.FINE)) log.fine("testSelectorStandard: query '" + query + "' on set '" + getDataAsText(j));
            try {
               boolean response = selector.select(query, this.dataSet[j]);
               assertEquals("wrong answer for query '" + i + "'\"" + query + "\" on set '" + j + "' " + getDataAsText(j), shouldAnswers[j], response);
            }
            catch (XmlBlasterException ex) {
               ex.printStackTrace();
               assertTrue("An exception should not occur on query '" + i + "'\"" + query + "\" for dataset " + getDataAsText(j), false);
            }
         }
      }
   }

   
   /**
    * 
    * @return the milliseconds per request
    */
   private void performanceCheck() {
      if (log.isLoggable(Level.FINER)) log.finer("performanceCheck");
      // for each data set one selector
      consistencyCheck();
      /*
      Sql92Selector[] selectors = new Sql92Selector[this.dataSet.length];
      for (int i=0; i < this.dataSet.length; i++) {
         selectors[i] = new Sql92Selector(this.glob);
      }
      */
      Sql92Selector selector = new Sql92Selector(this.glob);
      try {
         int kmax = 100;
         long t0 = System.currentTimeMillis();
         for (int k=0; k <  kmax; k++) {
            for (int i=0; i <  this.querySet.length; i++) {
               String query = this.querySet[i];
               for (int j=0; j < this.dataSet.length; j++) {
                  boolean response = selector.select(query, this.dataSet[j]);
               }
            }
         }
         long dt = System.currentTimeMillis() - t0;
         int nmax = kmax * this.dataSet.length * this.querySet.length;
         log.info("performance: '" + nmax + "' requests in '" + dt + "' ms");
         double ret = 1.0 * dt / nmax;
         log.info("performance: '" + ret + "' ms per request");
         log.info("performance: '" + ((int)(1000.0 / ret)) + "' request per second (rps)");
      }
      catch (XmlBlasterException ex) {
         ex.printStackTrace();
      }
   }
   
   
   
   public void interactive() {
      BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
      System.out.println("\n- input new query: ");
      while (true) {
         try {
            String line = br.readLine();
            if (line == null) break;
            System.out.print("Result: ");
            for (int i=0; i < this.dataSet.length; i++) {
               Sql92Selector selector = new Sql92Selector(this.glob);
               boolean ret = selector.select(line, this.dataSet[i]);
               System.out.print(ret + "\t");
            }
         }
         catch (Exception ex) {
            ex.printStackTrace();
         }
         System.out.println("\n- input new query: ");
      }
   }

   
   // THE TESTING METHODS COME HERE .....
   
   /**
    * Tests the outer logical operators AND, OR, NOT with and without brackets. 
    * For each data there must be a boolean value telling if the result is true or false
    */
   public void testLogicalOps() {
      ArrayList queries = new ArrayList();
      ArrayList results = new ArrayList();
      boolean t = true;
      boolean f = false;
      
      // query 0
      queries.add("age=23 AND city='London' AND amount<200.2");
      /*  helper                 0.0.0  0.0.1  0.1.0  0.1.1  1.0.0  1.0.1  1.1.0  1.1.1  */
      results.add(new boolean[] {false, false, false, false, false, false, false, true });
      
      // query 1
      queries.add("age=23 OR city='London' OR amount < 200.2");
      /*  helper                 0  0  0, 0  0  1, 0  1  0, 0  1  1, 1  0  0  1  0  1  1  1  0  1  1  1  */
      results.add(new boolean[] {f||f||f, f||f||t, f||t||f, f||t||t, t||f||f, t||f||t, t||t||f, t||t||t });
      
      // query 2
      queries.add("age=23 OR city='London' AND amount < 200.2");
      /*  helper                 0  0  0, 0  0  1, 0  1  0, 0  1  1, 1  0  0  1  0  1  1  1  0  1  1  1  */
      results.add(new boolean[] {f||f&&f, f||f&&t, f||t&&f, f||t&&t, t||f&&f, t||f&&t, t||t&&f, t||t&&t });
      
      // query 3
      queries.add("age=23 OR city='London' AND amount < 200.2");
      /*  helper                 0  0  0, 0  0  1, 0  1  0, 0  1  1, 1  0  0  1  0  1  1  1  0  1  1  1  */
      results.add(new boolean[] {f||f&&f, f||f&&t, f||t&&f, f||t&&t, t||f&&f, t||f&&t, t||t&&f, t||t&&t });
      
      // query 4
      queries.add("(age=23 AND city='London') OR amount < 200.2");
      /*  helper                 0  0  0, 0  0  1, 0  1  0, 0  1  1, 1  0  0  1  0  1  1  1  0  1  1  1  */
      results.add(new boolean[] {f&&f||f, f&&f||t, f&&t||f, f&&t||t, t&&f||f, t&&f||t, t&&t||f, t&&t||t });
      
      // query 5
      queries.add("age=23 OR (city='London' AND amount < 200.2)");
      /*  helper                 0   0  0 , 0   0  1 , 0   1  0 , 0   1  1 , 1   0  0,  1   0  1 , 1   1  0 , 1   1  1  */
      results.add(new boolean[] {f||(f&&f), f||(f&&t), f||(t&&f), f||(t&&t), t||(f&&f), t||(f&&t), t||(t&&f), t||(t&&t) });

      // query 6
      queries.add("NOT age=23 OR (city='London' AND amount < 200.2)");
      /*  helper                  0   0  0 ,  0   0  1 ,  0   1  0 ,  0   1  1 ,  1   0  0 ,  1   0  1 ,  1   1  0 ,  1   1  1  */
      results.add(new boolean[] {!f||(f&&f), !f||(f&&t), !f||(t&&f), !f||(t&&t), !t||(f&&f), !t||(f&&t), !t||(t&&f), !t||(t&&t) });

      // query 7
      queries.add("age=23 OR NOT (city='London' AND amount < 200.2)");
      /*  helper                 0    0  0 , 0    0  1 , 0    1  0 , 0    1  1 , 1    0  0,  1    0  1 , 1    1  0 , 1    1  1  */
      results.add(new boolean[] {f||!(f&&f), f||!(f&&t), f||!(t&&f), f||!(t&&t), t||!(f&&f), t||!(f&&t), t||!(t&&f), t||!(t&&t) });

      // query 8
      queries.add("(age=23 OR NOT (city='London' AND amount < 200.2))");
      /*  helper                 0    0  0 , 0    0  1 , 0    1  0 , 0    1  1 , 1    0  0,  1    0  1 , 1    1  0 , 1    1  1  */
      results.add(new boolean[] {f||!(f&&f), f||!(f&&t), f||!(t&&f), f||!(t&&t), t||!(f&&f), t||!(f&&t), t||!(t&&f), t||!(t&&t) });

      // query 9
      queries.add("NOT (age=23 OR NOT (city='London' AND amount < 200.2))");
      /*  helper                   0    0  0 ,    0    0  1 ,    0    1  0 ,    0    1  1 ,    1    0  0,     1    0  1 ,    1    1  0 ,    1    1  1  */
      results.add(new boolean[] {!(f||!(f&&f)), !(f||!(f&&t)), !(f||!(t&&f)), !(f||!(t&&t)), !(t||!(f&&f)), !(t||!(f&&t)), !(t||!(t&&f)), !(t||!(t&&t)) });

      // query 10
      queries.add("NOT (age=23 OR NOT (city='London' AND (amount < 200.2)))");
      /*  helper                   0    0  0 ,    0    0  1 ,    0    1  0 ,    0    1  1 ,    1    0  0,     1    0  1 ,    1    1  0 ,    1    1  1  */
      results.add(new boolean[] {!(f||!(f&&f)), !(f||!(f&&t)), !(f||!(t&&f)), !(f||!(t&&t)), !(t||!(f&&f)), !(t||!(f&&t)), !(t||!(t&&f)), !(t||!(t&&t)) });

      // query 11
      queries.add("age=23 OR NOT ((city='London') AND amount < 200.2)");
      /*  helper                 0    0  0 , 0    0  1 , 0    1  0 , 0    1  1 , 1    0  0,  1    0  1 , 1    1  0 , 1    1  1  */
      results.add(new boolean[] {f||!(f&&f), f||!(f&&t), f||!(t&&f), f||!(t&&t), t||!(f&&f), t||!(f&&t), t||!(t&&f), t||!(t&&t) });

      // query 12
      queries.add("age=23 OR NOT ((city='London') AND (amount < 200.2))");
      /*  helper                 0    0  0 , 0    0  1 , 0    1  0 , 0    1  1 , 1    0  0,  1    0  1 , 1    1  0 , 1    1  1  */
      results.add(new boolean[] {f||!(f&&f), f||!(f&&t), f||!(t&&f), f||!(t&&t), t||!(f&&f), t||!(f&&t), t||!(t&&f), t||!(t&&t) });

      // query 13
      queries.add("age=23 OR NOT ((city='London') AND NOT(amount < 200.2))");
      /*  helper                 0    0   0 , 0    0   1 , 0    1   0 , 0    1   1 , 1    0   0,  1    0   1 , 1    1   0 , 1    1   1  */
      results.add(new boolean[] {f||!(f&&!f), f||!(f&&!t), f||!(t&&!f), f||!(t&&!t), t||!(f&&!f), t||!(f&&!t), t||!(t&&!f), t||!(t&&!t) });

      this.querySet = (String[])queries.toArray(new String[queries.size()]);
      this.resultSet = (boolean[][])results.toArray(new boolean[results.size()][]);
      // here the real testing is performed (the algorithm is the same for all tests)
      selectorPerformTest();
   }

   
   /**
    * Tests the NULL and NOT NULL statements.  
    * For each data there must be a boolean value telling if the result is true or false
    */
   public void testNullOps() {
      ArrayList queries = new ArrayList();
      ArrayList results = new ArrayList();
      boolean t = true;
      boolean f = false;
      
      // query 0
      queries.add("age IS NULL OR city IS NULL OR amount IS NULL");
      /*  helper                 0  0  0, 0  0  1, 0  1  0, 0  1  1, 1  0  0  1  0  1  1  1  0  1  1  1  */
      results.add(new boolean[] {t||t||t, t||t||f, t||f||t, t||f||f, f||t||t, f||t||f, f||f||t, f||f||f });
      
      // query 1
      queries.add("age IS NULL AND city IS NULL AND amount IS NULL");
      /*  helper                 0  0  0, 0  0  1, 0  1  0, 0  1  1, 1  0  0  1  0  1  1  1  0  1  1  1  */
      results.add(new boolean[] {t&&t&&t, t&&t&&f, t&&f&&t, t&&f&&f, f&&t&&t, f&&t&&f, f&&f&&t, f&&f&&f });
      
      // query 2
      queries.add("age IS NOT NULL OR city IS NOT NULL OR amount IS NOT NULL");
      /*  helper                 0  0  0, 0  0  1, 0  1  0, 0  1  1, 1  0  0  1  0  1  1  1  0  1  1  1  */
      results.add(new boolean[] {f||f||f, f||f||t, f||t||f, f||t||t, t||f||f, t||f||t, t||t||f, t||t||t });
      
      // query 3
      queries.add("age IS NOT NULL AND city IS NOT NULL AND amount IS NOT NULL");
      /*  helper                 0  0  0, 0  0  1, 0  1  0, 0  1  1, 1  0  0  1  0  1  1  1  0  1  1  1  */
      results.add(new boolean[] {f&&f&&f, f&&f&&t, f&&t&&f, f&&t&&t, t&&f&&f, t&&f&&t, t&&t&&f, t&&t&&t });

      this.querySet = (String[])queries.toArray(new String[queries.size()]);
      this.resultSet = (boolean[][])results.toArray(new boolean[results.size()][]);
      // here the real testing is performed (the algorithm is the same for all tests)
      selectorPerformTest();
   }

   /**
    * Tests the outer logical operators AND, OR, NOT with and without brackets. 
    * For each data there must be a boolean value telling if the result is true or false
    */
   public void testArithmeticOps() {
      ArrayList queries = new ArrayList();
      ArrayList results = new ArrayList();
      boolean t = true;
      boolean f = false;
      
      // query 0
      queries.add("age = 15+8 AND amount < 300.0+10.0");
      /*  helper                 0  0, 0  1, 0  0, 0  1, 1  0  1  1  1  0  1  1  */
      results.add(new boolean[] {f&&f, f&&t, f&&f, f&&t, t&&f, t&&t, t&&f, t&&t });
      
      // query 1
      queries.add("age = 26-3 AND amount < 300.0-10.0");
      /*  helper                 0  0, 0  1, 0  0, 0  1, 1  0  1  1  1  0  1  1  */
      results.add(new boolean[] {f&&f, f&&t, f&&f, f&&t, t&&f, t&&t, t&&f, t&&t });
      
      // query 2
      queries.add("age = 23*1 AND amount < 110.0*2");
      /*  helper                 0  0, 0  1, 0  0, 0  1, 1  0  1  1  1  0  1  1  */
      results.add(new boolean[] {f&&f, f&&t, f&&f, f&&t, t&&f, t&&t, t&&f, t&&t });
      
      // query 3
      queries.add("age = 46/2 AND amount < 600.0/2.2");
      /*  helper                 0  0, 0  1, 0  0, 0  1, 1  0  1  1  1  0  1  1  */
      results.add(new boolean[] {f&&f, f&&t, f&&f, f&&t, t&&f, t&&t, t&&f, t&&t });
      
      // query 4
      queries.add("age = (26-3) AND amount < 300.0-10.0");
      /*  helper                 0  0, 0  1, 0  0, 0  1, 1  0  1  1  1  0  1  1  */
      results.add(new boolean[] {f&&f, f&&t, f&&f, f&&t, t&&f, t&&t, t&&f, t&&t });
      
      // query 5
      queries.add("age = (23*1) AND amount < 110.0*2");
      /*  helper                 0  0, 0  1, 0  0, 0  1, 1  0  1  1  1  0  1  1  */
      results.add(new boolean[] {f&&f, f&&t, f&&f, f&&t, t&&f, t&&t, t&&f, t&&t });
      
      // query 6
      queries.add("age = 2*(26-3)-23 AND amount < 300.0-10.0");
      /*  helper                 0  0, 0  1, 0  0, 0  1, 1  0  1  1  1  0  1  1  */
      results.add(new boolean[] {f&&f, f&&t, f&&f, f&&t, t&&f, t&&t, t&&f, t&&t });

      // query 7
      queries.add("age = -(23*1)+46 AND amount < 110.0*2");
      /*  helper                 0  0, 0  1, 0  0, 0  1, 1  0  1  1  1  0  1  1  */
      results.add(new boolean[] {f&&f, f&&t, f&&f, f&&t, t&&f, t&&t, t&&f, t&&t });
      
      // query 8
      queries.add("age = -23*1+46 AND amount < 110.0*2");
      /*  helper                 0  0, 0  1, 0  0, 0  1, 1  0  1  1  1  0  1  1  */
      results.add(new boolean[] {f&&f, f&&t, f&&f, f&&t, t&&f, t&&t, t&&f, t&&t });

      // query 9
      queries.add("age = 47-12*2 AND amount < 110.0*2");
      /*  helper                 0  0, 0  1, 0  0, 0  1, 1  0  1  1  1  0  1  1  */
      results.add(new boolean[] {f&&f, f&&t, f&&f, f&&t, t&&f, t&&t, t&&f, t&&t });
      
      // query 10
      queries.add("2*age = 46 AND amount < 110.0*2");
      /*  helper                 0  0, 0  1, 0  0, 0  1, 1  0  1  1  1  0  1  1  */
      results.add(new boolean[] {f&&f, f&&t, f&&f, f&&t, t&&f, t&&t, t&&f, t&&t });
      
      // query 11
      queries.add("age+6 = (27+2) AND amount < 110.0*2");
      /*  helper                 0  0, 0  1, 0  0, 0  1, 1  0  1  1  1  0  1  1  */
      results.add(new boolean[] {f&&f, f&&t, f&&f, f&&t, t&&f, t&&t, t&&f, t&&t });
      
      // query 12
      queries.add("age+4 = 27 AND amount < (110.0*2)");
      /*  helper                 0  0, 0  1, 0  0, 0  1, 1  0  1  1  1  0  1  1  */
      results.add(new boolean[] {f&&f, f&&t, f&&f, f&&t, t&&f, t&&t, t&&f, t&&t });
      
      this.querySet = (String[])queries.toArray(new String[queries.size()]);
      this.resultSet = (boolean[][])results.toArray(new boolean[results.size()][]);
      // here the real testing is performed (the algorithm is the same for all tests)
      selectorPerformTest();
   }

   /**
    * Tests the IN (...) statements.  
    * For each data there must be a boolean value telling if the result is true or false
    */
   public void testInSetOps() {
      ArrayList queries = new ArrayList();
      ArrayList results = new ArrayList();
      boolean t = true;
      boolean f = false;
      
      // query 0
      queries.add("age IN (25,23,30) AND city IN('London', 'Paris', 'Caslano')");
      /*  helper                 0  0, 0  0, 0  1, 0  1, 1  0  1  0  1  1  1  1  */
      results.add(new boolean[] {f&&f, f&&f, f&&t, f&&t, t&&f, t&&f, t&&t, t&&t });
      
      // query 1
      queries.add("age IN (23) AND city IN('London')");
      /*  helper                 0  0, 0  0, 0  1, 0  1, 1  0  1  0  1  1  1  1  */
      results.add(new boolean[] {f&&f, f&&f, f&&t, f&&t, t&&f, t&&f, t&&t, t&&t });
      
      // query 2
      queries.add("age IN (24,25) AND city IN('London')");
      // helper : all must be false
      results.add(new boolean[] {f&&f, f&&t, f&&f, f&&t, f&&f, f&&t, f&&f, f&&t });
      
      // query 3
      queries.add("age IN (23) AND city IN('Caslano', 'Paris')");
      //  helper all must be false
      results.add(new boolean[] {f&&f, f&&f, f&&f, f&&f, t&&f, t&&f, t&&f, t&&f });

      this.querySet = (String[])queries.toArray(new String[queries.size()]);
      this.resultSet = (boolean[][])results.toArray(new boolean[results.size()][]);
      // here the real testing is performed (the algorithm is the same for all tests)
      selectorPerformTest();
   }

   /**
    * Tests the IN (...) statements.  
    * For each data there must be a boolean value telling if the result is true or false
    */
   public void testBetweenOps() {
      ArrayList queries = new ArrayList();
      ArrayList results = new ArrayList();
      boolean t = true;
      boolean f = false;
      
      // query 0
      queries.add("age BETWEEN 10 AND 40 OR city BETWEEN 'Amsterdam' AND 'Paris' OR amount BETWEEN 10.0 AND 2000.0");
      /*  helper                 0  0  0, 0  0  1, 0  1  0, 0  1  1, 1  0  0  1  0  1  1  1  0  1  1  1  */
      results.add(new boolean[] {f||f||f, f||f||t, f||t||f, f||t||t, t||f||f, t||f||t, t||t||f, t||t||t });

      // query 1
      queries.add("age BETWEEN 10 AND 40 AND city BETWEEN 'Amsterdam' AND 'Paris' AND amount BETWEEN 10.0 AND 2000.0");
      /*  helper                 0  0  0, 0  0  1, 0  1  0, 0  1  1, 1  0  0  1  0  1  1  1  0  1  1  1  */
      results.add(new boolean[] {f&&f&&f, f&&f&&t, f&&t&&f, f&&t&&t, t&&f&&f, t&&f&&t, t&&t&&f, t&&t&&t });

      // query 2
      queries.add("(age BETWEEN 10 AND 40) AND (city BETWEEN 'Amsterdam' AND 'Paris') AND (amount BETWEEN 10.0 AND 2000.0)");
      /*  helper                 0  0  0, 0  0  1, 0  1  0, 0  1  1, 1  0  0  1  0  1  1  1  0  1  1  1  */
      results.add(new boolean[] {f&&f&&f, f&&f&&t, f&&t&&f, f&&t&&t, t&&f&&f, t&&f&&t, t&&t&&f, t&&t&&t });

      // query 3
      queries.add("age BETWEEN 23 AND 23 OR city BETWEEN 'London' AND 'London' OR amount BETWEEN 10.0 AND 2000.0");
      /*  helper                 0  0  0, 0  0  1, 0  1  0, 0  1  1, 1  0  0  1  0  1  1  1  0  1  1  1  */
      results.add(new boolean[] {f||f||f, f||f||t, f||t||f, f||t||t, t||f||f, t||f||t, t||t||f, t||t||t });

      // query 4
      queries.add("age BETWEEN 30 AND 10 OR city BETWEEN 'London' AND 'London' OR amount BETWEEN 10.0 AND 2000.0");
      /*  helper                 0  0  0, 0  0  1, 0  1  0, 0  1  1, 0  0  0  0  0  1  0  1  0  1  1  1  */
      results.add(new boolean[] {f||f||f, f||f||t, f||t||f, f||t||t, f||f||f, f||f||t, f||t||f, f||t||t });

      // query 5
      queries.add("age BETWEEN 23 AND 23 OR city BETWEEN 'Amsteram' AND 'Caslano' OR amount BETWEEN 10.0 AND 2000.0");
      /*  helper                 0  0  0, 0  0  1, 0  0  0, 0  0  1, 1  0  0  1  0  1  1  0  0  1  0  1  */
      results.add(new boolean[] {f||f||f, f||f||t, f||f||f, f||f||t, t||f||f, t||f||t, t||f||f, t||f||t });

      this.querySet = (String[])queries.toArray(new String[queries.size()]);
      this.resultSet = (boolean[][])results.toArray(new boolean[results.size()][]);
      // here the real testing is performed (the algorithm is the same for all tests)
      selectorPerformTest();
   }

   /**
    * Tests the LIKE statements.  
    * For each data there must be a boolean value telling if the result is true or false
    */
   public void testLikeOps() {
      ArrayList queries = new ArrayList();
      ArrayList results = new ArrayList();
      boolean t = true;
      boolean f = false;
      
      // query 0
      queries.add("age=23 OR city LIKE 'L%n' OR amount BETWEEN 10.0 AND 2000.0");
      /*  helper                 0  0  0, 0  0  1, 0  1  0, 0  1  1, 1  0  0  1  0  1  1  1  0  1  1  1  */
      results.add(new boolean[] {f||f||f, f||f||t, f||t||f, f||t||t, t||f||f, t||f||t, t||t||f, t||t||t });

      // query 1
      queries.add("age=23 OR city LIKE 'L%x' OR amount BETWEEN 10.0 AND 2000.0");
      /*  helper                 0  0  0, 0  0  1, 0  0  0, 0  0  1, 1  0  0  1  0  1  1  0  0  1  0  1  */
      results.add(new boolean[] {f||f||f, f||f||t, f||f||f, f||f||t, t||f||f, t||f||t, t||f||f, t||f||t });

      // query 2
      queries.add("age=23 OR city LIKE 'L%n' ESCAPE '\\' OR amount BETWEEN 10.0 AND 2000.0");
      /*  helper                 0  0  0, 0  0  1, 0  1  0, 0  1  1, 1  0  0  1  0  1  1  1  0  1  1  1  */
      results.add(new boolean[] {f||f||f, f||f||t, f||t||f, f||t||t, t||f||f, t||f||t, t||t||f, t||t||t });

      // query 3
      queries.add("age=23 OR city NOT LIKE 'L%n' OR amount BETWEEN 10.0 AND 2000.0");
      /*  helper                 0  1  0, 0  1  1, 0  0  0, 0  f  1, 1  1  0  1  t  1  1  0  0  1  0  1  */
      results.add(new boolean[] {f||t||f, f||t||t, f||f||f, f||f||t, t||t||f, t||t||t, t||f||f, t||f||t });

      // query 4
      queries.add("age=23 OR city NOT LIKE 'L%n' ESCAPE '\\' OR amount BETWEEN 10.0 AND 2000.0");
      /*  helper                 0  1  0, 0  1  1, 0  0  0, 0  f  1, 1  1  0  1  t  1  1  0  0  1  0  1  */
      results.add(new boolean[] {f||t||f, f||t||t, f||f||f, f||f||t, t||t||f, t||t||t, t||f||f, t||f||t });

      // query 5
      queries.add("age=23 OR city LIKE 'Lo_d_n' OR amount BETWEEN 10.0 AND 2000.0");
      /*  helper                 0  0  0, 0  0  1, 0  1  0, 0  1  1, 1  0  0  1  0  1  1  1  0  1  1  1  */
      results.add(new boolean[] {f||f||f, f||f||t, f||t||f, f||t||t, t||f||f, t||f||t, t||t||f, t||t||t });

      // query 6
      queries.add("age=23 OR city LIKE 'Lo_d_x' OR amount BETWEEN 10.0 AND 2000.0");
      /*  helper                 0  0  0, 0  0  1, 0  0  0, 0  0  1, 1  0  0  1  0  1  1  0  0  1  0  1  */
      results.add(new boolean[] {f||f||f, f||f||t, f||f||f, f||f||t, t||f||f, t||f||t, t||f||f, t||f||t });

      // query 7
      queries.add("age=23 OR city LIKE 'Lo_d_n' ESCAPE '\\' OR amount BETWEEN 10.0 AND 2000.0");
      /*  helper                 0  0  0, 0  0  1, 0  1  0, 0  1  1, 1  0  0  1  0  1  1  1  0  1  1  1  */
      results.add(new boolean[] {f||f||f, f||f||t, f||t||f, f||t||t, t||f||f, t||f||t, t||t||f, t||t||t });

      // query 8
      queries.add("age=23 OR city NOT LIKE 'Lo_d_n' OR amount BETWEEN 10.0 AND 2000.0");
      /*  helper                 0  1  0, 0  1  1, 0  0  0, 0  f  1, 1  1  0  1  t  1  1  0  0  1  0  1  */
      results.add(new boolean[] {f||t||f, f||t||t, f||f||f, f||f||t, t||t||f, t||t||t, t||f||f, t||f||t });

      // query 9
      queries.add("age=23 OR city NOT LIKE 'Lo_d_n' ESCAPE '\\' OR amount BETWEEN 10.0 AND 2000.0");
      /*  helper                 0  1  0, 0  1  1, 0  0  0, 0  f  1, 1  1  0  1  t  1  1  0  0  1  0  1  */
      results.add(new boolean[] {f||t||f, f||t||t, f||f||f, f||f||t, t||t||f, t||t||t, t||f||f, t||f||t });

      this.querySet = (String[])queries.toArray(new String[queries.size()]);
      this.resultSet = (boolean[][])results.toArray(new boolean[results.size()][]);
      // here the real testing is performed (the algorithm is the same for all tests)
      selectorPerformTest();
   }

   /**
    * Tests the REGEX statements.  
    * For each data there must be a boolean value telling if the result is true or false
    */
   public void testRegexOps() {
      ArrayList queries = new ArrayList();
      ArrayList results = new ArrayList();
      boolean t = true;
      boolean f = false;
      
      // query 0
      queries.add("age=23 OR city REGEX 'L.*n' OR amount BETWEEN 10.0 AND 2000.0");
      /*  helper                 0  0  0, 0  0  1, 0  1  0, 0  1  1, 1  0  0  1  0  1  1  1  0  1  1  1  */
      results.add(new boolean[] {f||f||f, f||f||t, f||t||f, f||t||t, t||f||f, t||f||t, t||t||f, t||t||t });

      // query 1
      queries.add("age=23 OR city REGEX 'L\\Dn\\Son' OR amount BETWEEN 10.0 AND 2000.0");
      /*  helper                 0  0  0, 0  0  1, 0  1  0, 0  1  1, 1  0  0  1  0  1  1  1  0  1  1  1  */
      results.add(new boolean[] {f||f||f, f||f||t, f||t||f, f||t||t, t||f||f, t||f||t, t||t||f, t||t||t });

      // query 2
      queries.add("age=23 OR city REGEX 'L[m-z]ndo[^z]' OR amount BETWEEN 10.0 AND 2000.0");
      /*  helper                 0  0  0, 0  0  1, 0  1  0, 0  1  1, 1  0  0  1  0  1  1  1  0  1  1  1  */
      results.add(new boolean[] {f||f||f, f||f||t, f||t||f, f||t||t, t||f||f, t||f||t, t||t||f, t||t||t });

      // query 3
      queries.add("age=23 OR city REGEX 'L.*x' OR amount BETWEEN 10.0 AND 2000.0");
      /*  helper                 0  0  0, 0  0  1, 0  0  0, 0  0  1, 1  0  0  1  0  1  1  0  0  1  0  1  */
      results.add(new boolean[] {f||f||f, f||f||t, f||f||f, f||f||t, t||f||f, t||f||t, t||f||f, t||f||t });

      // query 4
      queries.add("age=23 OR city REGEX 'Lo.d.n' OR amount BETWEEN 10.0 AND 2000.0");
      /*  helper                 0  0  0, 0  0  1, 0  1  0, 0  1  1, 1  0  0  1  0  1  1  1  0  1  1  1  */
      results.add(new boolean[] {f||f||f, f||f||t, f||t||f, f||t||t, t||f||f, t||f||t, t||t||f, t||t||t });

      // query 5
      queries.add("age=23 OR city REGEX 'Lo.d.x' OR amount BETWEEN 10.0 AND 2000.0");
      /*  helper                 0  0  0, 0  0  1, 0  0  0, 0  0  1, 1  0  0  1  0  1  1  0  0  1  0  1  */
      results.add(new boolean[] {f||f||f, f||f||t, f||f||f, f||f||t, t||f||f, t||f||t, t||f||f, t||f||t });

      this.querySet = (String[])queries.toArray(new String[queries.size()]);
      this.resultSet = (boolean[][])results.toArray(new boolean[results.size()][]);
      // here the real testing is performed (the algorithm is the same for all tests)
      selectorPerformTest();
   }








   /**
    * These are the examples found in the JMS 1.1. specification
    */
   public void testLikeOpWrapper() {
      try {
         String pattern = "12%3";
         LikeOpWrapper wrapper = new LikeOpWrapper(this.glob, pattern);
         
         assertEquals("wrong result with pattern '" + pattern + "' for '123'", true, wrapper.match("123"));
         assertEquals("wrong result with pattern '" + pattern + "' for '12993'", true, wrapper.match("12993"));
         assertEquals("wrong result with pattern '" + pattern + "' for '1234'", false, wrapper.match("1234"));

         pattern = "l_se";
         wrapper = new LikeOpWrapper(this.glob, pattern);
         assertEquals("wrong result with pattern '" + pattern + "' for 'lose'", true, wrapper.match("lose"));
         assertEquals("wrong result with pattern '" + pattern + "' for 'loose'", false, wrapper.match("loose"));

         pattern = "\\_%";
         wrapper = new LikeOpWrapper(this.glob, pattern, '\\');
         assertEquals("wrong result with pattern '" + pattern + "' for '_foo'", true, wrapper.match("_foo"));
         assertEquals("wrong result with pattern '" + pattern + "' for 'bar'", false, wrapper.match("bar"));

      }
      catch (XmlBlasterException ex) {
         ex.printStackTrace();
         assertTrue("an exception should not occur here " + ex.getMessage(), false);
      }
   }
   
   /**
    * <pre>
    *  java org.xmlBlaster.test.classtest.Sql92SelectorTest
    * </pre>
    */
   public static void main(String args[])
   {
      try {
         Global global = new Global(args);
         Sql92SelectorTest testSub = new Sql92SelectorTest(global, "Sql92SelectorTest");
         if (global.getProperty().get("interactive", false)) {
            testSub.setUp();
            testSub.interactive();
            return;
         }
         boolean doPerformance = global.getProperty().get("performance", false);
         
         testSub.setUp();
         testSub.testLikeOpWrapper();
         testSub.tearDown();

         testSub.setUp();
         testSub.testLogicalOps();
         if (doPerformance) testSub.performanceCheck();
         testSub.tearDown();

         testSub.setUp();
         testSub.testNullOps();
         if (doPerformance) testSub.performanceCheck();
         testSub.tearDown();
      
         testSub.setUp();
         testSub.testArithmeticOps();
         if (doPerformance) testSub.performanceCheck();
         testSub.tearDown();
      
         testSub.setUp();
         testSub.testInSetOps();
         if (doPerformance) testSub.performanceCheck();
         testSub.tearDown();
      
         testSub.setUp();
         testSub.testBetweenOps();
         if (doPerformance) testSub.performanceCheck();
         testSub.tearDown();
      
         testSub.setUp();
         testSub.testLikeOps();
         if (doPerformance) testSub.performanceCheck();
         testSub.tearDown();
      
         testSub.setUp();
         testSub.testRegexOps();
         if (doPerformance) testSub.performanceCheck();
         testSub.tearDown();
      
      }
      catch(Throwable e) {
         e.printStackTrace();
         //fail(e.toString());
      }
   }
}

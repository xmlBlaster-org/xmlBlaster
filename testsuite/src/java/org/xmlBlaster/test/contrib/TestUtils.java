/*------------------------------------------------------------------------------
Name:      TestUtils.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.test.contrib;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;

import org.custommonkey.xmlunit.XMLTestCase;
import org.custommonkey.xmlunit.XMLUnit;
import org.xmlBlaster.contrib.MomEventEngine;


/**
 * TestUtils
 * @author <a href="mailto:michele@laghi.eu">Michele Laghi</a>
 */
public class TestUtils  extends XMLTestCase {

   public TestUtils() {
      super();
      XMLUnit.setIgnoreWhitespace(true);
   }
   
   /**
    * Configure database access.
    * @see TestCase#setUp()
    */
   protected void setUp() throws Exception {
      super.setUp();
   }

   /*
    * @see TestCase#tearDown()
    */
   protected void tearDown() throws Exception {
      super.tearDown();
   }

   private String compress(int size, int sweeps, boolean isZip) {
      isZip = true;
      // long seed = System.currentTimeMillis();
      long t0 = 0L;
      long sumCompress = 0L;
      long sumExpand = 0L;
      long sumSize = 0L;
      // Random random = new Random(seed);
      HashMap map = new HashMap();
      byte[] buf = new byte[size];
      for (int i=0; i < sweeps; i++) {
         //random.nextBytes(buf);
         for (int j=0; j < buf.length; j++)
            buf[i] = (byte)j;
         t0 = System.currentTimeMillis();
         byte[] compressed = MomEventEngine.compress(buf, map, 1, null);
         sumCompress += System.currentTimeMillis() - t0;
         t0 = System.currentTimeMillis();
         byte[] copy =  getContent(MomEventEngine.decompress(new ByteArrayInputStream(compressed), map));
         sumExpand += System.currentTimeMillis() - t0;
         sumSize += compressed.length;
         assertEquals("Wrong size of copy of message", buf.length, copy.length);
         for (int j=0; j < buf.length; j++) {
            if (buf[j] != copy[j])
               assertTrue("The position '" + j + "' is '" + copy[j] + "' but should be '" + buf[j] + "'", false);
         }
      }
      double ratio = 1.0*sumSize/(1.0*size*sweeps);
      double comp = 1.0 * sumCompress / sweeps;
      double expand = 1.0 * sumExpand / sweeps;
      double limit = 1024.0 * (1.0 - ratio) * size / (comp + expand);
      
      String txt = "" + ratio + "\t" + comp  + "\t" + expand + "\t" + limit + "";
      return txt;
   }
   
   public static byte[] getContent(InputStream is) {
      int ret = 0;
      byte[] buf = new byte[1024];
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      try {
         while ( (ret=is.read(buf)) > -1) {
            baos.write(buf, 0, ret);
         }
      }
      catch (IOException ex) {
         ex.printStackTrace();
         return new byte[0];
      }
      return baos.toByteArray();
   }
   
   public void testCompress() {
      System.out.println("\t\tZIP\t\t\t\tGZIP\t\t");
      System.out.println("SIZE\tratio\tcompress (ms)\texpand (ms)\tlimit (KB/s)\t\tratio\tcompress (ms)\texpand (ms)\tlimit (KB/s)");
      /*
      compressBoth(100, 1); // fail anyway
      compressBoth(1000, 1000);
      compressBoth(10000, 100);
      compressBoth(100000, 10);
      compressBoth(1000000, 1);
      compressBoth(10000000, 1);
      */
      compressBoth(100, 1); // fail anyway
      compressBoth(1000, 1);
      compressBoth(10000, 1);
      compressBoth(100000, 1);
      compressBoth(1000000, 1);
      compressBoth(10000000, 1);
   }

   private void compressBoth(int size, int sweeps) {
      String txt1 = compress(size, sweeps, true);
      String txt2 = compress(size, sweeps, false);
      System.out.println("" + size + "\t" + txt1 + "\t" + txt2);
   }   
   
   /**
    * @param args
    */
   public static void main(String[] args) {
      // junit.swingui.TestRunner.run(TestDbBasics.class);
      
      TestUtils test = new TestUtils();
      try {
         test.setUp();
         test.testCompress();
         test.tearDown();
      } 
      catch (Exception ex) {
         ex.printStackTrace();
         fail();
      }
   }

}


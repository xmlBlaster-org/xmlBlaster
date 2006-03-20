/*
 * Copyright (c) 2003 Peter Antman, Teknik i Media  <peter.antman@tim.se>
 *
 * $Id$
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2 of the License, or (at your option) any later version
 * 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package org.xmlBlaster.test.j2ee;
import junit.framework.*;
/**
 * AllTests.java
 *
 *
 * Created: Tue Sep 23 13:20:40 2003
 *
 * @author <a href="mailto:pra@tim.se">Peter Antman</a>
 * @version $Revision: 1.1 $
 */

public class AllTests {

   public static Test suite() {
      TestSuite suite= new TestSuite("All xmlBlaster j2ee tests");
      suite.addTest(new TestSuite(org.xmlBlaster.test.j2ee.TestJ2eeServices.class));
      return suite;
   }
}// AllTests

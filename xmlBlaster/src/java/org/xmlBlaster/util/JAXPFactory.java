/*
 * Copyright (c) 2002 Peter Antman, Teknik i Media  <peter.antman@tim.se>
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
package org.xmlBlaster.util;

import javax.xml.parsers.SAXParserFactory;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.FactoryConfigurationError;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
/**
 * Factory for JAXP factories.
 *
 * <p>Use this factory when you need to localy override the System default settings for the JAXP parser and transformer factories.</p>
 *
 *
 * @author Peter Antman
 * @version $Revision: 1.1 $ $Date: 2002/09/19 09:14:55 $
 */

public class JAXPFactory {
   /**
    * Use the default SAXParserFactory.
    */
   public static SAXParserFactory newSAXParserFactory() 
      throws FactoryConfigurationError{
      return SAXParserFactory.newInstance();
   }
   
   /**
    * Use the SAXParserFactory class specifyed.
    */
   public static SAXParserFactory newSAXParserFactory(String factoryName)
      throws FactoryConfigurationError {
      try {
         return  (SAXParserFactory)newInstance(factoryName);
      } catch (InstanceException e) {
         throw new FactoryConfigurationError(e.getException(),e.getMessage());
      } // end of try-catch
   }
   /**
    * Use the default DocumentBuilderFactory.
    */
   public static DocumentBuilderFactory newDocumentBuilderFactory()
      throws FactoryConfigurationError {
      return DocumentBuilderFactory.newInstance();
   }
   /**
    * Use the DocumentBuilderFactory class specifyed.
    */
   public static DocumentBuilderFactory newDocumentBuilderFactory(String factoryName)
      throws FactoryConfigurationError {
      try {
         return (DocumentBuilderFactory)newInstance(factoryName);
      } catch (InstanceException e) {
         throw new FactoryConfigurationError(e.getException(),e.getMessage());
      } // end of try-catch
   }
   /**
    * Use the default TransformerFactory.
    */
   public static TransformerFactory newTransformerFactory()
      throws TransformerFactoryConfigurationError {
      return TransformerFactory.newInstance();
   }
   /**
    * Use the TransformerFactory class specifyed.
    */
   public static TransformerFactory newTransformerFactory(String factoryName)
      throws TransformerFactoryConfigurationError {
      try {
         return (TransformerFactory)newInstance(factoryName);
      } catch (InstanceException e) {
         throw new TransformerFactoryConfigurationError(e.getException(),e.getMessage());
      } // end of try-catch
      
      
   }

   /**
    * Load the given classname from the thread context classloader.
    */
   private static Object newInstance(String className) 
      throws InstanceException{
      try {  
         ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
         Class fac =classLoader.loadClass(className);
         return fac.newInstance();
         
      } catch (ClassNotFoundException e) {
         throw new InstanceException("Could not find class: "+className,e);
      } catch(Exception e) {
         throw new InstanceException("Could not load factory: "+className,e);
      }
   }
   
   
   static class InstanceException extends Exception {
      private Exception exception;
      
      InstanceException(String msg, Exception x) {
         super(msg);
         this.exception = x;
      }
      
      Exception getException() {
         return exception;
      }
   }
}

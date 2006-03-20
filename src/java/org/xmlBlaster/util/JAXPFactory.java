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
 * @author Peter Antman
 */
public class JAXPFactory {

   private final static JAXPFactory factory = new JAXPFactory();

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
      if (factoryName == null || factoryName.length() < 1) {
         return newSAXParserFactory();
      }
      try {
         SAXParserFactory spf = (SAXParserFactory) factory.getClass().getClassLoader().loadClass(factoryName).newInstance();
         return  spf;
      } catch (Exception e) {
         throw new FactoryConfigurationError(e,e.getMessage());
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
      if (factoryName == null || factoryName.length() < 1) {
         return newDocumentBuilderFactory();
      }
      try {
         DocumentBuilderFactory dbf = (DocumentBuilderFactory) factory.getClass().getClassLoader().loadClass(factoryName).newInstance();
         return dbf;
      } catch (Exception e) {
         throw new FactoryConfigurationError(e,e.getMessage());
      }
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
      if (factoryName == null || factoryName.length() < 1) {
         return newTransformerFactory();
      }
      try {
         TransformerFactory tf = (TransformerFactory) factory.getClass().getClassLoader().loadClass(factoryName).newInstance();
         return tf;
      } catch (Exception e) {
         throw new TransformerFactoryConfigurationError(e,e.getMessage());
      } // end of try-catch
   }
}

/*----------------------------------------------------------------------------
Name:      NmeaTest.cs
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Test cases for GPS NMEA reading from serial line
Author:    "Marcel Ruff" <xmlBlaster@marcelruff.info>
Date:      01/2007
-----------------------------------------------------------------------------*/
using System;
using System.Collections.Generic;
using System.Text;
using NUnit.Framework;
using System.Reflection;
using System.Collections;

namespace org.xmlBlaster.client
{
   [TestFixture]
   public class NmeaTest
   {
      private StringBuilder buf = new StringBuilder(1024);
      private static readonly string[] EMPTY_ARR = new string[0];

      [Test]
      public void CheckSerialInput()
      {
         string[] sentences = null;

         sentences = getSentences(buf);
         Assert.AreEqual(0, sentences.Length);

         buf.Append("746.92418,N,910.024934,E,000.0,000.0,080107,,*39,blabdie sdkfh,,sdkf\r\n$GPRMC,095637.01,A,4743.230636,N,00903.623845,E,071.7,302.0,080107,,*35\r\n$GPGSV,3,1,12,1");
         sentences = getSentences(buf);
         Assert.AreEqual(1, sentences.Length);
         Console.WriteLine("'" + sentences[0] + "'");
         Assert.AreEqual("$GPRMC,095637.01,A,4743.230636,N,00903.623845,E,071.7,302.0,080107,,*35", sentences[0]);

         sentences = getSentences(buf);
         Assert.AreEqual(0, sentences.Length);

         buf.Append("5,80,095,,16,63,206,31,03,53,095,,21,49,060,32*7C\r\n");
         sentences = getSentences(buf);
         Assert.AreEqual(1, sentences.Length);
         Console.WriteLine("'" + sentences[0] + "'");
         Assert.AreEqual("$GPGSV,3,1,12,15,80,095,,16,63,206,31,03,53,095,,21,49,060,32*7C", sentences[0]);

         buf.Append("$GPGSV,3,1,12,15,80,095,,16,63,206,31,03,53,095,,21,49,060,32*7C");
         sentences = getSentences(buf);
         Assert.AreEqual(0, sentences.Length);

         buf.Append("\r\n");
         sentences = getSentences(buf);
         Assert.AreEqual(1, sentences.Length);
         Console.WriteLine("'" + sentences[0] + "'");
         Assert.AreEqual("$GPGSV,3,1,12,15,80,095,,16,63,206,31,03,53,095,,21,49,060,32*7C", sentences[0]);

         buf.Append(",16,63,206,31,03,53,095,,21,49,060,32*7C\r\n");
         buf.Append("$GPGSV,3,1,12,15,80,095,,16,63,206,31,03,53,095,,21,49,060,32*7C\r\n");
         buf.Append("$GPGSV,3,2,12,18,46,110,25,22,32,154,,19,24,286,24,07,18,105,*78\r\n");
         buf.Append("$GPGSV,3,3,12,27,10,324,,29,06,038,,06,06,105,,26,05,050,*7D\r\n");
         buf.Append("$GPGGA,123158.86,4746.922579,N,00910.0");
         sentences = getSentences(buf);
         Assert.AreEqual(3, sentences.Length);
         for (int i = 0; i < sentences.Length; i++)
            Console.WriteLine(i + ": '" + sentences[i] + "'");
         Assert.AreEqual("$GPGSV,3,1,12,15,80,095,,16,63,206,31,03,53,095,,21,49,060,32*7C", sentences[0]);
         Assert.AreEqual("$GPGSV,3,2,12,18,46,110,25,22,32,154,,19,24,286,24,07,18,105,*78", sentences[1]);
         Assert.AreEqual("$GPGSV,3,3,12,27,10,324,,29,06,038,,06,06,105,,26,05,050,*7D", sentences[2]);
         Console.WriteLine("DONE");
      }

      public string[] getSentences(StringBuilder buf)
      {
         int curr = 0;
         ArrayList arrayList = new ArrayList();
         string sentence;
         while ((sentence = getSentence(buf, ref curr)) != null) {
            arrayList.Add(sentence);
         }
         if (arrayList.Count == 0)
            return EMPTY_ARR;
         buf.Remove(0, curr);
         return (string[])arrayList.ToArray(typeof( string ));
      }

      public string getSentence(StringBuilder buf, ref int curr) {
         int start = GetIndexOf(buf, curr, '$');
         if (start == -1) return null;
         //int end = GetIndexOf(buf, 0, '\n');
         //if (end == -1) return EMPTY_ARR;
         //string sentence = buf.
         StringBuilder sentence = new StringBuilder(256);
         bool isComplete = false;
         int origCurr = curr;
         for (curr = start; curr < buf.Length; curr++)
         {
            if (buf[curr] == '\r')
               continue; // ignore
            if (buf[curr] == '\n') {
               isComplete = true;
               break;
            }
            sentence.Append(buf[curr]);
         }
         if (!isComplete)
         {
            curr = origCurr;
            return null;
         }
         return sentence.ToString();
      }

      private int GetIndexOf(StringBuilder buf, int start, char chr)
      {
         if (start < 0) start = 0;
         for (int i = start; i < buf.Length; i++)
            if (buf[i] == chr)
               return i;
         return -1;
      }

      /*
      static void Main(string[] argv)
      {
         NmeaTest n = new NmeaTest();
         n.CheckSerialInput();
      }
      */
   }

}

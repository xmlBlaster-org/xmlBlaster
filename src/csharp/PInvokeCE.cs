// PInvokeCE.cs  2006-12  http://www.xmlBlaster.org/
// Simple layer to delegate C# calls to Windows CE xmlBlaster client C library
// Using P/Invoke of the .NET Compact Framework 1.0
//
// You need:
//    xmlBlasterClientC-Arm4.dll
//    pthreads270-Arm4.dll
//    zlib123-Arm4.dll
//
// Currently only tested on Windows CE 4.2 and 5.1 with ARM processor
//
// o All DLL C code is 'multibyte characters' of type UTF-8
// o All C# code is 'wchar_t' UTF-16
// o We do all conversion from UTF-8 or to UTF-8in C# and transfer byte[]
// o char * are allocated in the DLL and freed in the DLL
//
// Features: All features of the client C library (compression, tunnel callbacks), see
//           http://www.xmlblaster.org/xmlBlaster/doc/requirements/client.c.socket.html
//
// IMPORTANT: Please edit/change the lookup path below for xmlBlasterClientC.dll
//            and set the PATH to access it
//
// @todo     publishOneway crashes
//           OnUpdate() throwing exception seems not to be passed to C
//           logging with log4net
//           write a testsuite
//           write a requirement
//           create an assembly with ant or nant
//           create the same wrapper for the xmlBlaster C++ library
//
// @author   xmlBlaster@marcelruff.info
//
// @prepare Compile the C client library first (see xmlBlaster\src\c\xmlBlasterClientC.sln)
// @compile csc /d:NATIVE_C_MAIN -debug+ -out:PInvokeCE.exe PInvokeCE.cs   (Windows)
//
// @see      http://www.xmlblaster.org/xmlBlaster/doc/requirements/client.csharp.html
// @c        http://www.xmlBlaster/org
// @see http://msdn.microsoft.com/mobility/understanding/articles/default.aspx?pull=/library/en-us/dnnetcomp/html/netcfintrointerp.asp?frame=true
// @see http://msdn.microsoft.com/mobility/understanding/articles/default.aspx?pull=/library/en-us/dnnetcomp/html/netcfadvinterop.asp
//
/*
Usage:
XmlBlaster C SOCKET client

   -dispatch/connection/plugin/socket/hostname [localhost]
                       Where to find xmlBlaster.
   -dispatch/connection/plugin/socket/port [7607]
                       The port where xmlBlaster listens.
   -dispatch/connection/plugin/socket/localHostname [NULL]
                       Force the local IP, useful on multi homed computers.
   -dispatch/connection/plugin/socket/localPort [0]
                       Force the local port, useful to tunnel firewalls.
   -dispatch/connection/plugin/socket/compress/type []
                       Switch on compression with 'zlib:stream'.
   -dispatch/connection/plugin/socket/useUdpForOneway [false]
                       Use UDP for publishOneway() calls.
   -dispatch/callback/plugin/socket/hostname [localhost]
                       The IP where to establish the callback server.
                       Can be useful on multi homed hosts.
   -dispatch/callback/plugin/socket/port [7611]
                       The port of the callback server.
   -plugin/socket/multiThreaded  [true]
                       If true the update() call to your client code is a separate thread.
   -plugin/socket/responseTimeout  [60000 (one minute)]
                       The time in millis to wait on a response, 0 is forever.
   -logLevel            ERROR | WARN | INFO | TRACE | DUMP [WARN]

Example:
  PInvokeCE -logLevel TRACE -dispatch/connection/plugin/socket/hostname 192.168.2.9
*/
using System;
using System.Text;
using System.Runtime.InteropServices;

namespace org.xmlBlaster
{
   /// Calling unmanagegd code: xmlBlasterClientC.dll
   public class PInvokeCE : I_XmlBlasterAccess
   {
      static bool verbose = true; // TODO: log4net

#     if XMLBLASTER_CLIENT_MONO // Linux Debug, set LD_LIBRARY_PATH to find the shared library
         const string XMLBLASTER_C_LIBRARY = "xmlBlasterClientCD"; //libxmlBlasterClientCD.so
#     elif PocketPC || Smartphone || WINCE
         const string XMLBLASTER_C_LIBRARY = "xmlBlasterClientCD-Arm4.dll";
#     else // Windows
         const string XMLBLASTER_C_LIBRARY = "..\\..\\lib\\xmlBlasterClientC.dll";
#     endif

      // Helper struct for DLL calls
      unsafe struct XmlBlasterUnmanagedException
      {
         public XmlBlasterUnmanagedException(bool isRemote) {
            remote = (isRemote) ? 1 : 0;
            errorCode = IntPtr.Zero;
            message = IntPtr.Zero;
         }
         public int remote;
         public IntPtr errorCode;
         public IntPtr message;
         public bool CaughtException() {
            return errorCode != IntPtr.Zero;
         }
         public string GetErrorCode() {
            return stringFromUtf8IntPtr(errorCode);
         }
         public string GetMessage() {
            return stringFromUtf8IntPtr(message);
         }
      }

      public struct QosArr
      {
         public int len;  /* Number of XML QoS strings */
         public string[] qosArr;
      }

      public struct MsgUnitUnmanagedArr
      {
         public string secretSessionId;
         public int len;
         public MsgUnit[] msgUnitArr;
      }



      [StructLayout(LayoutKind.Sequential/*, CharSet = CharSet.Unicode*/)]
      public class StringArr
      {
         public string str;
      }

      public static void logger(String str)
      {
         if (verbose) Console.WriteLine("[C#] "+str);
      }

      delegate string UpdateUnmanagedFp(string cbSessionId, MsgUnit msgUnit, ref XmlBlasterUnmanagedException exception);

      /**
       * Allocates native C memory. 
       * You need to call
       *   xmlBlasterUnmanagedExceptionFree(XmlBlasterUnmanagedException *exception)
       * later!
       */
      void fillUnmanagedException(ref XmlBlasterUnmanagedException outE, string errorCode, string message)
      {
         outE.errorCode = stringToUtf8IntPtr(errorCode);
         outE.message = stringToUtf8IntPtr(message);
         outE.remote = 1;
      }

      /**
       * Copy the unmanaged data to our XmlBlasterException and
       * frees the native memory.
       */
      XmlBlasterException fillXmlBlasterException(ref XmlBlasterUnmanagedException exception)
      {
         XmlBlasterException managed = new XmlBlasterException(exception.remote != 0,
              stringFromUtf8IntPtr(exception.errorCode),
              stringFromUtf8IntPtr(exception.message));
         xmlBlasterUnmanagedExceptionFree(ref exception);
         return managed;
      }

      /// Callback by xmlBlaster, see UpdateUnmanagedFp
      string updateUnmanaged(string cbSessionId, MsgUnit msgUnit, ref XmlBlasterUnmanagedException exception)
      {
         if (null != onUpdate)
         {
            try
            {
               return onUpdate(cbSessionId, msgUnit);
            }
            // TODO: Exception seems not to reach the C code
            catch (XmlBlasterException e)
            {
               logger("OnUpdate() exception: " + e.ToString());
               fillUnmanagedException(ref exception, e.ErrorCode, e.Message);
               return null;
            }
            catch (Exception e)
            {
               logger("OnUpdate() exception: " + e.ToString());
               fillUnmanagedException(ref exception, "user.update.internalError", e.ToString());
               return null;
            }
         }
         logger("updateUnmanaged invoked START ==================");
         logger(msgUnit.key);
         logger(msgUnit.getContentStr());
         logger(msgUnit.qos);
         string ret = "<qos><state id='OK'/></qos>";
         logger("updateUnmanaged invoked DONE ===================");
         return ret;
      }

      /**
       * Convert a string to a UTF-8 encoded byte array.
       * @param str UTF-16 C# string
       * @return UTF-8 multibyte array to be passed to C dll (zero terminated)
       */
      public static byte[] StringToUtf8ByteArray(string str)
      {
         byte[] data = System.Text.Encoding.UTF8.GetBytes(str); // TODO
         return data;
      }

      /**
       * Convert a UTF-8 byte array to a UTF-16 string
       * @param dBytes UTF-8 multibyte string from C (zero terminated)
       * @return string to be used in C#
       */
      public static string Utf8ByteArrayToString(byte[] dBytes)
      {
         string str;
         str = System.Text.Encoding.UTF8.GetString(dBytes, 0, dBytes.Length);
         return str;
      }

      /**
       * Allocates native memory
       * @return The pointer memory needs to be freed
       */
      unsafe static IntPtr stringToUtf8IntPtr(string str) {
         byte[] e = StringToUtf8ByteArray(str);
         IntPtr ptr = xmlBlasterUnmanagedMalloc(e.Length+1);
         byte* bp = (byte*)ptr.ToPointer();
         for (int i=0; i<e.Length; i++) {
            bp[i] = e[i];
         }
         bp[e.Length] = 0;
         return ptr;
      }


      /**
       * Handling a DLL C function which returns a malloced char *
       * 
       * As the following throws NotSupportedException on Windows CE
       *   byte[] tmp = stringReturner();
       * we have a workaround and return an IntPtr.
       * The string which was allocated in the DLL C code is
       * extracted here and then then freed by a call to
       *   xmlBlasterFree(IntPtr)
       * @param ptr A C malloced 'char *' containing UTF-8 text
       * @return A unicode string ('wchar_t *' UTF-16 string)
       */
      unsafe static string stringFromUtf8IntPtr(IntPtr ptr)
      {
         if (ptr == IntPtr.Zero)
         {
            return "";
         }

         //Cannot convert type 'System.IntPtr' to 'byte[]'
         //  byte[] tmp = (byte[])sayHelloRet();
         //so we need to copy it manually:
         void* vPtr = ptr.ToPointer();
         byte* tmpP = (byte*)vPtr;
         int len = 0;
         for (len = 0; tmpP[len] != 0; len++) ;
         byte[] tmp = new byte[len];
         for (int i = 0; i < tmp.Length; i++)
            tmp[i] = tmpP[i];
         //Console.WriteLine("PInvokeCE.cs: After calling DLL C function returning a malloc char*");
         string str = Utf8ByteArrayToString(tmp);
         logger("DLL returned us '" + str + "'");

         // Now free() the malloc() IntPtr in the DLL ...
         xmlBlasterUnmanagedFree(ptr);
         //Console.WriteLine("PInvokeCE.cs: After  xmlBlasterUnmanagedFree()");
         return str;
      }
      /*
         [StructLayout(LayoutKind.Explicit)]
         public struct Rect {
             [FieldOffset(0)] public int left;
             [FieldOffset(4)] public int top;
             [FieldOffset(8)] public int right;
             [FieldOffset(12)] public int bottom;
         }
      */

      [DllImport(XMLBLASTER_C_LIBRARY)]
      private extern static void sayHello();

      [DllImport(XMLBLASTER_C_LIBRARY)]
      private extern static void sayHelloLPCT(string p);


      [DllImport(XMLBLASTER_C_LIBRARY)]
      private extern static void sayHelloP(int size, byte[] p);

      //private extern static void sayHelloP(int size, /*[MarshalAs(UnmanagedType.LPStr)]*/ String p);
      //private extern static void sayHelloP(int size, char[] p);

      [DllImport(XMLBLASTER_C_LIBRARY)]
      private extern static void sayHelloArr(byte[][] arr);
      // private extern static void sayHelloArr(string[] arr);

      [DllImport(XMLBLASTER_C_LIBRARY)]
      private extern static void sayHelloEx(ref XmlBlasterUnmanagedException exception);

      [DllImport(XMLBLASTER_C_LIBRARY)]
      private extern static IntPtr sayHelloRet();
      //private extern static byte[] sayHelloRet(); // throws NotSupportedException on WINCE

      [DllImport(XMLBLASTER_C_LIBRARY)]
      private extern static IntPtr xmlBlasterUnmanagedMalloc(int size);

      [DllImport(XMLBLASTER_C_LIBRARY)]
      private extern static void xmlBlasterUnmanagedFree(IntPtr p);

      [DllImport(XMLBLASTER_C_LIBRARY)]
      private extern static void xmlBlasterUnmanagedExceptionFree(ref XmlBlasterUnmanagedException exception);

      [DllImport(XMLBLASTER_C_LIBRARY)]
      private extern static IntPtr getXmlBlasterAccessUnparsedUnmanaged(int argc, IntPtr[] argv);

      [DllImport(XMLBLASTER_C_LIBRARY)]
      private extern static void freeXmlBlasterAccessUnparsedUnmanaged(IntPtr xa);

      [DllImport(XMLBLASTER_C_LIBRARY)]
      private extern static IntPtr xmlBlasterUnmanagedConnect(IntPtr xa, IntPtr qos, IntPtr updateUnmanaged, ref XmlBlasterUnmanagedException exception);

      [DllImport(XMLBLASTER_C_LIBRARY)]
      private extern static bool xmlBlasterUnmanagedInitialize(IntPtr xa, IntPtr updateUnmanaged, ref XmlBlasterUnmanagedException exception);

      [DllImport(XMLBLASTER_C_LIBRARY)]
      private extern static bool xmlBlasterUnmanagedDisconnect(IntPtr xa, IntPtr qos, ref XmlBlasterUnmanagedException exception);

      [DllImport(XMLBLASTER_C_LIBRARY)]
      private extern static string xmlBlasterUnmanagedPublish(IntPtr xa, MsgUnit msgUnit, ref XmlBlasterUnmanagedException exception);

      //[DllImport(XMLBLASTER_C_LIBRARY )]
      //private extern static QosArr xmlBlasterUnmanagedPublishArr(IntPtr xa, MsgUnitArr msgUnitArr, ref XmlBlasterUnmanagedException exception);

      [DllImport(XMLBLASTER_C_LIBRARY)]
      private extern static void xmlBlasterUnmanagedPublishOneway(IntPtr xa, MsgUnit[] msgUnitArr, int length, ref XmlBlasterUnmanagedException exception);

      [DllImport(XMLBLASTER_C_LIBRARY)]
      private extern static string xmlBlasterUnmanagedSubscribe(IntPtr xa, string key, string qos, ref XmlBlasterUnmanagedException exception);

      [DllImport(XMLBLASTER_C_LIBRARY)]
      private extern static void xmlBlasterUnmanagedUnSubscribe(IntPtr xa, string key, string qos, ref XmlBlasterUnmanagedException exception, out int size, out IntPtr ptr);

      [DllImport(XMLBLASTER_C_LIBRARY)]
      private extern static void xmlBlasterUnmanagedErase(IntPtr xa, string key, string qos, ref XmlBlasterUnmanagedException exception, out int size, out IntPtr ptr);

      [DllImport(XMLBLASTER_C_LIBRARY)]
      private extern static void xmlBlasterUnmanagedGet(IntPtr xa, string key, string qos, ref XmlBlasterUnmanagedException exception, out int size, out IntPtr ptr);

      [DllImport(XMLBLASTER_C_LIBRARY)]
      private extern static string xmlBlasterUnmanagedPing(IntPtr xa, string qos, ref XmlBlasterUnmanagedException exception);

      [DllImport(XMLBLASTER_C_LIBRARY)]
      private extern static bool xmlBlasterUnmanagedIsConnected(IntPtr xa);

      [DllImport(XMLBLASTER_C_LIBRARY)]
      private extern static string xmlBlasterUnmanagedUsage();


      private IntPtr xa;
      private UpdateUnmanagedFp updateUnmanagedFp;

      unsafe public PInvokeCE(string[] argv)
      {
         logger("Entering PInvokeCE() ....");

         if (argv == null) argv = new String[0];

         updateUnmanagedFp = new UpdateUnmanagedFp(this.updateUnmanaged);

         // Convert command line arguments: C client lib expects the executable name as first entry
         IntPtr[] c_argv = new IntPtr[argv.Length + 1];
         c_argv[0] = stringToUtf8IntPtr("NativC"); // my executable name
         for (int i = 0; i < argv.Length; ++i)
         {
            if (argv[i] == "--help")
            {
               logger("Usage:\n" + xmlBlasterUnmanagedUsage());
               throw new XmlBlasterException("user.usage", "Good bye");
            }
            c_argv[i + 1] = stringToUtf8IntPtr(argv[i]);
         }

         xa = getXmlBlasterAccessUnparsedUnmanaged(c_argv.Length, c_argv);
         
         {
            /*
            sayHello();
            sayHelloLPCT("Mein LPCTSTR");
            string txt = "Text transfer";
            sayHelloP(txt.Length, StringToUtf8ByteArray(txt));

            logger("Before sayHelloRet() ...");
            string ret = stringFromUtf8IntPtr(sayHelloRet());
            logger("sayHelloRet(): " + ret);

             [DllImport("TheirDLL.dll", SetLastError=true)]
            public static extern void TheirDllFunc( byte[] Path );

            string sPath = @"C:\MyFolder\MySubFolder\";
            ASCIIEncoding ascii = new ASCIIEncoding();
            byte[] byPath = ascii.GetBytes( sPath );
            TheirDllFunc( byPath );

            The external function's signature is:
                    void TheirDllFunc( char *path); 
            logger("PInvokeCE() ...");
            */
            XmlBlasterUnmanagedException exception = new XmlBlasterUnmanagedException(false);
            logger("calling now sayHelloEx()");
            sayHelloEx(ref exception);
            if (exception.CaughtException()) {
               XmlBlasterException e = fillXmlBlasterException(ref exception);
               logger("Got exception: " + e.ToString());
            }
         }

         logger("Leaving PInvokeCE()");
      }

      ~PInvokeCE()
      {
         if (xa != new IntPtr(0))
            freeXmlBlasterAccessUnparsedUnmanaged(xa);
         logger("~PInvokeCE() ...");
      }

      void check(string methodName)
      {
         logger(methodName + "() check ...");
         if (xa == new IntPtr(0))
            throw new XmlBlasterException("internal.illegalState", "Can't process " + methodName + ", xmlBlaster pointer is reset, please create a new instance of PInvokeCE.cs class after a disconnect() call");
      }

      private delegate string OnUpdate(string cbSessionId, MsgUnit msgUnit);
      private event OnUpdate onUpdate;

      public string connect(string qos, I_Callback listener)
      {
         check("connect");
         if (listener != null)
         {
            onUpdate += new OnUpdate(listener.OnUpdate);
         }
         try
         {
            XmlBlasterUnmanagedException exception = new XmlBlasterUnmanagedException(false);
            bool bb = xmlBlasterUnmanagedInitialize(xa, IntPtr.Zero, ref exception);
            //bool bb = xmlBlasterUnmanagedInitialize(xa, updateUnmanagedFp, ref exception);
            if (exception.CaughtException())
            {
               logger("xmlBlasterUnmanagedInitialize: Got exception from C: exception=" + exception.errorCode + " - " + exception.message);
               throw fillXmlBlasterException(ref exception);
            }
            else
               logger("xmlBlasterUnmanagedInitialize: SUCCESS '" + bb + "' xa:"/* + xa.isInitialized*/);

            logger("connect 2");
            IntPtr retP = xmlBlasterUnmanagedConnect(xa, stringToUtf8IntPtr(qos), IntPtr.Zero, ref exception);
            string ret = stringFromUtf8IntPtr(retP);
            //string ret = xmlBlasterUnmanagedConnect(xa, qos, updateUnmanagedFp, ref exception);
            if (exception.CaughtException())
            {
               XmlBlasterException e = fillXmlBlasterException(ref exception);
               logger("xmlBlasterUnmanagedConnect: Got exception from C: " + e.ToString());
               throw e;
            }
            else
               logger("xmlBlasterUnmanagedConnect: SUCCESS '" + ret + "'");
            return ret;
         }
         catch (XmlBlasterException e)
         {
            throw e;
         }
         catch (Exception e)
         {
            throw new XmlBlasterException("internal.unknown", "connect failed", e);
         }
      }

      /// After calling diconnect() this class is not usable anymore
      /// you need to create a new instance to connect again
      public bool disconnect(string qos)
      {
         check("disconnect");
         try
         {
            XmlBlasterUnmanagedException exception = new XmlBlasterUnmanagedException(false);
            bool bb = xmlBlasterUnmanagedDisconnect(xa, stringToUtf8IntPtr(qos), ref exception);
            if (exception.CaughtException())
            {
               logger("xmlBlasterUnmanagedDisconnect: Got exception from C: exception=" + exception.errorCode + " - " + exception.message);
               throw fillXmlBlasterException(ref exception);
            }
            else
               logger("xmlBlasterUnmanagedDisconnect: SUCCESS '" + bb + "'");

            freeXmlBlasterAccessUnparsedUnmanaged(xa);
            xa = new IntPtr(0);
            logger("xmlBlasterUnmanagedDisconnect: SUCCESS freed all resources");

            return bb;
         }
         catch (XmlBlasterException e)
         {
            throw e;
         }
         catch (Exception e)
         {
            throw new XmlBlasterException("internal.unknown", "disconnect failed", e);
         }
      }

      public string publish(string key, string content, string qos)
      {
         check("publish");
         try
         {
            XmlBlasterUnmanagedException exception = new XmlBlasterUnmanagedException(false);
            MsgUnit msgUnit = new MsgUnit();
            msgUnit.key = key;
            msgUnit.setContentStr(content);
            //unsafe { msgUnit.content =  StringToUtf8ByteArray(content); }
            msgUnit.contentLen = content.Length;
            msgUnit.qos = qos;
            string ret = xmlBlasterUnmanagedPublish(xa, msgUnit, ref exception);
            if (exception.CaughtException())
            {
               logger("xmlBlasterUnmanagedPublish: Got exception from C: exception=" + exception.errorCode + " - " + exception.message);
               throw fillXmlBlasterException(ref exception);
            }
            else
               logger("xmlBlasterUnmanagedPublish: SUCCESS '" + ret + "'");
            return ret;
         }
         catch (XmlBlasterException e)
         {
            throw e;
         }
         catch (Exception e)
         {
            throw new XmlBlasterException("internal.unknown", "publish failed", e);
         }
      }

      // TODO: Crashs for array size > 2
      /*
       Invalid read of size 1
         at 0x63FFF14: encodeMsgUnitArr (xmlBlasterSocket.c:222)
            by 0x63FD11E: xmlBlasterPublishOneway (XmlBlasterConnectionUnparsed.c:1004)
            by 0x63F9125: xmlBlasterPublishOneway (XmlBlasterAccessUnparsed.c:726)
            by 0x63FF0B8: xmlBlasterUnmanagedPublishOneway (XmlBlasterUnmanaged.c:365)
            by 0x6524CC8: ???
      */
      public void publishOneway(MsgUnit[] msgUnitArr)
      {
         check("publishOneway");
         try
         {
            XmlBlasterUnmanagedException exception = new XmlBlasterUnmanagedException(false);
            xmlBlasterUnmanagedPublishOneway(xa, msgUnitArr, msgUnitArr.Length, ref exception);
            if (exception.CaughtException())
            {
               logger("publishOneway: Got exception from C: exception=" + exception.errorCode + " - " + exception.message);
               throw fillXmlBlasterException(ref exception);
            }
            else
               logger("publishOneway: SUCCESS");
         }
         catch (XmlBlasterException e)
         {
            throw e;
         }
         catch (Exception e)
         {
            throw new XmlBlasterException("internal.unknown", "publishOneway failed", e);
         }
      }

      public string subscribe(string key, string qos)
      {
         check("subscribe");
         try
         {
            XmlBlasterUnmanagedException exception = new XmlBlasterUnmanagedException(false);
            string ret = xmlBlasterUnmanagedSubscribe(xa, key, qos, ref exception);
            if (exception.CaughtException())
            {
               logger("xmlBlasterUnmanagedSubscribe: Got exception from C: exception=" + exception.errorCode + " - " + exception.message);
               throw fillXmlBlasterException(ref exception);
            }
            else
               logger("xmlBlasterUnmanagedSubscribe: SUCCESS '" + ret + "'");
            return ret;
         }
         catch (XmlBlasterException e)
         {
            throw e;
         }
         catch (Exception e)
         {
            throw new XmlBlasterException("internal.unknown", "subscribe failed", e);
         }
      }

      public string[] unSubscribe(string key, string qos)
      {
         check("unSubscribe");
         try
         {
            XmlBlasterUnmanagedException exception = new XmlBlasterUnmanagedException(false);
            int size;
            IntPtr outArray;
            xmlBlasterUnmanagedUnSubscribe(xa, key, qos, ref exception, out size, out outArray);
            if (exception.CaughtException())
            {
               logger("xmlBlasterUnmanagedUnSubscribe: Got exception from C: exception=" + exception.errorCode + " - " + exception.message);
               throw fillXmlBlasterException(ref exception);
            }
            StringArr[] manArray = new StringArr[size];
            String[] retQosArr = new String[size];
            IntPtr current = outArray;
            for (int i = 0; i < size; i++)
            {
               manArray[i] = new StringArr();
               Marshal.PtrToStructure(current, manArray[i]);
#              if PocketPC || Smartphone
#              else
               Marshal.DestroyStructure( current, typeof(StringArr) );
#              endif
               current = (IntPtr)((long)current + Marshal.SizeOf(manArray[i]));
               //Console.WriteLine( "Element {0}: str={1}", i, manArray[ i ].str );
               retQosArr[i] = manArray[i].str;
            }
#           if Smartphone || PocketPC
#           else
               Marshal.FreeCoTaskMem(outArray);
#           endif
            logger("xmlBlasterUnmanagedUnSubscribe: SUCCESS");
            return retQosArr;
         }
         catch (XmlBlasterException e)
         {
            throw e;
         }
         catch (Exception e)
         {
            //try {
            //   disconnect("<qos/>");
            //}
            //catch (Exception e2) {
            //   logger("xmlBlasterUnmanagedUnSubscribe: Ignoring " + e2.ToString() + " root was " +e.ToString());
            //}
            throw new XmlBlasterException("internal.unknown", "unSubscribe failed", e);
         }
      }

      public string[] erase(string key, string qos)
      {
         check("erase");
         try
         {
            XmlBlasterUnmanagedException exception = new XmlBlasterUnmanagedException(false);
            int size;
            IntPtr outArray;
            xmlBlasterUnmanagedErase(xa, key, qos, ref exception, out size, out outArray);
            if (exception.CaughtException())
            {
               logger("xmlBlasterUnmanagedErase: Got exception from C: exception=" + exception.errorCode + " - " + exception.message);
               throw fillXmlBlasterException(ref exception);
            }
            StringArr[] manArray = new StringArr[size];
            String[] retQosArr = new String[size];
            IntPtr current = outArray;
            for (int i = 0; i < size; i++)
            {
               manArray[i] = new StringArr();
               Marshal.PtrToStructure(current, manArray[i]);
#              if PocketPC || Smartphone
#              else
                  Marshal.DestroyStructure( current, typeof(StringArr) );
#              endif
               current = (IntPtr)((long)current + Marshal.SizeOf(manArray[i]));
               //Console.WriteLine( "Element {0}: str={1}", i, manArray[ i ].str );
               retQosArr[i] = manArray[i].str;
            }
#           if Smartphone || PocketPC
#           else
               Marshal.FreeCoTaskMem(outArray);
#           endif
            logger("xmlBlasterUnmanagedErase: SUCCESS");
            return retQosArr;
         }
         catch (XmlBlasterException e)
         {
            throw e;
         }
         catch (Exception e)
         {
            throw new XmlBlasterException("internal.unknown", "unSubscribe failed", e);
         }
      }

      public MsgUnit[] get(string key, string qos)
      {
         check("get");
         try
         {
            XmlBlasterUnmanagedException exception = new XmlBlasterUnmanagedException(false);
            int size;
            IntPtr outArray;
            xmlBlasterUnmanagedGet(xa, key, qos, ref exception, out size, out outArray);
            if (exception.CaughtException())
            {
               logger("xmlBlasterUnmanagedGet: Got exception from C: exception=" + exception.errorCode + " - " + exception.message);
               throw fillXmlBlasterException(ref exception);
            }

            logger("get() size=" + size);
            MsgUnit[] manArray = new MsgUnit[size];
            IntPtr current = outArray;
            for (int i = 0; i < size; i++)
            {
               manArray[i] = new MsgUnit();
               Marshal.PtrToStructure(current, manArray[i]);
               //Marshal.FreeCoTaskMem( (IntPtr)Marshal.ReadInt32( current ));
#              if PocketPC || Smartphone
#              else
                  Marshal.DestroyStructure( current, typeof(MsgUnit) );
#              endif
               current = (IntPtr)((long)current +
               Marshal.SizeOf(manArray[i]));
               //Console.WriteLine( "Element {0}: key={1} qos={2} buffer={3} contentLength={4}", i, 
               //   manArray[ i ].key, manArray[ i ].qos, manArray[ i ].content, manArray[ i ].contentLen );
            }
#           if Smartphone || PocketPC
#           else
               Marshal.FreeCoTaskMem( outArray );
#           endif
            logger("xmlBlasterUnmanagedGet: SUCCESS");
            return manArray;
         }
         catch (XmlBlasterException e)
         {
            throw e;
         }
         catch (Exception e)
         {
            throw new XmlBlasterException("internal.unknown", "get failed", e);
         }
      }

      public string ping(string qos)
      {
         check("ping");
         try
         {
            XmlBlasterUnmanagedException exception = new XmlBlasterUnmanagedException(false);
            string ret = xmlBlasterUnmanagedPing(xa, qos, ref exception);
            if (exception.CaughtException())
            {
               logger("xmlBlasterUnmanagedPing: Got exception from C: exception=" + exception.errorCode + " - " + exception.message);
               throw fillXmlBlasterException(ref exception);
            }
            else
               logger("xmlBlasterUnmanagedPing: SUCCESS '" + ret + "'");
            return ret;
         }
         catch (XmlBlasterException e)
         {
            throw e;
         }
         catch (Exception e)
         {
            throw new XmlBlasterException("internal.unknown", "ping failed", e);
         }
      }

      public bool isConnected()
      {
         logger("isConnected() ...");
         if (xa == new IntPtr(0)) return false;
         try
         {
            bool bb = xmlBlasterUnmanagedIsConnected(xa);
            logger("xmlBlasterUnmanagedIsConnected: SUCCESS '" + bb + "'");
            return bb;
         }
         catch (XmlBlasterException e)
         {
            throw e;
         }
         catch (Exception e)
         {
            throw new XmlBlasterException("internal.unknown", "isConnected failed", e);
         }
      }

      public static string usage()
      {
         return xmlBlasterUnmanagedUsage();
      }
   }
}

/*----------------------------------------------------------------------------
Name:      xmlBlasterZlib.c
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Contains some socket specific helper methods
Author:    "Marcel Ruff" <xmlBlaster@marcelruff.info>
-----------------------------------------------------------------------------*/
#include <stdio.h>
#include <string.h>
#include <assert.h>
#include <socket/xmlBlasterZlib.h>
#include <socket/xmlBlasterSocket.h>  /* writen() */
#ifdef __IPhoneOS__
#include <CoreFoundation/CFSocket.h>
#include <XmlBlasterConnectionUnparsed.h>
#endif

#if XMLBLASTER_ZLIB==1

/*
TODO: 
 valgrind reports with zlib-1.2.1
   ==24008== Conditional jump or move depends on uninitialised value(s)
   ==24008==    at 0x8051823: longest_match (deflate.c:949)
   ==24008==    by 0x8052691: deflate_slow (deflate.c:1422)
   ==24008==    by 0x8050F19: deflate (deflate.c:630)
   ==24008==    by 0x804BE78: xmlBlaster_writenCompressed (xmlBlasterZlib.c:102)

 See a discussion of this:
  http://www.groupsrv.com/science/viewtopic.php?t=19234&start=0&postdays=0&postorder=asc&highlight=
  (http://www.gammon.com.au/forum/bbshowpost.php?bbsubject_id=4000)

Example:
   XmlBlasterZlibWriteBuffers zlibWriteBuf;

   if (xmlBlaster_initZlibWriter(zlibWriteBufP) != Z_OK) return -1;
   
   while () {
      ...
      ssize_t num = xmlBlaster_writenCompressed(&zlibWriteBuf, socketFd, buffer, nbytes);
   }

   
   XmlBlasterZlibReadBuffers zlibReadBuf;
   if (xmlBlaster_initZlibReader(&zlibReadBuf) != Z_OK) return -1;

*/

/**
 * Helper for debugging. 
 */
static void dumpZlib(const char *p, XmlBlasterZlibReadBuffers *zlibReadBufP, XmlBlasterZlibWriteBuffers *zlibWriteBufP) {
        z_stream *zlibP = (zlibReadBufP!=0) ? &zlibReadBufP->c_stream : &zlibWriteBufP->c_stream;
   printf("[%s:%d] %s\n", __FILE__, __LINE__, p);
   printf("{\n");
        if (zlibReadBufP!=0) {
           /*printf("  compBufferP     ="PRINTF_PREFIX_UINT64_T"\n", (uint64_t)zlibReadBufP->compBuffer);*/
           printf("  compBufferP     =%p\n", zlibReadBufP->compBuffer);
           printf("  currCompBufferP =%p\n", zlibReadBufP->currCompBufferP);
           printf("  currCompBytes   =%d\n", (int)zlibReadBufP->currCompBytes);
        }
   printf("  zlibP->next_in  =%p\n", zlibP->next_in);
   printf("  zlibP->avail_in =%d\n", (int)zlibP->avail_in);
   printf("  zlibP->next_out =%p\n", zlibP->next_out);
   printf("  zlibP->avail_out=%u\n", zlibP->avail_out);
   printf("}\n");
}

/**
 * @see xmlBlasterZlib.h
 */
int xmlBlaster_initZlibWriter(XmlBlasterZlibWriteBuffers *zlibWriteBufP) {
   int err;

   if (!zlibWriteBufP) return Z_BUF_ERROR;
   memset(zlibWriteBufP, 0, sizeof(XmlBlasterZlibWriteBuffers));

   zlibWriteBufP->debug = false;

   zlibWriteBufP->c_stream.zalloc = (alloc_func)0;
   zlibWriteBufP->c_stream.zfree = (free_func)0;
   zlibWriteBufP->c_stream.opaque = (voidpf)0;
   zlibWriteBufP->c_stream.data_type = Z_BINARY;

   err = deflateInit(&zlibWriteBufP->c_stream, Z_DEFAULT_COMPRESSION);
   if (err != Z_OK) {
      fprintf(stderr, "[%s:%d] deflateInit error: %s\n", __FILE__, __LINE__, zError(err));
   }
   return err;
}


/**
 * @see xmlBlasterZlib.h
 */
ssize_t xmlBlaster_writenCompressed(XmlBlasterZlibWriteBuffers *zlibWriteBufP, const int fd, const char * const ptr, const size_t nbytes)
{
   size_t written = 0; /* The written bytes of the compressed stream */
   if (!zlibWriteBufP) {
      fprintf(stderr, "[%s:%d] Internal error: XmlBlasterZlibWriteBuffers is NULL\n", __FILE__, __LINE__);
      return -1;
   }

   {
      z_stream *zlibP = &zlibWriteBufP->c_stream;
      bool onceMore = false;

      /* Initialize zlib buffer pointers */
      zlibP->next_in  = (Bytef*)ptr;
      zlibP->avail_in = (uInt)nbytes;
      zlibP->next_out = zlibWriteBufP->compBuffer;
      zlibP->avail_out = XMLBLASTER_ZLIB_WRITE_COMPBUFFER_LEN;

      if (zlibWriteBufP->debug) dumpZlib("writen(): Before while", 0, zlibWriteBufP);

      /* Compress and write to socket */

      while (zlibP->avail_in > 0 || onceMore) {
         int status = deflate(zlibP, Z_SYNC_FLUSH);
         if (zlibWriteBufP->debug) dumpZlib("writen(): In while after compress", 0, zlibWriteBufP);
         if (status != Z_OK) {
            fprintf(stderr, "[%s:%d] deflate error during sending of %u bytes: %s\n", __FILE__, __LINE__, (unsigned int)nbytes, zError(status));
            return -1;
         }
         onceMore = zlibP->avail_out == 0; /* && Z_OK which is checked above already */
         if ((XMLBLASTER_ZLIB_WRITE_COMPBUFFER_LEN - zlibP->avail_out) > 0) {
         /*if (zlibP->avail_out <= 6) { */ /* with Z_SYNC_FLUSH we should not go down to zero */
            ssize_t ret = writen(fd, (char *)zlibWriteBufP->compBuffer, XMLBLASTER_ZLIB_WRITE_COMPBUFFER_LEN-zlibP->avail_out);
            if (ret == -1) return -1;
            written += ret;
            zlibP->next_out = zlibWriteBufP->compBuffer;
            zlibP->avail_out = XMLBLASTER_ZLIB_WRITE_COMPBUFFER_LEN;
         }
      }
      if (zlibWriteBufP->debug) dumpZlib("writen(): After compress", 0, zlibWriteBufP);
      /*
      if ((XMLBLASTER_ZLIB_WRITE_COMPBUFFER_LEN - zlibP->avail_out) > 0) {
         ret = writen(fd, (char *)zlibWriteBufP->compBuffer, XMLBLASTER_ZLIB_WRITE_COMPBUFFER_LEN-zlibP->avail_out);
         if (ret == -1) return -1;
         written += ret;
      }
      */
      if (zlibWriteBufP->debug) printf("deflate - compressed %u bytes to %u\n", (unsigned int)nbytes, (unsigned int)written);

      return nbytes; /*written*/
   }
}


/**
 * @see xmlBlasterZlib.h
 */
int xmlBlaster_endZlibWriter(XmlBlasterZlibWriteBuffers *zlibWriteBufP) {
   int err;
   if (!zlibWriteBufP) return Z_BUF_ERROR;
   if (zlibWriteBufP->debug) dumpZlib("writen(): After compress", 0, zlibWriteBufP);
   err = deflateEnd(&zlibWriteBufP->c_stream);
   if (err != Z_OK) {
      /* TODO: Why does it return "-3 == data error"? Seems to be in BUSY_STATE */
      /*fprintf(stderr, "[%s:%d] deflateEnd error: %s\n", __FILE__, __LINE__, zError(err));*/
      return -1;
   }
   return err;
}


/**
 * @see xmlBlasterZlib.h
 */
int xmlBlaster_initZlibReader(XmlBlasterZlibReadBuffers *zlibReadBufP) {
   int err;

   if (!zlibReadBufP) return Z_BUF_ERROR;
   memset(zlibReadBufP, 0, sizeof(XmlBlasterZlibReadBuffers));

   zlibReadBufP->debug = false;

   zlibReadBufP->c_stream.zalloc = (alloc_func)0;
   zlibReadBufP->c_stream.zfree = (free_func)0;
   zlibReadBufP->c_stream.opaque = (voidpf)0;

   err = inflateInit(&zlibReadBufP->c_stream);
   if (err != Z_OK) {
      fprintf(stderr, "[%s:%d] inflateInit error: %s\n", __FILE__, __LINE__, zError(err));
   }

   zlibReadBufP->currCompBufferP = zlibReadBufP->compBuffer;
   zlibReadBufP->currCompBytes = 0;

   return err;
}


/**
 * @see xmlBlasterZlib.h
 */
ssize_t xmlBlaster_readnCompressed(XmlBlasterZlibReadBuffers *zlibReadBufP, int fd, char *ptr, size_t nbytes, XmlBlasterNumReadFunc fpNumRead, void *userP2)
{
   uInt readBytes = 0;     /* The read, uncompressed bytes */

   if (!zlibReadBufP) {
      fprintf(stderr, "[%s:%d] Internal error: XmlBlasterZlibReadBuffers is NULL\n", __FILE__, __LINE__);
      return -1;
   }

   {
      z_stream *zlibP = &zlibReadBufP->c_stream;

      if (zlibReadBufP->debug) printf("[%s:%d] Entering readnCompressed ...\n", __FILE__, __LINE__);

      /* Initialize zlib buffer pointers */
      zlibP->next_out = (Bytef*)ptr;
      zlibP->avail_out = (uInt)nbytes;

      if (zlibReadBufP->currCompBytes == 0)
         zlibReadBufP->currCompBufferP = zlibReadBufP->compBuffer;
      zlibP->next_in  = zlibReadBufP->currCompBufferP;
      zlibP->avail_in = zlibReadBufP->currCompBytes;

      if (zlibReadBufP->debug) dumpZlib("readn(): Before do", zlibReadBufP, 0);

      /* Read from socket and uncompress */
      do {
         if (fpNumRead != 0) {
            fpNumRead(userP2, (size_t)zlibP->next_out-(size_t)ptr, nbytes); /* Callback with current status */
         }
         if (zlibP->avail_out == 0) {
            if (zlibReadBufP->debug) printf("[%s:%d] readCompress() we are done with nbytes=%u currCompBytes=%u\n", __FILE__, __LINE__, (unsigned int)nbytes, zlibReadBufP->currCompBytes);
            if (fpNumRead != 0) {
               fpNumRead(userP2, nbytes, nbytes);
            }
            return nbytes;
         }

         if (zlibReadBufP->currCompBytes == 0 && readBytes != nbytes) {
            const int flag = 0;
            ssize_t nCompRead;
            if (zlibReadBufP->debug) printf("[%s:%d] recv() readBytes=%u, nbytes=%u, currCompBytes=%u\n", __FILE__, __LINE__, readBytes, (unsigned int)nbytes, zlibReadBufP->currCompBytes);
            zlibReadBufP->currCompBufferP = zlibReadBufP->compBuffer;
			/* currCompBufferP is of type "Bytef *" which is a "unsigned char *" */
#ifdef __IPhoneOS__
			// if(CFReadStreamHasBytesAvailable (		globalIPhoneXb->readStream	 ))
			 nCompRead =  CFReadStreamRead (
										globalIPhoneXb->readStream,
										 (UInt8*)zlibReadBufP->currCompBufferP,
										(CFIndex) 5
										);
			
#else
			 nCompRead = recv(fd, (char*)zlibReadBufP->currCompBufferP, (int)XMLBLASTER_ZLIB_READ_COMPBUFFER_LEN, flag); /* TODO: do we need at least two bytes?? */
#endif
			 if (nCompRead == -1 || nCompRead == 0) { /* 0 is not possible as we are blocking */
               if (zlibReadBufP->debug) printf("[%s:%d] EOF during reading of %u bytes\n", __FILE__, __LINE__, (unsigned int)(nbytes-readBytes));
               return -1;
            }
            zlibReadBufP->currCompBytes += (uInt)nCompRead;
            zlibP->next_in  = zlibReadBufP->currCompBufferP;
            zlibP->avail_in = zlibReadBufP->currCompBytes;
            if (zlibReadBufP->debug) dumpZlib("readn(): recv() returned", zlibReadBufP, 0);
         }

         while (zlibP->avail_in > 0 && zlibP->avail_out > 0) {
            int status = inflate(zlibP, Z_SYNC_FLUSH);
            if (status != Z_OK) {
               fprintf(stderr, "[%s:%d] inflate error during reading of %u bytes: %s\n", __FILE__, __LINE__, (unsigned int)nbytes, zError(status));
               return -1;
            }
            zlibReadBufP->currCompBufferP = zlibP->next_in;
            zlibReadBufP->currCompBytes = zlibP->avail_in;
            if (zlibReadBufP->debug) dumpZlib("readn(): inflate() returned", zlibReadBufP, 0);
            if (zlibP->avail_out == 0) {
               if (zlibReadBufP->debug) printf("[%s:%d] readCompress() we are done with nbytes=%u\n", __FILE__, __LINE__, (unsigned int)nbytes);
               if (fpNumRead != 0) {
                  fpNumRead(userP2, nbytes, nbytes);
               }
               return nbytes;
            }
         }
      } while(true);

      /* check if bytes are available
            int hasMoreBytes;
            fd_set fds;
            FD_ZERO(&fds);
            FD_SET(fd, &fds);
            hasMoreBytes = select (fd+1, &fds, NULL, NULL, NULL);
      */
   }
}


/**
 * @see xmlBlasterZlib.h
 */
int xmlBlaster_endZlibReader(XmlBlasterZlibReadBuffers *zlibReadBufP) {
   int err;
   if (!zlibReadBufP) return Z_BUF_ERROR;
   err = inflateEnd(&zlibReadBufP->c_stream);
   if (err != Z_OK) {
      fprintf(stderr, "[%s:%d] inflateEnd error: %s\n", __FILE__, __LINE__, zError(err));
      return -1;
   }
   return err;
}

#else

/* If no zlib is available use dummies: */
int xmlBlaster_initZlibWriter(XmlBlasterZlibWriteBuffers *zlibWriteBufP) { 
   fprintf(stderr, "No support for zlib is compiled, try with -DXMLBLASTER_ZLIB=1\n");
   assert(0);
   zlibWriteBufP = 0;/* to supress compiler warning */
   return 0;
}
ssize_t xmlBlaster_writenCompressed(XmlBlasterZlibWriteBuffers *zlibWriteBufP, const int fd, const char * const ptr, const size_t nbytes) { 
   if (fd && ptr && nbytes) zlibWriteBufP = 0;/* to supress compiler warning */
   return 0;
}
int xmlBlaster_endZlibWriter(XmlBlasterZlibWriteBuffers *zlibWriteBufP) { zlibWriteBufP = 0;/* to supress compiler warning */ return -1; }
int xmlBlaster_initZlibReader(XmlBlasterZlibReadBuffers *zlibReadBufP) { zlibReadBufP = 0;/* to supress compiler warning */ return -1; }
ssize_t xmlBlaster_readnCompressed(XmlBlasterZlibReadBuffers *zlibReadBufP, int fd, char *ptr, size_t nbytes, XmlBlasterNumReadFunc fpNumRead, void *userP2) {
	if (fd && ptr && nbytes && fpNumRead && userP2) zlibReadBufP = 0;/* to supress compiler warning */
	return 0;
}
int xmlBlaster_endZlibReader(XmlBlasterZlibReadBuffers *zlibReadBufP) { zlibReadBufP = 0;/* to supress compiler warning */ return -1; }

# endif /* XMLBLASTER_ZLIB */


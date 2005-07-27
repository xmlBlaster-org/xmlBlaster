/*----------------------------------------------------------------------------
Name:      xmlBlasterZlib.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   SOCKET internal header (not included directly by clients)
Author:    "Marcel Ruff" <xmlBlaster@marcelruff.info>
See:       http://www.gzip.org/zlib/
-----------------------------------------------------------------------------*/
#ifndef XMLBLASTER_ZLIB_H
#define XMLBLASTER_ZLIB_H

#ifdef __cplusplus
#ifndef XMLBLASTER_C_COMPILE_AS_CPP /* 'g++ -DXMLBLASTER_C_COMPILE_AS_CPP ...' allows to compile the lib as C++ code */
extern "C" {
#endif
#endif

#include <util/basicDefs.h>
#if XMLBLASTER_ZLIB==1
#  include <zlib.h>
#endif

#define XMLBLASTER_ZLIB_WRITE_COMPBUFFER_LEN 20000

/**
 * Helper struct to compress a byte buffer before putting it into the socket. 
 *
 * We need exactly one instance of this for each socket
 * and the access must be synchronized to be thread safe.
 *
 * Different instances of this struct are thread safe.
 * @see http://www.gzip.org/zlib/
 */
typedef struct XmlBlasterZlibWriteBuffers {
   int debug;             /**< 0: no debugging, 1: switch on debugging */
#if XMLBLASTER_ZLIB==1
   z_stream c_stream;     /**< zlib compression stream structure */
   Bytef compBuffer[XMLBLASTER_ZLIB_WRITE_COMPBUFFER_LEN]; /**< buffer to hold temporary the outgoing compressed bytes */
#endif
} XmlBlasterZlibWriteBuffers;


#define XMLBLASTER_ZLIB_READ_COMPBUFFER_LEN 20000

/**
 * Helper struct to uncompress a byte buffer coming from the socket connection. 
 *
 * We need exactly one instance of this for each socket
 * and the access must be synchronized to be thread safe.
 *
 * Different instances of this struct are thread safe.
 */
typedef struct XmlBlasterZlibReadBuffers {
   int debug;             /**< 0: no debugging, 1: switch on debugging */
#if XMLBLASTER_ZLIB==1
   z_stream c_stream;     /**< zlib compression stream structure */
   Bytef compBuffer[XMLBLASTER_ZLIB_READ_COMPBUFFER_LEN]; /**< buffer to hold temporary the incoming compressed bytes */
   Bytef *currCompBufferP; /**< Pointer into compBuffer, points on current start position */
   uInt currCompBytes;     /**< Number of compressed bytes in compBuffer, starting at currCompBufferP */
#endif
} XmlBlasterZlibReadBuffers;


/**
 * Call only once for a socket connection stream. 
 * @return Z_OK==0 or error from deflateInit()
 */
/*Dll_Export*/extern int xmlBlaster_initZlibWriter(XmlBlasterZlibWriteBuffers *zlibWriteBufP);

/**
 * Compress given bytes with zlib and write them to the socket
 * @param fd The socket descriptor
 * @param ptr The buffer with raw bytes
 * @param nbytes The number of bytes in 'ptr'
 */
extern ssize_t xmlBlaster_writenCompressed(XmlBlasterZlibWriteBuffers *zlibWriteBufP, const int fd, const char * const ptr, size_t const nbytes);

/**
 * @see xmlBlasterZlib.h
 */
extern int xmlBlaster_endZlibWriter(XmlBlasterZlibWriteBuffers *zlibWriteBufP);

/**
 * Call only once for a socket connection stream. 
 * @return Z_OK==0 or error from inflateInit()
 */
extern int xmlBlaster_initZlibReader(XmlBlasterZlibReadBuffers *zlibReadBufP);

/**
 * Read compressed data from the socket and uncompress it. 
 * @param zlibReadBufP Struct holding necessary variables to use zlib
 * @param fd The socket descriptor
 * @param ptr The empty buffer which gets filled with raw bytes from socket (out parameter)
 * @param nbytes The max. size of 'ptr'
 * @return number of bytes read, -1 is EOF
 */
extern ssize_t xmlBlaster_readnCompressed(XmlBlasterZlibReadBuffers *zlibReadBufP, int fd, char *ptr, size_t nbytes, XmlBlasterNumReadFunc fpNumRead, void *userP2);

/**
 * Cleanup after socket is closed. 
 * @param zlibReadBufP Struct holding necessary variables to use zlib
 */
extern int xmlBlaster_endZlibReader(XmlBlasterZlibReadBuffers *zlibReadBufP);

#ifdef __cplusplus
#ifndef XMLBLASTER_C_COMPILE_AS_CPP
} /* extern "C" */
#endif
#endif

#endif /* XMLBLASTER_ZLIB_H */


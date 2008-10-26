/*
 * XmlUtil.h
 *
 *  Created on: 2008-10-26
 *      Author: mr@marcelruff.info
 */

#ifndef XMLUTIL_H_
#define XMLUTIL_H_

#include <util/basicDefs.h>

#ifdef __cplusplus
#ifndef XMLBLASTER_C_COMPILE_AS_CPP /* 'g++ -DXMLBLASTER_C_COMPILE_AS_CPP ...' allows to compile the lib as C++ code */
extern "C" {
#endif
#endif

/**
 * Unescape e.g. "&lt;" to "<"
 * @param xml The char * must be writeable, it can NOT be on the stack like 'char *xml="HELLO";'
 * @param newLen out parameter If the unescaped char * containes '\0' (is binary) you need newLen
 * @return the modified xml (same pointer as given xml, nothing is allocated)
 */
Dll_Export extern char *xmlBlasterUnEscapeXml(char * const xml, int *newLen);

/**
 * @param len The length of xml
 * @param bytes The binary data, does not need to be '\0' terminated
 * @return Is zero terminated, must be freed with xmlBlasterFree(p)
 */
Dll_Export extern char* xmlBlasterEscapeXmlBytes(int len, const char *bytes);
Dll_Export extern char* xmlBlasterEscapeXml(const char *xml);

/**
 * @return must be freed with xmlBlasterFree(p)
 */
Dll_Export extern char* xmlBlasterEscapeCSV(const char *csv);

/**
 * Unescape "&comma;" to ","
 * @param csv The char * must be writeable, it can NOT be on the stack like 'char *xml="HELLO";'
 * @param newLen out parameter If the unescaped char * containes '\0' (is binary) you need newLen
 * @return the modified csv (same pointer as given csv, nothing is allocated)
 */
Dll_Export extern char *xmlBlasterUnEscapeCSV(char * const csv, int *newLen);


/**
 * Find the given attribute from the given tag from the given xml string and return its value.
 * @param xml The xml string to parse
 * @param tag For example "node" for a tag &lt;node id='heron'>
 * @param attributeName "id"
 * @return 'heron' null if none is found, you need to free it
 * with xmlBlasterFree(p);
 */
Dll_Export extern char *xmlBlasterExtractAttributeValue(const char * const xml,
		const char * const tag, const char * const attributeName);
Dll_Export extern long xmlBlasterExtractAttributeLong(const char * const xml,
		const char * const tag, const char * const attributeName, long defaultValue);


/**
 * Find the given attribute from the given tag from the given xml string and return
 * the <b>tags value</b>.
 * @param xml The xml string to parse
 * @param tag For example "node" for a tag &lt;node id='heron'>Good day&lt;/node>
 * @param attributeName "id"
 * @param attributeValue Can be 0
 * @return 'Good day' null if none is found, you need to free it
 */
Dll_Export extern char *xmlBlasterExtractTagValueWithAttribute(const char * const xml,
		const char * const tag, const char * const attributeName,
		const char * const attributeValue);
/**
 * Find the first given tag and return its value.
 * @param xml The xml string to parse
 * @param tag For example "node" for a tag &lt;node id='heron'>Good day&lt;/node>
 * @return 'Good day' null if none is found, you need to free it
 */
Dll_Export extern char *xmlBlasterExtractTagValue(const char * const xml,
		const char * const tag);

Dll_Export extern char *xmlBlasterReadBinaryFile(const char *name, int *len);


#ifdef __cplusplus
#ifndef XMLBLASTER_C_COMPILE_AS_CPP
}
#endif
#endif

#endif /* XMLUTIL_H_ */

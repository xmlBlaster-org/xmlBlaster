#------------------------------------------------------------------------------
#Name:      Makefile
#Project:   xmlBlaster.org
#Comment:   Makefile for sub directories
#------------------------------------------------------------------------------

include $(XMLBLASTER_HOME)/Makefile.Include

SUB_DIRS = src demo testsuite

all: all_SUB_DIRS

clean: clean_SUB_DIRS

distclean: clean
	$(RM_RECURSIVE) $(JAVADOC_DIR)
	$(RM) $(JAR_DIR)/xmlBlaster.jar

#			org.xmlBlaster.engine.xmldb \
#			org.xmlBlaster.engine.xmldb.cache \

PACKGS = org.xmlBlaster \
			org.xmlBlaster.engine \
			org.xmlBlaster.engine.xml2java \
			org.xmlBlaster.engine.xmldb.file \
			org.xmlBlaster.engine.persistence \
			org.xmlBlaster.authentication \
			org.xmlBlaster.client \
			org.xmlBlaster.client.feeder \
			org.xmlBlaster.client.reader \
			org.xmlBlaster.protocol \
			org.xmlBlaster.protocol.corba.clientIdl \
			org.xmlBlaster.protocol.corba.authenticateIdl \
			org.xmlBlaster.protocol.corba.serverIdl \
			org.xmlBlaster.protocol.email \
			org.xmlBlaster.protocol.http \
			org.xmlBlaster.protocol.jdbc \
			org.xmlBlaster.util \
			org.xmlBlaster.util.pool.jdbc

JUTILS_PACKGS = org.jutils \
			org.jutils.collection \
			org.jutils.init \
			org.jutils.io \
			org.jutils.log \
			org.jutils.pool \
			org.jutils.runtime \
			org.jutils.text \
			org.jutils.time



TEST_PACKGS = testsuite.org.xmlBlaster

DEMO_PACKGS = javaclients html.systemInfo

DOC_PACKGS = doc.requirements

javadoc_server:
	${MKDIR_RECURSIVE} ${JAVADOC_DIR} 
	${JAVADOC} -d ${JAVADOC_DIR} -sourcepath ./src/java $(PACKGS) 
 

javadoc_testsuite:
	${MKDIR_RECURSIVE} ${JAVADOC_DIR} 
	${JAVADOC} -d ${JAVADOC_DIR} -sourcepath . $(TEST_PACKGS) 
 

javadoc_demo:
	${MKDIR_RECURSIVE} ${JAVADOC_DIR} 
	${JAVADOC} -d ${JAVADOC_DIR} -sourcepath demo $(DEMO_PACKGS) 

doc: javadoc 

javadoc:
	${MKDIR_RECURSIVE} ${JAVADOC_DIR} 
	${JAVADOC} -d ${JAVADOC_DIR} -sourcepath ./src/java:./:./demo $(PACKGS) $(JUTILS_PACKGS) $(TEST_PACKGS) $(DEMO_PACKGS) $(DOC_PACKGS)
	# making requirements.html from xml requirements:
	( cd ${SRC_DOCS_DIR}; make doc)
	( cd ${SRC_TESTSUITE_TOP_DIR}/org/xmlBlaster; make html)

jar: all
	( cd ${CLASS_TOP_DIR}; jar cvf ${JAR_DIR}/xmlBlaster.jar ./org ./testsuite ./javaclients)


test: all
	( cd ${XMLBLASTER_HOME}/testsuite/org/xmlBlaster; make test )

test-console: all
	( cd ${XMLBLASTER_HOME}/testsuite/org/xmlBlaster; make test-console )

install: all
	( tar cvzf xmlBlasterInstall.tar.gz bin config lib )






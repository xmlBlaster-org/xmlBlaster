#------------------------------------------------------------------------------
#Name:      Makefile
#Project:   xmlBlaster.org
#Comment:   Makefile for sub directories
#------------------------------------------------------------------------------

include $(XMLBLASTER_HOME)/Makefile.Include

SUB_DIRS = src demo testsuite

all: all_SUB_DIRS jar

clean: clean_SUB_DIRS

distclean: clean
	$(RM_RECURSIVE) $(JAVADOC_DIR)
	$(RM) $(JAR_DIR)/xmlBlaster.jar

PACKGS = org.xmlBlaster \
			org.xmlBlaster.engine \
			org.xmlBlaster.engine.xml2java \
			org.xmlBlaster.engine.persistence \
			org.xmlBlaster.engine.persistence.filestore \
			org.xmlBlaster.engine.persistence.mudb \
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
			org.xmlBlaster.protocol.rmi \
			org.xmlBlaster.protocol.xmlrpc \
			org.xmlBlaster.protocol.nativ \
			org.xmlBlaster.util

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

DEMO_PACKGS = javaclients javaclients.corba javaclients.rmi javaclients.jdbc javaclients.chat javaclients.HelloWorldApplet html.systemInfo

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

# Indexing of jar file only since JDK 1.3:  jar -i  ${JAR_DIR}/xmlBlaster.jar
jar:
	(	\
	   cd ${CLASS_TOP_DIR}; \
		jar cmf manifest.xmlBlaster ${JAR_DIR}/xmlBlaster.jar ./org ./testsuite ./javaclients xmlBlaster.policy; \
	)


test: all
	( cd ${XMLBLASTER_HOME}/testsuite/org/xmlBlaster; make test )

test-console: all
	( cd ${XMLBLASTER_HOME}/testsuite/org/xmlBlaster; make test-console )

install: all
	( tar cvzf xmlBlasterInstall.tar.gz bin config lib )






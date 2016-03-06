JAVA_FILES=nl/tjeb/XTST/*.java
LIBS=lib/saxon9he.jar:lib/argparse4j.jar
INSTALLDIR=~/opt/XTST

all: build jar

build: env ${JAVA_FILES}
	javac -d build -cp ${LIBS} ${JAVA_FILES}

env:
	@if test ! -d build; then mkdir build ; mkdir build/lib ; fi

install: install-main install-libs

install-libs:
	@if test ! -d ${INSTALLDIR}/lib; then mkdir -p ${INSTALLDIR}/lib ; fi
	@if test ! -f ${INSTALLDIR}/lib/saxon9he.jar; then\
		cp lib/saxon9he.jar ${INSTALLDIR}/lib/; \
	fi
	@if test ! -f ${INSTALLDIR}/lib/argparse4j.jar; then\
		cp lib/argparse4j.jar ${INSTALLDIR}/lib/; \
	fi

install-main:
	@if test ! -d ${INSTALLDIR}; then mkdir -p ${INSTALLDIR} ; fi
	cp XTST.jar ${INSTALLDIR}/
	cp xslt_transformer_service.sh ${INSTALLDIR}/
	cp send_document.py ${INSTALLDIR}/
	@if test ! -f ${INSTALLDIR}/transform.xsl; then\
		cp example.xsl ${INSTALLDIR}/transform.xsl ; \
	fi

clean:
	rm -rf build

jar:
	(cd build; jar cvmf ../manifest.txt ../XTST.jar *)

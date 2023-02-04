JAVA_HOME ?= ~/local/jdk-classfile
BOOTSTRAP_TCLJ_MDIR ?= ../jvm-stuff/bootstrap-tclj

JAVA_BIN=$(if $(JAVA_HOME),$(JAVA_HOME)/bin/,)
JAVA=$(JAVA_BIN)java
JAVAC=$(JAVA_BIN)javac
JAVAP=$(JAVA_BIN)javap

JAVA_OPTS=--enable-preview --add-modules jdk.incubator.concurrent \
  --add-exports java.base/jdk.internal.classfile=ALL-UNNAMED \
  --add-exports java.base/jdk.internal.classfile.constantpool=ALL-UNNAMED \
  --add-exports java.base/jdk.internal.classfile.instruction=ALL-UNNAMED \
  --add-exports java.base/jdk.internal.classfile.attribute=ALL-UNNAMED
#JAVA_OPTS += -XX:+UseZGC -Xlog:gc
#JAVA_OPTS += -Djdk.tracePinnedThreads

#DET=--deterministic
BOOTSTRAP_TCLJ_MAIN=$(if $(findstring /bootstrap-tcljc,$(BOOTSTRAP_TCLJ_MDIR)),tcljc.main.___,tinyclj.build.main.___)
BOOTSTRAP_TCLJ_SOURCE_CORE=$(if $(findstring /bootstrap-tcljc,$(BOOTSTRAP_TCLJ_MDIR)),,../jvm-stuff/tclj-in-tclj/)src/tinyclj.core
BOOTSTRAP_TCLJ_MOD_RT=$(BOOTSTRAP_TCLJ_MDIR)/tinyclj.rt
BOOTSTRAP_TCLJ=$(JAVA) --class-path $(BOOTSTRAP_TCLJ_MOD_RT):$(BOOTSTRAP_TCLJ_MDIR)/tinyclj.core:$(BOOTSTRAP_TCLJ_MDIR)/tinyclj.compiler $(JAVA_OPTS) $(BOOTSTRAP_TCLJ_MAIN) $(DET) --parent-loader :platform

# $(DEST_DIR) matches the compiler's default destination directory
PROJECT_DIR ?= $(notdir $(PWD))
DEST_DIR=/tmp/$(USER)/tinyclj/$(PROJECT_DIR)
TCLJC_MOD_RT=$(DEST_DIR).mdir/tinyclj-rt.jar

SOURCE_OPTS=-s $(BOOTSTRAP_TCLJ_MOD_RT) -s  $(BOOTSTRAP_TCLJ_SOURCE_CORE) -s src/tinyclj.compiler -s test/tinyclj.core -s test/tinyclj.compiler
RUN_TESTS_NS=tcljc.run-tests

compile: $(TCLJC_MOD_RT)
	$(BOOTSTRAP_TCLJ) -d $(DEST_DIR) $(SOURCE_OPTS) $(RUN_TESTS_NS)
watch-and-compile: $(TCLJC_MOD_RT)
	$(BOOTSTRAP_TCLJ) --watch -d $(DEST_DIR) $(SOURCE_OPTS) $(RUN_TESTS_NS)

# Call with "make test TEST=<scope>" (with <scope> being "ns-name" or
# "ns-name/var-name") to only run tests from the given namespace or
# var.  Only call this after compile, possibly while one of the
# watch-and-xxx targets is running.
test: $(TCLJC_MOD_RT)
	$(JAVA) $(JAVA_OPTS) -cp $(BOOTSTRAP_TCLJ_MOD_RT):$(DEST_DIR) $(RUN_TESTS_NS).___
watch-and-test: $(TCLJC_MOD_RT)
	$(BOOTSTRAP_TCLJ) --watch $(SOURCE_OPTS) $(RUN_TESTS_NS)/run

test-tcljc: $(TCLJC_MOD_RT)
	$(BOOTSTRAP_TCLJ) -d $(DEST_DIR).compile-tcljc-stage0 $(SOURCE_OPTS) tcljc.compile-tcljc/run

clean:
	rm -rf "$(DEST_DIR)"/* "$(DEST_DIR)"*.* *.class hs_err_pid*.log replay_pid*.log

print-line-count:
	find src/tinyclj.compiler/tcljc -name "*.cljt" | xargs wc -l | sort -n

.PHONY: compile watch-and-compile test watch-and-test clean


print-javap:
	rm -f *.class
	$(JAVAC) $(JAVA_OPTS) -source 20 Hello.java
	$(JAVAP) -v -p *.class

TCLJ=$(JAVA) --module-path $(BOOTSTRAP_TCLJ_MDIR) $(JAVA_OPTS) -m tinyclj.compiler $(DET)
threadlog:
	$(TCLJ) -d :none -s test/tinyclj.compiler tcljc.threadlog/-main

########################################################################

TIME_JAVA=time -p $(JAVA)
TCLJC_MAIN_NS=tcljc.main

bootstrap-fixpoint: $(DEST_DIR).stageDI2/DONE
bootstrap-mdir: $(DEST_DIR).mdir/DONE
bootstrap-check: $(DEST_DIR).rtiowFS/DONE

bootstrap-check-with-tclj:
	$(MAKE) bootstrap-check PROJECT_DIR=tcljc-with-tclj BOOTSTRAP_TCLJ_MDIR=../jvm-stuff/bootstrap-tclj
bootstrap-check-with-tcljc:
	$(MAKE) bootstrap-check PROJECT_DIR=tcljc-with-tcljc BOOTSTRAP_TCLJ_MDIR=../bootstrap-tcljc
bootstrap-check-with-all: \
	bootstrap-check-with-tclj \
	bootstrap-check-with-tcljc

# Naming:
# Dx -- deterministic classfile output but slow compilation
# Fx -- fast compilation but non-deterministic classfile output
# xI -- isolated runtime, i.e. with "--parent-loader :platform"
# xS -- shared runtime, i.e. with default "--parent-loader :system"

# Zero: Build "tcljc" using the bootstrap compiler "tclj-in-tclj".
# Because there is no name collision between the compilers' namespaces
# currently, "tcljc" can be compiled like a regular application using
# the shared runtime setup.  Once tcljc becomes its own bootstrap
# compiler, this must be changed to the isolated runtime setup.
$(DEST_DIR).stageZero/DONE: $(wildcard $(BOOTSTRAP_TCLJ_MDIR)/commit-id.txt src/tinyclj.compiler/tcljc/*.cljt src/tinyclj.compiler/tcljc/*/*.cljt)
	@echo; echo "### $(dir $@)"
	@rm -rf "$(dir $@)"
	$(BOOTSTRAP_TCLJ) -d "$(dir $@)" -s $(BOOTSTRAP_TCLJ_MOD_RT) -s $(BOOTSTRAP_TCLJ_SOURCE_CORE) -s src/tinyclj.compiler $(TCLJC_MAIN_NS)
	touch "$@"

# DI1: Build "tcljc" using the initial compiler from PREV_DEST_DIR
# (aka the first prerequisite's $< directory).  The initial compiler
# needs BOOTSTRAP_TCLJ_MDIR's tinyclj-rt jar to run, but the compiled
# application makes use of TCLJC_MOD_RT.
$(DEST_DIR).stageDI1/DONE: $(DEST_DIR).stageZero/DONE $(TCLJC_MOD_RT)
	@echo; echo "### $(dir $@)"
	@rm -rf "$(dir $@)"
	$(TIME_JAVA) -cp $(BOOTSTRAP_TCLJ_MDIR)/tinyclj.rt:$(dir $<) $(JAVA_OPTS) $(TCLJC_MAIN_NS).___ --deterministic --parent-loader :platform -d "$(dir $@)" -s $(TCLJC_MOD_RT) -s src/tinyclj.core -s src/tinyclj.compiler $(TCLJC_MAIN_NS)
	touch "$@"

# DI2: Build "tcljc" using the DI1 compiler from PREV_DEST_DIR (aka
# the first prerequisite's $< directory).
$(DEST_DIR).stageDI2/DONE: $(DEST_DIR).stageDI1/DONE
	@echo; echo "### $(dir $@)"
	@rm -rf "$(dir $@)"
	$(TIME_JAVA) -cp $(TCLJC_MOD_RT):$(dir $<) $(JAVA_OPTS) $(TCLJC_MAIN_NS).___ --deterministic --parent-loader :platform -d "$(dir $@)" -s $(TCLJC_MOD_RT) -s src/tinyclj.core -s src/tinyclj.compiler $(TCLJC_MAIN_NS)
	touch "$@"
	diff -Nrq "$(dir $<)" "$(dir $@)"

# ------------------------------------------------------------------------
# Use bootstrapped compiler to build modules for runtime, core
# library, and compiler.  Collect the jar files into $(DEST_DIR).mdir.

BUILD_JAVAC=$(JAVAC)
#BUILD_JAVAC=$(JAVAC) --release 17
BUILD_JAVA=$(JAVA)
BUILD_JAR=$(JAVA_BIN)jar

TINYCLJ_RT_SOURCE := $(sort $(wildcard src/tinyclj.rt/*/lang/*.java)) src/tinyclj.rt/module-info.java
$(DEST_DIR).mod-tinyclj-rt/module-info.class: $(TINYCLJ_RT_SOURCE)
	@echo; echo "### $(dir $@)"
	@rm -rf "$(dir $@)"
	mkdir -p --mode 700 "/tmp/$(USER)" "$(dir $@)"
	$(BUILD_JAVAC) -d "$(dir $@)" $^

TINYCLJ_CORE_SOURCE := $(sort $(wildcard src/tinyclj.core/*/*.cljt src/tinyclj.core/*/*/*.cljt)) src/tinyclj.core/module-info.java
$(DEST_DIR).mod-tinyclj-core/module-info.class: $(DEST_DIR).mod-tinyclj-rt/module-info.class $(TINYCLJ_CORE_SOURCE) $(DEST_DIR).stageDI2/DONE
	@echo; echo "### $(dir $@)"
	@rm -rf "$(dir $@)"
	$(BUILD_JAVA) -cp $(TCLJC_MOD_RT):$(DEST_DIR).stageDI2 $(JAVA_OPTS) $(TCLJC_MAIN_NS).___ --deterministic -d "$(dir $@)" --parent-loader :platform -s $(dir $<) -s src/tinyclj.core tinyclj.core.all
	$(BUILD_JAVAC) -p $(dir $<) -d "$(dir $@)" src/tinyclj.core/module-info.java

TINYCLJ_COMPILER_SOURCE := $(sort $(wildcard src/tinyclj.compiler/*/*.cljt src/tinyclj.compiler/*/*/*.cljt)) src/tinyclj.compiler/module-info.java
$(DEST_DIR).mod-tinyclj-compiler/module-info.class: $(DEST_DIR).mod-tinyclj-core/module-info.class $(TINYCLJ_COMPILER_SOURCE) $(DEST_DIR).stageDI2/DONE
	@echo; echo "### $(dir $@)"
	@rm -rf "$(dir $@)"
	$(BUILD_JAVA) -cp $(TCLJC_MOD_RT):$(DEST_DIR).stageDI2 $(JAVA_OPTS) $(TCLJC_MAIN_NS).___ --deterministic -d "$(dir $@)" --parent-loader :platform -s $(DEST_DIR).mod-tinyclj-rt -s $(dir $<) -s src/tinyclj.compiler $(TCLJC_MAIN_NS)
	$(BUILD_JAVAC) -p $(DEST_DIR).mod-tinyclj-rt:$(dir $<) -d "$(dir $@)" src/tinyclj.compiler/module-info.java

$(TCLJC_MOD_RT): $(DEST_DIR).mod-tinyclj-rt/module-info.class
	@echo; echo "### $@"
	@rm -rf "$(dir $@)"
	mkdir -p --mode 700 "$(dir $@)"
	$(BUILD_JAR) --create --file="$@" -C "$(dir $<)" .

$(DEST_DIR).mdir/DONE: $(TCLJC_MOD_RT) $(DEST_DIR).mod-tinyclj-core/module-info.class $(DEST_DIR).mod-tinyclj-compiler/module-info.class
	@echo; echo "### $(dir $@)"
	$(BUILD_JAR) --create --file="$(dir $@)"/tinyclj-core.jar -C $(DEST_DIR).mod-tinyclj-core .
	$(BUILD_JAR) --create --file="$(dir $@)"/tinyclj-compiler.jar --main-class=$(TCLJC_MAIN_NS).___ -C $(DEST_DIR).mod-tinyclj-compiler/ .
	touch "$@"

# ------------------------------------------------------------------------
# Use bootstrap compiler again, with module jars on classpath.
# Finally, compile and run an application using the jars as modules,
# i.e. from the module path.

DISTRIB_SOURCE_OPTS=-s $(TCLJC_MOD_RT) -s src/tinyclj.core -s src/tinyclj.compiler
BOOTSTRAP_DISTRIB_TCLJ=$(TIME_JAVA) $(JAVA_OPTS) -cp $(DEST_DIR).mdir/\* $(TCLJC_MAIN_NS).___

$(DEST_DIR).stageFI1/DONE: $(DEST_DIR).mdir/DONE
	@echo; echo "### $(dir $@)"
	@rm -rf "$(dir $@)"
	$(BOOTSTRAP_DISTRIB_TCLJ) -d "$(dir $@)" --parent-loader :platform $(DISTRIB_SOURCE_OPTS) $(TCLJC_MAIN_NS)
	touch "$@"

$(DEST_DIR).stageFI2/DONE: $(DEST_DIR).stageFI1/DONE
	@echo; echo "### $(dir $@)"
	@rm -rf "$(dir $@)"
	$(TIME_JAVA) -cp $(TCLJC_MOD_RT):$(dir $<) $(JAVA_OPTS) $(TCLJC_MAIN_NS).___ -d "$(dir $@)" --parent-loader :platform $(DISTRIB_SOURCE_OPTS) $(TCLJC_MAIN_NS)
	touch "$@"

$(DEST_DIR).rtiowFS/DONE: $(DEST_DIR).stageFI2/DONE
	@echo; echo "### $(dir $@)"
	@rm -rf "$(dir $@)"
	$(TIME_JAVA) --enable-preview --add-exports java.base/jdk.internal.classfile=tinyclj.compiler --add-exports java.base/jdk.internal.classfile.constantpool=tinyclj.compiler --add-exports java.base/jdk.internal.classfile.instruction=tinyclj.compiler --add-exports java.base/jdk.internal.classfile.attribute=tinyclj.compiler -p $(DEST_DIR).mdir -m tinyclj.compiler -d "$(dir $@)" -s test/tinyclj.compiler tcljc.rtiow-ref
	@echo "\nRun from class path:"
	$(JAVA) -cp $(DEST_DIR).mdir/\*:$(dir $@) tcljc.rtiow-ref.___ >"$(dir $@)"ray.ppm
	@echo "3cf6c9b9f93edb0de2bc24015c610d78  $(dir $@)ray.ppm" | md5sum -c -
	@echo "\nRun from module path:"
	$(JAVA) -p $(DEST_DIR).mdir --add-modules tinyclj.core -cp $(dir $@) tcljc.rtiow-ref.___ >"$(dir $@)"ray.ppm
	@echo "3cf6c9b9f93edb0de2bc24015c610d78  $(dir $@)ray.ppm" | md5sum -c -
	touch "$@"

# ------------------------------------------------------------------------

install-into-bootstrap-tcljc: $(DEST_DIR).mdir/DONE
	$(MAKE) -C ../bootstrap-tcljc pack JAR=$(BUILD_JAR)
	cp -f $(DEST_DIR).mdir/*.jar ../bootstrap-tcljc
	$(MAKE) -C ../bootstrap-tcljc unpack JAR=$(BUILD_JAR)

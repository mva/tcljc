module tcljc {
  requires transitive tinyclj.core;
  
  exports tcljc.main;
  
  exports tcljc.classfile;
  exports tcljc.compiler.branch;
  exports tcljc.compiler;
  exports tcljc.compiler.conv;
  exports tcljc.compiler.resolve;
  exports tcljc.compiler.sigclass;
  exports tcljc.compiler.sigfn;
  exports tcljc.config;
  exports tcljc.context;
  // exports tcljc.core;
  exports tcljc.emitter.beachhead;
  exports tcljc.emitter.bytecode;
  exports tcljc.emitter.classes;
  exports tcljc.emitter;
  exports tcljc.emitter.emitfn;
  exports tcljc.emitter.export;
  exports tcljc.emitter.exprcode;
  exports tcljc.emitter.namespace;
  exports tcljc.emitter.prepare;
  exports tcljc.expr;
  exports tcljc.exprfold;
  exports tcljc.grammar;
  exports tcljc.javabase;
  exports tcljc.macro;
  exports tcljc.main.options;
  exports tcljc.main.tclj0;
  exports tcljc.predefined;
  exports tcljc.publics;
  exports tcljc.reader;
  exports tcljc.runtime;
  exports tcljc.synquote;
  exports tcljc.util;
  exports tcljc.wrong;
}
package org.checkerframework.dataflow.cfg;

/*>>>
import org.checkerframework.checker.nullness.qual.Nullable;
*/

import org.checkerframework.javacutil.BasicTypeProcessor;
import org.checkerframework.javacutil.TreeUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;

import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.util.TreePathScanner;
import com.sun.tools.javac.file.JavacFileManager;
import com.sun.tools.javac.main.JavaCompiler;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.List;

/**
 * Class to generate the DOT representation of the control flow graph of a given
 * method.
 *
 * @author Stefan Heule
 */
// Typeprocessor: Javasource => CFG
// CFG => DOT, SVG, format

// Javasource2CFG
// CFGPrinter => DOT 
// CFGDOTPrinter

public class JavaSource2CFG {

    /**
     * @return the AST of a specific method in a specific class as well as the
     *         {@link CompilationUnitTree} in a specific file (or null they do
     *         not exist).
     */
    public static ControlFlowGraph generateMethodCFG(
            String file, String clas, final String method) {

        CFGProcessor cfgProcessor = new CFGProcessor(clas, method);

        Context context = new Context();

        JavaCompiler javac = new JavaCompiler(context);

        javac.attrParseOnly = true;
        JavacFileManager fileManager = (JavacFileManager) context
                .get(JavaFileManager.class);

        JavaFileObject l = fileManager
                .getJavaFileObjectsFromStrings(List.of(file)).iterator().next();

        PrintStream err = System.err;

        try {
            // redirect syserr to nothing (and prevent the compiler from issuing
            // warnings about our exception.
            System.setErr(new PrintStream(new OutputStream() {
                @Override
                public void write(int b) throws IOException {
                }
            }));
            javac.compile(List.of(l), List.of(clas), List.of(cfgProcessor));
        } catch (Throwable e) {
            // ok
        } finally {
            System.setErr(err);
        }

        CFGProcessResult res = cfgProcessor.getCFGProcessResult();

        if (res == null) {
            printError("internal error in type processor! method typeProcessOver() doesn't get called.");
            // TODO: directly exit is not friendly, refactor this to using throw-catch
            System.exit(1);
        }

        if (!res.isSuccess()) {
            printError(res.getErrMsg());
            // TODO: directly exit is not friendly, refactor this to using throw-catch
            System.exit(1);
        }

        return res.getCFG();
    }

    @SupportedAnnotationTypes("*")
    private static class CFGProcessor extends BasicTypeProcessor {

        private final String className;
        private final String methodName;

        private CompilationUnitTree rootTree;
        private ClassTree classTree;
        private MethodTree methodTree;

        private CFGProcessResult result;

        /**
         *TODO: make this a public seperate class, to allow clients only get the generated CFG
         * @param className
         * @param methodName
         * @param res
         */
        CFGProcessor(String className, String methodName) {
            this.className = className;
            this.methodName = methodName;
            this.result = null;
        }

        public final CFGProcessResult getCFGProcessResult () {
            return this.result;
        }

        @Override
        public void typeProcessingOver() {
            if (rootTree == null) {
                this.result = new CFGProcessResult(null, false, "root tree is null.");
                return;
            }

            if (classTree == null) {
                this.result = new CFGProcessResult(null, false, "method tree is null.");
                return;
            }

            if (methodTree == null) {
                this.result = new CFGProcessResult(null, false, "class tree is null.");
                return;
            }

            ControlFlowGraph cfg =
                    CFGBuilder.build(rootTree, processingEnv, methodTree, classTree);
            this.result = new CFGProcessResult(cfg);
        }

        @Override
        protected TreePathScanner<?, ?> createTreePathScanner(CompilationUnitTree root) {
            rootTree = root;
            return new TreePathScanner<Void, Void>() {
                @Override
                public Void visitClass(ClassTree node, Void p) {
                    TypeElement el = TreeUtils
                            .elementFromDeclaration(node);
                    if (el.getSimpleName().contentEquals(className)) {
                        classTree = node;
                    }
                    return super.visitClass(node, p);
                }

                @Override
                public Void visitMethod(MethodTree node, Void p) {
                    ExecutableElement el = TreeUtils
                            .elementFromDeclaration(node);
                    if (el.getSimpleName().contentEquals(methodName)) {
                        methodTree = node;
                        // stop execution by throwing an exception. this
                        // makes sure that compilation does not proceed, and
                        // thus the AST is not modified by further phases of
                        // the compilation (and we save the work to do the
                        // compilation).
                        throw new RuntimeException();
                    }
                    return null;
                }
            };
        }

        @Override
        public SourceVersion getSupportedSourceVersion() {
          return SourceVersion.latestSupported();
        }
    }

    private static class CFGProcessResult {
        private final ControlFlowGraph controlFlowGraph;
        private final boolean isSuccess;
        private final String errMsg;

        public CFGProcessResult(final ControlFlowGraph cfg) {
            this(cfg, true, null);
            assert cfg != null : "this constructor should called if cfg were success built.";
        }

        public CFGProcessResult(ControlFlowGraph cfg, boolean isSuccess, String errMsg) {
            this.controlFlowGraph = cfg;
            this.isSuccess = isSuccess;
            this.errMsg = errMsg;
        }

        public boolean isSuccess() {
            return isSuccess;
        }

        public ControlFlowGraph getCFG() {
            return controlFlowGraph;
        }

        public String getErrMsg() {
            return errMsg;
        }
    }


    /** Print an error message. */
    protected static void printError(String string) {
        System.err.println("ERROR: " + string);
    }

}

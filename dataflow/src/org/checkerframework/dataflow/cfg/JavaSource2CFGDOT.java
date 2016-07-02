package org.checkerframework.dataflow.cfg;

/*>>>
import org.checkerframework.checker.nullness.qual.Nullable;
*/

import org.checkerframework.dataflow.analysis.AbstractValue;
import org.checkerframework.dataflow.analysis.Analysis;
import org.checkerframework.dataflow.analysis.Store;
import org.checkerframework.dataflow.analysis.TransferFunction;
import org.checkerframework.javacutil.BasicTypeProcessor;
import org.checkerframework.javacutil.TreeUtils;


import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.xml.ws.Holder;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.util.TreePath;
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
public class JavaSource2CFGDOT {

    /** Main method. */
    public static void main(String[] args) {
        if (args.length < 2) {
            printUsage();
            System.exit(1);
        }
        String input = args[0];
        String output = args[1];
        File file = new File(input);
        if (!file.canRead()) {
            printError("Cannot read input file: " + file.getAbsolutePath());
            printUsage();
            System.exit(1);
        }

        String method = "test";
        String clas = "Test";
        boolean pdf = false;
        boolean error = false;

        for (int i = 2; i < args.length; i++) {
            if (args[i].equals("-pdf")) {
                pdf = true;
            } else if (args[i].equals("-method")) {
                if (i >= args.length - 1) {
                    printError("Did not find <name> after -method.");
                    continue;
                }
                i++;
                method = args[i];
            } else if (args[i].equals("-class")) {
                if (i >= args.length - 1) {
                    printError("Did not find <name> after -class.");
                    continue;
                }
                i++;
                clas = args[i];
            } else {
                printError("Unknown command line argument: " + args[i]);
                error = true;
            }
        }

        if (error) {
            System.exit(1);
        }

        generateDOTofCFGWithoutAnalysis(input, output, method, clas, pdf);
    }

    /** Print an error message. */
    protected static void printError(String string) {
        System.err.println("ERROR: " + string);
    }

    /** Print usage information. */
    protected static void printUsage() {
        System.out
                .println("Generate the control flow graph of a Java method, represented as a DOT graph.");
        System.out
                .println("Parameters: <inputfile> <outputdir> [-method <name>] [-class <name>] [-pdf]");
        System.out
                .println("    -pdf:    Also generate the PDF by invoking 'dot'.");
        System.out
                .println("    -method: The method to generate the CFG for (defaults to 'test').");
        System.out
                .println("    -class:  The class in which to find the method (defaults to 'Test').");
    }

    /** Just like method above but without analysis. */
    public static void generateDOTofCFGWithoutAnalysis(String inputFile, String outputDir,
            String method, String clas, boolean pdf) {
        generateDOTofCFG(inputFile, outputDir, method, clas, pdf, null);
    }

    /**
     * Generate the DOT representation of the CFG for a method.
     *
     * @param inputFile
     *            Java source input file.
     * @param outputDir
     *            Source output directory.
     * @param method
     *            Method name to generate the CFG for.
     * @param pdf
     *            Also generate a PDF?
     * @param analysis
     *            Analysis to perform befor the visualization (or
     *            <code>null</code> if no analysis is to be performed).
     */
    public static
    <A extends AbstractValue<A>, S extends Store<S>, T extends TransferFunction<A, S>>
    void generateDOTofCFG(
            String inputFile, String outputDir, String method, String clas,
            boolean pdf, /*@Nullable*/ Analysis<A, S, T> analysis) {

        ControlFlowGraph cfg = getMethodCFG(inputFile, method, clas);

        String fileName = (new File(inputFile)).getName();
        System.out.println("Working on " + fileName + "...");

        if (analysis != null) {
            analysis.performAnalysis(cfg);
        }

        Map<String, Object> args = new HashMap<>();
        args.put("outdir", outputDir);
        args.put("checkerName", "");

        CFGVisualizer<A, S, T> viz = new DOTCFGVisualizer<A, S, T>();
        viz.init(args);
        Map<String, Object> res = viz.visualize(cfg, cfg.getEntryBlock(), analysis);
        viz.shutdown();

        if (pdf) {
            producePDF((String) res.get("dotFileName"));
        }
    }

    /**
     * Invoke DOT to generate a PDF.
     */
    protected static void producePDF(String file) {
        try {
            String command = "/usr/local/bin/dot -Tpdf \"" + file + "\" -o \"" + file
                    + ".pdf\"";
            Process child = Runtime.getRuntime().exec(command);
            child.waitFor();
            System.out.println("generating pdf, command is:\n" + command);
            System.out.println("success!");
        } catch (InterruptedException | IOException e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * @return the AST of a specific method in a specific class as well as the
     *         {@link CompilationUnitTree} in a specific file (or null they do
     *         not exist).
     */
    public static ControlFlowGraph getMethodCFG(
            String file, final String method, String clas) {

        final Holder<ResultType> resultHolder = new Holder<>();
        BasicTypeProcessor typeProcessor = new MyProcessor(clas, method, resultHolder);

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
            javac.compile(List.of(l), List.of(clas), List.of(typeProcessor));
        } catch (Throwable e) {
            // ok
        } finally {
            System.setErr(err);
        }

        ResultType res = resultHolder.value;

        if (res == null) {
            printError("internal error in type processor! method typeProcess() doesn't get called.");
            System.exit(1);
        }

        if (!res.isSuccess) {
            printError(res.errMsg);
            System.exit(1);
        }

        return res.getCFG();
    }

    @SupportedAnnotationTypes("*")
    static class MyProcessor extends BasicTypeProcessor {

        private final String className;
        private final String methodName;

        private final Holder<CompilationUnitTree> rootTree;
        private final Holder<ClassTree> classTree;
        private final Holder<MethodTree> methodTree;

        private final Holder<ResultType> result;

        MyProcessor(String className, String methodName, Holder<ResultType> res) {
            this.className = className;
            this.methodName = methodName;
            this.rootTree = new Holder<> ();
            this.methodTree = new Holder<> ();
            this.classTree = new Holder<> ();
            this.result = res;
        }

        @Override
        public void typeProcess(TypeElement e, TreePath p) {
            super.typeProcess(e, p);
            if (rootTree.value == null) {
                this.result.value = new ResultType(null, false, "rootTree is null!.");
                return;
            }

            if (classTree.value == null) {
                this.result.value = new ResultType(null, false, "class not found.");
                return;
            }

            if (methodTree.value == null) {
              this.result.value = new ResultType(null, false, "method not found.");
              return;
            }

            ControlFlowGraph cfg =
                    CFGBuilder.build(rootTree.value, processingEnv, methodTree.value, classTree.value);
            this.result.value = new ResultType(cfg);
        }

        @Override
        protected TreePathScanner<?, ?> createTreePathScanner(CompilationUnitTree root) {
            rootTree.value = root;
            return new TreePathScanner<Void, Void>() {
                @Override
                public Void visitClass(ClassTree node, Void p) {
                    TypeElement el = TreeUtils
                            .elementFromDeclaration(node);
                    if (el.getSimpleName().contentEquals(className)) {
                        classTree.value = node;
                    }
                    return super.visitClass(node, p);
                }

                @Override
                public Void visitMethod(MethodTree node, Void p) {
                    ExecutableElement el = TreeUtils
                            .elementFromDeclaration(node);
                    if (el.getSimpleName().contentEquals(methodName)) {
                        methodTree.value = node;
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

    static class ResultType {
        private final ControlFlowGraph controlFlowGraph;
        private final boolean isSuccess;
        private final String errMsg;

        public ResultType(final ControlFlowGraph cfg) {
            this(cfg, true, null);
            assert cfg != null : "this constructor should called if cfg were success built.";
        }

        public ResultType(ControlFlowGraph cfg, boolean isSuccess, String errMsg) {
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

}

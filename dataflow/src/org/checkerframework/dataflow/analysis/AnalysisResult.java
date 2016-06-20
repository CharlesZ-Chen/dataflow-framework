package org.checkerframework.dataflow.analysis;

/*>>>
import org.checkerframework.checker.nullness.qual.Nullable;
*/

import org.checkerframework.dataflow.cfg.block.Block;
import org.checkerframework.dataflow.cfg.node.Node;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Map.Entry;

import javax.lang.model.element.Element;

import com.sun.source.tree.Tree;

/**
 * An {@link AnalysisResult} represents the result of a org.checkerframework.dataflow analysis by
 * providing the abstract values given a node or a tree. Note that it does not
 * keep track of custom results computed by some analysis.
 *
 * @author Stefan Heule
 *
 * @param <V>
 *            type of the abstract value that is tracked.
 */
public class AnalysisResult<V extends AbstractValue<V>, S extends Store<S>> {

    /** Abstract values of nodes. */
    protected final IdentityHashMap<Node, V> nodeValues;

    /** Map from AST {@link Tree}s to {@link Node}s. */
    protected final IdentityHashMap<Tree, Node> treeLookup;

    /** Map from (effectively final) local variable elements to their abstract value. */
    protected final HashMap<Element, V> finalLocalValues;

    /**
     * The stores before every method call.
     */
    protected final IdentityHashMap<Block, TransferInput<V, S>> stores;

    /**
     * Initialize with a given node-value mapping.
     */
    public AnalysisResult(Map<Node, V> nodeValues,
            IdentityHashMap<Block, TransferInput<V, S>> stores,
            IdentityHashMap<Tree, Node> treeLookup, HashMap<Element, V> finalLocalValues) {
        this.nodeValues = new IdentityHashMap<>(nodeValues);
        this.treeLookup = new IdentityHashMap<>(treeLookup);
        this.stores = stores;
        this.finalLocalValues = finalLocalValues;
    }

    /**
     * Initialize empty result.
     */
    public AnalysisResult() {
        nodeValues = new IdentityHashMap<>();
        treeLookup = new IdentityHashMap<>();
        stores = new IdentityHashMap<>();
        finalLocalValues = new HashMap<>();
    }

    /**
     * Combine with another analysis result.
     */
    public void combine(AnalysisResult<V, S> other) {
        for (Entry<Node, V> e : other.nodeValues.entrySet()) {
            nodeValues.put(e.getKey(), e.getValue());
        }
        for (Entry<Tree, Node> e : other.treeLookup.entrySet()) {
            treeLookup.put(e.getKey(), e.getValue());
        }
        for (Entry<Block, TransferInput<V, S>> e : other.stores.entrySet()) {
            stores.put(e.getKey(), e.getValue());
        }
        for (Entry<Element, V> e : other.finalLocalValues.entrySet()) {
            finalLocalValues.put(e.getKey(), e.getValue());
        }
    }

    /**
     * @return the value of effectively final local variables
     */
    public HashMap<Element, V> getFinalLocalValues() {
        return finalLocalValues;
    }

    /**
     * @return the abstract value for {@link Node} {@code n}, or {@code null} if
     *         no information is available.
     */
    public /*@Nullable*/ V getValue(Node n) {
        return nodeValues.get(n);
    }

    /**
     * @return the abstract value for {@link Tree} {@code t}, or {@code null} if
     *         no information is available.
     */
    public /*@Nullable*/ V getValue(Tree t) {
        V val = getValue(treeLookup.get(t));
        return val;
    }

    /**
     * @return the {@link Node} for a given {@link Tree}.
     */
    public /*@Nullable*/ Node getNodeForTree(Tree tree) {
        return treeLookup.get(tree);
    }

    /**
     * @return the store immediately before a given {@link Tree}.
     */
    public S getStoreBefore(Tree tree) {
        Node node = getNodeForTree(tree);
        if (node == null) {
            return null;
        }
        return getStoreBefore(node);
    }

    /**
     * @return the store immediately before a given {@link Node}.
     */
    public S getStoreBefore(Node node) {
        return runAnalysisFor(node, true);
    }

    /**
     * @return the store immediately after a given {@link Tree}.
     */
    public S getStoreAfter(Tree tree) {
        Node node = getNodeForTree(tree);
        if (node == null) {
            return null;
        }
        return runAnalysisFor(node, false);
    }

    /**
     * Runs the analysis again within the block of {@code node} and returns the
     * store at the location of {@code node}. If {@code before} is true, then
     * the store immediately before the {@link Node} {@code node} is returned.
     * Otherwise, the store after {@code node} is returned.
     *
     * <p>
     * If the given {@link Node} cannot be reached (in the control flow graph),
     * then {@code null} is returned.
     */
    protected S runAnalysisFor(Node node, boolean before) {
        Block block = node.getBlock();
        TransferInput<V, S> transferInput = stores.get(block);
        if (transferInput == null) {
            return null;
        }
        return AbstractAnalysis.runAnalysisFor(node, before, transferInput, transferInput.analysis.direction);
    }

}

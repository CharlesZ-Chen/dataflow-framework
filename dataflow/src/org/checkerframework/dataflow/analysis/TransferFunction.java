package org.checkerframework.dataflow.analysis;

/*>>>
import org.checkerframework.checker.nullness.qual.Nullable;
*/

import org.checkerframework.dataflow.cfg.node.Node;
import org.checkerframework.dataflow.cfg.node.NodeVisitor;

/**
 * Interface of a transfer function for the abstract interpretation used for the
 * flow analysis.
 *
 * <p>
 *
 * A transfer function consists of the following components:
 * <ul>
 * <li>A method {@code initialStore} that determines which initial store should
 * be used in the org.checkerframework.dataflow analysis.</li>
 * <li>A function for every {@link Node} type that determines the behavior of
 * the org.checkerframework.dataflow analysis in that case. This method takes a {@link Node} and an
 * incoming store, and produces a {@link RegularTransferResult}.</li>
 * </ul>
 *
 * <p>
 *
 * <em>Important</em>: The individual transfer functions ( {@code visit*}) are
 * allowed to use (and modify) the stores contained in the argument passed; the
 * ownership is transfered from the caller to that function.
 *
 * @author Stefan Heule
 *
 * @param <S>
 *            The {@link Store} used to keep track of intermediate results.
 */
public interface TransferFunction<V extends AbstractValue<V>, S extends Store<S>>
        extends NodeVisitor<TransferResult<V, S>, TransferInput<V, S>> {
}

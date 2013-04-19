package semper.carbon.modules

import components.{StateComponent, ComponentRegistry}
import semper.sil.{ast => sil}
import semper.carbon.boogie.{LocalVarDecl, Exp, Stmt}

/**
 * A module for dealing with the state of a program during execution.  Allows other modules
 * to register [[semper.carbon.modules.components.StateComponent]]s that contribute to the
 * state.
 *
 * @author Stefan Heule
 */
trait StateModule extends Module with ComponentRegistry[StateComponent] {

  /**
   * Returns an assumption that the current state is 'good', or well-formed.
   */
  def assumeGoodState: Stmt

  /**
   * Returns a static invocation of the 'is good state' function with the arguments from
   * `stateContributions`.
   */
  def staticGoodState: Exp

  /**
   * The statements necessary to initialize the part of the state belonging to this module.
   */
  def initState: Stmt

  /**
   * The statements necessary to initialize old(state).
   */
  def initOldState: Stmt

  /**
   * The name and type of the contribution of this components to the state.
   */
  def stateContributions: Seq[LocalVarDecl]

  /**
   * The current values for this components state contributions.  The number of elements
   * in the list and the types must correspond to the ones given in `stateContributions`.
   */
  def currentStateContributions: Seq[Exp]

  type StateSnapshot

  /**
   * Backup the current state and return enough information such that it can
   * be restored again at a later point.
   */
  def freshTempState: (Stmt, StateSnapshot)

  /**
   * Restore the state to a given snapshot.
   */
  def restoreState(snapshot: StateSnapshot)

  /**
   * Change the state from 'x' to 'old(x)' and return a snapshot of 'x' such
   * that it can be restored later.
   */
  def makeOldState: StateSnapshot
}

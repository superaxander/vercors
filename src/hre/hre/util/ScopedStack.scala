package hre.util

import java.util.concurrent.atomic.AtomicBoolean
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

case object ScopedStack {
  implicit class ScopedArrayBufferStack[T](stack: ScopedStack[ArrayBuffer[T]]) {
    def collect[R](f: => R): (Seq[T], R) = {
      val buf = ArrayBuffer[T]()
      val res = stack.having(buf)(f)
      (buf.toSeq, res)
    }
  }
}

case class ScopedStack[T]() {
  private val stacks: ThreadLocal[mutable.Stack[T]] = ThreadLocal.withInitial(() => mutable.Stack())
  private val modified: AtomicBoolean = new AtomicBoolean(false)

  private def stack: mutable.Stack[T] = stacks.get()

  def isEmpty: Boolean = stack.isEmpty
  def nonEmpty: Boolean = stack.nonEmpty
  def push(t: T): Unit = {
    modified.set(true)
    stack.push(t)
  }
  def pop(): T = stack.pop()
  def top: T = stack.top
  def topOption: Option[T] = stack.headOption
  def find(f: T => Boolean): Option[T] = stack.find(f)
  def exists(f: T => Boolean): Boolean = stack.exists(f)
  def foreach(f: T => Unit): Unit = stack.foreach(f)
  def toSeq: Seq[T] = if (modified.get()) stack.toSeq else Seq()
  def contains(t: T): Boolean = find(_ == t).isDefined

  def having[R](x: T)(f: => R): R = {
    modified.set(true)
    stack.push(x)
    try {
      f
    } finally {
      stack.pop()
    }
  }
}

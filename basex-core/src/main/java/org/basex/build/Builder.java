package org.basex.build;

import static org.basex.build.BuildText.*;
import static org.basex.core.Text.*;
import static org.basex.util.Token.*;

import java.io.*;

import org.basex.core.*;
import org.basex.data.*;
import org.basex.index.name.*;
import org.basex.index.path.*;
import org.basex.io.*;
import org.basex.util.*;
import org.basex.util.list.*;

/**
 * This class provides an interface for building database instances.
 * The specified {@link Parser} sends events to this class whenever nodes
 * are to be added or closed. The builder implementation decides whether
 * the nodes are stored on disk or kept in memory.
 *
 * @author BaseX Team 2005-15, BSD License
 * @author Christian Gruen
 */
public abstract class Builder extends Proc {
  /** Tree structure. */
  final PathSummary path = new PathSummary();
  /** Namespace index. */
  final Namespaces ns = new Namespaces();
  /** Parser instance. */
  final Parser parser;
  /** Database name. */
  final String dbname;

  /** Number of cached size values. */
  int ssize;
  /** Currently stored size value. */
  int spos;

  /** Meta data on built database. */
  MetaData meta;
  /** Element name index. */
  Names elemNames;
  /** Attribute name index. */
  Names attrNames;

  /** Parent stack. */
  private final IntList pstack = new IntList();
  /** Stack with element names. */
  private final IntList tstack = new IntList();
  /** Current tree height. */
  private int level;

  /**
   * Constructor.
   * @param dbname name of database
   * @param parser parser
   */
  Builder(final String dbname, final Parser parser) {
    this.dbname = dbname;
    this.parser = parser;
  }

  // PUBLIC METHODS ===========================================================

  /**
   * Parses the given input source and builds the database.
   * @throws IOException I/O exception
   */
  final void parse() throws IOException {
    // add document node and parse document
    parser.parse(this);
    meta.lastid = meta.size - 1;
  }

  /**
   * Opens a document node.
   * @param value document name
   * @throws IOException I/O exception
   */
  public final void openDoc(final byte[] value) throws IOException {
    path.put(0, Data.DOC, level);
    pstack.set(level++, meta.size);
    addDoc(value);
    ns.prepare();
  }

  /**
   * Closes a document node.
   * @throws IOException I/O exception
   */
  public final void closeDoc() throws IOException {
    final int pre = pstack.get(--level);
    setSize(pre, meta.size - pre);
    ++meta.ndocs;
    ns.close(meta.size);
  }

  /**
   * Opens a new element node.
   * @param name name of element
   * @param att attributes
   * @param nsp namespaces
   * @throws IOException I/O exception
   */
  public final void openElem(final byte[] name, final Atts att, final Atts nsp) throws IOException {
    addElem(name, att, nsp);
    ++level;
  }

  /**
   * Stores an empty element.
   * @param name name of element
   * @param att attributes
   * @param nsp namespaces
   * @throws IOException I/O exception
   */
  public final void emptyElem(final byte[] name, final Atts att, final Atts nsp)
      throws IOException {
    addElem(name, att, nsp);
    final int pre = pstack.get(level);
    ns.close(pre);
    if(att.size() > IO.MAXATTS) setSize(pre, meta.size - pre);
  }

  /**
   * Closes an element.
   * @throws IOException I/O exception
   */
  public final void closeElem() throws IOException {
    checkStop();
    --level;
    final int pre = pstack.get(level);
    setSize(pre, meta.size - pre);
    ns.close(pre);
  }

  /**
   * Stores a text node.
   * @param value text value
   * @throws IOException I/O exception
   */
  public final void text(final byte[] value) throws IOException {
    if(value.length != 0) addText(value, Data.TEXT);
  }

  /**
   * Stores a comment.
   * @param value comment text
   * @throws IOException I/O exception
   */
  public final void comment(final byte[] value) throws IOException {
    addText(value, Data.COMM);
  }

  /**
   * Stores a processing instruction.
   * @param pi processing instruction name and value
   * @throws IOException I/O exception
   */
  public final void pi(final byte[] pi) throws IOException {
    addText(pi, Data.PI);
  }

  /**
   * Sets the document encoding.
   * @param encoding encoding
   */
  public final void encoding(final String encoding) {
    meta.encoding = Strings.normEncoding(encoding);
  }

  // PROGRESS INFORMATION =====================================================

  @Override
  protected final String tit() {
    return CREATING_DB;
  }

  @Override
  public final String det() {
    return spos == 0 ? parser.detail() : FINISHING_D;
  }

  @Override
  public final double prog() {
    return spos == 0 ? parser.progress() : (double) spos / ssize;
  }

  // ABSTRACT METHODS =========================================================

  /**
   * Builds the database.
   * @return data database instance
   * @throws IOException I/O exception
   */
  public abstract Data build() throws IOException;

  /**
   * Returns a data clip with the parsed input.
   * @return data data clip
   * @throws IOException I/O exception
   */
  public abstract DataClip dataClip() throws IOException;

  /**
   * Closes open references.
   * @throws IOException I/O exception
   */
  public abstract void close() throws IOException;

  /**
   * Adds a document node to the database.
   * @param value name of the document
   * @throws IOException I/O exception
   */
  protected abstract void addDoc(byte[] value) throws IOException;

  /**
   * Adds an element node to the database. This method stores a preliminary
   * size value; if this node has further descendants, {@link #setSize} must
   * be called to set the final size value.
   * @param dist distance to parent
   * @param name element name
   * @param asize number of attributes
   * @param uri namespace uri reference
   * @param ne namespace flag
   * @throws IOException I/O exception
   */
  protected abstract void addElem(int dist, int name, int asize, int uri, boolean ne)
      throws IOException;

  /**
   * Adds an attribute to the database.
   * @param name attribute name
   * @param value attribute value
   * @param dist distance to parent
   * @param uri namespace uri reference
   * @throws IOException I/O exception
   */
  protected abstract void addAttr(int name, byte[] value, int dist, int uri) throws IOException;

  /**
   * Adds a text node to the database.
   * @param value the token to be added
   * @param dist distance to parent
   * @param kind the node kind
   * @throws IOException I/O exception
   */
  protected abstract void addText(byte[] value, int dist, byte kind) throws IOException;

  /**
   * Stores a size value to the specified table position.
   * @param pre pre reference
   * @param size value to be stored
   * @throws IOException I/O exception
   */
  protected abstract void setSize(int pre, int size) throws IOException;

  // PRIVATE METHODS ==========================================================

  /**
   * Adds an element node to the storage.
   * @param name element name
   * @param att attributes
   * @param nsp namespaces
   * @throws IOException I/O exception
   */
  private void addElem(final byte[] name, final Atts att, final Atts nsp) throws IOException {
    // get reference of element name
    int n = elemNames.index(name, null, true);
    path.put(n, Data.ELEM, level);

    // cache pre value
    final int pre = meta.size;
    // remember id of element name and parent reference
    tstack.set(level, n);
    pstack.set(level, pre);

    // parse namespaces
    ns.prepare();
    final int nl = nsp.size();
    for(int nx = 0; nx < nl; nx++) ns.add(nsp.name(nx), nsp.value(nx), pre);

    // get and store element references
    final int dis = level == 0 ? 1 : pre - pstack.get(level - 1);
    final int as = att.size();
    int u = ns.uri(name, true);
    if(u == 0 && indexOf(name, ':') != -1 && !eq(prefix(name), XML))
      throw new BuildException(WHICHNS, parser.detail(), prefix(name));
    addElem(dis, n, Math.min(IO.MAXATTS, as + 1), u, nl != 0);

    // get and store attribute references
    for(int a = 0; a < as; ++a) {
      final byte[] av = att.value(a);
      final byte[] an = att.name(a);
      n = attrNames.index(an, av, true);
      u = ns.uri(an, false);
      if(u == 0 && indexOf(an, ':') != -1 && !eq(prefix(an), XML))
        throw new BuildException(WHICHNS, parser.detail(), an);

      path.put(n, Data.ATTR, level + 1, av, meta);
      addAttr(n, av, Math.min(IO.MAXATTS, a + 1), u);
    }

    // set leaf node information in index
    if(level > 1) elemNames.stat(tstack.get(level - 1)).setLeaf(false);

    // check if data ranges exceed database limits,
    // based on the storage details in {@link Data}
    limit(elemNames.size(), 0x8000, LIMITELEMS);
    limit(attrNames.size(), 0x8000, LIMITATTS);
    limit(ns.size(), 0x100, LIMITNS);
    if(meta.size < 0) limit(0, 0, LIMITRANGE);
  }

  /**
   * Checks a value limit and optionally throws an exception.
   * @param value value
   * @param limit limit
   * @param msg message
   * @throws IOException I/O exception
   */
  private void limit(final int value, final int limit, final String msg) throws IOException {
    if(value >= limit) throw new BuildException(msg, parser.detail(), limit);
  }

  /**
   * Adds a simple text, comment or processing instruction to the database.
   * @param value the value to be added
   * @param kind the node type
   * @throws IOException I/O exception
   */
  private void addText(final byte[] value, final byte kind) throws IOException {
    final int l = level;
    if(l > 1) {
      final int i = tstack.get(l - 1);
      // text node processing for statistics
      if(kind == Data.TEXT) elemNames.index(i, value);
      // set leaf node information in index
      else elemNames.stat(i).setLeaf(false);
    }

    path.put(0, kind, l, value, meta);
    addText(value, l == 0 ? 1 : meta.size - pstack.get(l - 1), kind);
  }
}

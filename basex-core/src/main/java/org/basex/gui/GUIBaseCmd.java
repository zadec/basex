package org.basex.gui;

import javax.swing.*;

/**
 * This class provides a default implementation for GUI commands.
 *
 * @author BaseX Team 2005-13, BSD License
 * @author Christian Gruen
 */
public abstract class GUIBaseCmd implements GUICmd {
  /** Separator. */
  public static final GUICmd SEPARATOR = new GUIBaseCmd() {
    @Override
    public void execute(final GUI gui) { }
    @Override
    public String label() { return null; }
  };

  @Override
  public final boolean toggle() {
    return false;
  }
  @Override
  public final String help() {
    return null;
  }
  @Override
  public Object key() {
    return null;
  }
  @Override
  public final void refresh(final GUI main, final AbstractButton button) {
    button.setEnabled(enabled());
  }

  /**
   * Checks if the command is currently enabled.
   * @return result of check
   */
  public boolean enabled() {
    return true;
  }
}

/*
 *  Freeplane - mind map editor
 *  Copyright (C) 2008 Joerg Mueller, Daniel Polansky, Christian Foltin, Dimitry Polivaev
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.freeplane.features.nodestyle.mindmapmode;

import java.awt.event.ActionEvent;

import javax.swing.JOptionPane;

import org.freeplane.core.ui.AFreeplaneAction;
import org.freeplane.core.ui.AMultipleNodeAction;
import org.freeplane.core.util.TextUtils;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.styles.LogicalStyleKeys;

/**
 * @author foltin
 */
class CopyFormat extends AFreeplaneAction {
	private static NodeModel pattern = null;
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	public static NodeModel getPattern() {
		return pattern;
	}

	public CopyFormat() {
		super("FormatCopy");
	}

	public void actionPerformed(final ActionEvent e) {
		copyFormat(Controller.getCurrentModeController().getMapController().getSelectedNode());
	}

	/**
	 */
	private void copyFormat(final NodeModel node) {
		CopyFormat.pattern = new NodeModel(null);
		Controller.getCurrentModeController().undoableCopyExtensions(LogicalStyleKeys.NODE_STYLE, node, pattern);
		Controller.getCurrentModeController().undoableCopyExtensions(LogicalStyleKeys.LOGICAL_STYLE, node, pattern);
	}
}

class PasteFormat extends AMultipleNodeAction {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	public PasteFormat() {
		super("FormatPaste");
	}

	@Override
	protected void actionPerformed(final ActionEvent e, final NodeModel node) {
		pasteFormat(node);
	}

	/**
	 */
	private void pasteFormat(final NodeModel node) {
		final NodeModel pattern = CopyFormat.getPattern();
		if (pattern == null) {
			JOptionPane.showMessageDialog(Controller.getCurrentController().getViewController().getContentPane(), TextUtils
			    .getText("no_format_copy_before_format_paste"), "" /*=Title*/, JOptionPane.ERROR_MESSAGE);
			return;
		}
		Controller.getCurrentModeController().undoableRemoveExtensions(LogicalStyleKeys.LOGICAL_STYLE, node, node);
		Controller.getCurrentModeController().undoableRemoveExtensions(LogicalStyleKeys.NODE_STYLE, node, node);
		Controller.getCurrentModeController().undoableCopyExtensions(LogicalStyleKeys.NODE_STYLE, pattern, node);
		Controller.getCurrentModeController().undoableCopyExtensions(LogicalStyleKeys.LOGICAL_STYLE, pattern, node);
	}
}

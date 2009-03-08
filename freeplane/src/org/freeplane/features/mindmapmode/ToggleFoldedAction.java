/*
 *  Freeplane - mind map editor
 *  Copyright (C) 2008 Joerg Mueller, Daniel Polansky, Christian Foltin, Dimitry Polivaev
 *
 *  This file is modified by Dimitry Polivaev in 2008.
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
package org.freeplane.features.mindmapmode;

import java.awt.event.ActionEvent;
import java.util.ListIterator;

import org.apache.commons.lang.StringUtils;
import org.freeplane.core.controller.Controller;
import org.freeplane.core.enums.ResourceControllerProperties;
import org.freeplane.core.modecontroller.ModeController;
import org.freeplane.core.model.NodeModel;
import org.freeplane.core.resources.ResourceController;
import org.freeplane.core.ui.AFreeplaneAction;
import org.freeplane.core.undo.IUndoableActor;

class ToggleFoldedAction extends AFreeplaneAction {
	public ToggleFoldedAction(final Controller controller) {
		super(controller, "toggle_folded");
	}

	public void actionPerformed(final ActionEvent e) {
		toggleFolded();
	}

	/**
	 */
	public void setFolded(final NodeModel node, final boolean folded) {
		if (node.isFolded() == folded) {
			return;
		}
		toggleFolded(node);
	}

	public void toggleFolded() {
		toggleFolded(getModeController().getMapController().getSelectedNodes().listIterator());
	}

	public void toggleFolded(final ListIterator listIterator) {
		while (listIterator.hasNext()) {
			toggleFolded((NodeModel) listIterator.next());
		}
	}

	private void toggleFolded(final NodeModel node) {
		if (!getModeController().getMapController().hasChildren(node)
		        && !StringUtils.equals(ResourceController.getResourceController().getProperty("enable_leaves_folding"),
		            "true")) {
			return;
		}
		final ModeController modeController = getModeController();
		final IUndoableActor actor = new IUndoableActor() {
			public void act() {
				modeController.getMapController()._setFolded(node, !node.isFolded());
				final ResourceController resourceController = ResourceController.getResourceController();
				if (resourceController.getProperty(ResourceControllerProperties.RESOURCES_SAVE_FOLDING)
				    .equals(ResourceControllerProperties.RESOURCES_ALWAYS_SAVE_FOLDING)) {
					modeController.getMapController().nodeChanged(node);
				}
			}

			public String getDescription() {
				return "toggleFolded";
			}

			public void undo() {
				act();
			}
		};
		modeController.execute(actor);
	}
}

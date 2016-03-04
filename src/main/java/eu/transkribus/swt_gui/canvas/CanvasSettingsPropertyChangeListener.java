package eu.transkribus.swt_gui.canvas;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.transkribus.swt_canvas.canvas.CanvasMode;
import eu.transkribus.swt_canvas.canvas.CanvasSettings;
import eu.transkribus.swt_canvas.canvas.shapes.CanvasShapeType;
import eu.transkribus.swt_gui.TrpConfig;
import eu.transkribus.swt_gui.mainwidget.TrpMainWidget;
import eu.transkribus.swt_gui.mainwidget.TrpMainWidgetView;

public class CanvasSettingsPropertyChangeListener implements PropertyChangeListener {
	private final static Logger logger = LoggerFactory.getLogger(CanvasSettingsPropertyChangeListener.class);
	
	TrpMainWidget mainWidget;
	TrpMainWidgetView ui;
	TrpSWTCanvas canvas;
	
	public static boolean SAVE_PROPS_ON_CHANGE = true;
	
	public CanvasSettingsPropertyChangeListener(TrpMainWidget mainWidget) {
		this.mainWidget = mainWidget;
		this.ui = mainWidget.getUi();
		this.canvas = mainWidget.getCanvas();
	}
	
	@Override
	public void propertyChange(PropertyChangeEvent evt) {
		String pn = evt.getPropertyName();
		logger.debug("property changed: " + pn);

		if (pn.equals(CanvasSettings.EDITING_ENABLED_PROPERTY)) {
			mainWidget.updateSegmentationEditStatus();
		} else if (pn.equals(CanvasSettings.MODE_PROPERTY)) {
			CanvasMode m = (CanvasMode) evt.getNewValue();
			if (m.isSplitOperation())
				canvas.getShapeEditor().setShapeToDraw(CanvasShapeType.POLYLINE);
			else if (m.isAddOperation()) {
				canvas.getShapeEditor().setShapeToDraw(ui.getShapeTypeToDraw());
			}
			
			// update message:
//			if (m.isAddOperation()) {
//				ui.setStatusMessage("Press Esc to return to select mode, Press <Enter> to confirm a shape", 0);
//			}
//			else if (m == CanvasMode.SELECTION)
//				ui.setStatusMessage("", 0);
		}
		
		if (SAVE_PROPS_ON_CHANGE && !CanvasSettings.DO_NOT_SAVE_THOSE_PROPERTIES.contains(pn)) {
			logger.debug("saving config file...");
			TrpConfig.save(pn);
		}

	}
}

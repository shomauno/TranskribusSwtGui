package eu.transkribus.swt_gui.metadata;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.transkribus.core.model.beans.customtags.CustomTag;
import eu.transkribus.core.model.beans.customtags.CustomTagAttribute;
import eu.transkribus.core.model.beans.customtags.CustomTagFactory;
import eu.transkribus.core.model.beans.customtags.CustomTagList;
import eu.transkribus.core.model.beans.pagecontent_trp.ITrpShapeType;
import eu.transkribus.core.model.beans.pagecontent_trp.TrpTextLineType;
import eu.transkribus.core.model.beans.pagecontent_trp.TrpWordType;
import eu.transkribus.core.util.IntRange;
import eu.transkribus.swt.util.DialogUtil;
import eu.transkribus.swt_gui.canvas.SWTCanvas;
import eu.transkribus.swt_gui.mainwidget.TrpMainWidget;
import eu.transkribus.swt_gui.mainwidget.TrpMainWidgetView;
import eu.transkribus.swt_gui.mainwidget.settings.TrpSettings;
import eu.transkribus.swt_gui.transcription.ATranscriptionWidget;

public class TaggingWidgetListener implements ITaggingWidgetListener {
	private final static Logger logger = LoggerFactory.getLogger(TaggingWidgetListener.class);
	
	TrpMainWidget mainWidget;
	TrpMainWidgetView ui;
	PageMetadataWidget mw;
	TextStyleTypeWidget tw;
	SWTCanvas canvas;
	TrpSettings settings;
	
	public TaggingWidgetListener(TrpMainWidget mainWidget) {
		this.mainWidget = mainWidget;
		this.ui = mainWidget.getUi();
		this.canvas = mainWidget.getCanvas();
		this.mw = mainWidget.getUi().getStructuralMetadataWidget();
		this.tw = ui.getTextStyleWidget();
		this.settings = mainWidget.getTrpSets();
		
		ui.getTaggingWidgetNew().addListener(this);
	}
	
	@Override public void createNewTag(String tagName) {
		try {
			logger.debug("creating new tag: "+tagName);
			CustomTagFactory.addToRegistry(CustomTagFactory.create(tagName), null);
			
//			ui.getMetadataWidget().getTaggingWidget().updateAvailableTags();
//			ui.getTaggingWidgetNew().updateAvailableTags();
			mainWidget.getUi().getLineTranscriptionWidget().redrawText(true);
			mainWidget.getUi().getWordTranscriptionWidget().redrawText(true);
		} catch (Exception e) {
			DialogUtil.showErrorMessageBox(ui.getShell(), "Cannot add tag", e.getMessage());
		}
	}
	
	@Override public void removeTagDefinition(String tagName) {
		try {
			logger.debug("deleting tag: "+tagName);
			CustomTagFactory.removeFromRegistry(tagName);
			
			ui.getTaggingWidgetNew().updateAvailableTags();
			
//			ui.getMetadataWidget().getTaggingWidget().updateAvailableTags();
//			ui.getTaggingWidgetNew().updateAvailableTags();
			mainWidget.getUi().getLineTranscriptionWidget().redrawText(true);
			mainWidget.getUi().getWordTranscriptionWidget().redrawText(true);
		} catch (IOException e) {
			DialogUtil.showErrorMessageBox(ui.getShell(), "Cannot remove tag", e.getMessage());
		}
	}
		
	@Override public void addTagForSelection(String tagName, Map<String, Object> attributes) {
		mainWidget.addTagForSelection(tagName, attributes, null);
	}
		
//	@Override public void deleteTag(CustomTagAndList ctal) {
//		ctal.cl.deleteTagAndContinuations(ctal.ct);
//
//		mainWidget.updatePageRelatedMetadata();
//		mainWidget.getUi().getLineTranscriptionWidget().redrawText(true);
//		mainWidget.getUi().getWordTranscriptionWidget().redrawText(true);
//		mainWidget.refreshStructureView();
//	}
	
	@Override public void deleteTag(CustomTag tag) {
		if (tag == null || tag.getCustomTagList() == null)
			return;
		
		mainWidget.deleteTags(tag);

//		if (tag == null || tag.getCustomTagList() == null)
//			return;
//		
//		tag.getCustomTagList().deleteTagAndContinuations(tag);
//		
//		mainWidget.updatePageRelatedMetadata();
//		mainWidget.getUi().getLineTranscriptionWidget().redrawText(true);
//		mainWidget.getUi().getWordTranscriptionWidget().redrawText(true);
//		mainWidget.refreshStructureView();
	}

	/**
	 * @deprecated Ambiguous
	 */
	@Override public void deleteTagForSelection(String tagName) {
		ATranscriptionWidget aw = ui.getSelectedTranscriptionWidget();
		if (aw==null) {
			logger.debug("no transcription widget selected - doin nothing!");
			return;
		}
		
		final Pair<ITrpShapeType, Integer> shapeAndRelativePositionAtOffset = aw.getTranscriptionUnitAndRelativePositionFromCurrentOffset();
		if (shapeAndRelativePositionAtOffset==null)
			return;
		
		final ITrpShapeType shape = shapeAndRelativePositionAtOffset.getLeft();
		final CustomTagList ctl = shape.getCustomTagList();		
		
		CustomTag tag = null;
		for (CustomTag t : aw.getCustomTagsForCurrentOffset()) {
			if (t.getTagName().equals(tagName)) {
				tag = t;
				break;
			}
		}
		if (tag==null) {
			logger.warn("Could not find tag with name '"+tagName+"' for current offset - should not happen here!");
			return;
		}
		
		ctl.deleteTagAndContinuations(tag);
		
		mainWidget.updatePageRelatedMetadata();
		mainWidget.getUi().getLineTranscriptionWidget().redrawText(true);
		mainWidget.getUi().getWordTranscriptionWidget().redrawText(true);
		mainWidget.refreshStructureView();		
	}

	@Override public void deleteTagsForCurrentSelection() {
		mainWidget.deleteTagsForCurrentSelection();
	}

//	@Override public void tagsUpdated() {
//		mainWidget.getUi().getLineTranscriptionWidget().redrawText(true);
//		mainWidget.getUi().getWordTranscriptionWidget().redrawText(true);
//	}

	@Override public void addTagsForSelection(List<String> checkedTags) {
		for (String tagName : checkedTags) {
			addTagForSelection(tagName, null);
		}
	}

	@Override public void addAttributeOnCustomTag(String tn, CustomTagAttribute att) {
		logger.debug("adding attribute to tag: tn = "+tn+", attribute: "+att);
		
		try {
			CustomTag t = CustomTagFactory.getTagObjectFromRegistry(tn);
			logger.debug("tag object: "+t);
			
			if (t.hasAttribute(att.getName()))
				throw new Exception("Attribute already exists: "+att.getName());
			
			if (t != null) {
				t.setAttribute(att.getName(), null, true);
			}
			
//			mainWidget.updatePageRelatedMetadata();
//			ui.getMetadataWidget().getTaggingWidget().updatePropertiesForSelectedTag();
			ui.getTaggingWidgetNew().updatePropertiesForSelectedTag();
			
		} catch (Exception e) {
			mainWidget.onError("Error adding attribute to tag "+tn, e.getMessage(), e);
			
		}
		
	}

	@Override public void deleteAttributeOnCustomTag(String tn, String attributeName) {
		logger.debug("deleting attribute from tag: tn = "+tn+", attribute: "+attributeName);
		
		try {
			CustomTag t = CustomTagFactory.getTagObjectFromRegistry(tn);
			if (t != null) {
				t.deleteCustomAttribute(attributeName);
			}
			
			updateTaggingWidgets();
		} catch (Exception e) {
			mainWidget.onError("Error adding attribute to tag "+tn, e.getMessage(), e);
			
		}
	}
	
	private void updateTaggingWidgets() {
		ui.getTaggingWidgetNew().updatePropertiesForSelectedTag();
	}



}

package eu.transkribus.swt_canvas.canvas.editing;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.eclipse.swt.graphics.Point;
import org.junit.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.transkribus.swt_canvas.canvas.CanvasKeys;
import eu.transkribus.swt_canvas.canvas.CanvasMode;
import eu.transkribus.swt_canvas.canvas.CanvasScene;
import eu.transkribus.swt_canvas.canvas.CanvasSettings;
import eu.transkribus.swt_canvas.canvas.SWTCanvas;
import eu.transkribus.swt_canvas.canvas.editing.ShapeEditOperation.ShapeEditType;
import eu.transkribus.swt_canvas.canvas.shapes.CanvasPolygon;
import eu.transkribus.swt_canvas.canvas.shapes.CanvasPolyline;
import eu.transkribus.swt_canvas.canvas.shapes.CanvasShapeType;
import eu.transkribus.swt_canvas.canvas.shapes.ICanvasShape;
import eu.transkribus.swt_canvas.canvas.shapes.RectDirection;

/**
 * This class is resonsible for editing and drawing new shapes according to interactive points drawn by the user.
 * Currently supported shapes are: Polygon, Polyline, Rectangle
 */
public class CanvasShapeEditor {
	static Logger logger = LoggerFactory.getLogger(CanvasShapeEditor.class);
	
	protected SWTCanvas canvas;
	protected CanvasScene scene;
//	protected Class<? extends ICanvasShape> shapeToDraw=CanvasRect.class;
	protected CanvasShapeType shapeToDraw = CanvasShapeType.POLYGON;
	
	protected List<ICanvasShape> drawnShapes = new ArrayList<>();
	protected List<java.awt.Point> drawnPoints = new ArrayList<>();
	
	protected ShapeEditOperation currentMoveOp = null;
	
	protected ICanvasShape backupShape;

	protected boolean stopPostProcess=false;
	
	public CanvasShapeEditor(SWTCanvas canvas) {
		Assert.assertNotNull(canvas);
		
		this.canvas = canvas;
		this.scene = canvas.getScene();
		initListener();
	}
	
	private void initListener() {
		canvas.getSettings().addPropertyChangeListener(new PropertyChangeListener() {
			@Override
			public void propertyChange(PropertyChangeEvent evt) {
				boolean isModeProp = evt.getPropertyName().equals(CanvasSettings.MODE_PROPERTY);
				boolean isOrWasMoving = isModeProp && (evt.getNewValue() == CanvasMode.MOVE || evt.getOldValue() == CanvasMode.MOVE);
				logger.trace("isOrWasMoving: "+isOrWasMoving);
				
				if (!isOrWasMoving) { // do not clear shapes if user is moving canvas!
					clearDrawnShapes();
				}
			}
		});
	}
	
	public void addPointForSplitting(int x, int y) {
		if (!canvas.getMode().isSplitOperation())
			return;
		
		Point invPt = canvas.inverseTransform(x, y);
		
		if (canvas.getMode() == CanvasMode.SPLIT_SHAPE_LINE) {
//			if (drawnPoints.size() == 1) {
//				splitShape(canvas.getFirstSelected(), drawnPoints.get(0).x, drawnPoints.get(0).y, invPt.x,invPt.y, true);
//				drawnPoints.clear();
//			} else {
//				// actually add point:
//				drawnPoints.add(new java.awt.Point(invPt.x,invPt.y));			
//			}
			drawnPoints.add(new java.awt.Point(invPt.x,invPt.y));
		} else if (canvas.getMode() == CanvasMode.SPLIT_SHAPE_BY_HORIZONTAL_LINE) {
			splitShape(canvas.getFirstSelected(), -1, invPt.y, 1, invPt.y, true);
		} else if (canvas.getMode() == CanvasMode.SPLIT_SHAPE_BY_VERTICAL_LINE) {
			splitShape(canvas.getFirstSelected(), invPt.x, -1, invPt.x, 1, true);
		}
	}
	
	public void finishSplitByLine() {
		if (canvas.getMode() != CanvasMode.SPLIT_SHAPE_LINE)
			return;

//		Point invPt = canvas.inverseTransform(x, y);
//		drawnPoints.add(new java.awt.Point(invPt.x, invPt.y));
		
		if (drawnPoints.size() < 2)
			return;
		
		splitShape(canvas.getFirstSelected(), new CanvasPolyline(drawnPoints), true);
		drawnPoints.clear();
	}
	
	public List<ShapeEditOperation> splitShape(ICanvasShape shape, int x1, int y1, int x2, int y2, boolean addToUndoStack) {
		return splitShape(shape, new CanvasPolyline(new java.awt.Point(x1, y1), new java.awt.Point(x2, y2)), addToUndoStack);		
	}
	
	public List<ShapeEditOperation> splitShape(ICanvasShape shape, CanvasPolyline pl, boolean addToUndoStack) {
//		ICanvasShape selected = canvas.getFirstSelected();
		if (shape == null) {
			logger.warn("Cannot split - no shape selected!");
			return null;
		}
		
		List<ICanvasShape> children = shape.getChildren(true); // get all children (recursively)!
		List<ShapeEditOperation> splitOps = new ArrayList<>();
				
		ShapeEditOperation op = scene.splitShape(shape, pl, true, null, null, false);
		if (op!=null) {
			splitOps.add(op);
		}
		
		// Split all child shapes
		for (ICanvasShape child : children) {
			// Determine the parent shapes of the child shape that shall be splitted by iterating through the edit operations that were done so far
			ICanvasShape p1=null, p2=null;
			for (ShapeEditOperation opParent : splitOps) {
				if (opParent.getNewShapes().get(0).equals(child.getParent()) 
						|| opParent.getNewShapes().get(1).equals(child.getParent())) {
					p1 = opParent.getNewShapes().get(0);
					p2 = opParent.getNewShapes().get(1);
//					logger.debug("readjusting child elements - parents: "+p1+"/"+p2);
					break;
				}
			}
			
			// Try to split child shape using the parents that were just determined:
			logger.debug("p1 / p2 = "+p1+" / "+p2);
			ShapeEditOperation opChild = scene.splitShape(child, pl, true, p1, p2, true);
			if (opChild!=null) {
				splitOps.add(opChild);
			}
		}
		
		if (splitOps.isEmpty()) {
			logger.warn("Cannot split - no shapes actually splitted by line!");
			return null;
		}
		
		scene.notifyOnAfterShapeSplitted(op);
		
		if (addToUndoStack)
			addToUndoStack(splitOps);
		
		return splitOps;
	}

	/** Adds the given point as a new point while drawing a new shape */
	public void addPointForNewShape(int x, int y, boolean startNewShape) {
		if (startNewShape && !drawnPoints.isEmpty()) {
			finishCurrentShape(false);
		}
		
		logger.debug("shape to draw = "+shapeToDraw);
		
		Point invPt = canvas.inverseTransform(x, y);
		// cannot add more than one point to a rectangle -> second point will finish shape!
		if (shapeToDraw == CanvasShapeType.RECTANGLE && drawnPoints.size() == 1) {
			drawnPoints.add(new java.awt.Point(invPt.x,invPt.y));
			finishCurrentShape(true);
			return;
		}
		// finish polygon if clicked into the first point:
		else if (shapeToDraw == CanvasShapeType.POLYGON && drawnPoints.size()>=3) {
			if (drawnPoints.get(0).distance(invPt.x, invPt.y) < canvas.getSettings().getSelectedPointRadius()) {
				finishCurrentShape(true);
				return;
			}
		}
		// actually add point:
		drawnPoints.add(new java.awt.Point(invPt.x,invPt.y));
	}
	
	/** Finishes the currently drawn shape and adds it to the canvas */
	public void finishCurrentShape(boolean finishAll) {
		if (drawnPoints.isEmpty())
			return;
		
		// add current mouse point as second rectangle point if not explicitly drawn:
		if (shapeToDraw == CanvasShapeType.RECTANGLE && drawnPoints.size() == 1) {
			Point mPt = canvas.getMouseListener().getMousePt();
			Point invPt = canvas.inverseTransform(mPt.x, mPt.y); 
			drawnPoints.add(new java.awt.Point(invPt.x,invPt.y));
		}
		
		ICanvasShape newShape = constructShapeFromPoints(drawnPoints, shapeToDraw);
		if (newShape == null) {
			logger.error("Could not add new shape for points (maybe only one point drawn?): "+drawnPoints+" \nshapeToDraw = "+shapeToDraw);
			return;
		}
		else {
			drawnShapes.add(newShape);
			drawnPoints.clear();
		}
		
		if (finishAll) {
			postProcessDrawnShapes();
		}
	}
	
	private void postProcessDrawnShapes() {
		CanvasMode m = canvas.getSettings().getMode();
		if (m.isAddOperation()) {
			stopPostProcess=false;
			for (ICanvasShape s : drawnShapes) {
				addShapeToCanvas(s, true);
				if (stopPostProcess==true)
					break;
			}
		}
		
		clearDrawnShapes();
	}
	
	/** Clears the currently drawn shape */
	public void clearDrawnShapes() {
		logger.debug("clearing drawn shapes!");
		stopPostProcess=true;
		drawnShapes.clear();
		drawnPoints.clear();
		
		canvas.redraw();
	}
	
	protected ICanvasShape constructShapeFromPoints(List<java.awt.Point> pts, CanvasShapeType shapeType) {
		ICanvasShape newShape=null;
		if (shapeType == CanvasShapeType.POLYGON && pts.size()>=3) {
			newShape = new CanvasPolygon(pts);
		}
		else if (shapeType == CanvasShapeType.POLYLINE && pts.size() >= 2) {
			newShape = new CanvasPolyline(pts);
		}
		else if (shapeType == CanvasShapeType.RECTANGLE && pts.size() == 2) {
//			if (pts.size()==1) {
//				Point mPt = canvas.getMouseListener().getMousePt();
//				Point invPt = canvas.inverseTransform(mPt.x, mPt.y); 
//				pts.add(new java.awt.Point(invPt.x,invPt.y));
//			}
			
			// FIXME(?) add polygon even if rectangle is added --> for later editing!
			List<java.awt.Point> polyPts = new ArrayList<>();
			java.awt.Point p1 = pts.get(0);
			java.awt.Point p2 = pts.get(1);
			polyPts.add(p1);
			polyPts.add(new java.awt.Point(p2.x, p1.y));
			polyPts.add(p2);
			polyPts.add(new java.awt.Point(p1.x, p2.y));			
			newShape = new CanvasPolygon(polyPts);
			
			// the old shit: here, an actual rectangle is drawn:
//			try {
//				newShape = new CanvasRect(drawnPoints);
//			}
//			catch (Exception e) { // exception is thrown when drawnPoint does not contain exactly two point - should not happen though
//				newShape = null;
//				DialogUtil.showErrorMessageBox(canvas.getShell(), "Error creating rectangle", e.getMessage());
//			}
		}
		return newShape;
	}	
	
	public void removeAll() {
		logger.debug("removing all shapes: "+scene.getNSelected());
		removeShapesFromCanvas(new ArrayList<ICanvasShape>(scene.getShapes()), true);
		canvas.redraw();
	}
		
	public void removeSelected() {
		List<ICanvasShape> selected = scene.getSelectedAsNewArray();
		
		logger.debug("removing selected: "+selected.size()+ " / "+scene.nShapes());
		
		removeShapesFromCanvas(selected, true);
				
		canvas.redraw();
	}
	
	public void mergeSelected() {	
		List<ICanvasShape> selected = scene.getSelectedAsNewArray();
		logger.debug("merging selected: "+selected.size());
		if (selected.size() < 2)
			return;
		
		ShapeEditOperation op = scene.mergeSelected(true);
		if (op!=null) {
			addToUndoStack(op);	
		}
		
		
	}
	
	public void simplifySelected(double perc) {
		ShapeEditOperation op = new ShapeEditOperation(ShapeEditType.EDIT, "Polygon simplification", canvas.getScene().getSelectedAsNewArray());
		
		for (ICanvasShape s : op.getShapes()) {			
			double d = s.getPoint(0).distance(s.getPoint(2));
			double eps = (d * perc)/100.0d;
			logger.debug("simplifying selected with perc: "+perc+" diameter = "+d+ " eps = "+eps);
			s.simplify(eps);	
		}
		addToUndoStack(op);

		canvas.redraw();
	}	
	
	public void addToUndoStack(ShapeEditOperation op) {
		if (op != null)
			canvas.getUndoStack().addToUndoStack(op);
	}
	
	public void addToUndoStack(List<ShapeEditOperation> ops) {
		if (ops!=null && !ops.isEmpty())
			canvas.getUndoStack().addToUndoStack(ops);
	}	

	/** Sets the type of shape that is currently drawn */
	public void setShapeToDraw(CanvasShapeType shapeToDraw) { this.shapeToDraw = shapeToDraw; }
	/** Returns the type of shape that is currently drawn */
	public CanvasShapeType getShapeToDraw() { return shapeToDraw; }

//	public ArrayList<java.awt.Point> getDrawnPoints() { return drawnPoints; }
	
	// THOSE ARE THE ACTUAL EDIT OPERATIONS:
	
	public ShapeEditOperation addShapeToCanvas(ICanvasShape newShape, boolean addToUndoStack) {
		if (newShape == null)
			return null;
			
		newShape.setEditable(true);
		ShapeEditOperation op = scene.addShape(newShape, null, true);
		if (op!=null && addToUndoStack) {
			addToUndoStack(op);
		}
		return op;	
	}
	
	public ShapeEditOperation removeShapeFromCanvas(ICanvasShape shapesToRemove, boolean addToUndoStack) {
		List<ICanvasShape> sl = new ArrayList<>();
		sl.add(shapesToRemove);
		return removeShapesFromCanvas(sl, addToUndoStack);
	}

	public ShapeEditOperation removeShapesFromCanvas(List<ICanvasShape> shapesToRemove, boolean addToUndoStack) {
		if (shapesToRemove==null || shapesToRemove.isEmpty())
			return null;
		
		Collections.sort(shapesToRemove);			
		logger.debug("removing shapes: "+shapesToRemove.size());
		
		// remove shapes - add actually removed shapes to new list - it is possible that some shapes are not removed, if its parent element gets removed first!
		List<ICanvasShape> removedShapes = new ArrayList<ICanvasShape>();
		for (ICanvasShape s : shapesToRemove) {
			if (scene.removeShape(s, true, true)) {
				removedShapes.add(s);
			}
		}
		logger.debug("actually removed shapes: "+removedShapes.size());
		
		ShapeEditOperation op = new ShapeEditOperation(ShapeEditType.DELETE, removedShapes.size()+" shapes removed", removedShapes);
		
		if (addToUndoStack)
			addToUndoStack(op);
		
		return op;

	}
	
	protected ShapeEditOperation insertPointIntoShape(ICanvasShape shape, int x, int y) {
		if (shape != null) {
			ShapeEditOperation op = new ShapeEditOperation(ShapeEditType.EDIT, "Added point to shape", shape);
			shape.insertPoint(x, y);
			return op;
		} else
			return null;
	}

	public ShapeEditOperation addPointToShape(ICanvasShape shape, int mouseX, int mouseY, boolean addToUndoStack) {
		logger.debug("inserting point!");
		Point mousePtWoTr = canvas.inverseTransform(mouseX, mouseY);
		
		if (shape != null && shape.isEditable()) {
			ShapeEditOperation op = new ShapeEditOperation(ShapeEditType.EDIT, "Inserted point to shape", shape);
			if (addToUndoStack)
				addToUndoStack(op);
			
			int index = shape.insertPoint(mousePtWoTr.x, mousePtWoTr.y);
			op.data = index;
			
			return op;
		}
		return null;
	}
	
	public void removePointFromSelected(int pointIndex) {
		logger.debug("removing point "+pointIndex);
		ICanvasShape selected = canvas.getFirstSelected();
		if (selected!=null && selected.isEditable()) {
			
			ShapeEditOperation op = new ShapeEditOperation(ShapeEditType.EDIT, "Removed point from shape", selected);
			addToUndoStack(op);
			
			if (!selected.removePoint(pointIndex)) {
				logger.warn("Could not remove point "+pointIndex+" from shape!");
			}
		}
	}
	
	public static boolean isEditable(ICanvasShape s) {
		return s!=null && s.isEditable();
	}
	
	public ShapeEditOperation movePointAndSelected(ICanvasShape shape, int selectedPoint, int mouseX, int mouseY, boolean addToUndoStack) {
		if (!isEditable(shape))
			return null;
		
		ShapeEditOperation op = new ShapeEditOperation(ShapeEditType.EDIT, "Moved point(s) of shape", shape);
		
		logger.debug("moving points, shape: "+shape+" point: "+selectedPoint);
		Point mousePtWoTr = canvas.inverseTransform(mouseX, mouseY);
		List<Integer> movedPts = shape.movePointAndSelected(selectedPoint, mousePtWoTr.x, mousePtWoTr.y);
		if (movedPts.isEmpty())
			return null;
		
//		selected.movePoint(selectedPoint, mousePtWoTr.x, mousePtWoTr.y);

		if (addToUndoStack)
			addToUndoStack(op);
		
		return op;
	}
	
	public ShapeEditOperation translatePoints(ICanvasShape shape, int tx, int ty, boolean addToUndoStack, int ...indices) {
		if (shape == null)
			return null;
		
		ShapeEditOperation op = new ShapeEditOperation(ShapeEditType.EDIT, "Altered point(s) of shape", shape);
		for (int index : indices) {			
			if (!shape.translatePoint(index, tx, ty)) {
				return null;
			}
		}
		
		if (addToUndoStack)
			addToUndoStack(op);
		
		return op;
	}
	
//	public ShapeEditOperation translatePoints(ICanvasShape shape, int index, int tx, int ty, /*boolean firstMove,*/ boolean addToUndoStack) {
//		if (shape == null)
//			return null;
//						
//		ShapeEditOperation op = new ShapeEditOperation(ShapeEditType.EDIT, "Altered point(s) of shape", shape);
//		if (addToUndoStack)
//			addToUndoStack(op);
//		
//		if (!shape.translatePoint(index, tx, ty)) {
//			return null;
//		}
//		
//		return op;
//	}
	
	public void resizeBoundingBoxFromSelected(RectDirection direction, int mouseTrX, int mouseTrY, boolean firstMove) {
		ICanvasShape selected = canvas.getFirstSelected();
		
		if (selected!=null && selected.isEditable() && direction!=RectDirection.NONE) {
			// invert transform:
			Point transWoTr = canvas.invertTranslationTransform(mouseTrX, mouseTrY);
			
			logger.trace("moving bounding box trans: "+transWoTr.x+" x "+transWoTr.y);	
			if (firstMove) {
				currentMoveOp = new ShapeEditOperation(ShapeEditType.EDIT, "Moved bounding box of shape", selected);
				addToUndoStack(currentMoveOp);
			}
			
			// set points back if this is not the first move s.t. total translation can be applied:
			if (!firstMove && currentMoveOp!=null && currentMoveOp.getFirstBackupShape()!=null)
				selected.setPoints(currentMoveOp.getFirstBackupShape().getPoints());			
			
			selected.moveBoundingBox(direction, transWoTr.x, transWoTr.y);
		}
	}
	
	/** Translate the selection object by the given coordinates. The translation is always given as the \emph{total}
	 * translation for a current move operation so that rounding errors are minimized! 
	 * **/
	public ShapeEditOperation moveShape(ICanvasShape shape, int mouseTrX, int mouseTrY, ShapeEditOperation currentMoveOp, boolean addToUndoStack) {
//		ICanvasShape selected = canvas.getFirstSelected();
		if (shape == null)
			return null;

		// invert transform:
		java.awt.Point transWoTr = canvas.getPersistentTransform().inverseTransformWithoutTranslation(mouseTrX, mouseTrY);
		logger.trace("t = "+transWoTr);
				
		// if first move --> determine shapes to move
		if (currentMoveOp==null) {
			List<ICanvasShape> shapesToMove = new ArrayList<>();
			shapesToMove.add(shape);
			// move subshapes if required key down:
			if (CanvasKeys.isKeyDown(canvas.getKeyListener().getCurrentStateMask(), CanvasKeys.MOVE_SUBSHAPES_REQUIRED_KEY)) {
				logger.debug("moving subshapes!");
				shapesToMove.addAll(shape.getChildren(true));
			}
			currentMoveOp = new ShapeEditOperation(ShapeEditType.EDIT, "Moved shape(s)", shapesToMove);
			if (addToUndoStack) {
				addToUndoStack(currentMoveOp);
			}
		}
		
		// now move all shapes for the current move operation:
//		boolean movedFirst = true;
		for (int i=0; i<currentMoveOp.getShapes().size(); ++i) {
			ICanvasShape s = currentMoveOp.getShapes().get(i);
			
			// reset points if this isnt the first move (translation is always specified global for one move to prevent rounding errors!)
//			if (!firstMove) {
				ICanvasShape bs = currentMoveOp.getBackupShapes().get(i);
				s.setPoints(bs.getPoints());
//			}
						
			boolean moved = scene.moveShape(s, transWoTr.x, transWoTr.y, true); 
			
			if (i == 0 && !moved) { // if first shape (i.e. parent shape) was not moved, jump out
				return null;
//				movedFirst = false;
//				break;
			}
		}
		
//		if (addToUndoStack /*&& movedFirst*/ && firstMove && currentMoveOp!=null) {
//			addToUndoStack(currentMoveOp);
//		}
		
		return currentMoveOp;
	}

	public SWTCanvas getCanvas() {
		return canvas;
	}

	public List<ICanvasShape> getDrawnShapes() {
		return drawnShapes;
	}

	public List<java.awt.Point> getDrawnPoints() {
		return drawnPoints;
	}
	
//	public List<java.awt.Point> getDrawnPointsExtended() {
//		final double extensionFactor = 1000;
//		
//		List<java.awt.Point> extendedPts = new ArrayList<>();
//		
//		if (drawnPoints.size() >= 2) {
//			java.awt.Point p1 = drawnPoints.get(1);
//			java.awt.Point p2 = drawnPoints.get(0);
//			Vector2D v1 = new Vector2D(p2.x - p1.x, p2.y - p1.y);
//			v1 = v1.normalize();
//			Vector2D newFirstPt = new Vector2D(p2.x, p2.y).plus(v1.times(extensionFactor));
//			extendedPts.add(new java.awt.Point((int)newFirstPt.x(), (int)newFirstPt.y()));
//		}
//		
//		for (java.awt.Point p : drawnPoints) {
//			extendedPts.add(new java.awt.Point(p.x, p.y));
//		}
//		
//		if (drawnPoints.size() >= 2) {
//			java.awt.Point p1 = drawnPoints.get(drawnPoints.size()-2);
//			java.awt.Point p2 = drawnPoints.get(drawnPoints.size()-1);
//			Vector2D v1 = new Vector2D(p2.x - p1.x, p2.y - p1.y);
//			v1 = v1.normalize();
//			Vector2D newFirstPt = new Vector2D(p2.x, p2.y).plus(v1.times(extensionFactor));
//			extendedPts.add(new java.awt.Point((int)newFirstPt.x(), (int)newFirstPt.y()));
//		}
//		
//		return extendedPts;
//	}

	public ShapeEditOperation getCurrentMoveOp() {
		return currentMoveOp;
	}

	public ICanvasShape getBackupShape() {
		return backupShape;
	}	
	




	
	

}

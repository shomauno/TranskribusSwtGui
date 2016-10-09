package eu.transkribus.swt_gui.pagination_tables;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.ws.rs.ClientErrorException;
import javax.ws.rs.ServerErrorException;
import javax.ws.rs.client.InvocationCallback;

import org.apache.commons.beanutils.BeanUtils;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.nebula.widgets.pagination.collections.PageResultLoaderList;
import org.eclipse.nebula.widgets.pagination.table.SortTableColumnSelectionListener;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.transkribus.client.util.SessionExpiredException;
import eu.transkribus.core.model.beans.TrpCollection;
import eu.transkribus.core.model.beans.TrpDocMetadata;
import eu.transkribus.swt.pagination_table.ATableWidgetPagination;
import eu.transkribus.swt.pagination_table.IPageLoadMethods;
import eu.transkribus.swt.pagination_table.RemotePageLoader;
import eu.transkribus.swt.util.DialogUtil;
import eu.transkribus.swt.util.Fonts;
import eu.transkribus.swt.util.TableViewerUtils;
import eu.transkribus.swt_gui.mainwidget.Storage;
import eu.transkribus.swt_gui.mainwidget.TrpMainWidget;

public class DocTableWidgetPagination extends ATableWidgetPagination<TrpDocMetadata> {
	private final static Logger logger = LoggerFactory.getLogger(DocTableWidgetPagination.class);
	
	public static final String DOC_NR_COL = "NR";
	public static final String DOC_ID_COL = "ID";
	public static final String DOCS_TITLE_COL = "Title";
	public static final String DOC_NPAGES_COL = "N-Pages";
	public static final String DOC_UPLOADER_COL = "Uploader";
	public static final String DOC_UPLOADED_COL = "Uploaded";
	public static final String DOC_COLLECTIONS_COL = "Collections";
	
	public static final boolean LOAD_ALL_DOCS_ONCE = true;
	
//	public static final ColumnConfig[] DOCS_COLS = new ColumnConfig[] {
//		new ColumnConfig(DOC_NR_COL, 50, false, DefaultTableColumnViewerSorter.ASC),
//		new ColumnConfig(DOC_ID_COL, 65, false, DefaultTableColumnViewerSorter.ASC),
//		new ColumnConfig(DOCS_TITLE_COL, 100, false, DefaultTableColumnViewerSorter.ASC),
//		new ColumnConfig(DOC_NPAGES_COL, 50, false, DefaultTableColumnViewerSorter.ASC),
//		new ColumnConfig(DOC_OWNER_COL, 50, false, DefaultTableColumnViewerSorter.ASC),
//		new ColumnConfig(DOC_COLLECTIONS_COL, 200, false, DefaultTableColumnViewerSorter.ASC),
//	};

	protected int collectionId=Integer.MIN_VALUE;
//	boolean reloadDocs=false;
	
	List<TrpDocMetadata> docs = new ArrayList<>();
	PageResultLoaderList<TrpDocMetadata> listLoader;
	static final boolean USE_LIST_LOADER = true;
	
	public DocTableWidgetPagination(Composite parent, int style, int initialPageSize) {
		super(parent, style, initialPageSize);
	}	
	
	public DocTableWidgetPagination(Composite parent, int style, int initialPageSize, IPageLoadMethods<TrpDocMetadata> methods) {
		super(parent, style, initialPageSize, methods);
	}
	
	private void setCollectionId(int collectionId) {
//		if (collectionId != this.collectionId)
//			reloadDocs=true;
			
		this.collectionId = collectionId;
	}
	
	public void refreshList(int collectionId, boolean resetPage) {
		logger.debug("old coll-id: "+this.collectionId+" new coll-id: "+collectionId);
		
		boolean hasChanged = this.collectionId != collectionId;
		setCollectionId(collectionId);
		
		logger.debug("refreshing doc table, collectionId="+collectionId+" resetPage="+resetPage+" hasChanged="+hasChanged);
		if (hasChanged) {
			logger.debug("reloading docs from server...");
			reloadDocs(resetPage);
		} else {
			refreshPage(resetPage);
		}
	}
	
	private void setDocList(List<TrpDocMetadata> newDocs, boolean resetPage) {
		synchronized (this) {
			this.docs = new ArrayList<>();
			this.docs.addAll(newDocs);
			
//			logger.debug("adding newDocs: "+newDocs.size());
//			
//			this.docs.clear();
//			this.docs.addAll(newDocs);
			
			logger.debug("size after: "+this.docs.size());

			Display.getDefault().asyncExec(() -> {
				listLoader.setItems(docs);
				refreshPage(resetPage);	
			});
		}
	}
	
	private void reloadDocs(boolean resetPage) {
		Storage store = Storage.getInstance();

		if (collectionId != store.getCollId()) { // have to reload doclist
			store.getConnection().getAllDocsAsync(collectionId, 0, 0, null, null, new InvocationCallback<List<TrpDocMetadata>>() {
				@Override public void failed(Throwable throwable) {
					DialogUtil.showBallonToolTip(DocTableWidgetPagination.this, SWT.ICON_ERROR, "Error loading documents", throwable.getMessage());
					logger.error(throwable.getMessage(), throwable);
				}
				
				@Override public void completed(List<TrpDocMetadata> response) {
					logger.debug("loaded docs from server: "+response.size());
					setDocList(response, resetPage);
				}
			});
		} else {
			logger.debug("setting docs from storage: "+store.getDocList().size());
			setDocList(store.getDocList(), resetPage);
		}

//		new Thread() {
//			public void run() {
//				
//				DocTableWidgetPagination me = DocTableWidgetPagination.this;
//				
//				List<TrpDocMetadata> newDocs=store.getDocList();
//								
//				if (me.collectionId != store.getCollId()) { // have to reload doclist
//					try {
//						newDocs = store.getConnection().getAllDocs(collectionId, 0, 0, null, null);
//					} catch (SessionExpiredException | ServerErrorException | ClientErrorException | IllegalArgumentException e) {
//						DialogUtil.showBallonToolTip(me, SWT.ICON_ERROR, "Error loading documents", e.getMessage());
//						logger.error(e.getMessage(), e);
//					}
//				} else {
//					newDocs = store.getDocList();
//				}
//				
//				synchronized (me) {
//					docs.clear();
//					docs.addAll(newDocs);
//				
//					refreshPage(resetPage);
//				}
//			}
//		}.start();
	}
	
//	public void refreshList(List<TrpDocMetadata> docs, boolean resetPage) {
//		
//		if (getPageableTable().getPageLoader() instanceof PageResultLoaderList) {
//			PageResultLoaderList<TrpDocMetadata> pl = (PageResultLoaderList<TrpDocMetadata>) getPageableTable().getPageLoader();
//			pl.setItems(docs);
//		}
//		
//		if (USE_LIST_LOADER && listLoader!=null) {
//			listLoader.setItems(docs);
//		}
//		
//		refreshPage(true);
//		
//	}
	
	protected void createColumns() {
		// generic label provider constructed with the bean property used for this column
		class DocTableColumnLabelProvider extends ColumnLabelProvider {
			String colName;
			Font boldFont = Fonts.createBoldFont(tv.getControl().getFont());
			
			public DocTableColumnLabelProvider(String colName) {
				this.colName = colName;
			}
			
			@Override
			public String getToolTipText(Object element) {
				
				TrpDocMetadata docMd = (TrpDocMetadata) element;
				return "ID=" + docMd.getDocId() + " / Title=" + docMd.getTitle() + " / N-Pages=" + docMd.getNrOfPages() + " / Uploader=" + docMd.getUploader() + " / Uploaded=" + docMd.getUploadTime().toString() + " / Collections=" + docMd.getColString();
			}
			
            @Override public String getText(Object element) {
            	try {
					return BeanUtils.getSimpleProperty(element, colName);
				} catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
					return "i am error";
				}
            }
            
        	@Override public Font getFont(Object element) {
        		if (element instanceof TrpDocMetadata) {
        			TrpDocMetadata md = (TrpDocMetadata) element;
        			if (md.getDocId() == Storage.getInstance().getDocId())
        				return boldFont;
        		}
        		
        		return null;
        	}
		}
		
		TableViewerColumn col = TableViewerUtils.createTableViewerColumn(tv, 0, DOC_ID_COL, 50);
		col.setLabelProvider(new DocTableColumnLabelProvider("docId"));
		col.getColumn().addSelectionListener(new SortTableColumnSelectionListener("docId"));
		
		col = TableViewerUtils.createTableViewerColumn(tv, 0, DOCS_TITLE_COL, 150);
		col.setLabelProvider(new DocTableColumnLabelProvider("title"));
		col.getColumn().addSelectionListener(new SortTableColumnSelectionListener("title"));
		
		col = TableViewerUtils.createTableViewerColumn(tv, 0, DOC_NPAGES_COL, 100);
		col.setLabelProvider(new DocTableColumnLabelProvider("nrOfPages"));
//		col.getColumn().addSelectionListener(new SortTableColumnSelectionListener("nrOfPages"));

		col = TableViewerUtils.createTableViewerColumn(tv, 0, DOC_UPLOADER_COL, 100);
		col.setLabelProvider(new DocTableColumnLabelProvider("uploader"));
		col.getColumn().addSelectionListener(new SortTableColumnSelectionListener("uploader"));
		
		col = TableViewerUtils.createTableViewerColumn(tv, 0, DOC_UPLOADED_COL, 100);
		col.setLabelProvider(new DocTableColumnLabelProvider("uploadTime"));
		col.getColumn().addSelectionListener(new SortTableColumnSelectionListener("uploadTimestamp"));		
		
		col = TableViewerUtils.createTableViewerColumn(tv, 0, DOC_COLLECTIONS_COL, 100);	
		col.setLabelProvider(new DocTableColumnLabelProvider("colString"));
//		col.getColumn().addSelectionListener(new SortTableColumnSelectionListener("colString"));

	}

	@Override protected void setPageLoader() {
		if (USE_LIST_LOADER && methods==null) {
			listLoader = new PageResultLoaderList<TrpDocMetadata>(docs);
			pageableTable.setPageLoader(listLoader);
		} else {
			if (methods == null) { // if not set from outside -> set default one that loads docs from current collection!
				methods = new IPageLoadMethods<TrpDocMetadata>() {
					Storage store = Storage.getInstance();
		
					@Override public int loadTotalSize() {
						
						if (!store.isLoggedIn() || collectionId <= 0)
							return 0;
						
						int totalSize = 0;
						try {
							totalSize = store.getConnection().countDocs(collectionId);
						} catch (SessionExpiredException | ServerErrorException | IllegalArgumentException e) {
							TrpMainWidget.getInstance().onError("Error loading documents", e.getMessage(), e);
						}
						return totalSize;
					}
		
					@Override public List<TrpDocMetadata> loadPage(int fromIndex, int toIndex, String sortPropertyName, String sortDirection) {
						if (!store.isLoggedIn() || collectionId <= 0)
							return new ArrayList<>();
						
						List<TrpDocMetadata> docs = new ArrayList<>();
						try {
							logger.debug("loading docs, sortDirection = "+sortDirection+" collectionId = "+collectionId+" fromIndex = "+fromIndex+" toIndex = "+toIndex);
							docs = store.getConnection().getAllDocs(collectionId, fromIndex, toIndex-fromIndex, sortPropertyName, sortDirection);
						} catch (SessionExpiredException | ServerErrorException | IllegalArgumentException e) {
							TrpMainWidget.getInstance().onError("Error loading documents", e.getMessage(), e);
						}
						return docs;
					}
				};
			}
			
			RemotePageLoader<TrpDocMetadata> pl = new RemotePageLoader<>(pageableTable.getController(), methods);
			pageableTable.setPageLoader(pl);
		}
	} // end setPageLoader()



}

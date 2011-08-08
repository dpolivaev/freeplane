package org.docear.plugin.pdfutilities.pdf;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.docear.plugin.pdfutilities.util.Tools;
import org.freeplane.core.util.LogUtils;

import de.intarsys.pdf.cds.CDSNameTreeEntry;
import de.intarsys.pdf.cds.CDSNameTreeNode;
import de.intarsys.pdf.cds.CDSRectangle;
import de.intarsys.pdf.cos.COSArray;
import de.intarsys.pdf.cos.COSCatalog;
import de.intarsys.pdf.cos.COSDictionary;
import de.intarsys.pdf.cos.COSName;
import de.intarsys.pdf.cos.COSNull;
import de.intarsys.pdf.cos.COSObject;
import de.intarsys.pdf.cos.COSRuntimeException;
import de.intarsys.pdf.cos.COSString;
import de.intarsys.pdf.parser.COSLoadException;
import de.intarsys.pdf.pd.PDAnnotation;
import de.intarsys.pdf.pd.PDAnyAnnotation;
import de.intarsys.pdf.pd.PDDocument;
import de.intarsys.pdf.pd.PDExplicitDestination;
import de.intarsys.pdf.pd.PDHighlightAnnotation;
import de.intarsys.pdf.pd.PDOutline;
import de.intarsys.pdf.pd.PDOutlineItem;
import de.intarsys.pdf.pd.PDOutlineNode;
import de.intarsys.pdf.pd.PDPage;
import de.intarsys.pdf.pd.PDTextAnnotation;
import de.intarsys.pdf.pd.PDTextMarkupAnnotation;
import de.intarsys.tools.locator.FileLocator;

public class PdfAnnotationImporter {
	
	private File currentFile;
	private boolean importAll = false;
	
	
	public Map<File, List<PdfAnnotation>> importAnnotations(List<File> files) throws IOException, COSLoadException, COSRuntimeException{
		Map<File, List<PdfAnnotation>> annotationMap = new HashMap<File, List<PdfAnnotation>>();
		
		for(File file : files){
			annotationMap.put(file, this.importAnnotations(file));
		}
		
		return annotationMap;
	}
	
	public List<PdfAnnotation> importAnnotations(URI uri) throws IOException, COSLoadException{
		File file = Tools.getFilefromUri(uri);
		if(file == null){
			return new ArrayList<PdfAnnotation>();
		}
		else{
			return this.importAnnotations(file);
		}
	}
	
	public List<PdfAnnotation> importAnnotations(File file) throws IOException, COSLoadException{
		List<PdfAnnotation> annotations = new ArrayList<PdfAnnotation>();
		
		this.currentFile = file;
		PDDocument document = getPDDocument(file);
		if(document == null){
			return annotations;
		}
		try{
			annotations.addAll(this.importAnnotations(document));					
			annotations.addAll(this.importBookmarks(document.getOutline()));
			
		} catch(ClassCastException e){			
			PDOutlineItem outline = (PDOutlineItem)PDOutline.META.createFromCos(document.getCatalog().cosGetOutline());
			annotations.addAll(this.importBookmarks(outline));			
		} finally {
			if(document != null)
			document.close();
		}
        
		return annotations;
	}

	private PDDocument getPDDocument(File file) throws IOException,	COSLoadException, COSRuntimeException {
		if(file == null || !file.exists() || !new PdfFileFilter().accept(file)){
			return null;
		}
		FileLocator locator = new FileLocator(file.getPath());
		PDDocument document = PDDocument.createFromLocator(locator);
		return document;
	}
	
	private List<PdfAnnotation> importBookmarks(PDOutlineNode parent) throws IOException, COSLoadException, COSRuntimeException{
		List<PdfAnnotation> annotations = new ArrayList<PdfAnnotation>();
		/*if(!ResourceController.getResourceController().getBooleanProperty(PdfUtilitiesController.IMPORT_BOOKMARKS_KEY)){
			return annotations;
		}*/
		if(parent == null) return annotations;
		@SuppressWarnings("unchecked")
		List<PDOutlineItem> children = parent.getChildren();
		for(PDOutlineItem child : children){
			PdfAnnotation annotation = new PdfAnnotation();
			annotation.setFile(currentFile);
			annotation.setTitle(child.getTitle());			
			annotation.setAnnotationType(getAnnotationType(child));
			annotation.getChildren().addAll(this.importBookmarks(child));
			
			switch(annotation.getAnnotationType()){
				
				case PdfAnnotation.BOOKMARK_WITH_URI:
					annotation.setDestinationUri(this.getAnnotationDestinationUri(child));
					break;
				case PdfAnnotation.BOOKMARK:
					annotation.setPage(this.getAnnotationDestinationPage(child));
					break;
			}	
			
			annotations.add(annotation);
		}
		
		return annotations;
	}
	
	private URI getAnnotationDestinationUri(PDOutlineItem bookmark) {
		if(bookmark != null && !(bookmark.cosGetField(PDOutlineItem.DK_A) instanceof COSNull)){
			COSDictionary cosDictionary = (COSDictionary)bookmark.cosGetField(PDOutlineItem.DK_A);
			if(!(cosDictionary.get(COSName.create("URI")) instanceof COSNull)){
				COSObject destination = cosDictionary.get(COSName.create("URI"));
		        if(destination instanceof COSString && destination.getValueString(null) != null && destination.getValueString(null).length() > 0){
		        	try {
						return new URI(destination.getValueString(null));						
					} catch (URISyntaxException e) {
						LogUtils.warn("Bookmark Destination Uri Syntax incorrect.", e);
					}
		        }
			}            
		}		
		return null;
	}

	private int getAnnotationType(PDOutlineItem bookmark) {
		if(bookmark != null && (bookmark.cosGetField(PDOutlineItem.DK_A) instanceof COSNull)){
			return PdfAnnotation.BOOKMARK_WITHOUT_DESTINATION;
		}
		if(bookmark != null && !(bookmark.cosGetField(PDOutlineItem.DK_A) instanceof COSNull)){
			COSDictionary cosDictionary = (COSDictionary)bookmark.cosGetField(PDOutlineItem.DK_A);
			if(!(cosDictionary.get(COSName.create("URI")) instanceof COSNull)){
				return PdfAnnotation.BOOKMARK_WITH_URI;
			}            
		}
		return PdfAnnotation.BOOKMARK;
	}

	private List<PdfAnnotation> importAnnotations(PDDocument document){
		List<PdfAnnotation> annotations = new ArrayList<PdfAnnotation>();
		boolean importComments = true;//ResourceController.getResourceController().getBooleanProperty(PdfUtilitiesController.IMPORT_COMMENTS_KEY);
		boolean importHighlightedTexts = true;//ResourceController.getResourceController().getBooleanProperty(PdfUtilitiesController.IMPORT_HIGHLIGHTED_TEXTS_KEY);
		String lastString = "";
		
		@SuppressWarnings("unchecked")
		List<PDAnnotation> pdAnnotations = document.getAnnotations();
		for(PDAnnotation annotation : pdAnnotations){
			// Avoid empty entries
            if(annotation.getContents().equals("")) continue;
            // Avoid double entries (Foxit Reader)
            if(annotation.getContents().equals(lastString)) continue;
            lastString = annotation.getContents();
            // Sometimes page = NULL though this is a proper annotation
            if(annotation.getPage() != null)
            {
                // Avoid entries outside the page
                if(annotation.getPage().getMediaBox() == null || annotation.getRectangle() == null) continue;
                CDSRectangle page_rec = annotation.getPage().getMediaBox();
                CDSRectangle anno_rec = annotation.getRectangle();
                if(anno_rec.getLowerLeftX() > page_rec.getUpperRightX() ||
                   anno_rec.getLowerLeftY() > page_rec.getUpperRightY() ||
                   anno_rec.getUpperRightX() < page_rec.getLowerLeftX() ||
                   anno_rec.getUpperRightY() < page_rec.getLowerLeftY())  continue;
            }
            if((annotation.getClass() == PDAnyAnnotation.class || annotation.getClass() == PDTextAnnotation.class) && (importComments || this.importAll)){
            	PdfAnnotation pdfAnnotation = new PdfAnnotation();
            	pdfAnnotation.setFile(currentFile);
            	pdfAnnotation.setTitle(annotation.getContents());            	
            	pdfAnnotation.setAnnotationType(PdfAnnotation.COMMENT);
            	pdfAnnotation.setPage(this.getAnnotationDestination(annotation));
    			annotations.add(pdfAnnotation);
            }
            if((annotation.getClass() == PDTextMarkupAnnotation.class || annotation.getClass() == PDHighlightAnnotation.class) && (importHighlightedTexts || this.importAll)){
            	PdfAnnotation pdfAnnotation = new PdfAnnotation();
            	pdfAnnotation.setFile(currentFile);
            	pdfAnnotation.setTitle(annotation.getContents());           	
            	pdfAnnotation.setAnnotationType(PdfAnnotation.HIGHLIGHTED_TEXT); 
            	pdfAnnotation.setPage(this.getAnnotationDestination(annotation));
    			annotations.add(pdfAnnotation);
            }
		}
		
		return annotations;
	}	
	
	public Integer getAnnotationDestination(PDAnnotation pdAnnotation) {				
		
		if(pdAnnotation != null){
			PDPage page = pdAnnotation.getPage();			
			if(page != null)
				return page.getNodeIndex()+1;
		}
		
		return null;		
	}

	public Integer getAnnotationDestinationPage(PDOutlineItem bookmark) throws IOException, COSLoadException {
		
		PDDocument document = bookmark.getDoc();
		if(document == null || bookmark == null){
			return null;
		}		
		
		if(bookmark != null && bookmark.getDestination() != null){
            PDExplicitDestination destination = bookmark.getDestination().getResolvedDestination(document);
            if(destination != null){
                PDPage page = destination.getPage(document);
                return page.getNodeIndex()+1;
            }
        }
        if(bookmark != null && !(bookmark.cosGetField(PDOutlineItem.DK_A) instanceof COSNull)){        	
            
            COSDictionary cosDictionary = (COSDictionary)bookmark.cosGetField(PDOutlineItem.DK_A);            
            COSArray destination = getCOSArrayFromDestination(cosDictionary);
                        
            return getPageFromCOSArray(document, (COSArray)destination);           
        }
        
        return null;
	}

	private COSArray getCOSArrayFromDestination(COSDictionary cosDictionary) {
		COSObject cosObject = cosDictionary.get(COSName.create("D"));
		if(cosObject instanceof COSArray){
			return (COSArray)cosObject;
		}
		if(cosObject instanceof COSString){
			String destinationName = cosObject.getValueString(null);
			if(destinationName == null || destinationName.length() <= 0){
				return null;
			}
				
        	COSDictionary dests = cosDictionary.getDoc().getCatalog().cosGetDests();
    		if (dests != null) {
    			for (Iterator<?> i = dests.keySet().iterator(); i.hasNext();) {
    				COSName key = (COSName) i.next();
    				if(key.stringValue().equals(destinationName)){
    					cosDictionary = (COSDictionary)dests.get(key);
    					cosObject = cosDictionary.get(COSName.create("D"));
    					if(cosObject instanceof COSArray){
    						return (COSArray)cosObject;
    					}
    				}
    			}
    		}
    		
    		COSDictionary names = cosDictionary.getDoc().getCatalog().cosGetNames();
    		if (names != null) {
    			COSDictionary destsDict = names.get(COSCatalog.DK_Dests).asDictionary();
    			if (destsDict != null) {
    				CDSNameTreeNode destsTree = CDSNameTreeNode.createFromCos(destsDict);
    				for (Iterator<?> i = destsTree.iterator(); i.hasNext();) {
    					CDSNameTreeEntry entry = (CDSNameTreeEntry) i.next();        					
    					if(entry.getName().stringValue().equals(destinationName)){
    						cosDictionary = (COSDictionary)entry.getValue();
    						cosObject = cosDictionary.get(COSName.create("D"));
        					if(cosObject instanceof COSArray){
        						return (COSArray)cosObject;
        					}       					
        				}
    				}
    			}
    		}
    		
    		
		}
		return null;
	}

	private Integer getPageFromCOSArray(PDDocument document, COSArray destination) {
		
		Iterator<?> it = destination.iterator();
	    while(it.hasNext()){
	         COSObject o = (COSObject)it.next();
	         if(o.isIndirect()){  //the page is indirect referenced
	             o.dereference();
	         }
	         PDPage page = document.getPageTree().getFirstPage();
	         while(page != null){
	             if(page.cosGetObject().equals(o)){
	                 return page.getNodeIndex() + 1;
	             }
	             page = page.getNextPage();
	         }	                 
	    }
	    return null;
	}	

	public PdfAnnotation searchAnnotation(File file, String annotationTitle) throws IOException, COSLoadException, COSRuntimeException {
		this.currentFile = file;
		if(!this.isImportAll()) this.setImportAll(true);
		List<PdfAnnotation> annotations = this.importAnnotations(file);
		this.setImportAll(false);
		return searchAnnotation(annotations, annotationTitle);        
   }
	
	public PdfAnnotation searchAnnotation(List<PdfAnnotation> annotations, String annotationTitle) {
		for(PdfAnnotation annotation : annotations){
           String title = annotation.getTitle();
           System.out.println(title);
           if(annotation.getTitle().equals(annotationTitle)){
               return annotation;
           }
           else{
        	   PdfAnnotation searchResult = searchAnnotation(annotation.getChildren(), annotationTitle);
               if(searchResult != null) return searchResult;
           }
       }
		return null;
	}	

	public boolean isImportAll() {
		return importAll;
	}

	public void setImportAll(boolean importAll) {
		this.importAll = importAll;
	}

}
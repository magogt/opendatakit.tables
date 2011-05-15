package yoonsung.odk.spreadsheet.Activity;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.nio.channels.FileChannel;

import yoonsung.odk.spreadsheet.Database.TableList;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

public class SpreadSheet extends TableActivity {
	
	// options menu IDs
	private static final int OPEN_TABLE_MANAGER = 0;
	private static final int OPEN_COLUMN_MANAGER = 1;
	private static final int OPEN_SECURITY_MANAGER = 2;
	private static final int GRAPH = 3;
	private static final int OPEN_DEFOPTS_MANAGER = 4;
	private static final int IMPORTEXPORT = 5;
	// context menu IDs
	private static final int SELECT_COLUMN = 6;
	private static final int SEND_SMS_ROW = 7;
	private static final int HISTORY_IN = 8;
	private static final int DELETE_ROW = 9;
	private static final int SET_COL_AS_PRIME = 10;
	private static final int UNSET_COL_AS_PRIME = 11;
	private static final int SET_COL_AS_ORDERBY = 12;
	private static final int UNSET_COL_AS_ORDERBY = 13;
	private static final int OPEN_COL_OPTS = 14;
	private static final int SET_COL_WIDTH = 15;
	private static final int SET_FOOTER_OPT = 16;
	private static final int UNSELECT_COLUMN = 17;
	private static final int OPEN_FILE = 18;
	
	// context menu creation listeners
	private View.OnCreateContextMenuListener regularOccmListener;
	private View.OnCreateContextMenuListener headerOccmListener;
	private View.OnCreateContextMenuListener footerOccmListener;
	private View.OnCreateContextMenuListener indexedColOccmListener;
	
	private int lastHeaderMenued; // the ID of the last header cell that a
	                              // context menu was created for
	private int lastFooterMenued; // the ID of the last footer cell that a
	                              // context menu was created for
	
	/**
	 * Called when the activity is first created.
	 * @param savedInstanceState the data most recently saved if the activity
	 * is being re-initialized; otherwise null
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		prepOccmListeners();
		super.onCreate(savedInstanceState);
		lastHeaderMenued = -1;
	}
	
	/**
	 * Initializes the contents of the standard options menu.
	 * @param menu the options menu to place items in
	 * @return true if the menu is to be displayed; false otherwise
	 */
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		int none = Menu.NONE;
		menu.add(none, OPEN_TABLE_MANAGER, none, "Table Manager");
		menu.add(none, OPEN_COLUMN_MANAGER, none, "Column Manager");
		menu.add(none, OPEN_SECURITY_MANAGER, none, "Security Manager");
		menu.add(none, GRAPH, none, "Graph");
		menu.add(none, OPEN_DEFOPTS_MANAGER, none, "Defaults Manager");
		menu.add(none, IMPORTEXPORT, none, "Import/Export");
		return true;
	}
	
	/**
	 * Called when an item in the options menu is selected.
	 * @param featureId the panel that the menu is in
	 * @param item the menu item that was selected.
	 * @return true to finish processing of selection; false to perform normal
	 * menu handling
	 */
	@Override
	public boolean onMenuItemSelected(int featureId, MenuItem item) {
		switch(item.getItemId()) {
		case OPEN_TABLE_MANAGER:
			openTableManager();
			return true;
		case OPEN_COLUMN_MANAGER:
			openColumnManager();
			return true;
		case OPEN_SECURITY_MANAGER:
			openSecurityManager();
			return true;
		case GRAPH:
			openGraph();
			return true;
		case OPEN_DEFOPTS_MANAGER:
			openDefOptsManager();
			return true;
		case IMPORTEXPORT:
			openImportExportScreen();
			return true;
		default:
			return super.onMenuItemSelected(featureId, item);
		}
	}
	
	/**
	 * Called when an item in a context menu is selected.
	 * @param item the context menu item that was selected
	 * @return false to allow context menu processing to proceed; true to
	 * consume it
	 */
	@Override
	public boolean onContextItemSelected(MenuItem item) {
		Log.d("REFACTOR SSJ", "onContextItemSelected called:" + item.getItemId());
		switch(item.getItemId()) {
		case OPEN_FILE: // open a file for file type cell
			String path = table.getCellValue(selectedCellID);
			handleOpenFile(path);
			return true;
		case SELECT_COLUMN: // index on this column
			indexTableView(selectedCellID % table.getWidth());
			return true;
		case UNSELECT_COLUMN:
		    indexTableView(-1);
		    return true;
		case SEND_SMS_ROW: // sends an SMS on the selected row
			sendSMSRow();
			return true;
		case HISTORY_IN: // view a collection
			viewCollection(table.getTableRowID(
					selectedCellID / table.getWidth()));
			return true;
		case DELETE_ROW: // delete a row
			deleteRow(table.getRowNum(selectedCellID));
			return true;
		case SET_COL_AS_PRIME: // set a column to be a prime column
			setAsPrimeCol(table.getColName(lastHeaderMenued));
			return true;
		case UNSET_COL_AS_PRIME: // set a column to be a non-prime column
			unsetAsPrimeCol(table.getColName(lastHeaderMenued));
			return true;
		case SET_COL_AS_ORDERBY: // set a column to be the sort column
			setAsSortCol(table.getColName(lastHeaderMenued));
			return true;
		case UNSET_COL_AS_ORDERBY:
            setAsSortCol(null);
		    return true;
		case OPEN_COL_OPTS:
			openColPropsManager(table.getColName(lastHeaderMenued));
			return true;
		case SET_COL_WIDTH:
			openColWidthDialog(table.getColName(lastHeaderMenued));
			return true;
		case SET_FOOTER_OPT:
			openFooterOptDialog(table.getColName(lastFooterMenued));
			return true;
		default:
			return super.onContextItemSelected(item);
		}
	}
	
	/**
	 * Opens up Security Manager. Available to this activity only.
	 */
	private void openSecurityManager() {
		Intent i = new Intent(this, SecurityManager.class);
		i.putExtra("tableName", (new TableList()).getTableName(this.tableID));
		startActivity(i);
	}
	
	/**
	 * Handle the open file request. Availabe to this activity only.
	 */
	private void handleOpenFile(String path) {
		// Open up program
		Intent i = new Intent("org.odk.collect.android.action.FormEntry");

		// formPath is the path of the xform
		i.putExtra("formpath", path);

		// instancepath is the path of result
		String instancePath = path.replace(".xml", "_Instance.xml");
		try {
			File formF = new File(path);
			File instanceF = new File(instancePath);
			if (!instanceF.exists()) {
				FileChannel src = new FileInputStream(formF).getChannel();
				FileChannel dst = new FileOutputStream(instanceF).getChannel();
				dst.transferFrom(src, 0, src.size());
				src.close();
				dst.close();
			}
		} catch (Exception e) {}
		
		i.putExtra("instancepath", "/sdcard/TestForm_Instance.xml");

		//startActivityForResult(i, 1);
		startActivity(i);
	}
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
	    if (requestCode == 1) {
	        if (resultCode == RESULT_OK) {
	            // todo
	        	Log.e("result back", data.toString());
	        }
	    }
	}
	
	/**
	 * Prepares the context menu creation listeners.
	 */
	private void prepOccmListeners() {
		prepRegularOccmListener();
		prepHeaderOccmListener();
		prepFooterOccmListener();
		prepIndexedColOccmListener();
	}
	
	/**
	 * Prepares the context menu creation listener for regular cells.
	 */
	private void prepRegularOccmListener() {
		regularOccmListener = new View.OnCreateContextMenuListener() {
			@Override
			public void onCreateContextMenu(ContextMenu menu, View v,
					ContextMenuInfo menuInfo) {
				selectContentCell(v.getId());
				int none = ContextMenu.NONE;
				String selectedColName = table.getColName(table.getColNum(selectedCellID));
				String selectedColType = cp.getType(selectedColName);
				if (selectedColType != null && selectedColType.equals("File")) {
					menu.add(none, OPEN_FILE, none, "Open File");
				}
				menu.add(none, SELECT_COLUMN, none, "Select Column");
				menu.add(none, SEND_SMS_ROW, none, "Send SMS Row");
				menu.add(none, HISTORY_IN, none, "View Collection");
				menu.add(none, DELETE_ROW, none, "Delete Row");
			}
		};
	}
    
    /**
     * Prepares the context menu creation listener for indexed column cells.
     */
    private void prepIndexedColOccmListener() {
        indexedColOccmListener = new View.OnCreateContextMenuListener() {
            @Override
            public void onCreateContextMenu(ContextMenu menu, View v,
                    ContextMenuInfo menuInfo) {
                selectContentCell(v.getId());
                int none = ContextMenu.NONE;
                menu.add(none, UNSELECT_COLUMN, none, "Unselect Column");
                menu.add(none, SEND_SMS_ROW, none, "Send SMS Row");
                menu.add(none, HISTORY_IN, none, "View Collection");
                menu.add(none, DELETE_ROW, none, "Delete Row");
            }
        };
    }
	
	/**
	 * Prepares the context menu creation listener for header cells.
	 */
	private void prepHeaderOccmListener() {
		headerOccmListener = new View.OnCreateContextMenuListener() {
			@Override
			public void onCreateContextMenu(ContextMenu menu, View v,
					ContextMenuInfo menuInfo) {
				lastHeaderMenued = v.getId();
				int none = ContextMenu.NONE;
				String colName = table.getHeader().get(v.getId());
				if(cp.getIsIndex(colName)) {
					menu.add(none, UNSET_COL_AS_PRIME, none, "Unset as Index");
				} else if(colName.equals(tp.getSortBy())) {
				    menu.add(none, UNSET_COL_AS_ORDERBY, none, "Unset as Sort");
				} else {
					menu.add(none, SET_COL_AS_PRIME, none, "Set as Index");
					menu.add(none, SET_COL_AS_ORDERBY, none, "Set as Sort");
				}
				menu.add(none, OPEN_COL_OPTS, none, "Column Properties");
				menu.add(none, SET_COL_WIDTH, none, "Set Column Width");
			}

		};
	}
	
	/**
	 * Prepares the context menu creation listener for footer cells.
	 */
	private void prepFooterOccmListener() {
		footerOccmListener = new View.OnCreateContextMenuListener() {
			@Override
			public void onCreateContextMenu(ContextMenu menu, View v,
					ContextMenuInfo menuInfo) {
				lastFooterMenued = v.getId();
				int none = ContextMenu.NONE;
				menu.add(none, SET_FOOTER_OPT, none, "Set Footer Mode");
			}
		};
	}
	
	@Override
	public void prepRegularCellOccmListener(TextView cell) {
		cell.setOnCreateContextMenuListener(regularOccmListener);
	}
	
	@Override
	public void prepHeaderCellOccmListener(TextView cell) {
		cell.setOnCreateContextMenuListener(headerOccmListener);
	}
	
	@Override
	public void prepIndexedColCellOccmListener(TextView cell) {
	    cell.setOnCreateContextMenuListener(indexedColOccmListener);
	}
	
	@Override
	public void prepFooterCellOccmListener(TextView cell) {
		cell.setOnCreateContextMenuListener(footerOccmListener);
	}
	
}

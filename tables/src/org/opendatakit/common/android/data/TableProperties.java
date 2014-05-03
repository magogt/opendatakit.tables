/*
 * Copyright (C) 2012 University of Washington
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.opendatakit.common.android.data;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.IOFileFilter;
import org.codehaus.jackson.JsonGenerationException;
import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.annotate.JsonAutoDetect.Visibility;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.opendatakit.aggregate.odktables.rest.KeyValueStoreConstants;
import org.opendatakit.aggregate.odktables.rest.SyncState;
import org.opendatakit.common.android.data.ColumnProperties.ColumnDefinitionChange;
import org.opendatakit.common.android.database.DataModelDatabaseHelper;
import org.opendatakit.common.android.database.DataModelDatabaseHelperFactory;
import org.opendatakit.common.android.exception.TableAlreadyExistsException;
import org.opendatakit.common.android.provider.DataTableColumns;
import org.opendatakit.common.android.provider.TableDefinitionsColumns;
import org.opendatakit.common.android.sync.aggregate.SyncTag;
import org.opendatakit.common.android.utilities.ODKFileUtils;
import org.opendatakit.common.android.utils.ColorRuleUtil;
import org.opendatakit.common.android.utils.DataUtil;
import org.opendatakit.common.android.utils.NameUtil;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

/**
 * A class for accessing and managing table properties.
 * <p>
 * Note: Sam (sudar.sam@gmail.com) has begun to transition this code to use the
 * key value stores rather than an actual table properties table. The idea is
 * that you will be using the "Active" key value store to modify and display
 * your things, and that when you download from the server, these properties
 * will be in the "default" key value store.
 * <p>
 * NB sudar.sam@gmail.com: The properties of a table exist in two different
 * places in the datastore: the immutable (ish) properties that are part of the
 * table's definition (things like db backing name, type of the table, etc), are
 * stored in the table_definitions table. The ODK Tables-specific properties
 * exist in the key value stores as described above.
 *
 * @author hkworden@gmail.com (Hilary Worden)
 * @author sudar.sam@gmail.com
 */
public class TableProperties {

  private static final ObjectMapper mapper;
  private static final String t = "TableProperties";

  /***********************************
   * The partition and aspect of table properties in the key value store.
   ***********************************/
  public static final String KVS_PARTITION = KeyValueStoreConstants.PARTITION_TABLE;

  /***********************************
   * The names of keys that are defaulted to exist in the key value store.
   ***********************************/
  public static final String KEY_DISPLAY_NAME = KeyValueStoreConstants.TABLE_DISPLAY_NAME;
  private static final String KEY_COLUMN_ORDER = KeyValueStoreConstants.TABLE_COL_ORDER;

  private static final String KEY_GROUP_BY_COLUMNS = KeyValueStoreConstants.TABLE_GROUP_BY_COLS;

  private static final String KEY_SORT_COLUMN = KeyValueStoreConstants.TABLE_SORT_COL;
  private static final String KEY_SORT_ORDER = KeyValueStoreConstants.TABLE_SORT_ORDER;

  // INDEX_COL is held fixed during left/right pan
  private static final String KEY_INDEX_COLUMN = KeyValueStoreConstants.TABLE_INDEX_COL;

  // this is not known by the server...
  private static final String KEY_DEFAULT_VIEW_TYPE = "defaultViewType";

  /*
   * Keys that can exist in the key value store but are not defaulted to exist
   * can be added her just as a sanity check. Note that they are NOT guaranteed
   * to be here. The general convention is going to be that these keys should be
   * defined in the respective classes that rely on them, and should be
   * formatted as "Class.keyName." Eg "CollectUtil.formId".
   *
   * Keys are known to exist in: CollectUtil
   */

  // TODO atm not sure if we want to keep these things here, or if we should
  // be using their names from their respective sources.
  private static final String JSON_KEY_VERSION = "jVersion";
  private static final String JSON_KEY_TABLE_ID = "tableId";
  private static final String JSON_KEY_DB_TABLE_NAME = "dbTableName";
  private static final String JSON_KEY_DISPLAY_NAME = "displayName";
  private static final String JSON_KEY_COLUMN_ORDER = "colOrder";
  private static final String JSON_KEY_COLUMNS = "columns";
  private static final String JSON_KEY_GROUP_BY_COLUMNS = "groupByCols";
  private static final String JSON_KEY_SORT_COLUMN = "sortCol";
  private static final String JSON_KEY_SORT_ORDER = "sortOrder";
  private static final String JSON_KEY_INDEX_COLUMN = "indexCol";
  private static final String JSON_KEY_DEFAULT_VIEW_TYPE = "defaultViewType";

  /***********************************
   * Default values for those keys which require them. TODO When the keys in the
   * KVS are moved to the respective classes that use them, these should go
   * there most likely.
   ***********************************/
  public static final String DEFAULT_KEY_GROUP_BY_COLUMNS = "";
  public static final String DEFAULT_KEY_SORT_COLUMN = "";
  public static final String DEFAULT_KEY_INDEX_COLUMN = "";
  public static final String DEFAULT_KEY_CURRENT_VIEW_TYPE = TableViewType.SPREADSHEET.name();
  public static final String DEFAULT_KEY_COLUMN_ORDER = "";

  /*
   * These are the keys that exist in the key value store after the creation of
   * a table. In other words they should always exist in the key value store.
   */
  private static final String[] INIT_KEYS = { KEY_DISPLAY_NAME, KEY_COLUMN_ORDER,
      KEY_GROUP_BY_COLUMNS, KEY_SORT_COLUMN, KEY_DEFAULT_VIEW_TYPE, KEY_INDEX_COLUMN };

  // columns included in json properties
  private static final List<String> JSON_COLUMNS = Arrays.asList(new String[] { KEY_DISPLAY_NAME,
      KEY_COLUMN_ORDER, KEY_GROUP_BY_COLUMNS, KEY_SORT_COLUMN, KEY_DEFAULT_VIEW_TYPE,
      KEY_INDEX_COLUMN });

  static {
    mapper = new ObjectMapper();
    mapper.setVisibilityChecker(mapper.getVisibilityChecker().withFieldVisibility(Visibility.ANY));
  }

  private static boolean staleActiveCache = true;
  private static List<String> idsInActiveKVS = new ArrayList<String>();
  private static Map<String, TableProperties> activeTableIdMap = new HashMap<String, TableProperties>();

  /**
   * Return the TableProperties for all the tables in the specified KVS. store.
   *
   * @param dbh
   * @param typeOfStore
   *          the KVS from which to get the store
   * @return
   */
  private static synchronized void refreshActiveCache(Context context, String appName, SQLiteDatabase db) {
    try {
      KeyValueStoreManager kvsm = new KeyValueStoreManager();
      idsInActiveKVS = kvsm.getAllIdsFromStore(db);

      activeTableIdMap.clear();
      for ( String tableId : idsInActiveKVS ) {
        Map<String, String> propPairs = getMapOfPropertiesForTable(db, tableId);
        TableProperties tp = constructPropertiesFromMap(context, appName, db, propPairs);
        if ( tp == null ) {
          throw new IllegalStateException("Unexpectedly missing " + tableId);
        }
        activeTableIdMap.put(tp.getTableId(), tp);
      }
      staleActiveCache = false;
    } finally {
    }
  }

  private static synchronized void ensureActiveTableIdMapLoaded(Context context, String appName, SQLiteDatabase db) {
    // A pre-requisite to having an update cache is to have agreement on
    // all the known table ids.
    List<String> allIds = TableDefinitions.getAllTableIds(db);
    if ( staleActiveCache || idsInActiveKVS.size() != allIds.size() ) {
      refreshActiveCache(context, appName, db);
    }
  }

  /**
   *
   * @param typeOfStore - null if everything
   */
  public static synchronized void markStaleCache() {
    staleActiveCache = true;
  }

  /**
   * Return the TableProperties for the given table id.
   *
   * @param dbh
   * @param tableId
   * @param typeOfStore
   *          the store from which to get the properties
   * @param forceRefresh
   *           do not use the cache; update it with a fresh pull
   * @return
   */
  public static TableProperties getTablePropertiesForTable(Context context, String appName, String tableId) {


    DataModelDatabaseHelper dh = DataModelDatabaseHelperFactory.getDbHelper(context, appName);
    SQLiteDatabase db = dh.getReadableDatabase();
    try {
      ensureActiveTableIdMapLoaded(context, appName, db);
      // just use the cached value...
      return activeTableIdMap.get(tableId);
    } finally {
      db.close();
    }
  }

  public static TableProperties refreshTablePropertiesForTable(Context context, String appName, String tableId) {

    DataModelDatabaseHelper dh = DataModelDatabaseHelperFactory.getDbHelper(context, appName);
    SQLiteDatabase db = dh.getReadableDatabase();
    try {
      Map<String, String> mapProps = getMapOfPropertiesForTable(db, tableId);
      TableProperties tp = constructPropertiesFromMap(context, appName, db, mapProps);
      if (tp != null) {
        // update the cache...
        activeTableIdMap.put(tp.getTableId(), tp);
      }
      return tp;
    } finally {
      db.close();
    }
  }

  /**
   * Return the TableProperties for all the tables in the specified KVS. store.
   *
   * @param dbh
   * @param typeOfStore
   *          the KVS from which to get the store
   * @return
   */
  public static TableProperties[] getTablePropertiesForAll(Context context, String appName) {
    SQLiteDatabase db = null;
    try {
      DataModelDatabaseHelper dh = DataModelDatabaseHelperFactory.getDbHelper(context, appName);
      db = dh.getReadableDatabase();
      KeyValueStoreManager kvsm = new KeyValueStoreManager();
      ensureActiveTableIdMapLoaded(context, appName, db);
      return activeTableIdMap.values().toArray(new TableProperties[activeTableIdMap.size()]);
    } finally {
      if ( db != null ) {
        db.close();
      }
    }
  }

  /**
   * Get the TableProperties for all the tables that have synchronized set to
   * true in the sync KVS. typeOfStore tells you the KVS (active, default, or
   * server) from which to construct the properties.
   *
   * @param dbh
   * @param typeOfStore
   *          the KVS from which to get the properties
   * @return
   */
  public static TableProperties[] getTablePropertiesForSynchronizedTables(Context context, String appName) {

    SQLiteDatabase db = null;
    try {
      DataModelDatabaseHelper dh = DataModelDatabaseHelperFactory.getDbHelper(context, appName);
      db = dh.getReadableDatabase();
      KeyValueStoreManager kvsm = new KeyValueStoreManager();
      // don't do caching for other KVS's
      List<String> synchedIds = kvsm.getSynchronizedTableIds(db);
      return constructPropertiesFromIds(context, appName, db, synchedIds);
    } finally {
       if ( db != null ) {
       db.close();
       }
    }
  }

  /***********************************
   * The fields that make up a TableProperties object.
   ***********************************/
  /*
   * The fields that belong only to the object, and are not related to the
   * actual table itself.
   */

  private final Context context;
  private final String appName;

  private final String[] whereArgs;
  /*
   * The fields that reside in TableDefintions
   */
  private final String tableId;
  private String dbTableName;
  private SyncTag syncTag;
  // TODO lastSyncTime should probably eventually be an int?
  // keeping as a string for now to minimize errors.
  private String lastSyncTime;
  private SyncState syncState;
  private boolean transactioning;
  /*
   * The fields that are in the key value store.
   */
  private String displayName;
  // private ColumnProperties[] columns;
  /**
   * Maps the elementKey of a column to its ColumnProperties object.
   */
  private Map<String, ColumnProperties> mElementKeyToColumnProperties;
  private boolean staleColumnsInOrder = true;
  private List<ColumnProperties> columnsInOrder = new ArrayList<ColumnProperties>();

  private List<String> columnOrder;
  private List<String> groupByColumns;
  private String sortColumn;
  private String sortOrder;
  private String indexColumn;
  private TableViewType defaultViewType;
  private KeyValueStoreHelper tableKVSH;

  private TableProperties(Context context, String appName, SQLiteDatabase db, String tableId, String dbTableName,
      String displayName,
      ArrayList<String> columnOrder, ArrayList<String> groupByColumns, String sortColumn,
      String sortOrder,
      String indexColumn, SyncTag syncTag,
      String lastSyncTime,
      TableViewType defaultViewType,
      SyncState syncState, boolean transactioning) {
    this.context = context;
    this.appName = appName;
    whereArgs = new String[] { tableId };
    this.tableId = tableId;
    this.dbTableName = dbTableName;
    this.displayName = displayName;
    // columns = null;
    this.mElementKeyToColumnProperties = null;
    if ( groupByColumns == null ) {
    	groupByColumns = new ArrayList<String>();
    }
    this.groupByColumns = groupByColumns;
    if (sortColumn == null || sortColumn.length() == 0) {
      this.sortColumn = null;
    } else {
      this.sortColumn = sortColumn;
    }
    if (sortOrder == null || sortOrder.length() == 0) {
      this.sortOrder = null;
    } else {
      this.sortOrder = sortOrder;
    }
    if ((indexColumn == null)) {
      this.indexColumn = DEFAULT_KEY_INDEX_COLUMN;
    } else {
      this.indexColumn = indexColumn;
    }
    this.syncTag = syncTag;
    this.lastSyncTime = lastSyncTime;
    this.defaultViewType = defaultViewType;
    this.syncState = syncState;
    this.transactioning = transactioning;
    this.tableKVSH = this.getKeyValueStoreHelper(TableProperties.KVS_PARTITION);

    // This should be OK even when we are creating a new database
    // because there should be no column entries defined yet.
    refreshColumns(db);
    if (columnOrder.size() == 0) {

      for (ColumnProperties cp : mElementKeyToColumnProperties.values()) {
        if  ( cp.isUnitOfRetention() ) {
          columnOrder.add(cp.getElementKey());
        }
      }
      Collections.sort(columnOrder, new Comparator<String>() {

        @Override
        public int compare(String lhs, String rhs) {
          return lhs.compareTo(rhs);
        }
      });
    }
    this.columnOrder = columnOrder;
    this.staleColumnsInOrder = true;
  }

  public KeyValueStoreSync getSyncStoreForTable() {
    KeyValueStoreManager kvsm = getKeyValueStoreManager();
    return kvsm.getSyncStoreForTable(getTableId());
  }

  public boolean isSetToSync() {
    KeyValueStoreSync syncKVS = getSyncStoreForTable();
    SQLiteDatabase db = null;
    try {
      db = getReadableDatabase();
      return syncKVS.isSetToSync(db);
    } finally {
      db.close();
    }
  }

  public void setIsSetToSync(boolean value) {
    KeyValueStoreSync syncKVS = getSyncStoreForTable();
    SQLiteDatabase db = getWritableDatabase();
    try {
      db.beginTransaction();
      syncKVS.setIsSetToSync(db, value);
      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
      db.close();
    }
  }

  public KeyValueStore getStoreForTable() {
    return getKeyValueStoreManager().getStoreForTable(this.tableId);
  }

  public ArrayList<OdkTablesKeyValueStoreEntry> getMetaDataEntries() {
    KeyValueStore kvs = getStoreForTable();
    SQLiteDatabase db = null;
    try {
      db = getReadableDatabase();
      ArrayList<OdkTablesKeyValueStoreEntry> kvsEntries = kvs.getEntries(db);
      return kvsEntries;
    } finally {
      db.close();
    }
  }

  public boolean hasMetaDataEntries() {
    KeyValueStore kvs = getStoreForTable();
    SQLiteDatabase db = null;
    try {
      db = getReadableDatabase();
      return kvs.entriesExist(db);
    } finally {
      db.close();
    }
  }

  public void addMetaDataEntries(List<OdkTablesKeyValueStoreEntry> entries, boolean clear) {
    KeyValueStore kvs = getStoreForTable();
    SQLiteDatabase db = getWritableDatabase();
    try {
      db.beginTransaction();
      if ( clear ) {
        kvs.clearKeyValuePairs(db);
      }
      kvs.addEntriesToStore(db, entries);
      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
      db.close();
    }
  }

  public KeyValueStoreManager getKeyValueStoreManager() {
	  return new KeyValueStoreManager();
  }

  public SQLiteDatabase getReadableDatabase() {
    DataModelDatabaseHelper dh = DataModelDatabaseHelperFactory.getDbHelper(context, appName);
    return dh.getReadableDatabase();
  }

  public SQLiteDatabase getWritableDatabase() {
    DataModelDatabaseHelper dh = DataModelDatabaseHelperFactory.getDbHelper(context, appName);
    return dh.getWritableDatabase();
  }
  /*
   * Return the map of all the properties for the given table. The properties
   * include both the values of this table's row in TableDefinition and the
   * values in the key value store pointed to by INIT_KEYS.
   *
   * Atm this is just key->value. The caller must know the intended type of the
   * value and parse it correctly. This map should eventually become a
   * key->TypeValuePair or something like that. TODO: make it the above
   *
   * This deserves its own method b/c to get the properties you are forced to go
   * through both the key value store and the TableDefinitions table.
   *
   * NOTE: this routine now supplies valid default values should the database
   * not contain the appropriate default settings for a particular value.
   * This provides a degree of upgrade-ability.
   *
   * @param dbh
   *
   * @param tableId
   *
   * @param typeOfStore
   *
   * @return
   */
  private static Map<String, String> getMapOfPropertiesForTable(SQLiteDatabase db, String tableId) {
    try {
      KeyValueStoreManager kvsm = new KeyValueStoreManager();
      KeyValueStore intendedKVS = kvsm.getStoreForTable(tableId);
      Map<String, String> tableDefinitionsMap = TableDefinitions.getFields(db, tableId);
      Map<String, String> kvsMap = intendedKVS.getProperties(db);
      Map<String, String> mapProps = new HashMap<String, String>();
      // table definitions wins -- apply it 2nd
      mapProps.putAll(kvsMap);
      mapProps.putAll(tableDefinitionsMap);

      if ( mapProps.get(KEY_DISPLAY_NAME) == null ) {
        mapProps.put(KEY_DISPLAY_NAME, tableId);
      }
      if ( mapProps.get(KEY_COLUMN_ORDER) == null ) {
        mapProps.put(KEY_COLUMN_ORDER, DEFAULT_KEY_COLUMN_ORDER);
      }
      if ( mapProps.get(KEY_GROUP_BY_COLUMNS) == null ) {
        mapProps.put(KEY_GROUP_BY_COLUMNS, DEFAULT_KEY_GROUP_BY_COLUMNS);
      }
      if ( mapProps.get(KEY_SORT_COLUMN) == null ) {
        mapProps.put(KEY_SORT_COLUMN, DEFAULT_KEY_SORT_COLUMN);
      }
      if ( mapProps.get(KEY_INDEX_COLUMN) == null ) {
        mapProps.put(KEY_INDEX_COLUMN, DEFAULT_KEY_INDEX_COLUMN);
      }
      if ( mapProps.get(KEY_DEFAULT_VIEW_TYPE) == null ) {
        mapProps.put(KEY_DEFAULT_VIEW_TYPE, DEFAULT_KEY_CURRENT_VIEW_TYPE);
      }
      return mapProps;
    } finally {
      // TODO: fix the when to close problem
      // if ( db != null ) {
      // db.close();
      // }
    }
  }

  /*
   * Constructs a table properties object based on a map of key values as would
   * be acquired from the key value store.
   */
  private static TableProperties constructPropertiesFromMap(Context context, String appName,
        SQLiteDatabase db, Map<String, String> props) {
    // first we have to get the appropriate type for the non-string fields.
    String syncStateStr = props.get(TableDefinitionsColumns.SYNC_STATE);
    if ( syncStateStr == null ) {
      // we don't have any entry for this table
      return null;
    }
    SyncState syncState = SyncState.valueOf(syncStateStr);
    String transactioningStr = props.get(TableDefinitionsColumns.TRANSACTIONING);
    int transactioningInt = Integer.parseInt(transactioningStr);
    boolean transactioning = DataHelper.intToBool(transactioningInt);
    String columnOrderValue = props.get(KEY_COLUMN_ORDER);
    String defaultViewTypeStr = props.get(KEY_DEFAULT_VIEW_TYPE);
    TableViewType defaultViewType;
    if (defaultViewTypeStr == null) {
      defaultViewType = TableViewType.SPREADSHEET;
      props.put(KEY_DEFAULT_VIEW_TYPE, TableViewType.SPREADSHEET.name());
    } else {
      try {
        defaultViewType = TableViewType.valueOf(defaultViewTypeStr);
      } catch (Exception e) {
        defaultViewType = TableViewType.SPREADSHEET;
        props.put(KEY_DEFAULT_VIEW_TYPE, TableViewType.SPREADSHEET.name());
      }
    }
    // for legacy reasons, the code expects the DB_COLUMN_ORDER and
    // DB_PRIME_COLUMN values to be empty strings, not null. However, when
    // retrieving values from the key value store, empty strings are converted
    // to null, because many others expect null values. For that reason, first
    // check here to set null values for these columns to empty strings.
    if (columnOrderValue == null)
      columnOrderValue = "";
    ArrayList<String> columnOrder = new ArrayList<String>();
    if (columnOrderValue.length() != 0) {
      try {
        columnOrder = mapper.readValue(columnOrderValue, ArrayList.class);
      } catch (JsonParseException e) {
        e.printStackTrace();
        Log.e(t, "ignore invalid json: " + columnOrderValue);
      } catch (JsonMappingException e) {
        e.printStackTrace();
        Log.e(t, "ignore invalid json: " + columnOrderValue);
      } catch (IOException e) {
        e.printStackTrace();
        Log.e(t, "ignore invalid json: " + columnOrderValue);
      }
    }

    String groupByColumnsJsonString = props.get(KEY_GROUP_BY_COLUMNS);
    ArrayList<String> groupByCols = new ArrayList<String>();
    if (groupByColumnsJsonString != null &&
    	groupByColumnsJsonString.length() != 0) {
      try {
    	  groupByCols = mapper.readValue(groupByColumnsJsonString, ArrayList.class);
      } catch (JsonParseException e) {
        e.printStackTrace();
        Log.e(t, "ignore invalid json: " + groupByColumnsJsonString);
      } catch (JsonMappingException e) {
        e.printStackTrace();
        Log.e(t, "ignore invalid json: " + groupByColumnsJsonString);
      } catch (IOException e) {
        e.printStackTrace();
        Log.e(t, "ignore invalid json: " + groupByColumnsJsonString);
      }
    }

    return new TableProperties(context, appName, db, props.get(TableDefinitionsColumns.TABLE_ID),
        props.get(TableDefinitionsColumns.DB_TABLE_NAME),
        props.get(KEY_DISPLAY_NAME),
        columnOrder, groupByCols,
        props.get(KEY_SORT_COLUMN), props.get(KEY_SORT_ORDER),
        props.get(KEY_INDEX_COLUMN),
        SyncTag.valueOf(props.get(TableDefinitionsColumns.SYNC_TAG)),
        props.get(TableDefinitionsColumns.LAST_SYNC_TIME), defaultViewType,
        syncState, transactioning);
  }

  /*
   * Construct an array of table properties for the given ids. The properties
   * are collected from the intededStore.
   */
  private static TableProperties[] constructPropertiesFromIds(Context context, String appName,
      SQLiteDatabase db, List<String> ids) {
    TableProperties[] allProps = new TableProperties[ids.size()];
    for (int i = 0; i < ids.size(); i++) {
      String tableId = ids.get(i);
      Map<String, String> propPairs = getMapOfPropertiesForTable(db, tableId);
      allProps[i] = constructPropertiesFromMap(context, appName, db, propPairs);
      if ( allProps[i] == null ) {
        throw new IllegalStateException("Unexpectedly missing " + tableId);
      }
    }
    return allProps;
  }

  /**
   * Add a table from the JSON representation of a TableProperties object, as
   * set from {@link toJson}. There is currently no control for versioning or
   * anything of that nature.
   * <p>
   * This method is equivalent to parsing the json object yourself to get the id
   * and database names, calling the appropriate {@link addTable} method, and
   * then calling {@link setFromJson}.
   *
   * @param dbh
   * @param json
   * @param typeOfStore
   * @return
   * @throws TableAlreadyExistsException
   *           if the dbTableName or tableId specified by the json string
   *           conflict with any of the properties from the three key value
   *           stores. If so, nothing is done.
   */
  public static TableProperties addTableFromJson(Context context, String appName, String json)
      throws TableAlreadyExistsException {
    // we just need to reclaim the bare minimum that we need to call the other
    // methods.
    Map<String, Object> jo;
    try {
      jo = mapper.readValue(json, Map.class);
    } catch (JsonParseException e) {
      e.printStackTrace();
      throw new IllegalArgumentException("invalid json: " + json);
    } catch (JsonMappingException e) {
      e.printStackTrace();
      throw new IllegalArgumentException("invalid json: " + json);
    } catch (IOException e) {
      e.printStackTrace();
      throw new IllegalArgumentException("invalid json: " + json);
    }
    String tableId = (String) jo.get(JSON_KEY_TABLE_ID);
    String dbTableName = (String) jo.get(JSON_KEY_DB_TABLE_NAME);
    String displayName = (String) jo.get(JSON_KEY_DISPLAY_NAME);
    // And now we need to check for conflicts that would mess up the database.
    TableProperties[] activeProps = getTablePropertiesForAll(context, appName);
    // this arraylist will hold all the properties.
    ArrayList<TableProperties> listProps = new ArrayList<TableProperties>();
    listProps.addAll(Arrays.asList(activeProps));
    if (NameUtil.dbTableNameAlreadyExists(context, appName, dbTableName)
        || NameUtil.tableIdAlreadyExists(context, appName, tableId)) {
      Log.e(t, "a table already exists with the dbTableName: " + dbTableName
          + " or with the tableId: " + tableId);
      throw new TableAlreadyExistsException("a table already exists with the" + " dbTableName: "
          + dbTableName + " or with the tableId: " + tableId);
    }
    TableProperties tp = addTable(context, appName, dbTableName, displayName,
        tableId);
    if ( !tp.setFromJson(json) ) {
      throw new IllegalStateException("this should never happen");
    }
    return tp;
  }

  /**
   * Add a table to the database. The intendedStore type exists to force you to
   * be specific to which store you are adding the table to.
   * <p>
   * NB: Currently adds all the keys defined in this class to the key value
   * store as well. This should likely change.
   * <p>
   * NB: Sets the db_table_name in TableDefinitions to the dbTableName parameter.
   *
   * @param context
   * @param appName
   * @param dbTableName
   * @param displayName
   * @param tableId
   * @param typeOfStore
   * @return
   */
  public static TableProperties addTable(Context context, String appName,
      String dbTableName, String displayName, String tableId) {
    Log.e(t, "adding table with id: " + tableId);
    // First we will add the entry in TableDefinitions.
    // TODO: this should check for duplicate names.
    TableProperties tp = null;
    SQLiteDatabase db;
    try {
      DataModelDatabaseHelper dh = DataModelDatabaseHelperFactory.getDbHelper(context, appName);
      db = dh.getReadableDatabase();
      db.beginTransaction();
      try {
        TableDefinitions.addTable(db, tableId, dbTableName);
        Map<String, String> propPairs = getMapOfPropertiesForTable(db, tableId);
        if ( !(displayName.startsWith("\"") || displayName.startsWith("{")) ) {
          // wrap it so that it is a JSON string
          displayName = ODKFileUtils.mapper.writeValueAsString(displayName);
        }
        propPairs.put(KEY_DISPLAY_NAME, displayName);
        tp = constructPropertiesFromMap(context, appName, db, propPairs);
        if ( tp == null ) {
          throw new IllegalStateException("Unexpectedly missing " + tableId);
        }
        Log.d(t, "adding table: " + dbTableName);
        DbTable.createDbTable(db, tp);
        // And now set the default color rules.
        ColorRuleGroup ruleGroup = ColorRuleGroup.getStatusColumnRuleGroup(tp);
        ruleGroup.replaceColorRuleList(ColorRuleUtil.getDefaultSyncStateColorRules());
        ruleGroup.saveRuleList();
        db.setTransactionSuccessful();
      } catch (Exception e) {
        e.printStackTrace();
        if ( e instanceof IllegalStateException ) {
        	throw (IllegalStateException) e;
        } else {
        	throw new IllegalStateException("TableProperties could not be created", e);
        }
      } finally {
        db.endTransaction();
        db.close();
      }
      return tp;
    } finally {
      // TODO: fix the when to close problem
      // db.close();
    }
  }

  public boolean isSharedTable() {
    KeyValueStoreManager kvsm = new KeyValueStoreManager();
    KeyValueStoreSync syncKVSM = kvsm.getSyncStoreForTable(tableId);
    SQLiteDatabase db = null;
    try {
      db = getReadableDatabase();
      boolean isSetToSync = syncKVSM.isSetToSync(db);
      return (isSetToSync && (syncState == SyncState.rest || syncState == SyncState.updating));
    } finally {
      db.close();
    }
  }

  /**
   * Remove the table from the local database. This cannot be undone.
   */
  public void deleteTable() {
    // Two things must be done: delete all the key value pairs from the active
    // key value store and drop the table holding the data from the database.
    String tableDir = ODKFileUtils.getTablesFolder(appName, tableId);
    try {
      FileUtils.deleteDirectory(new File(tableDir));
    } catch (IOException e1) {
      e1.printStackTrace();
      throw new IllegalStateException("Unable to delete the " + tableDir + " directory", e1);
    }

    String assetsCsvDir = ODKFileUtils.getAssetsFolder(appName) + "/csv";
    try {
      Collection<File> files = FileUtils.listFiles(new File(assetsCsvDir), new IOFileFilter() {

        @Override
        public boolean accept(File file) {
          String[] parts = file.getName().split("\\.");
          return ( parts[0].equals(tableId) && parts[parts.length-1].equals("csv") &&
                  (parts.length == 2 ||
                   parts.length == 3 ||
                   (parts.length == 4 && parts[parts.length-2].equals("properties"))) );
        }

        @Override
        public boolean accept(File dir, String name) {
          String[] parts = name.split("\\.");
          return ( parts[0].equals(tableId) && parts[parts.length-1].equals("csv") &&
                  (parts.length == 2 ||
                   parts.length == 3 ||
                   (parts.length == 4 && parts[parts.length-2].equals("properties"))) );
        }}, new IOFileFilter() {

        // don't traverse into directories
        @Override
        public boolean accept(File arg0) {
          return false;
        }

        // don't traverse into directories
        @Override
        public boolean accept(File arg0, String arg1) {
          return false;
        }});

      FileUtils.deleteDirectory(new File(tableDir));
      for ( File f : files ) {
        FileUtils.deleteQuietly(f);
      }
    } catch (IOException e1) {
      e1.printStackTrace();
      throw new IllegalStateException("Unable to delete the " + tableDir + " directory", e1);
    }

    Map<String, ColumnProperties> columns = getAllColumns();
    SQLiteDatabase db = null;
    try {
      DataModelDatabaseHelper dh = DataModelDatabaseHelperFactory.getDbHelper(context, appName);
      db = dh.getReadableDatabase();
      db.beginTransaction();
      try {
        db.execSQL("DROP TABLE " + dbTableName);
        for (ColumnProperties cp : columns.values()) {
          cp.deleteColumn(db);
        }
        TableDefinitions.deleteTableFromTableDefinitions(db, tableId);
        KeyValueStoreManager kvsm = new KeyValueStoreManager();
        kvsm.getStoreForTable(tableId).clearKeyValuePairs(db);
        // remove it from sync store
        kvsm.getSyncStoreForTable(tableId).clearKeyValuePairs(db);
        db.setTransactionSuccessful();
      } catch (Exception e) {
        e.printStackTrace();
        Log.e(t, "error deleting table: " + this.tableId);
      } finally {
        db.endTransaction();
        db.close();
      }
    } finally {
      // TODO: fix the when to close problem
      // db.close();
      markStaleCache();
    }
  }

  public String getAppName() {
    return appName;
  }

  /**
   * This is the user-defined string that is not translated and uniquely identifies
   * this data table. Begins as the cleaned up display name of the table.
   *
   * @return
   */
  public String getTableId() {
    return tableId;
  }

  /**
   * @return the table's name in the database
   */
  public String getDbTableName() {
    return dbTableName;
  }

  /**
   * @return the table's display name
   */
  public String getDisplayName() {
    return displayName;
  }

  public String getLocalizedDisplayName() {
    Locale locale = Locale.getDefault();
    String full_locale = locale.toString();
    int underscore = full_locale.indexOf('_');
    String lang_only_locale = (underscore == -1) ? full_locale : full_locale.substring(0, underscore);

    if ( displayName.startsWith("\"") && displayName.endsWith("\"")) {
      return displayName.substring(1,displayName.length()-1);
    } else if ( displayName.startsWith("{") && displayName.endsWith("}")) {
      try {
        Map<String,Object> localeMap = ODKFileUtils.mapper.readValue(displayName, Map.class);
        String candidate = (String) localeMap.get(full_locale);
        if ( candidate != null ) return candidate;
        candidate = (String) localeMap.get(lang_only_locale);
        if ( candidate != null ) return candidate;
        candidate = (String) localeMap.get("default");
        if ( candidate != null ) return candidate;
        return getTableId();
      } catch (JsonParseException e) {
        e.printStackTrace();
        throw new IllegalStateException("bad displayName for tableId: " + getTableId());
      } catch (JsonMappingException e) {
        e.printStackTrace();
        throw new IllegalStateException("bad displayName for tableId: " + getTableId());
      } catch (IOException e) {
        e.printStackTrace();
        throw new IllegalStateException("bad displayName for tableId: " + getTableId());
      }
    }
    return displayName;
    // throw new IllegalStateException("bad displayName for tableId: " + getTableId());
  }

  /**
   * Sets the table's display name.
   *
   * @param displayName
   *          the new display name
   */
  public void setDisplayName(SQLiteDatabase db, String displayName) {
    tableKVSH.setString(db, KEY_DISPLAY_NAME, displayName);
    this.displayName = displayName;
  }

  /**
   * Get the current view type of the table.
   *
   * @return
   */
  public TableViewType getDefaultViewType() {
    return this.defaultViewType;
  }

  /**
   * Set the current view type of the table.
   *
   * @param viewType
   */
  public void setDefaultViewType(SQLiteDatabase db, TableViewType viewType) {
    tableKVSH.setString(db, TableProperties.KEY_DEFAULT_VIEW_TYPE, viewType.name());
    this.defaultViewType = viewType;
  }

  /**
   * Return a map of elementKey to columns as represented by their
   * {@link ColumnProperties}. If something has happened to a column that did
   * not go through TableProperties, update row also needs to be called.
   * <p>
   * If used repeatedly, this value should be cached by the caller.
   *
   * @return a map of the table's columns as represented by their
   *         {@link ColumnProperties}.
   */
  public Map<String, ColumnProperties> getDatabaseColumns() {
    if (mElementKeyToColumnProperties == null) {
      refreshColumnsFromDatabase();
    }
    Map<String, ColumnProperties> defensiveCopy = new HashMap<String, ColumnProperties>();
    for ( String col : mElementKeyToColumnProperties.keySet() ) {
      ColumnProperties cp = mElementKeyToColumnProperties.get(col);
      if ( cp.isUnitOfRetention() ) {
        defensiveCopy.put(col, cp);
      }
    }
    return defensiveCopy;
  }

  /**
   * Return all the columns for the given table.  These will be
   * the element keys that are 'units of retention' (stored as columns in
   * the database) AND the element keys that define super- or sub- structural
   * elements such as composite types whose sub-elements are written
   * individually to the database (e.g., geopoint) or subsumed by the
   * enclosing element (e.g., lists of items).
   *
   * @return map of all the columns in the table
   */
  public Map<String, ColumnProperties> getAllColumns() {
    if (mElementKeyToColumnProperties == null) {
      refreshColumnsFromDatabase();
    }
    Map<String, ColumnProperties> defensiveCopy = new HashMap<String, ColumnProperties>();
    for ( String col : mElementKeyToColumnProperties.keySet() ) {
      ColumnProperties cp = mElementKeyToColumnProperties.get(col);
      defensiveCopy.put(col, cp);
    }
    return defensiveCopy;
  }

  private void refreshColumnsFromDatabase() {
   SQLiteDatabase db = null;
   try {
     db = getReadableDatabase();
     refreshColumns(db);
   } finally {
     db.close();
   }
  }

  /**
   * Pulls the columns from the database into this TableProperties. Also updates
   * the maps of display name and sms label.
   */
  public void refreshColumns(SQLiteDatabase db) {
    this.mElementKeyToColumnProperties = ColumnProperties.getColumnPropertiesForTable(db, this);
  }

  /**
   * Return the index of the elementKey in the columnOrder, or -1 if it is not
   * present.
   *
   * @param elementKey
   * @return
   */
  public int getColumnIndex(String elementKey) {
    return columnOrder.indexOf(elementKey);
  }

  public int getNumberOfDisplayColumns() {
    return columnOrder.size();
  }

  public ColumnProperties getColumnByIndex(int idx) {
    return getColumnsInOrder().get(idx);
  }

  public ColumnProperties getColumnByElementKey(String elementKey) {
    if (this.mElementKeyToColumnProperties == null) {
      refreshColumnsFromDatabase();
    }
    return mElementKeyToColumnProperties.get(elementKey);
  }

  /**
   * Return the element key of the column with the given display name. This
   * behavior is undefined if there are two columns with the same name. This
   * means that all the methods in {@link ColumnProperties} for creating a
   * column must be used for creation and changing of display names to ensure
   * there are no collisions.
   *
   * @param displayName
   * @return
   */
  public ColumnProperties getColumnByDisplayName(String displayName) {
    if (this.mElementKeyToColumnProperties == null) {
      refreshColumnsFromDatabase();
    }
    for (ColumnProperties cp : this.mElementKeyToColumnProperties.values()) {
      if (cp.getLocalizedDisplayName().equals(displayName)) {
        return cp;
      }
    }
    return null;
  }

  public boolean isLocalizedColumnDisplayNameInUse(String localizedName) {
    if (this.mElementKeyToColumnProperties == null) {
      refreshColumnsFromDatabase();
    }
    for (ColumnProperties cp : this.mElementKeyToColumnProperties.values()) {
      if (cp.getLocalizedDisplayName().equals(localizedName)) {
        return true;
      }
    }
    return false;
  }
  /**
   * Return the element key for the column based on the element path.
   * <p>
   * TODO: CURRENTLY A HACK!!!
   * @param elementPath
   * @return
   */
  public String getElementKeyFromElementPath(String elementPath) {
    // TODO: do this correctly. This is just a hack that often works.
    String hackPath = elementPath.replace(".", "_");
    return hackPath;
  }

  /**
   * Take the proposed display name and return a display name that has no
   * conflicts with other display names in the table. If there is a conflict,
   * integers are appended to the proposed name until there are no conflicts.
   *
   * @param proposedDisplayName
   * @return
   */
  public String createDisplayName(String proposedDisplayName) {
    if (!isLocalizedColumnDisplayNameInUse(proposedDisplayName)) {
      return proposedDisplayName;
    }
    // otherwise we need to create a non-conflicting name.
    int suffix = 1;
    while (true) {
      String nextName = proposedDisplayName + suffix;
      if (getColumnByDisplayName(nextName) == null) {
        return nextName;
      }
      suffix++;
    }
  }

  /**
   * Adds a column to the table.
   * <p>
   * The column is set to the default visibility. The column is added to the
   * backing store.
   * <p>
   * The elementKey and elementName must be unique to a given table. If you are
   * not ensuring this yourself, you should pass in null values and it will
   * generate names based on the displayName via
   * {@link ColumnProperties.createDbElementKey} and
   * {@link ColumnProperties.createDbElementName}.
   *
   * @param displayName
   *          the column's display name
   * @param elementKey
   *          should either be received from the server or null
   * @param elementName
   *          should either be received from the server or null
   * @return ColumnProperties for the new table
   */
  public ColumnProperties addColumn(String displayName, String elementKey,
      String elementName, ColumnType columnType,
      List<String> listChildElementKeys, boolean isUnitOfRetention) {
    if (elementKey == null) {
      elementKey = NameUtil.createUniqueElementKey(displayName, this);
    } else if (!NameUtil.isValidUserDefinedDatabaseName(elementKey)) {
      throw new IllegalArgumentException("[addColumn] invalid element key: " +
          elementKey);
    }
    // it is OK for elementName to be null if it isn't a stored value.
    // e.g., Array types and custom data types can have child elements
    // with a null element name. The child element defines the underlying
    // storage type of the value.
    if ( isUnitOfRetention ) {
      if (elementName == null) {
        throw new IllegalArgumentException("[addColumn] null element name for elementKey: " +
            elementKey);
      } else if (!NameUtil.isValidUserDefinedDatabaseName(elementName)) {
        throw new IllegalArgumentException("[addColumn] invalid element name: " +
            elementName + " for elementKey: " + elementKey);
      }
    }
    String jsonStringifyDisplayName = null;
    try {
      if ( (displayName.startsWith("\"") && displayName.endsWith("\"")) ||
           (displayName.startsWith("{") && displayName.endsWith("}")) ) {
        jsonStringifyDisplayName = displayName;
      } else {
        jsonStringifyDisplayName = mapper.writeValueAsString(displayName);
      }
    } catch (JsonGenerationException e) {
      e.printStackTrace();
      throw new IllegalArgumentException("[addColumn] invalid display name: " +
          displayName + " for: " + elementName);
    } catch (JsonMappingException e) {
      e.printStackTrace();
      throw new IllegalArgumentException("[addColumn] invalid display name: " +
          displayName + " for: " + elementName);
    } catch (IOException e) {
      e.printStackTrace();
      throw new IllegalArgumentException("[addColumn] invalid display name: " +
          displayName + " for: " + elementName);
    }
    ColumnProperties cp = null;
    cp = ColumnProperties.createNotPersisted(this, jsonStringifyDisplayName,
        elementKey, elementName, columnType, listChildElementKeys, isUnitOfRetention,
        true);

    return addColumn(cp);
  }

  /**
   * Adds a column to the table.
   * <p>
   * The column is set to the default visibility. The column is added to the
   * backing store.
   * <p>
   * The elementKey and elementName must be unique to a given table. If you are
   * not ensuring this yourself, you should pass in null values and it will
   * generate names based on the displayName via
   * {@link ColumnProperties.createDbElementKey} and
   * {@link ColumnProperties.createDbElementName}.
   *
   * @param displayName
   *          the column's display name
   * @param elementKey
   *          should either be received from the server or null
   * @param elementName
   *          should either be received from the server or null
   * @return ColumnProperties for the new table
   * @throws SQLException
   * @throws IllegalStateException
   * @throws IllegalArgumentException
   */
  private ColumnProperties addColumn(ColumnProperties cp) throws SQLException, IllegalStateException, IllegalArgumentException {
    // ensuring columns is initialized
    // refreshColumns();
    // adding column
    boolean failure = false;
    SQLiteDatabase db = null;
    try {
      db = getWritableDatabase();
      try {
        db.beginTransaction();
        // ensure that we have persisted this column's values
        cp.persistColumn(db);
        db.execSQL("ALTER TABLE \"" + dbTableName + "\" ADD COLUMN \"" + cp.getElementKey() + "\"");
        // use a copy of columnOrder for roll-back purposes
        List<String> newColumnOrder = this.getColumnOrder();
        newColumnOrder.add(cp.getElementKey());
        setColumnOrder(db, newColumnOrder);

        mElementKeyToColumnProperties.put(cp.getElementKey(), cp);
        Log.d(t, "addColumn successful: " + cp.getElementKey());
        db.setTransactionSuccessful();
        return cp;
      } finally {
        db.endTransaction();
        db.close();
      }
    } catch (SQLException e) {
      failure = true;
      e.printStackTrace();
      throw e;
    } catch (IllegalStateException e) {
      failure = true;
      e.printStackTrace();
      throw e;
    } catch (JsonGenerationException e) {
      failure = true;
      e.printStackTrace();
      throw new IllegalArgumentException("[addColumn] failed for: " + cp.getElementKey(), e);
    } catch (JsonMappingException e) {
      failure = true;
      e.printStackTrace();
      throw new IllegalArgumentException("[addColumn] failed for: " + cp.getElementKey(), e);
    } catch (IOException e) {
      failure = true;
      e.printStackTrace();
      throw new IllegalArgumentException("[addColumn] failed for: " + cp.getElementKey(), e);
    } finally {
      if (failure) {
        refreshColumnsFromDatabase();
      }
    }
  }

  /**
   * Deletes a column from the table.
   *
   * @param elementKey
   *          the elementKey of the column to delete
   * @throws SQLException
   * @throws IllegalStateException
   * @throws JsonGenerationException
   * @throws JsonMappingException
   * @throws IOException
   */
  public void deleteColumn(String elementKey) throws SQLException, IllegalStateException, JsonGenerationException, JsonMappingException, IOException {
    // forming a comma-separated list of columns to keep
    ColumnProperties colToDelete = this.getColumnByElementKey(elementKey);
    if (colToDelete == null) {
      Log.e(t, "could not find column to delete with element key: " + elementKey);
      return;
    }

    boolean failure = false;
    // use a copy of columnOrder for roll-back purposes
    List<String> newColumnOrder = this.getColumnOrder();
    newColumnOrder.remove(elementKey);
    // THIS IS REQUIRED FOR reformTable to work correctly
    // THIS IS REQUIRED FOR reformTable to work correctly
    // THIS IS REQUIRED FOR reformTable to work correctly
    // THIS IS REQUIRED FOR reformTable to work correctly
    // THIS IS REQUIRED FOR reformTable to work correctly
    mElementKeyToColumnProperties.remove(elementKey);
    // deleting the column
    SQLiteDatabase db = null;
    try {
      db = getWritableDatabase();
      db.beginTransaction();
      try {
        // NOTE: this assumes mElementKeyToColumnProperties
        // has been updated; the rest of TableProperties
        // can have stale values (e.g., ColumnOrder).
        reformTable(db, null);
        // The hard part was done -- now delete the column
        // definition and update the column order.
        this.setColumnOrder(db, newColumnOrder);
        colToDelete.deleteColumn(db);
        db.setTransactionSuccessful();
      } catch (SQLException e) {
        failure = true;
        throw e;
      } catch (IllegalStateException e) {
        failure = true;
        throw e;
      } catch (JsonGenerationException e) {
        failure = true;
        throw e;
      } catch (JsonMappingException e) {
        failure = true;
        throw e;
      } catch (IOException e) {
        failure = true;
        throw e;
      } finally {
        db.endTransaction();
      }
    } finally {
      if ( db != null ) {
        try {
          db.close();
        } catch ( Exception e ) {
          e.printStackTrace();
          Log.e(t, "Error while closing database");
        }
      }
      if (failure) {
        refreshColumnsFromDatabase();
      }
    }
  }

  private static class RowUpdate {
    String id;
    String savepointTimestamp;
    String value;

    RowUpdate(String id, String timestamp, String value) {
      this.id = id;
      this.savepointTimestamp = timestamp;
      this.value = value;
    }
  }

  /**
   * Uses mElementKeyToColumnProperties to construct a temporary database table
   * to save the current table, then creates a new table and copies the data
   * back in.
   *
   * @param db
   */
  public void reformTable(SQLiteDatabase db, String elementKey) {
    StringBuilder csvBuilder = new StringBuilder(DbTable.DB_CSV_COLUMN_LIST);
    Map<String, ColumnProperties> cols = getDatabaseColumns();
    ColumnProperties cpKey = null;
    ColumnType elementType = null;
    boolean needsConversion = false;
    for (String col : cols.keySet()) {
      ColumnProperties cp = cols.get(col);
      if (cp.isUnitOfRetention()) {
        csvBuilder.append(", " + col);
        if (cp.getElementKey().equals(elementKey)) {
          cpKey = cp;
          elementType = cp.getColumnType();
          needsConversion = (elementType == ColumnType.DATE)
              || (elementType == ColumnType.DATETIME)
              || (elementType == ColumnType.NUMBER)
              || (elementType == ColumnType.INTEGER)
              || (elementType == ColumnType.TIME)
              || (elementType == ColumnType.DATE_RANGE);
        }
      }
    }
    String csv = csvBuilder.toString();
    db.execSQL("CREATE TEMPORARY TABLE backup_(" + csv + ")");
    db.execSQL("INSERT INTO backup_(" + csv + ") SELECT " + csv + " FROM " + dbTableName);
    if (needsConversion) {
      List<RowUpdate> updates = new ArrayList<RowUpdate>();
      Cursor c = db.query("backup_", new String[] { DataTableColumns.ID,
          DataTableColumns.SAVEPOINT_TIMESTAMP, elementKey }, null, null, null, null, null);
      int idxId = c.getColumnIndex(DataTableColumns.ID);
      int idxTimestamp = c.getColumnIndex(DataTableColumns.SAVEPOINT_TIMESTAMP);
      int idxKey = c.getColumnIndex(elementKey);
      DataUtil du = new DataUtil(Locale.ENGLISH, TimeZone.getDefault());
      while (c.moveToNext()) {
        if ( !c.isNull(idxKey) ) {
          String value = c.getString(idxKey);
          String update = du.validifyValue(cpKey, value);
          if (update == null) {
            throw new IllegalArgumentException("Unable to convert " + value + " to "
                + elementType.name());
          }
          updates.add(new RowUpdate(c.getString(idxId), c.getString(idxTimestamp), update));
        }
      }
      c.close();
      for (RowUpdate ru : updates) {
        ContentValues cv = new ContentValues();
        cv.put(elementKey, ru.value);
        db.update("backup_", cv, DataTableColumns.ID + "=? and "
            + DataTableColumns.SAVEPOINT_TIMESTAMP + "=?",
            new String[] { ru.id, ru.savepointTimestamp });
      }
    }
    db.execSQL("DROP TABLE " + dbTableName);
    DbTable.createDbTable(db, this);
    db.execSQL("INSERT INTO " + dbTableName + "(" + csv + ") SELECT " + csv + " FROM backup_");
    db.execSQL("DROP TABLE backup_");
  }

  /**
   * Returns an unmodifiable list of the ColumnProperties in columnOrder.
   *
   * @return
   */
  public List<ColumnProperties> getColumnsInOrder() {
    if (staleColumnsInOrder) {
      ArrayList<ColumnProperties> cio = new ArrayList<ColumnProperties>();
      for (String elementKey : columnOrder) {
        cio.add(getColumnByElementKey(elementKey));
      }
      columnsInOrder = Collections.unmodifiableList(cio);
      staleColumnsInOrder = false;
    }
    return columnsInOrder;
  }

  /**
   * The column order is specified by an ordered list of element keys.
   *
   * @return a copy of the columnOrder. Since it is a copy, should cache when
   *         possible.
   */
  public List<String> getColumnOrder() {
    List<String> defensiveCopy = new ArrayList<String>();
    defensiveCopy.addAll(columnOrder);
    return defensiveCopy;
  }

  public void setColumnOrder(SQLiteDatabase db, List<String> columnOrder) throws JsonGenerationException, JsonMappingException, IOException {
    String colOrderList = null;
    colOrderList = mapper.writeValueAsString(columnOrder);
    tableKVSH.setString(db, KEY_COLUMN_ORDER, colOrderList);
    this.columnOrder = columnOrder;
    this.staleColumnsInOrder = true;
  }

  /**
   * @return a copy of the element names of the prime columns. Since is a copy,
   *         should cache when possible.
   */
  public List<String> getGroupByColumns() {
    List<String> defensiveCopy = new ArrayList<String>();
    defensiveCopy.addAll(this.groupByColumns);
    return defensiveCopy;
  }

  public boolean isGroupByColumn(String elementKey) {
    return groupByColumns.contains(elementKey);
  }

  public boolean hasGroupByColumns() {
    return !groupByColumns.isEmpty();
  }

  /**
   * Sets the table's prime columns.
   *
   * @param db
   * @param groupByCols
   *          an array of the database names of the table's prime columns
   * @throws IOException
   * @throws JsonMappingException
   * @throws JsonGenerationException
   */
  public void setGroupByColumns(SQLiteDatabase db, List<String> groupByCols) throws JsonGenerationException, JsonMappingException, IOException {
    String groupByJsonStr;
    if ( groupByCols == null ) {
    	groupByCols = new ArrayList<String>();
    }
    groupByJsonStr = mapper.writeValueAsString(groupByCols);
    tableKVSH.setString(db, KEY_GROUP_BY_COLUMNS, groupByJsonStr);
    this.groupByColumns = groupByCols;
  }

  /**
   * @return the database name of the sort column (or null for no sort column)
   */
  public String getSortColumn() {
    return sortColumn;
  }

  /**
   * Sets the table's sort column.
   *
   * @param sortColumn
   *          the database name of the new sort column (or null for no sort
   *          column)
   */
  public void setSortColumn(SQLiteDatabase db, String sortColumn) {
    if ((sortColumn != null) && (sortColumn.length() == 0)) {
      sortColumn = null;
    }
    tableKVSH.setString(db, KEY_SORT_COLUMN, sortColumn);
    this.sortColumn = sortColumn;
  }

  public String getSortOrder() {
    return sortOrder;
  }

  public void setSortOrder(SQLiteDatabase db, String sortOrder) {
    if ((sortOrder != null) && (sortOrder.length() == 0)) {
      sortOrder = null;
    }

    tableKVSH.setString(db, KEY_SORT_ORDER, sortOrder);
    this.sortOrder = sortOrder;
  }

  /**
   * Unimplemented.
   * <p>
   * Should set the table id. First should verify uniqueness, etc.
   * @param newTableId
   */
  public void setTableId(String newTableId) {
    throw new UnsupportedOperationException(
        "setTableId is not yet implemented.");
  }

  /**
   * Return the element key of the indexed (frozen) column.
   *
   * @return
   */
  public String getIndexColumn() {
    return this.indexColumn;
  }

  /**
   * Set the index column for the table. This should be set by the display name
   * of the column. A null value will set the index column back to the default
   * value. TODO: make this use the element key
   *
   * @param indexColumnElementKey
   */
  public void setIndexColumn(SQLiteDatabase db, String indexColumnElementKey) {
    if ((indexColumnElementKey == null)) {
      indexColumnElementKey = DEFAULT_KEY_INDEX_COLUMN;
    }
    tableKVSH.setString(db, KEY_INDEX_COLUMN, indexColumnElementKey);
    this.indexColumn = indexColumnElementKey;
  }

  /**
   * @return the sync tag. Unsynched tables return the empty string.
   */
  public SyncTag getSyncTag() {
    return syncTag;
  }

  /**
   * Sets the table's sync tag.
   *
   * @param syncTag
   *          the new sync tag
   */
  public void setSyncTag(SyncTag syncTag) {
    SQLiteDatabase db = getWritableDatabase();
    try {
      db.beginTransaction();
      TableDefinitions.setValue(db, tableId, TableDefinitionsColumns.SYNC_TAG, syncTag.toString());
      this.syncTag = syncTag;
      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
      db.close();
      markStaleCache(); // all are stale because of sync state change
    }
  }

  /**
   * @return the last synchronization time (in the format of
   *         {@link DataUtil#getNowInDbFormat()}.
   */
  public String getLastSyncTime() {
    return lastSyncTime;
  }

  /**
   * Sets the table's last synchronization time.
   *
   * @param time
   *          the new synchronization time (in the format of
   *          {@link DataUtil#getNowInDbFormat()}).
   */
  public void setLastSyncTime(String time) {
    SQLiteDatabase db = getWritableDatabase();
    try {
      db.beginTransaction();
      TableDefinitions.setValue(db, tableId, TableDefinitionsColumns.LAST_SYNC_TIME, time);
      this.lastSyncTime = time;
      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
      db.close();
      markStaleCache(); // all are stale because of sync state change
    }
  }

  /**
   * @return the synchronization state
   */
  public SyncState getSyncState() {
    return syncState;
  }

  /**
   * Sets the table's synchronization state. Can only move to or from the REST
   * state (e.g., no skipping straight from INSERTING to UPDATING).
   *
   * @param state
   *          the new synchronization state
   */
  public void setSyncState(SyncState state) {
    if (state == SyncState.rest || this.syncState == SyncState.rest) {
      SQLiteDatabase db = getWritableDatabase();
      try {
        db.beginTransaction();
        TableDefinitions.setValue(db, tableId, TableDefinitionsColumns.SYNC_STATE, state.name());
        this.syncState = state;
        db.setTransactionSuccessful();
      } finally {
        db.endTransaction();
        db.close();
        markStaleCache(); // all are stale because of sync state change
      }
    }
  }

  /**
   * @return the transactioning status
   */
  public boolean isTransactioning() {
    return transactioning;
  }

  /**
   * Sets the transactioning status.
   *
   * @param transactioning
   *          the new transactioning status
   */
  public void setTransactioning(boolean transactioning) {
    tableKVSH
        .setInteger(TableDefinitionsColumns.TRANSACTIONING, DataHelper.boolToInt(transactioning));
    this.transactioning = transactioning;
    markStaleCache(); // all are stale because of sync state change
  }

  public String toJson() throws JsonGenerationException, JsonMappingException, IOException {
    Map<String, ColumnProperties> columnsMap = getDatabaseColumns(); // ensuring columns
                                                             // is initialized
    // I think this removes exceptions from not having getters/setters...
    ArrayList<String> colOrder = new ArrayList<String>();
    ArrayList<Object> cols = new ArrayList<Object>();
    for (ColumnProperties cp : columnsMap.values()) {
      colOrder.add(cp.getElementKey());
      cols.add(cp.toJson());
    }
    Map<String, Object> jo = new HashMap<String, Object>();
    jo.put(JSON_KEY_VERSION, 1);
    jo.put(JSON_KEY_TABLE_ID, tableId);
    jo.put(JSON_KEY_DB_TABLE_NAME, dbTableName);
    jo.put(JSON_KEY_DISPLAY_NAME, displayName);
    jo.put(JSON_KEY_COLUMN_ORDER, colOrder);
    jo.put(JSON_KEY_COLUMNS, cols);
    jo.put(JSON_KEY_GROUP_BY_COLUMNS, getGroupByColumns());
    jo.put(JSON_KEY_SORT_COLUMN, sortColumn);
    jo.put(JSON_KEY_SORT_ORDER, sortOrder);
    jo.put(JSON_KEY_INDEX_COLUMN, indexColumn);
    jo.put(JSON_KEY_DEFAULT_VIEW_TYPE, defaultViewType.name());

    String toReturn = null;
    toReturn = mapper.writeValueAsString(jo);
    return toReturn;
  }

  /**
   * Called from CSV import and server synchronization primitives
   *
   * @param json
   */
  public boolean setFromJson(String json) {
    @SuppressWarnings("unchecked")
    Map<String, Object> jo;
    try {
      jo = mapper.readValue(json, Map.class);

      if ( !((String) jo.get(JSON_KEY_TABLE_ID)).equals(this.getTableId()) ) {
        return false;
      }
      ArrayList<String> colOrder = (ArrayList<String>) jo.get(JSON_KEY_COLUMN_ORDER);
      ArrayList<String> groupByCols = (ArrayList<String>) jo.get(JSON_KEY_GROUP_BY_COLUMNS);
      Set<String> columnsToDelete = new HashSet<String>();

      // add or alter columns
      SQLiteDatabase db = getWritableDatabase();
      try {
        db.beginTransaction();
        setDisplayName(db, (String) jo.get(JSON_KEY_DISPLAY_NAME));
        setGroupByColumns(db, groupByCols);
        setSortColumn(db, (String) jo.get(JSON_KEY_SORT_COLUMN));
        setSortOrder(db, (String) jo.get(JSON_KEY_SORT_ORDER));
        setIndexColumn(db, (String) jo.get(JSON_KEY_INDEX_COLUMN));
        String viewType = (String) jo.get(JSON_KEY_DEFAULT_VIEW_TYPE);
        setDefaultViewType(db, viewType == null ? TableViewType.SPREADSHEET : TableViewType.valueOf(viewType));

        Set<String> columnElementKeys = new HashSet<String>();
        ArrayList<Object> colJArr = (ArrayList<Object>) jo.get(JSON_KEY_COLUMNS);
        for (int i = 0; i < colOrder.size(); i++) {
          ColumnProperties cp = ColumnProperties.constructColumnPropertiesFromJson(this,
              (String) colJArr.get(i));

          columnElementKeys.add(cp.getElementKey());
          ColumnProperties existing = this.getColumnByElementKey(cp.getElementKey());
          if (existing != null) {
            ColumnDefinitionChange change = existing.compareColumnDefinitions(cp);
            if (change == ColumnDefinitionChange.INCOMPATIBLE) {
              throw new IllegalArgumentException("incompatible column definitions: "
                  + cp.getElementKey());
            }

            if (change == ColumnDefinitionChange.CHANGE_ELEMENT_TYPE) {
              ColumnType now = existing.getColumnType();
              ColumnType next = cp.getColumnType();
              existing.setColumnType(db, this, next);
            }
            // persist the incoming definition
            cp.persistColumn(db);
            this.refreshColumns(db); // to read in this new definition.
          } else {
            this.addColumn(cp);
          }
        }

        // Collect the columns that are not in the newly defined set
        for (String column : this.getDatabaseColumns().keySet()) {
          if (!columnElementKeys.contains(column)) {
            columnsToDelete.add(column);
          }
        }

        setColumnOrder(db, colOrder);
        db.setTransactionSuccessful();
      } finally {
        db.endTransaction();
        db.close();
      }

      // Remove columns
      for (String columnToDelete : columnsToDelete) {
        deleteColumn(columnToDelete);
      }

    } catch (JsonParseException e) {
      e.printStackTrace();
      throw new IllegalArgumentException("invalid json: " + json);
    } catch (JsonMappingException e) {
      e.printStackTrace();
      throw new IllegalArgumentException("invalid json: " + json);
    } catch (IOException e) {
      e.printStackTrace();
      throw new IllegalArgumentException("invalid json: " + json);
    }
    return true;
  }

  /**
   * Get the possible view types for this table.
   * @return a {@link Set} containing the possible view types in the table.
   */
  public Set<TableViewType> getPossibleViewTypes() {
    int locationColCount = 0;
    Map<String, ColumnProperties> columnProperties = this.getDatabaseColumns();
    List<ColumnProperties> geoPoints = getGeopointColumns();
    for (ColumnProperties cp : columnProperties.values()) {
      if (cp.getColumnType() == ColumnType.NUMBER || cp.getColumnType() == ColumnType.INTEGER) {
        if (isLatitudeColumn(geoPoints, cp) || isLongitudeColumn(geoPoints, cp)) {
          locationColCount++;
        }
      } else if (cp.getColumnType() == ColumnType.GEOPOINT) {
        locationColCount += 2;// latitude and longitude
      } else if (isLatitudeColumn(geoPoints, cp) || isLongitudeColumn(geoPoints, cp)) {
        locationColCount++;
      }
    }
    Set<TableViewType> viewTypes = new HashSet<TableViewType>();
    viewTypes.add(TableViewType.SPREADSHEET);
    viewTypes.add(TableViewType.LIST);
    viewTypes.add(TableViewType.GRAPH);
    if (locationColCount >= 1) {
      viewTypes.add(TableViewType.MAP);
    }
    return viewTypes;
  }

  public List<ColumnProperties> getGeopointColumns() {
    Map<String, ColumnProperties> allColumns = this.getAllColumns();
    List<ColumnProperties> cpList = new ArrayList<ColumnProperties>();
    // TODO: HACK BROKEN HACK BROKEN
    // this is all entirely broken
//    for ( ColumnProperties cp : allColumns.values()) {
//      if ( cp.getColumnType() == ColumnType.GEOPOINT ) {
//        cpList.add(cp);
//      }
//    }
    return cpList;
  }

  public boolean isLatitudeColumn(List<ColumnProperties> geoPointList, ColumnProperties cp) {
    if ( endsWithIgnoreCase(cp.getLocalizedDisplayName(), "latitude") ) return true;
    if ( cp.getColumnType() != ColumnType.NUMBER ) return false;
    // TODO: HACK BROKEN HACK BROKEN
    if ( "latitude".equals(cp.getElementName()) ) return true;
//
//    for ( ColumnProperties geoPoint : geoPointList ) {
//      List<String> children = geoPoint.getListChildElementKeys();
//      for ( String elementKey : children ) {
//        if ( elementKey.equals(cp.getElementKey()) ) {
//          return cp.getElementName().equals("latitude");
//        }
//      }
//    }
    return false;
  }

  public boolean isLongitudeColumn(List<ColumnProperties> geoPointList, ColumnProperties cp) {
    if ( endsWithIgnoreCase(cp.getLocalizedDisplayName(), "longitude") ) return true;
    if ( cp.getColumnType() != ColumnType.NUMBER ) return false;
    if ( "longitude".equals(cp.getElementName()) ) return true;
    // TODO: HACK BROKEN HACK BROKEN
//    for ( ColumnProperties geoPoint : geoPointList ) {
//      List<String> children = geoPoint.getListChildElementKeys();
//      for ( String elementKey : children ) {
//        if ( elementKey.equals(cp.getElementKey()) ) {
//          return cp.getElementName().equals("longitude");
//        }
//      }
//    }
    return false;
  }

  private static boolean endsWithIgnoreCase(String text, String ending) {
    if (text.equalsIgnoreCase(ending)) {
      return true;
    }
    int spidx = text.lastIndexOf(' ');
    int usidx = text.lastIndexOf('_');
    int idx = Math.max(spidx, usidx);
    if (idx == -1) {
      return false;
    }
    return text.substring(idx + 1).equalsIgnoreCase(ending);
  }

  /**
   * Get the accessor object for persisted values in the key value store.
   *
   * @param partition
   * @return
   */
  public KeyValueStoreHelper getKeyValueStoreHelper(String partition) {
    KeyValueStoreManager kvsm = new KeyValueStoreManager();
    KeyValueStore backingStore = kvsm.getStoreForTable(this.tableId);
    return new KeyValueStoreHelper(backingStore, partition, this);
  }

  /**
   * Returns an array of the initialized properties. These are the keys that
   * exist in the key value store for any table.
   *
   * @return
   */
  public static String[] getInitKeys() {
    return INIT_KEYS;
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof TableProperties)) {
      return false;
    }
    TableProperties other = (TableProperties) obj;
    return tableId.equals(other.tableId);
  }

  // TODO: this is a crap hash function given all the information that this
  // object contains. It should really be updated.
  @Override
  public int hashCode() {
    return tableId.hashCode();
  }

  @Override
  public String toString() {
    return displayName;
  }

}
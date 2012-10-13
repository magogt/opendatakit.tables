package org.opendatakit.tables.data;

import java.util.ArrayList;
import java.util.List;

import org.opendatakit.aggregate.odktables.entity.OdkTablesKeyValueStoreEntry;
import org.opendatakit.tables.sync.SyncUtil;

import android.database.sqlite.SQLiteDatabase;

public class KeyValueStoreSync extends KeyValueStore {

  public KeyValueStoreSync(String dbName, DbHelper dbh, String tableId) {
    super(dbName, dbh, tableId);
  }
  
  /**
   * Returns whether or not the table is set to sync, according to the sync
   * key value store. If there is no entry in the sync KVS, which will happen
   * if there are no table properties for the table in the server KVS, then
   * this will return false. (Is this the right decision?)
   * @return
   */
  public boolean isSetToSync() {
    SQLiteDatabase db = this.dbh.getReadableDatabase();
    List<String> isSetToSyncKey = new ArrayList<String>();
    isSetToSyncKey.add(SyncPropertiesKeys.IS_SET_TO_SYNC.getKey());
    List<OdkTablesKeyValueStoreEntry> isSetToSyncEntry =
        this.getEntriesForKeys(db, isSetToSyncKey);
    if (isSetToSyncEntry.size() == 0)
      return false;
    // otherwise there is a single entry and it is the one we want.
    if (isSetToSyncEntry.get(0).value.equals("1")) {
      return true;
    } else {
      return false;
    }
  }
  
  /**
   * Set in the sync KVS whether or not the table is set to be synched.
   * @param val
   */
  public void setIsSetToSync(boolean val) {
    SQLiteDatabase db = this.dbh.getWritableDatabase();
    int newValue = SyncUtil.boolToInt(val);
    this.insertOrUpdateKey(db, "Integer", 
        SyncPropertiesKeys.IS_SET_TO_SYNC.getKey(), 
        Integer.toString(newValue));
  }
  
  
  
  
  /**
   * These are the keys that have assigned functions in the key value store 
   * that holds sync properties.
   */
  public static enum SyncPropertiesKeys {
    /**
     * Holds an integer (1 or 0) of whether or not this table is set to be 
     * synched with the server. This is only applicable to tables that have 
     * properties in the server key value store. The reasoning behind this is
     * that synching with the server is based on the properties in the server
     * key value store. This means that a table is NOT in the server key value
     * store you will not be able to select it to sync. Upon copying data into
     * from the default to the server key value store, an entry for the table 
     * with this key created and added to the sync key value store (if an entry
     * for the table in the sync key value store does not already exist) and 
     * its value is set to 0. 
     */
    IS_SET_TO_SYNC("isSetToSync");
    
    private String key;
    
    private SyncPropertiesKeys(String key) {
      this.key = key;
    }
    
    public String getKey() {
      return key;
    }
  }
}

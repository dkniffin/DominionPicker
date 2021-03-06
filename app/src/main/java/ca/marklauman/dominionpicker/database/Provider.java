package ca.marklauman.dominionpicker.database;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;

import java.io.File;

/** This content provider is where all database queries in this app end up.
 *  @author Mark Lauman */
@SuppressWarnings("WeakerAccess")
public class Provider extends ContentProvider {

    /** The authority this provider operates over */
    public static final String AUTHORITY = "ca.marklauman.dominionpicker";

    /** MIME type for cards. */
    public static final String MIME_CARD = "ca.marklauman.dominionpicker.card";
    /** Mime type for supplies. */
    public static final String MIME_SUPPLY = "ca.marklauman.dominionpicker.supply";
    /** Mime type for translated supplies. */
    public static final String MIME_SUPPLY_TRANS = MIME_SUPPLY +".trans";

    /** Internal id for unrecognized URIs */
    private static final int ID_WTF = 0;
    /** Internal id for the cardData table's URI. */
    private static final int ID_CARD_DATA = 1;
    /** Internal id for the cardTrans table's URI. */
    private static final int ID_CARD_TRANS = 2;
    /** Internal id for the combined card table's URI. */
    private static final int ID_CARD_ALL = 3;
    /** Internal id for the supply table's URI. */
    private static final int ID_SUPPLY = 4;
    /** Internal id for the history table's URI. */
    private static final int ID_HIST = 5;

    /** URI to access the card data table */
    public static final Uri URI_CARD_DATA = Uri.parse("content://ca.marklauman.dominionpicker/cardData");
    /** URI to access the card trans table */
    public static final Uri URI_CARD_TRANS = Uri.parse("content://ca.marklauman.dominionpicker/cardTrans");
    /** URI to access the combination of all card tables */
    public static final Uri URI_CARD_ALL = Uri.parse("content://ca.marklauman.dominionpicker/cardAll");
    /** URI to access the sample supply table */
    public static final Uri URI_SUPPLY = Uri.parse("content://ca.marklauman.dominionpicker/supply");
    /** URI to access the history table */
    public static final Uri URI_HIST = Uri.parse("content://ca.marklauman.dominionpicker/history");

    /** Used to match URIs to tables. */
    UriMatcher matcher;
    /** Handle to the card database. */
	private CardDb card_db;
    /** Handle to the supply database */
    private SupplyDb supplyDb;
    /** Handle to the data database */
    private DataDb data_db;


	@Override
	public boolean onCreate() {
        // setup the uri matcher
        matcher = new UriMatcher(ID_WTF);
        matcher.addURI(AUTHORITY, "cardData", ID_CARD_DATA);
        matcher.addURI(AUTHORITY, "cardTrans", ID_CARD_TRANS);
        matcher.addURI(AUTHORITY, "cardAll", ID_CARD_ALL);
        matcher.addURI(AUTHORITY, "supply", ID_SUPPLY);
        matcher.addURI(AUTHORITY, "history", ID_HIST);


        // Remove old database files.
        Context c = getContext();
        File[] dbListing = c.getDatabasePath(CardDb.FILE_NAME)
                            .getParentFile()
                            .listFiles();
        String name;
        if(dbListing != null) {
            for (File db : dbListing) {
                name = db.getName();
                if (!(CardDb.FILE_NAME.equals(name) || SupplyDb.FILE_NAME.equals(name)
                      || DataDb.FILE_NAME.equals(name)))
                    //noinspection ResultOfMethodCallIgnored
                    db.delete();
            }
        }

        // get the database files
		card_db = new CardDb(c);
        supplyDb = new SupplyDb(c);
        data_db = new DataDb(c);
		return true;
	}


	@Override
	public String getType(Uri uri) {
        switch (matcher.match(uri)) {
            case ID_CARD_DATA:
            case ID_CARD_TRANS:
            case ID_CARD_ALL: return MIME_CARD;
            case ID_SUPPLY: return MIME_SUPPLY_TRANS;
            case ID_HIST: return MIME_SUPPLY;
            default: return null;
        }
	}

	@Override
	public Cursor query(Uri uri, String[] projection,
				String selection, String[] selectionArgs,
				String sortOrder) {
        SQLiteDatabase db;
        Cursor res;
        switch(matcher.match(uri)) {
            case ID_CARD_DATA:
                res = card_db.query(CardDb.TABLE_DATA, projection,
                                    selection, selectionArgs, sortOrder);
                break;
            case ID_CARD_TRANS:
                res = card_db.query(CardDb.TABLE_TRANS, projection,
                                    selection, selectionArgs, sortOrder);
                break;
            case ID_CARD_ALL:
                res = card_db.query(CardDb.VIEW_ALL, projection,
                                    selection, selectionArgs, sortOrder);
                break;
            case ID_SUPPLY:
                res = supplyDb.query(projection, selection, selectionArgs, sortOrder);
                break;
            case ID_HIST:
                db = data_db.getReadableDatabase();
                res = db.query(DataDb.TABLE_HISTORY, projection,
                               selection, selectionArgs,
                               null, null, sortOrder);
                break;
            default: return null;
        }
        res.setNotificationUri(getContext().getContentResolver(), uri);
        return res;
	}

	
	@Override
	public Uri insert(Uri uri, ContentValues values) {
        switch(matcher.match(uri)) {
            case ID_HIST:
                long row;
                try {
                    row = data_db.getReadableDatabase()
                                 .insertOrThrow(DataDb.TABLE_HISTORY, null, values);
                } catch (Exception e) {
                    row = values.getAsLong(DataDb._H_TIME);
                    int change = update(uri, values,
                                        DataDb._H_TIME + "=?",
                                        new String[]{"" + row});
                    if(change < 1) row = -1L;
                }
                if(row == -1L) return null;
                notifyChange(URI_HIST);
                return Uri.withAppendedPath(URI_HIST, "" + row);
            default: return null;
        }
	}


	@Override
	public int update(Uri uri, ContentValues values,
                      String selection, String[] selectionArgs) {
        switch(matcher.match(uri)) {
            case ID_HIST:
                int change = data_db.getReadableDatabase()
                                    .update(DataDb.TABLE_HISTORY, values,
                                            selection, selectionArgs);
                if(0 < change) notifyChange(URI_HIST);
                return change;
            default: return 0;
        }
	}
	
	@Override
	public int delete(Uri uri, String selection, String[] selectionArgs) {
        switch (matcher.match(uri)) {
            case ID_HIST:
                int change = data_db.getReadableDatabase()
                                    .delete(DataDb.TABLE_HISTORY,
                                            selection, selectionArgs);
                if(change != 0) notifyChange(URI_HIST);
                return change;
            default: return 0;
        }
    }

    /** Notify all listening processes that the data at the uri has changed */
    private void notifyChange(Uri uri) {
        getContext().getContentResolver()
                    .notifyChange(uri, null);
    }
}
/*
 * Copyright (C) 2015 NuBits Developers
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.matthewmitchell.nubits_android_wallet.ui.preference;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.matthewmitchell.nubits_android_wallet.R;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A database helper for storing trusted servers in an SQLite database.
 * @author Matthew Mitchell
 */
public class TrustedServersDatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "trusted_servers.db";
    private static final int DATABASE_VERSION = 2;

    private static final String TABLE_NAME =  "servers";

    private static final String FIELD_ID = "id";
    private static final String FIELD_ORDER = "ordering";
    private static final String FIELD_NAME =  "name";
    private static final String FIELD_URL =   "url";
    private static final String FIELD_EQUAL = "equal";

    public static final String DEFAULT_SERVER_EXPLORER = "https://svr1.nubitsexplorer.nu/q/getvalidhashes";
    public static final String DEFAULT_SERVER_ANTON = "https://anton.coinerella.com/q/getvalidhashes";

    private final Context context;

    private static TrustedServersDatabaseHelper instance = null;

    /**
     * Obtains a singleton instance of the database helper.
     */
    synchronized public static TrustedServersDatabaseHelper getInstance (final Context context) {

        if (instance == null)
            instance = new TrustedServersDatabaseHelper(context);

        return instance;

    }

    private TrustedServersDatabaseHelper(Context context) {

        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        this.context = context;

    }

    private TrustedServer[] insertDefaults(SQLiteDatabase db) {

        TrustedServer[] servers = {
            insertServer(db, context.getString(R.string.trusted_servers_default_explorer), DEFAULT_SERVER_EXPLORER, false, 0),
            insertServer(db, context.getString(R.string.trusted_servers_default_anton), DEFAULT_SERVER_ANTON, true, 1),
        };

        return servers;
    }

    /**
     * Reset the server list to the default in the database.
     */
    public TrustedServer[] restoreDefaults() {

        SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE_NAME, null, null);
        return insertDefaults(db);

    }

    public TrustedServer insertAnton() {

        // Insert Anton Server to top of list

        SQLiteDatabase db = getWritableDatabase();
        TrustedServer server = insertServerWithDb(db, context.getString(R.string.trusted_servers_default_anton), DEFAULT_SERVER_ANTON, false);
        updateOrderWithDb(db, server.id, server.order, 0);

        // Now make the server below it equal in priority

        ContentValues values = new ContentValues(3);
        values.put(FIELD_EQUAL, true);
        db.update(TABLE_NAME, values, FIELD_ORDER + " = 1", null);

        return server;

    }

    /**
     * Insert the information for a new server to be added to the end of the list.
     */
    public TrustedServer insertServer(String name, String url, boolean equal) {

        SQLiteDatabase db = getWritableDatabase();
        return insertServerWithDb(db, name, url, equal);

    }

    public TrustedServer insertServerWithDb(SQLiteDatabase db, String name, String url, boolean equal) {

        Cursor cursor = db.query(TABLE_NAME, new String[]{ FIELD_ORDER }, null, null, null, null, FIELD_ORDER + " DESC", "1");
        cursor.moveToFirst();

        int orderToInsert = 0;
        if (cursor.getCount() != 0)
            orderToInsert = cursor.getInt(0) + 1;

        cursor.close();

        return insertServer(getWritableDatabase(), name, url, equal, orderToInsert);

    }

    private TrustedServer insertServer(SQLiteDatabase db, String name, String url, boolean equal, int order) {

        ContentValues values = new ContentValues(4);
        values.put(FIELD_ORDER, order);
        values.put(FIELD_NAME, name);
        values.put(FIELD_URL, url);
        values.put(FIELD_EQUAL, equal);

        return new TrustedServer(db.insert(TABLE_NAME, null, values), name, url, equal, order);

    }

    /**
     * Deletes a server from the list by specifying its order.
     */
    public void deleteServer(int order) {

        SQLiteDatabase db = getWritableDatabase();

        db.beginTransaction();

        try {

            // Delete the server and also update the orders of higher servers to be decremented

            db.delete(TABLE_NAME, FIELD_ORDER + " = " + order, null);

            db.execSQL(String.format(
                        Locale.US,
                        "UPDATE %s SET %s = %s - 1 WHERE %s > %d;", 
                        TABLE_NAME, FIELD_ORDER, FIELD_ORDER, FIELD_ORDER, order
                        ));

            db.setTransactionSuccessful();

        } finally {
            db.endTransaction();
        }

    }

    /**
     * For a given server id, update its details in the database.
     */
    public void updateServerDetails(long id, String name, String url, boolean equal) {

        ContentValues values = new ContentValues(3);
        values.put(FIELD_NAME, name);
        values.put(FIELD_URL, url);
        values.put(FIELD_EQUAL, equal);

        getWritableDatabase().update(TABLE_NAME, values, FIELD_ID + " = " + id, null);

    }

    /**
     * Moves the server given by its id, from its correct specified postion to a new position.
     */
    public void updateOrder(long id, int prevOrder, int newOrder) {

        SQLiteDatabase db = getWritableDatabase();
        updateOrderWithDb(db, id, prevOrder, newOrder);

    }

    public void updateOrderWithDb(SQLiteDatabase db, long id, int prevOrder, int newOrder) {

        /* 
         * Given that p is the previous order and n is the new order:
         * If p = n then do nothing
         * If p < n then for all p + 1 ... n, decrement order.
         * If p > n then for all n ... p - 1, increment order.
         */

        if (prevOrder == newOrder)
            return;

        int start, end;
        String operator;

        if (prevOrder < newOrder) {
            operator = "-";
            start = prevOrder + 1;
            end = newOrder;
        } else {
            operator = "+";
            start = newOrder;
            end = prevOrder - 1;
        }

        // Update database in transaction

        db.beginTransaction();

        try {

            // As SQLiteDatabase has trouble with the update method, do a raw query.

            db.execSQL(String.format(
                        Locale.US,
                        "UPDATE %s SET %s = %s %s 1 WHERE %s BETWEEN %d AND %d;", 
                        TABLE_NAME, FIELD_ORDER, FIELD_ORDER, operator, FIELD_ORDER, start, end
                        ));

            ContentValues values = new ContentValues(1);
            values.put(FIELD_ORDER, newOrder);
            db.update(TABLE_NAME, values, FIELD_ID + " = " + id, null);

            db.setTransactionSuccessful();

        } finally {
            db.endTransaction();
        }

    }

    /**
     * Gets a Cursor object for the server id, name, url, and priority equality, for all servers in ascending order.
     */
    public Cursor getServersCursor() {

        return getReadableDatabase().query(
                TABLE_NAME, new String[]{ FIELD_ID, FIELD_NAME, FIELD_URL, FIELD_EQUAL }, null, null, null, null, FIELD_ORDER + " ASC"
                );

    }

    @Override
    public void onCreate(SQLiteDatabase db) {

        db.execSQL(
                "CREATE TABLE " + TABLE_NAME + " ("
                + FIELD_ID + " integer primary key, "
                + FIELD_ORDER + " integer not null, "
                + FIELD_NAME + " text not null, "
                + FIELD_URL + " text not null, "
                + FIELD_EQUAL + " boolean not null"
                + ");"
                );

        db.execSQL("CREATE INDEX " + TABLE_NAME + "_" + FIELD_ORDER + "_index ON " + TABLE_NAME + " (" + FIELD_ORDER + ");");

        insertDefaults(db);

    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Do nothing
    }

}
